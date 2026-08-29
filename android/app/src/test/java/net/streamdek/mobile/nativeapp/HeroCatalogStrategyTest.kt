package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroCatalogStrategyTest {
  private fun catalog(id: String, title: String, type: String = "movie") = CatalogDefinition(
    id = id,
    title = title,
    mediaType = type,
    group = "discover",
    previewLimit = 20,
    maxItems = null,
    paginated = true,
  )

  @Test
  fun `provider categories are ranked independently of home row state`() {
    val ids = heroCatalogIds(
      listOf(
        catalog("documentaries", "Documentaries"),
        catalog("popular_tv", "Popular Series", "tv"),
        catalog("recommended", "Recommended For You"),
        catalog("top_rated", "Top Rated"),
        catalog("live_channels", "Live TV", "tv"),
      ),
    )

    assertEquals(listOf("recommended", "popular_tv", "top_rated", "documentaries"), ids)
  }

  @Test
  fun `non media catalogs are excluded from hero candidates`() {
    assertEquals(
      listOf("recent_movies"),
      heroCatalogIds(listOf(catalog("networks", "Networks", "network"), catalog("recent_movies", "Recently Added"))),
    )
  }

  @Test
  fun `streaming network and live tv catalogs never feed the hero`() {
    assertEquals(
      listOf("popular_movies"),
      heroCatalogIds(
        listOf(
          catalog("streaming_networks", "Streaming Networks", "network"),
          catalog("addon_live", "Live TV Channels", "tv"),
          catalog("popular_movies", "Popular Movies"),
        ),
      ),
    )
  }

  @Test
  fun `legacy provider still supplies hero fallback catalogs`() {
    assertEquals(
      listOf("new_movies", "new_series", "trending_movies", "trending_series"),
      heroCatalogIds(emptyList()),
    )
  }
}
