package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Why a curated Home layout kept coming back switched fully on.
 *
 * A viewer with seventy add-on catalogues had nineteen of them on, in an order they had arranged.
 * Every restart put all seventy back on in registry order, however many times they set it.
 *
 * The merge forgot any saved row it could not find a candidate for, and the home load runs before
 * the add-on list arrives — so on a cold start every add-on row was dropped, that reduced layout was
 * saved over the good one, and when the add-ons landed a moment later all seventy looked new. New
 * rows default to enabled, which is the "all turned back on" part; they were appended in registry
 * order, which is the "order reset" part.
 *
 * The rule now is that a row is only forgotten when the source it came from is present and has
 * genuinely dropped it. Absence of evidence is not evidence of absence.
 */
class HomeCatalogRowPersistenceTest {

  private fun definition(id: String, title: String = id, mediaType: String = "movie") = CatalogDefinition(
    id = id,
    title = title,
    mediaType = mediaType,
    group = "default",
    previewLimit = 20,
    maxItems = null,
    paginated = true,
  )

  private fun addonRowId(addonId: String, catalogId: String, index: Int, type: String = "movie") =
    "addon:$addonId:$type:$catalogId:$index"

  private fun savedRow(id: String, enabled: Boolean = true) =
    HomeCatalogRow(id = id, title = id, subtitle = "", builtin = false, enabled = enabled)

  private fun testAddon(id: String, catalogIds: List<String>, enabled: Boolean = true) = InstalledAddon(
    id = id,
    enabled = enabled,
    position = 0,
    manifest = AddonManifest(
      id = id,
      name = id,
      version = "1.0.0",
      description = null,
      logo = null,
      catalogs = catalogIds.map { AddonCatalog(type = "movie", id = it, name = it) },
    ),
  )

  @Test
  fun `a cold start with no add-ons loaded keeps the saved layout intact`() {
    val saved = listOf(
      savedRow(addonRowId("xperience", "trending", 0)),
      savedRow(addonRowId("xperience", "popular", 1), enabled = false),
      savedRow(addonRowId("xperience", "classics", 2), enabled = false),
    )

    // What the home load sees on a cold start: the registry has answered, the add-ons have not.
    val rows = mergeHomeCatalogRows(saved, emptyList(), listOf(definition("trending_movies")))

    val addonRows = rows.filter { it.id.startsWith("addon:") }
    assertEquals(3, addonRows.size)
    assertEquals(listOf(true, false, false), addonRows.map { it.enabled })
  }

  @Test
  fun `the switches survive the round trip once the add-ons arrive`() {
    val saved = listOf(
      savedRow(addonRowId("xperience", "trending", 0)),
      savedRow(addonRowId("xperience", "popular", 1), enabled = false),
    )
    val definitions = listOf(definition("trending_movies"))

    // The exact sequence that used to destroy the layout: merge with no add-ons, save that, then
    // merge again when the add-ons land.
    val afterColdStart = mergeHomeCatalogRows(saved, emptyList(), definitions)
    val afterAddonsLoad = mergeHomeCatalogRows(
      afterColdStart,
      listOf(testAddon("xperience", listOf("trending", "popular"))),
      definitions,
    )

    val addonRows = afterAddonsLoad.filter { it.id.startsWith("addon:") }
    assertEquals(listOf(true, false), addonRows.map { it.enabled })
  }

  @Test
  fun `a disabled add-on keeps its rows and their switches`() {
    val saved = listOf(savedRow(addonRowId("xperience", "trending", 0), enabled = false))

    val rows = mergeHomeCatalogRows(
      saved,
      listOf(testAddon("xperience", listOf("trending"), enabled = false)),
      listOf(definition("trending_movies")),
    )

    val addonRows = rows.filter { it.id.startsWith("addon:") }
    assertEquals(1, addonRows.size)
    assertFalse(addonRows.single().enabled)
  }

  @Test
  fun `a catalogue an installed add-on has genuinely dropped is forgotten`() {
    val saved = listOf(
      savedRow(addonRowId("xperience", "trending", 0)),
      savedRow(addonRowId("xperience", "retired", 1)),
    )

    val rows = mergeHomeCatalogRows(
      saved,
      listOf(testAddon("xperience", listOf("trending"))),
      listOf(definition("trending_movies")),
    )

    assertTrue(rows.none { it.id.contains(":retired:") })
    assertTrue(rows.any { it.id.contains(":trending:") })
  }

  @Test
  fun `a switch survives the add-on reordering its own catalogues`() {
    // The row id carries the catalogue's index in the manifest, so adding one catalogue ahead of
    // another used to renumber every id after it and lose every switch with them.
    val saved = listOf(
      savedRow(addonRowId("xperience", "trending", 0), enabled = false),
      savedRow(addonRowId("xperience", "popular", 1)),
    )

    val rows = mergeHomeCatalogRows(
      saved,
      listOf(testAddon("xperience", listOf("brand-new", "trending", "popular"))),
      listOf(definition("trending_movies")),
    )

    val trending = rows.single { it.id.contains(":trending:") }
    assertFalse("the viewer switched this off and it must stay off", trending.enabled)
  }

  @Test
  fun `the match key ignores the manifest position and nothing else`() {
    assertEquals(
      homeCatalogRowMatchKey("addon:xperience:movie:trending:0"),
      homeCatalogRowMatchKey("addon:xperience:movie:trending:7"),
    )
    // A different catalogue, a different add-on and a different type all stay distinct.
    assertTrue(
      homeCatalogRowMatchKey("addon:xperience:movie:trending:0") !=
        homeCatalogRowMatchKey("addon:xperience:movie:popular:0"),
    )
    assertTrue(
      homeCatalogRowMatchKey("addon:xperience:movie:trending:0") !=
        homeCatalogRowMatchKey("addon:other:movie:trending:0"),
    )
    assertTrue(
      homeCatalogRowMatchKey("addon:xperience:movie:trending:0") !=
        homeCatalogRowMatchKey("addon:xperience:tv:trending:0"),
    )
    // A built-in id is not an add-on id and is left exactly as it is.
    assertEquals("trending_movies", homeCatalogRowMatchKey("trending_movies"))
  }

  @Test
  fun `the add-on behind a row is read from its id`() {
    assertEquals("xperience", homeCatalogRowAddonId("addon:xperience:movie:trending:0"))
    assertEquals(null, homeCatalogRowAddonId("trending_movies"))
  }

  @Test
  fun `no row is ever listed twice after a reorder collapses two saved ids`() {
    val saved = listOf(
      savedRow(addonRowId("xperience", "trending", 0)),
      savedRow(addonRowId("xperience", "trending", 4)),
    )

    val rows = mergeHomeCatalogRows(
      saved,
      listOf(testAddon("xperience", listOf("trending"))),
      listOf(definition("trending_movies")),
    )

    assertEquals(rows.size, rows.map { it.id }.distinct().size)
  }
}
