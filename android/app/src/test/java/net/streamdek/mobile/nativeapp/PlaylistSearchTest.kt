package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSearchTest {
  private fun channel(title: String, group: String? = null) = MediaItem(
    id = "id:$title",
    type = "tv",
    title = title,
    year = null,
    poster = null,
    backdrop = null,
    rating = null,
    description = "",
    sourceCatalogName = group,
  )

  @Test fun ranksTitleMatchesAheadOfCategoryMatches() {
    val items = listOf(
      channel("Film Four", group = "Movies"),
      channel("BBC One", group = "UK Entertainment"),
      channel("Sky Atlantic", group = "BBC Partner Channels"),
      channel("BBC Two", group = "UK Entertainment"),
    )

    val results = items.matchingPlaylistItems("bbc").map { it.title }

    // The two channels actually called BBC come before the one that only matches on its category.
    assertEquals(listOf("BBC One", "BBC Two", "Sky Atlantic"), results)
  }

  @Test fun prefersExactThenPrefixThenWordStart() {
    val items = listOf(
      channel("The One Show"),
      channel("One Piece"),
      channel("One"),
      channel("Anyone Home"),
    )

    val results = items.matchingPlaylistItems("one").map { it.title }

    // Exact, then starts-with (alphabetical between equals), then a word start inside the title,
    // and finally the substring buried inside "Anyone".
    assertEquals(listOf("One", "One Piece", "The One Show", "Anyone Home"), results)
  }

  @Test fun matchingIsCaseInsensitiveAndNeedsTwoCharacters() {
    val items = listOf(channel("Discovery Channel"))

    assertEquals(1, items.matchingPlaylistItems("DISCOVERY").size)
    assertEquals(1, items.matchingPlaylistItems("dIsCoVeRy").size)
    // A single character would match most of a provider list, so it is not treated as a query.
    assertTrue(items.matchingPlaylistItems("d").isEmpty())
  }

  @Test fun capsResultsAtTheRequestedLimit() {
    val items = (1..500).map { channel("Sport Channel $it") }

    assertEquals(PLAYLIST_SEARCH_LIMIT, items.matchingPlaylistItems("sport").size)
    assertEquals(5, items.matchingPlaylistItems("sport", limit = 5).size)
  }

  @Test fun nonMatchesScoreNull() {
    assertNull(channel("BBC One").playlistSearchRank("itv"))
    // A category-only match still scores, just last.
    assertEquals(4, channel("Sky Atlantic", group = "BBC Partner").playlistSearchRank("bbc"))
  }

  @Test fun wordStartIgnoresMatchesInsideAWord() {
    // "one" starts a word in "BBC One" but not in "Anyone", which is what separates rank 2 from 3.
    assertTrue("BBC One".containsWordStart("one"))
    assertTrue(!"Anyone Home".containsWordStart("one"))
    // Punctuation and digits either side of a word still count as a boundary.
    assertTrue("Sports-One HD".containsWordStart("one"))
  }

  @Test fun emptyPlaylistAnswersEmpty() {
    assertTrue(emptyList<MediaItem>().matchingPlaylistItems("anything").isEmpty())
  }
}
