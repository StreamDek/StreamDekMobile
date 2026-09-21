package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import net.streamdek.mobile.R

/**
 * Settings > Audio, and the timing section of Settings > Subtitles.
 *
 * Audio used to live under a "Subtitle and Audio" heading on the Subtitles page, which is not where
 * anyone looks for the spoken language. It has its own page now, beside Subtitles under Playback,
 * with the language choices moved across unchanged - same rows, same stored values.
 *
 * In their own file because the settings screen's generated method is within a few hundred bytes
 * of the JVM's 64KB ceiling; see [videoDecodingSettings] for the same arrangement.
 */
internal fun LazyListScope.audioSettings(
  preferredAudioLanguage: String,
  secondaryAudioLanguage: String,
  onPreferredAudioLanguageChange: (String) -> Unit,
  onSecondaryAudioLanguageChange: (String) -> Unit,
  tunneledPlayback: Boolean,
) {
  item {
    SettingsSection(stringResource(R.string.settings_section_audio_language)) {
      LanguageChoiceRow(
        "AUD",
        Color(0xFFF59E0B),
        stringResource(R.string.settings_row_preferred_audio_language),
        stringResource(R.string.settings_m_the_spoken_language_to_choose_when_a),
        Languages.audioOptions(),
        preferredAudioLanguage,
        onPreferredAudioLanguageChange,
      )
      SettingsDivider()
      LanguageChoiceRow(
        "AUD2",
        Color(0xFFFBBF24),
        stringResource(R.string.settings_row_secondary_audio_language),
        stringResource(R.string.settings_m_used_when_a_release_carries_nothing_in),
        listOf(Languages.NONE) + Languages.all.map { it.code },
        secondaryAudioLanguage,
        onSecondaryAudioLanguageChange,
      )
    }
  }
  item {
    SettingsSection(stringResource(R.string.settings_section_audio_sync)) {
      DefaultAudioDelayRow(tunneledPlayback)
    }
  }
}

/**
 * Where subtitle timing is adjusted, said on the page people look for it.
 *
 * There is deliberately no saved subtitle delay. A delay corrects one subtitle file against one
 * release; applied to everything, it would put every correctly timed subtitle out by the same
 * amount. So the control is in the player, and this says so.
 */
internal fun LazyListScope.subtitleTimingSettings() {
  item {
    SettingsSection(stringResource(R.string.settings_section_subtitle_timing)) {
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        SettingsIcon("SYN", Color(0xFF38BDF8))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            stringResource(R.string.player_subtitle_delay_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          SettingsSubtitle(stringResource(R.string.settings_subtitle_timing_detail), collapsedMaxLines = 6)
        }
      }
    }
  }
}

/** The default audio delay: a signed value, steps either way, and Reset. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultAudioDelayRow(tunneledPlayback: Boolean) {
  val context = LocalContext.current
  val delayMs = AudioSyncOptions.defaultDelayMs
  val seconds = delayMs / 1000.0
  Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
      SettingsIcon("DLY", Color(0xFF22C55E))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(R.string.settings_row_default_audio_delay),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          Text(
            stringResource(R.string.player_delay_seconds, signedDelay(seconds, 2)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (delayMs == 0) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
          )
        }
        val amount = AppFormats.number(LocalAppLanguage.current, abs(seconds), 2)
        SettingsSubtitle(
          when {
            delayMs > 0 -> stringResource(R.string.player_audio_delay_later, amount)
            delayMs < 0 -> stringResource(R.string.player_audio_delay_earlier, amount)
            else -> stringResource(R.string.player_audio_delay_none)
          },
        )
        SettingsSubtitle(stringResource(R.string.settings_row_default_audio_delay_detail), collapsedMaxLines = 5)
      }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(-0.5, -0.05, 0.05, 0.5).forEach { step ->
        OutlinedButton(onClick = {
          val next = steppedDelay(seconds, step, AUDIO_DELAY_LIMIT_SECONDS)
          AudioSyncOptions.setDefaultDelayMs(context, (next * 1000).roundToInt())
        }) {
          Text(stringResource(R.string.player_delay_seconds, signedDelay(step, if (abs(step) < 0.1) 2 else 1)))
        }
      }
      FilledTonalButton(onClick = { AudioSyncOptions.setDefaultDelayMs(context, 0) }, enabled = delayMs != 0) {
        Text(stringResource(R.string.player_delay_reset))
      }
    }
    if (tunneledPlayback) {
      SettingsSubtitle(stringResource(R.string.settings_audio_delay_tunneled_note), collapsedMaxLines = 4)
    }
  }
}
