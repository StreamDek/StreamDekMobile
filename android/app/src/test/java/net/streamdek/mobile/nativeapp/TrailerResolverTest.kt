package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerResolverTest {
  @Test
  fun trailerResolutionSupports4kAndClampsOutOfRangeValues() {
    assertEquals(360, normalizeTrailerMaxHeight(144))
    assertEquals(2160, normalizeTrailerMaxHeight(2160))
    assertEquals(2160, normalizeTrailerMaxHeight(4320))
  }

  private fun formats(vararg entries: Triple<String, String, Int>): JSONArray {
    val array = JSONArray()
    entries.forEach { (url, mime, height) ->
      array.put(JSONObject().put("url", url).put("mimeType", mime).put("height", height))
    }
    return array
  }

  private fun audio(vararg entries: Pair<String, Pair<String, Int>>): JSONArray {
    val array = JSONArray()
    entries.forEach { (url, spec) ->
      array.put(JSONObject().put("url", url).put("mimeType", spec.first).put("bitrate", spec.second))
    }
    return array
  }

  @Test
  fun `reaches 2160p through vp9 when avc stops at 1080p`() {
    // YouTube publishes nothing above 1080p in AVC, so accepting only avc1 was what capped
    // trailers at 1080p no matter how the resolution setting was configured.
    val available = formats(
      Triple("avc1080", "video/mp4; codecs=\"avc1.640028\"", 1080),
      Triple("vp9-2160", "video/webm; codecs=\"vp9\"", 2160),
      Triple("av1-1440", "video/mp4; codecs=\"av01.0.12M.08\"", 1440),
    )
    assertEquals("vp9-2160" to 2160, selectAdaptiveVideo(available, 2160))
  }

  @Test
  fun `prefers avc at the same height for decoder compatibility`() {
    val available = formats(
      Triple("vp9-1080", "video/webm; codecs=\"vp9\"", 1080),
      Triple("avc-1080", "video/mp4; codecs=\"avc1.640028\"", 1080),
    )
    assertEquals("avc-1080" to 1080, selectAdaptiveVideo(available, 2160))
  }

  @Test
  fun `never exceeds the configured resolution`() {
    val available = formats(
      Triple("vp9-2160", "video/webm; codecs=\"vp9\"", 2160),
      Triple("avc-720", "video/mp4; codecs=\"avc1.640028\"", 720),
    )
    assertEquals("avc-720" to 720, selectAdaptiveVideo(available, 720))
  }

  @Test
  fun `ranks codecs avc over vp9 over av1`() {
    assertEquals(3, trailerCodecRank("video/mp4; codecs=\"avc1.640028\""))
    assertEquals(2, trailerCodecRank("video/webm; codecs=\"vp9\""))
    assertEquals(1, trailerCodecRank("video/mp4; codecs=\"av01.0.12M.08\""))
    assertEquals(0, trailerCodecRank("video/x-unknown"))
  }

  @Test
  fun `ignores video formats in codecs the player cannot take`() {
    assertNull(selectAdaptiveVideo(formats(Triple("weird", "video/x-unknown", 2160)), 2160))
  }

  @Test
  fun `falls back to webm audio so a vp9 pick still has sound`() {
    assertEquals("opus", selectAdaptiveAudio(audio("opus" to ("audio/webm; codecs=\"opus\"" to 160000))))
  }

  @Test
  fun `prefers m4a audio over webm even at a lower bitrate`() {
    val available = audio(
      "opus" to ("audio/webm; codecs=\"opus\"" to 160000),
      "m4a" to ("audio/mp4; codecs=\"mp4a.40.2\"" to 128000),
    )
    assertEquals("m4a", selectAdaptiveAudio(available))
  }
}
