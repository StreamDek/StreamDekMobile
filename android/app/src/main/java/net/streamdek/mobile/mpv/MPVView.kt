package net.streamdek.mobile.mpv

import android.content.Context
import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.io.FileOutputStream
import net.streamdek.mobile.BuildConfig

data class MpvTrackInfo(
    val id: Int,
    val type: String,
    val title: String?,
    val language: String?,
    val codec: String?,
    val selected: Boolean,
)

class MPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener, MPVLib.EventObserver, MPVLib.LogObserver {

    companion object {
        private const val TAG = "StreamDekMPVView"
        private const val MPV_EVENT_END_FILE = 7
        private const val MPV_EVENT_FILE_LOADED = 8
        private const val MPV_FORMAT_NONE = 0
        private const val MPV_FORMAT_FLAG = 3
        private const val MPV_FORMAT_INT64 = 4
        private const val MPV_FORMAT_DOUBLE = 5
        private const val MPV_LOG_LEVEL_ERROR = 2
        private const val MPV_LOG_LEVEL_WARN = 3
    }

    private var initialized = false
    private var pendingSource: String? = null
    private var currentSource: String? = null
    private var pendingResizeMode: String = "cover"
    private var pendingDecoderMode: String = "HW+"
    private var pendingRenderSurface: String = "Standard"
    private var paused = false
    private var surface: Surface? = null
    private var headers: Map<String, String>? = null
    private var lastMpvErrorMessage: String? = null
    private var pendingLoadRunnable: Runnable? = null
    private var subtitleFontsDir: File? = null
    @Volatile private var isDestroyed = false
    /**
     * Set to true when we intentionally call loadfile to switch sources.
     * While true, END_FILE events fired for the outgoing source are suppressed
     * so they don't trigger a false "MPV could not play this source" error overlay.
     * Cleared on the next FILE_LOADED (new source started) or surface destruction.
     *
     * @Volatile: this field is written on the main thread (loadFile / FILE_LOADED)
     * and read on MPV's internal event thread (event()). Without @Volatile the JVM
     * may not guarantee cross-thread visibility, causing the event thread to see a
     * stale false and incorrectly surface a suppressed END_FILE as an error.
     */
    @Volatile private var isSwitchingSource = false

    var onLoadCallback: ((duration: Double, width: Int, height: Int) -> Unit)? = null
    var onProgressCallback: ((position: Double, duration: Double) -> Unit)? = null
    var onEndCallback: (() -> Unit)? = null
    var onErrorCallback: ((message: String) -> Unit)? = null
    var onTracksChangedCallback: ((audioTracks: List<MpvTrackInfo>, subtitleTracks: List<MpvTrackInfo>, selectedAudioTrackId: Int?, selectedSubtitleTrackId: Int?) -> Unit)? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
        keepScreenOn = true
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        try {
            isDestroyed = false
            if (BuildConfig.DEBUG) Log.i(TAG, "onSurfaceTextureAvailable (${width}x${height}) pendingSource=${!pendingSource.isNullOrBlank()}")
            keepScreenOn = true
            surface = Surface(surfaceTexture)
            MPVLib.create(context.applicationContext)
            initOptions()
            MPVLib.init()
            MPVLib.attachSurface(surface!!)
            MPVLib.addObserver(this)
            MPVLib.addLogObserver(this)
            MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
            // Apply resize mode â€” default to cover (Fill) so videos always fill the screen
            when (pendingResizeMode) {
                "cover" -> {
                    MPVLib.setPropertyDouble("panscan", 1.0)
                    MPVLib.setPropertyString("keepaspect", "yes")
                }
                "stretch" -> {
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyString("keepaspect", "no")
                }
                else -> {
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyString("keepaspect", "yes")
                }
            }
            observeProperties()
            initialized = true

            pendingSource?.let { source ->
                if (BuildConfig.DEBUG) Log.i(TAG, "Applying pending source after surface ready")
                applyHeaders()
                loadFile(source)
                pendingSource = null
            }
            MPVLib.setPropertyBoolean("pause", paused)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to initialize MPV", error)
            onErrorCallback?.invoke("Embedded MPV initialization failed: ${error.message}")
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (!initialized) return
        MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        isDestroyed = true
        val wasInitialized = initialized
        initialized = false
        pendingLoadRunnable?.let {
            removeCallbacks(it)
            pendingLoadRunnable = null
        }
        pendingSource = currentSource
        isSwitchingSource = false
        onLoadCallback = null
        onProgressCallback = null
        onEndCallback = null
        onErrorCallback = null
        onTracksChangedCallback = null
        if (wasInitialized) {
            MPVLib.removeObserver(this)
            MPVLib.removeLogObserver(this)
            MPVLib.detachSurface()
            MPVLib.destroy()
        }
        surface?.release()
        surface = null
        keepScreenOn = false
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // no-op
    }

    private fun initOptions() {
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("msg-level", "all=info")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        applyDecoderMode()
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("cache", "yes")
        // Track changes in mkv/stream sources often force a refresh seek.
        // Give mpv enough cache headroom to survive that seek without draining
        // immediately back into buffering.
        MPVLib.setOptionString("cache-secs", "300")
        MPVLib.setOptionString("cache-on-disk", "yes")
        MPVLib.setOptionString("cache-pause-wait", "1")
        MPVLib.setOptionString("demuxer-seekable-cache", "yes")
        MPVLib.setOptionString("demuxer-readahead-secs", "45")
        MPVLib.setOptionString("demuxer-max-bytes", "536870912")
        MPVLib.setOptionString("demuxer-max-back-bytes", "536870912")
        MPVLib.setOptionString("network-timeout", "60")
        MPVLib.setOptionString(
            "stream-lavf-o",
            "reconnect=1,reconnect_streamed=1,reconnect_delay_max=10,reconnect_on_http_error=429"
        )
        MPVLib.setOptionString("sub-auto", "fuzzy")
        MPVLib.setOptionString("sub-visibility", "yes")
        MPVLib.setOptionString("sub-font", "Roboto")
        MPVLib.setOptionString("sub-font-provider", "none")
        MPVLib.setOptionString("sub-fonts-dir", "/system/fonts")
        MPVLib.setOptionString("sub-codepage", "auto")
        MPVLib.setOptionString("embeddedfonts", "yes")
        MPVLib.setOptionString("alang", "eng,en")
        MPVLib.setOptionString("slang", "eng,en")
        MPVLib.setOptionString("sid", "auto")
        MPVLib.setOptionString("terminal", "no")
        MPVLib.setOptionString("input-default-bindings", "no")
        MPVLib.setOptionString("osc", "no")
        applyRenderSurfaceMode()
    }

    private fun applyDecoderMode() {
        when (pendingDecoderMode) {
            "HW" -> {
                MPVLib.setOptionString("hwdec", "auto")
                MPVLib.setOptionString("hwdec-codecs", "all")
            }
            "SW" -> {
                MPVLib.setOptionString("hwdec", "no")
                MPVLib.setOptionString("hwdec-codecs", "")
            }
            else -> {
                MPVLib.setOptionString("hwdec", "auto-safe")
                MPVLib.setOptionString("hwdec-codecs", "all")
            }
        }
    }

    private fun applyRenderSurfaceMode() {
        val compatibility = pendingRenderSurface.equals("Compatibility", ignoreCase = true)
        isOpaque = compatibility
        if (initialized && !isDestroyed) {
            MPVLib.setPropertyString("video-sync", if (compatibility) "audio" else "display-resample")
            MPVLib.setPropertyString("interpolation", if (compatibility) "no" else "yes")
        }
    }

    private fun ensureSubtitleFontsDir() {
        if (subtitleFontsDir?.exists() == true) return

        val targetDir = File(context.cacheDir, "mpv-fonts")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Log.w(TAG, "Failed to create subtitle fonts directory: ${targetDir.absolutePath}")
            subtitleFontsDir = targetDir
            return
        }

        copyBundledFont(context.assets, "mpv_fonts/Arial.ttf", File(targetDir, "Arial.ttf"))
        subtitleFontsDir = targetDir
        if (BuildConfig.DEBUG) Log.i(TAG, "Subtitle fonts directory ready: ${targetDir.absolutePath}")
    }

    private fun copyBundledFont(assetManager: AssetManager, assetPath: String, destination: File) {
        if (destination.exists() && destination.length() > 0) return
        try {
            assetManager.open(assetPath).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to copy bundled subtitle font $assetPath", error)
        }
    }

    private fun observeProperties() {
        MPVLib.observeProperty("time-pos", MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration/full", MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPV_FORMAT_FLAG)
        MPVLib.observeProperty("aid", MPV_FORMAT_INT64)
        MPVLib.observeProperty("sid", MPV_FORMAT_INT64)
        MPVLib.observeProperty("width", MPV_FORMAT_INT64)
        MPVLib.observeProperty("height", MPV_FORMAT_INT64)
        MPVLib.observeProperty("track-list", MPV_FORMAT_NONE)
    }

    private fun loadFile(url: String) {
        if (isDestroyed) return
        if (BuildConfig.DEBUG) Log.i(TAG, "loadFile called")
        // Clear any error message from the outgoing source so it can't bleed
        // into the incoming source's END_FILE handler.
        lastMpvErrorMessage = null
        // Mark that we're intentionally replacing the current source.
        // Any END_FILE that fires for the outgoing source will be suppressed
        // until FILE_LOADED confirms the new source has started.
        isSwitchingSource = true
        MPVLib.command(arrayOf("loadfile", url, "replace"))
    }

    private fun applyHeaders() {
        val nextHeaders = headers
        if (nextHeaders.isNullOrEmpty()) {
            MPVLib.setPropertyString("http-header-fields", "")
            return
        }

        getHeaderValue("User-Agent")?.takeIf { it.isNotBlank() }?.let { userAgent ->
            MPVLib.setPropertyString("user-agent", userAgent)

        }

        buildHttpHeaderFields()?.let { headerString ->
            MPVLib.setPropertyString("http-header-fields", headerString)

        }
    }

    private fun buildHttpHeaderFields(): String? {
        val nextHeaders = headers ?: return null
        val mapped = nextHeaders.entries
            .filterNot { it.key.equals("User-Agent", ignoreCase = true) }
            .filter { it.value.isNotBlank() }
            .map { "${it.key}: ${escapeHeaderValue(it.value)}" }
        if (mapped.isEmpty()) return null
        return mapped.joinToString(",")
    }

    private fun escapeHeaderValue(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(",", "\\,")
    }

    private fun getHeaderValue(name: String): String? {
        val nextHeaders = headers ?: return null
        return nextHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    private fun scheduleLoad(source: String) {
        if (isDestroyed) return
        if (BuildConfig.DEBUG) Log.i(TAG, "scheduleLoad called (initialized=$initialized, len=${source.length})")
        pendingLoadRunnable?.let {
            removeCallbacks(it)
            pendingLoadRunnable = null
        }
        if (!initialized) {
            pendingSource = source
            return
        }
        val runnable = Runnable {
            pendingLoadRunnable = null
            if (!initialized) {
                if (BuildConfig.DEBUG) Log.i(TAG, "scheduleLoad runnable before init; keeping pending source")
                pendingSource = source
                return@Runnable
            }
            applyHeaders()
            loadFile(source)
        }
        pendingLoadRunnable = runnable
        post(runnable)
    }

    fun setSource(url: String?) {
        val source = url?.trim().orEmpty()
        if (source.isBlank() || isDestroyed || source == currentSource) return
        if (BuildConfig.DEBUG) Log.i(TAG, "setSource called (len=${source.length})")
        currentSource = source
        pendingSource = source
        scheduleLoad(source)
    }
    fun reloadSource() {
        val source = currentSource?.trim().orEmpty()
        if (source.isBlank() || isDestroyed) return
        if (BuildConfig.DEBUG) Log.i(TAG, "reloadSource called")
        pendingSource = source
        scheduleLoad(source)
    }


    fun setHeaders(nextHeaders: Map<String, String>?) {
        if (isDestroyed) return
        headers = nextHeaders
        if (initialized) applyHeaders()
    }

    fun setPaused(nextPaused: Boolean) {
        if (isDestroyed) return
        paused = nextPaused
        keepScreenOn = !nextPaused
        if (!initialized) return
        MPVLib.setPropertyBoolean("pause", nextPaused)
    }

    fun seekTo(positionSeconds: Double) {
        if (!initialized || isDestroyed) return
        if (BuildConfig.DEBUG) Log.i(TAG, "seekTo -> $positionSeconds")
        MPVLib.command(arrayOf("seek", positionSeconds.toString(), "absolute"))
    }

    fun setSpeed(speed: Double) {
        if (!initialized || isDestroyed) return
        MPVLib.setPropertyDouble("speed", speed)
    }

    fun setVolume(volume: Double) {
        if (!initialized || isDestroyed) return
        MPVLib.setPropertyDouble("volume", (volume * 100.0).coerceIn(0.0, 100.0))
    }

    fun setResizeMode(mode: String?) {
        if (mode != null) pendingResizeMode = mode
        if (!initialized || isDestroyed) return
        when (mode) {
            "cover" -> {
                MPVLib.setPropertyDouble("panscan", 1.0)
                MPVLib.setPropertyString("keepaspect", "yes")
            }

            "stretch" -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyString("keepaspect", "no")
            }

            else -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyString("keepaspect", "yes")
            }
        }
    }

    fun setDecoderMode(mode: String?) {
        if (mode != null) pendingDecoderMode = mode
        if (!initialized || isDestroyed) return
        when (pendingDecoderMode) {
            "HW" -> {
                MPVLib.setPropertyString("hwdec", "auto")
                MPVLib.setPropertyString("hwdec-codecs", "all")
            }
            "SW" -> {
                MPVLib.setPropertyString("hwdec", "no")
                MPVLib.setPropertyString("hwdec-codecs", "")
            }
            else -> {
                MPVLib.setPropertyString("hwdec", "auto-safe")
                MPVLib.setPropertyString("hwdec-codecs", "all")
            }
        }
    }

    fun setRenderSurface(mode: String?) {
        if (mode != null) pendingRenderSurface = mode
        applyRenderSurfaceMode()
    }

    fun setAudioTrack(trackId: Int) {
        if (!initialized || isDestroyed) return
        MPVLib.setPropertyInt("aid", trackId)
        dispatchTracksChanged()
    }

    fun setSubtitleTrack(trackId: Int) {
        if (!initialized || isDestroyed) return
        ensureSubtitleVisibility()
        MPVLib.command(arrayOf("set", "sid", trackId.toString()))
        ensureSubtitleVisibility()
        post {
            logSubtitleState("setSubtitleTrack($trackId)")
            dispatchTracksChanged()
        }
    }

    fun disableSubtitleTrack() {
        if (!initialized) return
        MPVLib.command(arrayOf("set", "sid", "no"))
        post {
            logSubtitleState("disableSubtitleTrack")
            dispatchTracksChanged()
        }
    }

    /**
     * Load an external subtitle file into the currently playing media.
     *
     * Uses mpv's `sub-add` command with the "select" flag so the newly added
     * subtitle is immediately selected for display. After the command runs,
     * mpv fires a track-list change which triggers [dispatchTracksChanged] via
     * the [eventProperty] observer, so the React side receives the updated track
     * list automatically.
     *
     * @param path A file:// URI or absolute path to the subtitle file on device
     *             storage.
     */
    fun addSubtitleFile(path: String) {
        if (!initialized || isDestroyed) {
            Log.w(TAG, "addSubtitleFile called before init; ignoring")
            return
        }
        ensureSubtitleVisibility()
        // Subtitle storage may provide file:// URIs (e.g. "file:///data/user/0/.../sub.srt").
        // MPV's sub-add on Android expects the raw absolute path, not a file:// URI,
        // so we strip the scheme prefix. "file://" + "/absolute/path" â†’ "/absolute/path".
        val normalizedPath = if (path.startsWith("file://")) path.removePrefix("file://") else path
        if (BuildConfig.DEBUG) Log.i(TAG, "addSubtitleFile: $normalizedPath")
        // "select" tells mpv to activate this sub immediately after loading it
        MPVLib.command(arrayOf("sub-add", normalizedPath, "select"))
        // Dispatch synchronously so the React side sees the new track right away
        post { dispatchTracksChanged() }
    }

    /**
     * Set the subtitle display delay in seconds.
     * Positive values delay the subtitle (shows later than the audio cue).
     * Negative values advance it (shows earlier).
     * This maps directly to mpv's `sub-delay` property.
     */
    fun setSubtitleDelay(seconds: Double) {
        if (!initialized || isDestroyed) return
        if (BuildConfig.DEBUG) Log.i(TAG, "setSubtitleDelay: $seconds")
        MPVLib.setPropertyDouble("sub-delay", seconds)
    }

    /** Set subtitle font size (mpv default is 55). */
    fun setSubtitleFontSize(size: Int) {
        if (!initialized || isDestroyed) return
        if (BuildConfig.DEBUG) Log.i(TAG, "setSubtitleFontSize: $size")
        MPVLib.setPropertyInt("sub-font-size", size)
    }

    /**
     * Set subtitle text color in #RRGGBBAA hex format.
     * E.g. white = "#FFFFFFFF", yellow = "#FFFF00FF".
     */
    fun setSubtitleColor(color: String) {
        if (!initialized || isDestroyed) return
        val mpvColor = normalizeSubtitleColor(color)
        if (BuildConfig.DEBUG) Log.i(TAG, "setSubtitleColor: $color -> $mpvColor")
        MPVLib.setPropertyString("sub-color", mpvColor)
    }

    /**
     * Set subtitle vertical position (0â€“150; 90 = near bottom, 0 = top).
     * Maps to mpv's `sub-pos` property.
     */
    fun setSubtitlePosition(position: Int) {
        if (!initialized || isDestroyed) return
        if (BuildConfig.DEBUG) Log.i(TAG, "setSubtitlePosition: $position")
        MPVLib.setPropertyInt("sub-pos", position)
    }

    private fun dispatchTracksChanged() {
        if (isDestroyed) return
        val callback = onTracksChangedCallback ?: return
        val trackCount = (MPVLib.getPropertyInt("track-list/count") ?: 0).coerceAtLeast(0)
        if (trackCount <= 0) {
            callback(emptyList(), emptyList(), MPVLib.getPropertyInt("aid"), normalizeSubtitleTrackId(MPVLib.getPropertyInt("sid")))
            return
        }

        val selectedAudioTrackId = MPVLib.getPropertyInt("aid")
        val selectedSubtitleTrackId = normalizeSubtitleTrackId(MPVLib.getPropertyInt("sid"))
        val audioTracks = mutableListOf<MpvTrackInfo>()
        val subtitleTracks = mutableListOf<MpvTrackInfo>()

        for (index in 0 until trackCount) {
            val type = MPVLib.getPropertyString("track-list/$index/type")?.trim() ?: continue
            if (type != "audio" && type != "sub") continue
            val trackId = MPVLib.getPropertyInt("track-list/$index/id") ?: continue
            val title = MPVLib.getPropertyString("track-list/$index/title")?.trim()?.takeIf { it.isNotEmpty() }
            val language = MPVLib.getPropertyString("track-list/$index/lang")?.trim()?.takeIf { it.isNotEmpty() }
            val codec = MPVLib.getPropertyString("track-list/$index/codec")?.trim()?.takeIf { it.isNotEmpty() }

            val selected = if (type == "audio") {
                selectedAudioTrackId != null && selectedAudioTrackId == trackId
            } else {
                selectedSubtitleTrackId != null && selectedSubtitleTrackId == trackId
            }

            val trackInfo = MpvTrackInfo(
                id = trackId,
                type = type,
                title = title,
                language = language,
                codec = codec,
                selected = selected,
            )

            if (type == "audio") {
                audioTracks.add(trackInfo)
            } else {
                subtitleTracks.add(trackInfo)
            }
        }

        callback(audioTracks, subtitleTracks, selectedAudioTrackId, selectedSubtitleTrackId)
    }

    private fun normalizeSubtitleTrackId(trackId: Int?): Int? {
        if (trackId == null) return null
        return if (trackId < 0) null else trackId
    }

    private fun ensureSubtitleVisibility() {
        if (!initialized || isDestroyed) return
        MPVLib.command(arrayOf("set", "sub-visibility", "yes"))
        val visibility = MPVLib.getPropertyBoolean("sub-visibility")
        if (BuildConfig.DEBUG) Log.i(TAG, "ensureSubtitleVisibility -> sub-visibility=${visibility ?: "unknown"}")
    }

    /**
     * MPV/libass expects hex colors as #AARRGGBB. The UI sends #RRGGBBAA.
     * Convert the incoming value so color names map correctly in the player.
     */
    private fun normalizeSubtitleColor(color: String): String {
        val rgba = Regex("^#([0-9a-fA-F]{8})$")
        val match = rgba.matchEntire(color.trim())
        if (match == null) return color

        val hex = match.groupValues[1]
        val red = hex.substring(0, 2)
        val green = hex.substring(2, 4)
        val blue = hex.substring(4, 6)
        val alpha = hex.substring(6, 8)
        return "#${alpha}${red}${green}${blue}"
    }

    private fun logSubtitleState(reason: String) {
        if (isDestroyed) return
        val sid = normalizeSubtitleTrackId(MPVLib.getPropertyInt("sid"))
        val visibility = MPVLib.getPropertyBoolean("sub-visibility")
        val trackCount = (MPVLib.getPropertyInt("track-list/count") ?: 0).coerceAtLeast(0)
        if (BuildConfig.DEBUG) Log.i(TAG, "subtitle-state[$reason]: sid=${sid ?: "none"}, sub-visibility=${visibility ?: "unknown"}, track-count=$trackCount")
    }

    override fun eventProperty(property: String) {
        if (isDestroyed) return
        if (property == "track-list") {
            dispatchTracksChanged()
        }
    }

    override fun eventProperty(property: String, value: Long) {
        if (isDestroyed) return
        if (property == "aid" || property == "sid") {
            dispatchTracksChanged()
        }
    }

    override fun eventProperty(property: String, value: Double) {
        if (isDestroyed) return
        when (property) {
            "time-pos" -> {
                val duration = MPVLib.getPropertyDouble("duration/full")
                    ?: MPVLib.getPropertyDouble("duration")
                    ?: 0.0
                onProgressCallback?.invoke(value, duration)
            }

            "duration/full", "duration" -> {
                val width = MPVLib.getPropertyInt("width") ?: 0
                val height = MPVLib.getPropertyInt("height") ?: 0
                onLoadCallback?.invoke(value, width, height)
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (isDestroyed) return
        if (property == "eof-reached" && value) {
            onEndCallback?.invoke()
        }
    }

    override fun eventProperty(property: String, value: String) {
        if (isDestroyed) return
    }

    override fun event(eventId: Int) {
        if (isDestroyed) return
        when (eventId) {
            MPV_EVENT_FILE_LOADED -> {
                // New source has started â€” END_FILE events from here on are genuine
                isSwitchingSource = false
                lastMpvErrorMessage = null
                ensureSubtitleVisibility()
                logSubtitleState("FILE_LOADED")
                dispatchTracksChanged()
                if (!paused) {
                    keepScreenOn = true
                    MPVLib.setPropertyBoolean("pause", false)
                } else {
                    keepScreenOn = false
                }
            }

            MPV_EVENT_END_FILE -> {
                val duration = MPVLib.getPropertyDouble("duration/full")
                    ?: MPVLib.getPropertyDouble("duration")
                    ?: 0.0
                val eofReached = MPVLib.getPropertyBoolean("eof-reached") ?: false
                val fileError = MPVLib.getPropertyString("file-error")?.trim().orEmpty()
                if (fileError.isNotBlank() && !fileError.equals("success", ignoreCase = true)) {
                    // Genuine file-error string â€” always surface this, even during a source switch,
                    // because it means the new source itself failed to open.
                    isSwitchingSource = false
                    val baseMessage = "MPV could not play this source ($fileError)."
                    val detailed = lastMpvErrorMessage?.takeIf { it.isNotBlank() }?.let { "$baseMessage $it" } ?: baseMessage
                    onErrorCallback?.invoke(detailed)
                } else if (isSwitchingSource) {
                    // END_FILE fired for the outgoing source during a loadfile replace.
                    // FILE_LOADED for the incoming source hasn't arrived yet â€” suppress
                    // this event so the React side never sees a false error overlay.
                    if (BuildConfig.DEBUG) Log.i(TAG, "END_FILE suppressed (isSwitchingSource=true, duration=$duration, eofReached=$eofReached)")
                } else if (duration < 1.0 && !eofReached) {
                    val fallbackMessage = lastMpvErrorMessage?.takeIf { it.isNotBlank() }?.let {
                        "MPV could not play this source. $it"
                    } ?: "MPV could not play this source."
                    onErrorCallback?.invoke(fallbackMessage)
                } else {
                    onEndCallback?.invoke()
                }
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (isDestroyed) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        if (level <= MPV_LOG_LEVEL_WARN) {
            Log.w(TAG, "mpv[$prefix][$level] $trimmed")
        } else {
            if (BuildConfig.DEBUG) Log.i(TAG, "mpv[$prefix][$level] $trimmed")
        }

        // Ignore internal MPV scripting/hook messages â€” they are never user-actionable
        // and frequently appear during normal source switches (e.g. auto_profiles hooks
        // being torn down). Storing them in lastMpvErrorMessage causes false error
        // overlays when END_FILE subsequently fires.
        val isInternalScriptMessage =
            trimmed.contains("hook", ignoreCase = true) ||
                trimmed.contains("script", ignoreCase = true) ||
                prefix == "cplayer" && trimmed.startsWith("Removing")
        if (!isInternalScriptMessage) {
            val isUsefulError =
                level <= MPV_LOG_LEVEL_ERROR ||
                    trimmed.contains("error", ignoreCase = true) ||
                    trimmed.contains("failed", ignoreCase = true) ||
                    trimmed.contains("unsupported", ignoreCase = true)
            if (isUsefulError) {
                lastMpvErrorMessage = "mpv[$prefix] $trimmed"
            }
        }
    }
}
