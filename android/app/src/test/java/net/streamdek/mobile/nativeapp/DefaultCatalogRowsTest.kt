package net.streamdek.mobile.nativeapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The default catalog rows: how a saved layout survives the registry growing under it, and what
 * the app makes of the backend's catalog payloads.
 */
class DefaultCatalogRowsTest {

  private fun definition(id: String, title: String = id, mediaType: String = "movie") = CatalogDefinition(
    id = id,
    title = title,
    mediaType = mediaType,
    group = "default",
    previewLimit = 20,
    maxItems = null,
    paginated = mediaType != "network",
  )

  private fun savedRow(id: String, enabled: Boolean = true) =
    HomeCatalogRow(id = id, title = id, subtitleRes = null, builtin = false, enabled = enabled)

  /**
   * The catalog rows only.
   *
   * The saved layout also carries StreamDek's own assembled rows -- Recommended, Trending On Trakt
   * and Watchlist -- so that they can be switched on and off beside the catalogues. They are not
   * registry rows and say nothing about registry ordering, so the assertions below look past them.
   */
  private fun catalogIds(rows: List<HomeCatalogRow>) = rows.map { it.id }.filterNot { it in streamDekFeatureRowIds }

  @Test
  fun `fresh install takes the registry order`() {
    val rows = mergeHomeCatalogRows(emptyList(), emptyList(), fallbackCatalogDefinitions)

    assertEquals(fallbackCatalogDefinitions.map { it.id }, catalogIds(rows))
    assertTrue(rows.all { it.enabled && it.builtin })
    // The feature rows are part of the layout too, and default to on.
    assertTrue(streamDekFeatureRowIds.all { id -> rows.any { it.id == id && it.enabled } })
  }

  @Test
  fun `the shipped defaults stay within the thirty row budget and have unique ids`() {
    assertTrue(fallbackCatalogDefinitions.size <= 30)
    assertEquals(fallbackCatalogDefinitions.size, fallbackCatalogDefinitions.map { it.id }.toSet().size)
  }

  @Test
  fun `a layout saved before the registry adopts the new order but keeps rows switched off`() {
    val legacy = listOf(
      savedRow("trending_series"),
      savedRow("new_movies"),
      savedRow("streaming_networks", enabled = false),
      savedRow("new_series"),
      savedRow("trending_movies"),
    )

    val rows = mergeHomeCatalogRows(legacy, emptyList(), fallbackCatalogDefinitions)

    assertEquals(fallbackCatalogDefinitions.map { it.id }, catalogIds(rows))
    assertFalse(rows.first { it.id == "streaming_networks" }.enabled)
    assertTrue(rows.filterNot { it.id == "streaming_networks" }.all { it.enabled })
  }

  @Test
  fun `migrating a legacy layout keeps an add-on row the viewer pinned above the defaults`() {
    val addon = InstalledAddon(
      id = "cinemeta",
      enabled = true,
      position = 0,
      manifest = AddonManifest(
        id = "cinemeta",
        name = "Cinemeta",
        version = "1.0.0",
        description = null,
        logo = null,
        catalogs = listOf(AddonCatalog(type = "movie", id = "top", name = "Popular")),
      ),
    )
    val addonRowId = addonHomeCatalogCandidates(listOf(addon)).single().id
    val legacy = listOf(
      savedRow(addonRowId),
      savedRow("trending_movies"),
      savedRow("new_movies"),
      savedRow("new_series"),
      savedRow("streaming_networks"),
      savedRow("trending_series"),
    )

    val rows = mergeHomeCatalogRows(legacy, listOf(addon), fallbackCatalogDefinitions)

    assertEquals(addonRowId, rows.first().id)
    assertEquals(fallbackCatalogDefinitions.map { it.id }, catalogIds(rows.drop(1)))
  }

  @Test
  fun `an arrangement the viewer made is preserved and new rows land in registry order`() {
    // The viewer moved Hidden Gems to the top and switched Anime off; a later backend deploy adds
    // Timeless Classics. Their arrangement has to survive, and the new row belongs where the
    // registry puts it — after Hidden Gems — not at the bottom.
    val definitions = listOf(
      definition("trending_movies"),
      definition("anime_series", mediaType = "tv"),
      definition("hidden_gems_movies"),
      definition("classic_movies"),
    )
    val saved = listOf(
      savedRow("hidden_gems_movies"),
      savedRow("trending_movies"),
      savedRow("anime_series", enabled = false),
    )

    val rows = mergeHomeCatalogRows(saved, emptyList(), definitions)

    assertEquals(listOf("hidden_gems_movies", "classic_movies", "trending_movies", "anime_series"), catalogIds(rows))
    assertFalse(rows.first { it.id == "anime_series" }.enabled)
  }

  @Test
  fun `a row the registry has dropped disappears from the layout`() {
    val saved = listOf(savedRow("trending_movies"), savedRow("retired_catalog"))

    val rows = mergeHomeCatalogRows(saved, emptyList(), listOf(definition("trending_movies")))

    assertEquals(listOf("trending_movies"), catalogIds(rows))
  }

  @Test
  fun `titles follow the registry rather than whatever was saved`() {
    val saved = listOf(savedRow("trending_movies"))

    val rows = mergeHomeCatalogRows(saved, emptyList(), listOf(definition("trending_movies", title = "Hot Right Now")))

    assertEquals("Hot Right Now", rows.single { it.id !in streamDekFeatureRowIds }.title)
  }

  @Test
  fun `disabled rows and disabled defaults are left off the home screen`() {
    val sections = listOf(
      MediaSection("trending_movies", "Trending Movies", listOf(mediaItem("1"))),
      MediaSection("anime_series", "Anime", listOf(mediaItem("2"))),
    )
    val rows = listOf(
      HomeCatalogRow("trending_movies", "Trending Movies", subtitleRes = null, builtin = true),
      HomeCatalogRow("anime_series", "Anime", subtitleRes = null, builtin = true, enabled = false),
    )

    assertEquals(listOf("trending_movies"), applyHomeCatalogLayout(sections, rows, true).map { it.id })
    assertTrue(applyHomeCatalogLayout(sections, rows, false).isEmpty())
  }

  @Test
  fun `networks are the one default row that cannot be paged`() {
    val pageable = pageableCatalogRowIds(fallbackCatalogDefinitions)

    assertFalse("streaming_networks" in pageable)
    assertTrue("trending_movies" in pageable)
    assertEquals(fallbackCatalogDefinitions.size - 1, pageable.size)
  }

  @Test
  fun `top 100 rows declare their hundred item ceiling`() {
    val topHundred = fallbackCatalogDefinitions.filter { it.id.startsWith("top_100") }

    assertEquals(2, topHundred.size)
    assertTrue(topHundred.all { it.maxItems == 100 })
    assertTrue(fallbackCatalogDefinitions.none { !it.id.startsWith("top_100") && it.maxItems != null })
  }

  @Test
  fun `manifest payloads are read into definitions and junk entries are skipped`() {
    val json = JSONObject(
      """
      {
        "version": 2,
        "catalogs": [
          {"id":"trending_movies","title":"Trending Movies","media_type":"movie","group":"hot","preview_limit":20,"max_items":null,"paginated":true},
          {"id":"top_100_movies","title":"Top 100 Movies","media_type":"movie","group":"acclaimed","preview_limit":20,"max_items":100,"paginated":true},
          {"id":"streaming_networks","title":"Streaming Networks","media_type":"network","group":"streaming","preview_limit":20,"paginated":false},
          {"id":"","title":"Nameless"},
          {"title":"No id at all"}
        ]
      }
      """.trimIndent(),
    )

    val definitions = parseCatalogManifest(json)

    assertEquals(listOf("trending_movies", "top_100_movies", "streaming_networks"), definitions.map { it.id })
    assertEquals(100, definitions[1].maxItems)
    assertNull(definitions[0].maxItems)
    assertFalse(definitions[2].paginated)
  }

  @Test
  fun `home payloads keep their paging cursor and drop rows with nothing in them`() {
    val json = JSONObject(
      """
      {
        "version": 2,
        "sections": [
          {"id":"trending_movies","title":"Trending Movies","total_pages":500,"next_page":2,
           "results":[{"id":"603","tmdbId":603,"title":"The Matrix","type":"movie","poster":"https://image.tmdb.org/t/p/w500/x.jpg","year":"1999"}]},
          {"id":"empty_row","title":"Empty","total_pages":0,"next_page":null,"results":[]},
          {"id":"top_100_movies","title":"Top 100 Movies","total_pages":5,"next_page":2,
           "results":[{"id":"278","tmdbId":278,"title":"The Shawshank Redemption","type":"movie","poster":"https://image.tmdb.org/t/p/w500/y.jpg","year":"1994"}]}
        ]
      }
      """.trimIndent(),
    )

    val sections = parseCatalogHomeSections(json)

    assertEquals(listOf("trending_movies", "top_100_movies"), sections.map { it.id })
    assertEquals(2, sections.first().nextPage)
    assertEquals(500, sections.first().totalPages)
    assertEquals("The Matrix", sections.first().items.single().title)
    assertEquals(5, sections.last().totalPages)
  }

  @Test
  fun `a network row is recognised as a network rather than a movie`() {
    val json = JSONObject(
      """
      {"sections":[{"id":"streaming_networks","title":"Streaming Networks","total_pages":1,
       "results":[{"id":8,"name":"Netflix","browseId":8,"logo":"https://image.tmdb.org/t/p/w92/n.png","type":"network"}]}]}
      """.trimIndent(),
    )

    val section = parseCatalogHomeSections(json).single()

    assertEquals("network", section.items.single().type)
    assertNull(section.nextPage)
  }

  private fun mediaItem(id: String) = MediaItem(
    id = id,
    type = "movie",
    title = "Title $id",
    year = "2024",
    poster = null,
    backdrop = null,
    rating = null,
    description = "",
  )
}
