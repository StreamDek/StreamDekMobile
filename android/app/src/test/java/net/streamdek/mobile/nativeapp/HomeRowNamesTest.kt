package net.streamdek.mobile.nativeapp

import net.streamdek.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Why plugin rows on the Home rows page turned into "Row" a few seconds after every refresh.
 *
 * The account's copy of the layout is ids and switches. Reading it replaced the rows on screen with
 * rows named after their own ids, and the settings watcher reads it every time its stamp moves -
 * including after this phone's own writes. A CloudStream row id carries only a slug of the page
 * name, which is "row" when the page has no Latin name, so that is what the page printed.
 */
class HomeRowNamesTest {

  private val pluginRowId = "addon:cloudstream.cnc-verse:series:row:0"
  private val otherPluginRowId = "addon:cloudstream.other:series:trending:0"

  private fun named(id: String, title: String, source: String) =
    HomeCatalogRow(id = id, title = title, subtitleRes = R.string.home_row_from_addon, subtitleArg = source, builtin = false)

  private fun bare(id: String, enabled: Boolean = true) =
    HomeCatalogRow(id = id, title = id, subtitleRes = null, builtin = false, enabled = enabled)

  @Test
  fun `a layout read from the account keeps the names already on screen`() {
    val known = listOf(named(pluginRowId, "Netflix Originals", "CNC Verse"))
    val fromAccount = listOf(bare(pluginRowId, enabled = false))

    val result = withKnownHomeRowNames(fromAccount, known).single()

    assertEquals("Netflix Originals", result.title)
    assertEquals("CNC Verse", result.subtitleArg)
    assertEquals(R.string.home_row_from_addon, result.subtitleRes)
    // The switch is the account's, not the remembered row's.
    assertFalse(result.enabled)
  }

  @Test
  fun `rows with the same page name under different plugins keep their own names`() {
    val known = listOf(
      named("addon:cloudstream.a:series:trending:0", "Trending", "Plugin A"),
      named("addon:cloudstream.b:series:trending:0", "Trending", "Plugin B"),
    )
    val result = withKnownHomeRowNames(known.map { bare(it.id) }, known)
    assertEquals(listOf("Plugin A", "Plugin B"), result.map { it.subtitleArg })
  }

  @Test
  fun `an add-on that moved its catalogue still finds the name`() {
    val known = listOf(named("addon:aio:movie:popular:3", "Popular", "AIOStreams"))
    val result = withKnownHomeRowNames(listOf(bare("addon:aio:movie:popular:1")), known).single()
    assertEquals("addon:aio:movie:popular:1", result.id)
    assertEquals("Popular", result.title)
  }

  @Test
  fun `a row with no known name is left as it is`() {
    val row = bare(otherPluginRowId)
    assertEquals(row, withKnownHomeRowNames(listOf(row), listOf(named(pluginRowId, "X", "Y"))).single())
  }

  @Test
  fun `this device's copy of the layout carries names the account copy does not`() {
    val rows = listOf(
      named(pluginRowId, "Netflix Originals", "CNC Verse"),
      HomeCatalogRow("trending_movies", "Trending Movies", subtitleRes = R.string.home_row_provided_by_streamdek, builtin = true),
    )

    val account = serializeHomeCatalogRows(rows)
    assertFalse(account.contains("Netflix Originals"))

    val restored = parseHomeCatalogRows(serializeHomeCatalogRowsForDevice(rows))
    assertEquals("Netflix Originals", restored[0].title)
    assertEquals("CNC Verse", restored[0].subtitleArg)
    assertTrue(restored[1].builtin)
    assertNull(restored[1].subtitleArg)
  }
}
