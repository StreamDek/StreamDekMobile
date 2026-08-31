package net.streamdek.mobile.nativeapp

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The motion helpers, called rather than only compiled.
 *
 * Each of these used to delegate to a bare `tween(...)`, which inside `StreamDekMotion` resolves to
 * the member rather than to the imported platform function - and because every argument on that
 * overload has a default, it compiled and then recursed until the stack ran out. Nothing failed at
 * build time and nothing failed until the first screen actually asked for one of them, so these
 * tests exist to make the calls happen somewhere cheaper than a device.
 */
class StreamDekMotionTest {

  @Test
  fun `tween returns a spec instead of calling itself`() {
    val spec = StreamDekMotion.tween<Float>(durationMillis = StreamDekMotion.normal)
    assertEquals(StreamDekMotion.normal, (spec as TweenSpec).durationMillis)
  }

  @Test
  fun `reduced motion collapses a tween to no duration but still changes state`() {
    val spec = StreamDekMotion.tween<Float>(durationMillis = StreamDekMotion.slow, reduced = true)
    assertEquals(0, (spec as TweenSpec).durationMillis)
  }

  @Test
  fun `a spring is a spring normally and an instant tween under reduced motion`() {
    assertTrue(StreamDekMotion.gentleSpring<Float>() is SpringSpec)
    assertEquals(0, (StreamDekMotion.gentleSpring<Float>(reduced = true) as TweenSpec).durationMillis)
  }

  @Test
  fun `the offset spring behaves the same way`() {
    assertTrue(StreamDekMotion.offsetSpring() is SpringSpec)
    val reduced = StreamDekMotion.offsetSpring(reduced = true)
    assertEquals(0, (reduced as TweenSpec<IntOffset>).durationMillis)
  }
}
