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
}
