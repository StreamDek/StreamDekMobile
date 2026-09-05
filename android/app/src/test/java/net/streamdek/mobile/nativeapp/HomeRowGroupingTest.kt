package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Why the Home rows settings list showed several identical "Add-on" groups.
 *
 * The saved layout is only a list of ids and on/off flags. A row's title and the add-on it belongs
 * to are rebuilt at load time from whichever add-ons are currently installed, and the rebuild only
 * has candidates for add-ons that are switched **on** -- so a row from a switched-off add-on
 * arrives as a bare stub with an empty subtitle. Naming a group from that subtitle therefore gave
 * "Add-on" for exactly the add-ons the viewer had turned off, and several of them at once read as
 * duplicates.
 *
 * The name has to come from the installed add-on list, which carries disabled add-ons too, and is
 * also what says whether a group is switched off.
 */
class HomeRowGroupingTest {

  private companion object {
    /** What the settings screen passes; the wording itself lives in string resources now. */
    const val ORPHAN_TITLE = "No longer installed"
    const val FALLBACK_ADDON_NAME = "Add-on"
  }

  private fun addonRowId(addonId: String, catalogId: String, index: Int, type: String = "movie") =
    "addon:$addonId:$type:$catalogId:$index"

  /** A row as it comes back off disk: id and flag only, no title and no subtitle. */
  private fun savedAddonRow(addonId: String, catalogId: String, index: Int, enabled: Boolean = true) =
    HomeCatalogRow(id = addonRowId(addonId, catalogId, index), title = "", subtitleRes = null, builtin = false, enabled = enabled)

  private fun builtinRow(id: String, enabled: Boolean = true) =
    HomeCatalogRow(id = id, title = id, subtitleRes = null, builtin = true, enabled = enabled)

  private fun testAddon(id: String, name: String, enabled: Boolean = true) = InstalledAddon(
    id = id,
    enabled = enabled,
    position = 0,
    manifest = AddonManifest(
      id = id,
      name = name,
      version = "1.0.0",
      description = null,
      logo = null,
      catalogs = emptyList(),
    ),
  )

  @Test
  fun `a switched-off add-on still gets its name, not a generic label`() {
    val rows = listOf(
      savedAddonRow("uuid-a", "trending", 0),
      savedAddonRow("uuid-a", "popular", 1),
    )
    val addons = listOf(testAddon("uuid-a", "MediaFusion | Midnight RD", enabled = false))

    val groups = buildHomeRowGroups(rows, addons, streamDekRowsEnabled = true, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME)

    assertEquals(1, groups.size)
    assertEquals("MediaFusion | Midnight RD", groups.single().title)
  }

  @Test
  fun `a switched-off add-on is gated, and says why`() {
    val rows = listOf(savedAddonRow("uuid-a", "trending", 0))
    val addons = listOf(testAddon("uuid-a", "TvVoo", enabled = false))

    val group = buildHomeRowGroups(rows, addons, streamDekRowsEnabled = true, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME).single()

    assertNotNull(group.gatedNoteRes)
  }

  @Test
  fun `an add-on that is on is not gated`() {
    val rows = listOf(savedAddonRow("uuid-a", "trending", 0))
    val addons = listOf(testAddon("uuid-a", "TvVoo", enabled = true))

    assertNull(buildHomeRowGroups(rows, addons, streamDekRowsEnabled = true, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME).single().gatedNoteRes)
  }

  @Test
  fun `StreamDek rows are gated by the master switch and keep their stored flags`() {
    val rows = listOf(builtinRow("trending_movies"), builtinRow("new_movies", enabled = false))

    val off = buildHomeRowGroups(rows, emptyList(), streamDekRowsEnabled = false, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME).single()
    assertEquals(STREAMDEK_ROW_GROUP_KEY, off.key)
    assertNotNull(off.gatedNoteRes)
    // The gate must not rewrite what it gates.
    assertEquals(listOf(true, false), off.rows.map { it.enabled })

    val on = buildHomeRowGroups(rows, emptyList(), streamDekRowsEnabled = true, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME).single()
    assertNull(on.gatedNoteRes)
    assertEquals(listOf(true, false), on.rows.map { it.enabled })
  }

  @Test
  fun `the master switch does not gate add-on groups`() {
    val rows = listOf(builtinRow("trending_movies"), savedAddonRow("uuid-a", "trending", 0))
    val addons = listOf(testAddon("uuid-a", "Ultra MAX"))

    val groups = buildHomeRowGroups(rows, addons, streamDekRowsEnabled = false, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME)

    assertNotNull(groups.first { it.key == STREAMDEK_ROW_GROUP_KEY }.gatedNoteRes)
    assertNull(groups.first { it.key == "uuid-a" }.gatedNoteRes)
  }

  @Test
  fun `one group per add-on, in the order the sources first appear`() {
    val rows = listOf(
      savedAddonRow("uuid-b", "one", 0),
      savedAddonRow("uuid-a", "one", 0),
      savedAddonRow("uuid-b", "two", 1),
      builtinRow("trending_movies"),
    )
    val addons = listOf(testAddon("uuid-a", "Alpha"), testAddon("uuid-b", "Beta"))

    val groups = buildHomeRowGroups(rows, addons, streamDekRowsEnabled = true, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME)

    assertEquals(listOf("uuid-b", "uuid-a", STREAMDEK_ROW_GROUP_KEY), groups.map { it.key })
    assertEquals(2, groups.first { it.key == "uuid-b" }.rows.size)
  }

  @Test
  fun `rows from an uninstalled add-on collapse into one group rather than several nameless ones`() {
    val rows = listOf(
      savedAddonRow("gone-1", "one", 0),
      savedAddonRow("gone-2", "one", 0),
      savedAddonRow("uuid-a", "one", 0),
    )
    val addons = listOf(testAddon("uuid-a", "Alpha"))

    val groups = buildHomeRowGroups(rows, addons, streamDekRowsEnabled = true, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME)

    assertEquals(2, groups.size)
    val orphans = groups.first { it.key == ORPHAN_ROW_GROUP_KEY }
    assertEquals("No longer installed", orphans.title)
    assertEquals(2, orphans.rows.size)
    assertNotNull(orphans.gatedNoteRes)
  }

  @Test
  fun `before the add-on list arrives nothing is called uninstalled`() {
    // The home load runs before the add-on list does. Routing every add-on row into "no longer
    // installed" for that second would be both alarming and wrong.
    val rows = listOf(savedAddonRow("uuid-a", "one", 0), savedAddonRow("uuid-b", "one", 0))

    val groups = buildHomeRowGroups(rows, emptyList(), streamDekRowsEnabled = true, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME)

    assertEquals(listOf("uuid-a", "uuid-b"), groups.map { it.key })
    assertEquals(emptyList<Int?>(), groups.mapNotNull { it.gatedNoteRes })
  }

  @Test
  fun `a row still carrying its subtitle names the group when the add-on is unknown but the list is empty`() {
    val rows = listOf(
      HomeCatalogRow(id = addonRowId("uuid-a", "one", 0), title = "Live TV", subtitleRes = 0, subtitleArg = "Xperience", builtin = false),
    )

    assertEquals("Xperience", buildHomeRowGroups(rows, emptyList(), streamDekRowsEnabled = true, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME).single().title)
  }

  @Test
  fun `StreamDek's own assembled rows sit in the StreamDek group and follow the master switch`() {
    // Recommended, Trending On Trakt and Watchlist are built by Home rather than fetched from the
    // catalog registry, but they are StreamDek's rows and belong under the same switch.
    val groups = buildHomeRowGroups(streamDekFeatureRows, emptyList(), streamDekRowsEnabled = false, orphanGroupTitle = ORPHAN_TITLE, fallbackAddonName = FALLBACK_ADDON_NAME)

    val group = groups.single()
    assertEquals(STREAMDEK_ROW_GROUP_KEY, group.key)
    assertEquals(listOf("recommended", "trending", "watchlist"), group.rows.map { it.id })
    assertNotNull(group.gatedNoteRes)
  }

  @Test
  fun `the assembled rows are never asked for as catalog ids`() {
    // They are not catalogs; sending them to /tmdb/home would ask the backend for rows it has
    // never heard of.
    assertEquals(setOf("recommended", "trending", "watchlist"), streamDekFeatureRowIds)
    assertEquals(emptyList<String>(), fallbackCatalogDefinitions.map { it.id }.filter { it in streamDekFeatureRowIds })
  }
}
