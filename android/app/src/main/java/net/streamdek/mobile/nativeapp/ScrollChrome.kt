package net.streamdek.mobile.nativeapp

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import dev.chrisbanes.haze.HazeState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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

  private var presentedState by mutableFloatStateOf(0f)
  private var presentedVelocity = 0f
  private var chaseJob: Job? = null

  /**
   * [collapseFraction] as the eye should see it: chased by a critically damped spring rather than
   * copied frame for frame.
   *
   * The raw fraction follows the finger exactly, which is right for deciding things and wrong for
   * drawing them. Every hitch in a drag and every snap at the end of a settle would land on screen as
   * a jolt. Chasing it gives the chrome a little physical weight: it keeps pace with a steady scroll,
   * glides into a settle instead of snapping to it, and turns an abrupt change of state into one
   * continuous movement that headers can stagger their parts across.
   */
  val presentedFraction: Float get() = presentedState

  var phase: ScrollPhase by mutableStateOf(ScrollPhase.NearTop)
    private set

  /**
   * Whether the floating navigation should be tucked away.
   *
   * True while the viewer is moving through content in either direction, momentum included; false
   * again once everything has been still for a moment. See [ScrollChromeMachine.onIdle].
   */
  var navigationCollapsed: Boolean by mutableStateOf(false)
    private set

  private var navigationReturnWatch: Job? = null

  /**
   * Bumped by every [reset], so pages re-tell the chrome where they are.
   *
   * A page change resets the chrome and composes the new page in the same frame, and nothing
   * guarantees which of the two effects runs first. Without this, a page restored halfway down its
   * list could report its position and then have the reset wipe it, leaving the chrome believing it
   * was at the top.
   */
  internal var resetGeneration by mutableIntStateOf(0)
    private set

  /**
   * Watches fingers on the page without taking part in any gesture.
   *
   * Scroll deltas alone cannot tell "stopped" from "holding still": a thumb resting on a list it has
   * just caught, or pausing mid-drag to read, produces no movement at all. Watching the pointer on
   * the Initial pass, and consuming nothing, lets the navigation wait for the finger to actually
   * leave before its return clock starts — while taps, swipes, pagers and pull-to-refresh all see
   * exactly the events they always did.
   */
  val touchObserver: Modifier = Modifier.pointerInput(this) {
    awaitPointerEventScope {
      var down = false
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val pressed = event.changes.any { it.pressed }
        if (pressed != down) {
          down = pressed
          if (pressed) onTouchDown() else onTouchUp()
        }
      }
    }
  }

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
    scheduleNavigationReturn()
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

  private fun onTouchDown() {
    machine.onTouchDown(SystemClock.uptimeMillis())
    publish()
  }

  private fun onTouchUp() {
    machine.onTouchUp(SystemClock.uptimeMillis())
    publish()
    scheduleNavigationReturn()
  }

  /**
   * Brings the navigation back once everything has been still for long enough.
   *
   * One sleeper, re-reading the deadline each time it wakes, rather than a timer restarted on every
   * scrolled frame: a fling that keeps moving simply pushes the deadline on. It stands down while a
   * finger is on the page and is started again when that finger lifts.
   */
  private fun scheduleNavigationReturn() {
    if (!machine.navigationCollapsed || navigationReturnWatch?.isActive == true) return
    navigationReturnWatch = scope.launch {
      while (true) {
        val now = SystemClock.uptimeMillis()
        val remaining = machine.navigationReturnDelay(now) ?: break
        if (remaining <= 0) {
          machine.onIdle(now)
          publish()
          break
        }
        delay(remaining)
      }
      navigationReturnWatch = null
    }
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
    navigationReturnWatch?.cancel()
    navigationReturnWatch = null
    unsettled = false
    ScrollActivity.active = false
    machine.reset()
    resetGeneration += 1
    chaseJob?.cancel()
    presentedVelocity = 0f
    presentedState = 0f
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
        chase()
      }
    }
  }

  private fun chase() {
    if (motion.motionless) {
      chaseJob?.cancel()
      presentedVelocity = 0f
      presentedState = fractionState
      return
    }
    if (chaseJob?.isActive == true) return
    chaseJob = scope.launch {
      var previous = withFrameNanos { it }
      while (true) {
        val now = withFrameNanos { it }
        // Clamped so a dropped frame or a paused app cannot fling the value past its target.
        val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, 1f / 30f)
        previous = now
        val omega = CHASE_OMEGA / motion.scale.coerceAtLeast(0.1f)
        val target = fractionState
        val position = presentedState
        // Critically damped: the fastest approach that never overshoots, so a header slides into
        // place and stops rather than bouncing past it.
        presentedVelocity += (omega * omega * (target - position) - 2f * omega * presentedVelocity) * dt
        val next = position + presentedVelocity * dt
        if (kotlin.math.abs(target - next) < 0.0015f && kotlin.math.abs(presentedVelocity) < 0.02f) {
          presentedState = target
          presentedVelocity = 0f
          break
        }
        presentedState = next
      }
    }
  }

  private fun publish() {
    fractionState = machine.fraction
    chase()
    if (phase != machine.phase) phase = machine.phase
    if (navigationCollapsed != machine.navigationCollapsed) navigationCollapsed = machine.navigationCollapsed
  }

  private companion object {
    const val IDLE_SETTLE_MS = 220L
    /** Spring stiffness of the presentation chase, in rad/s: around a third of a second to settle. */
    const val CHASE_OMEGA = 15f
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
  LaunchedEffect(chrome, chrome.resetGeneration) {
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

/** What a [ScrollAwareHeader] draws behind its content. */
@Immutable
sealed interface ScrollAwareHeaderSurface {
  /**
   * The Default style: a tinted band behind the header.
   *
   * [pillAroundAnchor] decides what it becomes as the header compacts. On a page that condenses to a
   * search field, the band closes into a pill around that field and the rest of the background goes
   * away, as the Modern glass does. Otherwise — a library page keeping its title row — it tightens
   * into a band across the width.
   */
  @Immutable
  data class Solid(val color: Color, val pillAroundAnchor: Boolean = false) : ScrollAwareHeaderSurface

  /** The Modern style: a glass panel that closes into a floating pill around the search field. */
  @Immutable
  data class Glass(val hazeState: HazeState) : ScrollAwareHeaderSurface
}

/**
 * What a [ScrollAwareHeader]'s content can mark.
 *
 * The header needs to know two things about its own content: where the search field is, since that
 * is the part that stays, and which other parts go and in what order. Both are declared and measured
 * by the content rather than passed in, because a title is taller in German than in English and
 * taller again at a large font scale.
 */
@Stable
class ScrollAwareHeaderScope internal constructor() {
  internal var root: LayoutCoordinates? = null
  internal var stageHeight by mutableIntStateOf(0)
  internal var movingHeight by mutableIntStateOf(0)
  internal var anchorLeft by mutableIntStateOf(0)
  internal var anchorTop by mutableIntStateOf(0)
  internal var anchorWidth by mutableIntStateOf(0)
  internal var anchorHeight by mutableIntStateOf(0)
  internal var anchorKnown by mutableStateOf(false)
  internal var keepAnchor = false
  /** Whether the surface closes into a pill around the anchor, rather than tightening into a band. */
  internal var pill = false
  internal var panelTopPx = 0
  internal var restingMarginPx = 0
  internal var bandPaddingPx = 0
  internal var liftPx = 0f
  internal var anchorPaddingHorizontalPx = 0
  internal var anchorPaddingVerticalPx = 0
  internal var fraction: () -> Float = { 0f }

  /** 0 at rest, 1 once compact. Only a header that keeps its field compacts; others slide away. */
  internal fun compactProgress(): Float =
    if (keepAnchor && anchorKnown) (fraction() / ScrollChromeMachine.COMPACT).coerceIn(0f, 1f) else 0f

  /**
   * Tucks this part away as the header compacts, in a choreographed order.
   *
   * [order] staggers the parts: 0 leaves first and returns last, so on the way back the header
   * rebuilds itself outward from the search field. A part does not simply fade. It eases toward the
   * field, settles slightly smaller and dissolves, the way a large title condenses into a bar.
   * [belowAnchor] says which side of the field the part sits on, so it always moves toward the field
   * rather than away from it.
   */
  fun Modifier.compactsAway(order: Int = 0, belowAnchor: Boolean = false): Modifier = graphicsLayer {
    val start = order * STAGGER
    val progress = ((compactProgress() - start) / ITEM_SPAN).coerceIn(0f, 1f)
    val eased = HeaderEasing.transform(progress)
    alpha = 1f - eased
    val scale = 1f - 0.06f * eased
    scaleX = scale
    scaleY = scale
    translationY = (if (belowAnchor) -liftPx else liftPx) * eased
    transformOrigin = TransformOrigin(0f, if (belowAnchor) 0f else 1f)
  }

  /** The search field: the part that stays once everything else has gone. */
  fun Modifier.compactAnchor(): Modifier = onPlaced { coordinates ->
    val parent = root ?: return@onPlaced
    if (!parent.isAttached) return@onPlaced
    val position = parent.localPositionOf(coordinates, Offset.Zero)
    anchorLeft = position.x.roundToInt()
    anchorTop = position.y.roundToInt().coerceAtLeast(0)
    anchorWidth = coordinates.size.width
    anchorHeight = coordinates.size.height
    anchorKnown = true
  }

  /** How far the header content has moved up, in pixels. */
  internal fun translation(): Int {
    if (keepAnchor && anchorKnown) {
      val eased = HeaderEasing.transform(compactProgress())
      val restingTop = if (pill) restingMarginPx + anchorPaddingVerticalPx else bandPaddingPx
      return ((panelTopPx + anchorTop - restingTop).coerceAtLeast(0) * eased).roundToInt()
    }
    // No field to keep: the whole header is gone by the compact point, not merely halfway there.
    // Compact is a resting state, and a header resting half off the screen would be a sliver of
    // glass stuck under the status bar.
    val hidden = HeaderEasing.transform((fraction() / ScrollChromeMachine.COMPACT).coerceIn(0f, 1f))
    return (movingHeight * hidden).roundToInt()
  }

  /** The surface's bounds within the stage: the full panel at rest, the field (or a band) once compact. */
  internal fun surfaceBounds(stageWidth: Int, stageHeight: Int): IntRect {
    val full = IntRect(0, 0, stageWidth, stageHeight)
    val progress = compactProgress()
    if (progress <= 0f) return full
    // The surface trails the parts a little, so they have begun to leave before it closes on them.
    val morph = HeaderEasing.transform(((progress - SURFACE_DELAY) / (1f - SURFACE_DELAY)).coerceIn(0f, 1f))
    val compact = if (pill) {
      IntRect(
        (anchorLeft - anchorPaddingHorizontalPx).coerceAtLeast(0),
        (anchorTop - anchorPaddingVerticalPx).coerceAtLeast(0),
        (anchorLeft + anchorWidth + anchorPaddingHorizontalPx).coerceAtMost(stageWidth),
        (anchorTop + anchorHeight + anchorPaddingVerticalPx).coerceAtMost(stageHeight),
      )
    } else {
      IntRect(0, anchorTop - bandPaddingPx, stageWidth, anchorTop + anchorHeight + bandPaddingPx)
    }
    return IntRect(
      lerp(full.left, compact.left, morph),
      lerp(full.top, compact.top, morph),
      lerp(full.right, compact.right, morph),
      lerp(full.bottom, compact.bottom, morph),
    )
  }

  private fun lerp(from: Int, to: Int, t: Float): Int = (from + (to - from) * t).roundToInt()

  internal companion object {
    const val STAGGER = 0.16f
    const val ITEM_SPAN = 0.52f
    const val SURFACE_DELAY = 0.12f

    /** A measured start and a long, soft arrival, in the manner of iOS system motion. */
    val HeaderEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
  }
}

/**
 * The glass panel's outline, rounded in proportion to how far it has closed on the search field.
 *
 * Derived from the outline's own size rather than read from animation state: a clip recomputes its
 * outline whenever its size changes, which is exactly when the morph moves, so the corners and the
 * bounds can never disagree for a frame.
 */
private class HeaderMorphShape(
  private val scope: ScrollAwareHeaderScope,
  private val panelRadius: Dp,
  private val pillRadius: Dp,
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val anchorHeight = (scope.anchorHeight + 2 * scope.anchorPaddingVerticalPx).toFloat()
    val span = scope.stageHeight - anchorHeight
    val openness = if (span <= 0f) 1f else ((size.height - anchorHeight) / span).coerceIn(0f, 1f)
    val radius = with(density) { (pillRadius + (panelRadius - pillRadius) * openness).toPx() }
    return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
  }
}

/**
 * A page header that makes room while the viewer scrolls, and keeps its search field.
 *
 * # What it does
 *
 * Scrolling down, the header's other parts (title, subtitle, filters) condense away in a staggered
 * sequence while the header slides up to leave only the search field at the top. The surface behind
 * follows: in the Modern style the glass panel closes into a floating pill around the field; in the
 * Default style the opaque band tightens to hug it. Scrolling back up plays the same choreography in
 * reverse, rebuilding outward from the field.
 *
 * The part that stays is whatever the content marks as its anchor: the search field on a search page,
 * the title row on a library page. A header with no anchor ([keepAnchorVisible] false) slides away
 * whole.
 *
 * # What it costs
 *
 * Nothing on the page moves: the header's slot keeps its size, so the list underneath never
 * re-measures. Every animated value is read in a placement, layout or graphics-layer lambda (the
 * slide is a re-placement, the morph a re-layout of one surface node, the stagger a layer property),
 * so a scrolled frame recomposes nothing. The moving parts are placed rather than translated,
 * because the glass works out what to blur from where it is placed.
 *
 * Put the clip edge where the header should disappear: [modifier] is applied outside the clip, so a
 * caller passes the status-bar padding there and the header tucks under the status bar rather than
 * sliding over its icons.
 */
@Composable
internal fun ScrollAwareHeader(
  surface: ScrollAwareHeaderSurface,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  keepAnchorVisible: Boolean = false,
  /** Space between the clip edge and the surface. */
  panelPadding: PaddingValues = PaddingValues(0.dp),
  /** A fixed surface height, or null to take the content's own. */
  panelHeight: Dp? = null,
  /** Space between the surface's edge and the content at rest. */
  contentPadding: PaddingValues = PaddingValues(0.dp),
  /**
   * Room the compact glass leaves around the anchor. A search field brings its own container and
   * wants none; a row of title and buttons needs some, or the pill would end at the letters.
   */
  anchorPaddingHorizontal: Dp = 0.dp,
  anchorPaddingVertical: Dp = 0.dp,
  content: @Composable ScrollAwareHeaderScope.() -> Unit,
) {
  val chrome = LocalScrollChrome.current
  val density = LocalDensity.current
  val headerScope = remember { ScrollAwareHeaderScope() }
  val active = enabled && chrome != null
  headerScope.keepAnchor = keepAnchorVisible && active
  headerScope.pill = when (surface) {
    is ScrollAwareHeaderSurface.Glass -> true
    is ScrollAwareHeaderSurface.Solid -> surface.pillAroundAnchor
  }
  with(density) {
    headerScope.panelTopPx = panelPadding.calculateTopPadding().roundToPx()
    headerScope.restingMarginPx = 8.dp.roundToPx()
    headerScope.bandPaddingPx = 10.dp.roundToPx()
    headerScope.liftPx = 10.dp.toPx()
    headerScope.anchorPaddingHorizontalPx = anchorPaddingHorizontal.roundToPx()
    headerScope.anchorPaddingVerticalPx = anchorPaddingVertical.roundToPx()
  }
  headerScope.fraction = if (active) ({ chrome!!.presentedFraction }) else ({ 0f })

  Box(modifier = modifier.clipToBounds()) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .then(if (active) Modifier.offset { IntOffset(0, -headerScope.translation()) } else Modifier)
        .onSizeChanged { headerScope.movingHeight = it.height }
        .padding(panelPadding),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .then(if (panelHeight != null) Modifier.height(panelHeight) else Modifier)
          .onSizeChanged { headerScope.stageHeight = it.height }
          .onPlaced { headerScope.root = it },
      ) {
        Box(
          modifier = Modifier
            .matchParentSize()
            .layout { measurable, constraints ->
              val bounds = headerScope.surfaceBounds(constraints.maxWidth, constraints.maxHeight)
              val placeable = measurable.measure(
                Constraints.fixed(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0)),
              )
              layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(bounds.left, bounds.top) }
            },
        ) {
          when (surface) {
            is ScrollAwareHeaderSurface.Solid -> {
              // A band has square edges across the screen; a pill rounds off as it closes on the field.
              val shape = if (surface.pillAroundAnchor) {
                remember(headerScope) { HeaderMorphShape(headerScope, 0.dp, StreamDekRadius.card) }
              } else {
                RectangleShape
              }
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .clip(shape)
                  .drawBehind {
                    // Read at draw time, so the tint firming up as the band closes costs a redraw only.
                    val progress = if (surface.pillAroundAnchor) headerScope.compactProgress() else 0f
                    val alpha = DefaultHeaderAlpha + (DefaultHeaderPillAlpha - DefaultHeaderAlpha) * progress
                    drawRect(surface.color.copy(alpha = alpha))
                  },
              )
            }
            is ScrollAwareHeaderSurface.Glass -> {
              val lightHeader = MaterialTheme.colorScheme.background.luminance() > 0.5f
              val shape = remember(headerScope) { HeaderMorphShape(headerScope, StreamDekRadius.sheet, StreamDekRadius.card) }
              FrostedGlassSurface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                hazeStateOverride = surface.hazeState,
                blurRadius = 68f,
                tintAlpha = if (lightHeader) 0.14f else 0.06f,
                borderAlpha = if (lightHeader) 0.10f else 0f,
                baseAlpha = if (lightHeader) 0.28f else 0.08f,
                fillColorOverride = if (lightHeader) null else Color.White,
                showEdgeGradient = false,
                contrastZone = GlassContrastZone.TopChrome,
              ) {}
            }
          }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(contentPadding)) { headerScope.content() }
      }
    }
  }
}

/**
 * How opaque a Default header's background is at rest.
 *
 * Not fully opaque: a little of the page reads through, which keeps the flat style from looking like a
 * slab pasted over the content. Not much lower either, because without blur anything clearer lets
 * the page's own text compete with the header's.
 */
internal const val DefaultHeaderAlpha = 0.92f

/** How far the status strip's scrim fades out below the bar. Matches [ChromeStatusBarScrim]. */
private val StatusStripFeather = 14.dp

/**
 * How opaque the pill behind a pinned search field becomes.
 *
 * Never lighter than the resting header. Once compact, the pill is the only thing between the
 * field's placeholder and whatever poster happens to scroll beneath it, and there is no blur to soften
 * that poster, so it has to carry the field's legibility on its own. Kept a separate value so the two
 * can be tuned apart.
 */
internal const val DefaultHeaderPillAlpha = 0.92f

/**
 * The strip behind the status bar above a Default header.
 *
 * At rest it continues the header's own tint up to the top of the screen. When [fadesWithHeader], it
 * fades out as the header condenses — so on a page that keeps only its search field, nothing of the
 * background is left above it — and the status-bar scrim takes over as content scrolls under the
 * clock. A page whose header keeps a full-width band leaves [fadesWithHeader] false and keeps its strip.
 *
 * Its height is exactly the status bar's, whatever it draws, so a header in a sticky list slot never
 * changes size because of it.
 */
@Composable
internal fun DefaultHeaderStatusStrip(color: Color, fadesWithHeader: Boolean, modifier: Modifier = Modifier) {
  val chrome = LocalScrollChrome.current
  val contrast = LocalGlassContrast.current
  // The same ground as the status-bar scrim: deepen toward black in a dark theme, wash toward the page
  // in a light one, so the icons keep whichever contrast the theme gave them.
  val ground = if (color.luminance() > 0.5f) color else Color.Black
  Box(
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsTopHeight(WindowInsets.statusBars)
      .drawBehind {
        val compact = if (fadesWithHeader && chrome != null) {
          (chrome.presentedFraction / ScrollChromeMachine.COMPACT).coerceIn(0f, 1f)
        } else {
          0f
        }
        val tint = DefaultHeaderAlpha * (1f - compact)
        if (tint > 0.005f) drawRect(color.copy(alpha = tint))
        val scrim = if (fadesWithHeader) (contrast?.statusBar?.value ?: 0f) * compact else 0f
        if (scrim > 0.005f) {
          // Feathered past the bottom of the bar, as [ChromeStatusBarScrim] is. Stopping the gradient
          // at the strip's own edge left it at over half strength there, which read as a hard line
          // across the page. The strip cannot grow to hold the feather — in a sticky list slot that
          // would move the header — so it draws past its bounds instead.
          val feather = StatusStripFeather.toPx()
          val total = size.height + feather
          drawRect(
            brush = Brush.verticalGradient(
              0f to ground.copy(alpha = 0.86f * scrim),
              (size.height / total) to ground.copy(alpha = 0.58f * scrim),
              1f to Color.Transparent,
              startY = 0f,
              endY = total,
            ),
            size = androidx.compose.ui.geometry.Size(size.width, total),
          )
        }
      },
  )
}
