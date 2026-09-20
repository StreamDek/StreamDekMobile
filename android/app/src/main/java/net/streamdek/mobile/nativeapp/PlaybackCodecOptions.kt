package net.streamdek.mobile.nativeapp

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * Two decoder choices that belong to the device rather than to the account.
 *
 * Deliberately not synced. Whether a Dolby Vision stream needs mapping down, and whether tunneled
 * output helps or breaks, is a property of the silicon in front of the viewer — a phone and a
 * television box will not agree, and copying one's answer onto the other is how a working player
 * gets broken from another room. They are read at the moment a player is built, so a change takes
 * effect on the next thing played rather than needing a restart.
 */
object PlaybackCodecOptions {
  private const val DV7_HEVC_KEY = "dv7_hevc_fallback"
  private const val TUNNELED_KEY = "tunneled_playback"

  @Volatile
  var dv7HevcFallback: Boolean = true
    private set

  @Volatile
  var tunneledPlayback: Boolean = false
    private set

  /** Seeds the in-memory copy the player reads. Safe to call more than once. */
  fun initialize(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
    dv7HevcFallback = prefs.getBoolean(DV7_HEVC_KEY, true)
    tunneledPlayback = prefs.getBoolean(TUNNELED_KEY, false)
  }

  fun setDv7HevcFallback(context: Context, enabled: Boolean) {
    dv7HevcFallback = enabled
    context.applicationContext.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
      .edit().putBoolean(DV7_HEVC_KEY, enabled).apply()
  }

  fun setTunneledPlayback(context: Context, enabled: Boolean) {
    tunneledPlayback = enabled
    context.applicationContext.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
      .edit().putBoolean(TUNNELED_KEY, enabled).apply()
  }
}

/**
 * DV7 compatibility uses mpv/FFmpeg's HEVC path, not a MIME-type rewrite.
 * Unknown profiles are deliberately left to normal engine behavior. Capability reports are
 * advisory: a decoder failure can still trigger one fallback after native playback is selected.
 * HDR passthrough and tone mapping remain properties of the engine's actual output surface;
 * neither a codec profile nor a first-frame callback proves correct HDR or visible pixels.
 */
@OptIn(UnstableApi::class)
internal object Dv7Hevc {
  private const val TAG = "StreamDekDv7"

  /** `dvhe.07`, as MediaCodec numbers it. */
  private const val PROFILE_DVHE_DTB = android.media.MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtb

  fun isDolbyVisionProfile7(format: Format): Boolean {
    if (format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION) return false
    val profile = MediaCodecUtil.getCodecProfileAndLevel(format)?.first
    return profile == PROFILE_DVHE_DTB
  }

  /** Require the exact DV7 profile, stream limits, and Dolby Vision on this display. */
  fun supportsNativePlayback(format: Format, display: android.view.Display?): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 24 || display == null) return false
    return runCatching {
      val supportsDisplay = display.hdrCapabilities.supportedHdrTypes.contains(
        android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION,
      )
      supportsDisplay && MediaCodecUtil.getDecoderInfos(
        MimeTypes.VIDEO_DOLBY_VISION, format.drmInitData != null, false,
      ).any { decoder ->
        decoder.profileLevels.any { it.profile == PROFILE_DVHE_DTB } &&
          decoder.isFormatSupported(format)
      }
    }.getOrDefault(false)
  }

  /**
   * Everything worth knowing about a Dolby Vision stream, on one line.
   *
   * Logged for every such stream whatever the setting says, because the failure this exists for is
   * silent: without it there is nothing in the log to say what the stream was, and "why did it not
   * switch" cannot be answered after the fact.
   */
  fun describe(format: Format): String =
    "codecs=${format.codecs}" +
      " profile=${MediaCodecUtil.getCodecProfileAndLevel(format)?.first}" +
      " csdBuffers=${format.initializationData.size}" +
      " settingOn=${PlaybackCodecOptions.dv7HevcFallback}" +
      " profile7=${isDolbyVisionProfile7(format)}"

  fun log(message: String) = Log.i(TAG, message)
}
