package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollChromeMachineTest {
  // Density 1 keeps pixels and dp interchangeable, so the thresholds read as written.
  private fun machine() = ScrollChromeMachine(density = 1f)
  private var clock = 0L

  private fun ScrollChromeMachine.idleFor(ms: Long) {
    clock += ms
    onIdle(clock)
  }

  private fun ScrollChromeMachine.touchDown() {
    clock += 16
    onTouchDown(clock)
  }

  private fun ScrollChromeMachine.touchUp() {
    clock += 16
    onTouchUp(clock)
  }

  /** Scrolls [totalDp] in steps of [stepDp], 16ms apart, like frames of a steady drag. */
  private fun ScrollChromeMachine.drag(totalDp: Float, stepDp: Float = 4f) {
    val steps = (kotlin.math.abs(totalDp) / stepDp).toInt()
    val sign = if (totalDp < 0) -1f else 1f
    repeat(steps) {
      clock += 16
      onScroll(sign * stepDp, blockedAtTop = false, timeMs = clock)
    }
  }

  @Test
  fun startsFullyShownAtTheTop() {
    val m = machine()
    assertEquals(0f, m.fraction)
    assertEquals(ScrollPhase.NearTop, m.phase)
    assertFalse(m.navigationCollapsed)
  }

  @Test
  fun smallMovementsNearTheTopChangeNothing() {
    val m = machine()
    m.drag(20f)
    assertEquals(0f, m.fraction)
    assertEquals(ScrollPhase.NearTop, m.phase)
  }

  @Test
  fun scrollingDownCompactsThenHides() {
    val m = machine()
    m.drag(400f) // clear of the top
    m.reset()
    m.reportAtTop(false)
    m.drag(90f)
    assertTrue("compacting: ${m.fraction}", m.fraction in 0.3f..0.7f)
    m.drag(200f)
    assertEquals(1f, m.fraction)
    assertTrue(m.navigationCollapsed)
    assertEquals(ScrollPhase.ScrollingDown, m.phase)
  }

  @Test
  fun jitterBelowTheSlopDoesNotReverseDirection() {
    val m = machine()
    m.reportAtTop(false)
    m.drag(300f)
    val hidden = m.fraction
    // A thumb wobbling back and forth by a few dp.
    repeat(10) {
      m.drag(-4f)
      m.drag(4f)
    }
    assertEquals(hidden, m.fraction)
    assertTrue(m.navigationCollapsed)
  }

  @Test
  fun meaningfulUpwardScrollBringsChromeBackQuickly() {
    val m = machine()
    m.reportAtTop(false)
    m.drag(400f)
    assertEquals(1f, m.fraction)
    m.drag(-60f)
    // 10dp of slop, then 50dp at the upward gain of 1.8 over a 144dp travel.
    assertTrue("returning: ${m.fraction}", m.fraction < 0.5f)
    // The header returns on the way up; the navigation does not, because the viewer is still moving.
    assertTrue(m.navigationCollapsed)
    assertEquals(ScrollPhase.ScrollingUp, m.phase)
  }

  @Test
  fun settleContinuesTheGestureRatherThanUndoingIt() {
    val down = machine()
    down.reportAtTop(false)
    down.drag(84f) // just into compacting
    assertEquals(ScrollChromeMachine.COMPACT, down.settle())
    assertEquals(ScrollPhase.Settled, down.phase)

    val up = machine()
    up.reportAtTop(false)
    up.drag(400f)
    up.drag(-24f) // a little way back from hidden
    assertEquals(ScrollChromeMachine.COMPACT, up.settle())

    val nearTop = machine()
    nearTop.drag(30f)
    assertEquals(ScrollChromeMachine.SHOWN, nearTop.settle())
  }

  @Test
  fun reachingTheTopAlwaysShowsEverything() {
    val m = machine()
    m.reportAtTop(false)
    m.drag(400f)
    clock += 16
    m.onScroll(0f, blockedAtTop = true, timeMs = clock)
    assertEquals(0f, m.fraction)
    assertEquals(ScrollPhase.NearTop, m.phase)
    // Arriving at the top is not stopping: the navigation waits for the viewer to be still.
    assertTrue(m.navigationCollapsed)
    m.idleFor(ScrollChromeMachine.NAVIGATION_RETURN_DELAY_MS)
    assertFalse(m.navigationCollapsed)
  }

  @Test
  fun interactingHoldsTheChromeStill() {
    val m = machine()
    m.reportAtTop(false)
    m.setInteracting(true)
    m.drag(400f)
    assertEquals(0f, m.fraction)
    assertFalse(m.navigationCollapsed)
    assertEquals(ScrollPhase.Interacting, m.phase)
    assertEquals(ScrollChromeMachine.SHOWN, m.settle())

    m.setInteracting(false)
    m.drag(400f)
    assertTrue("resumes after typing: ${m.fraction}", m.fraction > 0.9f)
  }

  @Test
  fun scrollingEitherWayCollapsesTheNavigation() {
    val down = machine()
    down.drag(20f)
    assertTrue(down.navigationCollapsed)

    val up = machine()
    up.reportAtTop(false)
    up.drag(-20f)
    assertTrue(up.navigationCollapsed)
  }

  @Test
  fun tinyMovementsDoNotCollapseTheNavigation() {
    val m = machine()
    m.drag(12f)
    assertFalse(m.navigationCollapsed)
    // Separate nudges with a real pause between them are not one journey.
    m.idleFor(ScrollChromeMachine.NAVIGATION_RETURN_DELAY_MS + 50)
    m.drag(12f)
    assertFalse(m.navigationCollapsed)
  }

  @Test
  fun reversingDirectionKeepsTheNavigationCollapsed() {
    val m = machine()
    m.reportAtTop(false)
    m.drag(300f)
    m.drag(-300f)
    m.drag(40f)
    assertTrue(m.navigationCollapsed)
  }

  @Test
  fun navigationReturnsOnlyAfterTheIdleDelay() {
    val m = machine()
    m.drag(100f)
    m.idleFor(ScrollChromeMachine.NAVIGATION_RETURN_DELAY_MS - 100)
    assertTrue("too early", m.navigationCollapsed)
    m.idleFor(100)
    assertFalse(m.navigationCollapsed)
  }

  @Test
  fun momentumKeepsTheNavigationCollapsed() {
    val m = machine()
    m.touchDown()
    m.drag(80f)
    m.touchUp()
    // A fling decelerating long after the finger lifted: every frame is activity.
    repeat(60) { m.drag(2f, stepDp = 2f) }
    m.idleFor(ScrollChromeMachine.NAVIGATION_RETURN_DELAY_MS - 50)
    assertTrue(m.navigationCollapsed)
    m.idleFor(50)
    assertFalse(m.navigationCollapsed)
  }

  @Test
  fun aFingerRestingOnThePageHoldsTheNavigationAway() {
    val m = machine()
    m.touchDown()
    m.drag(100f)
    // Still touching, not moving, for well over the delay.
    m.idleFor(ScrollChromeMachine.NAVIGATION_RETURN_DELAY_MS * 3)
    assertTrue(m.navigationCollapsed)
    assertEquals(null, m.navigationReturnDelay(clock))
    m.touchUp()
    m.idleFor(ScrollChromeMachine.NAVIGATION_RETURN_DELAY_MS - 1)
    assertTrue("the clock starts when the finger lifts", m.navigationCollapsed)
    m.idleFor(1)
    assertFalse(m.navigationCollapsed)
  }

  @Test
  fun quickConsecutiveSwipesDoNotBringTheNavigationBackBetweenThem() {
    val m = machine()
    repeat(4) {
      m.touchDown()
      m.drag(120f)
      m.touchUp()
      m.idleFor(ScrollChromeMachine.NAVIGATION_RETURN_DELAY_MS / 2)
      assertTrue("between swipes", m.navigationCollapsed)
    }
    m.idleFor(ScrollChromeMachine.NAVIGATION_RETURN_DELAY_MS)
    assertFalse(m.navigationCollapsed)
  }

  @Test
  fun navigationTheViewerAskedForSurvivesTheRestOfTheirFling() {
    val m = machine()
    m.reportAtTop(false)
    m.touchDown()
    m.drag(400f)
    m.touchUp()
    m.expand()
    m.animateTo(0f)
    // The fling they tapped through keeps moving the page.
    m.drag(200f)
    assertFalse(m.navigationCollapsed)
    // A new gesture is a new decision.
    m.touchDown()
    m.drag(40f)
    assertTrue(m.navigationCollapsed)
  }

  @Test
  fun leavingTheTopDoesNotInflateTheDistance() {
    // What a list reports as its first pixel scrolls away: it must not count as 56dp of travel.
    val m = machine()
    m.drag(4f)
    m.reportAtTop(false)
    m.drag(110f)
    assertTrue("distance ${m.distanceFromTop}", m.distanceFromTop in 110f..118f)
    assertEquals(ScrollChromeMachine.COMPACT, m.settle())
  }

  @Test
  fun aPageRestoredMidListIsNotTreatedAsAtTheTop() {
    val m = machine()
    m.reportAtTop(false)
    assertFalse(m.nearTop)
  }

  @Test
  fun flingsAreRecognisedAsFast() {
    val m = machine()
    m.reportAtTop(false)
    m.drag(360f, stepDp = 60f) // 60dp per 16ms frame, long enough for the speed average to build
    assertEquals(ScrollPhase.FastScrollingDown, m.phase)
  }
}
