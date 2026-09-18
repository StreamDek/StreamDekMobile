package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What StreamDek looks like before anybody changes anything.
 *
 * These are product decisions, not incidental values: a fresh install and a newly created profile
 * are the first impression the app gets to make, and each of them was chosen deliberately. Pinning
 * them here means a default cannot drift back by way of a refactor without someone saying so.
 *
 * Only the defaults that live in code are here. The ones that live as a fallback argument to a
 * preference read - the Streaming Networks card style, the New Episodes row - are covered by the
 * device checks instead, since asserting them would mean standing up Android's preference storage
 * to read a constant back.
 */
class DefaultSettingsTest {

  @Test
  fun `appearance and language`() {
    // Artwork sets the mood; the theme stays out of its way.
    assertEquals(AppThemePreset.Monochrome, AppThemePreset.Default)
    // Everything StreamDek can draw, rather than a guess from the battery state - see the enum.
    assertEquals(VisualEffectsMode.Full, VisualEffectsMode.Default)
    assertEquals(AnimationSpeed.Standard, AnimationSpeed.Default)
    // The device's own language, not English.
    assertEquals(AppLanguage.SystemSelection, AppLanguage.DefaultSelection)
  }

  @Test
  fun `the navigation is expanded, and headers make room while scrolling`() {
    assertEquals(
      NavigationBehaviour.ExpandedScrollAwareHeaders,
      NavigationBehaviour.from(collapsible = false, collapseOnScroll = false, expandedHeadersScrollAware = true),
    )
  }

  @Test
  fun `the home screen`() {
    assertEquals(HomeDensity.Relaxed, HomeDensity.Default)
    assertEquals(HomeCardTextMode.ShowFull, HomeCardTextMode.Default)
    // Rows stay under the source that offers them until the viewer says otherwise.
    assertEquals(HomeRowMode.BySource, HomeRowMode.Default)
  }

  @Test
  fun `artwork behind a page is felt, not guessed at`() {
    // Half strength reads as a slightly lighter background rather than as artwork being there.
    assertEquals(80, DEFAULT_AMBIENT_TINT_PERCENT)
  }

  @Test
  fun `trailers wait to be asked for, and do not pile up on disk`() {
    assertEquals(3, DEFAULT_TRAILER_DELAY_SECONDS)
    assertEquals(24, DEFAULT_TRAILER_CACHE_CLEAR_HOURS)
  }

  @Test
  fun `episode notifications are off until someone turns them on`() {
    val notifications = EpisodeNotificationSettings()
    assertEquals(false, notifications.availableEnabled)
    assertEquals(false, notifications.upcomingEnabled)
    // And when they are on, a day's warning.
    assertEquals(1, notifications.upcomingDays)
  }
}
