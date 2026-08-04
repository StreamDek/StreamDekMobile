package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistManagerTest {
  @Test fun separatesLiveAndVodEntriesAndPreservesHeaders() {
    val playlist = """
      #EXTM3U
      #EXTINF:-1 tvg-logo="https://img.test/news.png" group-title="News",World News
      #EXTVLCOPT:http-user-agent=Provider Player
      https://stream.test/live/news.m3u8|Referer=https%3A%2F%2Fprovider.test%2F
      #EXTINF:7200 group-title="Movies",Example Film
      https://stream.test/movie/account/token/42.mkv
      #EXTINF:-1 group-title="Series",Example Show S02E03
      https://stream.test/series/account/token/23.ts
    """.trimIndent()

    val items = parseM3u(playlist, "m3u:test", "Test Playlist")

    assertEquals(3, items.size)
    assertEquals("tv", items[0].type)
    assertEquals("movie", items[1].type)
    assertEquals("movie", items[2].type)
    assertEquals("Provider Player", items[0].requestHeaders["User-Agent"])
    assertEquals("https://provider.test/", items[0].requestHeaders["Referer"])
  }

  @Test fun acceptsM3u8PlaylistBodyRegardlessOfSourceUrlExtension() {
    // Real IPTV panels commonly serve playlist bodies from extensionless or `.m3u8` URLs
    // (e.g. Xtream Codes `get.php?...&output=m3u8`); parsing only depends on the body's
    // `#EXTM3U` marker, never on the source URL's file extension.
    val playlist = """
      #EXTM3U
      #EXTINF:-1 group-title="Sports",Sports Channel
      https://stream.test/live/sports.m3u8
      #EXTINF:5400 group-title="Movies",Feature Film
      https://stream.test/get.php?username=u&password=p&stream=99&output=m3u8
    """.trimIndent()

    val items = parseM3u(playlist, "m3u:xtream", "Xtream Playlist")

    assertEquals(2, items.size)
    assertEquals("tv", items[0].type)
    assertEquals("movie", items[1].type)
  }

  @Test fun reportsIncrementalProgressForLargePlaylists() {
    val playlist = buildString {
      appendLine("#EXTM3U")
      repeat(2_500) { index ->
        appendLine("#EXTINF:-1 group-title=\"Live\",Channel $index")
        appendLine("https://stream.test/live/$index.m3u8")
      }
    }
    val reports = mutableListOf<Triple<Int, Int, Int>>()

    val items = parseM3u(playlist, "m3u:large") { parsed, processed, total ->
      reports += Triple(parsed, processed, total)
    }

    assertEquals(2_500, items.size)
    assertTrue(reports.size > 2)
    assertEquals(2_500, reports.last().first)
    assertEquals(reports.last().second, reports.last().third)
  }
}
