package net.streamdek.mobile.nativeapp

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.streamdek.mobile.R

/**
 * The "Who's watching?" page.
 *
 * Split out of `StreamDekNativeApp.kt` because it is a whole screen with its own timing model, and
 * that timing is the point of the file: hero artwork, hero copy, the headline, the avatars and the
 * Manage Profiles button are all driven by *one* clock rather than each animating itself the moment
 * its own data happens to land. See [ProfilePickerReveal].
 */

// ---------------------------------------------------------------------------------------------
// Entrance choreography
// ---------------------------------------------------------------------------------------------

/**
 * When each part of the page arrives, in milliseconds from the start of the reveal.
 *
 * These are cues on a shared timeline, not per-element animation specs, and they are written as
 * absolute times precisely so the overlaps are visible in one place: the title starts while the
 * artwork is still settling, the headline while the synopsis is still arriving. That overlap is
 * what makes the page read as one scene assembling rather than as six things taking turns.
 */
private object PickerCue {
  const val HeroStart = 0f
  const val HeroDuration = 540f

  const val LabelStart = 210f
  const val TitleStart = 280f
  const val SynopsisStart = 350f
  const val HeroTextDuration = 330f

  const val HeadingStart = 420f
  const val HeadingDuration = 330f

  const val CardsStart = 510f
  const val CardDuration = 350f
  const val CardStagger = 65f

  /**
   * Past this many avatars the stagger stops growing. A household with nine profiles should not
   * wait half a second longer for its last one than a household with two; the point of the stagger
   * is direction of travel, and four steps is enough to read it.
   */
  const val MaxStaggeredCards = 4

  const val ManageStart = 830f
  const val ManageDuration = 330f

  /**
   * The whole reveal at [AnimationSpeed.Standard].
   *
   * Every number above is a base value, multiplied by the viewer's chosen scale - so Fast lands
   * this around 810ms and Cinematic around 1.7s, on the same cues in the same order.
   *
   * Standard was 980ms and is now a little longer. At the shorter length the stagger was closer to
   * a flicker than to a sequence: you could see that the avatars had not all arrived together but
   * not really watch them arrive. The extra ~180ms is entirely in the decorative half - the
   * profiles are still interactive from the moment they are composed, and the first touch lands the
   * whole thing instantly - so nothing about the page got slower to use.
   */
  const val Total = 1160f

  /**
   * Motion off: one short crossfade for the entire page, no travel and no scale.
   *
   * Twice [MotionDuration.motionlessCrossfade] rather than one, because the elements still overlap
   * on this timeline - it is a single fade for the page, not six of them end to end.
   */
  const val MotionlessTotal = MotionDuration.motionlessCrossfade * 2f

  /**
   * Everything was already in hand when the page opened, so the same sequence plays faster. It is
   * not a different animation - a returning viewer should still recognise the page assembling.
   */
  const val CachedPace = 0.62f
}

/**
 * How long the page will wait on decorative artwork before starting without it.
 *
 * The hero is a backdrop, not content: nobody opens this page to look at it. If the catalog or the
 * image is slow, the profiles appear on the dark background at this point and the artwork fades in
 * behind them later, against a layout whose dimensions were already reserved.
 */
private const val ProfilePickerHeroGraceMs = 700L

/**
 * The outside edge of the loading state, measured from the page appearing.
 *
 * [ProfilePickerHeroGraceMs] is counted from the moment the profiles land, which is the moment the
 * page could otherwise have opened - but on a slow sign-in that would stack one wait on top of
 * another. This caps the pair of them, so the dark screen has an end regardless of what is late.
 */
private const val ProfilePickerRevealDeadlineMs = 1200L

/**
 * Below this, nothing was actually waited for - the profiles and the artwork were already cached -
 * so the reveal runs at [PickerCue.CachedPace]. Measured from the page appearing, so a fast
 * network gets the short version too; there is no artificial delay either way.
 */
private const val ProfilePickerCachedEntryMs = 260L

/**
 * One clock for the whole page.
 *
 * Every animated part of the picker asks this object where it is instead of owning an animation.
 * That buys three things the previous version could not have:
 *
 * - **Nothing can restart on its own.** The clock is created when the page enters composition and
 *   started once; an image finishing, a hero rotating, or any state change recomposing the screen
 *   cannot rewind it, because none of them own it.
 * - **No per-frame recomposition.** [progress] is read inside `graphicsLayer` lambdas, where the
 *   state read is deferred to layer-update time, so the whole reveal recomposes nothing.
 * - **Skipping is one call.** [skip] snaps the clock to the end and every element lands at once,
 *   which is what the page owes anyone who taps before it has finished being pretty.
 */
@Stable
internal class ProfilePickerReveal(
  private val scope: CoroutineScope,
  /**
   * The viewer's animation speed together with the system's reduce-motion request, from
   * [LocalMotionSettings].
   *
   * Captured once, at entry. This timeline is a single choreographed run, and re-length-ing it
   * halfway through would be exactly the animation restart the page must not have - so a selection
   * changed while the picker is open applies the next time it is opened.
   */
  private val motion: MotionSettings,
) {
  private val clock = Animatable(0f)

  /** Movement and scale are replaced by a plain crossfade when motion is off. */
  val reduced: Boolean get() = motion.motionless

  /**
   * Not snapshot state on purpose: it is assigned once, before [started] flips, and every read of
   * it happens inside a layer block that is already re-running because the clock moved.
   */
  private var pace: Float = 1f

  var started by mutableStateOf(false)
    private set

  private val totalMs: Float get() = if (reduced) PickerCue.MotionlessTotal else PickerCue.Total * pace

  fun start(cached: Boolean) {
    if (started) return
    // Two multipliers doing different jobs, and both apply: the speed the viewer chose, and the
    // shorter run a page that had nothing to wait for has earned.
    pace = motion.scale * if (cached) PickerCue.CachedPace else 1f
    started = true
    val duration = totalMs
    scope.launch {
      clock.animateTo(duration, tween(durationMillis = duration.toInt(), easing = LinearEasing))
    }
  }

  /** Land everything immediately. Called on the first touch anywhere on the page. */
  fun skip() {
    // Not `clock.isRunning`: for a frame or two after [start] the animation has been launched but
    // has not begun, and a skip in that window would otherwise be dropped and let the full
    // sequence play out under the finger. The snap is queued behind the animation on the same
    // dispatcher, and Animatable lets the later mutation cancel the earlier one.
    if (!started || clock.value >= totalMs) return
    scope.launch { clock.snapTo(totalMs) }
  }

  /**
   * Eased 0..1 progress of one element's slice of the shared clock.
   *
   * The clock itself runs linearly; the easing lives here so each element decelerates into place on
   * its own curve while still being positioned by one timeline.
   */
  fun progress(startMs: Float, durationMs: Float): Float {
    if (!started) return 0f
    val raw = if (reduced) {
      clock.value / PickerCue.MotionlessTotal
    } else {
      (clock.value - startMs * pace) / (durationMs * pace)
    }
    return StreamDekMotion.enter.transform(raw.coerceIn(0f, 1f))
  }

  /** Whether an element's entrance has carried far enough to be worth handing focus to. */
  fun settled(startMs: Float, durationMs: Float): Boolean = progress(startMs, durationMs) >= 0.7f
}

@Composable
private fun rememberProfilePickerReveal(): ProfilePickerReveal {
  val scope = rememberCoroutineScope()
  val motion = LocalMotionSettings.current
  return remember(scope) { ProfilePickerReveal(scope, motion) }
}

/** Opacity, a small upward settle and an optional scale, all read from the page's single clock. */
private fun Modifier.pickerCue(
  reveal: ProfilePickerReveal,
  startMs: Float,
  durationMs: Float,
  rise: Dp = 14.dp,
  fromScale: Float = 1f,
): Modifier = graphicsLayer {
  val progress = reveal.progress(startMs, durationMs)
  alpha = progress
  if (!reveal.reduced) {
    translationY = rise.toPx() * (1f - progress)
    if (fromScale != 1f) {
      val scale = fromScale + (1f - fromScale) * progress
      scaleX = scale
      scaleY = scale
    }
  }
}

/** The cue for the nth avatar in the row, Add Profile included as simply the last one. */
private fun profileCardCue(index: Int): Float =
  PickerCue.CardsStart + PickerCue.CardStagger * index.coerceAtMost(PickerCue.MaxStaggeredCards)

/**
 * What the page shows while it is loading: the app's own background, and a slow breath of light
 * behind where the artwork and the avatars are about to be.
 *
 * Deliberately not a spinner. A spinner under a headline announces that something is missing;
 * this reads as the page being dark rather than the page being broken, and it is the only thing
 * that has to be replaced when the real content arrives - so there is nothing to shift.
 */
@Composable
private fun ProfilePickerLoadingVeil(modifier: Modifier = Modifier, alpha: () -> Float) {
  val reduced = LocalReducedMotion.current
  val breath = if (reduced) {
    null
  } else {
    rememberInfiniteTransition(label = "picker_loading_glow").animateFloat(
      initialValue = 0.34f,
      targetValue = 0.72f,
      animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "picker_loading_breath",
    )
  }
  val tint = MaterialTheme.colorScheme.onBackground
  Box(
    modifier = modifier
      .fillMaxSize()
      .graphicsLayer { this.alpha = alpha() }
      .drawBehind {
        val strength = breath?.value ?: 0.5f
        drawRect(
          Brush.radialGradient(
            colors = listOf(tint.copy(alpha = 0.11f * strength), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.32f),
            radius = size.maxDimension * 0.70f,
          ),
        )
        drawRect(
          Brush.radialGradient(
            colors = listOf(tint.copy(alpha = 0.07f * strength), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.84f),
            radius = size.maxDimension * 0.52f,
          ),
        )
      },
  )
}

// ---------------------------------------------------------------------------------------------
// The page
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ProfilePickerScreen(
  profiles: List<StreamProfile>,
  profilesLoading: Boolean,
  /** Home's catalog, which the hero is drawn from. Empty until it lands; the page copes. */
  heroSections: List<MediaSection>,
  pinPromptProfileId: String?,
  onProfileSelected: (String) -> Unit,
  onSubmitProfilePin: (String) -> Unit,
  onCancelProfilePin: () -> Unit,
  onManageProfiles: () -> Unit,
  onOpenProfileManager: () -> Unit,
) {
  val context = LocalContext.current
  val motion = LocalMotionSettings.current
  val reveal = rememberProfilePickerReveal()
  val pickerMetrics = profilePickerMetrics()
  val profileHazeState = rememberHazeState()
  val lightProfile = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val profileForeground = MaterialTheme.colorScheme.onBackground

  val heroItems = remember(heroSections) { profileSwitcherHeroItems(heroSections) }
  var heroIndex by rememberSaveable(heroItems.map { it.id }.joinToString("|")) { mutableStateOf(0) }
  val heroItem = heroItems.getOrNull(heroIndex.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0)))
  var pin by rememberSaveable(pinPromptProfileId) { mutableStateOf("") }

  // Whether the artwork has finished doing whatever it is going to do. Failure counts: the page
  // waits for an answer, not for a success, so a dead image URL costs no more than a slow one.
  var heroArtworkSettled by remember { mutableStateOf(false) }
  var heroArtworkVisible by remember { mutableStateOf(false) }

  // The hero's own fade, separate from the timeline, for the case where the artwork lost the race
  // and arrives after the profiles are already on screen. It fades in behind a layout whose
  // dimensions were reserved from the first frame, so nothing below it moves.
  val heroGate = remember { Animatable(0f) }
  LaunchedEffect(heroArtworkVisible) {
    if (!heroArtworkVisible) return@LaunchedEffect
    if (!reveal.started || reveal.reduced) heroGate.snapTo(1f)
    else heroGate.animateTo(1f, tween(durationMillis = motion.scaled(MotionDuration.long), easing = StreamDekMotion.enter))
  }

  // The one decision this page makes about timing: when the scene is worth showing at all.
  //
  // Profiles are the content and are waited for unconditionally. Artwork is decoration and gets a
  // grace period from the moment the profiles land - the moment the page could otherwise have
  // opened - bounded by an absolute deadline so the two waits cannot stack.
  val enteredAt = remember { SystemClock.uptimeMillis() }
  LaunchedEffect(profilesLoading, heroArtworkSettled, heroItem != null) {
    if (reveal.started || profilesLoading) return@LaunchedEffect
    // `heroItem != null` matters as much as `heroArtworkSettled`. The grace below waits on the
    // AsyncImage callbacks, and those only exist while there is a hero to draw — with no catalog
    // yet there is no image request in flight, so nothing could ever settle and the page sat out
    // the whole deadline waiting for something it had not asked for. On a cold launch that is
    // always the case: the hero is drawn from Home's sections, which land a second or two after
    // the profiles do. Wait only when there is genuinely an image on its way.
    if (!heroArtworkSettled && heroItem != null) {
      // Evaluated on the pass where `profilesLoading` first went false, so this is the time the
      // profiles became ready rather than the time of some later recomposition.
      val profilesReadyAt = SystemClock.uptimeMillis()
      val deadline = minOf(profilesReadyAt + ProfilePickerHeroGraceMs, enteredAt + ProfilePickerRevealDeadlineMs)
      // Cancelled and re-entered if the artwork settles during the wait, which is how the common
      // case skips straight past this without polling for anything.
      val remaining = deadline - profilesReadyAt
      if (remaining > 0L) delay(remaining)
    }
    // The end of the dark screen a viewer sees at launch, so the two waits behind it — the profile
    // fetch and the artwork grace — can be told apart in a trace rather than guessed at.
    Perf.startupMark("picker.revealed", "heroArtwork=$heroArtworkSettled")
    reveal.start(cached = SystemClock.uptimeMillis() - enteredAt <= ProfilePickerCachedEntryMs)
  }

  // Rotation is part of the page at rest, not part of its entrance: starting it earlier would let
  // the hero change out from under a reveal that is still fading the first one in.
  LaunchedEffect(heroItems.size, heroIndex, reveal.started) {
    if (heroItems.size <= 1 || !reveal.started) return@LaunchedEffect
    delay(3500)
    heroIndex = (heroIndex + 1) % heroItems.size
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      // A touch anywhere lands everything at once. Observed on the initial pass and never
      // consumed, so the tap it belongs to still reaches whatever it was aimed at - by the time
      // the finger lifts, the thing under it is fully drawn.
      .pointerInput(reveal) {
        awaitPointerEventScope {
          while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            reveal.skip()
          }
        }
      },
  ) {
    AnimatedContent(
      targetState = heroItem,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .height(pickerMetrics.heroHeight)
        .graphicsLayer {
          alpha = reveal.progress(PickerCue.HeroStart, PickerCue.HeroDuration) * heroGate.value
        },
      transitionSpec = {
        // The reveal owns the first appearance; only the 3.5s rotation crossfades here. Without
        // this the artwork arriving would fire its own 720ms fade on top of the timeline's, which
        // is exactly the disconnected double-transition the page used to have.
        if (initialState == null) {
          fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
        } else {
          // Three crossfades long. A hero that sits for three and a half seconds should change
          // over languidly rather than at the speed of a badge, and expressing that as a multiple
          // of the shared token keeps it moving with the viewer's setting instead of being a
          // number of its own.
          val rotation = motion.crossfade(MotionDuration.crossfade * 3)
          fadeIn(animationSpec = tween(rotation)) togetherWith fadeOut(animationSpec = tween(rotation))
        }
      },
      label = "profile_hero_crossfade",
    ) { currentHero ->
      if (currentHero == null) {
        Box(modifier = Modifier.fillMaxSize())
      } else {
        Box(modifier = Modifier.fillMaxSize()) {
          AsyncImage(
            model = ImageRequest.Builder(context)
              .data(currentHero.backdrop ?: currentHero.poster)
              .size(1280, 720)
              .memoryCachePolicy(CachePolicy.ENABLED)
              .diskCachePolicy(CachePolicy.ENABLED)
              // Coil's own crossfade would be a second, unsynchronised opinion about when this
              // image should appear. The timeline above is the only one.
              .crossfade(false)
              .build(),
            contentDescription = currentHero.title,
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                // Settles down from slightly overscaled instead of snapping to its final framing.
                val settle = reveal.progress(PickerCue.HeroStart, PickerCue.HeroDuration)
                val scale = if (reveal.reduced) 1f else 1f + 0.055f * (1f - settle)
                scaleX = scale
                scaleY = scale
              }
              .hazeSource(profileHazeState),
            contentScale = ContentScale.Crop,
            onSuccess = { heroArtworkVisible = true; heroArtworkSettled = true },
            onError = { heroArtworkSettled = true },
          )
          ProfileHeroGlassPane(
            // Grows with heroHeight rather than staying at the 220dp it used to be. The pane is
            // anchored to the bottom of the hero, so a taller hero would otherwise carry the
            // synopsis down into the "Who's watching?" headline; keeping the difference constant
            // holds the text where it was and simply extends the glass further down the image.
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(240.dp),
            hazeState = profileHazeState,
          ) {
            Text(
              stringResource(R.string.home_trending_now),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White.copy(alpha = 0.76f),
              modifier = Modifier.pickerCue(reveal, PickerCue.LabelStart, PickerCue.HeroTextDuration, rise = 10.dp),
            )
            Text(
              currentHero.title,
              style = MaterialTheme.typography.displaySmall,
              fontWeight = FontWeight.Black,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              color = Color.White,
              modifier = Modifier.pickerCue(reveal, PickerCue.TitleStart, PickerCue.HeroTextDuration, rise = 12.dp),
            )
            Text(
              currentHero.description.ifBlank { currentHero.year ?: "" },
              style = MaterialTheme.typography.titleMedium,
              color = Color.White.copy(alpha = 0.80f),
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.pickerCue(reveal, PickerCue.SynopsisStart, PickerCue.HeroTextDuration, rise = 12.dp),
            )
          }
        }
      }
    }

    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(pickerMetrics.scrimHeight)
        // Part of the background foundation, so it arrives with the artwork rather than after it -
        // there is never a frame where the image has a hard edge above the avatars.
        .graphicsLayer { alpha = reveal.progress(PickerCue.HeroStart, PickerCue.HeroDuration) }
        .background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              // This scrim is what the avatars and the headline sit on, and it is painted over the
              // glass pane above it - so in the light theme it was washing the bottom half of the
              // hero with the page background. It now holds off for its first sixth and then comes
              // up quickly, reaching the same opacity by the time the headline starts. The glass
              // keeps more of its height clean; the text underneath is backed just as solidly.
              0.00f to Color.Transparent,
              0.14f to MaterialTheme.colorScheme.background.copy(alpha = 0.22f),
              0.30f to MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
              1.00f to MaterialTheme.colorScheme.background,
            ),
          ),
        ),
    )

    // Composed only once the profile list is final, so every dimension below is the one it will
    // keep. Nothing here is measured against placeholder content and then re-measured.
    if (reveal.started) {
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(horizontal = pickerMetrics.contentPadding, vertical = 58.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
      ) {
        Text(
          stringResource(R.string.profiles_who_is_watching),
          style = MaterialTheme.typography.displaySmall.let {
            if (pickerMetrics.headlineScale == 1f) it else it.copy(fontSize = it.fontSize * pickerMetrics.headlineScale)
          },
          fontWeight = FontWeight.Black,
          color = profileForeground,
          modifier = Modifier.pickerCue(reveal, PickerCue.HeadingStart, PickerCue.HeadingDuration, rise = 16.dp),
        )
        LazyRow(
          // Centred within whatever width is available: a handful of profiles on a wide screen
          // should sit in the middle of it rather than hugging the leading edge, while a long list
          // still scrolls exactly as before.
          horizontalArrangement = Arrangement.spacedBy(pickerMetrics.avatarSpacing, Alignment.CenterHorizontally),
          contentPadding = PaddingValues(horizontal = 4.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
            ProfilePickerAvatar(
              profile = profile,
              avatarSize = pickerMetrics.avatarSize,
              modifier = Modifier.pickerCue(reveal, profileCardCue(index), PickerCue.CardDuration, rise = 18.dp, fromScale = 0.92f),
              onClick = { onProfileSelected(profile.id) },
            )
          }
          item {
            // Last in the same stagger rather than an afterthought: it is one of the things you
            // can choose here, and it should arrive as part of the row it belongs to.
            AddProfileAvatar(
              avatarSize = pickerMetrics.avatarSize,
              modifier = Modifier.pickerCue(reveal, profileCardCue(profiles.size), PickerCue.CardDuration, rise = 18.dp, fromScale = 0.92f),
              onClick = onManageProfiles,
            )
          }
        }
        Button(
          onClick = onOpenProfileManager,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (lightProfile) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF171717),
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
          shape = StreamDekRadius.pill,
          modifier = Modifier.pickerCue(reveal, PickerCue.ManageStart, PickerCue.ManageDuration, rise = 10.dp),
        ) {
          Text(stringResource(R.string.profiles_manage), fontWeight = FontWeight.Bold)
        }
      }
    }

    ProfilePickerLoadingVeil(
      alpha = {
        // The inverse of the artwork's own cue, so the glow is gone by the time there is anything
        // to look through it at. Both sides of the handover are the same clock.
        1f - reveal.progress(PickerCue.HeroStart, PickerCue.HeroDuration * 0.6f)
      },
    )

    pinPromptProfileId?.let { profileId ->
      val profile = profiles.firstOrNull { it.id == profileId }
      ProfilePinPadScreen(
        profile = profile,
        pin = pin,
        onPinChange = { updated -> pin = updated.filter(Char::isDigit).take(4) },
        onSubmit = {
          if (pin.length == 4) {
            onSubmitProfilePin(pin)
            pin = ""
          }
        },
        onBack = { pin = ""; onCancelProfilePin() },
      )
    }
  }
}

@Composable
private fun ProfileHeroGlassPane(
  modifier: Modifier = Modifier,
  hazeState: HazeState,
  content: @Composable ColumnScope.() -> Unit,
) {
  FrostedGlassSurface(
    modifier = modifier,
    shape = RectangleShape,
    hazeStateOverride = hazeState,
    blurRadius = 68f,
    // The pane used to add a white fill on top of an already white-tinted blur - a 28% tint inside
    // the blur, then 8% base, then a 6% gradient. Stacked over the light theme's scrim below it,
    // that was enough to turn the hero into flat grey. What is left is the blur itself, which is
    // the part that actually reads as glass.
    tintAlpha = 0f,
    borderAlpha = 0f,
    baseAlpha = 0.02f,
    showEdgeGradient = false,
    hazeTintAlphaOverride = 0.08f,
  ) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              // Still darkening downward, because the title and synopsis are white and sit on
              // whatever the backdrop happens to be - but far less of it, so the blurred artwork
              // stays visible through the pane instead of being buried.
              0.00f to Color.Black.copy(alpha = 0.06f),
              0.45f to Color.Black.copy(alpha = 0.24f),
              0.80f to Color.Black.copy(alpha = 0.58f),
              1.00f to Color.Black.copy(alpha = 0.88f),
            ),
          ),
        ),
    )
    Column(
      // The synopsis is prose, and prose set across the full width of a tablet is genuinely harder
      // to read — the eye loses the start of the next line. Capping it leaves the artwork spanning
      // the whole pane while the text stays a comfortable column against the leading edge.
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = LocalStreamDekSpacing.current.readableContentWidth)
        .padding(horizontal = 24.dp, vertical = 22.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

@Composable
private fun ProfilePickerAvatar(
  profile: StreamProfile,
  avatarSize: Dp = 92.dp,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  // The lock badge and its icon are sized against the avatar so the proportions hold as it grows.
  val badgeSize = avatarSize * 0.28f
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
    modifier = modifier.width(avatarSize + 8.dp).clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier
        .size(avatarSize + 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(avatarSize)
          .clip(CircleShape)
          // Drawn under the avatar art, which is what an avatar that fails to decode falls back
          // to: the profile's colour in the right circle, no gap and no broken-image glyph.
          .background(profileAvatarColor(profile.avatarIndex)),
      ) {
        ProfileAvatarImage(avatarIndex = profile.avatarIndex, modifier = Modifier.fillMaxSize())
      }
      if (profile.hasPinSet) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(badgeSize)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Color.Black, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.profiles_pin_protected), tint = Color.Black, modifier = Modifier.size(badgeSize * 0.62f))
        }
      }
    }
    Text(
      profile.name,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

@Composable
private fun AddProfileAvatar(avatarSize: Dp = 92.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
    modifier = modifier.width(avatarSize + 20.dp).clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier
        .size(avatarSize)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        .border(2.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f))
    }
    Text(stringResource(R.string.profiles_add), maxLines = 1, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
  }
}

// ---------------------------------------------------------------------------------------------
// Shared profile chrome: avatars, the PIN pad, and the hand-off into Home
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ProfileHomeTransitionOverlay(profile: StreamProfile?) {
  val pulse = rememberInfiniteTransition(label = "profile_home_transition")
  val scale by pulse.animateFloat(
    initialValue = 0.92f,
    targetValue = 1.06f,
    animationSpec = infiniteRepeatable(tween(760, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "profile_avatar_pulse",
  )

  Box(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
      Box(
        modifier = Modifier
          .size(108.dp)
          .graphicsLayer { scaleX = scale; scaleY = scale }
          .clip(CircleShape)
          .background(profileAvatarColor(profile?.avatarIndex ?: 0))
          .border(3.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f), CircleShape),
      ) {
        ProfileAvatarImage(profile?.avatarIndex ?: 0, Modifier.fillMaxSize())
      }
      Text(stringResource(R.string.profiles_loading_name, profile?.name ?: stringResource(R.string.profiles_your_profile)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
      Text(stringResource(R.string.profiles_preparing_home), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f))
      LinearProgressIndicator(
        modifier = Modifier.width(180.dp).clip(StreamDekRadius.pill),
        color = MaterialTheme.colorScheme.onBackground,
        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
      )
    }
  }
}

internal fun profileAvatarResId(index: Int): Int {
  val avatars = listOf(
    R.drawable.profile_avatar_1,
    R.drawable.profile_avatar_2,
    R.drawable.profile_avatar_3,
    R.drawable.profile_avatar_4,
    R.drawable.profile_avatar_5,
    R.drawable.profile_avatar_6,
    R.drawable.profile_avatar_7,
    R.drawable.profile_avatar_8,
    R.drawable.profile_avatar_9,
    R.drawable.profile_avatar_10,
    R.drawable.profile_avatar_11,
    R.drawable.profile_avatar_12,
  )
  return avatars[index.floorMod(avatars.size)]
}

@Composable
internal fun ProfilePinPadScreen(
  profile: StreamProfile?,
  pin: String,
  onPinChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  LaunchedEffect(pin) {
    if (pin.length == 4) onSubmit()
  }
  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    GlassCircleButton(
      onClick = onBack,
      modifier = Modifier
        .align(Alignment.TopStart)
        .statusBarsPadding()
        .padding(start = 24.dp, top = 34.dp)
        .size(54.dp),
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(34.dp))
    }
    Column(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(horizontal = 34.dp)
        .offset(y = 178.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Box(
        modifier = Modifier
          .size(104.dp)
          .clip(CircleShape)
          .background(profileAvatarColor(profile?.avatarIndex ?: 0)),
        contentAlignment = Alignment.Center,
      ) {
        if (profile != null) ProfileAvatarImage(avatarIndex = profile.avatarIndex, modifier = Modifier.fillMaxSize())
      }
      Text(profile?.name ?: "Profile", color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
      Text(stringResource(R.string.profiles_enter_your_pin), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f), fontSize = 20.sp, lineHeight = 24.sp)
      Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(top = 14.dp)) {
        repeat(4) { index ->
          Box(
            modifier = Modifier
              .size(16.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (index < pin.length) 0.92f else 0.22f)),
          )
        }
      }
      Column(
        modifier = Modifier.padding(top = 36.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "delete")).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            row.forEach { key ->
              PinPadButton(
                label = key,
                onClick = {
                  when {
                    key.isBlank() -> Unit
                    key == "delete" -> onPinChange(pin.dropLast(1))
                    pin.length < 4 -> onPinChange(pin + key)
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PinPadButton(label: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .size(width = 64.dp, height = 47.dp)
      .clip(StreamDekRadius.thumbShape)
      .background(if (label.isBlank()) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
      .clickable(enabled = label.isNotBlank(), onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (label == "delete") {
      Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = stringResource(R.string.a11y_delete_digit), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    } else {
      Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
    }
  }
}

@Composable
internal fun ProfileAvatarImage(avatarIndex: Int, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(profileAvatarResId(avatarIndex)),
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.Crop,
  )
}

@Composable
internal fun ProfileAvatarPicker(selectedAvatarIndex: Int, onSelect: (Int) -> Unit) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
    items((0 until 12).toList(), key = { "profile-avatar-$it" }) { index ->
      Box(
        modifier = Modifier
          .size(58.dp)
          .clip(CircleShape)
          .background(profileAvatarColor(index))
          .border(2.dp, if (selectedAvatarIndex == index) Color.White else Color.Transparent, CircleShape)
          .clickable { onSelect(index) },
        contentAlignment = Alignment.Center,
      ) {
        ProfileAvatarImage(avatarIndex = index, modifier = Modifier.fillMaxSize())
        if (selectedAvatarIndex == index) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).size(18.dp))
        }
      }
    }
  }
}

internal fun profileAvatarColor(index: Int): Color {
  val colors = listOf(
    Color(0xFFE51E2A),
    Color(0xFFF4D81E),
    Color(0xFF1367F2),
    Color(0xFF16A34A),
    Color(0xFFB91C1C),
    Color(0xFF7C3AED),
  )
  return colors[index.floorMod(colors.size)]
}

internal fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

internal fun profileSwitcherHeroItems(sections: List<MediaSection>): List<MediaItem> {
  // Preferred sources first, then any remaining sections as a fallback, so the
  // hero keeps working even when the user disables or re-arranges builtin rows.
  val preferred = listOf("trending_movies", "trending_series", "new_movies", "in_theatres", "new_series")
    .flatMap { sectionId -> sections.firstOrNull { it.id == sectionId }?.items.orEmpty() }
  val fallback = sections.flatMap { it.items }
  return (preferred + fallback)
    .filter { item -> item.type == "movie" || isSeriesType(item.type) }
    .filter { item -> !item.backdrop.isNullOrBlank() || !item.poster.isNullOrBlank() }
    .distinctBy { item -> "${item.type}:${item.id}" }
    .take(12)
}
