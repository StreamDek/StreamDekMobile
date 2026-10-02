package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferedRangesTest {
  @Test
  fun `nothing is drawn without a duration`() {
    assertTrue(bufferedSegments(listOf(BufferedRange(0.0, 30.0)), durationSec = 0.0, playedSec = 0.0).isEmpty())
    assertTrue(bufferedSegments(listOf(BufferedRange(0.0, 30.0)), durationSec = Double.NaN, playedSec = 0.0).isEmpty())
  }

  @Test
  fun `buffered starts at the playhead and ends where the engine says`() {
    val segments = bufferedSegments(listOf(BufferedRange(20.0, 60.0)), durationSec = 100.0, playedSec = 25.0)
    assertEquals(1, segments.size)
    assertEquals(0.25f, segments[0].start, 0.0001f)
    assertEquals(0.60f, segments[0].end, 0.0001f)
  }

  @Test
  fun `a range wholly behind the playhead is not drawn`() {
    assertTrue(bufferedSegments(listOf(BufferedRange(0.0, 20.0)), durationSec = 100.0, playedSec = 40.0).isEmpty())
  }

  @Test
  fun `touching ranges join and a real gap is kept`() {
    val segments = bufferedSegments(
      listOf(BufferedRange(50.0, 60.0), BufferedRange(10.0, 20.0), BufferedRange(20.2, 30.0)),
      durationSec = 100.0,
      playedSec = 10.0,
    )
    assertEquals(2, segments.size)
    assertEquals(0.10f, segments[0].start, 0.0001f)
    assertEquals(0.30f, segments[0].end, 0.0001f)
    assertEquals(0.50f, segments[1].start, 0.0001f)
    assertEquals(0.60f, segments[1].end, 0.0001f)
  }

  @Test
  fun `ranges are clamped to the timeline and bad ones dropped`() {
    val segments = bufferedSegments(
      listOf(BufferedRange(90.0, 500.0), BufferedRange(30.0, 10.0), BufferedRange(Double.NaN, 5.0)),
      durationSec = 100.0,
      playedSec = 0.0,
    )
    assertEquals(1, segments.size)
    assertEquals(0.90f, segments[0].start, 0.0001f)
    assertEquals(1.0f, segments[0].end, 0.0001f)
  }

  @Test
  fun `ranges outside the timeline altogether draw nothing`() {
    assertTrue(bufferedSegments(listOf(BufferedRange(4000.0, 4030.0)), durationSec = 120.0, playedSec = 60.0).isEmpty())
  }

  @Test
  fun `mpv cache state yields its seekable ranges`() {
    val json = """{"cache-end":95.5,"reader-pts":61.2,"seekable-ranges":[{"start":12.5,"end":40.0},{"start":60.0,"end":95.5}],"bof-cached":false,"eof-cached":false,"fw-bytes":123}"""
    assertEquals(listOf(BufferedRange(12.5, 40.0), BufferedRange(60.0, 95.5)), parseMpvSeekableRanges(json))
  }

  @Test
  fun `unreadable mpv cache state yields nothing`() {
    assertTrue(parseMpvSeekableRanges(null).isEmpty())
    assertTrue(parseMpvSeekableRanges("").isEmpty())
    assertTrue(parseMpvSeekableRanges("""{"seekable-ranges":[]}""").isEmpty())
    assertTrue(parseMpvSeekableRanges("(unavailable)").isEmpty())
  }

  @Test
  fun `a longer buffer gets a larger memory budget but never more than the heap can spare`() {
    val mib = 1024L * 1024L
    // One minute fits inside Media3's own ceiling, so that is left alone.
    assertEquals(null, forwardBufferBudgetBytes(60, 512 * mib))
    // Five minutes wants more than a 512 MiB heap can spare: capped at about a third of it.
    assertEquals((512 * mib * 0.35).toLong().toInt(), forwardBufferBudgetBytes(300, 512 * mib))
    // On a small heap nothing above Media3's ceiling is affordable, so it is left alone again.
    assertEquals(null, forwardBufferBudgetBytes(600, 256 * mib))
    // With room to spare the budget is the time asked for at 10 Mbit/s.
    assertEquals(180 * 1_250_000, forwardBufferBudgetBytes(180, 2048 * mib))
  }

  @Test
  fun `a stored buffer length lands on a real option`() {
    assertEquals(300, PlaybackCodecOptions.normalizeForwardBufferSeconds(300))
    assertEquals(60, PlaybackCodecOptions.normalizeForwardBufferSeconds(0))
    assertEquals(600, PlaybackCodecOptions.normalizeForwardBufferSeconds(9_999))
    assertEquals(180, PlaybackCodecOptions.normalizeForwardBufferSeconds(200))
  }
}
