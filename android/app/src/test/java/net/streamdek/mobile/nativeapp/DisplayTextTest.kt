package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTextTest {
  @Test fun repairsCommonMojibake() {
    assertEquals("Fire & Blood \u2014 The Dragon", sanitizeDisplayText("Fire & Blood \u00E2\u20AC\u201D The Dragon"))
  }

  @Test fun preservesValidUnicode() {
    assertEquals("Am\u00E9lie \u2022 \u65E5\u672C\u8A9E", sanitizeDisplayText("Am\u00E9lie \u2022 \u65E5\u672C\u8A9E"))
  }

  @Test fun normalizesAudioPreferences() {
    assertEquals("en", normalizePreferredAudioLanguage("English"))
    assertEquals("hi", normalizePreferredAudioLanguage("hin"))
    assertEquals("original", normalizePreferredAudioLanguage("Original"))
    assertEquals("ta", normalizePreferredAudioLanguage("Tamil"))
    assertEquals("zh", normalizePreferredAudioLanguage("Mandarin"))
    assertEquals("vi", normalizePreferredAudioLanguage("Vietnamese"))
  }

  @Test fun usesPlayableIdentityForDuplicateSources() {
    val first = stream(url = "https://example.test/video.m3u8", title = "1080p")
    val duplicate = stream(url = "https://example.test/video.m3u8", title = "Playing")
    assertEquals(addonStreamPlaybackIdentity(first), addonStreamPlaybackIdentity(duplicate))
  }

  private fun stream(url: String, title: String) = AddonStream(
    addonId = "bridge",
    addonName = "CNCVerse Bridge",
    name = null,
    title = title,
    description = null,
    url = url,
    infoHash = null,
    fileIdx = null,
    filename = null,
    quality = "1080p",
    size = null,
    cachedBy = emptyList(),
  )
}
