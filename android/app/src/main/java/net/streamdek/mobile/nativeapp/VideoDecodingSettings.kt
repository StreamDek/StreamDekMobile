package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import net.streamdek.mobile.R

/**
 * Settings > Playback > Video Decoding.
 *
 * Four switches that were previously split across two pages: the Dolby Vision and tunnelling
 * toggles sat under Streams and Quality, whose own description covers choosing and labelling
 * streams and says nothing about decoders, while the two mpv rows sat under Player, whose
 * description promised "what to try when a video will not play" for settings that were half
 * somewhere else. Neither had anything to do with *which* stream is chosen - only with what this
 * device does with the one it already has.
 *
 * What earns them a page of their own rather than a section on either: every one of them is a
 * property of this device's hardware, and nothing else under Settings is. The rest follows the
 * account onto whatever the viewer signs in on next; a television's decoder has nothing useful to
 * say about what a phone should do, so these stay where they were set. The page subtitle is the
 * only place that distinction is visible, and it could not be made from inside a section.
 *
 * It lives in its own file because [StreamDekNativeApp]'s file-level facade class is at the JVM's
 * class size limit - adding this page to it fails the build with "Class too large". Every value it
 * draws is passed in rather than taken from the whole UI state, so nothing here has to grow when
 * that state does.
 */
internal fun LazyListScope.videoDecodingSettings(
  dv7HevcFallback: Boolean,
  onDv7HevcFallbackChange: (Boolean) -> Unit,
  tunneledPlayback: Boolean,
  onTunneledPlaybackChange: (Boolean) -> Unit,
  decoderMode: String,
  onDecoderModeChange: (String) -> Unit,
  renderSurface: String,
  onRenderSurfaceChange: (String) -> Unit,
) {
  item {
    SettingsSection(stringResource(R.string.settings_m_decoding)) {
      SettingsSwitchRow(
        "DV7", Color(0xFF8B5CF6), stringResource(R.string.settings_m_dv7_hevc_fallback),
        stringResource(R.string.settings_m_dolby_vision_profile_7_files_mostly_disc),
        dv7HevcFallback, onDv7HevcFallbackChange,
      )
      SettingsDivider()
      SettingsSwitchRow(
        "TUN", Color(0xFF14B8A6), stringResource(R.string.settings_m_tunneled_playback),
        stringResource(R.string.settings_m_let_the_hardware_decode_and_display_as),
        tunneledPlayback, onTunneledPlaybackChange,
      )
    }
  }
  item {
    // Read from and written to PlaybackCodecOptions directly rather than carried through the UI
    // state: it is a device choice the player reads when it is built, and nothing else on screen
    // depends on it, so the facade class that is already at its size limit does not have to grow.
    val context = LocalContext.current
    var bufferSeconds by remember { mutableIntStateOf(PlaybackCodecOptions.forwardBufferSeconds) }
    val oneMinute = stringResource(R.string.settings_m_buffer_ahead_one_minute)
    val minutes = PlaybackCodecOptions.forwardBufferOptions.associate { seconds ->
      seconds.toString() to if (seconds == 60) oneMinute else stringResource(R.string.settings_m_buffer_ahead_minutes, seconds / 60)
    }
    SettingsSection(stringResource(R.string.settings_m_buffering)) {
      SettingsChoiceRow(
        "BUF", Color(0xFFF59E0B),
        stringResource(R.string.settings_m_buffer_ahead),
        stringResource(R.string.settings_m_buffer_ahead_description),
        PlaybackCodecOptions.forwardBufferOptions.map(Int::toString), bufferSeconds.toString(),
        optionLabel = { value -> minutes[value] ?: value },
        onSelected = { value ->
          val seconds = value.toIntOrNull() ?: PlaybackCodecOptions.DEFAULT_FORWARD_BUFFER_SECONDS
          PlaybackCodecOptions.setForwardBufferSeconds(context.applicationContext, seconds)
          bufferSeconds = PlaybackCodecOptions.forwardBufferSeconds
        },
      )
    }
  }
  item {
    // Last on the page: only worth opening when something will not play, and only while mpv is
    // the engine - which both rows say for themselves.
    SettingsSection(stringResource(R.string.settings_m_if_a_video_will_not_play)) {
      SettingsChoiceRow(
        "HW", Color(0xFFA78BFA),
        stringResource(R.string.settings_row_mpv_video_compatibility),
        stringResource(R.string.settings_m_used_when_mpv_is_selected_or_automatic),
        listOf("HW+", "HW", "SW"), decoderMode,
        onSelected = onDecoderModeChange,
        choice = SettingsChoice.MpvVideoCompatibility,
      )
      SettingsDivider()
      SettingsChoiceRow(
        "SF", Color(0xFF06B6D4),
        stringResource(R.string.settings_row_mpv_display),
        stringResource(R.string.settings_m_used_when_mpv_is_selected_or_automatic_162),
        listOf("Standard", "Compatibility"), renderSurface,
        onSelected = onRenderSurfaceChange,
        choice = SettingsChoice.MpvDisplay,
      )
    }
  }
}
