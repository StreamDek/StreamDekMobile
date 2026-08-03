package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class PlaybackEnginePolicyTest {
  @Test
  fun `auto and media3 start with media3 while mpv starts with mpv`() {
    assertEquals(ActivePlaybackEngine.Media3, initialPlaybackEngine("Auto"))
    assertEquals(ActivePlaybackEngine.Media3, initialPlaybackEngine("Media3"))
    assertEquals(ActivePlaybackEngine.MPV, initialPlaybackEngine("MPV"))
  }

  @Test
  fun `auto falls back only once and only from media3`() {
    assertTrue(shouldAutoFallbackToMpv("Auto", ActivePlaybackEngine.Media3, fallbackUsed = false))
    assertFalse(shouldAutoFallbackToMpv("Auto", ActivePlaybackEngine.Media3, fallbackUsed = true))
    assertFalse(shouldAutoFallbackToMpv("Auto", ActivePlaybackEngine.MPV, fallbackUsed = false))
    assertFalse(shouldAutoFallbackToMpv("Media3", ActivePlaybackEngine.Media3, fallbackUsed = false))
    assertFalse(shouldAutoFallbackToMpv("MPV", ActivePlaybackEngine.MPV, fallbackUsed = false))
  }

  @Test
  fun `adaptive trailer selection supports 4k and respects the configured cap`() {
    val formats = JSONArray()
      .put(JSONObject().put("url", "https://video.test/1080.mp4").put("mimeType", "video/mp4; codecs=\"avc1\"").put("height", 1080))
      .put(JSONObject().put("url", "https://video.test/2160-av1.webm").put("mimeType", "video/webm; codecs=\"av01\"").put("height", 2160))
      .put(JSONObject().put("url", "https://video.test/2160-vp9.webm").put("mimeType", "video/webm; codecs=\"vp09\"").put("height", 2160))

    assertEquals("https://video.test/2160-vp9.webm", selectAdaptiveVideo(formats, 2160)?.first)
    assertEquals("https://video.test/1080.mp4", selectAdaptiveVideo(formats, 1080)?.first)
  }

  @Test
  fun `trailer source prefers adaptive 4k over capped progressive video`() {
    val progressive = JSONArray()
      .put(JSONObject().put("url", "https://video.test/720.mp4").put("mimeType", "video/mp4; codecs=\"avc1\"").put("height", 720).put("audioChannels", 2))
    val adaptive = JSONArray()
      .put(JSONObject().put("url", "https://video.test/2160.webm").put("mimeType", "video/webm; codecs=\"vp09\"").put("height", 2160))
      .put(JSONObject().put("url", "https://audio.test/track.m4a").put("mimeType", "audio/mp4").put("bitrate", 128000))

    val selected = selectTrailerSource(progressive, adaptive, 2160)

    assertEquals("https://video.test/2160.webm", selected?.url)
    assertEquals("https://audio.test/track.m4a", selected?.audioUrl)
    assertEquals(2160, selected?.height)
  }

  @Test
  fun `vertical player gestures clamp brightness and volume levels`() {
    assertEquals(1f, adjustedPlayerLevel(0.5f, -1_000f, 1_000f), 0.001f)
    assertEquals(0f, adjustedPlayerLevel(0.5f, 1_000f, 1_000f), 0.001f)
    assertEquals(0.5f, adjustedPlayerLevel(0.5f, 0f, 1_000f), 0.001f)
  }

  @Test
  fun `stored player names are normalized safely`() {
    assertEquals("Auto", normalizePlayerEngineSetting("unknown"))
    assertEquals("Auto", normalizePlayerEngineSetting("auto"))
    assertEquals("Media3", normalizePlayerEngineSetting("ExoPlayer"))
    assertEquals("Media3", normalizePlayerEngineSetting("media3"))
    assertEquals("MPV", normalizePlayerEngineSetting("mpv"))
  }
}
