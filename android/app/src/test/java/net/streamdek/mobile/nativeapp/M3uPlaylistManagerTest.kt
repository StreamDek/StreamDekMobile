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

  @Test fun capturesClearKeyDrmWhenKodipropPrecedesExtinf() {
    // Real playlists (e.g. Tamil IPTV lists) put #KODIPROP directives before the #EXTINF line
    // they belong to, not after.
    val playlist = """
      #EXTM3U
      #KODIPROP:inputstream.adaptive.license_type=clearkey
      #KODIPROP:inputstream.adaptive.license_key=3891557F1CB14DEDB7545BF52499D748:FB662F742E5F5E0C61A7C1C66D2B019A
      #EXTINF:-1 group-title="Entertainment",Sun TV HD
      https://livestream.test/SunTVHDB_IN_index.mpd
    """.trimIndent()

    val items = parseM3u(playlist, "m3u:drm")

    assertEquals(1, items.size)
    assertEquals("clearkey", items[0].drmLicenseType)
    assertEquals(
      "fb662f742e5f5e0c61a7c1c66d2b019a",
      items[0].drmClearKeys["3891557f1cb14dedb7545bf52499d748"],
    )
  }

  @Test fun capturesClearKeyDrmWhenKodipropFollowsExtinf() {
    val playlist = """
      #EXTM3U
      #EXTINF:-1 group-title="Entertainment",Star Vijay HD
      #KODIPROP:inputstream.adaptive.license_type=clearkey
      #KODIPROP:inputstream.adaptive.license_key=25dd000e5523540cbd82ae7957fef7d7:f1d145d84b648242ef0b3ad2cac7eeb8
      #EXTVLCOPT:http-user-agent=plaYtv/7.1.3
      https://stream.test/jtv/Star_Vijay_HD.mpd
    """.trimIndent()

    val items = parseM3u(playlist, "m3u:drm")

    assertEquals(1, items.size)
    assertEquals("clearkey", items[0].drmLicenseType)
    assertEquals("f1d145d84b648242ef0b3ad2cac7eeb8", items[0].drmClearKeys["25dd000e5523540cbd82ae7957fef7d7"])
    assertEquals("plaYtv/7.1.3", items[0].requestHeaders["User-Agent"])
  }

  @Test fun parsesMultipleClearKeyPairsAndDoesNotLeakBetweenEntries() {
    val playlist = """
      #EXTM3U
      #KODIPROP:inputstream.adaptive.license_type=clearkey
      #KODIPROP:inputstream.adaptive.license_key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa:11111111111111111111111111111111&bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb:22222222222222222222222222222222
      #EXTINF:-1 group-title="Entertainment",Multi Key Channel
      https://stream.test/multikey.mpd
      #EXTINF:-1 group-title="Entertainment",Plain Channel
      https://stream.test/plain.mpd
    """.trimIndent()

    val items = parseM3u(playlist, "m3u:drm")

    assertEquals(2, items.size)
    assertEquals(2, items[0].drmClearKeys.size)
    assertEquals("11111111111111111111111111111111", items[0].drmClearKeys["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"])
    assertEquals("22222222222222222222222222222222", items[0].drmClearKeys["bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"])
    // The second entry has no KODIPROP of its own - it must not inherit the first entry's keys.
    assertEquals(null, items[1].drmLicenseType)
    assertTrue(items[1].drmClearKeys.isEmpty())
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
