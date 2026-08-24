package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class EpisodeReleasePolicyTest {
  private fun episode(season: Int = 2, number: Int = 4, date: String = "2026-08-24") =
    AiringEpisode(number, "The Episode", season, number, date, null)

  private fun status(vararg episodes: AiringEpisode) = SeriesEpisodeStatus(
    tmdbId = 100, title = "Example", poster = null, backdrop = null, status = "Returning Series",
    nextEpisode = null, lastEpisode = null, episodes = episodes.toList(),
  )

  private fun clock(instant: String, zone: String = "Europe/London") =
    Clock.fixed(Instant.parse(instant), ZoneId.of(zone))

  @Test fun `new episode becomes one available notification and rerun dedupes it`() {
    val first = EpisodeReleasePolicy.candidates(
      listOf(status(episode())), EpisodeNotificationSettings(availableEnabled = true), emptySet(),
      clock("2026-08-24T12:00:00Z"),
    )
    assertEquals(1, first.size)
    assertEquals(EpisodeNotificationType.Available, first.single().type)
    assertTrue(EpisodeReleasePolicy.candidates(
      listOf(status(episode())), EpisodeNotificationSettings(availableEnabled = true), setOf(first.single().key),
      clock("2026-08-24T13:00:00Z"),
    ).isEmpty())
  }

  @Test fun `upcoming and later available are separate notification identities`() {
    val upcoming = EpisodeReleasePolicy.candidates(
      listOf(status(episode(date = "2026-08-25"))), EpisodeNotificationSettings(upcomingEnabled = true, upcomingDays = 1),
      emptySet(), clock("2026-08-24T10:00:00Z"),
    ).single()
    val available = EpisodeReleasePolicy.candidates(
      listOf(status(episode(date = "2026-08-25"))), EpisodeNotificationSettings(availableEnabled = true),
      setOf(upcoming.key), clock("2026-08-25T12:00:00Z"),
    ).single()
    assertEquals(EpisodeNotificationType.Upcoming, upcoming.type)
    assertEquals(EpisodeNotificationType.Available, available.type)
  }

  @Test fun `disabled unknown date and unrelated empty input produce nothing`() {
    assertTrue(EpisodeReleasePolicy.candidates(listOf(status(episode())), EpisodeNotificationSettings(), emptySet(), clock("2026-08-24T12:00:00Z")).isEmpty())
    assertTrue(EpisodeReleasePolicy.candidates(listOf(status(episode(date = "unknown"))), EpisodeNotificationSettings(true), emptySet(), clock("2026-08-24T12:00:00Z")).isEmpty())
    assertTrue(EpisodeReleasePolicy.candidates(emptyList(), EpisodeNotificationSettings(true), emptySet(), clock("2026-08-24T12:00:00Z")).isEmpty())
  }

  @Test fun `multiple episodes released together remain separate`() {
    val candidates = EpisodeReleasePolicy.candidates(
      listOf(status(episode(number = 1), episode(number = 2), episode(number = 3))),
      EpisodeNotificationSettings(availableEnabled = true), emptySet(), clock("2026-08-24T12:00:00Z"),
    )
    assertEquals(3, candidates.size)
    assertEquals(3, candidates.map { it.key }.distinct().size)
  }

  @Test fun `date-only release waits for local nine at timezone boundary`() {
    val settings = EpisodeNotificationSettings(availableEnabled = true)
    val before = EpisodeReleasePolicy.candidates(listOf(status(episode())), settings, emptySet(), clock("2026-08-24T07:59:59Z"))
    val after = EpisodeReleasePolicy.candidates(listOf(status(episode())), settings, emptySet(), clock("2026-08-24T08:00:00Z"))
    assertTrue(before.isEmpty())
    assertEquals(1, after.size)
  }

  @Test fun `same episode keys differ by profile only in each profiles delivered set`() {
    val candidates = EpisodeReleasePolicy.candidates(
      listOf(status(episode())), EpisodeNotificationSettings(availableEnabled = true), emptySet(), clock("2026-08-24T12:00:00Z"),
    )
    val profileAHistory = setOf(candidates.single().key)
    assertTrue(EpisodeReleasePolicy.candidates(listOf(status(episode())), EpisodeNotificationSettings(true), profileAHistory, clock("2026-08-24T12:00:00Z")).isEmpty())
    assertEquals(1, EpisodeReleasePolicy.candidates(listOf(status(episode())), EpisodeNotificationSettings(true), emptySet(), clock("2026-08-24T12:00:00Z")).size)
  }
}
