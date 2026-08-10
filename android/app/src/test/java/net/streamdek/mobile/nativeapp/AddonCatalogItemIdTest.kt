package net.streamdek.mobile.nativeapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AddonCatalogItemIdTest {
  @Test
  fun `canonical addon id wins over tmdb enrichment id`() {
    val item = JSONObject()
      .put("id", "tmdb:tv:1399")
      .put("tmdbId", 1399)

    assertEquals("tmdb:tv:1399", parseAddonCatalogItemId(item))
  }

  @Test
  fun `tmdb id remains a fallback when addon omits id`() {
    val item = JSONObject().put("tmdbId", 1399)

    assertEquals("1399", parseAddonCatalogItemId(item))
  }
}
