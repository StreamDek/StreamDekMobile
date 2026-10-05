package net.streamdek.mobile.nativeapp

/*
 * Why Media3 is not producing sound, said precisely enough to act on.
 *
 * "The video is playing and there is no audio" used to be one fact - an empty audio track list -
 * and one response: hand the source to mpv. That treated a release whose sound this phone cannot
 * decode, a track that was there but not chosen, and a decoder that fell over as the same thing,
 * and recorded none of them. Everything here is plain data and plain rules, so the reading of a
 * player's state can be tested without a player.
 */

/** The reason recorded when Media3 gives up a source, or recovers from something inside itself. */
internal enum class ExoFallbackReason(val code: String) {
  AudioNoTrack("EXO_AUDIO_NO_TRACK"),
  AudioTrackSelectionFailed("EXO_AUDIO_TRACK_SELECTION_FAILED"),
  AudioDecoderInitFailed("EXO_AUDIO_DECODER_INIT_FAILED"),
  AudioUnsupportedCodec("EXO_AUDIO_UNSUPPORTED_CODEC"),
  AudioOutputFailed("EXO_AUDIO_OUTPUT_FAILED"),
  AudioStalled("EXO_AUDIO_STALLED"),
  PlaybackFatalError("EXO_PLAYBACK_FATAL_ERROR"),
}

/** Where the fault lies. The letters are the ones the investigation was asked to tell apart. */
internal enum class ExoAudioCause(val letter: Char) {
  NotDiscovered('A'),
  NotSelected('B'),
  DecoderInit('C'),
  Output('D'),
  Unsupported('E'),
  Source('F'),
  Configuration('G'),
  Device('H');

  /** For the log, where a letter alone would need looking up. Never shown to a viewer. */
  val summary: String get() = CAUSE_SUMMARIES.getValue(this)
}

private val CAUSE_SUMMARIES = mapOf(
  ExoAudioCause.NotDiscovered to "audio track not discovered",
  ExoAudioCause.NotSelected to "audio track discovered but not selected",
  ExoAudioCause.DecoderInit to "audio track selected but its decoder could not start",
  ExoAudioCause.Output to "decoder running but the audio output failed",
  ExoAudioCause.Unsupported to "codec, profile or channel layout not supported",
  ExoAudioCause.Source to "manifest or source issue",
  ExoAudioCause.Configuration to "StreamDek configuration or implementation",
  ExoAudioCause.Device to "device-specific decoder issue",
)

/** Whether the player's renderers can play a track, as Media3 reports it. */
internal enum class ExoTrackSupport(val label: String) {
  Handled("handled"),
  ExceedsCapabilities("exceeds-capabilities"),
  UnsupportedDrm("unsupported-drm"),
  UnsupportedSubtype("unsupported-codec"),
  UnsupportedType("no-renderer"),
}

internal data class ExoAudioTrackInfo(
  val mimeType: String? = null,
  val codecs: String? = null,
  val channelCount: Int? = null,
  val sampleRateHz: Int? = null,
  val bitrateBps: Int? = null,
  val language: String? = null,
  val label: String? = null,
  val support: ExoTrackSupport = ExoTrackSupport.Handled,
  val selected: Boolean = false,
) {
  fun describe(): String = listOfNotNull(
    mimeType?.substringAfter('/') ?: "unknown",
    codecs?.takeIf { it.isNotBlank() },
    channelCount?.let { "${it}ch" },
    sampleRateHz?.let { "${it}Hz" },
    bitrateBps?.let { "${it / 1000}kbps" },
    language?.takeIf { it.isNotBlank() },
    support.label,
  ).joinToString("/") + if (selected) "*" else ""
}

/** One look at a Media3 player's audio, taken on the main thread and then only read. */
internal data class ExoAudioSnapshot(
  /** "hls", "dash", "smoothstreaming", or the container of a single file when it is known. */
  val container: String? = null,
  val videoMimeType: String? = null,
  val videoCodecs: String? = null,
  val videoDecoder: String? = null,
  /** Every audio track in the source, including the ones the player cannot play. */
  val tracks: List<ExoAudioTrackInfo> = emptyList(),
  /** Audio switched off in the track selection parameters - something only the app can have done. */
  val audioDisabled: Boolean = false,
  /** Whether the bundled FFmpeg decoders loaded. Without them only the device's own decoders exist. */
  val softwareDecodersLoaded: Boolean = true,
  val softwareDecoderVersion: String? = null,
  val audioDecoder: String? = null,
  val audioDecoderError: String? = null,
  val audioOutputError: String? = null,
  /** Audio buffers handed to the output since the decoder started; null before there is a decoder. */
  val audioBuffersRendered: Int? = null,
  val videoFramesRendered: Int? = null,
  val tunneling: Boolean = false,
) {
  val selectedTrack: ExoAudioTrackInfo? get() = tracks.firstOrNull { it.selected }
}

internal data class ExoAudioFinding(val reason: ExoFallbackReason, val cause: ExoAudioCause, val detail: String)

/** Video frames shown with not one audio buffer played before the sound is called stalled: about two seconds. */
internal const val EXO_AUDIO_STALL_VIDEO_FRAMES = 48

/** The audio the bundled FFmpeg build decodes. A track of one of these should never be unsupported. */
private val BUNDLED_AUDIO_MIME_TYPES = setOf(
  "audio/ac3", "audio/eac3", "audio/eac3-joc", "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.hd;profile=lbr",
  "audio/true-hd", "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2", "audio/mp4a-latm", "audio/vorbis", "audio/opus",
  "audio/flac", "audio/alac", "audio/3gpp", "audio/amr-wb", "audio/g711-mlaw", "audio/g711-alaw",
)

internal fun isBundledAudioMimeType(mimeType: String?): Boolean = mimeType?.lowercase() in BUNDLED_AUDIO_MIME_TYPES

/**
 * Android's own software codecs. Anything else that names a decoder is the device maker's, and a
 * failure there is the device's rather than the stream's.
 */
internal fun isPlatformSoftwareDecoder(name: String?): Boolean {
  val lower = name?.lowercase() ?: return false
  return lower.startsWith("c2.android.") || lower.startsWith("omx.google.") || lower.startsWith("ffmpeg") || lower.startsWith("lib")
}

/**
 * Reads a snapshot for an audio fault. Null means the audio is in order as far as Media3 can tell.
 *
 * The order is the order of the pipeline - found, playable, chosen, decoding, heard - so the first
 * stage that is wrong is the one named, and a later symptom of it is not mistaken for the cause.
 */
internal fun diagnoseExoAudio(snapshot: ExoAudioSnapshot): ExoAudioFinding? {
  val tracks = snapshot.tracks
  if (tracks.isEmpty()) {
    val adaptive = snapshot.container == "hls" || snapshot.container == "dash" || snapshot.container == "smoothstreaming"
    return ExoAudioFinding(
      ExoFallbackReason.AudioNoTrack,
      if (adaptive) ExoAudioCause.Source else ExoAudioCause.NotDiscovered,
      if (adaptive) "the ${snapshot.container} manifest declares no audio" else "no audio track found in ${snapshot.container ?: "the source"}",
    )
  }
  val playable = tracks.filter { it.support == ExoTrackSupport.Handled }
  if (playable.isEmpty()) {
    val bundled = tracks.filter { isBundledAudioMimeType(it.mimeType) }
    val listed = tracks.joinToString(", ") { it.describe() }
    return when {
      // The decoders that exist for exactly this audio are in the app and did not load.
      !snapshot.softwareDecodersLoaded && bundled.isNotEmpty() -> ExoAudioFinding(
        ExoFallbackReason.AudioUnsupportedCodec, ExoAudioCause.Configuration,
        "bundled software decoders did not load; no decoder for $listed",
      )
      tracks.all { it.support == ExoTrackSupport.ExceedsCapabilities } -> ExoAudioFinding(
        ExoFallbackReason.AudioUnsupportedCodec, ExoAudioCause.Unsupported,
        "profile or channel layout beyond this device: $listed",
      )
      else -> ExoAudioFinding(ExoFallbackReason.AudioUnsupportedCodec, ExoAudioCause.Unsupported, "no decoder for $listed")
    }
  }
  val selected = snapshot.selectedTrack
  if (selected == null) {
    return ExoAudioFinding(
      ExoFallbackReason.AudioTrackSelectionFailed,
      if (snapshot.audioDisabled) ExoAudioCause.Configuration else ExoAudioCause.NotSelected,
      (if (snapshot.audioDisabled) "audio is switched off in the track selection; " else "") +
        "${playable.size} playable track(s), none selected: " + playable.joinToString(", ") { it.describe() },
    )
  }
  snapshot.audioDecoderError?.let { failure ->
    val device = snapshot.audioDecoder != null && !isPlatformSoftwareDecoder(snapshot.audioDecoder)
    return ExoAudioFinding(
      ExoFallbackReason.AudioDecoderInitFailed,
      if (device) ExoAudioCause.Device else ExoAudioCause.DecoderInit,
      "${snapshot.audioDecoder ?: "decoder"} failed on ${selected.describe()}: $failure",
    )
  }
  snapshot.audioOutputError?.let { failure ->
    return ExoAudioFinding(ExoFallbackReason.AudioOutputFailed, ExoAudioCause.Output, "audio output failed for ${selected.describe()}: $failure")
  }
  val video = snapshot.videoFramesRendered ?: 0
  if (!snapshot.tunneling && video >= EXO_AUDIO_STALL_VIDEO_FRAMES && (snapshot.audioBuffersRendered ?: 0) == 0) {
    return ExoAudioFinding(
      ExoFallbackReason.AudioStalled, ExoAudioCause.Output,
      "$video video frames shown and no audio played from ${selected.describe()} (decoder ${snapshot.audioDecoder ?: "not started"})",
    )
  }
  return null
}

/** What an in-player recovery can do about a finding before another engine is asked. */
internal enum class ExoAudioRecovery {
  /** Clear whatever is keeping audio unselected and let the selector choose again. */
  ReselectAudio,
  /** Play a different audio track of the same source that this device can decode. */
  SwitchAudioTrack,
  /** Open the HLS source again reading its segments, since its manifest did not declare the audio. */
  ProbeHlsSegments,
  /** Prepare the renderers again: a decoder or output that failed to start once often starts the second time. */
  RetryRenderers,
}

/**
 * The recovery worth trying for [finding], or null when only another engine can help.
 *
 * Each is tried at most once per source ([tried]), so recovery can never become a loop. A release
 * whose only audio this device cannot decode is not recoverable here by design: that is exactly
 * what the hand-over to mpv exists for.
 */
internal fun exoAudioRecoveryFor(finding: ExoAudioFinding, snapshot: ExoAudioSnapshot, tried: Set<ExoAudioRecovery>): ExoAudioRecovery? {
  val alternative = snapshot.tracks.any { it.support == ExoTrackSupport.Handled && !it.selected }
  val candidate = when (finding.reason) {
    ExoFallbackReason.AudioNoTrack -> ExoAudioRecovery.ProbeHlsSegments.takeIf { snapshot.container == "hls" }
    ExoFallbackReason.AudioTrackSelectionFailed -> ExoAudioRecovery.ReselectAudio
    ExoFallbackReason.AudioDecoderInitFailed ->
      if (alternative && ExoAudioRecovery.SwitchAudioTrack !in tried) ExoAudioRecovery.SwitchAudioTrack else ExoAudioRecovery.RetryRenderers
    ExoFallbackReason.AudioOutputFailed, ExoFallbackReason.AudioStalled -> ExoAudioRecovery.RetryRenderers
    ExoFallbackReason.AudioUnsupportedCodec, ExoFallbackReason.PlaybackFatalError -> null
  }
  return candidate?.takeIf { it !in tried }
}

/** One line for the log: the reason first, then everything that was looked at to reach it. */
internal fun exoAudioLogLine(finding: ExoAudioFinding?, snapshot: ExoAudioSnapshot): String = buildString {
  append(finding?.reason?.code ?: "EXO_AUDIO_OK")
  finding?.let { append(" cause=").append(it.cause.letter).append(" (").append(it.cause.summary).append(") detail=\"").append(it.detail).append('"') }
  append(" container=").append(snapshot.container ?: "unknown")
  append(" video=").append(listOfNotNull(snapshot.videoMimeType?.substringAfter('/'), snapshot.videoCodecs).joinToString("/").ifEmpty { "none" })
  append(" videoDecoder=").append(snapshot.videoDecoder ?: "none")
  append(" audioTracks=[").append(snapshot.tracks.joinToString(", ") { it.describe() }).append(']')
  append(" audioDecoder=").append(snapshot.audioDecoder ?: "none")
  snapshot.audioDecoder?.let { append(if (isPlatformSoftwareDecoder(it)) "(software)" else "(hardware)") }
  append(" softwareDecoders=").append(if (snapshot.softwareDecodersLoaded) "loaded" else "NOT-LOADED")
  snapshot.softwareDecoderVersion?.let { append('(').append(it).append(')') }
  append(" audioBuffers=").append(snapshot.audioBuffersRendered ?: 0)
  append(" videoFrames=").append(snapshot.videoFramesRendered ?: 0)
  if (snapshot.audioDisabled) append(" audioDisabled=true")
  if (snapshot.tunneling) append(" tunneling=true")
  snapshot.audioDecoderError?.let { append(" decoderError=\"").append(it).append('"') }
  snapshot.audioOutputError?.let { append(" outputError=\"").append(it).append('"') }
}

/**
 * The reason for a fatal Media3 error, from its error code and the kind of renderer that threw it.
 *
 * Media3 numbers its errors by area: 4xxx is decoding and 5xxx is the audio output. A decoding
 * error counts as audio only when the audio renderer raised it; the same codes come from video.
 */
internal fun exoErrorReason(errorCode: Int, fromAudioRenderer: Boolean): ExoFallbackReason = when {
  errorCode in 5000..5999 -> ExoFallbackReason.AudioOutputFailed
  !fromAudioRenderer -> ExoFallbackReason.PlaybackFatalError
  errorCode == 4004 || errorCode == 4005 -> ExoFallbackReason.AudioUnsupportedCodec
  errorCode in 4000..4999 -> ExoFallbackReason.AudioDecoderInitFailed
  else -> ExoFallbackReason.PlaybackFatalError
}
