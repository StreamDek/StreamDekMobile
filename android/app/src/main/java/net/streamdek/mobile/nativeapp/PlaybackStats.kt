package net.streamdek.mobile.nativeapp

import java.util.Locale

/**
 * What the playing engine can say about the stream it is pulling, sampled while the info panel is
 * open.
 *
 * Both engines answer a subset: mpv reports a demuxer cache speed and codec names, Media3 reports a
 * bandwidth estimate and the selected formats. Every field is nullable so the panel can omit a line
 * rather than print a zero the engine never measured.
 */
data class PlaybackStats(
  /** Measured transfer rate into the player, bytes per second. */
  val bytesPerSecond: Double? = null,
  /** Nominal bitrate of the selected video track, bits per second. */
  val videoBitrateBps: Double? = null,
  val width: Int = 0,
  val height: Int = 0,
  val videoCodec: String? = null,
  val audioCodec: String? = null,
  val audioChannels: Int? = null,
  val frameRate: Double? = null,
  /** How far ahead of the playhead the engine has buffered, in seconds. */
  val bufferedSeconds: Double? = null,
  /** The hardware decoder in use, when the engine names one. */
  val hardwareDecoder: String? = null,
)

/** How the bytes actually reach the player, which is not always what the source advertised. */
enum class StreamTransport(val label: String) {
  Torrent("Torrent"),
  Usenet("Usenet"),
  Download("Offline download"),
  Hls("HLS"),
  Dash("DASH"),
  Http("Direct HTTP"),
  LocalFile("Local file"),
  Unknown("Stream"),
}

/**
 * Classifies a session by what it is pulling from.
 *
 * The stream's own fields decide first — an info hash means a torrent even though the URL handed to
 * the engine is this device's own loopback server, and the same goes for an NZB. Only when the
 * source says nothing does the URL get a vote.
 */
fun streamTransport(stream: AddonStream?, playbackUrl: String): StreamTransport {
  if (stream?.source.equals("download", ignoreCase = true)) return StreamTransport.Download
  if (!stream?.infoHash.isNullOrBlank()) return StreamTransport.Torrent
  if (!stream?.nzbUrl.isNullOrBlank() || stream?.nntpServers?.isNotEmpty() == true) return StreamTransport.Usenet
  val url = playbackUrl.trim()
  val path = url.substringBefore('?').lowercase(Locale.ROOT)
  return when {
    url.startsWith("magnet:", ignoreCase = true) -> StreamTransport.Torrent
    url.startsWith("file:", ignoreCase = true) || url.startsWith("/") || url.startsWith("content:", ignoreCase = true) -> StreamTransport.LocalFile
    path.endsWith(".m3u8") -> StreamTransport.Hls
    path.endsWith(".mpd") -> StreamTransport.Dash
    url.startsWith("http", ignoreCase = true) -> StreamTransport.Http
    url.isBlank() -> StreamTransport.Unknown
    else -> StreamTransport.Unknown
  }
}

/**
 * Who is serving this stream, as a viewer would name it: the debrid service when one cached it,
 * otherwise the addon that produced it.
 */
fun streamProviderLabel(stream: AddonStream?, fallback: String?): String? {
  if (stream == null) return fallback?.takeIf { it.isNotBlank() }
  val cached = stream.cachedBy.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
  val addon = stream.addonName.trim().takeIf { it.isNotEmpty() } ?: stream.addonId.trim().takeIf { it.isNotEmpty() }
  return when {
    cached.isNotEmpty() -> (listOfNotNull(addon) + cached.joinToString(", ")).joinToString(" · ")
    !stream.source.isNullOrBlank() && !stream.source.equals(addon, ignoreCase = true) ->
      listOfNotNull(addon, stream.source).joinToString(" · ")
    addon != null -> addon
    else -> fallback?.takeIf { it.isNotBlank() }
  }
}

/** "4.2 MB/s", or null when nothing has been measured yet. */
fun formatTransferRate(bytesPerSecond: Double?): String? {
  val rate = bytesPerSecond ?: return null
  if (!rate.isFinite() || rate <= 0.0) return null
  return when {
    rate >= 1_000_000.0 -> String.format(Locale.US, "%.1f MB/s", rate / 1_000_000.0)
    rate >= 1_000.0 -> String.format(Locale.US, "%.0f KB/s", rate / 1_000.0)
    else -> String.format(Locale.US, "%.0f B/s", rate)
  }
}

/** "7.7 Mbps", or null when the engine reported no bitrate. */
fun formatBitrate(bitsPerSecond: Double?): String? {
  val bits = bitsPerSecond ?: return null
  if (!bits.isFinite() || bits <= 0.0) return null
  return when {
    bits >= 1_000_000.0 -> String.format(Locale.US, "%.1f Mbps", bits / 1_000_000.0)
    bits >= 1_000.0 -> String.format(Locale.US, "%.0f kbps", bits / 1_000.0)
    else -> String.format(Locale.US, "%.0f bps", bits)
  }
}

/** A resolution with the shorthand a viewer recognises, e.g. "1920 × 1080 (1080p)". */
fun formatResolution(width: Int, height: Int): String? {
  if (width <= 0 || height <= 0) return null
  val shorthand = when {
    height >= 2000 -> "4K"
    height >= 1400 -> "1440p"
    height >= 1000 -> "1080p"
    height >= 700 -> "720p"
    height >= 500 -> "576p"
    height >= 400 -> "480p"
    else -> null
  }
  return listOfNotNull("$width × $height", shorthand?.let { "($it)" }).joinToString(" ")
}

/** Codec names arrive lowercase and inconsistent; print the ones viewers know by their usual name. */
fun prettyCodecName(codec: String?): String? {
  val raw = codec?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  // Media3 reports RFC 6381 codec strings ("avc1.640028"); only the family matters here.
  val family = raw.substringBefore('.').lowercase(Locale.ROOT)
  return when (family) {
    "avc1", "avc3", "h264" -> "H.264"
    "hvc1", "hev1", "h265", "hevc" -> "HEVC"
    "av01", "av1" -> "AV1"
    "vp09", "vp9" -> "VP9"
    "vp08", "vp8" -> "VP8"
    "mp4a", "aac" -> "AAC"
    "ec-3", "eac3" -> "E-AC-3"
    "ac-3", "ac3" -> "AC-3"
    "dts", "dtshd" -> "DTS"
    "opus" -> "Opus"
    "flac" -> "FLAC"
    "truehd" -> "TrueHD"
    "mp3" -> "MP3"
    else -> raw.uppercase(Locale.ROOT)
  }
}
