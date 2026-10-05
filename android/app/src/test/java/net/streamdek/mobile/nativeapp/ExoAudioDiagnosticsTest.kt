package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExoAudioDiagnosticsTest {
  private val aac = ExoAudioTrackInfo(mimeType = "audio/mp4a-latm", codecs = "mp4a.40.2", channelCount = 2, sampleRateHz = 48000, language = "en")
  private val eac3 = ExoAudioTrackInfo(mimeType = "audio/eac3", channelCount = 6, sampleRateHz = 48000, language = "en")

  @Test
  fun `healthy audio is not a finding`() {
    val snapshot = ExoAudioSnapshot(container = "matroska", tracks = listOf(aac.copy(selected = true)), audioDecoder = "c2.android.aac.decoder", audioBuffersRendered = 120, videoFramesRendered = 90)
    assertNull(diagnoseExoAudio(snapshot))
    assertTrue(exoAudioLogLine(null, snapshot).startsWith("EXO_AUDIO_OK"))
  }

  @Test
  fun `a file with no audio track is not discovered and a manifest without one is the source`() {
    val file = diagnoseExoAudio(ExoAudioSnapshot(container = "matroska"))!!
    assertEquals(ExoFallbackReason.AudioNoTrack, file.reason)
    assertEquals(ExoAudioCause.NotDiscovered, file.cause)
    val hls = ExoAudioSnapshot(container = "hls")
    val manifest = diagnoseExoAudio(hls)!!
    assertEquals(ExoAudioCause.Source, manifest.cause)
    // An HLS manifest that leaves the audio out of CODECS is read again from its segments, once.
    assertEquals(ExoAudioRecovery.ProbeHlsSegments, exoAudioRecoveryFor(manifest, hls, emptySet()))
    assertNull(exoAudioRecoveryFor(manifest, hls, setOf(ExoAudioRecovery.ProbeHlsSegments)))
    assertNull(exoAudioRecoveryFor(file, ExoAudioSnapshot(container = "matroska"), emptySet()))
  }

  @Test
  fun `bundled decoders that did not load are the app's fault not the stream's`() {
    val track = eac3.copy(support = ExoTrackSupport.UnsupportedSubtype)
    val broken = diagnoseExoAudio(ExoAudioSnapshot(tracks = listOf(track), softwareDecodersLoaded = false))!!
    assertEquals(ExoFallbackReason.AudioUnsupportedCodec, broken.reason)
    assertEquals(ExoAudioCause.Configuration, broken.cause)
    // With them loaded, a codec nothing here decodes is simply unsupported, and only another engine helps.
    val atmos = ExoAudioTrackInfo(mimeType = "audio/ac4", support = ExoTrackSupport.UnsupportedSubtype)
    val snapshot = ExoAudioSnapshot(tracks = listOf(atmos))
    val unsupported = diagnoseExoAudio(snapshot)!!
    assertEquals(ExoAudioCause.Unsupported, unsupported.cause)
    assertNull(exoAudioRecoveryFor(unsupported, snapshot, emptySet()))
    // A codec the bundled build does not cover is not blamed on it either.
    assertEquals(ExoAudioCause.Unsupported, diagnoseExoAudio(ExoAudioSnapshot(tracks = listOf(atmos), softwareDecodersLoaded = false))!!.cause)
  }

  @Test
  fun `a playable track left unselected is reselected before anything else`() {
    val snapshot = ExoAudioSnapshot(tracks = listOf(eac3.copy(support = ExoTrackSupport.UnsupportedSubtype), aac))
    val finding = diagnoseExoAudio(snapshot)!!
    assertEquals(ExoFallbackReason.AudioTrackSelectionFailed, finding.reason)
    assertEquals(ExoAudioCause.NotSelected, finding.cause)
    assertEquals(ExoAudioRecovery.ReselectAudio, exoAudioRecoveryFor(finding, snapshot, emptySet()))
    assertEquals(ExoAudioCause.Configuration, diagnoseExoAudio(snapshot.copy(audioDisabled = true))!!.cause)
  }

  @Test
  fun `a decoder that will not start tries the other track then a retry then gives up`() {
    val snapshot = ExoAudioSnapshot(
      tracks = listOf(eac3.copy(selected = true), aac),
      audioDecoder = "c2.qti.eac3.decoder",
      audioDecoderError = "Decoder init failed",
    )
    val finding = diagnoseExoAudio(snapshot)!!
    assertEquals(ExoFallbackReason.AudioDecoderInitFailed, finding.reason)
    // The device maker's decoder, so the device's fault.
    assertEquals(ExoAudioCause.Device, finding.cause)
    assertEquals(ExoAudioCause.DecoderInit, diagnoseExoAudio(snapshot.copy(audioDecoder = "c2.android.aac.decoder"))!!.cause)
    assertEquals(ExoAudioRecovery.SwitchAudioTrack, exoAudioRecoveryFor(finding, snapshot, emptySet()))
    assertEquals(ExoAudioRecovery.RetryRenderers, exoAudioRecoveryFor(finding, snapshot, setOf(ExoAudioRecovery.SwitchAudioTrack)))
    assertNull(exoAudioRecoveryFor(finding, snapshot, setOf(ExoAudioRecovery.SwitchAudioTrack, ExoAudioRecovery.RetryRenderers)))
    // With nothing else to play, the retry is all there is.
    val only = snapshot.copy(tracks = listOf(eac3.copy(selected = true)))
    assertEquals(ExoAudioRecovery.RetryRenderers, exoAudioRecoveryFor(diagnoseExoAudio(only)!!, only, emptySet()))
  }

  @Test
  fun `output failure and silence are told apart from decoding`() {
    val playing = ExoAudioSnapshot(tracks = listOf(aac.copy(selected = true)), audioDecoder = "c2.android.aac.decoder")
    val output = diagnoseExoAudio(playing.copy(audioOutputError = "AudioTrack init failed 0"))!!
    assertEquals(ExoFallbackReason.AudioOutputFailed, output.reason)
    assertEquals(ExoAudioCause.Output, output.cause)
    val silent = diagnoseExoAudio(playing.copy(videoFramesRendered = EXO_AUDIO_STALL_VIDEO_FRAMES, audioBuffersRendered = 0))!!
    assertEquals(ExoFallbackReason.AudioStalled, silent.reason)
    // Not yet: too few frames to tell a slow start from silence.
    assertNull(diagnoseExoAudio(playing.copy(videoFramesRendered = EXO_AUDIO_STALL_VIDEO_FRAMES - 1, audioBuffersRendered = 0)))
    // Tunneled audio is rendered by the hardware and never counted here.
    assertNull(diagnoseExoAudio(playing.copy(videoFramesRendered = 500, audioBuffersRendered = 0, tunneling = true)))
  }

  @Test
  fun `the log line leads with the code and names every track`() {
    val snapshot = ExoAudioSnapshot(
      container = "matroska", videoMimeType = "video/hevc", videoCodecs = "hvc1.2.4.L153", videoDecoder = "c2.qti.hevc.decoder",
      tracks = listOf(eac3.copy(support = ExoTrackSupport.UnsupportedSubtype), aac.copy(support = ExoTrackSupport.UnsupportedSubtype)),
      softwareDecodersLoaded = false,
    )
    val line = exoAudioLogLine(diagnoseExoAudio(snapshot), snapshot)
    assertTrue(line.startsWith("EXO_AUDIO_UNSUPPORTED_CODEC cause=G"))
    assertTrue(line.contains("eac3/6ch/48000Hz/en/unsupported-codec"))
    assertTrue(line.contains("softwareDecoders=NOT-LOADED"))
    assertTrue(line.contains("videoDecoder=c2.qti.hevc.decoder"))
  }

  @Test
  fun `fatal errors are sorted by area and by which renderer raised them`() {
    assertEquals(ExoFallbackReason.AudioOutputFailed, exoErrorReason(5001, fromAudioRenderer = true))
    assertEquals(ExoFallbackReason.AudioOutputFailed, exoErrorReason(5002, fromAudioRenderer = false))
    assertEquals(ExoFallbackReason.AudioDecoderInitFailed, exoErrorReason(4001, fromAudioRenderer = true))
    assertEquals(ExoFallbackReason.AudioDecoderInitFailed, exoErrorReason(4003, fromAudioRenderer = true))
    assertEquals(ExoFallbackReason.AudioUnsupportedCodec, exoErrorReason(4005, fromAudioRenderer = true))
    // The same decoder codes from the video renderer are not an audio fault.
    assertEquals(ExoFallbackReason.PlaybackFatalError, exoErrorReason(4001, fromAudioRenderer = false))
    assertEquals(ExoFallbackReason.PlaybackFatalError, exoErrorReason(2001, fromAudioRenderer = false))
    assertEquals(ExoFallbackReason.PlaybackFatalError, exoErrorReason(1000, fromAudioRenderer = true))
  }
}
