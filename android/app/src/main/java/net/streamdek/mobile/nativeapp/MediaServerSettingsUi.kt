package net.streamdek.mobile.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.streamdek.mobile.R
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLibrary
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLibraryKind
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLinkCode
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLinkStatus
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerManager
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReachability
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRoute
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerUiState
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerView
import net.streamdek.mobile.nativeapp.mediaserver.OfflineReason
import net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.PLEX_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.RemovedMediaServerEntry
import net.streamdek.mobile.nativeapp.mediaserver.inServerOrder
import net.streamdek.mobile.nativeapp.mediaserver.withLibrariesInOrder
import net.streamdek.mobile.nativeapp.mediaserver.listedMediaServers
import net.streamdek.mobile.nativeapp.mediaserver.mediaServerEntryKey
import net.streamdek.mobile.nativeapp.mediaserver.removedMediaServerEntries

/**
 * The Plex chevron, redrawn as a single-colour glyph for the bottom navigation, so it takes the same
 * tint and weight as Home and Search beside it. The full-colour badge (`R.drawable.plex_logo`) is for
 * places that name the service - Settings and the Plex page's header. Same geometry as the television's.
 */
internal object PlexIcons {
  val Chevron: ImageVector by lazy {
    ImageVector.Builder(name = "PlexChevron", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
      .path(fill = SolidColor(Color.White)) {
        moveTo(6.2f, 3.2f)
        lineTo(11.9f, 3.2f)
        quadTo(12.4f, 3.2f, 12.7f, 3.6f)
        lineTo(18.6f, 11.4f)
        quadTo(19.05f, 12f, 18.6f, 12.6f)
        lineTo(12.7f, 20.4f)
        quadTo(12.4f, 20.8f, 11.9f, 20.8f)
        lineTo(6.2f, 20.8f)
        quadTo(5.6f, 20.8f, 6f, 20.3f)
        lineTo(12.1f, 12f)
        lineTo(6f, 3.7f)
        quadTo(5.6f, 3.2f, 6.2f, 3.2f)
        close()
      }.build()
  }
}

/** Plex's own gold, for the code and the switches, so the page reads as Plex's at a glance. */
internal val PlexGold = Color(0xFFE5A00D)
private val Positive = Color(0xFF22C55E)
private val Waiting = Color(0xFF60A5FA)
private val Warning = Color(0xFFF59E0B)
private val RemoteQualities: List<Int?> = listOf(null, 20_000, 12_000, 8_000, 4_000, 2_000)

/** The one line Settings shows under "Plex": connected as whom, or not connected. */
@Composable
internal fun mediaServerSummary(state: MediaServerUiState): String = when {
  state.linked && state.needsAttention -> stringResource(R.string.plex_needs_attention)
  state.linked -> {
    val libraries = state.usableLibraries.size
    val account = state.accountName?.let { stringResource(R.string.plex_connected_as, it) } ?: stringResource(R.string.plex_connected)
    if (libraries > 0) "$account · ${stringResource(R.string.plex_libraries_on, libraries)}" else account
  }
  else -> stringResource(R.string.plex_not_connected)
}

/** Settings > Sources > Plex, as a row: the service's own badge, its name and where it stands. */
@Composable
internal fun PlexSettingsNavRow(state: MediaServerUiState, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Image(painterResource(R.drawable.plex_logo), contentDescription = null, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(stringResource(R.string.media_server_plex), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      SettingsSubtitle(mediaServerSummary(state))
    }
    Icon(StreamDekSettingsIcons.Forward, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
  }
}

@Composable
private fun Icon(imageVector: ImageVector, contentDescription: String?, tint: Color, modifier: Modifier = Modifier) =
  androidx.compose.material3.Icon(imageVector, contentDescription, tint = tint, modifier = modifier)

/**
 * The Plex page in Settings: linking, then managing.
 *
 * Linking is the plex.tv/link method. A short code is fetched through StreamDek, and "Open
 * plex.tv/link" opens Plex in the browser with that code already filled in. The viewer approves
 * StreamDek there, comes back, and the page notices on its own - nothing to paste back. The code is
 * large enough to type on another device too.
 *
 * Once linked the page is the management screen the brief asks for: the account, each server and
 * how it is being reached, each library's switch, remote quality, and Refresh / Reconnect /
 * Disconnect. Choices save to the StreamDek profile, so every device on it follows.
 */
@Composable
internal fun MediaServerSettingsPage(
  manager: MediaServerManager,
  signedIn: Boolean,
  remoteQualityKbps: () -> Int?,
  onRemoteQualityChange: (Int?) -> Unit,
  onMessage: (String) -> Unit,
  ambientEnabled: Boolean = true,
  onAmbientChange: (Boolean) -> Unit = {},
  continueLocation: MediaServerContinueLocation = MediaServerContinueLocation.StreamDek,
  onContinueLocationChange: (MediaServerContinueLocation) -> Unit = {},
) {
  val state by manager.state.collectAsState()
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val resources = context.resources
  var linkCode by remember { mutableStateOf<MediaServerLinkCode?>(null) }
  var linkExpired by remember { mutableStateOf(false) }
  var starting by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var confirmDisconnect by remember { mutableStateOf(false) }
  var quality by remember { mutableStateOf(remoteQualityKbps()) }

  fun startLink() {
    if (starting) return
    starting = true
    linkExpired = false
    scope.launch {
      val code = manager.startLink()
      starting = false
      if (code == null) onMessage(resources.getString(R.string.plex_link_unavailable)) else linkCode = code
    }
  }

  fun openLink(code: MediaServerLinkCode) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.directUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
      .onFailure { onMessage(code.linkUrl) }
  }

  // Polls while a code is showing, at the pace StreamDek asked for - including while the viewer is
  // in the browser approving it, so the page is already connected when they come back.
  LaunchedEffect(linkCode) {
    val code = linkCode ?: return@LaunchedEffect
    while (true) {
      delay(code.pollIntervalMs)
      when (val result = manager.pollLink(code)) {
        MediaServerLinkStatus.Pending -> Unit
        MediaServerLinkStatus.Expired -> { linkExpired = true; return@LaunchedEffect }
        MediaServerLinkStatus.Failed -> {
          linkCode = null
          onMessage(resources.getString(R.string.plex_link_failed))
          return@LaunchedEffect
        }
        is MediaServerLinkStatus.Linked -> {
          linkCode = null
          onMessage(result.accountName?.let { resources.getString(R.string.plex_connected_as, it) } ?: resources.getString(R.string.plex_connected))
          return@LaunchedEffect
        }
      }
    }
  }

  fun act(work: suspend () -> Boolean, success: Int? = null) {
    if (busy) return
    busy = true
    scope.launch {
      val ok = work()
      busy = false
      onMessage(resources.getString(if (ok) success ?: R.string.plex_saved else R.string.plex_saving_failed))
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    PlexHeaderCard(state, signedIn)

    val code = linkCode
    when {
      !signedIn -> Unit
      code != null -> PlexLinkCard(
        code = code,
        expired = linkExpired,
        onOpen = { openLink(code) },
        onNewCode = ::startLink,
        onCancel = { linkCode = null; linkExpired = false },
      )
      !state.linked || state.needsAttention -> Button(
        onClick = ::startLink,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PlexGold, contentColor = Color.Black),
      ) {
        if (starting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
        Text(
          stringResource(if (starting) R.string.plex_link_getting_code else if (state.needsAttention) R.string.plex_reconnect else R.string.plex_connect),
          modifier = Modifier.padding(start = if (starting) 8.dp else 0.dp),
          fontWeight = FontWeight.Bold,
        )
      }
    }

    if (signedIn && state.linked && code == null) {
      MediaServerGroups(
        provider = PLEX_PROVIDER_ID,
        manager = manager,
        state = state,
        accent = PlexGold,
        emptyNote = stringResource(if (state.refreshing) R.string.plex_status_connecting else R.string.plex_no_servers),
        onToggleServer = { server -> act({ manager.setServerEnabled(server.id, !server.enabled) }) },
        onToggleLibrary = { server, library -> act({ manager.setLibraryEnabled(server.id, library.key, !library.enabled) }) },
        onMessage = onMessage,
      )

      SettingsSection(stringResource(R.string.plex_playback)) {
        Row(
          modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
            val next = RemoteQualities[(RemoteQualities.indexOf(quality) + 1) % RemoteQualities.size]
            quality = next
            onRemoteQualityChange(next)
          }.padding(vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.plex_remote_quality), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            SettingsSubtitle(stringResource(R.string.plex_remote_quality_detail))
          }
          Text(
            quality?.let { stringResource(R.string.plex_quality_mbps, it / 1000) } ?: stringResource(R.string.plex_quality_original),
            color = PlexGold,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      SettingsSection(stringResource(R.string.plex_page_section)) {
        PlexSwitchRow(
          title = stringResource(R.string.plex_ambient),
          detail = stringResource(R.string.plex_ambient_detail),
          detailColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
          checked = ambientEnabled,
          indent = false,
          onToggle = { onAmbientChange(!ambientEnabled) },
        )
      }

      MediaServerContinueLocationSection(stringResource(R.string.media_server_plex), continueLocation, PlexGold, onContinueLocationChange)

      SettingsSection(stringResource(R.string.plex_manage)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = { if (!state.refreshing) scope.launch { manager.refresh(force = true); onMessage(resources.getString(R.string.plex_refreshed)) } },
            modifier = Modifier.weight(1f),
          ) { Text(stringResource(if (state.refreshing) R.string.plex_status_connecting else R.string.plex_refresh), maxLines = 1) }
          OutlinedButton(onClick = { if (!busy) startLink() }, modifier = Modifier.weight(1f)) {
            Text(stringResource(if (starting) R.string.plex_link_getting_code else R.string.plex_reconnect), maxLines = 1)
          }
        }
        TextButton(onClick = { if (!busy) confirmDisconnect = true }, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.plex_disconnect), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
        }
        PlexNote(stringResource(R.string.plex_revoke_note))
      }
    }
  }

  if (confirmDisconnect) {
    AlertDialog(
      onDismissRequest = { confirmDisconnect = false },
      title = { Text(stringResource(R.string.plex_disconnect_title)) },
      text = { Text(stringResource(R.string.plex_disconnect_body)) },
      confirmButton = {
        Button(onClick = { confirmDisconnect = false; act({ manager.disconnect() }, success = R.string.plex_disconnected) }) {
          Text(stringResource(R.string.plex_disconnect))
        }
      },
      dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun PlexHeaderCard(state: MediaServerUiState, signedIn: Boolean) {
  val (status, color) = when {
    !signedIn -> stringResource(R.string.plex_not_connected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    state.linked && state.needsAttention -> stringResource(R.string.plex_needs_attention) to Warning
    state.linked -> (state.accountName?.let { stringResource(R.string.plex_connected_as, it) } ?: stringResource(R.string.plex_connected)) to Positive
    else -> stringResource(R.string.plex_not_connected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
  }
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Image(painterResource(R.drawable.plex_logo), contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.media_server_plex), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(Modifier.size(8.dp).background(color, CircleShape))
          Text(status, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Text(
          stringResource(if (!signedIn) R.string.plex_signed_out_note else R.string.plex_intro_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
      }
    }
  }
}

@Composable
private fun PlexLinkCard(code: MediaServerLinkCode, expired: Boolean, onOpen: () -> Unit, onNewCode: () -> Unit, onCancel: () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      if (expired) {
        Text(stringResource(R.string.plex_link_expired), color = Warning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Button(onClick = onNewCode, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PlexGold, contentColor = Color.Black)) {
          Text(stringResource(R.string.plex_link_new_code), fontWeight = FontWeight.Bold)
        }
      } else {
        Text(stringResource(R.string.plex_link_step_code), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodyMedium)
        Text(code.code, color = PlexGold, letterSpacing = 8.sp, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PlexGold, contentColor = Color.Black)) {
          Text(stringResource(R.string.plex_open_link), fontWeight = FontWeight.Bold)
        }
        Text(
          stringResource(R.string.plex_link_phone_hint),
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
          style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Waiting)
          Text(stringResource(R.string.plex_link_waiting), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
        }
      }
      TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
  }
}

@Composable
internal fun PlexNote(text: String, indent: Boolean = false) {
  Text(
    text,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    style = MaterialTheme.typography.bodySmall,
    modifier = Modifier.padding(start = if (indent) 20.dp else 0.dp, top = 4.dp, bottom = 4.dp),
  )
}

@Composable
internal fun reachabilityLabel(server: MediaServerView): Pair<String, Color> = when {
  !server.enabled -> stringResource(R.string.plex_status_off) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
  else -> when (val reach = server.reachability) {
    is MediaServerReachability.Online -> when (reach.route) {
      MediaServerRoute.Local -> stringResource(R.string.plex_status_local) to Positive
      MediaServerRoute.Remote -> stringResource(R.string.plex_status_remote) to Positive
      MediaServerRoute.Relay -> stringResource(R.string.plex_status_relay) to Warning
    }
    MediaServerReachability.Connecting, MediaServerReachability.Unknown -> stringResource(R.string.plex_status_connecting) to Waiting
    is MediaServerReachability.Offline -> when (reach.reason) {
      OfflineReason.Unauthorized -> stringResource(R.string.plex_status_unauthorized) to Warning
      OfflineReason.NoConnections -> stringResource(R.string.plex_status_no_connections) to Warning
      OfflineReason.Unreachable -> stringResource(R.string.plex_status_offline) to Warning
    }
  }
}

/**
 * The servers and their libraries, as groups that fold, with a way to take a single library or a
 * single server off the list. Shared by the Plex and Jellyfin pages so the two behave alike.
 *
 * Removing is not disconnecting: every other server and library stays exactly as it was. What was
 * removed is kept in a "Removed" section so it can be brought back; see MediaServerListTidy.kt.
 */
@Composable
internal fun MediaServerGroups(
  provider: String,
  manager: MediaServerManager,
  state: MediaServerUiState,
  accent: Color,
  emptyNote: String?,
  onToggleServer: (MediaServerView) -> Unit,
  onToggleLibrary: (MediaServerView, MediaServerLibrary) -> Unit,
  onMessage: (String) -> Unit,
) {
  val removed by manager.removedEntries.collectAsState()
  val collapsed by manager.collapsedServers.collectAsState()
  val order by manager.serverOrder.collectAsState()
  val libraryOrder by manager.libraryOrder.collectAsState()
  val scope = rememberCoroutineScope()
  val resources = LocalContext.current.resources
  var working by remember { mutableStateOf(false) }
  var removingServer by remember { mutableStateOf<MediaServerView?>(null) }
  var removingLibrary by remember { mutableStateOf<MediaServerLibrary?>(null) }
  // In the viewer's own order: the server at the top here is the one at the top of the page.
  // Each server's libraries in the order set for that server, which is the order its rows take on the page.
  val listed = remember(provider, state.servers, removed, order, libraryOrder) {
    listedMediaServers(provider, state.servers, removed).inServerOrder(provider, order).map { it.withLibrariesInOrder(libraryOrder) }
  }
  val gone = remember(provider, state.servers, removed) { removedMediaServerEntries(provider, state.servers, removed) }

  fun perform(name: String, doneRes: Int, work: suspend () -> Boolean) {
    if (working) return
    working = true
    scope.launch {
      val ok = work()
      working = false
      onMessage(if (ok) resources.getString(doneRes, name) else resources.getString(R.string.plex_saving_failed))
    }
  }

  SettingsSection(stringResource(R.string.plex_servers)) {
    if (listed.isEmpty()) emptyNote?.let { PlexNote(it) }
    listed.forEachIndexed { index, server ->
      if (index > 0) SettingsDivider()
      val folded = mediaServerEntryKey(provider, server.id) in collapsed
      // Arrows rather than dragging: a settings page that scrolls, with switches and folding rows
      // in it, is a poor place to start a drag, and a tap is as quick for a list this short and
      // can be reached by every way of using the phone.
      val shownIds = listed.map { it.id }
      MediaServerGroupHeader(
        server = server,
        folded = folded,
        accent = accent,
        onFold = { manager.setServerCollapsed(provider, server.id, !folded) },
        onToggle = { onToggleServer(server) },
        onMoveUp = if (listed.size > 1 && index > 0) ({ manager.moveServer(provider, shownIds, server.id, -1) }) else null,
        onMoveDown = if (listed.size > 1 && index < listed.lastIndex) ({ manager.moveServer(provider, shownIds, server.id, 1) }) else null,
        reorderable = listed.size > 1,
      )
      AnimatedVisibility(visible = !folded) {
        Column {
          if (server.enabled) {
            if (server.libraries.isEmpty()) PlexNote(stringResource(R.string.plex_no_libraries), indent = true)
            // Visibility and order in one list: each library can be switched on and off, and dragged
            // by its handle to where its rows should sit on the page.
            ReorderableLibraries(
              libraries = server.libraries,
              onMove = { libraryKey, to -> manager.moveLibrary(server.id, server.libraries.map { it.key }, libraryKey, to) },
            ) { library, handle ->
              PlexLibrarySwitch(library, accent = accent, onRemove = { removingLibrary = library }, dragHandle = handle) { onToggleLibrary(server, library) }
            }
          }
          TextButton(onClick = { removingServer = server }, modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(R.string.media_server_remove_server), color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }

  if (gone.isNotEmpty()) {
    SettingsSection(stringResource(R.string.media_server_removed)) {
      PlexNote(stringResource(R.string.media_server_removed_note))
      gone.forEach { entry -> RemovedMediaServerRow(entry, accent) {
        val library = entry.library
        if (library == null) perform(entry.serverName, R.string.media_server_restored_done) { manager.restoreServer(provider, entry.serverId) }
        else perform(library.title, R.string.media_server_restored_done) { manager.restoreLibrary(provider, entry.serverId, library.key) }
      } }
    }
  }

  removingLibrary?.let { library ->
    AlertDialog(
      onDismissRequest = { removingLibrary = null },
      title = { Text(stringResource(R.string.media_server_remove_title, library.title)) },
      text = { Text(stringResource(R.string.media_server_remove_library_body)) },
      confirmButton = {
        Button(onClick = {
          removingLibrary = null
          perform(library.title, R.string.media_server_removed_done) { manager.removeLibrary(provider, library.serverId, library.key) }
        }) { Text(stringResource(R.string.media_server_remove)) }
      },
      dismissButton = { TextButton(onClick = { removingLibrary = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
  removingServer?.let { server ->
    AlertDialog(
      onDismissRequest = { removingServer = null },
      title = { Text(stringResource(R.string.media_server_remove_title, server.name)) },
      text = { Text(stringResource(if (provider == JELLYFIN_PROVIDER_ID) R.string.jellyfin_remove_server_body else R.string.media_server_remove_server_body)) },
      confirmButton = {
        Button(onClick = {
          removingServer = null
          perform(server.name, R.string.media_server_removed_done) { manager.removeServer(provider, server.id) }
        }) { Text(stringResource(R.string.media_server_remove)) }
      },
      dismissButton = { TextButton(onClick = { removingServer = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

/** A server's own row: tap it to fold or unfold its libraries; the switch turns the server on or off. */
@Composable
private fun MediaServerGroupHeader(
  server: MediaServerView,
  folded: Boolean,
  accent: Color,
  onFold: () -> Unit,
  onToggle: () -> Unit,
  onMoveUp: (() -> Unit)? = null,
  onMoveDown: (() -> Unit)? = null,
  reorderable: Boolean = false,
) {
  val (status, color) = reachabilityLabel(server)
  val owner = server.ownerName?.takeIf { !server.owned }?.let { stringResource(R.string.plex_server_shared_by, it) }
  // Folded, the row says what is inside it, so nothing has to be opened to find out.
  val count = if (folded && server.enabled && server.libraries.isNotEmpty()) {
    stringResource(R.string.media_server_libraries_on_of, server.libraries.count { it.enabled }, server.libraries.size)
  } else null
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(
      onClickLabel = stringResource(if (folded) R.string.media_server_show_libraries else R.string.media_server_hide_libraries),
      onClick = onFold,
    ).padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      StreamDekPlayerIcons.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
      modifier = Modifier.size(20.dp).rotate(if (folded) 0f else 90f),
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(server.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      Text(listOfNotNull(status, owner, count).joinToString(" · "), color = color, style = MaterialTheme.typography.bodySmall)
    }
    if (reorderable) {
      // Both always drawn, the unavailable one dimmed, so the switch never shifts sideways
      // between the first server, the middle ones and the last.
      ServerMoveButton(StreamDekPlayerIcons.ChevronUp, stringResource(R.string.media_server_move_up, server.name), onMoveUp)
      ServerMoveButton(StreamDekSettingsIcons.ChevronDown, stringResource(R.string.media_server_move_down, server.name), onMoveDown)
    }
    Switch(
      checked = server.enabled,
      onCheckedChange = { onToggle() },
      colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
    )
  }
}

/**
 * A server's libraries, each draggable by its handle to a new place - the same drag, lift and
 * accessibility actions as the Home Rows list, through its [ReorderableRow].
 *
 * Every step is saved as it happens, so the page behind Settings is already in the new order when
 * the finger lifts. Only the handle starts a drag; the rest of the row still switches the library,
 * and the page still scrolls. A server with a single library has nothing to order and shows no
 * handle.
 */
@Composable
private fun ReorderableLibraries(
  libraries: List<MediaServerLibrary>,
  onMove: (libraryKey: String, to: Int) -> Unit,
  row: @Composable (library: MediaServerLibrary, handle: (@Composable () -> Unit)?) -> Unit,
) {
  if (libraries.size < 2) {
    libraries.forEach { row(it, null) }
    return
  }
  val density = LocalDensity.current
  // About one library row: the distance the finger travels for each place the library moves.
  val stepPx = with(density) { 60.dp.toPx() }
  val currentLibraries by rememberUpdatedState(libraries)
  libraries.forEach { library ->
    androidx.compose.runtime.key(library.key) {
      ReorderableRow(
        itemKey = library.key,
        reorderThresholdPx = stepPx,
        // A server's libraries fit on a screen; the page is not scrolled under the finger.
        dragScrollBy = { 0f },
        onMove = { step ->
          val list = currentLibraries
          val from = list.indexOfFirst { it.key == library.key }
          val to = from + step
          if (from >= 0 && to in list.indices) onMove(library.key, to)
        },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
      ) { handleModifier, dragging ->
        Box(Modifier.weight(1f)) {
          row(library) {
            Box(modifier = handleModifier.size(width = 32.dp, height = 40.dp), contentAlignment = Alignment.Center) {
              Icon(
                StreamDekSettingsIcons.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.85f else 0.38f),
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ServerMoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: (() -> Unit)?) {
  IconButton(onClick = { onClick?.invoke() }, enabled = onClick != null, modifier = Modifier.size(36.dp)) {
    Icon(
      icon,
      contentDescription = description,
      tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (onClick != null) 0.8f else 0.22f),
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun RemovedMediaServerRow(entry: RemovedMediaServerEntry, accent: Color, onRestore: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(entry.library?.title ?: entry.serverName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
      Text(
        if (entry.library == null) stringResource(R.string.media_server_whole_server) else entry.serverName,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        style = MaterialTheme.typography.bodySmall,
      )
    }
    TextButton(onClick = onRestore) { Text(stringResource(R.string.media_server_restore), color = accent, fontWeight = FontWeight.Bold) }
  }
}

@Composable
internal fun PlexSwitchRow(
  title: String,
  detail: String,
  detailColor: Color,
  checked: Boolean,
  indent: Boolean,
  accent: Color = PlexGold,
  onRemove: (() -> Unit)? = null,
  /** Drawn before the title: a library's drag handle. It takes the indent's place. */
  leading: (@Composable () -> Unit)? = null,
  onToggle: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onToggle)
      .padding(start = if (leading != null) 4.dp else if (indent) 20.dp else 0.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(if (onRemove == null) 14.dp else 4.dp),
  ) {
    leading?.invoke()
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        title,
        style = if (indent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
        fontWeight = if (indent) FontWeight.Medium else FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(detail, color = detailColor, style = MaterialTheme.typography.bodySmall)
    }
    if (onRemove != null) {
      IconButton(onClick = onRemove) {
        Icon(
          StreamDekPlayerIcons.Close,
          contentDescription = stringResource(R.string.media_server_remove_named, title),
          tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
          modifier = Modifier.size(18.dp),
        )
      }
    }
    // Never disabled while saving: a second tap is ignored instead, so the switch does not flicker.
    Switch(
      checked = checked,
      onCheckedChange = { onToggle() },
      colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
    )
  }
}

@Composable
internal fun PlexServerSwitch(server: MediaServerView, accent: Color = PlexGold, onToggle: () -> Unit) {
  val (status, color) = reachabilityLabel(server)
  val owner = server.ownerName?.takeIf { !server.owned }?.let { stringResource(R.string.plex_server_shared_by, it) }
  PlexSwitchRow(server.name, listOfNotNull(status, owner).joinToString(" · "), color, server.enabled, indent = false, accent = accent, onToggle = onToggle)
}

@Composable
internal fun PlexLibrarySwitch(
  library: MediaServerLibrary,
  accent: Color = PlexGold,
  onRemove: (() -> Unit)? = null,
  dragHandle: (@Composable () -> Unit)? = null,
  onToggle: () -> Unit,
) {
  PlexSwitchRow(
    leading = dragHandle,
    title = library.title,
    detail = stringResource(
      when (library.kind) {
        MediaServerLibraryKind.Movies -> R.string.plex_library_movies
        MediaServerLibraryKind.Shows -> R.string.plex_library_shows
        MediaServerLibraryKind.Other -> R.string.plex_library_other
      },
    ),
    detailColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    checked = library.enabled,
    indent = true,
    accent = accent,
    onRemove = onRemove,
    onToggle = onToggle,
  )
}

/**
 * Where a provider's in-progress titles appear; see [MediaServerContinueLocation]. Offered on the
 * Plex and Jellyfin pages alike, in the words the television and the web portal use too, and
 * changed in place - nothing reconnects or reloads.
 */
@Composable
internal fun MediaServerContinueLocationSection(
  providerName: String,
  location: MediaServerContinueLocation,
  accent: Color,
  onChange: (MediaServerContinueLocation) -> Unit,
) {
  val options = listOf(
    Triple(MediaServerContinueLocation.ServerLibrary, R.string.media_server_continue_location_server, R.string.media_server_continue_location_server_detail),
    Triple(MediaServerContinueLocation.StreamDek, R.string.media_server_continue_location_streamdek, R.string.media_server_continue_location_streamdek_detail),
  )
  SettingsSection(stringResource(R.string.media_server_continue_location_title)) {
    PlexNote(stringResource(R.string.media_server_continue_location_detail, providerName))
    options.forEach { (option, title, detail) ->
      Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(option) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        androidx.compose.material3.RadioButton(
          selected = option == location,
          onClick = { onChange(option) },
          colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = accent),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
          Text(stringResource(detail, providerName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
      }
    }
  }
}
