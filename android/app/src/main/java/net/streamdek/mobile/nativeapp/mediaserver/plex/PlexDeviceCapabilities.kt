package net.streamdek.mobile.nativeapp.mediaserver.plex

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * The video this device decodes in hardware, and how large a frame each decoder takes.
 *
 * Read once from MediaCodecList and kept: it cannot change while the app runs, and walking the
 * codec list costs tens of milliseconds on a stick. Software decoders are left out on purpose -
 * "can decode" and "can decode 4K HEVC at 24 frames a second" are very different answers, and the
 * planner handles the software case itself.
 */
internal object PlexDeviceCapabilities {
    private val mimeToCodec = mapOf(
        "video/avc" to "h264",
        "video/hevc" to "hevc",
        "video/x-vnd.on2.vp9" to "vp9",
        "video/x-vnd.on2.vp8" to "vp8",
        "video/av01" to "av1",
        "video/mpeg2" to "mpeg2video",
        "video/mp4v-es" to "mpeg4",
        "video/wvc1" to "vc1",
        "video/dolby-vision" to "dvhe",
    )

    @Volatile private var cached: Map<String, Pair<Int, Int>>? = null

    fun hardwareVideo(): Map<String, Pair<Int, Int>> = cached ?: synchronized(this) {
        cached ?: read().also { cached = it }
    }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated && !info.isAlias
        val name = info.name.lowercase()
        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains(".sw."))
    }

    private fun read(): Map<String, Pair<Int, Int>> = runCatching {
        val result = HashMap<String, Pair<Int, Int>>()
        for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
            if (info.isEncoder || !isHardware(info)) continue
            for (mime in info.supportedTypes) {
                val codec = mimeToCodec[mime.lowercase()] ?: continue
                val capabilities = runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() ?: continue
                val width = capabilities.supportedWidths.upper
                val height = capabilities.supportedHeights.upper
                val existing = result[codec]
                if (existing == null || width.toLong() * height > existing.first.toLong() * existing.second) {
                    result[codec] = width to height
                }
            }
        }
        result as Map<String, Pair<Int, Int>>
    }.getOrDefault(mapOf("h264" to (1920 to 1080)))
}
