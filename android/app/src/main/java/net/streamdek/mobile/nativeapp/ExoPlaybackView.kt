package net.streamdek.mobile.nativeapp

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import net.streamdek.mobile.mpv.MpvTrackInfo

/** Media3 playback path used for CNCVerse Bridge VODs. */
@OptIn(UnstableApi::class)
class ExoPlaybackView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
) : PlayerView(context, attrs, defStyleAttr) {
  companion object {
    private const val TAG = "StreamDekExoPlayer"
    private const val DEFAULT_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
  }

  var onLoadCallback: ((duration: Double, width: Int, height: Int) -> Unit)? = null
  var onProgressCallback: ((position: Double, duration: Double) -> Unit)? = null
  var onEndCallback: (() -> Unit)? = null
  var onErrorCallback: ((message: String) -> Unit)? = null
  var onTracksChangedCallback: ((List<MpvTrackInfo>, List<MpvTrackInfo>, Int?, Int?) -> Unit)? = null
  var onStallChangedCallback: ((Boolean) -> Unit)? = null

  private var exoPlayer: ExoPlayer? = null
  private var source: String? = null
  private var requestHeaders: Map<String, String> = emptyMap()
  private var pendingPaused = false
  private var pendingSpeed = 1.0
  private var preferredAudioLanguage = "en"
  private var subtitlePositionPercent = 92
  private var pendingSubtitle: MediaItem.SubtitleConfiguration? = null
  private val audioSelections = mutableMapOf<Int, Pair<Tracks.Group, Int>>()
  private val subtitleSelections = mutableMapOf<Int, Pair<Tracks.Group, Int>>()
  private val progressTicker = object : Runnable {
    override fun run() {
      exoPlayer?.let { active ->
        val durationMs = active.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        onProgressCallback?.invoke(active.currentPosition / 1000.0, durationMs / 1000.0)
      }
      postDelayed(this, if (exoPlayer?.isPlaying == true) 500L else 1_500L)
    }
  }

  init {
    useController = false
    setShutterBackgroundColor(Color.BLACK)
    keepScreenOn = true
    subtitleView?.setApplyEmbeddedStyles(false)
    subtitleView?.setApplyEmbeddedFontSizes(false)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    post(progressTicker)
    source?.let(::prepareSource)
  }

  override fun onDetachedFromWindow() {
    removeCallbacks(progressTicker)
    releasePlayer()
    clearCallbacks()
    super.onDetachedFromWindow()
  }

  fun setHeaders(headers: Map<String, String>?) {
    requestHeaders = headers.orEmpty().mapNotNull { (key, value) ->
      key.trim().takeIf { it.isNotBlank() && !it.equals("Range", true) }
        ?.let { cleanKey -> value.trim().takeIf(String::isNotBlank)?.let { cleanKey to it } }
    }.toMap()
  }

  fun setSource(url: String?) {
    val next = url?.trim().orEmpty()
    if (next.isBlank() || next == source) return
    source = next
    if (isAttachedToWindow) prepareSource(next)
  }

  fun reloadSource() {
    val current = source ?: return
    prepareSource(current, exoPlayer?.currentPosition ?: 0L)
  }

  fun setPaused(paused: Boolean) {
    pendingPaused = paused
    keepScreenOn = !paused
    exoPlayer?.playWhenReady = !paused
  }

  fun seekTo(positionSeconds: Double) {
    exoPlayer?.seekTo((positionSeconds * 1000.0).toLong().coerceAtLeast(0L))
  }

  fun setSpeed(speed: Double) {
    pendingSpeed = speed
    exoPlayer?.setPlaybackSpeed(speed.toFloat())
  }

  fun setPreferredAudioLanguage(language: String?) {
    preferredAudioLanguage = normalizePreferredAudioLanguage(language)
    val tags = preferredAudioLanguageTags(preferredAudioLanguage)
    exoPlayer?.let { active ->
      if (tags.isNotEmpty()) {
        active.trackSelectionParameters = active.trackSelectionParameters.buildUpon()
          .setPreferredAudioLanguages(*tags.toTypedArray())
          .build()
      }
    }
  }

  fun setResizeMode(mode: String?) {
    resizeMode = when (mode) {
      "cover" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
      "stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
      else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
  }

  fun setDecoderMode(mode: String?) = Unit
  fun setRenderSurface(mode: String?) = Unit

  fun setAudioTrack(trackId: Int) = applyTrackSelection(audioSelections[trackId])

  fun setSubtitleTrack(trackId: Int) {
    val active = exoPlayer ?: return
    active.trackSelectionParameters = active.trackSelectionParameters.buildUpon()
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
    applyTrackSelection(subtitleSelections[trackId])
  }

  fun disableSubtitleTrack() {
    val active = exoPlayer ?: return
    active.trackSelectionParameters = active.trackSelectionParameters.buildUpon()
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
      .build()
  }

  fun addSubtitleFile(path: String) {
    val current = source ?: return
    pendingSubtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(path))
      .setMimeType(subtitleMimeType(path))
      .setLanguage("en")
      .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
      .build()
    prepareSource(current, exoPlayer?.currentPosition ?: 0L)
  }

  fun setSubtitleDelay(seconds: Double) = Unit

  fun setSubtitleFontSize(size: Int) {
    subtitleView?.setApplyEmbeddedStyles(false)
    subtitleView?.setApplyEmbeddedFontSizes(false)
    subtitleView?.setFractionalTextSize((size.coerceIn(28, 84) / 55f) * 0.0533f)
  }

  fun setSubtitleColor(color: String) {
    val parsed = runCatching { Color.parseColor(color.take(7)) }.getOrDefault(Color.WHITE)
    subtitleView?.setStyle(CaptionStyleCompat(parsed, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null))
  }

  fun setSubtitlePosition(position: Int) {
    subtitlePositionPercent = position.coerceIn(0, 100)
    subtitleView?.setBottomPaddingFraction(((100 - subtitlePositionPercent) / 100f).coerceIn(0.02f, 0.50f))
  }

  private fun prepareSource(url: String, startPositionMs: Long = 0L) {
    releasePlayer()
    val httpFactory = DefaultHttpDataSource.Factory()
      .setUserAgent(DEFAULT_USER_AGENT)
      .setAllowCrossProtocolRedirects(true)
      .setDefaultRequestProperties(requestHeaders)
    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
    val renderers = DefaultRenderersFactory(context)
      .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
      .setEnableDecoderFallback(true)
    val active = ExoPlayer.Builder(context)
      .setRenderersFactory(renderers)
      .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
      .build()
    exoPlayer = active
    player = active
    active.addListener(listener)
    preferredAudioLanguageTags(preferredAudioLanguage).takeIf(List<String>::isNotEmpty)?.let { tags ->
      active.trackSelectionParameters = active.trackSelectionParameters.buildUpon()
        .setPreferredAudioLanguages(*tags.toTypedArray())
        .build()
    }
    val item = MediaItem.Builder()
      .setUri(url)
      .apply { inferMimeType(url)?.let(::setMimeType) }
      .apply { pendingSubtitle?.let { setSubtitleConfigurations(listOf(it)) } }
      .build()
    active.setMediaItem(item, startPositionMs.coerceAtLeast(0L))
    active.setPlaybackSpeed(pendingSpeed.toFloat())
    active.playWhenReady = !pendingPaused
    active.prepare()
    Log.i(TAG, "Preparing CNCVerse VOD with Media3: ${url.substringBefore('?')}")
  }

  private val listener = object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
      onStallChangedCallback?.invoke(state == Player.STATE_BUFFERING)
      when (state) {
        Player.STATE_READY -> {
          val active = exoPlayer ?: return
          val duration = active.duration.takeIf { it > 0 && it != C.TIME_UNSET }?.div(1000.0) ?: 0.0
          val videoSize = active.videoSize
          Log.i(TAG, "Ready duration=${duration}s video=${videoSize.width}x${videoSize.height}")
          onLoadCallback?.invoke(duration, videoSize.width, videoSize.height)
        }
        Player.STATE_ENDED -> onEndCallback?.invoke()
      }
    }

    override fun onPlayerError(error: PlaybackException) {
      Log.e(TAG, "Media3 playback failed", error)
      onErrorCallback?.invoke(error.localizedMessage ?: "This source could not be played.")
    }

    override fun onTracksChanged(tracks: Tracks) = dispatchTracks(tracks)

    override fun onCues(cueGroup: CueGroup) {
      val userPositionedCues = cueGroup.cues.map { cue ->
        cue.buildUpon()
          .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
          .setPosition(Cue.DIMEN_UNSET)
          .build()
      }
      subtitleView?.setCues(userPositionedCues)
      subtitleView?.setBottomPaddingFraction(((100 - subtitlePositionPercent) / 100f).coerceIn(0.02f, 0.50f))
    }
  }

  private fun dispatchTracks(tracks: Tracks) {
    audioSelections.clear()
    subtitleSelections.clear()
    val audio = mutableListOf<MpvTrackInfo>()
    val subtitles = mutableListOf<MpvTrackInfo>()
    var nextId = 1
    tracks.groups.forEach { group ->
      for (index in 0 until group.length) {
        if (!group.isTrackSupported(index)) continue
        val format = group.getTrackFormat(index)
        val id = nextId++
        val info = MpvTrackInfo(id, if (group.type == C.TRACK_TYPE_AUDIO) "audio" else "sub", format.label, format.language, format.codecs, group.isTrackSelected(index))
        when (group.type) {
          C.TRACK_TYPE_AUDIO -> { audio += info; audioSelections[id] = group to index }
          C.TRACK_TYPE_TEXT -> { subtitles += info; subtitleSelections[id] = group to index }
        }
      }
    }
    onTracksChangedCallback?.invoke(audio, subtitles, audio.firstOrNull { it.selected }?.id, subtitles.firstOrNull { it.selected }?.id)
  }

  private fun applyTrackSelection(selection: Pair<Tracks.Group, Int>?) {
    val (group, index) = selection ?: return
    val active = exoPlayer ?: return
    active.trackSelectionParameters = active.trackSelectionParameters.buildUpon()
      .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
      .build()
  }

  private fun releasePlayer() {
    player = null
    exoPlayer?.removeListener(listener)
    exoPlayer?.release()
    exoPlayer = null
  }

  private fun clearCallbacks() {
    onLoadCallback = null
    onProgressCallback = null
    onEndCallback = null
    onErrorCallback = null
    onTracksChangedCallback = null
    onStallChangedCallback = null
  }

  private fun inferMimeType(url: String): String? = when (url.substringBefore('?').substringAfterLast('.').lowercase()) {
    "m3u8" -> MimeTypes.APPLICATION_M3U8
    "mpd" -> MimeTypes.APPLICATION_MPD
    "mkv" -> MimeTypes.VIDEO_MATROSKA
    "mp4", "m4v" -> MimeTypes.VIDEO_MP4
    "webm" -> MimeTypes.VIDEO_WEBM
    else -> null
  }

  private fun subtitleMimeType(path: String): String = when (path.substringBefore('?').substringAfterLast('.').lowercase()) {
    "vtt" -> MimeTypes.TEXT_VTT
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    "ttml", "xml" -> MimeTypes.APPLICATION_TTML
    else -> MimeTypes.APPLICATION_SUBRIP
  }
}
