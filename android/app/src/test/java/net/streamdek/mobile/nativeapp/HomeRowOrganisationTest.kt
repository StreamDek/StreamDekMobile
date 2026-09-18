package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a saved Home layout is read in each row mode.
 *
 * The layout is one flat list of rows plus a list of source keys, and the mode decides which of the
 * two the screen is drawn from. The cases that matter are the ones where a source is *absent* -
 * switched off, not installed on this device, failing to load - because that is where an ordering
 * scheme built on array positions quietly loses somebody's arrangement.
 */
class HomeRowOrganisationTest {

  private fun row(id: String, enabled: Boolean = true) =
    HomeCatalogRow(id = id, title = id, subtitleRes = null, builtin = !id.startsWith("addon:"), enabled = enabled)

  private fun addonRow(addonId: String, catalogId: String, index: Int) =
    row("addon:$addonId:movie:$catalogId:$index")

  private fun ids(rows: List<HomeCatalogRow>) = rows.map { it.id }

  @Test
  fun `mixed mode is the saved list exactly as it stands`() {
    val rows = listOf(row("trending"), addonRow("aio", "new", 0), row("popular"), addonRow("aio", "recent", 1))
    assertEquals(ids(rows), ids(orderedHomeCatalogRows(rows, HomeRowMode.Mixed, listOf("aio", STREAMDEK_ROW_GROUP_KEY))))
  }

  @Test
  fun `by source gathers each source together without reordering inside it`() {
    val rows = listOf(row("trending"), addonRow("aio", "new", 0), row("popular"), addonRow("aio", "recent", 1))
    val ordered = orderedHomeCatalogRows(rows, HomeRowMode.BySource, savedSourceOrder = emptyList())
    // StreamDek appears first in the saved list, so it leads; within each source the saved order holds.
    assertEquals(
      listOf("trending", "popular", "addon:aio:movie:new:0", "addon:aio:movie:recent:1"),
      ids(ordered),
    )
  }

  @Test
  fun `a source dragged to the top takes all of its rows with it`() {
    val rows = listOf(row("trending"), row("popular"), addonRow("aio", "new", 0), addonRow("aio", "recent", 1))
    val ordered = orderedHomeCatalogRows(rows, HomeRowMode.BySource, savedSourceOrder = listOf("aio", STREAMDEK_ROW_GROUP_KEY))
    assertEquals(
      listOf("addon:aio:movie:new:0", "addon:aio:movie:recent:1", "trending", "popular"),
      ids(ordered),
    )
  }

  @Test
  fun `a source the saved order has never seen goes underneath it`() {
    val rows = listOf(row("trending"), addonRow("newaddon", "cat", 0), addonRow("aio", "new", 0))
    val order = homeRowSourceOrder(rows, savedOrder = listOf("aio", STREAMDEK_ROW_GROUP_KEY))
    assertEquals(listOf("aio", STREAMDEK_ROW_GROUP_KEY, "newaddon"), order)
    assertEquals(
      listOf("addon:aio:movie:new:0", "trending", "addon:newaddon:movie:cat:0"),
      ids(orderedHomeCatalogRows(rows, HomeRowMode.BySource, order)),
    )
  }

  /**
   * The failure this is really about: an add-on that fails to load for one launch must not lose its
   * place. Its key is absent from the rows, and keeping it in the order is what puts the add-on
   * back where it was rather than at the bottom when it comes back.
   */
  @Test
  fun `a source that is not offered right now keeps its place for when it returns`() {
    val saved = listOf("aio", STREAMDEK_ROW_GROUP_KEY, "plugin-b")
    val whileMissing = listOf(row("trending"), addonRow("plugin-b", "films", 0))
    assertEquals(saved, homeRowSourceOrder(whileMissing, saved))

    val whenItReturns = listOf(row("trending"), addonRow("plugin-b", "films", 0), addonRow("aio", "new", 0))
    assertEquals(
      listOf("addon:aio:movie:new:0", "trending", "addon:plugin-b:movie:films:0"),
      ids(orderedHomeCatalogRows(whenItReturns, HomeRowMode.BySource, homeRowSourceOrder(whenItReturns, saved))),
    )
  }

  @Test
  fun `a row id keeps naming its source however the manifest reorders`() {
    // The trailing index is the catalogue's position in the add-on's manifest and moves about; the
    // source segment does not, which is why ordering keys on the id rather than on a position.
    assertEquals("aio", homeRowSourceKey(addonRow("aio", "new", 7)))
    assertEquals(STREAMDEK_ROW_GROUP_KEY, homeRowSourceKey(row("trending")))
  }

  @Test
  fun `a CloudStream row groups under the plugin that registered its provider`() {
    val cloudRow = row("addon:cloudstream.netflix:series:latest:0")
    val groups = mapOf("cloudstream.netflix" to ("cloudstream-plugin:/data/cnc.cs3" to "CNC Verse"))
    assertEquals("cloudstream-plugin:/data/cnc.cs3", homeRowSourceKey(cloudRow, groups))
    // With the plugin not loaded the provider still answers for itself, so the rows stay together.
    assertEquals("cloudstream.netflix", homeRowSourceKey(cloudRow))
  }

  @Test
  fun `moving a source steps past keys the screen is not showing`() {
    val order = listOf(STREAMDEK_ROW_GROUP_KEY, "offline-addon", "aio")
    assertEquals(listOf("aio", STREAMDEK_ROW_GROUP_KEY, "offline-addon"), moveHomeRowSource(order, "aio", -2))
    // Past either end is a no-op rather than a wrap-around.
    assertEquals(order, moveHomeRowSource(order, STREAMDEK_ROW_GROUP_KEY, -1))
    assertEquals(order, moveHomeRowSource(order, "aio", 5).let { it })
  }

  /**
   * What the flat list prints for a row whose source is switched off, and so has nothing left to
   * name it. The grouped view never had to answer this: a switched-off source will not open.
   */
  @Test
  fun `a row with no name left to rebuild from is still readable`() {
    // As it comes back off disk: the id stands in for the title.
    val saved = HomeCatalogRow(
      id = "addon:389b7f7d-fa32-4552-a030-13802cb4dd2f:series:search.series:24",
      title = "addon:389b7f7d-fa32-4552-a030-13802cb4dd2f:series:search.series:24",
      subtitleRes = null,
      builtin = false,
    )
    assertEquals("Search Series", homeRowDisplayTitle(saved))
    assertEquals(
      "Mal Airing",
      homeRowDisplayTitle(saved.copy(id = "addon:x:anime:mal.airing:17", title = "addon:x:anime:mal.airing:17")),
    )
    assertEquals(
      "Calendar Videos",
      homeRowDisplayTitle(saved.copy(id = "addon:x:series:calendar-videos:29", title = "addon:x:series:calendar-videos:29")),
    )
    // A row its source did name keeps that name untouched.
    assertEquals("Trending Movies", homeRowDisplayTitle(saved.copy(title = "Trending Movies")))
  }

  @Test
  fun `the stored form survives a round trip, and rubbish parses as no order at all`() {
    val order = listOf("aio", STREAMDEK_ROW_GROUP_KEY, "cloudstream-plugin:/data/cnc.cs3")
    assertEquals(order, parseHomeRowSourceOrder(serializeHomeRowSourceOrder(order)))
    assertEquals(emptyList<String>(), parseHomeRowSourceOrder("not json"))
    assertEquals(emptyList<String>(), parseHomeRowSourceOrder(null))
  }

  @Test
  fun `the mode key is the storage contract, and an unknown one falls back to today's behaviour`() {
    assertEquals(HomeRowMode.BySource, HomeRowMode.fromKey("by_source"))
    assertEquals(HomeRowMode.Mixed, HomeRowMode.fromKey("mixed"))
    assertEquals(HomeRowMode.Mixed, HomeRowMode.fromKey("Mixed"))
    assertEquals(HomeRowMode.Default, HomeRowMode.fromKey("something-else"))
    assertEquals(HomeRowMode.Default, HomeRowMode.fromKey(null))
  }

  /**
   * Switching to Mixed hands over the order the viewer was looking at, and switching back regroups
   * it to the same screen - which is what makes the two modes two views rather than two layouts.
   */
  @Test
  fun `flattening for mixed mode and regrouping afterwards are both lossless`() {
    val saved = listOf(row("trending"), addonRow("aio", "new", 0), row("popular"))
    val sourceOrder = listOf("aio", STREAMDEK_ROW_GROUP_KEY)
    val flattened = orderedHomeCatalogRows(saved, HomeRowMode.BySource, sourceOrder)
    assertEquals(ids(flattened), ids(orderedHomeCatalogRows(flattened, HomeRowMode.Mixed, sourceOrder)))
    assertEquals(ids(flattened), ids(orderedHomeCatalogRows(flattened, HomeRowMode.BySource, sourceOrder)))
  }

  @Test
  fun `ordering decides order and never visibility`() {
    val rows = listOf(row("trending", enabled = false), addonRow("aio", "new", 0))
    val ordered = orderedHomeCatalogRows(rows, HomeRowMode.BySource, listOf("aio", STREAMDEK_ROW_GROUP_KEY))
    assertEquals(rows.size, ordered.size)
    assertEquals(false, ordered.first { it.id == "trending" }.enabled)
  }
}
