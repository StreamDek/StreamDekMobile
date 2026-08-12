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
import net.streamdek.mobile.nativeapp.normalizePreferredAudioLanguage
import net.streamdek.mobile.nativeapp.preferredAudioLanguageTags

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
        private const val DEFAULT_SUBTITLE_COLOR = "#FFFFFFFF"
        private const val DEFAULT_SUBTITLE_OUTLINE_COLOR = "#FF000000"
    }

    private var initialized = false
    private var pendingSource: String? = null
    private var currentSource: String? = null
    private var pendingResizeMode: String = "cover"
    private var pendingDecoderMode: String = "HW+"
    private var pendingRenderSurface: String = "Standard"
    private var pendingPreferredAudioLanguage: String = "en"
    /**
     * Subtitle appearance, held here rather than written straight through: the player sets it while
     * the view is still being constructed, long before mpv exists to receive it.
     */
    private var subtitleFontSize = 55
    private var subtitlePosition = 92
    private var subtitleColor = DEFAULT_SUBTITLE_COLOR
    private var subtitleBackgroundColor = "#00000000"
    private var subtitleOutlineEnabled = true
    private var subtitleOutlineColor = DEFAULT_SUBTITLE_OUTLINE_COLOR
    private var subtitleBold = false
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

    /**
     * Fired when mpv starts or stops waiting on the network cache. A live feed can
     * starve indefinitely without ever emitting END_FILE or an error, so this is the
     * only signal the player gets that playback has stalled rather than failed.
     */
    var onStallChangedCallback: ((stalled: Boolean) -> Unit)? = null

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
            // Apply resize mode — default to cover (Fill) so videos always fill the screen
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
            // The player sets the subtitle appearance the moment this view is constructed, which is
            // before the surface exists and therefore before mpv can be told anything. Replay it
            // here so the viewer's choices are in place for the first subtitle drawn.
            applySubtitleStyle()

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
        onStallChangedCallback = null
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
        val protocolWhitelistResult = MPVLib.setOptionString(
            "demuxer-lavf-o",
            "protocol_whitelist=[file,tcp,tls,http,https,crypto,data]",
        )
        if (BuildConfig.DEBUG) Log.i(TAG, "protocol whitelist option result=$protocolWhitelistResult")
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
        MPVLib.setOptionString("alang", preferredAudioLanguageTags(pendingPreferredAudioLanguage).joinToString(","))
        MPVLib.setOptionString("slang", "eng,en")
        // Selecting an HLS subtitle rendition before FILE_LOADED can block the entire source
        // when an add-on advertises a dead subtitle playlist. NativePlayer applies the user's
        // preferred subtitle only after the video itself has prepared.
        MPVLib.setOptionString("sid", "no")
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
        MPVLib.observeProperty("paused-for-cache", MPV_FORMAT_FLAG)
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

    fun setPreferredAudioLanguage(language: String?) {
        pendingPreferredAudioLanguage = normalizePreferredAudioLanguage(language)
        if (initialized && !isDestroyed) {
            MPVLib.setPropertyString("alang", preferredAudioLanguageTags(pendingPreferredAudioLanguage).joinToString(","))
        }
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
        // so we strip the scheme prefix. "file://" + "/absolute/path" → "/absolute/path".
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
        subtitleFontSize = size
        applySubtitleStyle()
    }

    /**
     * Set subtitle text color in #AARRGGBB hex format - the order the settings store writes.
     * E.g. white = "#FFFFFFFF", yellow = "#FFFFEB3B".
     */
    fun setSubtitleColor(color: String) {
        subtitleColor = color
        applySubtitleStyle()
    }

    /**
     * Set the panel drawn behind subtitle text, in #AARRGGBB hex.
     *
     * A fully transparent value leaves the picture untouched, which is mpv's own default.
     */
    fun setSubtitleBackgroundColor(color: String) {
        subtitleBackgroundColor = color
        applySubtitleStyle()
    }

    /**
     * Set the border traced around subtitle glyphs, in #AARRGGBB hex.
     *
     * Turning the outline off is expressed as a zero outline size rather than a transparent colour,
     * so the border does not quietly reappear when the colour is next changed.
     */
    fun setSubtitleOutline(enabled: Boolean, color: String) {
        subtitleOutlineEnabled = enabled
        subtitleOutlineColor = color
        applySubtitleStyle()
    }

    /** Draw subtitles in a heavier weight. */
    fun setSubtitleBold(bold: Boolean) {
        subtitleBold = bold
        applySubtitleStyle()
    }

    /**
     * Set subtitle vertical position (0–150; 90 = near bottom, 0 = top).
     * Maps to mpv's `sub-pos` property.
     */
    fun setSubtitlePosition(position: Int) {
        subtitlePosition = position
        applySubtitleStyle()
    }

    /**
     * Push the whole appearance to mpv at once.
     *
     * Every setter routes through here because two of mpv's own behaviours otherwise make the
     * settings look like they do nothing:
     *
     *  - a styled (ASS/SSA) subtitle carries its own colours and weight, and mpv's default
     *    `sub-ass-override=scale` lets only the sizing options through. That is exactly why size
     *    and position appeared to work while colour, background and bold did not. The override is
     *    forced only once the viewer has chosen a look of their own, so a subtitle script that
     *    styles itself is left alone for anyone still on the defaults.
     *  - `sub-back-color` is the shadow colour under mpv's default border style, and the shadow has
     *    zero offset, so a background colour on its own is never drawn. It needs the box border
     *    style, which is switched on only while a background is actually wanted - the box would
     *    otherwise replace the outline for everyone.
     */
    private fun applySubtitleStyle() {
        if (!initialized || isDestroyed) return
        val textColor = normalizeSubtitleColor(subtitleColor)
        val backColor = normalizeSubtitleColor(subtitleBackgroundColor)
        val outlineColor = normalizeSubtitleColor(subtitleOutlineColor)
        val hasBackground = colorIsVisible(subtitleBackgroundColor)
        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "applySubtitleStyle: size=$subtitleFontSize pos=$subtitlePosition text=$textColor " +
                    "back=$backColor outline=$subtitleOutlineEnabled/$outlineColor bold=$subtitleBold",
            )
        }
        MPVLib.setPropertyInt("sub-font-size", subtitleFontSize)
        MPVLib.setPropertyInt("sub-pos", subtitlePosition)
        MPVLib.setPropertyString("sub-color", textColor)
        MPVLib.setPropertyString("sub-back-color", backColor)
        MPVLib.setPropertyString("sub-bold", if (subtitleBold) "yes" else "no")
        // Renamed in mpv 0.38; the deprecated names are still accepted, but ask for the current
        // ones first so this keeps working when they are finally dropped.
        setSubtitleProperty("sub-outline-color", "sub-border-color", outlineColor)
        setSubtitleProperty("sub-outline-size", "sub-border-size", if (subtitleOutlineEnabled) "3" else "0")
        MPVLib.setPropertyString("sub-border-style", if (hasBackground) "background-box" else "outline-and-shadow")
        MPVLib.setPropertyString("sub-ass-override", if (subtitleAppearanceIsCustomised()) "force" else "scale")
    }

    /** Set [name], falling back to [deprecatedName] on an mpv build that does not know it yet. */
    private fun setSubtitleProperty(name: String, deprecatedName: String, value: String) {
        val target = if (MPVLib.getPropertyString(name) != null) name else deprecatedName
        MPVLib.setPropertyString(target, value)
    }

    /** True once anything but sizing has been changed from the shipped defaults. */
    private fun subtitleAppearanceIsCustomised(): Boolean =
        subtitleBold ||
            !subtitleColor.trim().equals(DEFAULT_SUBTITLE_COLOR, ignoreCase = true) ||
            colorIsVisible(subtitleBackgroundColor) ||
            !subtitleOutlineEnabled ||
            !subtitleOutlineColor.trim().equals(DEFAULT_SUBTITLE_OUTLINE_COLOR, ignoreCase = true)

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
     * Colours are stored the way Android writes them, "#AARRGGBB", which is also the order mpv
     * reads - alpha first, FF opaque. A six-digit "#RRGGBB" is taken as fully opaque. Anything
     * else is handed to mpv untouched for it to reject.
     *
     * This used to rotate the value from "#RRGGBBAA", which turned every chosen colour into a
     * near-transparent one: yellow, "#FFFFEB3B", reached mpv as alpha 0x3B.
     */
    private fun normalizeSubtitleColor(color: String): String {
        val hex = color.trim().removePrefix("#")
        if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return color
        return when (hex.length) {
            8 -> "#$hex"
            6 -> "#FF$hex"
            else -> color
        }
    }

    /** False for a colour that would draw nothing, which is how "no background" is stored. */
    private fun colorIsVisible(color: String): Boolean {
        val hex = color.trim().removePrefix("#")
        if (hex.length != 8) return true
        return (hex.substring(0, 2).toIntOrNull(16) ?: 255) > 0
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
        if (property == "paused-for-cache") {
            onStallChangedCallback?.invoke(value)
        }
    }

    override fun eventProperty(property: String, value: String) {
        if (isDestroyed) return
    }

    override fun event(eventId: Int) {
        if (isDestroyed) return
        when (eventId) {
            MPV_EVENT_FILE_LOADED -> {
                // New source has started — END_FILE events from here on are genuine
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
                    // Genuine file-error string — always surface this, even during a source switch,
                    // because it means the new source itself failed to open.
                    isSwitchingSource = false
                    val baseMessage = "MPV could not play this source ($fileError)."
                    val detailed = lastMpvErrorMessage?.takeIf { it.isNotBlank() }?.let { "$baseMessage $it" } ?: baseMessage
                    onErrorCallback?.invoke(detailed)
                } else if (isSwitchingSource) {
                    // END_FILE fired for the outgoing source during a loadfile replace.
                    // FILE_LOADED for the incoming source hasn't arrived yet — suppress
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

        // Ignore internal MPV scripting/hook messages — they are never user-actionable
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
