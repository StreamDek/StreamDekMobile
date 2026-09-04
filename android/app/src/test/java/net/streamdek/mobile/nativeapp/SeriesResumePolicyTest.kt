package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesResumePolicyTest {
  private val episodes = (1..3).map { SeriesEpisodeSlot(2, it) } + SeriesEpisodeSlot(3, 1)

  @Test fun `partial episode wins and keeps exact seconds`() {
    val state = getSeriesResumeState("99", episodes, listOf(SeriesProgressEvent(2, 3, 1122.0, updatedAtMillis = 10)), emptySet())
    assertEquals(SeriesEpisodeSlot(2, 3), state.target)
    assertEquals(1122.0, state.resumePositionSec ?: 0.0, 0.001)
  }

  @Test fun `completed episode advances to next unwatched`() {
    val state = getSeriesResumeState("99", episodes, emptyList(), setOf("99:s2:e1", "99:s2:e2"))
    assertEquals(SeriesEpisodeSlot(2, 3), state.target)
  }

  @Test fun `recent explicit unwatch reselects episode`() {
    val state = getSeriesResumeState(
      "99", episodes, listOf(SeriesProgressEvent(2, 2, status = "unwatched", updatedAtMillis = 20)),
      setOf("99:s2:e1", "99:s2:e2"),
    )
    assertEquals(SeriesEpisodeSlot(2, 2), state.target)
    assertEquals(false, "99:s2:e2" in state.watchedEpisodeKeys)
  }

  @Test fun `completed season advances into next season`() {
    val state = getSeriesResumeState("99", episodes, emptyList(), setOf("99:s2:e1", "99:s2:e2", "99:s2:e3"))
    assertEquals(SeriesEpisodeSlot(3, 1), state.target)
  }

  /**
   * The reason this policy must not drive navigation.
   *
   * The target is the furthest-watched point in the series, not the next gap after whatever the
   * viewer last touched -- indexOfLast, over every slot. That is right for Continue Watching, and
   * it was wrong as the destination for a season reload: on a series with later seasons already
   * part-watched, ticking an episode in season one asked this policy where to go and was answered
   * "the last season", which is where the detail page then went.
   *
   * Pinned rather than changed. Continue Watching depends on this answer; the toggle no longer
   * asks the question while a season is on screen.
   */
  @Test fun `target is the furthest watched point, not the gap nearest what was just marked`() {
    val series = (1..3).map { SeriesEpisodeSlot(1, it) } +
      (1..3).map { SeriesEpisodeSlot(2, it) } +
      (1..3).map { SeriesEpisodeSlot(3, it) }

    // Season one episode one has just been marked, and season three is already part-watched.
    val state = getSeriesResumeState("99", series, emptyList(), setOf("99:s1:e1", "99:s3:e1"))

    assertEquals(SeriesEpisodeSlot(3, 2), state.target)
  }

  @Test fun `a series watched to the end targets its final episode rather than running off it`() {
    val series = (1..2).map { SeriesEpisodeSlot(1, it) }
    val state = getSeriesResumeState("99", series, emptyList(), setOf("99:s1:e1", "99:s1:e2"))
    assertEquals(SeriesEpisodeSlot(1, 2), state.target)
  }
}
