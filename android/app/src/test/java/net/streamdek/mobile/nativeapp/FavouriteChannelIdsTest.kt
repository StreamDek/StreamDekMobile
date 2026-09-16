package net.streamdek.mobile.nativeapp

import org.junit.Assert.*
import org.junit.Test

class FavouriteChannelIdsTest {
  private fun channel(id: String, url: String? = null) =
    MediaItem(id, "live", "Channel", null, null, null, null, "", directStreamUrl = url)

  private val fullId = "cs:" + "a".repeat(700)
  private val cutId = fullId.take(ACCOUNT_FAVOURITE_ID_LIMIT)

  @Test fun cutIdMatchesTheChannelItWasCutFrom() {
    assertTrue(favouriteChannelIdMatches(cutId, fullId))
    assertTrue(favouriteChannelIdMatches(fullId, fullId))
    assertFalse(favouriteChannelIdMatches(cutId, "cs:" + "b".repeat(700)))
    // A short id is only ever an exact match: it was never cut, so a prefix means another channel.
    assertFalse(favouriteChannelIdMatches("m3u:1", "m3u:12"))
  }

  @Test fun restoresFullIdsFromKnownChannelsAndCollapsesDuplicates() {
    val favourites = listOf(channel(cutId), channel(cutId), channel("m3u:1"))
    val restored = restoreTruncatedFavouriteIds(favourites, listOf(channel(fullId)))
    assertEquals(listOf(fullId, "m3u:1"), restored.map { it.id })
  }

  @Test fun returnsTheSameListWhenNothingNeedsRestoring() {
    val favourites = listOf(channel(cutId))
    assertSame(favourites, restoreTruncatedFavouriteIds(favourites, listOf(channel("cs:other"))))
    val short = listOf(channel("m3u:1"))
    assertSame(short, restoreTruncatedFavouriteIds(short, listOf(channel(fullId))))
  }

  @Test fun accountCopyKeepsThePhonesFullIdsAndStreamLinks() {
    val local = listOf(channel(fullId), channel("m3u:1", url = "https://stream"))
    val account = listOf(channel(cutId), channel("m3u:1"))
    val merged = mergeAccountFavourites(account, local)
    assertEquals(listOf(fullId, "m3u:1"), merged.map { it.id })
    assertEquals("https://stream", merged[1].directStreamUrl)
  }
}
