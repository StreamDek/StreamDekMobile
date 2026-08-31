package net.streamdek.mobile.nativeapp

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.ssa.SsaParser
import androidx.media3.extractor.text.subrip.SubripParser
import androidx.media3.extractor.text.webvtt.WebvttParser
import java.io.File

/** Parses a validated local subtitle without involving the video player's media source. */
@OptIn(UnstableApi::class)
internal fun parseExternalSubtitleCues(path: String): List<CuesWithTiming> {
  val file = File(path)
  val parser: SubtitleParser = when (file.extension.lowercase()) {
    "vtt" -> WebvttParser()
    "ass", "ssa" -> SsaParser()
    else -> SubripParser()
  }
  val bytes = file.readBytes()
  return buildList {
    parser.parse(bytes, 0, bytes.size, SubtitleParser.OutputOptions.allCues()) { add(it) }
  }.sortedBy(CuesWithTiming::startTimeUs)
}

/** Positive delay shows a cue later, so presentation reads an earlier point in its timeline. */
internal fun delayedSubtitlePositionUs(playbackPositionMs: Long, delaySeconds: Double): Long =
  playbackPositionMs * 1_000L - (delaySeconds.coerceIn(-15.0, 15.0) * 1_000_000.0).toLong()
