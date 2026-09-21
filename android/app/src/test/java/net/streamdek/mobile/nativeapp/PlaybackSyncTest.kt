package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** The arithmetic behind the subtitle and audio delay controls. */
class PlaybackSyncTest {

  @Test
  fun `ten tenths make exactly one second, and undoing them lands exactly on zero`() {
    var value = 0.0
    repeat(10) { value = steppedDelay(value, 0.1, SUBTITLE_DELAY_LIMIT_SECONDS) }
    assertEquals(1.0, value, 0.0)
    repeat(10) { value = steppedDelay(value, -0.1, SUBTITLE_DELAY_LIMIT_SECONDS) }
    assertEquals(0.0, value, 0.0)
  }

  @Test
  fun `subtitle delay reaches two minutes either way and stops there`() {
    assertEquals(120.0, steppedDelay(115.0, 10.0, SUBTITLE_DELAY_LIMIT_SECONDS), 0.0)
    assertEquals(-120.0, steppedDelay(-119.9, -1.0, SUBTITLE_DELAY_LIMIT_SECONDS), 0.0)
  }

  @Test
  fun `audio delay is held to its own, smaller range`() {
    assertEquals(5.0, steppedDelay(4.8, 0.5, AUDIO_DELAY_LIMIT_SECONDS), 0.0)
    assertEquals(-0.05, steppedDelay(0.0, -0.05, AUDIO_DELAY_LIMIT_SECONDS), 0.0)
  }

  @Test
  fun `a loaded subtitle file honours the full range`() {
    // Positive delay shows a cue later, so presentation reads an earlier point in the file.
    assertEquals(0L, delayedSubtitlePositionUs(90_000L, 90.0))
    assertEquals(210_000_000L, delayedSubtitlePositionUs(90_000L, -120.0))
    // Beyond the range is held to it rather than wrapping or being ignored.
    assertEquals(-60_000_000L, delayedSubtitlePositionUs(60_000L, 500.0))
  }
}
