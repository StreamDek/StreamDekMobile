package net.streamdek.mobile.nativeapp

import org.junit.Assert.*
import org.junit.Test

class MediaHubTest {
  private val catalogs = (1..9).map { MediaHubCatalog("$it", "$it", "Source $it", "Titles", false) }

  @Test fun firstVisitDiscoversEverySourceWithoutLoadMore() {
    assertEquals(catalogs, mediaHubPendingCatalogs(catalogs, false, false, false) { null })
  }

  @Test fun returningToAllReusesLoadedPagesAndLoadsOnlyMissingSources() {
    val pages = catalogs.take(6).associate { it.key to MediaHubPage(nextOffset = 40) }
    assertEquals(catalogs.drop(6), mediaHubPendingCatalogs(catalogs, false, false, false) { pages[it.key] })
    assertTrue(mediaHubPendingCatalogs(catalogs, false, false, false) { MediaHubPage(nextOffset = 40) }.isEmpty())
  }

  @Test fun deeperPagingIsBoundedAndFailuresRequireRetry() {
    assertEquals(4, mediaHubPendingCatalogs(catalogs, true, false, false) { MediaHubPage(nextOffset = 40) }.size)
    assertTrue(mediaHubPendingCatalogs(catalogs, true, false, false) { MediaHubPage(failed = true) }.isEmpty())
    assertEquals(4, mediaHubPendingCatalogs(catalogs, true, true, false) { MediaHubPage(failed = true) }.size)
    assertTrue(mediaHubPendingCatalogs(catalogs, true, true, false) { MediaHubPage(end = true) }.isEmpty())
  }

  @Test fun homeRowsSwitchedOffAreLeftOutOfTheHub() {
    val switches = mediaHubRowSwitches(listOf(
      HomeCatalogRow("addon:iptv:tv:sports:3", "Sports", subtitleRes = null, builtin = false, enabled = false),
      HomeCatalogRow("addon:iptv:movie:films:4", "Films", subtitleRes = null, builtin = false, enabled = true),
      HomeCatalogRow("addon:cloudstream.livxow:live:events:0", "Events", subtitleRes = null, builtin = false, enabled = true),
    ))
    // Matched without the manifest index, so a reordered add-on keeps its switch.
    assertFalse(isMediaHubRowSwitchedOn(mediaHubAddonRowId("iptv", " TV ", "sports"), switches, offWhenUnlisted = false))
    assertTrue(isMediaHubRowSwitchedOn(mediaHubAddonRowId("iptv", "movie", "films"), switches, offWhenUnlisted = false))
    assertTrue(isMediaHubRowSwitchedOn("addon:cloudstream.livxow:live:events:7", switches, offWhenUnlisted = true))
  }

  @Test fun unlistedCatalogsTakeTheirHomeRowsDefault() {
    assertTrue(isMediaHubRowSwitchedOn(mediaHubAddonRowId("new", "tv", "channels"), emptyMap(), offWhenUnlisted = false))
    assertFalse(isMediaHubRowSwitchedOn("addon:cloudstream.other:live:row:0", emptyMap(), offWhenUnlisted = true))
  }

  @Test fun disabledPreservesEveryRowAndItsOrder() {
    val rows = listOf("continue", "addon:live", "new-episodes", "streaming_networks", "vod")
    assertEquals(rows, mediaHubHomeOrder(rows, setOf("addon:live", "vod"), false))
  }

  @Test fun hubReplacesSourcesBetweenNewEpisodesAndNetworks() {
    assertEquals(listOf("continue", "new-episodes", MEDIA_HUB_ROW_ID, "streaming_networks", "trending"),
      mediaHubHomeOrder(listOf("continue", "new-episodes", "live", "streaming_networks", "vod", "trending"), setOf("live", "vod"), true))
  }

  @Test fun newMoviesAndNewSeriesStayAboveTheHub() {
    assertEquals(listOf("continue", "new_movies", "new_series", MEDIA_HUB_ROW_ID, "streaming_networks", "trending"),
      mediaHubHomeOrder(listOf("continue", "new_movies", "new_series", "live", "streaming_networks", "trending"), setOf("live"), true))
    // Placed further down by the viewer (Mixed), New Movies does not pull the hub down with it.
    assertEquals(listOf("continue", MEDIA_HUB_ROW_ID, "trending", "new_movies"),
      mediaHubHomeOrder(listOf("continue", "trending", "new_movies"), emptySet(), true))
  }

  @Test fun emptyLibraryStillOffersHubAndKeepsNetworksBelowIt() {
    assertEquals(listOf(MEDIA_HUB_ROW_ID, "streaming_networks"), mediaHubHomeOrder(listOf("streaming_networks"), emptySet(), true))
    assertEquals(listOf(MEDIA_HUB_ROW_ID), mediaHubHomeOrder(emptyList(), emptySet(), true))
  }

  @Test fun noNewEpisodesKeepsContinueWatchingBeforeHub() {
    assertEquals(listOf("continue", MEDIA_HUB_ROW_ID, "streaming_networks"), mediaHubHomeOrder(listOf("continue", "live", "streaming_networks"), setOf("live"), true))
  }

  @Test fun duplicateIdsFromDifferentProvidersKeepBothPlaybackChoices() {
    val item = MediaItem("one", "movie", "Title", null, null, null, null, "", sourceAddonId = "first")
    assertNotEquals(mediaHubItemKey(item), mediaHubItemKey(item.copy(sourceAddonId = "second")))
    assertEquals(mediaHubItemKey(item), mediaHubItemKey(item.copy(sourceCatalogId = "another-collection")))
    assertNotEquals(mediaHubItemKey(item), mediaHubItemKey(item.copy(type = "series")))
  }

  /**
   * What Fuse is for. It gathers the viewer's *channel* sources; a discovery catalogue is a row of
   * titles like StreamDek's own and belongs on Home, which is where it now stays.
   */
  @Test fun onlyChannelCataloguesBelongInFuse() {
    // Channels, whatever a provider calls them.
    assertTrue(isMediaHubLiveCatalogType("LIVE"))
    assertTrue(isMediaHubLiveCatalogType("tv"))
    assertTrue(isMediaHubLiveCatalogType(" iptv "))
    assertTrue(isMediaHubLiveCatalogType("sports"))
    // Catalogues of titles, and everything that is not a catalogue at all.
    assertFalse(isMediaHubLiveCatalogType("movie"))
    assertFalse(isMediaHubLiveCatalogType("series"))
    assertFalse(isMediaHubLiveCatalogType("anime"))
    assertFalse(isMediaHubLiveCatalogType("subtitle"))
    assertFalse(isMediaHubLiveCatalogType("network"))
  }
}
