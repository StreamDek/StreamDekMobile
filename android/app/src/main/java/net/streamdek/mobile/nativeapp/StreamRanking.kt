package net.streamdek.mobile.nativeapp

/**
 * How a title's stream results are filtered and ordered.
 *
 * All of this runs every time another source answers - while the viewer may be scrolling the page
 * with a trailer playing - so it is written to be cheap: patterns compiled once, and each stream
 * scored once per ranking rather than once per comparison. It reads no app state, which is what
 * lets the ranking run off the main thread; see NativeAppViewModel.streamRanker.
 */

/** "6.83 GB", "700 MB". The trailing boundary keeps bitrates ("7.71 Mbps") from reading as sizes. */
private val STREAM_SIZE_PATTERN = Regex("""([\d.]+)\s*(GB|GiB|MB|MiB|TB|TiB)\b""", RegexOption.IGNORE_CASE)

/**
 * How long a burst of answering sources is gathered into one publish of the result list.
 *
 * Long enough that a CloudStream collection finishing provider after provider costs one ranking and
 * one layout rather than dozens; short enough that the list still visibly fills in as it arrives.
 */
internal const val STREAM_PUBLISH_COALESCE_MS = 250L

private class RankedStream(val stream: AddonStream, val favourite: Boolean, val score: Int, val name: String)

internal fun parseStreamSizeGiB(size: String?): Double? {
  val raw = size?.trim().orEmpty()
  if (raw.isBlank()) return null
  // The trailing boundary keeps bitrates out: without it "~7.71 Mbps" matches as "7.71 MB", so a
  // 6 GB result reads as 7 MB and slips straight past the max-size cap.
  val match = STREAM_SIZE_PATTERN.find(raw) ?: return null
  val value = match.groupValues[1].toDoubleOrNull() ?: return null
  return when (match.groupValues[2].lowercase()) {
    "tb", "tib" -> value * 1024.0
    "mb", "mib" -> value / 1024.0
    else -> value
  }
}

// Some addons only report the size inside the title/description, so fall back to
// scanning the stream text — otherwise those results bypass the max size cap.
internal fun streamSizeGiB(stream: AddonStream): Double? =
  parseStreamSizeGiB(stream.size)
    ?: parseStreamSizeGiB(listOfNotNull(stream.title, stream.name, stream.filename, stream.description).joinToString(" "))

internal fun preferredQualityBoost(stream: AddonStream, preferredQuality: String): Int {
  val quality = preferredQuality.trim()
  if (quality.equals("Auto", ignoreCase = true)) return 0
  val text = listOfNotNull(stream.title, stream.name, stream.filename, stream.description, stream.quality).joinToString(" ").lowercase()
  // An exact match must dominate every other tag bonus combined (codec/audio/remux
  // add up to roughly +300) so the chosen quality genuinely wins the ranking.
  return when (quality) {
    "2160p" -> when {
      "2160" in text || "4k" in text -> 520
      "1080" in text -> 200
      "720" in text -> 90
      else -> 20
    }
    "1080p" -> when {
      "1080" in text -> 520
      "720" in text -> 200
      "2160" in text || "4k" in text -> 120
      else -> 20
    }
    "720p" -> when {
      "720" in text -> 520
      "1080" in text -> 160
      "2160" in text || "4k" in text -> 40
      else -> 20
    }
    else -> 0
  }
}

/** Whether a stream, whichever add-on or plugin produced it, is pornography. */
internal fun streamIsAdult(stream: AddonStream): Boolean = AdultContentFilter.isBlocked(
  stream.url,
  stream.addonId,
  stream.source,
  stream.name,
  stream.title,
  stream.filename,
  stream.addonName,
  // Unlike a catalogue entry, a stream's description is usually the release name rather than a
  // synopsis, and that is exactly where the marker tends to sit.
  stream.description,
)

internal fun rankedStreams(
  streams: List<AddonStream>,
  hasDebrid: Boolean,
  preferredQuality: String = "Auto",
  maxFileSizeGb: Int = 0,
  favouriteAddonIds: Set<String> = emptySet(),
  favouritePluginProviderIds: Set<String> = emptySet(),
): List<AddonStream> =
  streams
    // Every list that reaches the viewer is ranked here first, whatever produced it, so this is
    // the one place the block cannot be routed around by a new caller.
    .filterNot(::streamIsAdult)
    .filter { stream ->
      val sizeGiB = streamSizeGiB(stream)
      maxFileSizeGb <= 0 || sizeGiB == null || sizeGiB <= maxFileSizeGb.toDouble()
    }
    // Each stream's keys are worked out once and the sort compares those. A selector inside a
    // comparator runs on every comparison - about n log n of them - and scoring a stream joins,
    // lowercases and size-parses all of its text, so a hundred results cost well over a thousand
    // scorings per ranking, and the list is ranked again every time another source answers.
    .map { stream ->
      RankedStream(
        stream = stream,
        favourite = stream.addonId in favouriteAddonIds || stream.addonId.removePrefix("plugin:") in favouritePluginProviderIds,
        score = streamScore(stream, hasDebrid, preferredQuality, maxFileSizeGb),
        name = stream.title ?: stream.name ?: stream.filename ?: "",
      )
    }
    .sortedWith(
      compareByDescending<RankedStream> { it.favourite }
        .thenByDescending { it.score }
        .thenBy { it.name },
    )
    .map { it.stream }

internal fun streamScore(stream: AddonStream, hasDebrid: Boolean, preferredQuality: String = "Auto", maxFileSizeGb: Int = 0): Int {
  val text = listOfNotNull(stream.title, stream.name, stream.filename, stream.description, stream.quality, stream.addonName).joinToString(" ").lowercase()
  val sizeGiB = streamSizeGiB(stream)
  var score = 0
  if (maxFileSizeGb > 0 && sizeGiB != null && sizeGiB > maxFileSizeGb.toDouble()) return Int.MIN_VALUE / 4
  if (!stream.url.isNullOrBlank()) score += 380
  if (hasDebrid && !stream.infoHash.isNullOrBlank()) score += 260
  if (stream.cachedBy.isNotEmpty()) score += 400 + ((stream.cachedBy.size - 1).coerceAtLeast(0) * 45)
  score += when {
    "2160" in text || "4k" in text -> 90
    "1080" in text -> 75
    "720" in text -> 45
    else -> 20
  }
  if ("english" in text || "multi" in text) score += 70
  if ("remux" in text) score += 35
  if ("web-dl" in text || "webdl" in text) score += 26
  if ("bluray" in text || "blu-ray" in text) score += 12
  if ("aac" in text || "flac" in text || "mp3" in text || "opus" in text) score += 90
  if ("ac3" in text || "eac3" in text || "dd+" in text) score += 40
  if ("dts:x" in text) score -= 160
  else if ("dts" in text) score -= 90
  if ("h264" in text || "h.264" in text || "avc" in text || "x264" in text) score += 120
  if ("av1" in text) score -= 80
  if ("hevc" in text || "x265" in text || "h265" in text) score += 20
  if ("cam" in text) score -= 200
  if ("telesync" in text) score -= 120
  score += preferredQualityBoost(stream, preferredQuality)
  if (sizeGiB != null) {
    score += when {
      sizeGiB in 0.6..12.5 -> 30
      sizeGiB > 30.0 -> -65
      sizeGiB < 0.25 -> -55
      else -> 0
    }
  }
  return score
}
