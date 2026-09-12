package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollChromeMachineTest {
  // Density 1 keeps pixels and dp interchangeable, so the thresholds read as written.
  private fun machine() = ScrollChromeMachine(density = 1f)
  private var clock = 0L

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
    assertFalse(m.navigationCollapsed)
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
    assertFalse(m.navigationCollapsed)
    assertEquals(ScrollPhase.NearTop, m.phase)
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
  fun navigationWaitsForTheExpandLineOnTheWayBackUp() {
    val m = machine()
    m.reportAtTop(false)
    m.drag(400f)
    assertTrue(m.navigationCollapsed)
    // Confirmed upward, but the fraction is still well above the 55% line: stays collapsed.
    m.drag(-20f)
    assertTrue("fraction ${m.fraction}", m.fraction > 0.55f)
    assertTrue(m.navigationCollapsed)
  }

  @Test
  fun expandClearsTheCollapseUntilTheNextScroll() {
    val m = machine()
    m.reportAtTop(false)
    m.drag(400f)
    m.expand()
    m.animateTo(0f)
    assertFalse(m.navigationCollapsed)
    m.drag(8f) // under the slop
    assertFalse(m.navigationCollapsed)
    m.drag(120f)
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
