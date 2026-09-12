package net.streamdek.mobile.nativeapp

import org.junit.Assert.*
import org.junit.Test

class NetworkCatalogSearchTest {
  private fun item(id: String, type: String = "tv") =
    MediaItem(id, type, "Title $id", null, null, null, null, "")

  @Test fun searchPaginationDoesNotChangeRetainedBrowsePages() {
    val browse = NetworkCatalogPages().append(DiscoverPage(listOf(item("1")), 1, 8))
    val search = NetworkCatalogPages().append(DiscoverPage(listOf(item("99")), 1, 2))
      .append(DiscoverPage(emptyList(), 2, 2))
    assertEquals(listOf(item("1")), browse.items)
    assertEquals(1, browse.page)
    assertEquals(8, browse.totalPages)
    assertEquals(listOf(item("99")), search.items)
    assertFalse(search.hasMore)
  }

  @Test fun emptyFilteredPageDoesNotEndSearchBeforeLaterMatches() {
    val first = NetworkCatalogPages().append(DiscoverPage(emptyList(), 1, 25))
    assertTrue(first.hasMore)
    val later = first.append(DiscoverPage(listOf(item("501")), 21, 25))
    assertEquals(listOf(item("501")), later.items)
    assertTrue(later.hasMore)
  }

  @Test fun paginationDeduplicatesByBothIdAndMediaType() {
    val first = NetworkCatalogPages().append(DiscoverPage(listOf(item("1")), 1, 2))
    val second = first.append(DiscoverPage(listOf(item("1"), item("1", "movie")), 2, 2))
    assertEquals(2, second.items.size)
    assertFalse(second.hasMore)
  }

  @Test fun nonAdvancingPageCannotLoopForever() {
    val first = NetworkCatalogPages().append(DiscoverPage(emptyList(), 1, 20))
    assertFalse(first.append(DiscoverPage(emptyList(), 1, 20)).hasMore)
  }
}
