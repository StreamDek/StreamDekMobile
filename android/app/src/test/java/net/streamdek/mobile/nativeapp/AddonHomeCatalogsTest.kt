package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AddonHomeCatalogsTest {
  private fun newsio() = InstalledAddon(
    id = "newsio", enabled = true, position = 0,
    manifest = AddonManifest(
      id = "org.example.newsio", name = "Newsio", version = "1", description = null, logo = null,
      catalogs = parseAddonCatalogs(JSONArray("""[
        {"type":"news","id":"technology","name":"Technology","extra":[{"name":"skip"}]},
        {"type":"news","id":"top","name":"Top Stories","extra":[{"name":"skip"}]},
        {"type":"news","id":"sports","name":"Sports","extra":[{"name":"skip"}]},
        {"type":"news","id":"search","name":"News","extra":[{"name":"search","isRequired":true}]}
      ]""")),
    ),
  )

  @Test fun `custom news catalogs appear in Home settings and retain their protocol type`() {
    val addon = newsio()
    val rows = addonHomeCatalogCandidates(listOf(addon))
    assertEquals(listOf("Technology", "Top Stories", "Sports"), rows.map { it.title })
    assertEquals(listOf("addon:newsio:news:technology:0", "addon:newsio:news:top:1", "addon:newsio:news:sports:2"), rows.map { it.id })
    assertTrue(rows.all { it.enabled && !it.builtin })
    assertEquals(rows.map { it.id }, mergeHomeCatalogRows(emptyList(), listOf(addon), emptyList()).filterNot { it.builtin }.map { it.id })
    assertEquals("unknown", mapHomeCatalogType("news"))
    assertEquals("unknown", MediaClassification.item("news", "news", "yt_example"))
  }

  @Test fun `search-only catalogs stay searchable but are excluded from previews`() {
    val search = newsio().manifest.catalogs.last()
    assertTrue(search.supportsSearch)
    assertTrue(search.requiresSearch)
    assertFalse(search.hasHomePreview)
    val legacy = parseAddonCatalogs(JSONArray("""[{"type":"news","id":"search","extraSupported":["search"],"extraRequired":["search"]}]""")).single()
    assertTrue(legacy.requiresSearch)
    assertFalse(legacy.hasHomePreview)
    assertTrue(search.copy(requiresSearch = false).hasHomePreview)
  }

  @Test fun `row switches and ordering survive discovery and disabled addons stay hidden`() {
    val addon = newsio()
    val saved = addonHomeCatalogCandidates(listOf(addon)).reversed().map { it.copy(enabled = false) }
    val merged = mergeHomeCatalogRows(saved, listOf(addon), emptyList()).filterNot { it.builtin }
    assertEquals(saved, merged)
    assertTrue(addonHomeCatalogCandidates(listOf(addon.copy(enabled = false))).isEmpty())
    assertFalse(AddonCatalog("", "invalid", "Invalid").hasHomePreview)
  }
}
