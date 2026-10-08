package net.streamdek.mobile.nativeapp

import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSectionsTest {
  private fun item(id: String, sourceId: String? = null, sourceName: String? = null, subtitle: String? = null) =
    MediaItem(id = id, type = "movie", title = id, year = null, poster = null, backdrop = null, rating = null, description = "",
      cardSubtitle = subtitle, sourceAddonId = sourceId, sourceAddonName = sourceName)

  private fun server(provider: String, server: String, key: String, name: String) =
    item(MediaServerReference(provider, server, key).encode(), MediaServerReference.sourceIdOf(provider, server), name)

  @Test
  fun `each source gets its own section in a fixed order`() {
    val sections = searchResultSections(
      library = listOf(server("jellyfin", "j1", "a", "Jellyfin"), server("plex", "p1", "b", "Plex")),
      catalogue = listOf(item("tmdb:1")),
      addons = listOf(item("tt1", "addon.one", "Cinemeta"), item("tt2", "addon.two", "Anime"), item("tt3", "addon.one", "Cinemeta")),
      plugins = listOf(item("p:1", subtitle = "Provider A")),
    )
    assertEquals(
      listOf(SearchSourceKind.Plex, SearchSourceKind.Jellyfin, SearchSourceKind.Catalogue, SearchSourceKind.Addon, SearchSourceKind.Addon, SearchSourceKind.Plugin),
      sections.map { it.kind },
    )
    assertEquals(listOf("Plex", "Jellyfin", null, "Cinemeta", "Anime", "Provider A"), sections.map { it.name })
    // An add-on's results stay together whatever order they arrived in.
    assertEquals(2, sections[3].items.size)
    assertEquals(sections.size, sections.map { it.key }.toSet().size)
  }

  @Test
  fun `two servers of one kind are two sections`() {
    val sections = searchResultSections(
      library = listOf(server("plex", "home", "a", "Plex · Home"), server("plex", "cabin", "b", "Plex · Cabin"), server("plex", "home", "c", "Plex · Home")),
      catalogue = emptyList(), addons = emptyList(), plugins = emptyList(),
    )
    assertEquals(listOf("Plex · Home", "Plex · Cabin"), sections.map { it.name })
    assertEquals(listOf(2, 1), sections.map { it.items.size })
  }

  @Test
  fun `empty sources leave no section and a long one is capped`() {
    assertTrue(searchResultSections(emptyList(), emptyList(), emptyList(), emptyList()).isEmpty())
    val many = (1..90).map { item("tmdb:$it") }
    val only = searchResultSections(emptyList(), many, emptyList(), emptyList()).single()
    assertEquals(SEARCH_SECTION_LIMIT, only.items.size)
    assertNull(only.name)
    // A plugin card with no provider name is still listed, under the generic heading.
    assertNull(searchResultSections(emptyList(), emptyList(), emptyList(), listOf(item("p", subtitle = null))).single().name)
  }

  @Test
  fun `sections fold to two rows unless they are alone or opened`() {
    assertEquals(6, searchSectionVisibleCount(size = 20, columns = 3, expanded = false, onlySection = false))
    assertEquals(20, searchSectionVisibleCount(size = 20, columns = 3, expanded = true, onlySection = false))
    assertEquals(20, searchSectionVisibleCount(size = 20, columns = 3, expanded = false, onlySection = true))
    assertEquals(4, searchSectionVisibleCount(size = 4, columns = 3, expanded = false, onlySection = false))
    assertTrue(searchSectionFolds(size = 7, columns = 3, onlySection = false))
    assertFalse(searchSectionFolds(size = 6, columns = 3, onlySection = false))
    assertFalse(searchSectionFolds(size = 30, columns = 3, onlySection = true))
  }
}
