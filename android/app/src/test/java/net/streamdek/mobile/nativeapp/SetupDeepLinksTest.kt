package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupDeepLinksTest {
  @Test
  fun `parses supported setup destinations`() {
    assertEquals("register", normalizeSetupDestination("streamdek://setup/register"))
    assertEquals("profiles/create", normalizeSetupDestination("streamdek://setup/profiles/create"))
    assertEquals("content-services", normalizeSetupDestination("streamdek://setup/content-services"))
  }

  @Test
  fun `rejects unrelated and unknown links`() {
    assertNull(normalizeSetupDestination("https://streamdek.com/getting-started"))
    assertNull(normalizeSetupDestination("streamdek://setup/not-a-page"))
    assertNull(normalizeSetupDestination("stremio://example.com/manifest.json"))
  }
}
