package net.streamdek.mobile.nativeapp

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Whether the viewer is scrolling anywhere in the app right now.
 *
 * Read off the main thread by the frame monitor in `VisualEffects.kt`, which only counts frames
 * drawn mid-scroll: a slow frame while a page first composes says nothing about whether the glass
 * can keep up with a fling.
 */
internal object ScrollActivity {
  @Volatile
  var active: Boolean = false
}

/**
 * The one scroll observer the app's chrome shares.
 *
 * Installed once, as a nested-scroll parent of every page, by `MainScene`. Pages do not wire anything
 * up to be observed: any vertical scroll inside them — a lazy list, a column, a fling, a drag —
 * reaches [nestedScrollConnection] on its way out. Horizontal carousels report no vertical
 * movement and so are invisible to it, and it never consumes anything, so it cannot fight a swipe,
 * a pager or pull-to-refresh for the gesture.
 *
 * What it publishes is deliberately coarse. [collapseFraction] changes every scrolled frame, but is
 * meant to be read only inside `offset {}`, `graphicsLayer {}` and draw lambdas, where a change
 * re-places or redraws without recomposing. [phase] and [navigationCollapsed] change a handful of
 * times per gesture, and those are safe to read in composition.
 */
@Stable
class ScrollChromeState internal constructor(density: Float, private val scope: CoroutineScope) {
  private val machine = ScrollChromeMachine(density)

  private var fractionState by mutableFloatStateOf(0f)

  /** 0 when the chrome is fully shown, 0.5 compact, 1 tucked away. Read in layout or draw only. */
  val collapseFraction: Float get() = fractionState

  var phase: ScrollPhase by mutableStateOf(ScrollPhase.NearTop)
    private set

  /** The floating navigation's half of the decision, with its own hysteresis. */
  var navigationCollapsed: Boolean by mutableStateOf(false)
    private set

  /** Kept current by [rememberScrollChromeState], so settling honours the viewer's motion setting. */
  internal var motion: MotionSettings = MotionSettings()

  private var settleJob: Job? = null
  private var idleWatch: Job? = null
  private var lastScrollMs = 0L
  private var unsettled = false

  val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      val delta = -consumed.y
      // Asking to go further back than the page can is what happens at its very start, and it is
      // the one moment the distance estimate can be corrected without the page's help.
      val blockedAtTop = available.y > 0.5f
      if (delta == 0f && !blockedAtTop) return Offset.Zero
      onScroll(delta, blockedAtTop)
      return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      settleNow()
      return Velocity.Zero
    }
  }

  private fun onScroll(delta: Float, blockedAtTop: Boolean) {
    settleJob?.cancel()
    val now = SystemClock.uptimeMillis()
    machine.onScroll(delta, blockedAtTop, now)
    publish()
    if (delta == 0f) return
    unsettled = true
    lastScrollMs = now
    ScrollActivity.active = true
    // A drag that simply stops, without a fling, still has to settle. One watcher per gesture
    // rather than a coroutine per frame: it sleeps until the scrolling has been quiet long enough.
    if (idleWatch?.isActive != true) {
      idleWatch = scope.launch {
        while (true) {
          val wait = IDLE_SETTLE_MS - (SystemClock.uptimeMillis() - lastScrollMs)
          if (wait <= 0) break
          delay(wait)
        }
        idleWatch = null
        settleNow()
      }
    }
  }

  private fun settleNow() {
    if (!unsettled) return
    unsettled = false
    ScrollActivity.active = false
    idleWatch?.takeIf { it.isActive }?.cancel()
    idleWatch = null
    val target = machine.settle()
    publish()
    animateFractionTo(target)
  }

  /** The viewer started using a control in the chrome — focused Search, typically. */
  fun beginInteraction() {
    if (machine.interacting) return
    machine.setInteracting(true)
    publish()
    animateFractionTo(ScrollChromeMachine.SHOWN)
  }

  fun endInteraction() {
    if (!machine.interacting) return
    machine.setInteracting(false)
    publish()
  }

  /** The viewer asked for the chrome back, e.g. by tapping the collapsed navigation. */
  fun expand() {
    machine.expand()
    publish()
    animateFractionTo(ScrollChromeMachine.SHOWN)
  }

  /** A page that knows its real scroll position, see [ReportScrollTop]. */
  fun reportAtTop(atTop: Boolean) {
    machine.reportAtTop(atTop)
    publish()
  }

  /** A different page is showing: start again from the top, fully shown, with no animation. */
  fun reset() {
    settleJob?.cancel()
    idleWatch?.cancel()
    idleWatch = null
    unsettled = false
    ScrollActivity.active = false
    machine.reset()
    publish()
  }

  private fun animateFractionTo(target: Float) {
    settleJob?.cancel()
    val from = machine.fraction
    if (from == target) return
    if (motion.motionless) {
      machine.animateTo(target)
      publish()
      return
    }
    settleJob = scope.launch {
      animate(
        initialValue = from,
        targetValue = target,
        // A spring rather than a tween: a settle is often interrupted by the next scroll and has to
        // carry on from wherever it got to. Speed reaches it through stiffness, as elsewhere.
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow / motion.scale.coerceAtLeast(0.1f)),
      ) { value, _ ->
        machine.animateTo(value)
        fractionState = value
      }
    }
  }

  private fun publish() {
    fractionState = machine.fraction
    if (phase != machine.phase) phase = machine.phase
    if (navigationCollapsed != machine.navigationCollapsed) navigationCollapsed = machine.navigationCollapsed
  }

  private companion object {
    const val IDLE_SETTLE_MS = 220L
  }
}

/** Null outside `MainScene`, where chrome simply stays put. */
val LocalScrollChrome: ProvidableCompositionLocal<ScrollChromeState?> = staticCompositionLocalOf { null }

@Composable
fun rememberScrollChromeState(): ScrollChromeState {
  val density = LocalDensity.current.density
  val scope = rememberCoroutineScope()
  val state = remember(density, scope) { ScrollChromeState(density, scope) }
  val motion = LocalMotionSettings.current
  SideEffect { state.motion = motion }
  return state
}

/**
 * Tells the shared chrome where the page really is.
 *
 * Optional: the chrome estimates position from scroll deltas. A page with a list state should still
 * call this, because the estimate cannot see a programmatic scroll-to-top or a page restored mid-way
 * down. [isAtTop] is read in a snapshot flow, so it costs one comparison per scrolled frame and a
 * report only when the answer changes.
 */
@Composable
fun ReportScrollTop(isAtTop: () -> Boolean) {
  val chrome = LocalScrollChrome.current ?: return
  val current by rememberUpdatedState(isAtTop)
  LaunchedEffect(chrome) {
    snapshotFlow { current() }.distinctUntilChanged().collect { chrome.reportAtTop(it) }
  }
}

/**
 * Holds the chrome still while this text field is being typed into.
 *
 * "Being typed into" is focus *and* a keyboard. Compose keeps focus on a field after the keyboard is
 * dismissed, so focus alone would freeze Search open forever once a viewer put the keyboard away to
 * browse the results. The first moments after focusing count too, because the keyboard is still
 * rising and the field must not collapse under the finger that just tapped it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.holdsChromeWhileTyping(): Modifier {
  val chrome = LocalScrollChrome.current
  var focused by remember { mutableStateOf(false) }
  var keyboardSeen by remember { mutableStateOf(false) }
  val imeVisible = WindowInsets.isImeVisible
  LaunchedEffect(focused, imeVisible) {
    if (!focused) keyboardSeen = false else if (imeVisible) keyboardSeen = true
  }
  val typing = focused && (imeVisible || !keyboardSeen)
  DisposableEffect(chrome, typing) {
    if (typing) chrome?.beginInteraction() else chrome?.endInteraction()
    onDispose { if (typing) chrome?.endInteraction() }
  }
  return onFocusChanged { focused = it.isFocused }
}

/**
 * What a [ScrollAwareHeader]'s content can mark.
 *
 * The header needs to know two things about its own content: which parts go first when it compacts
 * (the title row, typically), and where the search field starts, since compacting means sliding up
 * until the field is the top of what is left. Both are measured rather than passed in, because a
 * title is taller in German than in English and taller again at a large font scale.
 */
@Stable
class ScrollAwareHeaderScope internal constructor() {
  internal var root: LayoutCoordinates? = null
  internal var totalHeight by mutableIntStateOf(0)
  internal var anchorTop by mutableIntStateOf(0)
  internal var marginPx = 0
  internal var stopAtCompact = false
  internal var fraction: () -> Float = { 0f }

  /** Fades out over the first half of the collapse, as the header compacts. */
  fun Modifier.compactsAway(): Modifier = graphicsLayer {
    alpha = (1f - fraction() / ScrollChromeMachine.COMPACT).coerceIn(0f, 1f)
  }

  /** The part that should remain once compacted. Everything above it slides away. */
  fun Modifier.compactAnchor(): Modifier = onPlaced { coordinates ->
    val parent = root ?: return@onPlaced
    if (!parent.isAttached) return@onPlaced
    anchorTop = parent.localPositionOf(coordinates, Offset.Zero).y.roundToInt().coerceAtLeast(0)
  }

  internal fun offsetFor(value: Float): Int {
    val compact = (anchorTop - marginPx).coerceIn(0, totalHeight)
    // A header that keeps its anchor treats "hidden" as compact: the rest of the page still gets out
    // of the way, but the part the viewer came to the page for never leaves.
    val hidden = if (stopAtCompact) compact else totalHeight
    val offset = if (value <= ScrollChromeMachine.COMPACT) {
      compact * (value / ScrollChromeMachine.COMPACT)
    } else {
      compact + (hidden - compact) * ((value - ScrollChromeMachine.COMPACT) / ScrollChromeMachine.COMPACT)
    }
    return offset.roundToInt()
  }
}

/**
 * A page header that makes room while the viewer scrolls: full, then compact, then hidden, and back.
 *
 * The header slides rather than shrinks. Its slot keeps its size, so the list underneath never
 * re-measures and nothing on the page jumps; the header's content is simply placed higher inside a
 * clip, which is a placement change only — no recomposition, no layout pass for the page. Placement
 * rather than a graphics-layer translation because the glass inside works out what to blur from
 * where it is placed, and a translation alone would leave the blur behind.
 *
 * Put the clip edge where the header should disappear: [modifier] is applied outside the clip, so a
 * caller passes the status-bar padding there and the header tucks under the status bar rather than
 * sliding over its icons.
 *
 * [enabled] false keeps the header fixed — a header laid out as a side column on a tablet takes no
 * vertical room, so it has nothing to give back.
 *
 * [keepAnchorVisible] stops the header at its compact state: scrolling further tucks away everything
 * above the [ScrollAwareHeaderScope.compactAnchor] and nothing more, so a search field stays pinned
 * to the top of a page whose whole purpose is searching.
 */
@Composable
internal fun ScrollAwareHeader(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  keepAnchorVisible: Boolean = false,
  content: @Composable ScrollAwareHeaderScope.() -> Unit,
) {
  val chrome = LocalScrollChrome.current
  val headerScope = remember { ScrollAwareHeaderScope() }
  headerScope.stopAtCompact = keepAnchorVisible
  headerScope.marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
  val active = enabled && chrome != null
  headerScope.fraction = if (active) ({ chrome!!.collapseFraction }) else ({ 0f })
  Box(modifier = modifier.clipToBounds()) {
    Box(
      modifier = Modifier
        .then(if (active) Modifier.offset { IntOffset(0, -headerScope.offsetFor(chrome!!.collapseFraction)) } else Modifier)
        .onSizeChanged { headerScope.totalHeight = it.height }
        .onPlaced { headerScope.root = it },
      content = { headerScope.content() },
    )
  }
}
