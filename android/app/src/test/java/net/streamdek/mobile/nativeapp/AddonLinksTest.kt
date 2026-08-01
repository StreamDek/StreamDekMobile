package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddonLinksTest {
  @Test
  fun convertsStremioManifestLinksToHttps() {
    assertEquals(
      "https://example.com/user/config/manifest.json?token=abc",
      normalizeAddonManifestUrl("stremio://example.com/user/config/manifest.json?token=abc"),
    )
  }

  @Test
  fun preservesWebManifestLinks() {
    assertEquals(
      "https://example.com/manifest.json",
      normalizeAddonManifestUrl("https://example.com/manifest.json"),
    )
  }

  @Test
  fun rejectsNonManifestAndUntrustedSchemes() {
    assertNull(normalizeAddonManifestUrl("stremio://example.com/configure"))
    assertNull(normalizeAddonManifestUrl("javascript://example.com/manifest.json"))
  }
}
