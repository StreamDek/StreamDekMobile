package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueWatchingMergeTest {
  @Test
  fun `episode card replaces ambiguous series card for the same title`() {
    val provider = item(type = "series", progress = 61.0)
    val localEpisode = item(type = "tv", season = 1, episode = 2, progress = 34.0, subtitle = "S1 E2")

    val merged = mergeContinueWatchingItems(listOf(provider), listOf(localEpisode))

    assertEquals(1, merged.size)
    assertEquals(1, merged.single().resumeSeasonNumber)
    assertEquals(2, merged.single().resumeEpisodeNumber)
    assertEquals("S1 E2", merged.single().cardSubtitle)
  }

  @Test
  fun `type aliases and the same episode merge into one card`() {
    val provider = item(type = "show", season = 2, episode = 4, progress = 70.0, poster = "provider-poster")
    val local = item(type = "tv", season = 2, episode = 4, progress = 24.0)

    val merged = mergeContinueWatchingItems(listOf(provider), listOf(local))

    assertEquals(1, merged.size)
    assertEquals(24.0, merged.single().progress)
    assertEquals("provider-poster", merged.single().poster)
  }

  @Test
  fun `local historical watched state survives provider artwork enrichment`() {
    val provider = item(type = "show", season = 2, episode = 4, poster = "provider-poster")
    val local = item(type = "tv", season = 2, episode = 4, progress = 24.0, historicallyWatched = true)

    val merged = mergeContinueWatchingItems(listOf(provider), listOf(local))

    assertEquals(true, merged.single().historicallyWatched)
  }

  @Test
  fun `different started episodes remain separate cards`() {
    val episodeTwo = item(season = 1, episode = 2, progress = 15.0)
    val episodeFive = item(season = 1, episode = 5, progress = 48.0)

    val merged = mergeContinueWatchingItems(emptyList(), listOf(episodeTwo, episodeFive))

    assertEquals(2, merged.size)
    assertEquals(setOf(2, 5), merged.mapNotNull { it.resumeEpisodeNumber }.toSet())
  }

  @Test
  fun `a standalone series provider card remains available when no episode identity exists`() {
    val merged = mergeContinueWatchingItems(listOf(item(type = "series")), emptyList())

    assertEquals(1, merged.size)
    assertNull(merged.single().resumeEpisodeNumber)
  }

  private fun item(
    type: String = "tv",
    season: Int? = null,
    episode: Int? = null,
    progress: Double? = null,
    subtitle: String? = null,
    poster: String? = null,
    historicallyWatched: Boolean = false,
  ) = MediaItem(
    id = "113962",
    type = type,
    title = "Example",
    year = "2025",
    poster = poster,
    backdrop = null,
    rating = null,
    description = "",
    progress = progress,
    cardSubtitle = subtitle,
    resumeSeasonNumber = season,
    resumeEpisodeNumber = episode,
    historicallyWatched = historicallyWatched,
  )
}
