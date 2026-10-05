package net.streamdek.mobile.nativeapp

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import java.io.File
import net.streamdek.mobile.mpv.MpvTrackInfo
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * The libVLC playback engine: a third way to play a source, beside Media3 and mpv.
 *
 * It presents the same surface the other two do - a source, a handful of setters, and callbacks for
 * loaded, progress, tracks, stalls, end and error - so the player screen drives it without knowing
 * which engine it is talking to. Resume, progress sync, Continue Watching, episode navigation and
 * source failover all hang off those callbacks and therefore work unchanged.
 *
 * Built against the libVLC 3.7 Android bindings. What follows was checked against those bindings
 * and the VLC 3.0 and 4.0 sources; `docs/libvlc-engine.md` has the full account.
 *
 *  - **Request headers.** libVLC's HTTP access sends a user agent and a referrer and nothing else,
 *    in VLC 4 as in VLC 3. A source that needs any other header is not given to this engine at all:
 *    see [libVlcBlocker]. The same goes for ClearKey DRM.
 *  - **Subtitle appearance** is read by libVLC's text renderer when it is created, and there is no
 *    supported call that changes it afterwards. So a change made during playback is applied by
 *    reopening the same source at the same position once the viewer has stopped adjusting; see
 *    [applyAppearanceSoon]. Playback pauses for a moment when that happens.
 *  - **Buffered ranges.** libVLC reports only a fill percentage while it is rebuffering, never
 *    what it holds ahead of the playhead, so the timeline draws no buffered stretch here.
 *  - **Track details** (language, codec, channels, profile) come from libVLC's own track records;
 *    the track's display name is only the fallback.
 *  - **Pinch zoom** scales the video's view. libVLC's own scale is a ratio of screen pixels to
 *    video pixels with no notion of "relative to fit", and scaling the view is done by the
 *    compositor: no frame is copied and the decoder is not affected.
 *
 * Hardware decoding through MediaCodec, rendering straight to the surface, is what libVLC is
 * asked for; it falls back to software by itself when a decoder refuses a stream, and the "SW"
 * decoder setting asks for software from the start.
 *
 * Everything that touches the player runs on the main thread. Tearing it down does not: stopping
 * a network stream can block for seconds, so the stop and the two releases are handed to a
 * background thread once the view has let go of the surface.
 */
class VlcPlaybackView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

  private companion object {
    const val TAG = "StreamDekVlcView"
    const val SUBTITLE_SLAVE = 0
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 StreamDek"
    /** How long after playback starts a source is still given to report its length and picture. */
    const val LOAD_GRACE_MS = 2_500L
    /** How long the viewer must leave the subtitle appearance alone before it is applied. */
    const val APPEARANCE_SETTLE_MS = 1_200L
    /** How long after a reopen the tracks that were selected before it are selected again. */
    const val RESELECT_DELAY_MS = 400L
  }

  var onLoadCallback: ((duration: Double, width: Int, height: Int) -> Unit)? = null
  var onProgressCallback: ((position: Double, duration: Double) -> Unit)? = null
  var onEndCallback: (() -> Unit)? = null
  var onErrorCallback: ((message: String) -> Unit)? = null
  var onTracksChangedCallback: ((List<MpvTrackInfo>, List<MpvTrackInfo>, Int?, Int?) -> Unit)? = null
  var onStallChangedCallback: ((Boolean) -> Unit)? = null

  /**
   * The source is Dolby Vision in a form libVLC plays without its Dolby Vision metadata. Returns
   * true when the screen is taking the source to another engine.
   */
  var onDolbyVisionCallback: (() -> Boolean)? = null

  private val main = Handler(Looper.getMainLooper())
  private val videoLayout = VLCVideoLayout(context)
  private var libVlc: LibVLC? = null
  private var player: MediaPlayer? = null
  private var viewsAttached = false
  private var released = false

  private var currentSource: String? = null
  private var headers: Map<String, String> = emptyMap()
  private var paused = false
  private var speed = 1.0
  private var resizeMode = "cover"
  private var zoom = 1f
  private var decoderMode = "HW+"
  private var renderSurface = "Standard"
  private var live = false
  private var audioLanguages: List<String> = emptyList()
  private var subtitleLanguages: List<String> = emptyList()
  private var audioDelaySec = 0.0
  private var subtitleDelaySec = 0.0

  // Subtitle appearance: read when the libVLC instance is created, see the class comment.
  private var subtitleFontSize = 55
  private var subtitlePosition = 92
  private var subtitleColor = "#FFFFFFFF"
  private var subtitleBackgroundColor = "#00000000"
  private var subtitleOutline = true
  private var subtitleOutlineColor = "#FF000000"
  private var subtitleBold = false
  private var styleKeyInUse: String? = null

  private var playingSeen = false
  private var playingSince = 0L
  private var voutSeen = false
  private var loadReported = false
  private var stalled = false
  private var lastLengthMs = 0L
  private var lastTimeMs = 0L

  /** libVLC's own records of the tracks, by track id. Read once per source; see [readTrackRecords]. */
  private var trackRecords: Map<Int, IMedia.Track> = emptyMap()
  private var trackRecordsRead = false
  private var dolbyVisionReported = false

  /** External subtitle files added to the current source, so a reopen can add them again. */
  private val subtitleFiles = ArrayList<String>()

  /** Where playback was when the window went away; reopened from here when it comes back. */
  private var stoppedAtMs: Long? = null
  /** The next load is this view reopening its own source, not something the screen asked for. */
  private var restoring = false
  private var loadReportedBeforeStop = false
  /** The tracks that were selected before a reopen, to select again once the source is back. */
  private var reselect: Reselect? = null

  private class Reselect(val audio: Int?, val subtitle: Int?, val subtitleFiles: List<String>)

  private val applyAppearance = Runnable { applyAppearanceNow() }

  init {
    setBackgroundColor(android.graphics.Color.BLACK)
    addView(videoLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    // Holds the screen awake while something is playing, as the other two engines' views do.
    // Without it the device's display timeout ran on regardless and the screen went to sleep in
    // the middle of a film. Let go of while paused, so a paused player does not hold it for ever.
    keepScreenOn = true
  }

  // ---- source ---------------------------------------------------------------------------------

  fun setLoadWaitsForPlayback(waits: Boolean) { live = waits }

  fun setHeaders(nextHeaders: Map<String, String>?) { headers = nextHeaders.orEmpty() }

  fun setSource(url: String?) {
    val next = url?.takeIf { it.isNotBlank() }
    if (next == currentSource) return
    currentSource = next
    stoppedAtMs = null
    restoring = false
    reselect = null
    subtitleFiles.clear()
    if (next == null) stopPlayback() else load(next)
  }

  fun reloadSource() {
    currentSource?.let { load(it) }
  }

  private fun styleKey(): String = listOf(
    subtitleFontSize, subtitlePosition, subtitleColor, subtitleBackgroundColor, subtitleOutline, subtitleOutlineColor, subtitleBold,
  ).joinToString("|")

  /** `#AARRGGBB` or `#RRGGBB` as the RGB integer and 0..255 opacity libVLC's text renderer takes. */
  private fun rgbAndOpacity(color: String): Pair<Int, Int> {
    val parsed = runCatching { android.graphics.Color.parseColor(color) }.getOrDefault(android.graphics.Color.WHITE)
    return (parsed and 0x00FFFFFF) to ((parsed ushr 24) and 0xFF)
  }

  private fun buildOptions(): ArrayList<String> {
    val (textColor, textOpacity) = rgbAndOpacity(subtitleColor)
    val (backgroundColor, backgroundOpacity) = rgbAndOpacity(subtitleBackgroundColor)
    val (outlineColor, outlineOpacity) = rgbAndOpacity(subtitleOutlineColor)
    // libVLC sizes text as a fraction of the picture height: 16 is its default and a smaller
    // number is larger text. 55 is this app's default size, so 55 maps onto 16.
    val relativeSize = (16.0 * 55.0 / subtitleFontSize.coerceIn(20, 140)).toInt().coerceIn(6, 40)
    // The position is a percentage down the picture; libVLC wants the margin up from the bottom.
    val marginPx = ((100 - subtitlePosition.coerceIn(0, 100)) / 100.0 * resources.displayMetrics.heightPixels).toInt().coerceAtLeast(0)
    return arrayListOf(
      "--no-video-title-show",
      // The counters behind the info panel and the half-a-stream check. They are a few integers
      // libVLC keeps as it goes; nothing reads them unless the panel is open.
      "--stats",
      "--http-reconnect",
      // Keeps speech at its own pitch when the playback speed is changed. The filter that does it
      // is only inserted while the speed is not 1x.
      "--audio-time-stretch",
      "--freetype-rel-fontsize=$relativeSize",
      "--freetype-color=$textColor",
      "--freetype-opacity=${textOpacity.coerceAtLeast(64)}",
      "--freetype-background-color=$backgroundColor",
      "--freetype-background-opacity=$backgroundOpacity",
      "--freetype-outline-color=$outlineColor",
      "--freetype-outline-opacity=${if (subtitleOutline) outlineOpacity.coerceAtLeast(64) else 0}",
      "--freetype-outline-thickness=${if (subtitleOutline) 4 else 0}",
      if (subtitleBold) "--freetype-bold" else "--no-freetype-bold",
      "--sub-margin=$marginPx",
    )
  }

  /** The player, created on first use and rebuilt when the subtitle appearance has changed. */
  private fun ensurePlayer(): MediaPlayer? {
    if (released) return null
    val wanted = styleKey()
    val existing = player
    if (existing != null) {
      if (styleKeyInUse == wanted) return existing
      teardown()
    }
    return runCatching {
      // The appearance options are the only ones a particular libVLC build might not know, and an
      // unknown option stops the instance being created at all. Playing without the viewer's
      // subtitle styling is better than not playing.
      val vlc = runCatching { LibVLC(context.applicationContext, buildOptions()) }.getOrElse { failure ->
        Log.w(TAG, "libVLC refused the subtitle options; starting without them", failure)
        LibVLC(context.applicationContext, arrayListOf("--no-video-title-show", "--stats", "--http-reconnect"))
      }
      val created = MediaPlayer(vlc)
      created.setEventListener { event -> handleEvent(event) }
      libVlc = vlc
      player = created
      styleKeyInUse = wanted
      attachViewsIfPossible()
      created
    }.onFailure { failure ->
      Log.e(TAG, "libVLC could not be started", failure)
      main.post { onErrorCallback?.invoke("libVLC could not be started on this device.") }
    }.getOrNull()
  }

  private fun attachViewsIfPossible() {
    val current = player ?: return
    if (viewsAttached || !isAttachedToWindow) return
    runCatching {
      // A TextureView only where the viewer asked for the compatibility surface: a SurfaceView is
      // what lets the hardware decoder hand frames straight to the display.
      current.attachViews(videoLayout, null, true, renderSurface == "Compatibility")
      viewsAttached = true
      applyScale()
    }.onFailure { Log.w(TAG, "attachViews failed", it) }
  }

  private fun load(url: String, startAtMs: Long? = null) {
    main.removeCallbacks(applyAppearance)
    val current = ensurePlayer() ?: return
    val vlc = libVlc ?: return
    keepScreenOn = !paused
    playingSeen = false
    voutSeen = false
    loadReported = false
    lastLengthMs = 0L
    trackRecords = emptyMap()
    trackRecordsRead = false
    dolbyVisionReported = false
    setStalled(false)
    runCatching {
      if (current.isPlaying) current.stop()
      val uri = if (url.startsWith("/")) Uri.fromFile(File(url)) else Uri.parse(url)
      val media = Media(vlc, uri)
      try {
        // Before the decoder is chosen: the bindings add 1.5 s of caching of their own for
        // hardware decoding unless a figure is already there.
        media.addOption(":network-caching=" + libVlcNetworkCachingMs(url, live))
        media.addOption(":file-caching=$LIBVLC_FILE_CACHING_MS")
        // MediaCodec first, rendering straight to the surface, and software when the hardware
        // decoder refuses the stream. "SW" skips the attempt for sources the device's decoder is
        // known to mishandle.
        media.setHWDecoderEnabled(!decoderMode.equals("SW", ignoreCase = true), false)
        media.addOption(":http-user-agent=" + (headerValue("User-Agent") ?: DEFAULT_USER_AGENT))
        headerValue("Referer")?.let { media.addOption(":http-referrer=$it") }
        if (audioLanguages.isNotEmpty()) media.addOption(":audio-language=" + audioLanguages.joinToString(","))
        if (subtitleLanguages.isNotEmpty()) media.addOption(":sub-language=" + subtitleLanguages.joinToString(","))
        if (startAtMs != null && startAtMs > 0L) media.addOption(":start-time=" + (startAtMs / 1000.0))
        current.setMedia(media)
      } finally {
        media.release()
      }
      current.setRate(speed.toFloat())
      current.play()
      if (paused) current.pause()
    }.onFailure { failure ->
      Log.e(TAG, "libVLC could not open the source", failure)
      main.post { onErrorCallback?.invoke(failure.message ?: "libVLC could not open this source.") }
    }
    // The screen routes sources with other headers away from this engine; this is for the case
    // where the viewer switched to libVLC by hand anyway.
    val unsupported = headersLibVlcCannotSend(headers)
    if (unsupported.isNotEmpty()) Log.w(TAG, "libVLC cannot send these request headers: $unsupported")
  }

  private fun headerValue(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }

  // ---- events ---------------------------------------------------------------------------------

  private fun handleEvent(event: MediaPlayer.Event) {
    if (released) return
    when (event.type) {
      MediaPlayer.Event.Opening -> setStalled(false)
      MediaPlayer.Event.Buffering -> {
        // Reported as a percentage while the cache fills; 100 is "playing again".
        if (playingSeen) setStalled(event.getBuffering() < 100f)
      }
      MediaPlayer.Event.Playing -> {
        if (!playingSeen) {
          playingSeen = true
          playingSince = SystemClock.elapsedRealtime()
          applyDelays()
          main.postDelayed({ maybeReportLoaded(force = true) }, LOAD_GRACE_MS)
          if (reselect != null) main.postDelayed({ reselectTracks() }, RESELECT_DELAY_MS)
        }
        setStalled(false)
        dispatchTracks()
        maybeReportLoaded()
      }
      MediaPlayer.Event.LengthChanged -> {
        lastLengthMs = event.getLengthChanged()
        maybeReportLoaded()
      }
      MediaPlayer.Event.Vout -> {
        if (event.getVoutCount() > 0) {
          voutSeen = true
          applyScale()
          maybeReportLoaded()
        }
      }
      MediaPlayer.Event.TimeChanged -> {
        lastTimeMs = event.getTimeChanged()
        maybeReportLoaded()
        if (loadReported) onProgressCallback?.invoke(lastTimeMs / 1000.0, lengthSeconds())
      }
      MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> dispatchTracks()
      MediaPlayer.Event.EndReached -> {
        keepScreenOn = false
        setStalled(false)
        onEndCallback?.invoke()
      }
      MediaPlayer.Event.EncounteredError -> {
        setStalled(false)
        onErrorCallback?.invoke("libVLC could not play this source.")
      }
    }
  }

  private fun lengthSeconds(): Double {
    val reported = player?.let { active -> runCatching { active.getLength() }.getOrDefault(0L) } ?: 0L
    val length = if (reported > 0L) reported else lastLengthMs
    return length.coerceAtLeast(0L) / 1000.0
  }

  private fun graceElapsed(): Boolean = SystemClock.elapsedRealtime() - playingSince >= LOAD_GRACE_MS

  /**
   * Tells the screen the source is up, once. The screen seeks to the resume position in answer,
   * and needs the length to do it - so an on-demand source waits for libVLC to know its length,
   * and for the picture so the size it reports is real, for a short grace period at most.
   */
  private fun maybeReportLoaded(force: Boolean = false) {
    if (loadReported || !playingSeen || released) return
    val duration = lengthSeconds()
    val waitedOut = force || graceElapsed()
    if (!waitedOut && !voutSeen) return
    if (!waitedOut && !live && duration <= 0.0) return
    loadReported = true
    readTrackRecords()
    if (restoring) {
      // The screen already knows this source is up and has done its resume seek; telling it again
      // would send playback back to that position.
      restoring = false
      dispatchTracks()
      return
    }
    val video = currentVideoRecord()
    onLoadCallback?.invoke(duration, video?.width ?: 0, video?.height ?: 0)
    dispatchTracks()
    reportDolbyVisionIfNeeded(video)
  }

  private fun reportDolbyVisionIfNeeded(video: IMedia.VideoTrack?) {
    if (dolbyVisionReported || video == null || !isDolbyVisionOnlyFourcc(video.fourcc)) return
    dolbyVisionReported = true
    Log.w(TAG, "Dolby Vision sample entry: libVLC plays this without its Dolby Vision metadata")
    onDolbyVisionCallback?.invoke()
  }

  private fun setStalled(next: Boolean) {
    if (stalled == next) return
    stalled = next
    onStallChangedCallback?.invoke(next)
  }

  // ---- transport ------------------------------------------------------------------------------

  fun setPaused(nextPaused: Boolean) {
    paused = nextPaused
    keepScreenOn = !nextPaused
    val current = player ?: return
    runCatching {
      if (nextPaused) {
        if (current.isPlaying) current.pause()
      } else if (!current.isPlaying && currentSource != null && stoppedAtMs == null) {
        current.play()
      }
    }
  }

  fun seekTo(positionSeconds: Double) {
    val target = (positionSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
    lastTimeMs = target
    runCatching { player?.setTime(target, false) }
  }

  fun setSpeed(nextSpeed: Double) {
    speed = nextSpeed.coerceIn(0.25, 4.0)
    runCatching { player?.setRate(speed.toFloat()) }
  }

  fun setVolume(volume: Double) {
    runCatching { player?.setVolume((volume.coerceIn(0.0, 1.0) * 100).toInt()) }
  }

  // ---- picture --------------------------------------------------------------------------------

  fun setResizeMode(mode: String?) {
    if (mode == null || mode == resizeMode) return
    resizeMode = mode
    applyScale()
  }

  /** Pinch zoom, done by the compositor on the video's view; see the class comment. */
  fun setVideoZoom(scale: Float) {
    val next = scale.coerceAtLeast(1f)
    if (next == zoom) return
    zoom = next
    videoLayout.scaleX = zoom
    videoLayout.scaleY = zoom
  }

  private fun applyScale() {
    val current = player ?: return
    runCatching {
      current.setVideoScale(
        when (resizeMode) {
          "cover" -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
          "stretch" -> MediaPlayer.ScaleType.SURFACE_FILL
          else -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        },
      )
    }
  }

  fun setDecoderMode(mode: String?) { if (mode != null) decoderMode = mode }

  fun setRenderSurface(mode: String?) { if (mode != null) renderSurface = mode }

  // ---- tracks ---------------------------------------------------------------------------------

  fun setPreferredAudioLanguage(language: String?) {
    audioLanguages = (listOfNotNull(language?.takeIf { it.isNotBlank() && it != "none" }) + audioLanguages.drop(1)).distinct()
  }

  fun setSecondaryAudioLanguage(language: String?) {
    audioLanguages = (audioLanguages.take(1) + listOfNotNull(language?.takeIf { it.isNotBlank() && it != "none" })).distinct()
  }

  fun setSubtitleLanguages(primary: String?, secondary: String?) {
    subtitleLanguages = listOfNotNull(primary, secondary).filter { it.isNotBlank() && it != "none" }.distinct()
  }

  fun setAudioTrack(trackId: Int) {
    runCatching { player?.setAudioTrack(trackId) }
    dispatchTracks()
  }

  fun setSubtitleTrack(trackId: Int) {
    runCatching { player?.setSpuTrack(trackId) }
    dispatchTracks()
  }

  fun disableSubtitleTrack() {
    runCatching { player?.setSpuTrack(-1) }
    dispatchTracks()
  }

  /** An external subtitle file, added beside the stream and selected. */
  fun addSubtitleFile(path: String, language: String? = null) {
    val current = player ?: return
    val added = runCatching { current.addSlave(SUBTITLE_SLAVE, Uri.fromFile(File(path)), true) }.getOrDefault(false)
    if (added) {
      if (path !in subtitleFiles) subtitleFiles.add(path)
    } else {
      Log.w(TAG, "libVLC did not accept the subtitle file $path (${language ?: "unknown language"})")
    }
  }

  /**
   * Reads libVLC's records of the tracks, once per source.
   *
   * The bindings keep the first list they are asked for and never refresh it, so asking before
   * the tracks are all known would freeze a partial list for the rest of the source. This waits
   * until there is a picture (or the grace period has passed), by which point a container has
   * announced what it holds. A track that turns up later - an external subtitle file - has no
   * record and falls back to its display name.
   */
  private fun readTrackRecords() {
    if (trackRecordsRead || !playingSeen) return
    if (!voutSeen && !graceElapsed()) return
    val media = runCatching { player?.media }.getOrNull() ?: return
    try {
      val found = HashMap<Int, IMedia.Track>()
      for (index in 0 until media.trackCount) {
        val track = media.getTrack(index) ?: continue
        found[track.id] = track
      }
      trackRecords = found
      trackRecordsRead = true
    } catch (failure: RuntimeException) {
      Log.w(TAG, "track records unavailable", failure)
    } finally {
      media.release()
    }
  }

  private fun currentVideoRecord(): IMedia.VideoTrack? {
    val selected = runCatching { player?.videoTrack }.getOrNull()
    val videos = trackRecords.values.filterIsInstance<IMedia.VideoTrack>()
    return videos.firstOrNull { it.id == selected } ?: videos.firstOrNull()
  }

  /** "Track 2 - [English]" and "English" both give English; a name with no language gives null. */
  private fun languageFromName(name: String?): String? =
    name?.let { Regex("\\[([^\\]]+)]").findAll(it).lastOrNull()?.groupValues?.getOrNull(1) }?.trim()?.takeIf { it.isNotEmpty() }

  private fun trackInfo(id: Int, name: String?, type: String, selected: Boolean): MpvTrackInfo {
    val record = trackRecords[id]
    return MpvTrackInfo(
      id = id,
      type = type,
      title = record?.description?.takeIf { it.isNotBlank() } ?: name,
      // libVLC's own language field first; the display name only when the record has none.
      language = record?.language?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) } ?: languageFromName(name),
      codec = libVlcCodecKey(record?.codec),
      selected = selected,
    )
  }

  private fun dispatchTracks() {
    val current = player ?: return
    val callback = onTracksChangedCallback ?: return
    runCatching {
      readTrackRecords()
      val selectedAudio = current.getAudioTrack().takeIf { it >= 0 }
      val selectedSubtitle = current.getSpuTrack().takeIf { it >= 0 }
      // libVLC lists "Disable" as track -1 in both lists; it is the screen's Off row, not a track.
      val audio = current.getAudioTracks().orEmpty().filter { it.id >= 0 }.map { trackInfo(it.id, it.name, "audio", it.id == selectedAudio) }
      val subtitles = current.getSpuTracks().orEmpty().filter { it.id >= 0 }.map { trackInfo(it.id, it.name, "sub", it.id == selectedSubtitle) }
      callback(audio, subtitles, selectedAudio, selectedSubtitle)
    }.onFailure { Log.w(TAG, "track list unavailable", it) }
  }

  /** Puts back the audio and subtitle choice that was in force before this view reopened its source. */
  private fun reselectTracks() {
    val wanted = reselect ?: return
    reselect = null
    val current = player ?: return
    runCatching {
      wanted.audio?.let { id -> if (current.getAudioTracks().orEmpty().any { it.id == id }) current.setAudioTrack(id) }
      wanted.subtitleFiles.forEach { path ->
        if (current.addSlave(SUBTITLE_SLAVE, Uri.fromFile(File(path)), false) && path !in subtitleFiles) subtitleFiles.add(path)
      }
      val subtitle = wanted.subtitle
      when {
        subtitle == null -> current.setSpuTrack(-1)
        current.getSpuTracks().orEmpty().any { it.id == subtitle } -> current.setSpuTrack(subtitle)
        // The subtitle that was showing was one of the external files, which get a new id each
        // time they are added: the last one added is the one that was selected.
        wanted.subtitleFiles.isNotEmpty() -> current.getSpuTracks().orEmpty().lastOrNull { it.id >= 0 }?.let { current.setSpuTrack(it.id) }
      }
    }.onFailure { Log.w(TAG, "could not reselect tracks after reopening", it) }
    dispatchTracks()
  }

  private fun rememberSelection() {
    val current = player ?: return
    reselect = runCatching {
      Reselect(
        audio = current.getAudioTrack().takeIf { it >= 0 },
        subtitle = current.getSpuTrack().takeIf { it >= 0 },
        subtitleFiles = subtitleFiles.toList(),
      )
    }.getOrNull()
  }

  // ---- timing ---------------------------------------------------------------------------------

  /** Positive is later, the same way round as the other engines; libVLC takes microseconds. */
  fun setSubtitleDelay(seconds: Double) {
    if (seconds == subtitleDelaySec) return
    subtitleDelaySec = seconds
    applyDelays()
  }

  fun setAudioDelay(seconds: Double) {
    if (seconds == audioDelaySec) return
    audioDelaySec = seconds
    applyDelays()
  }

  fun audioDelaySupported(): Boolean = true

  private fun applyDelays() {
    val current = player ?: return
    runCatching {
      current.setAudioDelay((audioDelaySec * 1_000_000.0).toLong())
      current.setSpuDelay((subtitleDelaySec * 1_000_000.0).toLong())
    }
  }

  // ---- subtitle appearance --------------------------------------------------------------------

  fun setSubtitleFontSize(size: Int) {
    if (size == subtitleFontSize) return
    subtitleFontSize = size
    applyAppearanceSoon()
  }

  fun setSubtitlePosition(position: Int) {
    if (position == subtitlePosition) return
    subtitlePosition = position
    applyAppearanceSoon()
  }

  fun setSubtitleColor(color: String) {
    if (color == subtitleColor) return
    subtitleColor = color
    applyAppearanceSoon()
  }

  fun setSubtitleBackgroundColor(color: String) {
    if (color == subtitleBackgroundColor) return
    subtitleBackgroundColor = color
    applyAppearanceSoon()
  }

  fun setSubtitleOutline(enabled: Boolean, color: String) {
    if (enabled == subtitleOutline && color == subtitleOutlineColor) return
    subtitleOutline = enabled
    subtitleOutlineColor = color
    applyAppearanceSoon()
  }

  fun setSubtitleBold(bold: Boolean) {
    if (bold == subtitleBold) return
    subtitleBold = bold
    applyAppearanceSoon()
  }

  /**
   * libVLC fixes the subtitle appearance when its text renderer is created, so a change during
   * playback can only be shown by creating it again. That is done here, once the viewer has left
   * the controls alone for a moment, and only when a subtitle is actually on - otherwise there is
   * nothing to see and the change simply waits for the next source.
   */
  private fun applyAppearanceSoon() {
    if (player == null || styleKeyInUse == null || styleKey() == styleKeyInUse) {
      main.removeCallbacks(applyAppearance)
      return
    }
    if (!loadReported || stoppedAtMs != null) return
    main.removeCallbacks(applyAppearance)
    main.postDelayed(applyAppearance, APPEARANCE_SETTLE_MS)
  }

  private fun applyAppearanceNow() {
    val current = player ?: return
    val source = currentSource ?: return
    if (released || !loadReported || styleKey() == styleKeyInUse) return
    val subtitleShowing = runCatching { current.getSpuTrack() >= 0 }.getOrDefault(false)
    if (!subtitleShowing) return
    Log.i(TAG, "subtitle appearance changed; reopening at ${lastTimeMs}ms to apply it")
    rememberSelection()
    restoring = true
    load(source, startAtMs = if (live) null else lastTimeMs)
  }

  // ---- reporting ------------------------------------------------------------------------------

  /** libVLC does not say what it holds ahead of the playhead; see the class comment. */
  fun bufferedRanges(): List<BufferedRange> = emptyList()

  /**
   * What libVLC knows about what it is playing. Asked for only while the info panel is open.
   *
   * Not available from libVLC, and so left empty rather than guessed: the HDR format, the Dolby
   * Vision profile, the decoder's name and whether it is hardware, passthrough, the container, and
   * how much is buffered.
   */
  fun playbackStats(): PlaybackStats {
    val current = player ?: return PlaybackStats()
    return runCatching {
      readTrackRecords()
      val video = currentVideoRecord()
      val audioId = current.getAudioTrack()
      val audio = trackRecords[audioId] as? IMedia.AudioTrack
      val videoKey = libVlcCodecKey(video?.codec)
      val counters = current.media?.let { media -> try { media.stats } finally { media.release() } }
      PlaybackStats(
        // libVLC counts rates in bytes per microsecond.
        bytesPerSecond = counters?.inputBitrate?.takeIf { it > 0f }?.let { it * 1_000_000.0 },
        videoBitrateBps = video?.bitrate?.takeIf { it > 0 }?.toDouble(),
        width = video?.width ?: 0,
        height = video?.height ?: 0,
        videoCodec = videoKey,
        audioCodec = libVlcCodecKey(audio?.codec),
        audioChannels = audio?.channels?.takeIf { it > 0 },
        frameRate = video?.takeIf { it.frameRateDen > 0 && it.frameRateNum > 0 }?.let { it.frameRateNum.toDouble() / it.frameRateDen },
        videoProfile = video?.let { libVlcProfileName(videoKey, it.profile, it.level) },
        videoBitDepth = video?.let { libVlcBitDepth(videoKey, it.profile) },
        audioSampleRateHz = audio?.rate?.takeIf { it > 0 },
        audioBitrateBps = audio?.bitrate?.takeIf { it > 0 }?.toDouble(),
        audioLanguage = audio?.language?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) },
        contentBitrateBps = counters?.demuxBitrate?.takeIf { it > 0f }?.let { it * 8_000_000.0 },
        decodedFrames = counters?.decodedVideo?.toLong()?.takeIf { it > 0L },
        droppedFrames = counters?.takeIf { it.decodedVideo > 0 }?.lostPictures?.toLong(),
      )
    }.getOrDefault(PlaybackStats())
  }

  /**
   * Whether this source is only half playing - picture with no sound, or sound with no picture -
   * by libVLC's own counters. Null while that cannot be told: before it has loaded, while paused,
   * or when both halves are running.
   */
  internal fun compatibilityProblem(): LibVlcCompatibilityProblem? {
    val current = player ?: return null
    if (!loadReported || paused || stalled) return null
    return runCatching {
      val counters = current.media?.let { media -> try { media.stats } finally { media.release() } } ?: return null
      libVlcCompatibilityProblem(
        hasAudioTrack = current.getAudioTracks().orEmpty().any { it.id >= 0 },
        audioSelected = current.getAudioTrack() >= 0,
        hasVideoTrack = current.getVideoTracks().orEmpty().any { it.id >= 0 },
        decodedAudio = counters.decodedAudio.toLong(),
        playedAudioBuffers = counters.playedAbuffers.toLong(),
        decodedVideo = counters.decodedVideo.toLong(),
        displayedPictures = counters.displayedPictures.toLong(),
      )
    }.getOrNull()
  }

  // ---- lifecycle ------------------------------------------------------------------------------

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    attachViewsIfPossible()
  }

  override fun onDetachedFromWindow() {
    release()
    super.onDetachedFromWindow()
  }

  /**
   * The window going away (another app, the screen switching off) takes the surface with it, and
   * libVLC does not pick a picture back up on a new surface by itself. So the engine is let go -
   * decoder, network connection and all - and the position kept, and the same source is reopened
   * from there when the window returns. Picture-in-picture keeps the window visible and is not
   * affected.
   */
  override fun onWindowVisibilityChanged(visibility: Int) {
    super.onWindowVisibilityChanged(visibility)
    if (released) return
    if (visibility == VISIBLE) {
      val resumeAt = stoppedAtMs ?: return
      stoppedAtMs = null
      val source = currentSource ?: return
      restoring = loadReportedBeforeStop
      load(source, startAtMs = resumeAt)
    } else if (stoppedAtMs == null && currentSource != null && player != null) {
      stoppedAtMs = if (live) 0L else lastTimeMs.coerceAtLeast(0L)
      loadReportedBeforeStop = loadReported
      rememberSelection()
      Log.i(TAG, "window hidden at ${stoppedAtMs}ms; releasing libVLC")
      teardown()
    }
  }

  private fun stopPlayback() {
    main.removeCallbacks(applyAppearance)
    runCatching { player?.stop() }
    setStalled(false)
  }

  /** Lets go of the surface here, then stops and frees the engine off the main thread. */
  private fun teardown() {
    val oldPlayer = player
    val oldVlc = libVlc
    player = null
    libVlc = null
    styleKeyInUse = null
    main.removeCallbacksAndMessages(null)
    setStalled(false)
    if (oldPlayer != null) {
      runCatching { oldPlayer.setEventListener(null) }
      if (viewsAttached) runCatching { oldPlayer.detachViews() }
    }
    viewsAttached = false
    if (oldPlayer == null && oldVlc == null) return
    Thread({
      runCatching { oldPlayer?.stop() }
      runCatching { oldPlayer?.release() }
      runCatching { oldVlc?.release() }
    }, "streamdek-vlc-release").start()
  }

  fun release() {
    if (released) return
    released = true
    keepScreenOn = false
    teardown()
  }
}
