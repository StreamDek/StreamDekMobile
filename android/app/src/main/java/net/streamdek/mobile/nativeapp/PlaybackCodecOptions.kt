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
  var dv7HevcFallback: Boolean = false
    private set

  @Volatile
  var tunneledPlayback: Boolean = false
    private set

  /** Seeds the in-memory copy the player reads. Safe to call more than once. */
  fun initialize(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
    dv7HevcFallback = prefs.getBoolean(DV7_HEVC_KEY, false)
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
 * Dolby Vision profile 7, and what can actually be done about it.
 *
 * Media3 copes with most Dolby Vision on a device that cannot decode it: when the display does not
 * report support, `MediaCodecVideoRenderer` asks for an alternative codec and
 * `MediaCodecUtil.getAlternativeCodecMimeType` answers HEVC for profiles 4 and 8, AVC for 9 and AV1
 * for 10. Profile 7 -- the dual-layer format disc remuxes use -- is the one profile with no answer.
 *
 * The failure is worse than a refusal. A television with a Dolby Vision decoder accepts a profile 7
 * stream, decodes it, reports a first frame and a healthy frame rate, and shows black. Nothing
 * errors, so the player's own "Media3 failed, try mpv" path never fires: it hangs off an error
 * callback, and there is no error.
 *
 * Handing the stream to an HEVC decoder instead was tried and is not reliable. The base layer is
 * ordinary HEVC and the extra NAL units are ones a conforming decoder must ignore, so in principle
 * it works -- but a hardware decoder is far less forgiving than a software one about NAL types it
 * did not expect, and on the hardware this was tested against it stayed black. mpv plays the same
 * file correctly, because ffmpeg decodes the base layer and skips the rest. So rather than trying
 * to talk MediaCodec into it, the stream is simply sent to the engine that already handles it.
 *
 * Detection is deliberately generous. Profile 7 when the codec string says so; also when the codec
 * string says nothing at all, which is the common case for Matroska, because a Dolby Vision stream
 * that reaches here with no readable profile is far more likely to be a disc remux than one of the
 * single-layer streaming profiles -- and those play correctly on a device with a Dolby Vision
 * decoder, which is not a device anyone turns this setting on for.
 */
@OptIn(UnstableApi::class)
internal object Dv7Hevc {
  private const val TAG = "StreamDekDv7"

  /** `dvhe.07`, as MediaCodec numbers it. */
  private const val PROFILE_DVHE_DTB = android.media.MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtb

  fun isDolbyVisionProfile7(format: Format): Boolean {
    if (format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION) return false
    val profile = MediaCodecUtil.getCodecProfileAndLevel(format)?.first
    return profile == null || profile == PROFILE_DVHE_DTB
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
