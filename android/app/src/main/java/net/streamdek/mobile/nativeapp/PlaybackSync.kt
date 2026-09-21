package net.streamdek.mobile.nativeapp

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.text.TextOutput
import kotlin.math.roundToLong

/**
 * Subtitle and audio timing: the ranges, the step arithmetic, and how ExoPlayer is made to honour
 * both - mpv has `sub-delay` and `audio-delay` of its own and needs none of this.
 */

/** How far subtitles can be moved either way, in seconds. Enough for a release cut differently. */
internal const val SUBTITLE_DELAY_LIMIT_SECONDS = 120.0

/**
 * How far audio can be moved either way, in seconds.
 *
 * Far smaller than the subtitle range on purpose: audio out of sync by more than a few seconds is a
 * different release, not a latency to correct, and past this ExoPlayer spends more time dropping
 * or holding frames to follow the offset than playing them.
 */
internal const val AUDIO_DELAY_LIMIT_SECONDS = 5.0

/**
 * [current] moved by [step] and held to ±[limit], rounded to the millisecond.
 *
 * Rounded because the steps are decimal and doubles are not: ten presses of +0.1 should read
 * "+1.0 s", not "+0.9999999 s", and should land exactly back on zero when undone.
 */
internal fun steppedDelay(current: Double, step: Double, limit: Double): Double =
  ((current + step) * 1000.0).roundToLong().div(1000.0).coerceIn(-limit, limit)

/**
 * The two offsets one ExoPlayer view applies, read on the playback thread as it renders.
 *
 * Positive means later, for both: subtitles shown later, audio heard later.
 */
internal class PlaybackOffsets {
  @Volatile var subtitleDelayUs: Long = 0L
  @Volatile var audioDelayUs: Long = 0L
}

/**
 * A text renderer that renders its cues as of a different moment than the one playing.
 *
 * Media3 has no subtitle offset. Its text renderer reads every subtitle sample as soon as it is
 * buffered - it does not wait for the playback position - and keeps them until they are due, so
 * asking it for the cues at `position - delay` is all a delay takes. That covers embedded text
 * tracks and broadcast captions alike, which previously ignored the delay entirely (only
 * subtitles loaded from a file, drawn by [ExoPlaybackView]'s own overlay, honoured it).
 *
 * One limit, and it is inherent: showing subtitles *earlier* needs cues from ahead of the playback
 * position, so an advance can reach no further than what has been buffered - usually tens of
 * seconds, and briefly nothing just after a seek.
 */
@OptIn(UnstableApi::class)
private class DelayedTextRenderer(renderer: Renderer, private val offsets: PlaybackOffsets) : ForwardingRenderer(renderer) {
  override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
    super.render(positionUs - offsets.subtitleDelayUs, elapsedRealtimeUs)
  }

  override fun getDurationToProgressUs(positionUs: Long, elapsedRealtimeUs: Long): Long =
    super.getDurationToProgressUs(positionUs - offsets.subtitleDelayUs, elapsedRealtimeUs)
}

/**
 * An audio sink whose clock runs [PlaybackOffsets.audioDelayUs] ahead of the sound it is playing.
 *
 * ExoPlayer times every video frame against the audio clock. Reporting that clock ahead of what is
 * actually audible shows each frame early relative to its sound - which is exactly audio played
 * later - and reporting it behind does the opposite. Nothing is re-decoded or re-buffered, so a
 * change takes effect at once: lowering the delay holds the picture until the sound catches up,
 * raising it lets the video renderer skip ahead.
 *
 * Tunneled playback is the exception. There the hardware locks video to audio itself and never
 * consults this clock, so the offset would do nothing - see [ExoPlaybackView.audioDelaySupported].
 */
@OptIn(UnstableApi::class)
private class DelayedAudioSink(sink: AudioSink, private val offsets: PlaybackOffsets) : ForwardingAudioSink(sink) {
  override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
    val positionUs = super.getCurrentPositionUs(sourceEnded)
    return if (positionUs == AudioSink.CURRENT_POSITION_NOT_SET) positionUs else positionUs + offsets.audioDelayUs
  }
}

/** Media3's own renderers, with the text renderers and the audio sink made to honour [offsets]. */
@OptIn(UnstableApi::class)
internal class SyncAdjustableRenderersFactory(
  context: Context,
  private val offsets: PlaybackOffsets,
) : DefaultRenderersFactory(context) {
  override fun buildAudioSink(
    context: Context,
    enableFloatOutput: Boolean,
    enableAudioOutputPlaybackParams: Boolean,
  ): AudioSink? = super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)?.let { DelayedAudioSink(it, offsets) }

  override fun buildTextRenderers(
    context: Context,
    output: TextOutput,
    outputLooper: Looper,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {
    val built = ArrayList<Renderer>()
    super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, built)
    built.mapTo(out) { DelayedTextRenderer(it, offsets) }
  }
}

/**
 * The audio delay every video starts with on this device.
 *
 * A property of what the sound comes out of - a Bluetooth headset or a soundbar that lags - rather
 * than of any one video, so it is kept on the device and never synced: the television in the next
 * room has different speakers. Adjusting the delay in the player changes only the video playing;
 * this is the starting point each new one gets. Kept at zero by default, so nothing changes for
 * anyone who never opens it.
 */
internal object AudioSyncOptions {
  private const val DEFAULT_DELAY_KEY = "default_audio_delay_ms"
  private val limitMs = (AUDIO_DELAY_LIMIT_SECONDS * 1000).toInt()

  /** Snapshot state, so the settings page redraws when it changes. */
  var defaultDelayMs by mutableIntStateOf(0)
    private set

  val defaultDelaySeconds: Double get() = defaultDelayMs / 1000.0

  fun initialize(context: Context) {
    defaultDelayMs = context.applicationContext.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
      .getInt(DEFAULT_DELAY_KEY, 0).coerceIn(-limitMs, limitMs)
  }

  fun setDefaultDelayMs(context: Context, delayMs: Int) {
    defaultDelayMs = delayMs.coerceIn(-limitMs, limitMs)
    context.applicationContext.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
      .edit().putInt(DEFAULT_DELAY_KEY, defaultDelayMs).apply()
  }
}
