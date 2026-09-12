package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class MoviePlaybackActionTest {
  @Test fun neverWatchedStartsWithPlay() {
    assertEquals(MoviePlaybackAction.Play, moviePlaybackAction(false))
  }
  @Test fun partialProgressKeepsResume() {
    assertEquals(MoviePlaybackAction.Resume, moviePlaybackAction(false, 42.0, 2400.0))
    assertEquals(MoviePlaybackAction.Resume, moviePlaybackAction(false, positionSec = 30.0))
  }
  @Test fun completionOverridesTheOldSavedPosition() {
    assertEquals(MoviePlaybackAction.PlayAgain, moviePlaybackAction(false, 40.0, 2400.0, completed = true))
    assertEquals(MoviePlaybackAction.PlayAgain, moviePlaybackAction(false, 95.0, 5700.0))
    assertEquals(MoviePlaybackAction.PlayAgain, moviePlaybackAction(true))
  }
  @Test fun aPartialRewatchResumesDespiteHistoricalWatchedState() {
    assertEquals(MoviePlaybackAction.Resume, moviePlaybackAction(true, 20.0, 1200.0))
  }
  @Test fun explicitUnwatchedClearsOldCompletionAndPosition() {
    assertEquals(MoviePlaybackAction.Play, moviePlaybackAction(true, 100.0, 6000.0, completed = true, unwatched = true))
  }
}
