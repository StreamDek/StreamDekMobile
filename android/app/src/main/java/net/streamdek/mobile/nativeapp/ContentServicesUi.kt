package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import net.streamdek.mobile.R

/**
 * Content Services, as the viewer meets them: two service cards, one key each, and a choice about
 * where the key is kept.
 *
 * Kept out of the settings monolith because it is one coherent experience that appears in three
 * places — the settings page, the setup prompt on first run, and the small nudge that shows up
 * where a missing key is actually costing something. All three draw the same cards from the same
 * state, so a service can never look connected in one place and absent in another.
 *
 * None of these composables know how a key is stored, validated or resolved. They call back to
 * [ContentServiceActions] and render [ContentServicesState]; every decision lives in
 * ServiceCredentials.kt and, above it, the view model.
 */

// ── Callbacks ─────────────────────────────────────────────────────────────────────────────────

/**
 * Everything the cards can ask for.
 *
 * Deliberately explicit about scope: `onRemove` takes a [CredentialRemoval] rather than guessing,
 * because "take this off my phone" and "delete it from my account, and therefore my television"
 * are different requests and the viewer is asked which they mean.
 */
data class ContentServiceActions(
  val onSubmitKey: (ContentService, String, StorageChoice) -> Unit,
  val onCopyDeviceKeyToAccount: (ContentService) -> Unit,
  val onRemove: (ContentService, CredentialRemoval) -> Unit,
  val onRefresh: () -> Unit,
  val onDismissNotice: () -> Unit,
  /**
   * Reopens the guided setup card.
   *
   * "Do this later" is honoured for as long as the viewer asked, which left no way to ask for it
   * back. Someone who dismissed it and then went looking for the feature should not have to wait
   * out their own deferral to see the guide again.
   */
  val onShowSetupGuide: () -> Unit,
)

// ── Brand ─────────────────────────────────────────────────────────────────────────────────────

private fun serviceAccent(service: ContentService): Color = when (service) {
  // TMDB's own teal-to-green, and MDBList's amber. Used for the card wash and the status pip, so
  // a glance at the page tells the two apart before any text is read.
  ContentService.Tmdb -> Color(0xFF01B4E4)
  ContentService.Mdblist -> Color(0xFFF5A524)
  ContentService.TheIntroDb -> Color(0xFF05DF72)
}

private fun serviceLogoRes(service: ContentService): Int = when (service) {
  ContentService.Tmdb -> R.drawable.rating_tmdb_logo
  ContentService.Mdblist -> R.drawable.sync_mdblist_logo
  ContentService.TheIntroDb -> R.drawable.theintrodb_logo
}

private fun openUrl(context: Context, url: String) {
  runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}

// ── Dialog sizing ─────────────────────────────────────────────────────────────────────────────

/**
 * How wide and how tall the three Content Services dialogs are allowed to be.
 *
 * Material's default dialog width leaves roughly three quarters of a phone's width, and these
 * dialogs carry service cards rather than a sentence and two buttons — at that width the card had
 * about 150dp to fit a logo, a name and a status into. Turning off the platform default width and
 * asking for nearly the full screen gives the cards room to breathe, with a cap so a tablet or a
 * landscape phone gets a readable column instead of a very wide one.
 *
 * The height is capped rather than filled: a dialog that reaches both edges of the screen reads as
 * a page that has failed to open properly, and leaving the background visible above and below
 * keeps it recognisable as something sitting on top of the app. Content taller than the cap
 * scrolls, which the setup card already expected to do.
 */
private const val PrimaryDialogWidthFraction = 0.95f

/**
 * Narrower than the sheet it opens from, so the two read as stacked rather than as one replacing
 * the other. Paired with a drop shadow and a lifted surface tint below, which is what actually
 * carries the depth — an inset alone just looks like a smaller dialog.
 */
private const val SubDialogWidthFraction = 0.87f
private const val DialogMaxHeightFraction = 0.85f
private val DialogMaxWidth = 620.dp
private val SubDialogElevation = 24.dp

/** Non-default width has to be opted into; the dismiss behaviour stays Material's. */
private val ContentServiceDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

@Composable
private fun contentServiceDialogModifier(sub: Boolean = false): Modifier {
  val screenHeight = LocalConfiguration.current.screenHeightDp
  val shape = StreamDekRadius.sheetShape
  return Modifier
    .fillMaxWidth(if (sub) SubDialogWidthFraction else PrimaryDialogWidthFraction)
    .widthIn(max = DialogMaxWidth)
    .heightIn(max = (screenHeight * DialogMaxHeightFraction).dp)
    // clip = false so the shadow is drawn outside the surface rather than cropped to it. The
    // ambient colour is lifted off pure black because a black shadow over a dark scrim is
    // invisible, which is the usual reason elevation "does not work" in a dark theme.
    .then(
      if (sub) {
        Modifier.shadow(
          elevation = SubDialogElevation,
          shape = shape,
          clip = false,
          ambientColor = Color.Black.copy(alpha = 0.6f),
          spotColor = Color.Black.copy(alpha = 0.75f),
        )
      } else {
        Modifier
      },
    )
}

// ── Shared pieces ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContentServicesCard(
  accent: Color,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = StreamDekRadius.panelShape,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
  ) {
    Column(
      modifier = Modifier
        .background(
          Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)),
        )
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      content = content,
    )
  }
}

/**
 * The connection badge.
 *
 * Four states rather than two, because "checking" and "this stopped working" are things the
 * viewer needs to see and a boolean cannot say. Colour and words always agree, so the state is
 * legible without relying on colour alone.
 *
 * The label is kept short deliberately. Where a key is stored is answered by [StorageLine] a few
 * lines below, and repeating "via StreamDek" here only made this badge wide enough to squeeze
 * whatever shared a row with it.
 */
@Composable
private fun StatusBadge(status: CredentialStatus) {
  val (label, tint) = when (status) {
    CredentialStatus.Connected -> "Connected" to Color(0xFF22C55E)
    CredentialStatus.Checking -> "Checking…" to Color(0xFF60A5FA)
    CredentialStatus.NeedsAttention -> "Needs attention" to Color(0xFFF59E0B)
    CredentialStatus.NotConfigured -> "Not configured" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
  }
  Surface(
    shape = StreamDekRadius.pill,
    color = tint.copy(alpha = 0.12f),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      if (status == CredentialStatus.Checking) {
        CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 2.dp, color = tint)
      } else {
        Box(modifier = Modifier.size(8.dp).background(tint, StreamDekRadius.pill))
      }
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = tint,
        maxLines = 1,
      )
    }
  }
}

/**
 * Logo, name, what the service is for, and whether it is connected.
 *
 * The badge sits on its own line under the name rather than at the end of the title row. In a Row
 * an unweighted child is measured at its intrinsic width first and the weighted ones divide what
 * is left, so a status label long enough to matter left the title column a few dp wide and every
 * word broke to one character per line. Nothing here now shares a row with text of unpredictable
 * length, so the card cannot collapse however narrow it gets.
 */
@Composable
private fun ServiceHeader(service: ContentService, state: ContentServiceState) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Surface(
      shape = StreamDekRadius.thumbShape,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
      modifier = Modifier.size(48.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Image(
          painter = painterResource(serviceLogoRes(service)),
          contentDescription = service.label,
          modifier = Modifier.size(30.dp),
          contentScale = ContentScale.Fit,
        )
      }
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        service.label,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        service.tagline,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      StatusBadge(if (state.status == CredentialStatus.Checking) CredentialStatus.Checking else state.status)
    }
  }
}

/**
 * The bullets that answer "what does this actually do for me", straight off the enum.
 *
 * [limit] exists for the setup card, which shows both services at once and has a phone's height
 * to fit them in. The settings page, where the viewer has already chosen to be, shows the lot.
 */
@Composable
private fun ServiceUses(service: ContentService, limit: Int = Int.MAX_VALUE) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    service.uses.take(limit).forEach { use ->
      Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        // Nudged down to sit on the first line's baseline rather than in the middle of a use that
        // wraps to two lines.
        Box(
          modifier = Modifier
            .padding(top = 7.dp)
            .size(5.dp)
            .background(serviceAccent(service).copy(alpha = 0.8f), StreamDekRadius.pill),
        )
        Text(
          use,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
        )
      }
    }
  }
}

/**
 * Where the key lives, spelled out.
 *
 * A viewer who cannot tell whether their key is on this phone or in their account cannot make
 * sense of why the television does or does not have it, so this line is never abbreviated to an
 * icon.
 */
@Composable
private fun StorageLine(state: ContentServiceState) {
  val storage = state.storage ?: return
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(
      "STORAGE",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
    )
    // One string rather than three Texts in a Row. Chained like that, the masked key measured at
    // its own intrinsic width and pushed the storage name into breaking a character at a time;
    // as one line it either fits or ellipsises, and both of those are readable.
    Text(
      storage.label + (state.maskedKey?.let { "  ·  $it" } ?: ""),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      storage.detail,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
    )
  }
}

@Composable
private fun NoticeBar(notice: String, isError: Boolean, onDismiss: () -> Unit) {
  val tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF22C55E)
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = StreamDekRadius.cardShape,
    color = tint.copy(alpha = 0.10f),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        if (isError) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp),
      )
      Text(
        notice,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
        Icon(
          Icons.Rounded.Close,
          contentDescription = "Dismiss",
          tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

// ── Key entry ─────────────────────────────────────────────────────────────────────────────────

/**
 * Entering a key, and choosing where it goes.
 *
 * One dialog does the whole job: paste, check, choose. Splitting the storage decision into a
 * second screen after a successful check reads as an interruption, and burying it in a settings
 * toggle somewhere else would make it something people never see — which is the outcome the
 * requirement to make the choice explicit exists to prevent.
 *
 * The choice defaults to saving to StreamDek, because that is what most people want and it is the
 * one that makes the television work. It is a visible, reversible default, not a silent one: both
 * options are on screen with their consequences written out before anything is sent.
 */
@Composable
fun ContentServiceKeyDialog(
  service: ContentService,
  existing: ContentServiceState,
  busy: Boolean,
  signedIn: Boolean,
  /** Why the last attempt for this service failed, if it did. Shown against the field. */
  failure: String?,
  /** Set once the service has accepted the key, so the dialog can say so before it closes. */
  verified: String?,
  onDismiss: () -> Unit,
  onSubmit: (String, StorageChoice) -> Unit,
) {
  val context = LocalContext.current
  var key by rememberSaveable(service) { mutableStateOf("") }
  // Device-only is the only possible choice when signed out: there is no account to save to.
  var choice by rememberSaveable(service) {
    mutableStateOf(if (signedIn) StorageChoice.SaveToStreamDek else StorageChoice.ThisDeviceOnly)
  }
  var showHelp by rememberSaveable(service) { mutableStateOf(false) }
  val updating = existing.configured

  /**
   * The dialog stays open until the key has actually been checked.
   *
   * It used to close the moment Connect was pressed, which meant the one thing the viewer wanted
   * to know -- did this key work -- was answered somewhere behind the sheet they had just
   * dismissed. Now the button reports progress, a refusal is shown against the field with the key
   * still in it to correct, and success is confirmed here before the dialog gets out of the way.
   */
  LaunchedEffect(verified) {
    if (verified != null) {
      delay(1_200)
      onDismiss()
    }
  }

  AlertDialog(
    modifier = contentServiceDialogModifier(sub = true),
    properties = ContentServiceDialogProperties,
    onDismissRequest = { if (!busy && verified == null) onDismiss() },
    title = {
      Text(
        if (updating) "Update your ${service.label} key" else "Add your ${service.label} key",
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          service.blurb,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
        )

        OutlinedTextField(
          value = key,
          onValueChange = { key = it.trim() },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          enabled = !busy && verified == null,
          isError = failure != null,
          label = { Text(service.keyHint) },
          placeholder = { Text("Paste your key", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)) },
          // Not a password field: this is pasted from a clipboard, and hiding it makes a mistyped
          // key impossible to spot. It is masked everywhere it is shown back, which is the part
          // that matters.
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
          ),
        )

        // The outcome, against the field that caused it. A refusal keeps the key so the viewer can
        // fix a missed character rather than fetch it again.
        AnimatedVisibility(visible = failure != null || verified != null) {
          val success = verified != null
          val tint = if (success) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
          Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              if (success) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
              contentDescription = null,
              tint = tint,
              modifier = Modifier.size(18.dp),
            )
            Text(
              verified ?: failure.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              color = tint,
            )
          }
        }

        TextButton(
          onClick = { showHelp = !showHelp },
          contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
          Text(if (showHelp) "Hide instructions" else "Don't have a ${service.label} key?")
        }

        AnimatedVisibility(
          visible = showHelp,
          enter = fadeIn() + expandVertically(),
          exit = fadeOut() + shrinkVertically(),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            service.howToGet.forEachIndexed { index, step ->
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                  "${index + 1}.",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  color = serviceAccent(service),
                )
                Text(
                  step,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                )
              }
            }
            OutlinedButton(
              onClick = { openUrl(context, service.keyUrl) },
              shape = StreamDekRadius.pill,
            ) {
              Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
              Text("  Open ${service.label}")
            }
          }
        }

        StorageChoiceSelector(
          choice = choice,
          signedIn = signedIn,
          enabled = !busy,
          onChoice = { choice = it },
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSubmit(key.trim(), choice) },
        enabled = key.trim().length >= 8 && !busy && verified == null,
        shape = StreamDekRadius.pill,
      ) {
        when {
          busy -> {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("  Checking…")
          }
          verified != null -> Text("Verified")
          // "Check &" rather than just "Connect": the button says what it is about to do, so a
          // refusal a moment later reads as the check working rather than as the save failing.
          else -> Text(if (updating) "Check & Update" else "Check & Connect")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !busy && verified == null) { Text("Cancel") }
    },
  )
}

/**
 * The storage choice itself.
 *
 * Both options are always visible with their consequence written underneath, rather than a
 * toggle whose off-state has to be inferred. The device-only wording says plainly that StreamDek
 * keeps no copy *and* that the key still travels to StreamDek's servers to make the request —
 * that second half is easy to leave out and would make the promise misleading.
 */
@Composable
private fun StorageChoiceSelector(
  choice: StorageChoice,
  signedIn: Boolean,
  enabled: Boolean,
  onChoice: (StorageChoice) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      "Where should this key be kept?",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    StorageOption(
      title = "Save to StreamDek",
      detail = if (signedIn) {
        "Stored encrypted in your StreamDek account, so your TV and other devices use it automatically — you only enter it once."
      } else {
        "Sign in to StreamDek to use this option."
      },
      selected = choice == StorageChoice.SaveToStreamDek,
      enabled = enabled && signedIn,
      onSelect = { onChoice(StorageChoice.SaveToStreamDek) },
    )
    StorageOption(
      title = "This device only",
      detail = "Stored encrypted on this phone, and StreamDek keeps no copy. It is sent with your " +
        "own requests so they can be made, and never saved. Your other devices will each need " +
        "their own key.",
      selected = choice == StorageChoice.ThisDeviceOnly,
      enabled = enabled,
      onSelect = { onChoice(StorageChoice.ThisDeviceOnly) },
    )
  }
}

@Composable
private fun StorageOption(
  title: String,
  detail: String,
  selected: Boolean,
  enabled: Boolean,
  onSelect: () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  val border by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = tween(180),
    label = "storage-option-border",
  )
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .alpha(if (enabled) 1f else 0.45f)
      .clickable(enabled = enabled, onClick = onSelect),
    shape = StreamDekRadius.cardShape,
    color = if (selected) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
    border = BorderStroke(
      1.dp,
      accent.copy(alpha = 0.10f + 0.40f * border),
    ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier.size(20.dp).padding(top = 2.dp),
        contentAlignment = Alignment.TopCenter,
      ) {
        Icon(
          Icons.Rounded.CheckCircle,
          contentDescription = null,
          tint = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
          modifier = Modifier.size(19.dp),
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          detail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
        )
      }
    }
  }
}

// ── Removal ───────────────────────────────────────────────────────────────────────────────────

/**
 * Removing a key, with the blast radius stated first.
 *
 * An account key is in use by every device signed in, so the dialog says that before offering the
 * button — and where a device also has its own copy, the two removals are offered separately
 * rather than collapsed into one destructive "Remove".
 */
@Composable
fun ContentServiceRemoveDialog(
  state: ContentServiceState,
  onDismiss: () -> Unit,
  onRemove: (CredentialRemoval) -> Unit,
) {
  val service = state.service
  val onAccount = state.storage == CredentialStorage.Account
  val bothPlaces = state.storage == CredentialStorage.Device && state.accountKeyAlsoAvailable

  AlertDialog(
    modifier = contentServiceDialogModifier(sub = true),
    properties = ContentServiceDialogProperties,
    onDismissRequest = onDismiss,
    title = { Text("Remove your ${service.label} key?", fontWeight = FontWeight.Bold) },
    text = {
      Text(
        when {
          onAccount ->
            "This key is saved to your StreamDek account and is currently available to all your " +
              "StreamDek devices. Removing it takes it away from your TV and any other signed-in " +
              "device as well as this one."
          bothPlaces ->
            "This phone has its own ${service.label} key, and your StreamDek account has one too. " +
              "Choose which to remove — removing the account key affects your other devices."
          else ->
            "This key is stored on this phone only, so removing it affects nothing else. " +
              "${service.label} features stop working here until you add a key again."
        },
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Column(horizontalAlignment = Alignment.End) {
        if (bothPlaces) {
          TextButton(onClick = { onRemove(CredentialRemoval.Device) }) { Text("Remove from this device") }
          TextButton(onClick = { onRemove(CredentialRemoval.Account) }) {
            Text("Remove from StreamDek", color = MaterialTheme.colorScheme.error)
          }
        } else {
          TextButton(
            onClick = { onRemove(if (onAccount) CredentialRemoval.Account else CredentialRemoval.Device) },
          ) {
            Text(
              if (onAccount) "Remove from StreamDek" else "Remove from this device",
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

// ── The service card ──────────────────────────────────────────────────────────────────────────

/**
 * One service, in full: what it does, whether it is connected, where its key lives, and what can
 * be done about any of that.
 *
 * The same card is used on the settings page and in the setup prompt. [compact] drops the
 * explanatory bullets for the settings page, where the viewer has already chosen to be here.
 */
@Composable
fun ContentServiceCard(
  state: ContentServiceState,
  busy: Boolean,
  signedIn: Boolean,
  actions: ContentServiceActions,
  compact: Boolean = false,
  /** How many of the service's uses to list. The setup card shows both services at once. */
  usesLimit: Int = Int.MAX_VALUE,
  /** The last outcome for *this* service, or null. Passed to the key dialog to report in place. */
  notice: String? = null,
  noticeIsError: Boolean = false,
) {
  val service = state.service
  val accent = serviceAccent(service)
  var entryOpen by rememberSaveable(service) { mutableStateOf(false) }
  var removeOpen by rememberSaveable(service) { mutableStateOf(false) }

  ContentServicesCard(accent = accent) {
    ServiceHeader(service, state.copy(status = if (busy) CredentialStatus.Checking else state.status))

    if (!compact || !state.configured) {
      Text(
        service.blurb,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
      )
    }
    if (!state.configured) ServiceUses(service, usesLimit)

    if (state.status == CredentialStatus.NeedsAttention) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StreamDekRadius.thumbShape,
        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(19.dp),
          )
          Text(
            "${service.label} is no longer accepting your saved key. Update it to get " +
              "${if (service == ContentService.Tmdb) "artwork and details" else "ratings"} back.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
          )
        }
      }
    }

    StorageLine(state)

    // A device key that could also live in the account: offered rather than done, because
    // uploading a key the viewer explicitly chose to keep local is exactly what must not happen
    // without them asking for it.
    if (state.storage == CredentialStorage.Device && !state.accountKeyAlsoAvailable && signedIn) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StreamDekRadius.thumbShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
          Text(
            "Use this key on your other devices?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            "Save it to your StreamDek account and your TV picks it up automatically, with no " +
              "typing on the remote.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
          )
          OutlinedButton(
            onClick = { actions.onCopyDeviceKeyToAccount(service) },
            enabled = !busy,
            shape = StreamDekRadius.pill,
          ) { Text("Save to StreamDek") }
        }
      }
    }

    if (state.storage == CredentialStorage.Device && state.accountKeyAlsoAvailable) {
      Text(
        "Your StreamDek account also has a ${service.label} key. This phone is using its own; " +
          "remove the one on this device to fall back to the account key.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      Button(
        onClick = {
          // Cleared on the way in, so the dialog never opens already showing the result of
          // something the viewer did a minute ago.
          actions.onDismissNotice()
          entryOpen = true
        },
        enabled = !busy,
        shape = StreamDekRadius.pill,
      ) {
        Text(
          when {
            state.status == CredentialStatus.NeedsAttention -> "Update Key"
            state.configured -> "Replace Key"
            else -> "Enter ${service.label} Key"
          },
          fontWeight = FontWeight.SemiBold,
        )
      }
      if (state.configured) {
        TextButton(onClick = { removeOpen = true }, enabled = !busy) {
          Text("Remove", color = MaterialTheme.colorScheme.error)
        }
      }
    }
  }

  if (entryOpen) {
    ContentServiceKeyDialog(
      service = service,
      existing = state,
      busy = busy,
      signedIn = signedIn,
      failure = notice.takeIf { noticeIsError },
      verified = notice.takeIf { !noticeIsError },
      onDismiss = {
        entryOpen = false
        actions.onDismissNotice()
      },
      // Deliberately does not close the dialog. The result of the check is what the viewer opened
      // it for, so the dialog stays until there is one.
      onSubmit = { key, choice -> actions.onSubmitKey(service, key, choice) },
    )
  }
  if (removeOpen) {
    ContentServiceRemoveDialog(
      state = state,
      onDismiss = { removeOpen = false },
      onRemove = { scope ->
        removeOpen = false
        actions.onRemove(service, scope)
      },
    )
  }
}

// ── Setup routes ──────────────────────────────────────────────────────────────────────────────

/**
 * The three ways a key can be set up, said out loud.
 *
 * People do not discover the web portal on their own, and a viewer who does not know the TV can
 * inherit a key from their account will sit there typing one into a remote. Naming all three
 * routes is cheap and is the difference between the feature being usable and being technically
 * present.
 */
@Composable
fun ContentServiceSetupRoutes(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = StreamDekRadius.panelShape,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Rounded.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(19.dp),
        )
        Text(
          "Three ways to set these up",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      SetupRoute(
        "On this phone",
        "Enter a key here and choose whether StreamDek keeps it for your other devices.",
      )
      SetupRoute(
        "On the StreamDek web portal",
        "Far easier for long keys — a keyboard beats a remote. Anything added there is saved to " +
          "your account and appears on your devices automatically.",
      )
      SetupRoute(
        "On your TV",
        "You can type a key straight into StreamDek TV, but you only need to if you chose to keep " +
          "your key on one device.",
      )
    }
  }
}

@Composable
private fun SetupRoute(title: String, detail: String) {
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(
      title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
    )
    Text(
      detail,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    )
  }
}

// ── The settings page ─────────────────────────────────────────────────────────────────────────

/** Settings → Content Services. Both cards, the routes explainer, and whatever just happened. */
@Composable
fun ContentServicesSettings(
  state: ContentServicesState,
  signedIn: Boolean,
  actions: ContentServiceActions,
  introDbApiKey: String,
  onIntroDbApiKeyChange: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    state.notice?.let { notice ->
      NoticeBar(notice, state.noticeIsError, actions.onDismissNotice)
    }

    if (!signedIn) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StreamDekRadius.cardShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
      ) {
        Text(
          "You're signed out. Keys you add now stay on this phone. Sign in to save them to your " +
            "StreamDek account and share them with your TV.",
          modifier = Modifier.padding(16.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
        )
      }
    }

    ContentServiceCard(
      state = state.tmdb,
      busy = state.busy == ContentService.Tmdb,
      signedIn = signedIn,
      actions = actions,
      compact = true,
      notice = state.notice.takeIf { state.noticeService == ContentService.Tmdb },
      noticeIsError = state.noticeIsError,
    )
    ContentServiceCard(
      state = state.mdblist,
      busy = state.busy == ContentService.Mdblist,
      signedIn = signedIn,
      actions = actions,
      compact = true,
      notice = state.notice.takeIf { state.noticeService == ContentService.Mdblist },
      noticeIsError = state.noticeIsError,
    )
    ContentServiceCard(
      state = state.theIntroDb,
      busy = state.busy == ContentService.TheIntroDb,
      signedIn = signedIn,
      actions = actions,
      compact = true,
      notice = state.notice.takeIf { state.noticeService == ContentService.TheIntroDb },
      noticeIsError = state.noticeIsError,
    )
    IntroDbApiKeyCard(introDbApiKey, onIntroDbApiKeyChange)

    ContentServiceSetupRoutes()

    TextButton(onClick = actions.onShowSetupGuide) { Text("Show the setup guide") }

    if (state.tmdb.configured || !state.sharedFallbackAvailable) {
      Text(
        "TMDB lookups use your own key, so StreamDek's shared allowance never limits you.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
      )
    } else {
      Text(
        "Until you add a TMDB key, StreamDek uses its own shared one. That works, but it's shared " +
          "with everyone — your own key is faster and never runs into someone else's limit.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
      )
    }
  }
}

/** The viewer's optional IntroDB key replaces the deployment key without mixing the two services. */
@Composable
private fun IntroDbApiKeyCard(savedKey: String, onSave: (String) -> Unit) {
  var draft by remember(savedKey) { mutableStateOf(savedKey) }
  var savedFeedback by remember { mutableStateOf(false) }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = StreamDekRadius.cardShape,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Image(
            painter = painterResource(R.drawable.introdb_logo),
            contentDescription = "IntroDB",
            modifier = Modifier.fillMaxWidth(0.52f).height(30.dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
          )
          Text("Series timing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
        }
        Text(
          if (savedKey.isBlank()) "StreamDek key" else "Own key",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = if (savedKey.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f) else Color(0xFF22C55E),
        )
      }
      Text(
        "IntroDB supplies intro, recap and ending times for series. Add your own API key for your personal allowance, or leave it empty to use StreamDek's built-in key.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
      )
      OutlinedTextField(
        value = draft,
        onValueChange = { draft = it; savedFeedback = false },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("IntroDB API key") },
        placeholder = { Text("idb_...") },
      )
      Button(
        onClick = {
          val normalized = draft.trim()
          onSave(normalized)
          draft = normalized
          savedFeedback = true
        },
        enabled = draft.trim() != savedKey,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = StreamDekRadius.pill,
      ) {
        Text(if (draft.isBlank() && savedKey.isNotBlank()) "Use StreamDek key" else "Save IntroDB key", fontWeight = FontWeight.Bold)
      }
      if (savedFeedback) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
          Text(
            if (draft.isBlank()) "StreamDek's IntroDB key is active." else "IntroDB key saved successfully.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF22C55E),
          )
        }
      }
    }
  }
}

// ── The setup prompt ──────────────────────────────────────────────────────────────────────────

/**
 * The proactive prompt, shown once when nothing is configured.
 *
 * It leads with what the viewer gets rather than with what StreamDek needs, allows either service
 * to be set up without the other, and has a real "Do this later" that is remembered. A modal that
 * cannot be dismissed properly is the thing that turns a helpful prompt into an obstacle people
 * learn to tap through without reading.
 */
@Composable
fun ContentServicesSetupPrompt(
  state: ContentServicesState,
  signedIn: Boolean,
  actions: ContentServiceActions,
  onLater: (SetupDeferral) -> Unit,
  onDone: () -> Unit,
) {
  /**
   * Whether the two "how long" options are showing.
   *
   * A second tap rather than three buttons across the footer: "Tomorrow", "In 3 days" and "Done"
   * side by side overflow a phone, and a viewer who is dismissing this does not need the choice
   * in front of them until they have said they want it.
   */
  var choosingLater by remember { mutableStateOf(false) }

  AlertDialog(
    modifier = contentServiceDialogModifier(),
    properties = ContentServiceDialogProperties,
    // Back and outside-tap take the shorter window. It is the reversible one: being asked again
    // tomorrow costs a tap, and not being asked for three days after a mis-tap costs the feature.
    onDismissRequest = { onLater(SetupDeferral.Tomorrow) },
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Enhance your StreamDek", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(
          "Connect your own content services for richer artwork, details and ratings — and use " +
            "them across every StreamDek device.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        state.notice?.let { NoticeBar(it, state.noticeIsError, actions.onDismissNotice) }
        ContentServiceCard(
          state = state.tmdb,
          busy = state.busy == ContentService.Tmdb,
          signedIn = signedIn,
          actions = actions,
          usesLimit = 3,
          notice = state.notice.takeIf { state.noticeService == ContentService.Tmdb },
          noticeIsError = state.noticeIsError,
        )
        ContentServiceCard(
          state = state.mdblist,
          busy = state.busy == ContentService.Mdblist,
          signedIn = signedIn,
          actions = actions,
          usesLimit = 3,
          notice = state.notice.takeIf { state.noticeService == ContentService.Mdblist },
          noticeIsError = state.noticeIsError,
        )
        ContentServiceSetupRoutes()
      }
    },
    confirmButton = {
      Button(onClick = onDone, shape = StreamDekRadius.pill) {
        Text(if (state.anyConfigured) "Done" else "Continue")
      }
    },
    dismissButton = {
      if (choosingLater) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          SetupDeferral.values().forEach { deferral ->
            TextButton(onClick = { onLater(deferral) }) { Text(deferral.label) }
          }
        }
      } else {
        TextButton(onClick = { choosingLater = true }) { Text("Do this later") }
      }
    },
  )
}

// ── The contextual nudge ──────────────────────────────────────────────────────────────────────

/**
 * The small in-place reminder, shown where a missing key is visibly costing something.
 *
 * Deliberately a card in the flow rather than a modal: the viewer came here to watch something,
 * and interrupting them to talk about API keys would be the wrong trade. Rate-limited by the
 * credential manager to once a day per service.
 */
@Composable
fun ContentServiceHint(
  service: ContentService,
  onSetUp: () -> Unit,
  onLater: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accent = serviceAccent(service)
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .shadow(
        elevation = 12.dp,
        shape = StreamDekRadius.cardShape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.45f),
        spotColor = Color.Black.copy(alpha = 0.55f),
      ),
    shape = StreamDekRadius.cardShape,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shadowElevation = 6.dp,
    border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
  ) {
    Row(
      modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Image(
        painter = painterResource(serviceLogoRes(service)),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        contentScale = ContentScale.Fit,
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          if (service == ContentService.Mdblist) "Ratings aren't set up yet" else "TMDB isn't set up yet",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          if (service == ContentService.Mdblist) {
            "Add your MDBList key to see IMDb and Rotten Tomatoes scores here."
          } else {
            "Add your TMDB key for faster, fuller artwork and details."
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
      }
      TextButton(onClick = onSetUp) { Text("Set up") }
      TextButton(onClick = onLater) {
        Text("Later", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
      }
    }
  }
}

/**
 * Stacks the contextual nudge above whatever normally occupies the bottom of the shell.
 *
 * [bar] is a plain composable lambda rather than a `ColumnScope` one, deliberately. An enclosing
 * `ColumnScope` becomes an implicit receiver for everything drawn inside it, and Kotlin then
 * prefers `ColumnScope.AnimatedVisibility` over the plain overload in code several layers down —
 * which is how adding a wrapper here breaks an animation in the navigation bar. Keeping the scope
 * inside this function means the bar below composes exactly as it did before.
 */
@Composable
fun ContentServiceHintHost(
  hint: ContentService?,
  onSetUp: () -> Unit,
  onLater: () -> Unit,
  bar: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    hint?.let { service ->
      ContentServiceHint(
        service = service,
        onSetUp = onSetUp,
        onLater = onLater,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    }
    // Invoked inside the Column so the two stack, but `bar` has no receiver type, so the lambda
    // written at the call site is still resolved in its own lexical scope — which is the point.
    bar()
  }
}

/** Row used on the settings home to summarise the section without opening it. */
internal fun contentServicesSubtitle(state: ContentServicesState): String {
  val connected = listOf(state.tmdb, state.mdblist).filter { it.configured }
  val attention = state.needsAttention
  return when {
    attention.isNotEmpty() -> "${attention.joinToString(", ") { it.service.label }} needs attention"
    connected.size == 2 -> "TMDB and MDBList connected"
    connected.size == 1 -> "${connected.first().service.label} connected - add ${
      if (connected.first().service == ContentService.Tmdb) "MDBList" else "TMDB"
    }"
    else -> "Use your own TMDB and MDBList keys"
  }
}

/** Unused placeholder colour filter kept out of the header so the logos render untinted. */
private val UntintedLogo: ColorFilter? = null

/** A thin divider matching the settings page rhythm, for callers composing their own sections. */
@Composable
internal fun ContentServicesDivider() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
  )
}

/** Centred empty-state text used while the account state is still being read. */
@Composable
internal fun ContentServicesLoading() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
    Text(
      "Checking your content services…",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
      textAlign = TextAlign.Center,
    )
  }
}
