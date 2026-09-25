package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Home hero's wrap-around: last title to first and back, dots and all. */
class HomeHeroLoopTest {

  @Test
  fun `a single title does not loop`() {
    assertEquals(1, homeHeroPageCount(1))
    assertEquals(0, homeHeroPageCount(0))
    assertEquals(0, homeHeroLoopStart(1))
  }

  @Test
  fun `the loop starts mid-range on the first title with room either way`() {
    val start = homeHeroLoopStart(10)
    assertEquals(0, homeHeroItemIndex(start, 10))
    assert(start > 10_000 && start < HOME_HERO_LOOP_PAGES - 10_000)
    // Titles that do not divide the range evenly still start on the first one.
    assertEquals(0, homeHeroItemIndex(homeHeroLoopStart(7), 7))
  }

  @Test
  fun `swiping on from the last title reaches the first, and back again`() {
    val start = homeHeroLoopStart(10)
    val last = start + 9
    assertEquals(9, homeHeroItemIndex(last, 10))
    assertEquals(0, homeHeroItemIndex(last + 1, 10))
    assertEquals(9, homeHeroItemIndex(start - 1, 10))
  }

  @Test
  fun `a dot takes the short way round`() {
    val start = homeHeroLoopStart(10)
    // On the last title, the first dot is one step forward, not nine back.
    assertEquals(start + 10, homeHeroPageForItem(start + 9, 0, 10))
    // On the first title, the last dot is one step back.
    assertEquals(start - 1, homeHeroPageForItem(start, 9, 10))
    // An ordinary nearby dot goes straight there.
    assertEquals(start + 3, homeHeroPageForItem(start + 1, 3, 10))
    assertEquals(start, homeHeroPageForItem(start, 0, 10))
  }

  @Test
  fun `the first dot fills while swiping from the last title to the first`() {
    val start = homeHeroLoopStart(10)
    val midSwipe = (start + 9) + 0.6f
    assertEquals(start + 10, homeHeroNearestPageForItem(midSwipe, 0, 10))
    assertEquals(start + 9, homeHeroNearestPageForItem(midSwipe, 9, 10))
  }

  @Test
  fun `two titles still wrap both ways`() {
    val start = homeHeroLoopStart(2)
    assertEquals(1, homeHeroItemIndex(start + 1, 2))
    assertEquals(0, homeHeroItemIndex(start + 2, 2))
    assertEquals(1, homeHeroItemIndex(start - 1, 2))
  }
}
