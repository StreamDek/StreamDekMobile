package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
  fun `stored player names are normalized safely`() {
    assertEquals("Auto", normalizePlayerEngineSetting("unknown"))
    assertEquals("Auto", normalizePlayerEngineSetting("auto"))
    assertEquals("Media3", normalizePlayerEngineSetting("ExoPlayer"))
    assertEquals("Media3", normalizePlayerEngineSetting("media3"))
    assertEquals("MPV", normalizePlayerEngineSetting("mpv"))
  }

  @Test
  fun `source fallback skips current and already failed streams`() {
    val first = stream("first")
    val second = stream("second")
    val third = stream("third")

    assertEquals(second, nextUntriedPlaybackSource(listOf(first, second, third), first, emptySet()))
    assertEquals(third, nextUntriedPlaybackSource(listOf(first, second, third), second, setOf(playerStreamIdentity(first))))
    assertEquals(null, nextUntriedPlaybackSource(listOf(first, second), second, setOf(playerStreamIdentity(first))))
  }

  @Test
  fun `a source the viewer reached down the list for falls to the one below it`() {
    val streams = (1..9).map { stream("source$it") }
    val picked = streams[7]

    // The reported fault, in one line: the viewer picks the eighth source, it drops, and the app
    // plays the first - because every source above their choice was still untried.
    assertEquals(streams[8], nextUntriedPlaybackSource(streams, picked, emptySet()))
    // And on down the list from there, rather than back to the top after each one.
    assertEquals(
      streams[8],
      nextUntriedPlaybackSource(streams, picked, setOf(playerStreamIdentity(streams[0]))),
    )
  }

  @Test
  fun `the list above is still tried once nothing below is left`() {
    val first = stream("first")
    val second = stream("second")
    val third = stream("third")

    // Nothing below the last source, so recovery goes back up rather than giving up on playback.
    assertEquals(first, nextUntriedPlaybackSource(listOf(first, second, third), third, emptySet()))
    assertEquals(
      second,
      nextUntriedPlaybackSource(listOf(first, second, third), third, setOf(playerStreamIdentity(first))),
    )
  }

  @Test
  fun `a stream that is no longer in the list still walks the whole list`() {
    val first = stream("first")
    val second = stream("second")
    val missing = stream("missing")

    assertEquals(first, nextUntriedPlaybackSource(listOf(first, second), missing, emptySet()))
  }

  private fun stream(id: String) = AddonStream(
    addonId = "addon", addonName = "Addon", name = id, title = id,
    description = null, url = "https://video.test/$id.m3u8", infoHash = null,
    fileIdx = null, filename = null, quality = "1080p", size = null, cachedBy = emptyList(),
  )
}
