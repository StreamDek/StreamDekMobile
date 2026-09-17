package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.streamdek.mobile.R

// The Theme picker in Settings → Appearance. Kept out of StreamDekNativeApp.kt, whose file class is
// already at the JVM's size limit.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemePresetPicker(selected: AppThemePreset, onSelected: (AppThemePreset) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 10.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      SettingsIcon("TH", LocalStreamDekThemeColors.current.accent)
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.settings_theme_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
      }
    }
    // Two across on a phone, more on a wider screen, so each preview is big enough to read.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val gap = 10.dp
      val columns = when {
        maxWidth >= 720.dp -> 4
        maxWidth >= 480.dp -> 3
        else -> 2
      }
      val cardWidth = (maxWidth - gap * (columns - 1)) / columns
      FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
        AppThemePreset.entries.forEach { preset ->
          ThemePresetCard(preset = preset, selected = preset == selected, modifier = Modifier.width(cardWidth), onClick = { onSelected(preset) })
        }
      }
    }
  }
}

/**
 * One theme, shown doing what a theme does rather than as a swatch.
 *
 * The preview is split: the theme in Dark on one side, in Light on the other, each with a play
 * button in the accent, a progress bar, a selected chip and a toggle. So the viewer sees the theme's
 * primary and highlight tones, and how it carries across appearances, before choosing it. The card
 * picks up the theme's own selected border once chosen, and a check mark says so without relying on
 * colour alone.
 */
@Composable
private fun ThemePresetCard(preset: AppThemePreset, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val current = LocalStreamDekThemeColors.current
  val own = streamDekThemeColors(preset, current.darkMode)
  val label = stringResource(preset.labelRes)
  Column(
    modifier = modifier
      .clip(StreamDekRadius.cardShape)
      .background(if (selected) own.selectedContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
      .border(if (selected) 1.5.dp else 1.dp, if (selected) own.selectedBorder else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), StreamDekRadius.cardShape)
      .semantics(mergeDescendants = true) { this.selected = selected }
      .clickable(onClickLabel = label, onClick = onClick)
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().height(76.dp).clip(StreamDekRadius.thumbShape)
        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), StreamDekRadius.thumbShape),
    ) {
      ThemePreviewHalf(preset = preset, darkMode = true, modifier = Modifier.weight(1f).fillMaxHeight())
      ThemePreviewHalf(preset = preset, darkMode = false, modifier = Modifier.weight(1f).fillMaxHeight())
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(preset.taglineRes), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      if (selected) {
        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(own.accent), contentAlignment = Alignment.Center) {
          Icon(Icons.Rounded.Check, contentDescription = null, tint = own.onAccent, modifier = Modifier.size(15.dp))
        }
      }
    }
  }
}

/** Half of a theme preview: the theme's own tones over StreamDek's neutral ground for [darkMode]. */
@Composable
private fun ThemePreviewHalf(preset: AppThemePreset, darkMode: Boolean, modifier: Modifier = Modifier) {
  val colors = streamDekThemeColors(preset, darkMode)
  val ground = if (darkMode) StreamDekNeutrals.darkSurface else StreamDekNeutrals.lightBackground
  val ink = if (darkMode) StreamDekNeutrals.darkOnSurface else StreamDekNeutrals.lightOnSurface
  Column(
    modifier = modifier.background(ground).padding(horizontal = 8.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
      Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
      }
      Box(
        modifier = Modifier.clip(StreamDekRadius.pill).background(colors.chipSelected).padding(horizontal = 6.dp, vertical = 2.dp),
      ) {
        Box(modifier = Modifier.width(14.dp).height(4.dp).clip(StreamDekRadius.pill).background(colors.onChipSelected))
      }
    }
    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(StreamDekRadius.pill).background(colors.progressTrack)) {
      Box(modifier = Modifier.fillMaxWidth(0.62f).fillMaxHeight().clip(StreamDekRadius.pill).background(colors.progress))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Box(modifier = Modifier.width(22.dp).height(4.dp).clip(StreamDekRadius.pill).background(ink.copy(alpha = 0.34f)))
      Box(modifier = Modifier.width(20.dp).height(11.dp).clip(StreamDekRadius.pill).background(colors.toggleTrack), contentAlignment = Alignment.CenterEnd) {
        Box(modifier = Modifier.padding(end = 2.dp).size(7.dp).clip(CircleShape).background(colors.toggleThumb))
      }
    }
  }
}
