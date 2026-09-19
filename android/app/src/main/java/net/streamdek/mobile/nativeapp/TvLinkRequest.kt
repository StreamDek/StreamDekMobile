package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.streamdek.mobile.R

/**
 * Linking a television by its QR code.
 *
 * The TV shows `https://www.streamdek.net/link-tv?code=ABCD-2345`. Scanned with the phone's own camera,
 * Android App Links open that URL here (see the manifest); scanned with the scanner under Settings,
 * Connect to TV, the same code arrives by hand. Either way it ends at [TvLinkApprovalDialog], which asks
 * the backend which television is behind the code, shows it, and approves or declines it - one screen,
 * one set of calls, whichever way the viewer came in.
 *
 * The code is not a credential. It does nothing until a signed-in person approves it, it works once,
 * and it lapses with the TV's request after ten minutes.
 */

/** Hosts whose `/link-tv` path is a television's QR code. */
private val TvLinkHosts = setOf("www.streamdek.net", "streamdek.net")

/** The one shape a pairing code takes: the backend's alphabet has no 0, 1, I or O. */
private val TvCodePattern = Regex("^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$")

/**
 * The pairing code in a television link, or null when [rawUrl] is not one.
 *
 * Accepts the https link the QR code carries and the older `streamdek://link-tv?code=` form, so a
 * television still on a build that shows the old link keeps working.
 */
internal fun parseTvLinkCode(rawUrl: String): String? {
  val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
  val scheme = uri.scheme?.lowercase() ?: return null
  val isLink = when (scheme) {
    "https" -> uri.host?.lowercase() in TvLinkHosts && uri.path.orEmpty().trimEnd('/').equals("/link-tv", ignoreCase = true)
    "streamdek" -> uri.host.equals("link-tv", ignoreCase = true)
    else -> false
  }
  if (!isLink) return null
  val code = uri.rawQuery.orEmpty().split('&')
    .firstOrNull { it.substringBefore('=').equals("code", ignoreCase = true) }
    ?.substringAfter('=', "")
    ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
    ?: return null
  return normalizeTvLinkCode(code)
}

/** "abcd 2345" to "ABCD-2345", or null when it cannot be a pairing code. */
internal fun normalizeTvLinkCode(raw: String): String? {
  val compact = raw.uppercase().filter(Char::isLetterOrDigit)
  if (compact.length != 8) return null
  return "${compact.take(4)}-${compact.drop(4)}".takeIf(TvCodePattern::matches)
}

/** What a television told the backend about itself when it asked to be linked. */
data class TvLinkRequestInfo(
  val code: String,
  val deviceName: String?,
  val clientName: String?,
  val platform: String?,
  val appVersion: String?,
  val expiresAtMillis: Long?,
)

/** Why a code cannot be used, so the screen can say something more useful than "failed". */
enum class TvLinkFailure { Expired, AlreadyUsed, Declined, RateLimited, Invalid, Network, Other }

class TvLinkException(val reason: TvLinkFailure, message: String) : IllegalStateException(message)

private sealed interface TvLinkStage {
  data object Looking : TvLinkStage
  data class Review(val info: TvLinkRequestInfo) : TvLinkStage
  data class Working(val info: TvLinkRequestInfo, val approving: Boolean) : TvLinkStage
  data class Linked(val deviceName: String?) : TvLinkStage
  data object Declined : TvLinkStage
  data class Failed(val reason: TvLinkFailure, val info: TvLinkRequestInfo?) : TvLinkStage
}

private fun TvLinkRequestInfo.displayName(fallback: String): String =
  deviceName?.takeIf { it.isNotBlank() } ?: clientName?.takeIf { it.isNotBlank() } ?: fallback

private fun platformLabel(platform: String?): String? = when (platform?.lowercase()) {
  null, "" -> null
  "android-tv", "androidtv" -> "Android TV"
  "firetv", "fire-tv" -> "Fire TV"
  "google-tv" -> "Google TV"
  else -> platform
}

/**
 * The approval screen, for a code from a QR link or from the in-app scanner.
 *
 * It looks the code up first, so the viewer approves a named television rather than a string of
 * letters, and shows the code to compare against the TV screen. Approve and Decline each call the
 * backend once; Cancel leaves the television waiting (its code simply lapses), which is the right
 * answer when the viewer is not sure. [onLinked] is told when a television has signed in.
 */
@Composable
internal fun TvLinkApprovalDialog(
  code: String,
  session: AuthSession,
  apiClient: StreamDekApiClient,
  onDismiss: () -> Unit,
  onLinked: (deviceName: String?) -> Unit = {},
) {
  val scope = rememberCoroutineScope()
  var stage by remember(code) { mutableStateOf<TvLinkStage>(TvLinkStage.Looking) }
  var attempt by remember(code) { mutableIntStateOf(0) }
  val fallbackName = stringResource(R.string.tv_link_unnamed_tv)

  LaunchedEffect(code, attempt) {
    stage = TvLinkStage.Looking
    apiClient.lookupTvCode(session, code)
      .onSuccess { stage = TvLinkStage.Review(it) }
      .onFailure { stage = TvLinkStage.Failed((it as? TvLinkException)?.reason ?: TvLinkFailure.Network, null) }
  }

  // The request's remaining life, ticking, so an approval is not attempted on a code that ran out
  // while the phone sat in a pocket.
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(stage) {
    while (stage is TvLinkStage.Review) {
      now = System.currentTimeMillis()
      val expiresAt = (stage as? TvLinkStage.Review)?.info?.expiresAtMillis
      if (expiresAt != null && now >= expiresAt) {
        stage = TvLinkStage.Failed(TvLinkFailure.Expired, (stage as? TvLinkStage.Review)?.info)
        break
      }
      delay(1_000)
    }
  }

  fun decide(info: TvLinkRequestInfo, approve: Boolean) {
    stage = TvLinkStage.Working(info, approving = approve)
    scope.launch {
      if (approve) {
        apiClient.approveTvCode(session, code)
          .onSuccess { name ->
            stage = TvLinkStage.Linked(name ?: info.deviceName)
            onLinked(name ?: info.deviceName)
          }
          .onFailure { stage = TvLinkStage.Failed((it as? TvLinkException)?.reason ?: TvLinkFailure.Network, info) }
      } else {
        apiClient.denyTvCode(session, code)
          .onSuccess { stage = TvLinkStage.Declined }
          .onFailure { stage = TvLinkStage.Failed((it as? TvLinkException)?.reason ?: TvLinkFailure.Network, info) }
      }
    }
  }

  val busy = stage is TvLinkStage.Looking || stage is TvLinkStage.Working
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    icon = {
      Icon(
        imageVector = when (stage) {
          is TvLinkStage.Linked -> Icons.Rounded.CheckCircle
          is TvLinkStage.Failed -> Icons.Rounded.ErrorOutline
          else -> Icons.Rounded.Tv
        },
        contentDescription = null,
        tint = when (stage) {
          is TvLinkStage.Linked -> Color(0xFF22C55E)
          is TvLinkStage.Failed -> MaterialTheme.colorScheme.error
          else -> MaterialTheme.colorScheme.primary
        },
      )
    },
    title = {
      Text(
        when (val current = stage) {
          is TvLinkStage.Linked -> stringResource(R.string.tv_link_linked_title, current.deviceName ?: fallbackName)
          TvLinkStage.Declined -> stringResource(R.string.tv_link_declined_title)
          is TvLinkStage.Failed -> stringResource(failureTitle(current.reason))
          else -> stringResource(R.string.pairing_authorize_title)
        },
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (val current = stage) {
          TvLinkStage.Looking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.tv_link_checking))
          }
          is TvLinkStage.Review -> TvLinkSummary(current.info, fallbackName, now)
          is TvLinkStage.Working -> TvLinkSummary(current.info, fallbackName, now)
          is TvLinkStage.Linked -> Text(stringResource(R.string.tv_link_linked_detail))
          TvLinkStage.Declined -> Text(stringResource(R.string.tv_link_declined_detail))
          is TvLinkStage.Failed -> Text(stringResource(failureDetail(current.reason)))
        }
      }
    },
    confirmButton = {
      when (val current = stage) {
        is TvLinkStage.Review -> Button(onClick = { decide(current.info, approve = true) }) {
          Text(stringResource(R.string.pairing_authorize_tv), fontWeight = FontWeight.Bold)
        }
        is TvLinkStage.Working -> Button(onClick = {}, enabled = false) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        is TvLinkStage.Failed -> if (current.reason == TvLinkFailure.Network || current.reason == TvLinkFailure.RateLimited) {
          Button(onClick = { attempt += 1 }) { Text(stringResource(R.string.action_retry)) }
        } else {
          Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
        TvLinkStage.Looking -> Unit
        else -> Button(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
      }
    },
    dismissButton = {
      when (val current = stage) {
        is TvLinkStage.Review -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
          OutlinedButton(onClick = { decide(current.info, approve = false) }) { Text(stringResource(R.string.tv_link_decline)) }
        }
        is TvLinkStage.Failed -> if (current.reason == TvLinkFailure.Network || current.reason == TvLinkFailure.RateLimited) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
        TvLinkStage.Looking -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        else -> Unit
      }
    },
  )
}

@Composable
private fun TvLinkSummary(info: TvLinkRequestInfo, fallbackName: String, now: Long) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(R.string.tv_link_review_detail, info.displayName(fallbackName)),
      style = MaterialTheme.typography.bodyMedium,
    )
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), StreamDekRadius.panelShape)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(info.displayName(fallbackName), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        listOfNotNull(platformLabel(info.platform), info.appVersion?.let { "v$it" }).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f))
        }
        Text(info.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
      }
    }
    Text(
      stringResource(R.string.tv_link_compare_code),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
    )
    info.expiresAtMillis?.let { expiresAt ->
      val seconds = ((expiresAt - now) / 1000L).coerceAtLeast(0L)
      Text(
        stringResource(R.string.tv_link_expires_in, "%d:%02d".format(seconds / 60, seconds % 60)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
      )
    }
  }
}

private fun failureTitle(reason: TvLinkFailure): Int = when (reason) {
  TvLinkFailure.Expired -> R.string.tv_link_expired_title
  TvLinkFailure.AlreadyUsed -> R.string.tv_link_used_title
  TvLinkFailure.Declined -> R.string.tv_link_declined_title
  TvLinkFailure.RateLimited -> R.string.tv_link_rate_limited_title
  TvLinkFailure.Invalid -> R.string.tv_link_invalid_title
  TvLinkFailure.Network, TvLinkFailure.Other -> R.string.tv_link_network_title
}

private fun failureDetail(reason: TvLinkFailure): Int = when (reason) {
  TvLinkFailure.Expired -> R.string.tv_link_expired_detail
  TvLinkFailure.AlreadyUsed -> R.string.tv_link_used_detail
  TvLinkFailure.Declined -> R.string.tv_link_declined_detail
  TvLinkFailure.RateLimited -> R.string.tv_link_rate_limited_detail
  TvLinkFailure.Invalid -> R.string.tv_link_invalid_detail
  TvLinkFailure.Network, TvLinkFailure.Other -> R.string.tv_link_network_detail
}
