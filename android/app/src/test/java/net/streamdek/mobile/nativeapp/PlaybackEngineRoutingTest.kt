package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEngineRoutingTest {
  @Test
  fun `libvlc is skipped for a source it is known not to open`() {
    assertEquals(ActivePlaybackEngine.VLC, routedPlaybackEngine("VLC", null))
    assertEquals(ActivePlaybackEngine.Media3, routedPlaybackEngine("VLC", LibVlcBlocker.Headers))
    assertEquals(ActivePlaybackEngine.Media3, routedPlaybackEngine("VLC", LibVlcBlocker.ClearKey))
    // The other preferences are not touched by what libVLC can or cannot do.
    assertEquals(ActivePlaybackEngine.MPV, routedPlaybackEngine("MPV", LibVlcBlocker.Headers))
    assertEquals(ActivePlaybackEngine.Media3, routedPlaybackEngine("Auto", LibVlcBlocker.Headers))
  }

  @Test
  fun `a viewer who chose libvlc and is not on it gets the auto path`() {
    assertEquals("Auto", effectiveEnginePreference("VLC", ActivePlaybackEngine.Media3))
    assertEquals("VLC", effectiveEnginePreference("VLC", ActivePlaybackEngine.VLC))
    assertEquals("MPV", effectiveEnginePreference("MPV", ActivePlaybackEngine.Media3))
    assertEquals("Auto", effectiveEnginePreference("Auto", ActivePlaybackEngine.MPV))
  }

  @Test
  fun `a failed engine is not handed the same source again`() {
    val trail = PlaybackEngineTrail(ActivePlaybackEngine.VLC)
    val second = trail.nextAfterFailure(ActivePlaybackEngine.VLC)
    assertEquals(ActivePlaybackEngine.Media3, second)
    trail.moveTo(second!!)
    val third = trail.nextAfterFailure(ActivePlaybackEngine.Media3)
    assertEquals(ActivePlaybackEngine.MPV, third)
    trail.moveTo(third!!)
    assertNull(trail.nextAfterFailure(ActivePlaybackEngine.MPV))
    assertEquals(listOf(ActivePlaybackEngine.VLC, ActivePlaybackEngine.Media3, ActivePlaybackEngine.MPV), trail.engines)
    assertTrue(trail.hasFailed(ActivePlaybackEngine.VLC))
  }

  @Test
  fun `libvlc never takes over from another engine`() {
    val trail = PlaybackEngineTrail(ActivePlaybackEngine.Media3)
    assertEquals(ActivePlaybackEngine.MPV, trail.nextAfterFailure(ActivePlaybackEngine.Media3))
    trail.moveTo(ActivePlaybackEngine.MPV)
    assertNull(trail.nextAfterFailure(ActivePlaybackEngine.MPV))
  }

  @Test
  fun `a clearkey source has only media3 to fall back to`() {
    val trail = PlaybackEngineTrail(ActivePlaybackEngine.MPV)
    assertEquals(ActivePlaybackEngine.Media3, trail.nextAfterFailure(ActivePlaybackEngine.MPV, LibVlcBlocker.ClearKey))
    trail.moveTo(ActivePlaybackEngine.Media3)
    assertNull(trail.nextAfterFailure(ActivePlaybackEngine.Media3, LibVlcBlocker.ClearKey))
  }

  @Test
  fun `a manual switch is recorded without being a failure`() {
    val trail = PlaybackEngineTrail(ActivePlaybackEngine.Media3)
    trail.moveTo(ActivePlaybackEngine.VLC)
    assertEquals(ActivePlaybackEngine.Media3, trail.nextAfterFailure(ActivePlaybackEngine.VLC))
    assertEquals("ExoPlayer → libVLC (failed)", trail.describe())
  }
}
