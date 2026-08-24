package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaCollectionKeyTest {
  @Test
  fun `different episodes of the same series have different collection keys`() {
    val episodeOne = mediaItem(season = 1, episode = 1)
    val episodeTwo = mediaItem(season = 1, episode = 2)

    assertNotEquals(mediaCollectionKey(episodeOne), mediaCollectionKey(episodeTwo))
  }

  @Test
  fun `ordinary title cards keep their type and id identity`() {
    assertEquals("tv-113962", mediaCollectionKey(mediaItem()))
  }

  private fun mediaItem(season: Int? = null, episode: Int? = null) = MediaItem(
    id = "113962",
    type = "tv",
    title = "Example",
    year = null,
    poster = null,
    backdrop = null,
    rating = null,
    description = "",
    resumeSeasonNumber = season,
    resumeEpisodeNumber = episode,
  )
}
