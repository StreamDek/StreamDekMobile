package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun channel(
  title: String,
  genres: List<String> = emptyList(),
  catalogName: String? = null,
  addonName: String? = null,
): MediaItem = MediaItem(
  id = title,
  type = "tv",
  title = title,
  year = null,
  poster = null,
  backdrop = null,
  rating = null,
  description = "",
  genres = genres,
  sourceAddonName = addonName,
  sourceCatalogName = catalogName,
)

class BrowseCategoryTest {
  @Test
  fun tidiesShoutyGroupTitlesButKeepsInitialisms() {
    assertEquals("US • Entertainment", tidyCategoryLabel("US| ENTERTAINMENT"))
    assertEquals("UK Sports", tidyCategoryLabel("  UK SPORTS  "))
    assertEquals("Movies • Action", tidyCategoryLabel("|MOVIES|ACTION|"))
    assertEquals("Kids", tidyCategoryLabel("Kids"))
    assertEquals("", tidyCategoryLabel("  |  "))
  }

  @Test
  fun usesPublishedGroupTitleAsCategory() {
    assertEquals("Sports", channel("BT Sport 1", genres = listOf("SPORTS")).declaredCategory())
  }

  @Test
  fun ignoresCatalogNameThatOnlyRepeatsTheSourceName() {
    assertNull(channel("Some Channel", catalogName = "My Playlist", addonName = "My Playlist").declaredCategory())
    assertEquals("News", channel("Some Channel", catalogName = "News", addonName = "My Playlist").declaredCategory())
  }

  @Test
  fun infersCategoryFromChannelName() {
    assertEquals("Sports", channel("ESPN 2 HD").inferredCategory())
    assertEquals("News", channel("Al Jazeera English").inferredCategory())
    assertEquals("Kids", channel("Cartoon Network").inferredCategory())
    assertEquals("Documentary", channel("Discovery Science").inferredCategory())
    assertEquals("Adult", channel("Playboy TV Movies").inferredCategory())
  }

  @Test
  fun fallsBackToCountryPrefixThenGeneral() {
    assertEquals("United Kingdom", channel("UK | BBC One HD").inferredCategory())
    assertEquals("India", channel("[IN] Star Plus").inferredCategory())
    assertEquals("General", channel("Channel 5").inferredCategory())
  }

  @Test
  fun prefersPublishedCategoriesWhenTheyCoverTheList() {
    // Ordered by name, not by size: News comes first despite Sports holding three of the four.
    val items = listOf(
      channel("One", genres = listOf("SPORTS")),
      channel("Two", genres = listOf("SPORTS")),
      channel("Three", genres = listOf("SPORTS")),
      channel("Four", genres = listOf("NEWS")),
    )
    assertEquals(listOf("News", "Sports"), buildBrowseCategories(items).map { it.name })
  }

  @Test
  fun infersThroughoutWhenPublishedCategoriesBarelyCoverTheList() {
    // Two tagged entries against eight untagged ones: grouping on the tags would leave almost
    // everything in one anonymous pile, so every entry is classified instead.
    val items = listOf(
      channel("One", genres = listOf("SPORTS")),
      channel("Two", genres = listOf("NEWS")),
    ) + List(8) { channel("Sky Cinema $it") }
    val categories = buildBrowseCategories(items)
    assertEquals(listOf("Movies", "News", "Sports"), categories.map { it.name })
    assertEquals(8, categories.first().items.size)
  }

  @Test
  fun sortsCatchAllCategoryLast() {
    val items = List(6) { channel("Channel $it") } + listOf(channel("Sky Sports Main Event"))
    val categories = buildBrowseCategories(items)
    assertEquals("General", categories.last().name)
    assertTrue(categories.first().items.size < categories.last().items.size)
  }

  @Test
  fun categoriesPartitionEveryItem() {
    val items = listOf(
      channel("ESPN"),
      channel("CNN"),
      channel("UK | Dave"),
      channel("Channel 5"),
    )
    assertEquals(items.size, buildBrowseCategories(items).sumOf { it.items.size })
  }
}
