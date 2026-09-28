package net.streamdek.mobile.nativeapp

import net.streamdek.mobile.nativeapp.mediaserver.MediaServerIdentities
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerResume
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRow
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRowKind
import net.streamdek.mobile.nativeapp.mediaserver.mediaServerHomeRowId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where Plex meets the phone's own lists: one card per title, newest wins, no tokens written down. */
class MediaServerIntegrationTest {
  private fun item(id: String, type: String = "movie", updatedAt: Long? = null) =
    MediaItem(id = id, type = type, title = id, year = null, poster = null, backdrop = null, rating = null, description = "", updatedAt = updatedAt)

  private fun plexId(key: String) = MediaServerReference("plex", "server-1", key).encode()

  private fun resume(key: String, at: Long, tmdb: Int? = null, type: String = "movie") =
    MediaServerResume(item(plexId(key), type, at), lastViewedAtMs = at, tmdbId = tmdb, imdbId = null)

  @Test fun `a Plex title StreamDek does not know is placed by recency`() {
    val local = listOf(item("a", updatedAt = 300), item("b", updatedAt = 100))
    val merged = reconcileContinueWatching(local, listOf(resume("10", at = 200)))
    assertEquals(listOf("a", plexId("10"), "b"), merged.map { it.id })
  }

  @Test fun `the same film on both sides appears once and the newer position wins`() {
    val local = listOf(item("603", updatedAt = 100))
    val newer = reconcileContinueWatching(local, listOf(resume("10", at = 500, tmdb = 603)))
    assertEquals(listOf(plexId("10")), newer.map { it.id })
    val older = reconcileContinueWatching(listOf(item("603", updatedAt = 900)), listOf(resume("10", at = 500, tmdb = 603)))
    assertEquals(listOf("603"), older.map { it.id })
  }

  @Test fun `a film and a series with the same TMDB number are different titles`() {
    val merged = reconcileContinueWatching(listOf(item("603", type = "tv", updatedAt = 100)), listOf(resume("10", at = 500, tmdb = 603)))
    assertEquals(2, merged.size)
  }

  @Test fun `the Library shows a title in both lists once, under Continue Watching`() {
    MediaServerIdentities.clear()
    val plex = plexId("10")
    MediaServerIdentities.remember(plex, 603, "tt0133093")
    val library = unifiedLibrary(listOf(item(plex)), listOf(item("603"), item("27205")))
    assertEquals(listOf(plex), library.continueWatching.map { it.id })
    assertEquals(listOf("27205"), library.watchlist.map { it.id })
    assertFalse(library.isEmpty)
    assertTrue(unifiedLibrary(emptyList(), emptyList()).isEmpty)
  }

  @Test fun `a media server token never survives being written down with a stream`() {
    val cleaned = withoutMediaServerHeaders(mapOf("X-Plex-Token" to "secret", "x-plex-client-identifier" to "c", "Referer" to "r"))
    assertEquals(mapOf("Referer" to "r"), cleaned)
    val plain = mapOf("User-Agent" to "u")
    assertTrue(withoutMediaServerHeaders(plain) === plain)
  }

  @Test fun `Home Rows offers media server rows, Recently Added on, libraries off, collections never`() {
    fun row(kind: MediaServerRowKind, index: Int) = MediaServerRow(
      id = mediaServerHomeRowId("plex", "server-1", "movie", "${kind.name.lowercase()}-1", index), title = kind.name,
      serverId = "server-1", serverName = "Living Room", kind = kind, mediaType = "movie", items = listOf(item(plexId("1"))), libraryKey = "1",
    )
    val candidates = mediaServerHomeCatalogCandidates(listOf(row(MediaServerRowKind.RecentlyAdded, 0), row(MediaServerRowKind.Library, 1), row(MediaServerRowKind.Collections, 2)))
    assertEquals(2, candidates.size)
    assertTrue(candidates[0].enabled)
    assertFalse(candidates[1].enabled)
    assertTrue(candidates.all { isMediaServerHomeRowSourceOf(it.id) })
    assertEquals(2, mediaServerHomeSections(listOf(row(MediaServerRowKind.RecentlyAdded, 0), row(MediaServerRowKind.Library, 1), row(MediaServerRowKind.Collections, 2))).size)
  }

  private fun isMediaServerHomeRowSourceOf(id: String) = id.split(':').getOrNull(1)?.startsWith("mediaserver.") == true

  @Test fun `media server ids are recognised and nothing else is`() {
    assertTrue(isMediaServerId(plexId("1")))
    assertFalse(isMediaServerId("tt0133093"))
    assertFalse(isMediaServerId(null))
  }
}
