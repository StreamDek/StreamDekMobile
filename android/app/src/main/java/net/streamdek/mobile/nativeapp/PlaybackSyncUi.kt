package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToLong
import net.streamdek.mobile.R

/**
 * The player's two timing controls - subtitle delay and audio delay - which share one shape.
 *
 * The value is said twice: as a signed number, and in words ("Subtitles appear 1.5 s later"),
 * because which way a negative delay moves things is exactly what nobody remembers. The slider is
 * for getting close in one drag across the whole range; the step buttons are for the last few
 * tenths, and for the long jumps a two-minute range needs without dozens of presses.
 */

/** "+1.5", "−0.25", "0.0" - always signed, in the interface language's own digits and separator. */
@Composable
internal fun signedDelay(seconds: Double, decimals: Int): String {
  val magnitude = AppFormats.number(LocalAppLanguage.current, abs(seconds), decimals)
  return when {
    seconds > 0 -> "+$magnitude"
    seconds < 0 -> "−$magnitude"
    else -> magnitude
  }
}

/** The fewest decimals that show [step] exactly: 10 -> 0, 0.5 -> 1, 0.05 -> 2. */
private fun decimalsOf(step: Double): Int {
  var decimals = 0
  var scaled = step
  while (decimals < 3 && abs(scaled - Math.rint(scaled)) > 1e-9) {
    scaled *= 10
    decimals += 1
  }
  return decimals
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerDelayControl(
  title: String,
  valueSeconds: Double,
  limitSeconds: Double,
  /** Largest first; the smallest is also what the slider snaps to. */
  steps: List<Double>,
  decimals: Int,
  meaning: String,
  scopeNote: String,
  onChange: (Double) -> Unit,
) {
  val finest = steps.last()
  Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      Text(
        stringResource(R.string.player_delay_seconds, signedDelay(valueSeconds, decimals)),
        color = if (valueSeconds == 0.0) Color.White.copy(alpha = 0.72f) else Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
      )
    }
    Text(meaning, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
    Slider(
      value = valueSeconds.toFloat(),
      valueRange = -limitSeconds.toFloat()..limitSeconds.toFloat(),
      onValueChange = { raw -> onChange(((raw / finest).roundToLong() * finest).let { steppedDelay(it, 0.0, limitSeconds) }) },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      (steps.map { -it } + steps.reversed()).forEach { step ->
        DelayChip(stringResource(R.string.player_delay_seconds, signedDelay(step, decimalsOf(step)))) {
          onChange(steppedDelay(valueSeconds, step, limitSeconds))
        }
      }
      DelayChip(stringResource(R.string.player_delay_reset), enabled = valueSeconds != 0.0, emphasised = true) { onChange(0.0) }
    }
    Text(scopeNote, color = Color.White.copy(alpha = 0.52f), fontSize = 11.5.sp)
  }
}

@Composable
private fun DelayChip(label: String, enabled: Boolean = true, emphasised: Boolean = false, onClick: () -> Unit) {
  Text(
    label,
    color = when {
      !enabled -> Color.White.copy(alpha = 0.32f)
      emphasised -> Color.Black
      else -> Color.White
    },
    fontWeight = FontWeight.SemiBold,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier
      .clip(StreamDekRadius.pill)
      .background(if (emphasised && enabled) Color.White else Color.White.copy(alpha = 0.10f))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 9.dp),
  )
}

/** Subtitles: ±120 s, in steps of 10 s, 1 s and 0.1 s. */
@Composable
internal fun SubtitleDelayControl(valueSeconds: Double, onChange: (Double) -> Unit) {
  val amount = AppFormats.number(LocalAppLanguage.current, abs(valueSeconds), 1)
  PlayerDelayControl(
    title = stringResource(R.string.player_subtitle_delay_title),
    valueSeconds = valueSeconds,
    limitSeconds = SUBTITLE_DELAY_LIMIT_SECONDS,
    steps = listOf(10.0, 1.0, 0.1),
    decimals = 1,
    meaning = when {
      valueSeconds > 0 -> stringResource(R.string.player_subtitle_delay_later, amount)
      valueSeconds < 0 -> stringResource(R.string.player_subtitle_delay_earlier, amount)
      else -> stringResource(R.string.player_subtitle_delay_none)
    },
    scopeNote = stringResource(R.string.player_subtitle_delay_scope),
    onChange = onChange,
  )
}

/**
 * Audio: ±5 s, in steps of 0.5 s and 0.05 s.
 *
 * When the engine cannot move the sound for what is playing, the control is replaced by the reason
 * rather than drawn disabled: a slider that moves and changes nothing is worse than none.
 */
@Composable
internal fun AudioDelayControl(valueSeconds: Double, supported: Boolean, onChange: (Double) -> Unit) {
  if (!supported) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.player_audio_delay), color = Color.White, fontWeight = FontWeight.Bold)
      Text(stringResource(R.string.player_audio_delay_unavailable_tunneled), color = Color.White.copy(alpha = 0.64f), style = MaterialTheme.typography.bodySmall)
    }
    return
  }
  val amount = AppFormats.number(LocalAppLanguage.current, abs(valueSeconds), 2)
  PlayerDelayControl(
    title = stringResource(R.string.player_audio_delay),
    valueSeconds = valueSeconds,
    limitSeconds = AUDIO_DELAY_LIMIT_SECONDS,
    steps = listOf(0.5, 0.05),
    decimals = 2,
    meaning = when {
      valueSeconds > 0 -> stringResource(R.string.player_audio_delay_later, amount)
      valueSeconds < 0 -> stringResource(R.string.player_audio_delay_earlier, amount)
      else -> stringResource(R.string.player_audio_delay_none)
    },
    scopeNote = stringResource(R.string.player_audio_delay_scope),
    onChange = onChange,
  )
}
