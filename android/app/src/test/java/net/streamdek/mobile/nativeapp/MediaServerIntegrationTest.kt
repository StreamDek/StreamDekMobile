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

  @Test fun `a slow or empty provider keeps its rows and never empties the other's`() {
    fun row(provider: String, index: Int, title: String) = MediaServerRow(
      id = mediaServerHomeRowId(provider, "s", "movie", "library-1", index), title = title,
      serverId = "s", serverName = "S", kind = MediaServerRowKind.Library, mediaType = "movie", items = listOf(item(plexId("1"))), libraryKey = "1",
    )
    val before = listOf(row("plex", 0, "old plex"), row("jellyfin", 0, "old jellyfin"))
    val merged = mergeMediaServerRows(before, linkedMapOf("plex" to listOf(row("plex", 0, "new plex")), "jellyfin" to null))
    assertEquals(listOf("new plex", "old jellyfin"), merged.map { it.title })
    assertEquals(listOf("old plex", "new jellyfin"), mergeMediaServerRows(before, linkedMapOf("plex" to emptyList(), "jellyfin" to listOf(row("jellyfin", 0, "new jellyfin")))).map { it.title })
    // A provider no longer linked is not asked, and its rows go.
    assertEquals(listOf("old plex"), mergeMediaServerRows(before, mapOf("plex" to null)).map { it.title })
  }

  private fun isMediaServerHomeRowSourceOf(id: String) = id.split(':').getOrNull(1)?.startsWith("mediaserver.") == true

  @Test fun `Plex lists are recognised so they page through the server and wear its mark`() {
    assertTrue(isMediaServerBrowseRowId(mediaServerHomeRowId("plex", "server-1", "movie", "library-1", 3)))
    assertTrue(isMediaServerBrowseRowId(MEDIA_SERVER_COLLECTION_ROW_PREFIX + plexId("77")))
    assertFalse(isMediaServerBrowseRowId("addon:com.example.addon:movie:top:0"))
    assertFalse(isMediaServerBrowseRowId("trending_movies"))
  }

  @Test fun `media server ids are recognised and nothing else is`() {
    assertTrue(isMediaServerId(plexId("1")))
    assertFalse(isMediaServerId("tt0133093"))
    assertFalse(isMediaServerId(null))
  }

  @Test
  fun `view all reads past a stretch of titles already shown and carries on from there`() = kotlinx.coroutines.runBlocking {
    val shown = (0 until 20).map { item("s$it") }
    // A series added to again shows up twice in the server's order: s5-s14 come round a second time.
    val server = (0 until 20).map { item("s$it") } + (5 until 15).map { item("s$it") } + (0 until 40).map { item("n$it") }
    val asked = mutableListOf<Int>()
    fun read(start: Int): net.streamdek.mobile.nativeapp.mediaserver.MediaServerPage {
      asked += start
      val slice = server.drop(start).take(10)
      return net.streamdek.mobile.nativeapp.mediaserver.MediaServerPage(slice, start, server.size)
    }
    // The stretch after the 20 shown holds nothing new, so it is read past rather than taken as the end.
    val first = MediaServerRowPaging.more("row-test", 20, shown) { read(it) }
    assertEquals((0 until 10).map { "n$it" }, first.map { it.id })
    assertEquals(listOf(20, 30), asked)
    // The list now holds 30; the next read starts where the server's order stopped (40), not at 30.
    val second = MediaServerRowPaging.more("row-test", 30, shown + first) { read(it) }
    assertEquals((10 until 20).map { "n$it" }, second.map { it.id })
    assertEquals(40, asked.last())
  }

  @Test
  fun `a plex title page keeps its own identity and takes what it lacks from the catalogue`() {
    fun detail(id: String, title: String, seasons: Int, logo: String?, similar: List<MediaItem>, cast: List<CastMember>) = MediaDetail(
      id = id, type = "tv", title = title, titleLogo = logo, tagline = null, year = "2011", releaseDate = null,
      description = "", poster = "plex-poster", backdrop = null, trailerUrl = null, rating = null, imdbRating = null,
      tmdbRating = null, genres = emptyList(), runtimeMinutes = null, seasonsCount = seasons, imdbId = null,
      seasons = (1..seasons).map { SeasonSummary(seasonNumber = it, name = "Season $it", episodeCount = 10, poster = null, airDate = null) },
      cast = cast, similarTitles = similar,
    )
    val plex = detail(plexId("7"), "My Show", 2, null, emptyList(), emptyList())
    val catalog = detail("1399", "Catalogue Show", 8, "logo.png", listOf(item("99")), listOf(CastMember(id = "1", name = "Actor", character = null, photo = null)))
      .copy(description = "From the catalogue", poster = "tmdb-poster", backdrop = "tmdb-backdrop", imdbId = "tt1")
    val merged = plex.enrichedFromCatalog(catalog)
    assertEquals(plex.id, merged.id)
    assertEquals("My Show", merged.title)
    assertEquals(2, merged.seasons.size)
    assertEquals("plex-poster", merged.poster)
    assertEquals("logo.png", merged.titleLogo)
    assertEquals("tmdb-backdrop", merged.backdrop)
    assertEquals("From the catalogue", merged.description)
    assertEquals(listOf("99"), merged.similarTitles.map { it.id })
    assertEquals(1, merged.cast.size)
    assertEquals("tt1", merged.imdbId)
  }

  @Test
  fun `jellyfin credentials never travel with a stored or handed-off stream`() {
    val headers = mapOf("Authorization" to "MediaBrowser Client=\"StreamDek\", Token=\"secret\"", "User-Agent" to "x", "X-Plex-Token" to "p")
    assertEquals(mapOf("User-Agent" to "x"), withoutMediaServerHeaders(headers))
    val basic = mapOf("Authorization" to "Basic abc")
    assertEquals(basic, withoutMediaServerHeaders(basic))
  }

  @Test
  fun `a row or list id names its provider`() {
    val jellyfinRow = mediaServerHomeRowId("jellyfin", "srv", "movie", "library-abc", 0)
    assertEquals("jellyfin", mediaServerProviderOfRowId(jellyfinRow))
    val collection = MEDIA_SERVER_COLLECTION_ROW_PREFIX + MediaServerReference("jellyfin", "srv", "box1").encode()
    assertEquals("jellyfin", mediaServerProviderOfRowId(collection))
    assertEquals(null, mediaServerProviderOfRowId("addon:com.example:movie:top:0"))
    assertTrue(isMediaServerBrowseRowId(jellyfinRow))
  }

  @Test
  fun `a jellyfin film maps to a card with its ids remembered`() {
    val context = net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinMappingContext(serverId = "srv", baseUrl = "http://10.0.0.2:8096", attribution = "Jellyfin")
    val film = net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinItem(
      id = "m1", name = "Film", type = "Movie", productionYear = 1999,
      providerIds = mapOf("Tmdb" to "603", "Imdb" to "tt0133093"),
      imageTags = mapOf("Primary" to "t"),
    )
    val card = net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinMapping.item(film, context)!!
    assertEquals("Film", card.title)
    assertEquals("1999", card.year)
    assertEquals(603, MediaServerIdentities.of(card.id)?.tmdbId)
    assertEquals("tt0133093", MediaServerIdentities.of(card.id)?.imdbId)
    assertEquals("jellyfin", MediaServerReference.providerOfSource(card.sourceAddonId))
  }
}
