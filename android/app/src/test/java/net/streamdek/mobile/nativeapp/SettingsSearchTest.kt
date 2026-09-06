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
  /**
   * The English headings, held here rather than in the app.
   *
   * The app draws each page's heading from `route.titleRes`, so the shipped wording is whichever
   * language the viewer chose and a unit test cannot resolve it without an Android context. What
   * this file needs is not the shipped wording but *a* set of titles to search against, and
   * keeping the English ones here also states plainly what each page is expected to be called.
   */
  private val titles = mapOf(
    SettingsRoute.Appearance to "Appearance and Language",
    SettingsRoute.HomeScreen to "Home Screen",
    SettingsRoute.HomeLayout to "Home Rows",
    SettingsRoute.TitlePages to "Title Pages",
    SettingsRoute.Ratings to "Ratings",
    SettingsRoute.LiveTv to "Live TV",
    SettingsRoute.Player to "Player",
    SettingsRoute.SkipAndAutoplay to "Skip and Autoplay",
    SettingsRoute.Subtitles to "Subtitles",
    SettingsRoute.Streams to "Streams and Quality",
    SettingsRoute.Downloads to "Downloads",
    SettingsRoute.Addons to "Add-ons",
    SettingsRoute.Plugins to "Plugins",
    SettingsRoute.M3uPlaylists to "Playlists",
    SettingsRoute.Debrid to "Premium Services",
    SettingsRoute.PeerToPeer to "Peer-to-Peer",
    SettingsRoute.ContentServices to "Content Services",
    SettingsRoute.SyncServices to "Sync Services",
    SettingsRoute.Trakt to "Trakt",
    SettingsRoute.Simkl to "SIMKL",
    SettingsRoute.Mdblist to "MDBList",
    SettingsRoute.Punchplay to "PunchPlay",
    SettingsRoute.ConnectTv to "Connect to TV",
    SettingsRoute.Network to "Network",
    SettingsRoute.Account to "Account",
    SettingsRoute.Profiles to "Profiles",
    SettingsRoute.AppUpdates to "App Updates",
  )

  private fun titleOf(route: SettingsRoute) = titles.getValue(route)
  private fun bodyOf(route: SettingsRoute) = settingsRouteKeywords(route)
  private fun find(query: String) = searchSettingsRoutes(query, ::titleOf, ::bodyOf)

  @Test fun everyRouteHasATitleInThisTest() {
    val missing = SettingsRoute.values().filterNot { it in titles }
    assertTrue("add these to `titles`: $missing", missing.isEmpty())
  }

  @Test fun everyPageIsFindableByItsOwnTitle() {
    val unreachable = SettingsRoute.values().filterNot { route -> route in find(titleOf(route)) }
    assertTrue("not findable by title: ${unreachable.map(::titleOf)}", unreachable.isEmpty())
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

  /**
   * Settings that moved when the pages were reorganised. Searching is how people who knew the old
   * layout will find them again, so each lands on the page that now owns it.
   */
  @Test fun findsSettingsOnThePageThatNowOwnsThem() {
    assertTrue("live progress bar", SettingsRoute.LiveTv in find("live progress bar"))
    assertTrue("progress bar", SettingsRoute.LiveTv in find("progress bar"))
    assertTrue("seek", SettingsRoute.Player in find("seek"))
    assertTrue("hold to speed up", SettingsRoute.Player in find("hold to speed up"))
    assertTrue("torrent", SettingsRoute.PeerToPeer in find("torrent"))
    assertTrue("magnet", SettingsRoute.PeerToPeer in find("magnet"))
    assertTrue("mpv", SettingsRoute.Player in find("mpv"))
    assertTrue("blur", SettingsRoute.TitlePages in find("blur"))
    assertTrue("cellular", SettingsRoute.SyncServices in find("cellular"))
    assertTrue("dark mode", SettingsRoute.Appearance in find("dark mode"))
    assertTrue("max file size", SettingsRoute.Streams in find("file size"))
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
