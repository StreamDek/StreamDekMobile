package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings search index used to be a hand-written list that had drifted three pages behind the
 * route enum, so whole pages could not be reached by search at all. These check that the words a
 * user would plausibly type reach the page holding the setting.
 */
class SettingsSearchTest {
  private fun find(query: String) = searchSettingsRoutes(query)

  @Test fun everyPageIsFindableByItsOwnTitle() {
    val unreachable = SettingsRoute.values().filterNot { route ->
      route in searchSettingsRoutes(settingsRouteTitle(route))
    }
    assertTrue("not findable by title: ${unreachable.map(::settingsRouteTitle)}", unreachable.isEmpty())
  }

  @Test fun everyPageHasKeywords() {
    val bare = SettingsRoute.values().filter { settingsRouteKeywords(it).isBlank() }
    assertTrue("pages with no search keywords: $bare", bare.isEmpty())
  }

  @Test fun findsPagesThatWereMissingFromTheOldIndex() {
    assertTrue("iptv", SettingsRoute.M3uPlaylists in find("iptv"))
    assertTrue("m3u", SettingsRoute.M3uPlaylists in find("m3u"))
    assertTrue("playlist", SettingsRoute.M3uPlaylists in find("playlist"))
    assertTrue("offline", SettingsRoute.Downloads in find("offline"))
    assertTrue("downloads", SettingsRoute.Downloads in find("downloads"))
    assertTrue("reorder rows", SettingsRoute.HomeLayout in find("reorder rows"))
  }

  @Test fun findsTheLiveProgressBarSetting() {
    // Lives on the Streams page; before this it matched nothing at all.
    assertTrue(SettingsRoute.Streams in find("live progress bar"))
    assertTrue(SettingsRoute.Streams in find("progress bar"))
    assertTrue(SettingsRoute.Streams in find("seek"))
  }

  @Test fun blankQueryReturnsNothing() {
    assertEquals(emptyList<SettingsRoute>(), find(""))
    assertEquals(emptyList<SettingsRoute>(), find("   "))
  }

  @Test fun aPageNamedByTheQueryRanksFirst() {
    // "trakt" has a page of its own and must outrank the generic Sync Services page.
    assertEquals(SettingsRoute.Trakt, find("trakt").first())
    assertEquals(SettingsRoute.Mdblist, find("mdblist").first())
    assertEquals(SettingsRoute.Downloads, find("downloads").first())
  }

  @Test fun matchingIgnoresCase() {
    assertEquals(find("IPTV"), find("iptv"))
    assertEquals(find("Live Progress Bar"), find("live progress bar"))
  }
}
