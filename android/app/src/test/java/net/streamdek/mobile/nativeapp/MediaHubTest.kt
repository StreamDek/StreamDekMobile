package net.streamdek.mobile.nativeapp

import org.junit.Assert.*
import org.junit.Test

class MediaHubTest {
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

  @Test fun metadataAndSubtitleCataloguesAreNotHiddenAsVod() {
    assertTrue(isMediaHubCatalogType("movie"))
    assertTrue(isMediaHubCatalogType("LIVE"))
    assertFalse(isMediaHubCatalogType("subtitle"))
    assertFalse(isMediaHubCatalogType("network"))
  }
}
