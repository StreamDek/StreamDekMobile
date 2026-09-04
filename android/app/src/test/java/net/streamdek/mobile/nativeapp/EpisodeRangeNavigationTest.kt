package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cutting a long season into blocks a viewer can actually reach.
 *
 * The cases worth pinning down are the ones a tidy season never shows: numbering that starts at
 * zero, gaps where an episode was pulled, seasons sitting exactly on the threshold, and a viewer
 * who has finished everything. Each of those has an obviously wrong answer that would look fine on
 * a season of twelve.
 */
class EpisodeRangeNavigationTest {

  private fun season(count: Int, from: Int = 1) = (from until from + count).toList()

  // --- when to cut at all ---

  @Test
  fun `an ordinary season is left alone`() {
    // No ranges means the caller draws exactly what it drew before. A season of ten does not need
    // navigating and wrapping it in machinery would make the common case worse.
    assertTrue(buildEpisodeRanges(season(10)).isEmpty())
    assertTrue(buildEpisodeRanges(season(1)).isEmpty())
    assertTrue(buildEpisodeRanges(emptyList()).isEmpty())
  }

  @Test
  fun `the threshold is inclusive, so a season of exactly twenty is unchanged`() {
    assertTrue(buildEpisodeRanges(season(20)).isEmpty())
    assertEquals(2, buildEpisodeRanges(season(21)).size)
  }

  // --- how the cut falls ---

  @Test
  fun `a long season is cut into even blocks with a remainder at the end`() {
    val ranges = buildEpisodeRanges(season(200))
    assertEquals(10, ranges.size)
    assertEquals("1–20", ranges.first().label)
    assertEquals("181–200", ranges.last().label)
    assertEquals(0, ranges.first().fromIndex)
    assertEquals(199, ranges.last().toIndex)
    assertTrue(ranges.all { it.size == 20 })

    val ragged = buildEpisodeRanges(season(45))
    assertEquals(3, ragged.size)
    assertEquals(listOf(20, 20, 5), ragged.map { it.size })
    assertEquals("41–45", ragged.last().label)
  }

  @Test
  fun `labels come from the episode numbers present, not from the block's position`() {
    // A season that starts at zero, or skips a number, would otherwise be labelled "1-20" over a
    // block containing neither 1 nor 20 - a label the viewer has to work around rather than use.
    assertEquals("0–19", buildEpisodeRanges(season(40, from = 0)).first().label)

    val withGap = (season(30) - 7).toList()
    val ranges = buildEpisodeRanges(withGap)
    assertEquals("1–21", ranges.first().label)
    // Blocks are cut by position, so a missing episode does not leave a short block behind.
    assertEquals(20, ranges.first().size)
  }

  @Test
  fun `a block holding one episode is labelled with that number alone`() {
    assertEquals("21", buildEpisodeRanges(season(21)).last().label)
  }

  // --- finding the right block ---

  @Test
  fun `the block containing an episode is the one shown`() {
    val ranges = buildEpisodeRanges(season(200))
    assertEquals(0, episodeRangeIndexFor(ranges, 1))
    assertEquals(0, episodeRangeIndexFor(ranges, 20))
    assertEquals(1, episodeRangeIndexFor(ranges, 21))
    // The case the whole feature exists for.
    assertEquals(8, episodeRangeIndexFor(ranges, 176))
    assertEquals(9, episodeRangeIndexFor(ranges, 200))
  }

  @Test
  fun `an episode that does not exist falls back to the first block rather than to nothing`() {
    val ranges = buildEpisodeRanges(season(200))
    assertEquals(0, episodeRangeIndexFor(ranges, 999))
    assertEquals(0, episodeRangeIndexFor(ranges, null))
    assertEquals(0, episodeRangeIndexFor(emptyList(), 176))
  }

  // --- which episode a season opens on ---

  @Test
  fun `a selected episode wins over everything else`() {
    assertEquals(
      176,
      focusEpisodeNumber(season(200), selectedEpisodeNumber = 176, inProgressEpisodeNumber = 12, watchedEpisodeNumbers = setOf(1)),
    )
  }

  @Test
  fun `otherwise the season opens where the viewer left off`() {
    assertEquals(
      88,
      focusEpisodeNumber(season(200), inProgressEpisodeNumber = 88, watchedEpisodeNumbers = (1..87).toSet()),
    )
  }

  @Test
  fun `with nothing in progress it opens on the first unwatched episode`() {
    assertEquals(41, focusEpisodeNumber(season(200), watchedEpisodeNumbers = (1..40).toSet()))
  }

  @Test
  fun `a finished season opens at the start rather than nowhere`() {
    assertEquals(1, focusEpisodeNumber(season(200), watchedEpisodeNumbers = season(200).toSet()))
    assertNull(focusEpisodeNumber(emptyList()))
  }

  @Test
  fun `a stale selection or resume point is ignored rather than followed off the end`() {
    // Switching season carries the old numbers along for a frame; landing on block 9 of a season
    // with two blocks would be worse than landing at the start.
    assertEquals(1, focusEpisodeNumber(season(30), selectedEpisodeNumber = 176))
    assertEquals(1, focusEpisodeNumber(season(30), inProgressEpisodeNumber = 176))
  }

  // --- next up ---

  @Test
  fun `next up is the first gap, not the highest watched plus one`() {
    // Somebody who skipped ahead has an earlier episode still waiting, and that is the one to offer.
    assertEquals(3, nextUnwatchedEpisodeNumber(season(10), setOf(1, 2, 4, 5)))
  }

  @Test
  fun `a finished season has no next episode`() {
    assertNull(nextUnwatchedEpisodeNumber(season(10), season(10).toSet()))
  }

  // --- jumping ---

  @Test
  fun `a jump to an episode that exists lands on it`() {
    assertEquals(176, resolveJumpTarget(season(200), 176))
  }

  @Test
  fun `a jump past the end lands on the nearest episode instead of failing`() {
    assertEquals(200, resolveJumpTarget(season(200), 500))
    assertEquals(1, resolveJumpTarget(season(200), -4))
  }

  @Test
  fun `a jump into a gap lands beside it`() {
    assertEquals(6, resolveJumpTarget((season(30) - 7).toList(), 7))
  }

  @Test
  fun `a jump with nothing to jump to is refused`() {
    assertNull(resolveJumpTarget(emptyList(), 3))
    assertNull(resolveJumpTarget(season(200), null))
  }

  /**
   * The layout override for long seasons.
   *
   * The limit sits well above the range threshold on purpose: blocks start earning their place at
   * twenty, and a vertical list is still the better way to read a season several times that long.
   * These pin both ends of that gap, because collapsing the two numbers into one would quietly
   * take the stacked layout away from the ordinary network season it was built for.
   */
  @Test
  fun `an ordinary season is left to the viewer's chosen layout`() {
    assertFalse(seasonRequiresStripLayout(0))
    assertFalse(seasonRequiresStripLayout(10))
    // Long enough to be cut into blocks, nowhere near long enough to need them.
    assertTrue(buildEpisodeRanges(season(24)).isNotEmpty())
    assertFalse(seasonRequiresStripLayout(24))
    assertFalse(seasonRequiresStripLayout(EPISODE_STACK_LIMIT))
  }

  @Test
  fun `a season past the limit is forced to the strip`() {
    assertTrue(seasonRequiresStripLayout(EPISODE_STACK_LIMIT + 1))
    assertTrue(seasonRequiresStripLayout(200))
  }

  @Test
  fun `the stack limit leaves room above the range threshold`() {
    assertTrue(EPISODE_STACK_LIMIT > EPISODE_RANGE_THRESHOLD)
  }
}
