package net.streamdek.mobile.nativeapp

import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plex and Jellyfin as sources for catalogue titles, and where their Continue Watching shows. */
class MediaServerUnifiedTest {
  @Test fun `a TMDB catalogue title is known by its number and its IMDb id`() {
    assertEquals(TitleIds(603, "tt0133093"), TitleIds.ofCatalogue("603", "tt0133093"))
    assertEquals(TitleIds(603, null), TitleIds.ofCatalogue("tmdb:603", null))
    assertEquals(TitleIds(null, "tt0133093"), TitleIds.ofCatalogue("tt0133093", null))
    assertTrue(TitleIds.ofCatalogue("kitsu:1", null).isEmpty)
  }

  @Test fun `a server title matches on a shared TMDB or IMDb id`() {
    val wanted = TitleIds(603, "tt0133093")
    assertTrue(isSameServerTitle("movie", wanted, "movie", TitleIds(603, null)))
    assertTrue(isSameServerTitle("movie", wanted, "movie", TitleIds(null, "TT0133093")))
  }

  @Test fun `a same-named title with other ids is never a match`() {
    assertFalse(isSameServerTitle("movie", TitleIds(603, "tt0133093"), "movie", TitleIds(10000, "tt9999999")))
  }

  @Test fun `a server title with no ids, or of another kind, is never a match`() {
    assertFalse(isSameServerTitle("movie", TitleIds(603, null), "movie", null))
    assertFalse(isSameServerTitle("movie", TitleIds(603, null), "movie", TitleIds(null, null)))
    assertFalse(isSameServerTitle("movie", TitleIds(1399, null), "tv", TitleIds(1399, null)))
  }

  @Test fun `series and tv are the same kind`() {
    assertTrue(isSameServerTitle("series", TitleIds(1399, null), "tv", TitleIds(1399, null)))
  }

  @Test fun `an unknown location setting reads as the default`() {
    assertEquals(MediaServerContinueLocation.StreamDek, MediaServerContinueLocation.fromKey(null))
    assertEquals(MediaServerContinueLocation.StreamDek, MediaServerContinueLocation.fromKey("elsewhere"))
    assertEquals(MediaServerContinueLocation.ServerLibrary, MediaServerContinueLocation.fromKey("server"))
  }

  private fun resume(provider: String, key: String) = MediaServerResume(
    item = MediaItem(
      id = MediaServerReference(provider, "s1", key).encode(), type = "movie", title = key, year = null, poster = null,
      backdrop = null, rating = null, description = "", sourceAddonId = MediaServerReference.sourceIdOf(provider, "s1"),
    ),
    lastViewedAtMs = 1L, tmdbId = null, imdbId = null,
  )

  @Test fun `each provider's titles follow that provider's own choice`() {
    val entries = listOf(resume("plex", "a"), resume("jellyfin", "b"))
    val locations = MediaServerContinueLocations(plex = MediaServerContinueLocation.ServerLibrary)
    assertEquals(listOf("b"), entries.shownInStreamDek(locations).map { it.item.title })
    assertEquals(listOf("a", "b"), entries.shownInStreamDek(MediaServerContinueLocations()).map { it.item.title })
  }
}
