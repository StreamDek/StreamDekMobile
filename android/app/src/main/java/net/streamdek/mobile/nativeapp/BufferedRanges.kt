package net.streamdek.mobile.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * One stretch of media the engine is holding, in the same seconds the playhead is reported in.
 *
 * This is what the engine says it has, never a guess: a controller that cannot answer returns no
 * ranges at all, and the timeline then draws no buffered state rather than an invented one.
 */
data class BufferedRange(val startSec: Double, val endSec: Double)

/** A buffered stretch as fractions of the timeline, ready to draw. */
data class BufferedSegment(val start: Float, val end: Float)

/** Ranges closer together than this are drawn as one; the gap would be under a pixel anyway. */
private const val BufferedJoinGapSec = 0.5

/** A segment narrower than this fraction of the bar is not worth a draw call. */
private const val BufferedMinFraction = 0.0015f

/**
 * Turns what the engine reported into what the timeline draws.
 *
 * Anything unusable is dropped rather than repaired: non-finite or inverted ranges, and everything
 * when there is no duration to measure against (a live channel with no seek window). Ranges are
 * clamped to the timeline, joined where they touch, and cut off at [playedSec] so the buffered tone
 * only ever appears ahead of the playhead - what lies behind it is already drawn as watched.
 * Ranges that are not connected to the playhead (mpv keeps them after a seek) stay as separate
 * segments, so a gap that is not buffered is never painted over.
 */
fun bufferedSegments(ranges: List<BufferedRange>, durationSec: Double, playedSec: Double): List<BufferedSegment> {
  if (ranges.isEmpty() || !durationSec.isFinite() || durationSec <= 0.0) return emptyList()
  val played = if (playedSec.isFinite()) playedSec.coerceIn(0.0, durationSec) else 0.0
  val usable = ranges
    .filter { it.startSec.isFinite() && it.endSec.isFinite() && it.endSec > it.startSec }
    .map { BufferedRange(it.startSec.coerceIn(0.0, durationSec), it.endSec.coerceIn(0.0, durationSec)) }
    .filter { it.endSec > it.startSec }
    .sortedBy { it.startSec }
  if (usable.isEmpty()) return emptyList()
  val merged = ArrayList<BufferedRange>(usable.size)
  for (range in usable) {
    val last = merged.lastOrNull()
    if (last != null && range.startSec <= last.endSec + BufferedJoinGapSec) {
      if (range.endSec > last.endSec) merged[merged.lastIndex] = BufferedRange(last.startSec, range.endSec)
    } else {
      merged += range
    }
  }
  return merged.mapNotNull { range ->
    val start = maxOf(range.startSec, played)
    if (range.endSec <= start) return@mapNotNull null
    val segment = BufferedSegment((start / durationSec).toFloat(), (range.endSec / durationSec).toFloat())
    segment.takeIf { it.end - it.start >= BufferedMinFraction }
  }
}

private val SeekableRangesArray = Regex("\"seekable-ranges\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
private val RangeObject = Regex("\\{([^\\{\\}]*)\\}")
private val RangeStart = Regex("\"start\"\\s*:\\s*(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)")
private val RangeEnd = Regex("\"end\"\\s*:\\s*(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)")

/**
 * Reads the cached ranges out of mpv's `demuxer-cache-state`, as mpv prints it (JSON).
 *
 * Only `seekable-ranges` is read: those are the stretches mpv can play without going back to the
 * network. Anything that does not parse yields no ranges, and the caller falls back to the single
 * range mpv reports as plain numbers.
 */
fun parseMpvSeekableRanges(cacheState: String?): List<BufferedRange> {
  val body = cacheState?.let { SeekableRangesArray.find(it) }?.groupValues?.get(1) ?: return emptyList()
  return RangeObject.findAll(body).mapNotNull { match ->
    val fields = match.groupValues[1]
    val start = RangeStart.find(fields)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
    val end = RangeEnd.find(fields)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
    BufferedRange(start, end).takeIf { end > start }
  }.toList()
}

/**
 * The seek bar's buffered tone: the watched colour, lightened and let through.
 *
 * Derived from whatever the bar paints as watched - the selected theme's accent - rather than
 * named, so every theme gets its own. Mixed toward white so it reads as the softer relative of the
 * watched fill, and translucent so it sits between that fill and the unplayed track in brightness
 * on the dark scrim the player's controls are drawn on.
 */
internal fun playerBufferedColor(played: Color): Color = lerp(played, Color.White, 0.35f).copy(alpha = 0.62f)
