package net.streamdek.mobile.nativeapp.mediaserver.plex

import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRoute
import java.net.URLEncoder
import java.util.Locale

/**
 * Deciding how to play a Plex title: Direct Play, Direct Stream or Transcode.
 *
 * The rule the brief sets is the rule here - never ask the server to transcode what this device can
 * play as it is. So the plan is worked out on the device, from what the file is and what this
 * device can decode, and the server's transcoder is only involved when the answer is no:
 *
 *  - **Direct Play**: the file itself, untouched. Chosen whenever the container, the video codec at
 *    its resolution and the bitrate all suit this device and this connection. Audio never blocks it:
 *    both engines here decode every common audio codec in software (Media3 with the FFmpeg extension,
 *    and mpv).
 *  - **Direct Stream**: the video copied into HLS, audio converted if it must be. For a container
 *    the chosen engine cannot open when the video itself is fine.
 *  - **Transcode**: the server re-encodes. For video this device cannot decode, and for a remote
 *    or relayed connection with less bandwidth than the file needs.
 *
 * The plan is a *list*, best first. It becomes the title's sources in that order, so if Direct Play
 * does not start after all - a decoder that claimed support and then failed - the player's own
 * fallback moves to Direct Stream and then Transcode without the viewer doing anything.
 */
internal enum class PlexPlaybackMode { DirectPlay, DirectStream, Transcode }

internal data class PlexPlaybackOption(
    val mode: PlexPlaybackMode,
    /** For Transcode: the bitrate asked for. */
    val maxBitrateKbps: Int? = null,
    /** For Transcode: the largest frame asked for, "1920x1080". */
    val maxResolution: String? = null,
)

/** What the file is, reduced to what the decision needs. */
internal data class PlexMediaFacts(
    val container: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val width: Int?,
    val height: Int?,
    val bitrateKbps: Int?,
    /**
     * Dolby Vision with nothing to fall back to - profile 5, which has no HDR10 or SDR base layer.
     * Without a Dolby Vision decoder it plays with the wrong colours, so it is not playable as it is.
     * Profiles 7 and 8 carry an HDR10 or SDR layer any decoder shows correctly, and are not this.
     */
    val dolbyVisionWithoutFallback: Boolean = false,
)

/** What this device can decode in hardware, codec to the largest frame, and which engine is chosen. */
internal data class PlexDeviceCaps(
    val hardwareVideo: Map<String, Pair<Int, Int>>,
    /** The player engine setting: "Auto", "ExoPlayer" or "MPV". */
    val engine: String,
)

internal object PlexPlaybackPlanner {
    /** What Plex's relay carries for an account without Plex Pass, and a safe ceiling for any. */
    const val RELAY_MAX_KBPS = 2_000
    private const val HIGH_TRANSCODE_KBPS = 20_000
    private const val LOW_TRANSCODE_KBPS = 4_000

    /** Containers Media3 opens itself. mpv opens effectively anything. */
    private val exoContainers = setOf("mp4", "m4v", "mov", "mkv", "webm", "ts", "mpegts", "m2ts", "flv", "ogg")

    /** Codecs mpv decodes comfortably in software at up to 1080p on a television's CPU. */
    private val softwareFriendly = setOf("h264", "mpeg2video", "mpeg4", "vc1", "msmpeg4v3", "vp8")

    /** The Dolby Vision decoder's key in PlexDeviceCapabilities.hardwareVideo. */
    private const val DOLBY_VISION = "dvhe"

    fun normaliseCodec(codec: String?): String? = when (val value = codec?.lowercase(Locale.US)?.trim()) {
        null, "" -> null
        "avc", "avc1", "h.264", "x264" -> "h264"
        "hevc", "h265", "h.265", "x265", "hvc1", "hev1" -> "hevc"
        "mpeg2", "mpeg-2" -> "mpeg2video"
        "vp09" -> "vp9"
        "av01" -> "av1"
        else -> value
    }

    private fun engineOpensAnyContainer(engine: String) = !engine.equals("ExoPlayer", ignoreCase = true)

    private fun videoPlayable(facts: PlexMediaFacts, caps: PlexDeviceCaps): Boolean {
        // mpv, chosen outright, reshapes Dolby Vision itself; Media3 and Automatic need the decoder.
        if (facts.dolbyVisionWithoutFallback && DOLBY_VISION !in caps.hardwareVideo && !caps.engine.equals("MPV", ignoreCase = true)) return false
        val codec = normaliseCodec(facts.videoCodec) ?: return true // audio-only or unknown: let it try
        val hardware = caps.hardwareVideo[codec]
        val width = facts.width ?: 0
        val height = facts.height ?: 0
        if (hardware != null) {
            val (maxWidth, maxHeight) = hardware
            // Rotated sources report a portrait frame; compare against either orientation.
            val fits = (width <= maxWidth && height <= maxHeight) || (width <= maxHeight && height <= maxWidth)
            if (fits || width == 0 || height == 0) return true
        }
        // No hardware path. mpv can still carry a modest file in software, which is what a TV
        // without, say, a VC-1 decoder has always relied on for these.
        return engineOpensAnyContainer(caps.engine) && codec in softwareFriendly && height in 1..1088
    }

    private fun containerPlayable(facts: PlexMediaFacts, caps: PlexDeviceCaps): Boolean {
        if (engineOpensAnyContainer(caps.engine)) return true
        val container = facts.container?.lowercase(Locale.US)?.trim() ?: return true
        return container.split(',').any { it.trim() in exoContainers }
    }

    /** The bandwidth this connection allows, or null for no limit. */
    fun bandwidthLimit(route: MediaServerRoute, remoteMaxKbps: Int?): Int? = when (route) {
        MediaServerRoute.Local -> null
        MediaServerRoute.Remote -> remoteMaxKbps
        MediaServerRoute.Relay -> minOf(remoteMaxKbps ?: RELAY_MAX_KBPS, RELAY_MAX_KBPS)
    }

    fun plan(
        facts: PlexMediaFacts,
        caps: PlexDeviceCaps,
        route: MediaServerRoute,
        remoteMaxKbps: Int?,
    ): List<PlexPlaybackOption> {
        val limit = bandwidthLimit(route, remoteMaxKbps)
        val fitsBandwidth = limit == null || facts.bitrateKbps == null || facts.bitrateKbps <= limit
        val video = videoPlayable(facts, caps)
        val container = containerPlayable(facts, caps)

        val transcodes = buildList {
            val first = limit?.coerceAtMost(HIGH_TRANSCODE_KBPS) ?: HIGH_TRANSCODE_KBPS
            add(PlexPlaybackOption(PlexPlaybackMode.Transcode, first, resolutionFor(first)))
            if (first > LOW_TRANSCODE_KBPS) add(PlexPlaybackOption(PlexPlaybackMode.Transcode, LOW_TRANSCODE_KBPS, resolutionFor(LOW_TRANSCODE_KBPS)))
        }
        return buildList {
            if (video && container && fitsBandwidth) add(PlexPlaybackOption(PlexPlaybackMode.DirectPlay))
            if (video && fitsBandwidth) add(PlexPlaybackOption(PlexPlaybackMode.DirectStream))
            addAll(transcodes)
        }
    }

    fun resolutionFor(kbps: Int): String = when {
        kbps >= 8_000 -> "1920x1080"
        kbps >= 3_000 -> "1280x720"
        kbps >= 1_500 -> "854x480"
        else -> "640x360"
    }

    /**
     * What the transcoder may produce for this device, as Plex's profile-extra directives.
     *
     * Without this the server assumes a generic client and re-encodes HEVC it could have copied.
     */
    fun profileExtra(caps: PlexDeviceCaps): String {
        val videoCodecs = buildList {
            add("h264")
            if ("hevc" in caps.hardwareVideo) add("hevc")
        }.joinToString(",")
        return "add-transcode-target(type=videoProfile&context=streaming&protocol=hls&container=mpegts" +
            "&videoCodec=$videoCodecs&audioCodec=aac,ac3,eac3&subtitleCodec=)"
    }
}

/** The request identity every call to a server carries. None of it is secret. */
internal data class PlexClientIdentity(
    val clientIdentifier: String,
    val product: String,
    val version: String,
    val platform: String,
    val deviceName: String,
) {
    fun headers(): Map<String, String> = mapOf(
        "X-Plex-Client-Identifier" to clientIdentifier,
        "X-Plex-Product" to product,
        "X-Plex-Version" to version,
        "X-Plex-Platform" to platform,
        "X-Plex-Device" to platform,
        "X-Plex-Device-Name" to deviceName,
    )
}

/** Playback URLs. Token-free by construction: the player sends the token as a header. */
internal object PlexPlaybackUrls {
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    fun directPlay(baseUri: String, partKey: String): String = baseUri + partKey

    fun universal(
        baseUri: String,
        ratingKey: String,
        option: PlexPlaybackOption,
        sessionId: String,
        identity: PlexClientIdentity,
        route: MediaServerRoute,
        profileExtra: String,
        /** Which of the title's versions, when it has more than one. */
        mediaIndex: Int = 0,
    ): String {
        val directStream = option.mode != PlexPlaybackMode.Transcode
        val params = linkedMapOf(
            "path" to "/library/metadata/$ratingKey",
            "mediaIndex" to mediaIndex.toString(),
            "partIndex" to "0",
            "protocol" to "hls",
            "fastSeek" to "1",
            "copyts" to "1",
            "directPlay" to "0",
            "directStream" to if (directStream) "1" else "0",
            "directStreamAudio" to "1",
            "videoQuality" to "100",
            "subtitles" to "auto",
            "location" to if (route == MediaServerRoute.Local) "lan" else "wan",
            "session" to sessionId,
            "X-Plex-Session-Identifier" to sessionId,
            "X-Plex-Client-Profile-Name" to "Generic",
            "X-Plex-Client-Profile-Extra" to profileExtra,
        )
        if (!directStream) {
            option.maxBitrateKbps?.let { params["maxVideoBitrate"] = it.toString() }
            option.maxResolution?.let { params["videoResolution"] = it }
        }
        params.putAll(identity.headers())
        val query = params.entries.joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }
        return "$baseUri/video/:/transcode/universal/start.m3u8?$query"
    }
}
