package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.streamdek.mobile.R

// SkyStream's settings controls and details dialog, kept out of StreamDekNativeApp.kt: that file's
// top-level class is at the JVM's size limit, and anything added to it can tip it over.

@Composable
internal fun SkySourceOptions(
  schema: SkySettingsSchema,
  address: String,
  onAddressChange: (String) -> Unit,
  subProvidersOn: Map<String, Boolean>,
  onSubProviderChange: (String, Boolean) -> Unit,
) {
  val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
  // Offered where SkyStream offers it: a plugin that declares an address setting, or whose
  // collection lists mirrors. Every manifest carries a baseUrl, but many never read it — a plugin
  // built on Stremio add-ons ships a placeholder — and an address box there changes nothing.
  if (schema.addressField != null || schema.domains.isNotEmpty()) {
    SettingsDivider()
    Text(schema.addressField?.label ?: stringResource(R.string.sky_settings_address), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(schema.addressField?.description ?: stringResource(R.string.sky_settings_address_hint), color = muted, style = MaterialTheme.typography.bodySmall)
    val effective = address.ifBlank { schema.defaultAddress }
    val choices = (listOfNotNull(schema.defaultAddress.takeIf { it.isNotBlank() }?.let { SkyDomain(it.substringAfter("//").trimEnd('/'), it.trimEnd('/')) }) + schema.domains).distinctBy { it.url }
    if (choices.size > 1) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { domain ->
          FilterChip(
            selected = effective.trimEnd('/') == domain.url,
            onClick = { onAddressChange(if (domain.url == schema.defaultAddress.trimEnd('/')) "" else domain.url) },
            label = { Text(domain.name) },
          )
        }
      }
    }
    OutlinedTextField(
      value = address,
      onValueChange = onAddressChange,
      label = { Text(stringResource(R.string.cloudstream_address)) },
      placeholder = schema.defaultAddress.takeIf { it.isNotBlank() }?.let { default -> { Text(default) } },
      supportingText = {
        Text(
          schema.defaultAddress.takeIf { it.isNotBlank() }?.let { stringResource(R.string.sky_settings_address_default_named, it) }
            ?: stringResource(R.string.sky_settings_address_default),
        )
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
  }
  if (schema.subProviders.isNotEmpty()) {
    SettingsDivider()
    val on = schema.subProviders.count { (id, _, default) -> subProvidersOn[id] ?: default }
    Text(stringResource(R.string.sky_settings_sub_providers), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.sky_settings_sub_providers_hint, on, schema.subProviders.size), color = muted, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(onClick = { schema.subProviders.forEach { (id, _, _) -> onSubProviderChange(id, true) } }) { Text(stringResource(R.string.sky_settings_all_on)) }
      TextButton(onClick = { schema.subProviders.forEach { (id, _, _) -> onSubProviderChange(id, false) } }) { Text(stringResource(R.string.sky_settings_all_off)) }
    }
    schema.subProviders.forEach { (id, name, default) ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(checked = subProvidersOn[id] ?: default, onCheckedChange = { onSubProviderChange(id, it) })
      }
    }
  }
}


/** A SkyStream collection or source: what it is, its address to copy or open, and what it holds. */
@Composable
internal fun SkyStreamDetailsDialog(
  name: String,
  description: String?,
  url: String,
  enabled: Boolean,
  providers: List<SkyProvider>,
  onDismiss: () -> Unit,
) {
  val single = providers.singleOrNull()?.takeIf { it.downloadUrl == url }
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = StreamDekRadius.panelShape, color = MaterialTheme.colorScheme.surface) {
      Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(if (single != null) R.string.sky_source_details else R.string.plugin_collection_details), style = MaterialTheme.typography.labelMedium)
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        DetailUrlRow(stringResource(R.string.cloudstream_address), url)
        DetailRow(stringResource(R.string.detail_fact_status), stringResource(if (enabled) R.string.state_on else R.string.settings_state_off))
        if (single != null) {
          DetailRow(stringResource(R.string.sky_detail_package), single.packageName)
          DetailRow(stringResource(R.string.sky_detail_version), single.version.toString())
          single.categories.takeIf { it.isNotEmpty() }?.let { DetailRow(stringResource(R.string.sky_detail_categories), it.joinToString(" / ")) }
          single.languages.takeIf { it.isNotEmpty() }?.let { DetailRow(stringResource(R.string.sky_detail_languages), it.joinToString(", ") { lang -> lang.uppercase() }) }
          single.authors.takeIf { it.isNotEmpty() }?.let { DetailRow(stringResource(R.string.sky_detail_authors), it.joinToString(", ")) }
          if (single.domains.isNotEmpty()) {
            Text(stringResource(R.string.sky_detail_mirrors), fontWeight = FontWeight.Bold)
            single.domains.forEach { DetailUrlRow(it.name, it.url) }
          }
        } else {
          Text(pluralStringResource(R.plurals.plugin_sources_count, providers.size, providers.size), fontWeight = FontWeight.Bold)
          providers.sortedBy { it.name.lowercase() }.forEach { provider ->
            Text(provider.name, fontWeight = FontWeight.SemiBold)
            Text(
              listOfNotNull(
                stringResource(R.string.settings_summary_version, provider.version.toString()),
                provider.categories.takeIf { it.isNotEmpty() }?.joinToString(" / "),
                if (provider.enabled) stringResource(R.string.state_on) else null,
              ).joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_close)) }
      }
    }
  }
}
