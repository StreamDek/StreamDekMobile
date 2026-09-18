package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What may fill a spotlight.
 *
 * Home's spotlight and the profile picker's are built from whatever the catalogues returned, and a
 * live TV add-on returns channels: a wall of station idents where a film's artwork should be. The
 * two screens used to decide this separately - one matched a few English words in a section title,
 * the other did not look at all - so a live add-on named "UK" or "Sports 24" walked into both.
 */
class HeroSourceTest {

  private fun section(id: String, title: String, items: List<MediaItem> = emptyList()) =
    MediaSection(id = id, title = title, items = items)

  private fun title(id: String, type: String = "movie") = MediaItem(
    id = id,
    type = type,
    title = "Title $id",
    year = "2026",
    poster = "https://example.test/$id.jpg",
    backdrop = "https://example.test/$id-wide.jpg",
    rating = null,
    description = "",
  )

  private fun channel(id: String, catalogType: String = "tv") = title(id, type = "tv").copy(
    sourceCatalogType = catalogType,
    sourceCatalogName = "UK Channels",
    sourceAddonName = "Live add-on",
  )

  @Test
  fun `a live add-on catalogue is not spotlight material, whatever it calls itself`() {
    // The type in the row id is what says so - the title says nothing useful here.
    assertTrue(isLiveHeroSection("addon:uuid:tv:uk:0", "UK"))
    assertTrue(isLiveHeroSection("addon:uuid:live:sports:1", "Sports 24"))
    assertTrue(isLiveHeroSection("addon:uuid:iptv:world:2", "World"))
    // Playlists and the viewer's starred channels are channels too.
    assertTrue(isLiveHeroSection("m3u_playlists_live", "Playlist"))
    assertTrue(isLiveHeroSection("favourites", "Live Favourites"))
    // Streaming Networks is a row of service tiles, which is not spotlight artwork either.
    assertTrue(isLiveHeroSection("streaming_networks", "Streaming Networks"))
    // A section with no type that names itself is still caught.
    assertTrue(isLiveHeroSection("some_id", "Live TV"))
  }

  @Test
  fun `an ordinary catalogue still is`() {
    assertFalse(isLiveHeroSection("new_movies", "New Movies"))
    assertFalse(isLiveHeroSection("addon:uuid:movie:trending:0", "Trending"))
    assertFalse(isLiveHeroSection("addon:uuid:series:recs:1", "For You"))
  }

  @Test
  fun `the profile picker is drawn from titles only`() {
    val sections = listOf(
      section("addon:uuid:tv:uk:0", "UK", listOf(channel("sky-1"), channel("sky-2"))),
      section("new_movies", "New Movies", listOf(title("27205"))),
    )
    assertEquals(listOf("27205"), profileSwitcherHeroItems(sections).map { it.id })
  }

  /**
   * The section passed the id test, and the channel inside it is typed like a series. Only asking
   * the item as well keeps it out.
   */
  @Test
  fun `a channel hiding in an untyped section is still kept out`() {
    val sections = listOf(section("addon:uuid:other:mixed:0", "Mixed", listOf(channel("bbc-one", catalogType = "live"), title("603"))))
    assertEquals(listOf("603"), profileSwitcherHeroItems(sections).map { it.id })
  }

  @Test
  fun `with nothing but channels the spotlight is simply empty`() {
    val sections = listOf(section("addon:uuid:tv:uk:0", "UK", listOf(channel("sky-1"))))
    assertTrue(profileSwitcherHeroItems(sections).isEmpty())
  }
}
