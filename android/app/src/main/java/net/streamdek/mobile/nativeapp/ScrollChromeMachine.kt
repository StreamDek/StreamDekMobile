package net.streamdek.mobile.nativeapp

import kotlin.math.abs

/**
 * What the viewer's scrolling currently means for the app's chrome — navigation, search, headers.
 *
 * One answer for the whole screen. Search, the floating navigation and the glass behind them all
 * read this rather than each watching raw scroll events, which is what stops Search expanding in
 * the same gesture that collapses the navigation.
 */
enum class ScrollPhase {
  /** At, or within a thumb's width of, the start of the page. Chrome is fully shown. */
  NearTop,
  /** Nothing has moved since the page appeared. */
  Idle,
  ScrollingDown,
  /** A flick rather than a read: chrome gets out of the way faster. */
  FastScrollingDown,
  ScrollingUp,
  /** Scrolling stopped and the chrome came to rest at one of its resting states. */
  Settled,
  /** The viewer is using a control in the chrome (typing a search), so the chrome holds still. */
  Interacting,
}

/**
 * The scroll-to-chrome decision, free of Compose and Android so it can be tested as plain maths.
 *
 * # The model
 *
 * A single [fraction] runs from 0 (everything shown) to 1 (everything tucked away). It follows the
 * finger rather than playing an animation after the gesture: scrolling down 72dp moves it halfway,
 * which is where Search has compacted, and another 72dp hides it. Scrolling back up moves it faster
 * than scrolling down did ([UP_GAIN]), because controls returning is the thing a viewer is waiting
 * for and controls leaving is not.
 *
 * When scrolling stops it settles to one of three resting values — shown, compact, hidden — so a
 * header is never left stuck a third of the way off the screen.
 *
 * # Calm, not hyperactive
 *
 * - A direction only counts once the finger has travelled [DIRECTION_SLOP_DP] in it. A thumb resting
 *   on the glass, or the wobble at the end of a drag, changes nothing.
 * - The navigation decision has hysteresis: it collapses going down past 45% and only expands going
 *   up below 55% (or on reaching the top), so hovering around the middle cannot flicker it.
 * - Near the top the fraction is capped by the distance from the top, so arriving at the start of a
 *   page always shows everything, however the page got there.
 *
 * Distances arrive in pixels and are converted with [density] so the feel is the same on every
 * screen.
 */
internal class ScrollChromeMachine(private val density: Float) {

  companion object {
    /** Scroll distance from fully shown to fully hidden. Half of it is the compact state. */
    const val TRAVEL_DP = 144f
    /** Movement ignored before a direction is believed. */
    const val DIRECTION_SLOP_DP = 10f
    /** Within this distance of the start the page counts as at the top. */
    const val NEAR_TOP_DP = 56f
    /** Distance from the top before anything starts to tuck away at all. */
    const val TOP_GRACE_DP = 24f
    /** Above this speed a downward scroll is a flick. */
    const val FAST_DP_PER_MS = 2.4f
    /** Upward scrolling returns the chrome this much faster than downward scrolling hid it. */
    const val UP_GAIN = 1.8f
    /** A flick hides the chrome a little faster than a read. */
    const val FAST_GAIN = 1.35f

    const val SHOWN = 0f
    const val COMPACT = 0.5f
    const val HIDDEN = 1f
  }

  private val travelPx = TRAVEL_DP * density
  private val slopPx = DIRECTION_SLOP_DP * density
  private val nearTopPx = NEAR_TOP_DP * density
  private val gracePx = TOP_GRACE_DP * density
  private val fastPxPerMs = FAST_DP_PER_MS * density

  var fraction: Float = SHOWN
    private set
  var phase: ScrollPhase = ScrollPhase.NearTop
    private set
  var navigationCollapsed: Boolean = false
    private set

  /**
   * Best estimate of how far the page is from its start.
   *
   * Built from scroll deltas, so it can drift when content above the viewport changes size; a page
   * that knows its real position corrects it with [reportAtTop].
   */
  var distanceFromTop: Float = 0f
    private set

  /** -1 up, +1 down, 0 not yet decided. Only set once [slopPx] has been travelled. */
  var direction: Int = 0
    private set

  var interacting: Boolean = false
    private set

  val nearTop: Boolean get() = distanceFromTop < nearTopPx

  private var pendingSign = 0
  private var pendingTravel = 0f
  private var lastEventMs = -1L
  private var speedPxPerMs = 0f

  /**
   * One frame of scrolling.
   *
   * @param deltaPx how far the content moved this frame; positive when scrolling toward the end of
   *   the page (the finger moving up).
   * @param blockedAtTop the gesture asked to go further back than the page could, which only happens
   *   at the very start of it.
   */
  fun onScroll(deltaPx: Float, blockedAtTop: Boolean, timeMs: Long) {
    distanceFromTop = (distanceFromTop + deltaPx).coerceAtLeast(0f)
    if (blockedAtTop) distanceFromTop = 0f
    if (deltaPx == 0f) {
      refreshPhase()
      updateNavigation()
      return
    }

    val dt = if (lastEventMs < 0) 0L else timeMs - lastEventMs
    lastEventMs = timeMs
    speedPxPerMs = if (dt in 1..100) speedPxPerMs * 0.6f + (abs(deltaPx) / dt) * 0.4f else 0f

    val sign = if (deltaPx > 0f) 1 else -1
    if (sign != pendingSign) {
      pendingSign = sign
      pendingTravel = 0f
    }
    pendingTravel += abs(deltaPx)
    if (direction != sign && pendingTravel >= slopPx) direction = sign

    if (!interacting) {
      if (direction == sign) {
        val gain = when {
          sign < 0 -> UP_GAIN
          speedPxPerMs > fastPxPerMs -> FAST_GAIN
          else -> 1f
        }
        fraction = (fraction + deltaPx * gain / travelPx).coerceIn(SHOWN, HIDDEN)
      }
      fraction = fraction.coerceAtMost(topCap())
    }
    refreshPhase()
    updateNavigation()
  }

  /** A page that knows its real scroll position corrects the delta-built estimate. */
  fun reportAtTop(atTop: Boolean) {
    distanceFromTop = when {
      atTop -> 0f
      distanceFromTop < nearTopPx -> nearTopPx
      else -> distanceFromTop
    }
    if (!interacting) fraction = fraction.coerceAtMost(topCap())
    refreshPhase()
    updateNavigation()
  }

  /**
   * Scrolling stopped. Returns the resting fraction the chrome should ease to.
   *
   * Direction decides between the two nearest resting states: coming back up prefers the more
   * visible one, going down the less visible one, so the settle continues the gesture rather than
   * undoing it.
   */
  fun settle(): Float {
    speedPxPerMs = 0f
    pendingTravel = 0f
    if (interacting) return SHOWN
    val target = when {
      nearTop -> SHOWN
      direction < 0 -> if (fraction <= 0.5f) SHOWN else COMPACT
      direction > 0 -> when {
        fraction < 0.2f -> SHOWN
        fraction < 0.7f -> COMPACT
        else -> HIDDEN
      }
      else -> listOf(SHOWN, COMPACT, HIDDEN).minBy { abs(it - fraction) }
    }
    phase = if (nearTop) ScrollPhase.NearTop else ScrollPhase.Settled
    when (target) {
      SHOWN -> navigationCollapsed = false
      HIDDEN -> navigationCollapsed = true
    }
    return target
  }

  /** The settle animation's frames. Never changes the navigation decision on its own. */
  fun animateTo(value: Float) {
    fraction = value.coerceIn(SHOWN, HIDDEN)
  }

  fun setInteracting(value: Boolean) {
    if (interacting == value) return
    interacting = value
    direction = 0
    pendingTravel = 0f
    if (value) navigationCollapsed = false
    refreshPhase()
  }

  /** The viewer asked for the chrome back — tapped the collapsed navigation, say. */
  fun expand() {
    direction = 0
    pendingTravel = 0f
    navigationCollapsed = false
  }

  /** A different page: start from the top, fully shown. */
  fun reset() {
    fraction = SHOWN
    distanceFromTop = 0f
    direction = 0
    pendingSign = 0
    pendingTravel = 0f
    lastEventMs = -1L
    speedPxPerMs = 0f
    navigationCollapsed = false
    refreshPhase()
  }

  /** Near the start, how much the chrome is allowed to have tucked away. */
  private fun topCap(): Float = ((distanceFromTop - gracePx) / travelPx).coerceIn(SHOWN, HIDDEN)

  private fun refreshPhase() {
    phase = when {
      interacting -> ScrollPhase.Interacting
      nearTop -> ScrollPhase.NearTop
      direction > 0 && speedPxPerMs > fastPxPerMs -> ScrollPhase.FastScrollingDown
      direction > 0 -> ScrollPhase.ScrollingDown
      direction < 0 -> ScrollPhase.ScrollingUp
      else -> ScrollPhase.Idle
    }
  }

  private fun updateNavigation() {
    navigationCollapsed = when {
      interacting -> false
      nearTop || fraction <= 0.05f -> false
      !navigationCollapsed && direction > 0 && fraction >= 0.45f -> true
      navigationCollapsed && direction < 0 && fraction <= 0.55f -> false
      else -> navigationCollapsed
    }
  }
}
