package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerInteractionPolicyTest {
  @Test
  fun `double tap regions do not overlap and honor the configured seek step`() {
    assertEquals(-5, playerDoubleTapSeekDelta(0.1f, enabled = true, stepSeconds = 5, seekable = true))
    assertNull(playerDoubleTapSeekDelta(0.5f, enabled = true, stepSeconds = 10, seekable = true))
    assertEquals(15, playerDoubleTapSeekDelta(0.9f, enabled = true, stepSeconds = 15, seekable = true))
    assertTrue(isPlayerCenterDoubleTap(0.5f, enabled = true))
    assertFalse(isPlayerCenterDoubleTap(0.9f, enabled = true))
  }

  @Test
  fun `double tap seeking is disabled for a non seekable live stream`() {
    assertNull(playerDoubleTapSeekDelta(0.1f, enabled = true, stepSeconds = 10, seekable = false))
    assertNull(playerDoubleTapSeekDelta(0.9f, enabled = false, stepSeconds = 10, seekable = true))
  }

  @Test
  fun `double tap seek positions clamp to media bounds`() {
    assertEquals(0.0, clampedPlayerSeekPosition(3.0, -10, 120.0), 0.0)
    assertEquals(120.0, clampedPlayerSeekPosition(117.0, 10, 120.0), 0.0)
  }

  @Test
  fun `source favourite identity ignores expiring urls and headers`() {
    val first = AddonStream(
      addonId = "Example.Addon",
      addonName = "Example Addon",
      source = "Provider",
      name = "Release Name",
      title = null,
      description = null,
      quality = "1080P",
      url = "https://cdn.example/first?token=secret-one",
      infoHash = null,
      fileIdx = null,
      filename = null,
      size = null,
      cachedBy = emptyList(),
      requestHeaders = mapOf("Authorization" to "secret-one"),
    )
    val refreshed = first.copy(
      url = "https://cdn.example/second?token=secret-two",
      requestHeaders = mapOf("Authorization" to "secret-two"),
    )

    assertEquals(stableSourceFavouriteKey(first), stableSourceFavouriteKey(refreshed))
    assertFalse(stableSourceFavouriteKey(first).contains("secret", ignoreCase = true))
  }
}
