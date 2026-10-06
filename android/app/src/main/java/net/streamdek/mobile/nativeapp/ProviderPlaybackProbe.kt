package net.streamdek.mobile.nativeapp

/** Renderer evidence for one Media3 source attempt; never stores a URL or a title. */
internal class ProviderPlaybackProbe(private val emit: (Boolean, String) -> Unit) {
  private var attempt = ""
  private var video = false
  private var audio = false
  private var played = 0L
  private var previous = -1L
  private var target: Long? = null
  private var afterSeek = 0L
  private var reported = false
  private var failed = false
  fun reset() {
    attempt = "health_${java.util.UUID.randomUUID()}"
    video = false; audio = false; played = 0; previous = -1; target = null
    afterSeek = 0; reported = false; failed = false
  }
  fun frame() { video = true }
  fun audio() { audio = true }
  fun seek(position: Long) {
    // Initial resume is not a playback seek test. Require playback first.
    if (played >= 10000 && video && audio && previous >= 0 && kotlin.math.abs(position - previous) >= 5000) {
      target = position; afterSeek = 0; previous = -1; video = false; audio = false
    }
  }
  fun tick(position: Long, playing: Boolean) {
    if (failed || attempt.isBlank()) return
    val delta = position - previous
    if (playing && previous >= 0 && delta in 1..2500) {
      played += delta
      target?.let { if (position >= it - 2000 && position <= it + 15000) afterSeek += delta }
    }
    previous = if (playing) position else -1
    if (!reported && video && audio && played >= 10000 && afterSeek >= 3000) {
      reported = true
      emit(true, attempt)
    }
  }
  fun fail() {
    if (failed || attempt.isBlank()) return
    failed = true
    emit(false, attempt)
  }
}
