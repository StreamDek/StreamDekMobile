package net.streamdek.mobile.nativeapp

import net.streamdek.mobile.mpv.MPVView

/**
 * What the player screen asks of whichever engine is playing.
 *
 * The three engines - Media3, mpv and libVLC - are three unrelated views with nearly the same
 * methods. The screen used to choose between two of them at every call with an `if`; a third
 * engine would have made each of those a three-way choice repeated in twenty places. This is the
 * one place that choice is made now: the screen holds a single [PlaybackEngineControls] for the
 * active engine and calls it, and each engine's view is wrapped below without being changed.
 *
 * It covers what the screen drives after a source is up. Creating a view, handing it a source and
 * wiring its callbacks stays with the screen's `PlayerSurface`, where each engine's differences in
 * set-up (DRM keys, caption probing, decoder mode) are visible rather than hidden behind defaults.
 */
internal interface PlaybackEngineControls {
  fun addSubtitleFile(path: String, language: String?)
  fun reloadSource()
  fun setPaused(paused: Boolean)
  fun seekTo(seconds: Double)
  fun setAudioTrack(id: Int)
  fun setSubtitleTrack(id: Int)
  fun disableSubtitleTrack()
  fun setSubtitleFontSize(size: Int)
  fun setSubtitlePosition(position: Int)
  fun setSubtitleBackgroundColor(color: String)
  fun setSubtitleOutline(enabled: Boolean, color: String)
  fun setSubtitleBold(bold: Boolean)
  fun setSubtitleDelay(seconds: Double)
  fun setAudioDelay(seconds: Double)
  /** False when the engine cannot move the sound for what is playing (Media3 while tunneled). */
  fun audioDelaySupported(): Boolean
  fun setSpeed(speed: Double)
  fun playbackStats(): PlaybackStats
  fun bufferedRanges(): List<BufferedRange>
}

internal fun ExoPlaybackView.asEngineControls(): PlaybackEngineControls = object : PlaybackEngineControls {
  private val view = this@asEngineControls
  override fun addSubtitleFile(path: String, language: String?) { view.addSubtitleFile(path, language) }
  override fun reloadSource() { view.reloadSource() }
  override fun setPaused(paused: Boolean) { view.setPaused(paused) }
  override fun seekTo(seconds: Double) { view.seekTo(seconds) }
  override fun setAudioTrack(id: Int) { view.setAudioTrack(id) }
  override fun setSubtitleTrack(id: Int) { view.setSubtitleTrack(id) }
  override fun disableSubtitleTrack() { view.disableSubtitleTrack() }
  override fun setSubtitleFontSize(size: Int) { view.setSubtitleFontSize(size) }
  override fun setSubtitlePosition(position: Int) { view.setSubtitlePosition(position) }
  override fun setSubtitleBackgroundColor(color: String) { view.setSubtitleBackgroundColor(color) }
  override fun setSubtitleOutline(enabled: Boolean, color: String) { view.setSubtitleOutline(enabled, color) }
  override fun setSubtitleBold(bold: Boolean) { view.setSubtitleBold(bold) }
  override fun setSubtitleDelay(seconds: Double) { view.setSubtitleDelay(seconds) }
  override fun setAudioDelay(seconds: Double) { view.setAudioDelay(seconds) }
  override fun audioDelaySupported(): Boolean = view.audioDelaySupported()
  override fun setSpeed(speed: Double) { view.setSpeed(speed) }
  override fun playbackStats(): PlaybackStats = view.playbackStats()
  override fun bufferedRanges(): List<BufferedRange> = view.bufferedRanges()
}

internal fun MPVView.asEngineControls(): PlaybackEngineControls = object : PlaybackEngineControls {
  private val view = this@asEngineControls
  override fun addSubtitleFile(path: String, language: String?) { view.addSubtitleFile(path, language) }
  override fun reloadSource() { view.reloadSource() }
  override fun setPaused(paused: Boolean) { view.setPaused(paused) }
  override fun seekTo(seconds: Double) { view.seekTo(seconds) }
  override fun setAudioTrack(id: Int) { view.setAudioTrack(id) }
  override fun setSubtitleTrack(id: Int) { view.setSubtitleTrack(id) }
  override fun disableSubtitleTrack() { view.disableSubtitleTrack() }
  override fun setSubtitleFontSize(size: Int) { view.setSubtitleFontSize(size) }
  override fun setSubtitlePosition(position: Int) { view.setSubtitlePosition(position) }
  override fun setSubtitleBackgroundColor(color: String) { view.setSubtitleBackgroundColor(color) }
  override fun setSubtitleOutline(enabled: Boolean, color: String) { view.setSubtitleOutline(enabled, color) }
  override fun setSubtitleBold(bold: Boolean) { view.setSubtitleBold(bold) }
  override fun setSubtitleDelay(seconds: Double) { view.setSubtitleDelay(seconds) }
  override fun setAudioDelay(seconds: Double) { view.setAudioDelay(seconds) }
  // mpv moves its audio in every output mode.
  override fun audioDelaySupported(): Boolean = true
  override fun setSpeed(speed: Double) { view.setSpeed(speed) }
  override fun playbackStats(): PlaybackStats = view.playbackStats()
  override fun bufferedRanges(): List<BufferedRange> = view.bufferedRanges()
}

internal fun VlcPlaybackView.asEngineControls(): PlaybackEngineControls = object : PlaybackEngineControls {
  private val view = this@asEngineControls
  override fun addSubtitleFile(path: String, language: String?) { view.addSubtitleFile(path, language) }
  override fun reloadSource() { view.reloadSource() }
  override fun setPaused(paused: Boolean) { view.setPaused(paused) }
  override fun seekTo(seconds: Double) { view.seekTo(seconds) }
  override fun setAudioTrack(id: Int) { view.setAudioTrack(id) }
  override fun setSubtitleTrack(id: Int) { view.setSubtitleTrack(id) }
  override fun disableSubtitleTrack() { view.disableSubtitleTrack() }
  override fun setSubtitleFontSize(size: Int) { view.setSubtitleFontSize(size) }
  override fun setSubtitlePosition(position: Int) { view.setSubtitlePosition(position) }
  override fun setSubtitleBackgroundColor(color: String) { view.setSubtitleBackgroundColor(color) }
  override fun setSubtitleOutline(enabled: Boolean, color: String) { view.setSubtitleOutline(enabled, color) }
  override fun setSubtitleBold(bold: Boolean) { view.setSubtitleBold(bold) }
  override fun setSubtitleDelay(seconds: Double) { view.setSubtitleDelay(seconds) }
  override fun setAudioDelay(seconds: Double) { view.setAudioDelay(seconds) }
  override fun audioDelaySupported(): Boolean = view.audioDelaySupported()
  override fun setSpeed(speed: Double) { view.setSpeed(speed) }
  override fun playbackStats(): PlaybackStats = view.playbackStats()
  override fun bufferedRanges(): List<BufferedRange> = view.bufferedRanges()
}
