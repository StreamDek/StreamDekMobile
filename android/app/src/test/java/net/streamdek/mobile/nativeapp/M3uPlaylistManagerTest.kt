package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistManagerTest {
  @Test fun acceptsProviderUrlsCarryingQueryFilters() {
    // The shapes IPTV panels actually hand out. java.net.URI rejects several of these outright,
    // which is what stopped them being addable at all.
    listOf(
      "http://panel.test:8080/get.php?username=abc&password=def&type=m3u_plus&output=ts",
      "https://panel.test/playlist?d=8f3a91c2&type=m3u&output=ts",
      "https://panel.test/get.php?token=a|b&type=m3u&output=hls",
      "https://panel.test/list?ids=[1,2,3]&type=m3u",
      "https://panel.test/playlist.m3u8?u=user name&type=m3u",
    ).forEach { url ->
      assertNotNull("expected $url to be accepted", M3uPlaylistManager.parsePlaylistUrl(url))
    }
  }

  @Test fun rejectsUrlsThatAreNotHttpPlaylists() {
    listOf("", "not a url", "ftp://panel.test/list.m3u", "/local/path.m3u").forEach { url ->
      assertNull("expected $url to be rejected", M3uPlaylistManager.parsePlaylistUrl(url))
    }
  }

  @Test fun parsesPlaylistWithoutExtm3uHeaderWhenEntriesArePresent() {
    // Some panels omit the #EXTM3U line entirely; the entries are still perfectly parseable.
    val items = parseM3uLines(
      sequenceOf(
        "#EXTINF:-1 group-title=\"News\",World News",
        "https://stream.test/live/news.m3u8",
      ),
      "m3u:headerless",
    )

    assertEquals(1, items.size)
    assertEquals("World News", items[0].title)
  }

  @Test fun rejectsBodyThatIsNotAPlaylist() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseM3uLines(sequenceOf("<html>", "<body>Invalid token</body>", "</html>"), "m3u:html")
    }
    assertTrue(error.message.orEmpty().contains("doesn't look like an M3U playlist"))
  }

  @Test fun sharesGroupDerivedStringsAcrossEntriesInTheSameCategory() {
    // A 200k-channel playlist only fits in memory because entries in one category reuse the same
    // description, catalog name and genre list instances rather than allocating per entry.
    val items = parseM3uLines(
      sequence {
        yield("#EXTM3U")
        repeat(50) { index ->
          yield("#EXTINF:-1 group-title=\"Sports\",Channel $index")
          yield("https://stream.test/live/$index.m3u8")
        }
      },
      "m3u:pool",
      "Pool Playlist",
    )

    assertEquals(50, items.size)
    assertTrue(items.all { it.description === items[0].description })
    assertTrue(items.all { it.genres === items[0].genres })
    assertTrue(items.all { it.sourceCatalogId === items[0].sourceCatalogId })
    // Entries with no headers or DRM share one empty map rather than allocating per entry.
    assertTrue(items.all { it.requestHeaders.isEmpty() && it.drmClearKeys.isEmpty() })
  }

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

  @Test fun capturesCookieFromExtvlcopt() {
    val playlist = """
      #EXTM3U
      #EXTINF:-1 group-title="News",Cookie Channel
      #EXTVLCOPT:http-user-agent=Provider Player
      #EXTVLCOPT:http-cookie=__hdnea__=st=1789548446~exp=1789570046~acl=/*~hmac=2e3de672
      https://stream.test/live/index.mpd
    """.trimIndent()

    val headers = parseM3u(playlist, "m3u:cookie").single().requestHeaders

    assertEquals("__hdnea__=st=1789548446~exp=1789570046~acl=/*~hmac=2e3de672", headers["Cookie"])
    assertEquals("Provider Player", headers["User-Agent"])
  }

  @Test fun capturesExtHttpHeadersLiterally() {
    // JSON values are not URL-encoded; a literal %2f in a signed token must survive untouched.
    val playlist = """
      #EXTM3U
      #EXTINF:-1 group-title="Sports",Json Channel
      #EXTHTTP:{"origin":"https://www.provider.test","Referer":"https://www.provider.test/","Cookie":"hdntl=exp=1789629904~acl=%2f*~hmac=68cd","X-Custom":"a, b"}
      https://stream.test/live/master.m3u8
    """.trimIndent()

    val headers = parseM3u(playlist, "m3u:exthttp").single().requestHeaders

    assertEquals("https://www.provider.test", headers["Origin"])
    assertEquals("https://www.provider.test/", headers["Referer"])
    assertEquals("hdntl=exp=1789629904~acl=%2f*~hmac=68cd", headers["Cookie"])
    assertEquals("a, b", headers["X-Custom"])
    assertEquals(4, headers.size)
  }

  @Test fun capturesHeadersAndDrmFromPremiumPlugxStyleEntry() {
    // The shape PremiumPlugX serves: KODIPROP DRM, EXTVLCOPT and EXTHTTP all describing one channel.
    val playlist = """
      #EXTM3U
      #EXTINF:-1 tvg-id="255" group-title="Jio TV+ | News",NDTV 24x7
      #KODIPROP:inputstream=inputstream.adaptive
      #KODIPROP:inputstream.adaptive.manifest_type=mpd
      #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Premium%20Plugx&Cookie=__hdnea__%3Dst%3D1
      #KODIPROP:inputstream.adaptive.license_type=clearkey
      #KODIPROP:inputstream.adaptive.license_key=9b5f31aacf4f57758fb654a54b5aafec:e7ff670f95103a87bdb0ede3689f257b
      #EXTVLCOPT:http-user-agent=Premium Plugx
      #EXTVLCOPT:http-referrer=https://www.jiotv.com/
      #EXTVLCOPT:http-cookie=__hdnea__=st=1~exp=2~acl=/*~hmac=abc
      #EXTHTTP:{"User-Agent":"Premium Plugx","Referer":"https://www.jiotv.com/","Origin":"https://www.jiotv.com/","Cookie":"__hdnea__=st=1~exp=2~acl=/*~hmac=abc"}
      https://jiotvmblive.cdn.jio.com/bpk-tv/NDTV_24x7_MOB/WDVLive/index.mpd
      #EXTINF:-1 group-title="Jio TV+ | News",No Headers Channel
      https://stream.test/plain.m3u8
    """.trimIndent()

    val items = parseM3u(playlist, "m3u:plugx")

    assertEquals(2, items.size)
    assertEquals(
      mapOf(
        "User-Agent" to "Premium Plugx",
        "Referer" to "https://www.jiotv.com/",
        "Cookie" to "__hdnea__=st=1~exp=2~acl=/*~hmac=abc",
        "Origin" to "https://www.jiotv.com/",
      ),
      items[0].requestHeaders,
    )
    assertEquals("clearkey", items[0].drmLicenseType)
    assertEquals("e7ff670f95103a87bdb0ede3689f257b", items[0].drmClearKeys["9b5f31aacf4f57758fb654a54b5aafec"])
    // Headers are per entry and must not carry over.
    assertTrue(items[1].requestHeaders.isEmpty())
  }

  @Test fun inlineSuffixStillWinsWithoutDuplicatingHeaderNames() {
    val playlist = """
      #EXTM3U
      #EXTINF:-1 group-title="Sports",Both Forms
      #EXTHTTP:{"Cookie":"from=json","Referer":"https://json.test/","X-Token":"json"}
      https://stream.test/live.m3u8?|cookie=from%3Dsuffix&referer=https://suffix.test/&x-token=suffix
    """.trimIndent()

    val item = parseM3u(playlist, "m3u:both").single()

    assertEquals("https://stream.test/live.m3u8?", item.directStreamUrl)
    assertEquals(
      mapOf("Cookie" to "from=suffix", "Referer" to "https://suffix.test/", "x-token" to "suffix"),
      item.requestHeaders,
    )
  }

  @Test fun ignoresMalformedOrUnsendableExtHttpValues() {
    val playlist = """
      #EXTM3U
      #EXTINF:-1 group-title="News",Broken Json
      #EXTVLCOPT:http-user-agent=Provider Player
      #EXTHTTP:{"Cookie":"unterminated
      https://stream.test/broken.m3u8
      #EXTINF:-1 group-title="News",Odd Values
      #EXTHTTP:{"Cookie":"a\r\nX-Injected: 1","Nested":{"a":1},"List":[1],"Empty":"","Missing":null,"Port":8080}
      https://stream.test/odd.m3u8
      #EXTINF:-1 group-title="News",Not An Object
      #EXTHTTP:["Cookie","x"]
      https://stream.test/array.m3u8
    """.trimIndent()

    val items = parseM3u(playlist, "m3u:badjson")

    assertEquals(3, items.size)
    assertEquals(mapOf("User-Agent" to "Provider Player"), items[0].requestHeaders)
    assertEquals(mapOf("Port" to "8080"), items[1].requestHeaders)
    assertTrue(items[2].requestHeaders.isEmpty())
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
