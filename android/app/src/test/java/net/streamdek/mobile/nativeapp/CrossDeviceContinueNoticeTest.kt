package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossDeviceContinueNoticeTest {
  @Test
  fun movieNoticeExplainsWhyAnewSourceIsNeeded() {
    val notice = crossDeviceContinueNotice("movie")

    assertEquals(
      "You started this movie on another device. Choose a source to continue watching from where you left off.",
      notice,
    )
    assertEquals(7_000L, appInfoNoticeDuration(notice))
  }

  @Test
  fun seriesNoticeNamesTheEpisodeBeingResumed() {
    val notice = crossDeviceContinueNotice("tv", seasonNumber = 2, episodeNumber = 3)

    assertEquals(
      "You started this series on another device. Choose a source for Season 2, Episode 3 to continue watching from where you left off.",
      notice,
    )
    assertEquals(7_000L, appInfoNoticeDuration(notice))
  }

  @Test
  fun ordinaryInfoNoticesKeepTheirExistingDuration() {
    assertEquals(5_000L, appInfoNoticeDuration("Watchlist updated."))
  }
}
