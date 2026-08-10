package net.streamdek.mobile.nativeapp

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.util.Base64
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
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import net.streamdek.mobile.mpv.MpvTrackInfo
import org.json.JSONArray
import org.json.JSONObject

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
  // A source change on an already-playing instance (live channel switch) prepares the new
  // player here, in the background, while the currently-visible `exoPlayer` keeps playing
  // undisturbed. Only once the candidate reports STATE_READY (or fails) do we swap it in -
  // this is what avoids the black "shutter" flash `player = null` would otherwise cause.
  private var pendingPlayer: ExoPlayer? = null
  private var pendingListener: Player.Listener? = null
  private var source: String? = null
  private var activeSource: String? = null
  private var retiringPlayer: ExoPlayer? = null
  private var retiringSource: String? = null
  private var awaitingFirstFrameAfterPromotion = false
  private var requestHeaders: Map<String, String> = emptyMap()
  private var drmLicenseType: String? = null
  private var drmClearKeys: Map<String, String> = emptyMap()
  private var pendingPaused = false
  private var pendingSpeed = 1.0
  private var pendingVolume = 1f
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
    // Retain the last rendered frame while PlayerView moves from the old, visible player to
    // an already-prepared replacement. Without this, PlayerView briefly exposes its black
    // shutter between detaching the old video output and receiving the new first frame.
    setKeepContentOnPlayerReset(true)
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
    releasePendingPlayer()
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

  /** Only "clearkey" (hex key-id -> hex key, as published by IPTV playlists via
   * #KODIPROP:inputstream.adaptive.license_* lines) is supported. Anything else is ignored -
   * the stream will fail to decrypt exactly as it did before this existed. */
  fun setDrmClearKeys(licenseType: String?, keys: Map<String, String>) {
    drmLicenseType = licenseType
    drmClearKeys = keys
  }

  fun setSource(url: String?) {
    val next = url?.trim().orEmpty()
    if (next.isBlank() || next == source) return
    val hadActivePlayer = exoPlayer != null
    source = next
    if (!isAttachedToWindow) return
    if (hadActivePlayer) prepareSourceInBackground(next) else prepareSource(next)
  }

  fun reloadSource() {
    val current = source ?: return
    // If the requested source has not replaced the visible source yet, this is a retry of a
    // failed/slow live-channel candidate. Keep the working channel visible and retry in the
    // background. A normal reload of the active source can still replace immediately.
    if (exoPlayer != null && activeSource != current) {
      prepareSourceInBackground(current)
    } else {
      prepareSource(current, exoPlayer?.currentPosition ?: 0L)
    }
  }

  fun setPaused(paused: Boolean) {
    pendingPaused = paused
    keepScreenOn = !paused
    exoPlayer?.playWhenReady = !paused
  }

  fun setVolume(volume: Float) {
    pendingVolume = volume.coerceIn(0f, 1f)
    exoPlayer?.volume = pendingVolume
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

  /** Builds a local (offline, no license server) ClearKey session from key-id/key pairs
   * published in plaintext by the playlist itself - the format inputstream.adaptive-based IPTV
   * M3U/M3U8 playlists use via #KODIPROP:inputstream.adaptive.license_key lines. ExoPlayer's
   * ClearKey implementation expects a JSON Web Key Set with base64url (no padding) values, so the
   * playlist's hex key-id/key pairs are re-encoded here. */
  private fun clearKeyDrmSessionManager(keys: Map<String, String>): DefaultDrmSessionManager {
    fun hexToBase64Url(hex: String): String {
      val clean = hex.trim().removePrefix("0x")
      val bytes = ByteArray(clean.length / 2) { i -> ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte() }
      return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
    val keyArray = JSONArray()
    keys.forEach { (keyId, key) ->
      keyArray.put(JSONObject().put("kty", "oct").put("kid", hexToBase64Url(keyId)).put("k", hexToBase64Url(key)))
    }
    val jwkSet = JSONObject().put("keys", keyArray).put("type", "temporary").toString()
    val drmCallback = LocalMediaDrmCallback(jwkSet.toByteArray(Charsets.UTF_8))
    return DefaultDrmSessionManager.Builder()
      .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
      .build(drmCallback)
  }

  private fun buildPlayer(url: String, startPositionMs: Long): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory()
      .setUserAgent(DEFAULT_USER_AGENT)
      .setAllowCrossProtocolRedirects(true)
      .setDefaultRequestProperties(requestHeaders)
    val upstreamFactory = DefaultDataSource.Factory(context, httpFactory)
    // Transparently serves already-downloaded content from disk (see StreamDekDownloads) when
    // the URL matches - falls through to the network otherwise, same as any cache miss.
    val dataSourceFactory = StreamDekDownloads.wrapWithDownloadCache(upstreamFactory)
    val renderers = DefaultRenderersFactory(context)
      .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
      .setEnableDecoderFallback(true)
    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
    if (drmLicenseType.equals("clearkey", ignoreCase = true) && drmClearKeys.isNotEmpty()) {
      runCatching { clearKeyDrmSessionManager(drmClearKeys) }
        .onSuccess { manager -> mediaSourceFactory.setDrmSessionManagerProvider { manager } }
        .onFailure { Log.w(TAG, "Unable to set up ClearKey DRM for $url, playback will likely fail to decrypt", it) }
    }
    val active = ExoPlayer.Builder(context)
      .setRenderersFactory(renderers)
      .setMediaSourceFactory(mediaSourceFactory)
      .build()
    // Media3 picks the media source implementation for this URL's content type reflectively,
    // inside setMediaItem, on the calling thread - so a type whose module isn't on the classpath
    // throws right here rather than arriving as a PlaybackException. Releasing the half-built
    // player lets that failure travel out to the normal error/failover path instead of leaking a
    // decoder on its way to crashing the app.
    try {
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
      active.volume = pendingVolume
      active.playWhenReady = !pendingPaused
      active.prepare()
    } catch (error: Throwable) {
      active.release()
      throw error
    }
    return active
  }

  private fun prepareSource(url: String, startPositionMs: Long = 0L) {
    releasePendingPlayer()
    releasePlayer()
    val active = try {
      buildPlayer(url, startPositionMs)
    } catch (error: Throwable) {
      Log.e(TAG, "Media3 could not open ${url.substringBefore('?')}", error)
      onErrorCallback?.invoke("This source could not be played.")
      return
    }
    exoPlayer = active
    activeSource = url
    player = active
    active.addListener(listener)
    Log.i(TAG, "Preparing CNCVerse VOD with Media3: ${url.substringBefore('?')}")
  }

  /** Prepares [url] on a second, not-yet-visible player while the current one keeps playing.
   * Promotes it (see [promotePendingPlayer]) once it's actually ready to show, so a live
   * channel switch never shows PlayerView's black shutter from `player = null`. */
  private fun prepareSourceInBackground(url: String) {
    releasePendingPlayer()
    val candidate = try {
      buildPlayer(url, 0L)
    } catch (error: Throwable) {
      Log.w(TAG, "Media3 could not open the next channel; keeping the current source visible", error)
      onErrorCallback?.invoke("This source could not be played.")
      return
    }
    // The outgoing channel keeps supplying audio until this candidate has rendered its first
    // frame. Muting the candidate prevents overlapping audio during that short handoff.
    candidate.volume = 0f
    pendingPlayer = candidate
    val swapListener = object : Player.Listener {
      override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_READY && pendingPlayer === candidate) promotePendingPlayer(candidate, url)
      }
      override fun onPlayerError(error: PlaybackException) {
        if (pendingPlayer !== candidate) return
        Log.w(TAG, "Background source prepare failed; keeping the current source visible", error)
        pendingPlayer = null
        pendingListener = null
        candidate.release()
        // Report through the normal retry/failover path, but never tear down the working
        // player just to surface this candidate's failure.
        onErrorCallback?.invoke(error.localizedMessage ?: "This source could not be played.")
      }
    }
    candidate.addListener(swapListener)
    pendingListener = swapListener
    Log.i(TAG, "Preparing next live source in background: ${url.substringBefore('?')}")
  }

  private fun promotePendingPlayer(candidate: ExoPlayer, url: String) {
    pendingListener?.let(candidate::removeListener)
    pendingListener = null
    pendingPlayer = null
    releaseRetiringPlayer()
    // Attach the already-buffered player without ever assigning `player = null`. PlayerView
    // retains the outgoing frame, while the outgoing player keeps its audio alive, until the
    // replacement confirms its first rendered frame in listener.onRenderedFirstFrame().
    val previous = exoPlayer
    previous?.removeListener(listener)
    retiringPlayer = previous
    retiringSource = activeSource
    exoPlayer = candidate
    activeSource = url
    awaitingFirstFrameAfterPromotion = true
    candidate.addListener(listener)
    player = candidate
    dispatchTracks(candidate.currentTracks)
  }

  private fun releasePendingPlayer() {
    val pending = pendingPlayer ?: return
    pendingListener?.let(pending::removeListener)
    pendingListener = null
    pendingPlayer = null
    pending.release()
  }

  private val listener = object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
      onStallChangedCallback?.invoke(state == Player.STATE_BUFFERING)
      when (state) {
        Player.STATE_READY -> {
          // A background-prepared replacement was already READY before it was attached to
          // PlayerView. Its switch is complete only after onRenderedFirstFrame(), not here.
          if (!awaitingFirstFrameAfterPromotion) dispatchLoaded(exoPlayer ?: return)
        }
        Player.STATE_ENDED -> onEndCallback?.invoke()
      }
    }

    override fun onRenderedFirstFrame() {
      if (!awaitingFirstFrameAfterPromotion) return
      val active = exoPlayer ?: return
      awaitingFirstFrameAfterPromotion = false
      active.volume = pendingVolume
      releaseRetiringPlayer()
      Log.i(TAG, "Replacement rendered first frame")
      dispatchLoaded(active)
    }

    override fun onPlayerError(error: PlaybackException) {
      Log.e(TAG, "Media3 playback failed", error)
      if (awaitingFirstFrameAfterPromotion) restoreRetiringPlayer()
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

  private fun dispatchLoaded(active: ExoPlayer) {
    val duration = active.duration.takeIf { it > 0 && it != C.TIME_UNSET }?.div(1000.0) ?: 0.0
    val videoSize = active.videoSize
    Log.i(TAG, "Ready duration=${duration}s video=${videoSize.width}x${videoSize.height}")
    onLoadCallback?.invoke(duration, videoSize.width, videoSize.height)
  }

  private fun restoreRetiringPlayer() {
    val failed = exoPlayer
    val previous = retiringPlayer
    val previousSource = retiringSource
    awaitingFirstFrameAfterPromotion = false
    failed?.removeListener(listener)
    if (previous != null) {
      exoPlayer = previous
      activeSource = previousSource
      retiringPlayer = null
      retiringSource = null
      previous.addListener(listener)
      player = previous
    } else {
      exoPlayer = null
      activeSource = null
      player = null
    }
    failed?.release()
  }

  private fun releaseRetiringPlayer() {
    retiringPlayer?.release()
    retiringPlayer = null
    retiringSource = null
  }

  private fun releasePlayer() {
    player = null
    exoPlayer?.removeListener(listener)
    exoPlayer?.release()
    exoPlayer = null
    activeSource = null
    awaitingFirstFrameAfterPromotion = false
    releaseRetiringPlayer()
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
