package net.streamdek.mobile.nativeapp

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.streamdek.mobile.R

/**
 * Settings > Backup & Restore.
 *
 * The everyday path is two taps each way - Create Backup, choose where; Restore Backup, pick the
 * file, confirm - and everything else (the password, choosing what to restore, undoing) waits in a
 * dialog or a row below until it is wanted.
 */
@Composable
internal fun BackupRestoreSettings(controller: BackupRestoreController, activeProfileName: String) {
  val state by controller.state.collectAsState()
  val context = LocalContext.current
  var createDialogVisible by remember { mutableStateOf(false) }
  var undoConfirmVisible by remember { mutableStateOf(false) }
  var deleteConfirmVisible by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { controller.refresh() }

  val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
    if (uri != null) controller.createBackup(uri) else controller.cancelBackup()
  }
  // Any type: most file managers do not know a .streamdek file, and would grey it out otherwise.
  val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) controller.inspect(uri)
  }

  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    SettingsSection(stringResource(R.string.backup_section_backup)) {
      SettingsNavRow(
        "BAK", Color(0xFF22C55E),
        stringResource(R.string.backup_create_title),
        if (state.lastBackupAt > 0L) stringResource(R.string.backup_last_backup, formatBackupDate(state.lastBackupAt))
        else stringResource(R.string.backup_create_subtitle),
        onClick = { createDialogVisible = true },
      )
    }
    SettingsSection(stringResource(R.string.backup_section_restore)) {
      SettingsNavRow(
        "RES", Color(0xFF60A5FA),
        stringResource(R.string.backup_restore_title),
        stringResource(R.string.backup_restore_subtitle),
        onClick = { openLauncher.launch(arrayOf("*/*")) },
      )
    }
    state.recoveryPoint?.let { point ->
      SettingsSection(stringResource(R.string.backup_section_manage)) {
        val here = controller.canUndoHere()
        SettingsNavRow(
          "UND", Color(0xFFF59E0B),
          stringResource(R.string.backup_undo_title),
          if (here) stringResource(R.string.backup_undo_subtitle, point.profileName, formatBackupDate(point.createdAt))
          else stringResource(R.string.backup_undo_other_profile, point.profileName, formatBackupDate(point.createdAt)),
          onClick = { if (here) undoConfirmVisible = true },
        )
        SettingsDivider()
        SettingsNavRow(
          "DEL", Color(0xFF94A3B8),
          stringResource(R.string.backup_recovery_delete_title),
          stringResource(R.string.backup_recovery_delete_subtitle),
          onClick = { deleteConfirmVisible = true },
        )
      }
    }
    Text(
      stringResource(R.string.backup_never_included),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
      modifier = Modifier.padding(horizontal = 4.dp),
    )
  }

  if (createDialogVisible) {
    CreateBackupDialog(
      onDismiss = { createDialogVisible = false },
      onConfirm = { passphrase ->
        createDialogVisible = false
        controller.prepareBackup(passphrase)
        createLauncher.launch(suggestedBackupFileName())
      },
    )
  }
  if (undoConfirmVisible) {
    AlertDialog(
      onDismissRequest = { undoConfirmVisible = false },
      title = { Text(stringResource(R.string.backup_undo_confirm_title)) },
      text = { Text(stringResource(R.string.backup_undo_confirm_body)) },
      confirmButton = {
        TextButton(onClick = { undoConfirmVisible = false; controller.undoLastRestore() }) { Text(stringResource(R.string.backup_undo_action)) }
      },
      dismissButton = { TextButton(onClick = { undoConfirmVisible = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }

  if (deleteConfirmVisible) {
    // Deleting the recovery point is permanent: once it is gone, the last restore can no longer be undone.
    AlertDialog(
      onDismissRequest = { deleteConfirmVisible = false },
      title = { Text(stringResource(R.string.backup_recovery_delete_confirm_title)) },
      text = { Text(stringResource(R.string.backup_recovery_delete_confirm_body)) },
      confirmButton = {
        TextButton(onClick = { deleteConfirmVisible = false; controller.deleteRecoveryPoint() }) {
          Text(stringResource(R.string.backup_recovery_delete_action), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { deleteConfirmVisible = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }

  val progress = state.progress
  when {
    progress != null -> BackupProgressDialog(progress)
    state.pending != null -> RestorePreviewDialog(state.pending!!, activeProfileName, controller)
    state.created != null -> {
      val created = state.created!!
      BackupCreatedDialog(
        created = created,
        onShare = {
          val send = Intent(Intent.ACTION_SEND)
            .setType(BACKUP_MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, created.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          runCatching { context.startActivity(Intent.createChooser(send, null)) }
        },
        onDismiss = controller::dismiss,
      )
    }
    state.restored != null -> RestoreReportDialog(state.restored!!, controller::dismiss)
    state.undone -> MessageDialog(R.string.backup_undone_title, stringResource(R.string.backup_undone_body), controller::dismiss)
    state.error != null -> MessageDialog(R.string.backup_error_title, stringResource(state.error!!), controller::dismiss)
  }
}

/** The line under Backup & Restore on the Settings home page. */
@Composable
internal fun backupRestoreSummary(controller: BackupRestoreController): String {
  val state by controller.state.collectAsState()
  LaunchedEffect(Unit) { controller.refresh() }
  return if (state.lastBackupAt > 0L) stringResource(R.string.settings_summary_backup_last, formatBackupDate(state.lastBackupAt))
  else stringResource(R.string.settings_summary_backup_none)
}

/** `StreamDek-Backup-2026-09-18.streamdek`, the name the save picker starts with. */
internal fun suggestedBackupFileName(now: Long = System.currentTimeMillis()): String =
  "StreamDek-Backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) + "." + BACKUP_FILE_EXTENSION

/**
 * A backup password is its own secret, not the StreamDek sign-in: marked as a new password so
 * autofill does not offer the account's saved one here.
 */
private fun Modifier.backupPasswordField(): Modifier = semantics { contentType = ContentType.NewPassword }

private fun formatBackupDate(at: Long): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date(at))

@Composable
private fun CreateBackupDialog(onDismiss: () -> Unit, onConfirm: (CharArray?) -> Unit) {
  var protect by remember { mutableStateOf(true) }
  var password by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }
  val tooShort = password.length < BACKUP_MIN_PASSPHRASE_LENGTH
  val mismatch = confirm.isNotEmpty() && confirm != password
  val ready = !protect || (!tooShort && confirm == password)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.backup_create_title)) },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.backup_protect_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.backup_protect_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
          }
          Switch(checked = protect, onCheckedChange = { protect = it })
        }
        if (protect) {
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.backup_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = if (password.isNotEmpty() && tooShort) {
              { Text(stringResource(R.string.backup_password_too_short, BACKUP_MIN_PASSPHRASE_LENGTH)) }
            } else null,
            modifier = Modifier.fillMaxWidth().backupPasswordField(),
          )
          OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text(stringResource(R.string.backup_password_confirm)) },
            singleLine = true,
            isError = mismatch,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = if (mismatch) { { Text(stringResource(R.string.backup_password_mismatch)) } } else null,
            modifier = Modifier.fillMaxWidth().backupPasswordField(),
          )
          Text(stringResource(R.string.backup_password_forget_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        } else {
          Text(stringResource(R.string.backup_unprotected_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = ready, onClick = { onConfirm(if (protect) password.toCharArray() else null) }) {
        Text(stringResource(R.string.backup_choose_location))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

@Composable
private fun BackupProgressDialog(message: Int) {
  AlertDialog(
    onDismissRequest = {},
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    confirmButton = {},
    text = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(stringResource(message))
      }
    },
  )
}

@Composable
private fun SummaryLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
    Text(value, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun BackupSummaryLines(summary: BackupSummary) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (summary.createdAt > 0L) SummaryLine(stringResource(R.string.backup_summary_created), formatBackupDate(summary.createdAt))
    if (summary.appVersion.isNotBlank()) SummaryLine(stringResource(R.string.backup_summary_app_version), summary.appVersion)
    SummaryLine(
      stringResource(R.string.backup_summary_from),
      stringResource(if (summary.platform == BACKUP_PLATFORM_MOBILE) R.string.backup_platform_mobile else R.string.backup_platform_other),
    )
    SummaryLine(stringResource(R.string.backup_summary_profiles), summary.profiles.toString())
    SummaryLine(stringResource(R.string.backup_summary_addons), summary.addons.toString())
    SummaryLine(stringResource(R.string.backup_summary_repositories), summary.pluginRepositories.toString())
    SummaryLine(stringResource(R.string.backup_summary_plugins), summary.pluginSources.toString())
    SummaryLine(stringResource(R.string.backup_summary_playlists), summary.playlists.toString())
    SummaryLine(stringResource(R.string.backup_summary_settings), summary.settings.toString())
    SummaryLine(stringResource(R.string.backup_summary_library), summary.libraryItems.toString())
    if (summary.credentials > 0) SummaryLine(stringResource(R.string.backup_summary_credentials), summary.credentials.toString())
    SummaryLine(stringResource(R.string.backup_summary_protected), stringResource(if (summary.encrypted) R.string.backup_yes else R.string.backup_no))
  }
}

@Composable
private fun BackupCreatedDialog(created: BackupCreated, onShare: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.backup_created_title)) },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackupSummaryLines(created.summary)
        if (created.leftOut.isNotEmpty()) {
          Text(stringResource(R.string.backup_left_out_title), fontWeight = FontWeight.SemiBold)
          Text(created.leftOut.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    dismissButton = { TextButton(onClick = onShare) { Text(stringResource(R.string.backup_share)) } },
  )
}

/** Restorable groups in the order they are offered; credentials only when the backup has any. */
private fun restorableCategories(summary: BackupSummary): List<BackupCategory> =
  BackupCategory.values().filter { it != BackupCategory.Credentials || summary.credentials > 0 }

@Composable
private fun categoryLabel(category: BackupCategory): String = stringResource(
  when (category) {
    BackupCategory.Addons -> R.string.backup_category_addons
    BackupCategory.Plugins -> R.string.backup_category_plugins
    BackupCategory.Playlists -> R.string.backup_category_playlists
    BackupCategory.Playback -> R.string.backup_category_playback
    BackupCategory.Appearance -> R.string.backup_category_appearance
    BackupCategory.General -> R.string.backup_category_general
    BackupCategory.Library -> R.string.backup_category_library
    BackupCategory.Credentials -> R.string.backup_category_credentials
  },
)

@Composable
private fun RestorePreviewDialog(pending: PendingRestore, activeProfileName: String, controller: BackupRestoreController) {
  var passphrase by remember { mutableStateOf("") }
  var custom by remember { mutableStateOf(false) }
  val available = remember(pending.summary) { restorableCategories(pending.summary) }
  var chosen by remember(pending.summary) { mutableStateOf(available.toSet()) }
  AlertDialog(
    onDismissRequest = controller::dismiss,
    title = { Text(stringResource(R.string.backup_restore_dialog_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        BackupSummaryLines(pending.summary)
        if (!pending.unlocked) {
          Text(stringResource(R.string.backup_unlock_prompt), fontWeight = FontWeight.SemiBold)
          OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(R.string.backup_password)) },
            singleLine = true,
            isError = pending.error != null,
            supportingText = pending.error?.let { { Text(stringResource(it)) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().backupPasswordField(),
          )
        } else {
          val profiles = pending.profiles.orEmpty()
          if (profiles.size > 1) {
            Text(stringResource(R.string.backup_restore_from), fontWeight = FontWeight.SemiBold)
            profiles.forEachIndexed { index, name ->
              Row(
                modifier = Modifier.fillMaxWidth().clickable { controller.chooseSourceProfile(index) },
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(selected = pending.sourceIndex == index, onClick = { controller.chooseSourceProfile(index) })
                Text(name.ifBlank { stringResource(R.string.profile_default_name) })
              }
            }
          }
          Text(stringResource(R.string.backup_restore_into, activeProfileName), style = MaterialTheme.typography.bodyMedium)
          ModeRow(!custom, R.string.backup_mode_complete, R.string.backup_mode_complete_subtitle) { custom = false }
          ModeRow(custom, R.string.backup_mode_custom, R.string.backup_mode_custom_subtitle) { custom = true }
          if (custom) {
            available.forEach { category ->
              Row(
                modifier = Modifier.fillMaxWidth().clickable { chosen = if (category in chosen) chosen - category else chosen + category },
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(checked = category in chosen, onCheckedChange = { chosen = if (it) chosen + category else chosen - category })
                Text(categoryLabel(category))
              }
            }
          }
          Text(stringResource(R.string.backup_restore_safety), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
      }
    },
    confirmButton = {
      if (!pending.unlocked) {
        TextButton(enabled = passphrase.isNotEmpty(), onClick = { controller.unlock(passphrase.toCharArray()) }) { Text(stringResource(R.string.backup_unlock)) }
      } else {
        TextButton(
          enabled = !custom || chosen.isNotEmpty(),
          onClick = { controller.restore(if (custom) chosen else available.toSet()) },
        ) { Text(stringResource(R.string.backup_restore_action)) }
      }
    },
    dismissButton = { TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

@Composable
private fun ModeRow(selected: Boolean, title: Int, subtitle: Int, onSelect: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
    RadioButton(selected = selected, onClick = onSelect)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(stringResource(title), fontWeight = FontWeight.SemiBold)
      Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
  }
}

@Composable
private fun RestoreReportDialog(report: RestoreReport, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.backup_restored_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(stringResource(R.string.backup_restored_body, report.profileName))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          SummaryLine(stringResource(R.string.backup_summary_settings), report.settings.toString())
          SummaryLine(stringResource(R.string.backup_summary_addons), report.addons.toString())
          SummaryLine(stringResource(R.string.backup_summary_plugins), report.pluginSources.toString())
          SummaryLine(stringResource(R.string.backup_summary_playlists), report.playlists.toString())
          SummaryLine(stringResource(R.string.backup_summary_library), report.libraryItems.toString())
          if (report.credentials > 0) SummaryLine(stringResource(R.string.backup_summary_credentials), report.credentials.toString())
        }
        val attention = buildList {
          if (report.needsSettingUp.isNotEmpty()) add(stringResource(R.string.backup_attention_left_out, report.needsSettingUp.joinToString(", ")))
          if (report.addonsNotRestored.isNotEmpty()) add(stringResource(R.string.backup_attention_addons_failed, report.addonsNotRestored.joinToString(", ")))
          if (report.pluginRepositoriesNotReached.isNotEmpty()) add(stringResource(R.string.backup_attention_repos_failed, report.pluginRepositoriesNotReached.joinToString(", ")))
          if (report.otherPlatform != null) add(stringResource(R.string.backup_attention_platform))
          if (report.skippedSettings > 0) add(stringResource(R.string.backup_attention_skipped, report.skippedSettings))
          if (report.damagedSections.isNotEmpty()) add(stringResource(R.string.backup_attention_damaged, report.damagedSections.joinToString(", ")))
          if (report.otherProfilesInBackup.isNotEmpty()) add(stringResource(R.string.backup_attention_other_profiles, report.otherProfilesInBackup.joinToString(", ")))
          add(stringResource(R.string.backup_attention_sign_in))
        }
        Text(stringResource(R.string.backup_attention_title), fontWeight = FontWeight.SemiBold)
        attention.forEach { line -> Text("• $line", style = MaterialTheme.typography.bodySmall) }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
  )
}

@Composable
private fun MessageDialog(title: Int, body: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(title)) },
    text = { Text(body) },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
  )
}
