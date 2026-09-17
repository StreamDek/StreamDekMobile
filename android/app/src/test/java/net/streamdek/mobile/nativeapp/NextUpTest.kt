package net.streamdek.mobile.nativeapp

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NextUpTest {
  private val seasons = listOf(SeasonSummary(1, "One", 2), SeasonSummary(2, "Two", 3))
  private fun episode(s: Int, e: Int, date: String? = "2020-01-01") = EpisodeItem("$s:$e", e, s, "", "", null, null, date)
  private fun event(s: Int = 1, e: Int = 1, at: Long = 100) = PlaybackProgressRecord(
    "tv", "1399", "s${s}e$e", s, e, "Show", null, null, null, 100.0, 100.0, 100.0, true, updatedAt = at)
  private fun item(e: Int, progress: Double? = null, next: Boolean = false, at: Long = 100) =
    MediaItem("1399", "tv", "Show", null, null, null, null, "", progress = progress,
      resumeSeasonNumber = 1, resumeEpisodeNumber = e, isNextUp = next, updatedAt = at)

  @Test fun immediateEpisodeOnlyAndSeasonBoundary() {
    assertEquals(episode(1, 2), immediateNextUpEpisode(1, 1, seasons, listOf(episode(1, 2), episode(2, 1))))
    assertEquals(episode(2, 1), immediateNextUpEpisode(1, 2, seasons, listOf(episode(1, 2), episode(2, 1))))
    assertNull(immediateNextUpEpisode(1, 1, seasons, listOf(episode(2, 1))))
    assertNull(immediateNextUpEpisode(2, 3, seasons, listOf(episode(2, 3))))
    assertNull(immediateNextUpEpisode(1, 2, seasons, listOf(episode(2, 2))))
  }

  @Test fun futureUnknownAndMalformedDatesDoNotQualify() {
    val now = Instant.parse("2026-09-17T12:00:00Z")
    val today = LocalDate.parse("2026-09-17")
    listOf(null, "", "not-a-date", "2026-02-30", "2026-09-18", "2026-09-17T13:00:00Z").forEach {
      assertFalse("unexpected release: $it", nextUpHasReleased(it, now, today))
    }
    assertTrue(nextUpHasReleased("2026-09-17", now, today))
    assertTrue(nextUpHasReleased("2026-09-17T11:00:00Z", now, today))
  }

  @Test fun dateBecomesEligibleWithoutAnotherPlaybackEvent() {
    assertFalse(nextUpHasReleased("2026-09-18", today = LocalDate.parse("2026-09-17")))
    assertTrue(nextUpHasReleased("2026-09-18", today = LocalDate.parse("2026-09-18")))
  }

  @Test fun partialPlaybackDoesNotAdvanceButEffectiveCompletionDoes() {
    assertTrue(nextUpAnchors(listOf(event().copy(completed = false, progress = 94.9))).isEmpty())
    assertEquals(1, nextUpAnchors(listOf(event().copy(completed = false, progress = 95.0))).size)
    assertTrue(nextUpAnchors(listOf(event(), event(e = 2, at = 101).copy(completed = false, progress = 20.0))).isEmpty())
  }

  @Test fun dismissalAndUnwatchedAreNotCompletionAnchors() {
    assertTrue(nextUpAnchors(listOf(event(), event(e = 2, at = 101).copy(completed = false, dismissed = true))).isEmpty())
    assertTrue(nextUpAnchors(listOf(event().copy(completed = false, unwatched = true))).isEmpty())
    assertEquals(2, nextUpAnchors(listOf(event(e = 2), event())).single().episodeNumber)
  }

  @Test fun unwatchedNextEpisodeDoesNotHideCompletedPredecessor() {
    val completedFour = event(e = 4, at = 100)
    val unwatchedFive = event(e = 5, at = 200).copy(completed = false, unwatched = true, progress = 0.0)
    assertEquals(completedFour, nextUpAnchors(listOf(completedFour, unwatchedFive)).single())
    assertFalse(nextUpTargetIsWatched(unwatchedFive, historicalWatched = true))
  }

  @Test fun markingCompletedEpisodeUnwatchedRetiresOnlyThatCompletion() {
    val completedThree = event(e = 3, at = 100)
    val completedFour = event(e = 4, at = 200)
    val unwatchedFour = completedFour.copy(completed = false, unwatched = true, progress = 0.0, updatedAt = 300)
    assertEquals(completedThree, nextUpAnchors(listOf(completedThree, completedFour, unwatchedFour)).single())
  }

  @Test fun unwatchedFutureEpisodeDoesNotOverrideResumeOrDismissal() {
    val completedFour = event(e = 4, at = 100)
    val partialFive = event(e = 5, at = 200).copy(completed = false, progress = 20.0)
    val unwatchedSix = event(e = 6, at = 300).copy(completed = false, unwatched = true, progress = 0.0)
    assertTrue(nextUpAnchors(listOf(completedFour, partialFive, unwatchedSix)).isEmpty())
    assertTrue(nextUpAnchors(listOf(completedFour, partialFive.copy(dismissed = true), unwatchedSix)).isEmpty())
  }

  @Test fun watchedTargetsAreNeverSkippedToAnotherEpisode() {
    assertTrue(nextUpTargetIsWatched(event(e = 2), false))
    assertTrue(nextUpTargetIsWatched(null, true))
    assertTrue(nextUpTargetIsWatched(event(e = 2).copy(completed = false, progress = 95.0), false))
    assertFalse(nextUpTargetIsWatched(event(e = 2).copy(completed = false, unwatched = true), true))
    assertTrue(nextUpTargetIsWatched(event(e = 2).copy(completed = false, dismissed = true), false))
  }

  @Test fun realResumeWinsAndOneSeriesHasOneCard() {
    val resume = item(1, 40.0)
    assertEquals(listOf(resume), mergeNextUpContinueWatching(listOf(resume), listOf(item(2, next = true))))
    val next = item(2, next = true)
    assertEquals(listOf(next), mergeNextUpContinueWatching(listOf(item(1)), listOf(next)))
  }
}
