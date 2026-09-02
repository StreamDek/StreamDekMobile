package net.streamdek.mobile.nativeapp

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * The shape and motion half of the design system. [StreamDekSpacing] in `AdaptiveLayout.kt` already
 * owns the measurements that change with window width; this owns the ones that do not.
 *
 * The app grew nineteen distinct corner radii - 5, 6, 7, 8, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24,
 * 26, 28, 30, 32 and pill - which is far more than anyone can hold in their head, so neighbouring
 * surfaces disagreed by a dp or two for no reason anybody chose. These names collapse that into one
 * six-step scale, and they are named for the *role* rather than the number: a card is
 * `StreamDekRadius.card` whatever that turns out to measure, so the whole app moves together when
 * the number changes.
 */
object StreamDekRadius {
  /** Badges, counts, tiny status tags. */
  val badge = 8.dp
  /** Buttons, text fields, chips, segmented controls. */
  val control = 12.dp
  /** Thumbnails, list rows, inline artwork. */
  val thumb = 16.dp
  /** Media cards and content cards - the app's most common surface. */
  val card = 20.dp
  /** Dialogs, menus, and the larger panels inside a page. */
  val panel = 26.dp
  /** Bottom sheets and hero surfaces: the biggest things that still have corners. */
  val sheet = 32.dp

  val badgeShape = RoundedCornerShape(badge)
  val controlShape = RoundedCornerShape(control)
  val thumbShape = RoundedCornerShape(thumb)
  val cardShape = RoundedCornerShape(card)
  val panelShape = RoundedCornerShape(panel)
  val sheetShape = RoundedCornerShape(sheet)

  /** Fully rounded. A large number rather than 50% so it survives non-square bounds. */
  val pill = RoundedCornerShape(999.dp)

  /** A sheet that rises from the bottom edge: round the top only, leave the screen edge square. */
  val bottomSheetShape = RoundedCornerShape(topStart = sheet, topEnd = sheet)
  /** A panel anchored to the top edge, e.g. an expanding search surface. */
  val topSheetShape = RoundedCornerShape(bottomStart = sheet, bottomEnd = sheet)
}

/**
 * Whether motion is off, for whatever reason.
 *
 * Two things can switch it on: the device's own reduce-motion request, and the viewer choosing
 * [AnimationSpeed.Off] in Settings. Almost nothing needs to know which - both mean "still change
 * state, just do not animate the change" - so this stays a plain boolean, and [LocalMotionSettings]
 * carries the detail for the few places that do (Settings itself, mainly, which has to say when the
 * system is overruling a selection rather than quietly ignoring it).
 */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * The app's dark colour scheme, in the viewer's chosen theme, whatever Appearance is set to.
 *
 * For the surfaces that are dark in every appearance because they are not pages: the video player
 * sits on the picture itself, so its controls are white on black in Light Mode exactly as they are
 * in Dark. A Material control dropped onto one of those takes its colours from
 * `MaterialTheme.colorScheme`, which in Light Mode is a light scheme — that is how the player's
 * progress bar came out as a bright white track laid over the video.
 *
 * Providing the dark scheme here means such a control can be wrapped in
 * `MaterialTheme(colorScheme = LocalDarkColorScheme.current)` and get precisely the treatment Dark
 * Mode already gives it, rather than a second set of hand-picked colours that has to be kept in
 * step with the first. Null only outside the app's theme (a preview, a dialog in its own window),
 * where the caller should fall back to the ambient scheme.
 */
val LocalDarkColorScheme: ProvidableCompositionLocal<androidx.compose.material3.ColorScheme?> =
  staticCompositionLocalOf { null }

/**
 * The device's own reduce-motion request.
 *
 * Android has no single "reduce motion" switch the way iOS does. What it has is the developer-
 * options animator duration scale, which is also what accessibility services and battery savers
 * turn down, and which users who are motion sensitive are widely advised to set to zero. Reading it
 * is the closest honest signal available.
 */
@Composable
fun rememberReducedMotion(): Boolean {
  val context = LocalContext.current
  return remember(context) {
    runCatching {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f) == 0f
  }
}

/** The viewer's chosen speed, combined with the device setting that can overrule it. */
@Composable
fun rememberMotionSettings(speed: AnimationSpeed): MotionSettings {
  val systemReduced = rememberReducedMotion()
  return remember(speed, systemReduced) {
    MotionSettings(speed = speed, systemReducedMotion = systemReduced)
  }
}

/**
 * Curves for everything that moves, and the bridge from [MotionDuration] to an animation spec.
 *
 * The durations themselves live in [MotionDuration] and are shared with the television app; this
 * object owns the curves and the Compose plumbing, which are not.
 *
 * Every accessor takes the reduced-motion flag and collapses to a zero-duration spec when it is
 * set. Callers therefore get accessibility for free instead of each having to remember it. Prefer
 * the `@Composable` accessors below, which additionally apply the viewer's [AnimationSpeed] without
 * the caller having to thread it through.
 */
object StreamDekMotion {
  /**
   * Press feedback, ripples, selection ticks - fast enough to feel like a direct response.
   *
   * These three are aliases of the shared tokens rather than numbers of their own, so the phone and
   * the television cannot drift apart on what "the default duration" is.
   */
  const val fast = MotionDuration.short
  /** The default: content appearing, expanding, cross-fading, most state changes. */
  const val normal = MotionDuration.standard
  /** Whole screens, bottom sheets, the player - larger travel needs longer to stay legible. */
  const val slow = MotionDuration.long

  /** Decelerate. For things entering the screen, which should arrive settled rather than land hard. */
  val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
  /** Accelerate. For things leaving, which need not be watched all the way out. */
  val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
  /** Symmetric. For things moving within the screen while staying visible. */
  val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
  /** Kept for the few places that were already using the platform curve. */
  val legacy: Easing = LinearOutSlowInEasing

  /**
   * Fully qualified on the right-hand side, and it has to stay that way.
   *
   * Inside this object the bare name `tween` resolves to this function rather than to the imported
   * `androidx.compose.animation.core.tween`: every argument on that overload has a default too, so
   * the call compiles perfectly and then recurses until the stack runs out. The two springs below
   * were written with the same trap in them.
   */
  fun <T> tween(durationMillis: Int = normal, easing: Easing = standard, reduced: Boolean = false):
    FiniteAnimationSpec<T> = androidx.compose.animation.core.tween(durationMillis = if (reduced) 0 else durationMillis, easing = easing)

  /**
   * A gentle spring for size and offset changes.
   *
   * Springs beat tweens whenever the target can change mid-flight - a card that is pressed and
   * released before the press animation finished, a list that re-measures while it is settling -
   * because a spring retargets from its current velocity instead of restarting.
   */
  fun <T> gentleSpring(visibilityThreshold: T? = null, reduced: Boolean = false, scale: Float = 1f): AnimationSpec<T> =
    if (reduced) androidx.compose.animation.core.tween(durationMillis = 0)
    else spring(
      dampingRatio = 0.85f,
      // Springs have no duration; the speed setting reaches them through stiffness instead, which
      // changes how long the travel takes without changing its shape or its distance.
      stiffness = Spring.StiffnessMediumLow / scale.coerceAtLeast(0.1f),
      visibilityThreshold = visibilityThreshold,
    )

  fun offsetSpring(reduced: Boolean = false, scale: Float = 1f): AnimationSpec<IntOffset> =
    if (reduced) androidx.compose.animation.core.tween(durationMillis = 0)
    else spring(
      dampingRatio = 0.9f,
      stiffness = Spring.StiffnessMediumLow / scale.coerceAtLeast(0.1f),
      visibilityThreshold = IntOffset.VisibilityThreshold,
    )

  /**
   * How far a screen or panel slides while fading in, as a fraction of its own size.
   *
   * Not scaled by [AnimationSpeed]. Cinematic makes a transition longer, not larger - growing the
   * travel with the time is what would turn "I like watching transitions" into "the app feels
   * loose", and it is the one thing the speed setting must never do.
   */
  const val slideFraction = 12

  // ---------------------------------------------------------------------------------------------
  // Speed-aware accessors. Prefer these; they read the viewer's setting themselves.
  // ---------------------------------------------------------------------------------------------

  /** The scaled length of a semantic token, for the rare caller that needs the number itself. */
  @Composable
  fun millis(baseMillis: Int): Int = LocalMotionSettings.current.scaled(baseMillis)

  /** A decelerating tween for something arriving or settling into place. */
  @Composable
  fun <T> enterSpec(baseMillis: Int = MotionDuration.standard): FiniteAnimationSpec<T> =
    androidx.compose.animation.core.tween(LocalMotionSettings.current.scaled(baseMillis), easing = enter)

  /** An accelerating tween, shorter by default, for something being dismissed. */
  @Composable
  fun <T> exitSpec(baseMillis: Int = MotionDuration.short): FiniteAnimationSpec<T> =
    androidx.compose.animation.core.tween(LocalMotionSettings.current.scaled(baseMillis), easing = exit)

  /** In-out, for a value moving between two resting states while staying visible. */
  @Composable
  fun <T> standardSpec(baseMillis: Int = MotionDuration.standard): FiniteAnimationSpec<T> =
    androidx.compose.animation.core.tween(LocalMotionSettings.current.scaled(baseMillis), easing = standard)

  /**
   * One thing replacing another in the same place.
   *
   * Never zero-length, even with motion off: an instant swap of a full-screen image is its own kind
   * of jarring, and a short opacity change is the accessible replacement for movement rather than
   * an instance of it.
   */
  @Composable
  fun <T> crossfadeSpec(baseMillis: Int = MotionDuration.crossfade): FiniteAnimationSpec<T> =
    androidx.compose.animation.core.tween(LocalMotionSettings.current.crossfade(baseMillis), easing = standard)
}

/**
 * Installs the motion locals. Call once, inside the theme.
 *
 * [LocalReducedMotion] is derived rather than provided separately, so a screen cannot end up
 * animating at Cinematic length while a sibling believes motion is off.
 */
@Composable
fun ProvideStreamDekMotion(settings: MotionSettings, content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalMotionSettings provides settings,
    LocalReducedMotion provides settings.motionless,
    content = content,
  )
}

/**
 * Fade and placement animation for a row of a lazy list, honouring reduced motion.
 *
 * Only worth applying to lists the user actually mutates - the watchlist, continue watching, the
 * configured add-ons and debrid accounts - where an item vanishing makes everything below it jump
 * up by a row with no indication of what happened. A static row of cast members gains nothing from
 * it and would only pay for the extra layout node.
 *
 * Requires the list to supply a stable `key`; without one Compose cannot tell a removal from a
 * change of contents, and the animation silently does nothing.
 */
@Composable
fun LazyItemScope.animatedItem(): Modifier {
  val motion = LocalMotionSettings.current
  return if (motion.motionless) Modifier
  else Modifier.animateItem(
    fadeInSpec = tween(motion.scaled(MotionDuration.standard), easing = StreamDekMotion.enter),
    placementSpec = spring(
      dampingRatio = 0.9f,
      // A spring has no duration to scale, so the speed setting reaches it through stiffness:
      // the same travel, arrived at sooner or later, with the damping - and so the shape of the
      // movement - left alone.
      stiffness = Spring.StiffnessMediumLow / motion.scale.coerceAtLeast(0.1f),
      visibilityThreshold = IntOffset.VisibilityThreshold,
    ),
    fadeOutSpec = tween(motion.scaled(MotionDuration.short), easing = StreamDekMotion.exit),
  )
}

/**
 * Tap handling for a card, with the press actually shown.
 *
 * The media cards all drove themselves straight from `detectTapGestures`, which draws nothing while
 * the finger is down - no ripple, because there is no `clickable`, and no state of their own. So a
 * poster gave no acknowledgement at all between the touch and the detail page arriving, and on a
 * slow load that gap is long enough to be tapped again.
 *
 * The scale is deliberately small. A card is already large, and a big shrink on a large surface
 * reads as the layout breaking rather than as a button depressing; 3.5% is enough to see and not
 * enough to notice. It runs on a spring so that a tap released mid-animation returns from wherever
 * it actually got to, and it is applied in a `graphicsLayer` lambda so the press redraws without
 * recomposing the card's contents.
 */
@Composable
fun Modifier.pressable(
  vararg keys: Any?,
  pressedScale: Float = 0.965f,
  onClick: () -> Unit,
  onLongPress: (() -> Unit)? = null,
): Modifier {
  val motion = LocalMotionSettings.current
  var pressed by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(
    targetValue = if (pressed) pressedScale else 1f,
    animationSpec = StreamDekMotion.gentleSpring(reduced = motion.motionless, scale = motion.scale),
    label = "press_scale",
  )
  return this
    .graphicsLayer {
      scaleX = scale
      scaleY = scale
    }
    .pointerInput(*keys) {
      detectTapGestures(
        onPress = {
          pressed = true
          tryAwaitRelease()
          pressed = false
        },
        onTap = { onClick() },
        onLongPress = onLongPress?.let { handler -> { _ -> handler() } },
      )
    }
}

@Immutable
data class StreamDekElevation(
  val resting: androidx.compose.ui.unit.Dp = 0.dp,
  val raised: androidx.compose.ui.unit.Dp = 3.dp,
  val floating: androidx.compose.ui.unit.Dp = 8.dp,
)
