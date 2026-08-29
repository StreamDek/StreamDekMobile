package net.streamdek.mobile.nativeapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddonMetaParserTest {
  @Test
  fun parsesCncVerseCanonicalMetaAndOpaqueEpisodeIds() {
    val payload = JSONObject(
      """
      {
        "meta": {
          "id": "cnc:parent",
          "type": "other",
          "name": "Musafir Cafe",
          "description": "A real synopsis",
          "year": 2026,
          "videos": [
            {
              "id": "cnc:opaque-episode-id",
              "title": "Arrival",
              "season": 1,
              "episode": 1,
              "thumbnail": "https://example.com/episode.jpg"
            }
          ]
        }
      }
      """.trimIndent(),
    )

    val meta = parseLocalAddonMetaResponse(payload, rawType = "other", fallbackId = "catalog-id")

    assertEquals("cnc:parent", meta.id)
    assertEquals("Musafir Cafe", meta.title)
    assertEquals("A real synopsis", meta.description)
    assertEquals("2026", meta.year)
    assertEquals("tv", meta.type)
    assertEquals(1, meta.episodes.size)
    assertEquals("cnc:opaque-episode-id", meta.episodes.single().sourceStreamId)
    assertTrue(meta.episodes.single().id.startsWith("cnc:"))
  }

  @Test
  fun parsesProviderOverviewWhenDescriptionIsMissing() {
    val payload = JSONObject(
      """{"meta":{"id":"tmdb:12345","type":"movie","name":"Example","overview":"A provider synopsis."}}""",
    )

    val meta = parseLocalAddonMetaResponse(payload, rawType = "movie", fallbackId = "tmdb:12345")

    assertEquals("A provider synopsis.", meta.description)
  }

  @Test
  fun stripsBrokenHlsSubtitleRenditionsWithoutChangingVideoOrAudio() {
    val manifest = """
      #EXTM3U
      #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",URI="https://subs.example/en.m3u8"
      #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",URI="https://media.example/audio.m3u8"
      #EXT-X-STREAM-INF:BANDWIDTH=1000,AUDIO="audio",SUBTITLES="subs"
      https://media.example/video.m3u8
    """.trimIndent()

    val sanitized = stripHlsSubtitleRenditions(manifest)

    assertTrue("TYPE=SUBTITLES" !in sanitized)
    assertTrue("SUBTITLES=\"subs\"" !in sanitized)
    assertTrue("TYPE=AUDIO" in sanitized)
    assertTrue("https://media.example/video.m3u8" in sanitized)
  }

  @Test
  fun preservesEveryAudioRenditionForPlayerLanguageSelection() {
    val manifest = """
      #EXTM3U
      #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",URI="subs/en.m3u8"
      #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",LANGUAGE="hin",DEFAULT=NO,URI="audio/hi.m3u8"
      #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",LANGUAGE="eng",DEFAULT=YES,URI="audio/en.m3u8"
      #EXT-X-STREAM-INF:BANDWIDTH=1000,AUDIO="audio"
      video.m3u8
    """.trimIndent()

    val sanitized = stripHlsSubtitleRenditions(manifest, "https://cdn.example/master.m3u8")

    assertTrue("https://cdn.example/audio/hi.m3u8" in sanitized)
    assertTrue("https://cdn.example/audio/en.m3u8" in sanitized)
    assertTrue("https://cdn.example/video.m3u8" in sanitized)
  }

  @Test
  fun resolvesRelativeHlsRenditionsForCachedMaster() {
    val manifest = """
      #EXTM3U
      #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",URI="subs/en.m3u8"
      #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",URI="audio/en.m3u8"
      #EXT-X-STREAM-INF:BANDWIDTH=1000,AUDIO="audio",SUBTITLES="subs"
      video/1080p.m3u8
    """.trimIndent()

    val sanitized = stripHlsSubtitleRenditions(manifest, "https://cdn.example/path/master.m3u8")

    assertTrue("TYPE=SUBTITLES" !in sanitized)
    assertTrue("URI=\"https://cdn.example/path/audio/en.m3u8\"" in sanitized)
    assertTrue("https://cdn.example/path/video/1080p.m3u8" in sanitized)
  }
}
