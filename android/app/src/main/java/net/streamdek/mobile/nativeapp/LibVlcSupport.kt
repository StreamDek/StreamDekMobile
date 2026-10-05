package net.streamdek.mobile.nativeapp

import java.util.Locale

/*
 * What libVLC can and cannot do, as facts the rest of the player can ask about before choosing an
 * engine - checked against the libVLC 3.7 Android bindings and the VLC 3.0 and 4.0 sources, not
 * assumed. Nothing here touches libVLC itself, so all of it is unit-tested.
 */

/**
 * The request headers libVLC's HTTP access can send. Its HTTP module reads exactly two options,
 * `http-user-agent` and `http-referrer`, in VLC 3.0 and in VLC 4.0 alike; there is no option for
 * an arbitrary header, an `Authorization` value, an `Origin` or a preset cookie.
 */
private val LIBVLC_SENDABLE_HEADERS = setOf("user-agent", "referer")

/**
 * Headers a source may carry that playback does not depend on: libVLC sends its own values for
 * these, or they describe a single request rather than the session.
 */
private val LIBVLC_IGNORABLE_HEADERS = setOf(
  "accept", "accept-language", "accept-encoding", "connection", "range", "icy-metadata", "cache-control", "pragma",
)

/** The headers this source needs that libVLC has no way to send, by name. Empty means it can play. */
internal fun headersLibVlcCannotSend(headers: Map<String, String>?): List<String> =
  headers.orEmpty()
    .filter { (name, value) -> name.isNotBlank() && value.isNotBlank() }
    .keys
    .filter { name ->
      val key = name.trim().lowercase(Locale.ROOT)
      key !in LIBVLC_SENDABLE_HEADERS && key !in LIBVLC_IGNORABLE_HEADERS
    }

/** Why libVLC cannot be the engine for a source. Known before anything is opened. */
internal enum class LibVlcBlocker {
  /** The source needs request headers libVLC cannot send. */
  Headers,
  /** The source is ClearKey-protected, which only Media3 decrypts. */
  ClearKey,
}

internal fun libVlcBlocker(headers: Map<String, String>?, clearKeyProtected: Boolean): LibVlcBlocker? = when {
  clearKeyProtected -> LibVlcBlocker.ClearKey
  headersLibVlcCannotSend(headers).isNotEmpty() -> LibVlcBlocker.Headers
  else -> null
}

/** What libVLC found wrong with a source it did open. */
internal enum class LibVlcCompatibilityProblem { NoAudio, NoVideo }

/**
 * Whether a source that has been playing for a while is only half playing, from libVLC's own
 * counters: a track that exists and is selected but has produced nothing while the other half
 * runs. Both sides at zero is "not started yet" (or statistics are off) and is never a problem.
 */
internal fun libVlcCompatibilityProblem(
  hasAudioTrack: Boolean,
  audioSelected: Boolean,
  hasVideoTrack: Boolean,
  decodedAudio: Long,
  playedAudioBuffers: Long,
  decodedVideo: Long,
  displayedPictures: Long,
): LibVlcCompatibilityProblem? {
  val audioRunning = decodedAudio > 0L || playedAudioBuffers > 0L
  val videoRunning = decodedVideo > 0L || displayedPictures > 0L
  return when {
    hasAudioTrack && audioSelected && !audioRunning && videoRunning -> LibVlcCompatibilityProblem.NoAudio
    hasVideoTrack && !videoRunning && audioRunning -> LibVlcCompatibilityProblem.NoVideo
    else -> null
  }
}

private fun fourcc(code: String): Int =
  code[0].code or (code[1].code shl 8) or (code[2].code shl 16) or (code[3].code shl 24)

private val DOLBY_VISION_FOURCCS = setOf(fourcc("dvhe"), fourcc("dvh1"), fourcc("dvav"), fourcc("dva1"))

/**
 * Whether a video track is declared as Dolby Vision with no HDR10-compatible signalling.
 *
 * VLC 3.0's MP4 demuxer keeps the `dvhe`/`dvh1`/`dvav`/`dva1` sample entry as the track's original
 * fourcc, and that entry is what profile 5 (and single-track profile 7) files use. libVLC decodes
 * such a track as plain HEVC or H.264 through MediaCodec and applies none of the Dolby Vision
 * metadata, which for profile 5 means wrong colours. Matroska gives libVLC no such marker, so a
 * Dolby Vision MKV is not detected here: its HDR10 base layer plays as HDR10.
 */
internal fun isDolbyVisionOnlyFourcc(originalFourcc: Int): Boolean = originalFourcc in DOLBY_VISION_FOURCCS

/**
 * libVLC describes a codec in words ("H264 - MPEG-4 AVC (part 10)"); the info panel names codecs
 * from short keys. Returns the key where the description is recognised, otherwise the description.
 */
internal fun libVlcCodecKey(description: String?): String? {
  val text = description?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val lower = text.lowercase(Locale.ROOT)
  return when {
    "hevc" in lower || "h.265" in lower || "h265" in lower -> "hevc"
    "h264" in lower || "h.264" in lower || "avc" in lower -> "h264"
    "av1" in lower || "aomedia" in lower -> "av1"
    "vp9" in lower -> "vp9"
    "vp8" in lower -> "vp8"
    "truehd" in lower || "mlp" in lower -> "truehd"
    "e-ac3" in lower || "e-ac-3" in lower || "eac3" in lower || "a/52 b" in lower -> "eac3"
    "ac3" in lower || "ac-3" in lower || "a52" in lower || "a/52" in lower -> "ac3"
    "dts" in lower -> "dts"
    "aac" in lower || "mp4a" in lower -> "aac"
    "opus" in lower -> "opus"
    "flac" in lower -> "flac"
    "mp3" in lower || "layer 3" in lower || "layer iii" in lower -> "mp3"
    else -> text
  }
}

// Profile names are the standards' own terms, the same in every language.
private val H264_PROFILES = mapOf(
  66 to "Baseline", 77 to "Main", 88 to "Extended", 100 to "High", 110 to "High 10", 122 to "High 4:2:2", 244 to "High 4:4:4",
)
private val HEVC_PROFILES = mapOf(1 to "Main", 2 to "Main 10", 3 to "Main Still Picture", 4 to "Range Extensions")

/** A codec profile as it is usually written, from the number the bitstream carries. */
internal fun libVlcProfileName(codecKey: String?, profile: Int, level: Int): String? {
  val name = when (codecKey) {
    "h264" -> H264_PROFILES[profile]
    "hevc" -> HEVC_PROFILES[profile]
    else -> null
  } ?: return null
  val levelText = when (codecKey) {
    // H.264 writes level 4.1 as 41; HEVC writes 5.1 as 153 (thirty times the level).
    "h264" -> level.takeIf { it in 10..62 }?.let { String.format(Locale.US, "%d.%d", it / 10, it % 10) }
    "hevc" -> level.takeIf { it in 30..186 && it % 3 == 0 }?.let { String.format(Locale.US, "%d.%d", it / 30, (it % 30) / 3) }
    else -> null
  }
  return if (levelText == null) name else "$name@L$levelText"
}

/** Bits per sample, where the profile settles it. Null when the profile allows more than one. */
internal fun libVlcBitDepth(codecKey: String?, profile: Int): Int? = when (codecKey) {
  "h264" -> when (profile) {
    66, 77, 88, 100 -> 8
    110 -> 10
    else -> null
  }
  "hevc" -> when (profile) {
    1, 3 -> 8
    2 -> 10
    else -> null
  }
  else -> null
}

/**
 * How much libVLC buffers before it starts, in milliseconds. This is latency as much as it is
 * safety: every millisecond here is a millisecond before the first frame and after every seek, and
 * a larger number keeps the radio busy for longer at each start. So it follows what the source is
 * rather than one large value for everything. libVLC raises its own delay when a stream runs late,
 * so the starting figure need not cover the worst case.
 */
internal fun libVlcNetworkCachingMs(url: String, live: Boolean): Int {
  val host = url.substringAfter("://", "").substringBefore('/').substringAfterLast('@').substringBefore(':').lowercase(Locale.ROOT)
  val onThisDeviceOrNetwork = host == "localhost" || host.startsWith("127.") || host.startsWith("10.") ||
    host.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2\\d|3[01])\\..*").matches(host)
  return when {
    // This device's own loopback servers (torrent, usenet, downloads) and media servers on the
    // home network answer in milliseconds.
    onThisDeviceOrNetwork -> 800
    // A live feed has no past to refill from; a little more rides out an uneven segment.
    live -> 1_500
    else -> 1_500
  }
}

/** libVLC's default for a file on disk; said out loud so the bindings do not raise it to 1.5 s. */
internal const val LIBVLC_FILE_CACHING_MS = 300
