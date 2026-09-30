package net.streamdek.mobile.nativeapp

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.streamdek.mobile.R
import net.streamdek.mobile.nativeapp.mediaserver.JellyfinQuickConnectCode
import net.streamdek.mobile.nativeapp.mediaserver.JellyfinServerCandidate
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLinkStatus
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerManager
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerUiState

/** Jellyfin's purple, from its logo: buttons and switches, so the page reads as Jellyfin's. */
private val JellyfinPurple = Color(0xFFAA5CC3)
private val JfPositive = Color(0xFF22C55E)
private val JfWarning = Color(0xFFF59E0B)
private val JfWaiting = Color(0xFF60A5FA)

/** Settings > Sources: the Jellyfin row. */
@Composable
internal fun JellyfinSettingsNavRow(state: MediaServerUiState, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101014)), contentAlignment = Alignment.Center) {
      Image(painterResource(R.drawable.jellyfin_logo), contentDescription = null, modifier = Modifier.size(30.dp))
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(stringResource(R.string.media_server_jellyfin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      SettingsSubtitle(
        when {
          state.linked && state.needsAttention -> stringResource(R.string.jellyfin_needs_attention)
          state.linked -> stringResource(R.string.plex_libraries_on, state.usableLibraries.size)
          else -> stringResource(R.string.plex_not_connected)
        },
      )
    }
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
  }
}

/**
 * Settings > Jellyfin, on the phone.
 *
 * Jellyfin is the viewer's own server at an address only they know, so connecting starts by finding
 * it: servers on this Wi-Fi are looked for at once, and an address can be typed instead. Signing in
 * is Quick Connect where the server allows it - approve a code from a Jellyfin app already signed
 * in - with a username and password beside it. The password goes to the server once and is never
 * kept; only the token the server issues is, encrypted on this device.
 */
@Composable
internal fun JellyfinSettingsPage(
  manager: MediaServerManager,
  signedIn: Boolean,
  onMessage: (String) -> Unit,
) {
  val state by manager.jellyfinState.collectAsState()
  val ambient by manager.jellyfinAmbient.collectAsState()
  val scope = rememberCoroutineScope()
  val resources = LocalContext.current.resources

  var adding by remember { mutableStateOf(false) }
  var searching by remember { mutableStateOf(false) }
  var found by remember { mutableStateOf<List<JellyfinServerCandidate>>(emptyList()) }
  var address by remember { mutableStateOf("") }
  var finding by remember { mutableStateOf(false) }
  var addressFailed by remember { mutableStateOf(false) }
  var chosen by remember { mutableStateOf<JellyfinServerCandidate?>(null) }
  var quickCode by remember { mutableStateOf<JellyfinQuickConnectCode?>(null) }
  var quickExpired by remember { mutableStateOf(false) }
  var startingQuick by remember { mutableStateOf(false) }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var signingIn by remember { mutableStateOf(false) }
  var confirmSignOut by remember { mutableStateOf(false) }

  val connecting = adding || !state.linked

  fun reset() {
    chosen = null
    quickCode = null
    quickExpired = false
    adding = false
    username = ""
    password = ""
  }

  fun search() {
    if (searching) return
    searching = true
    scope.launch {
      found = manager.discoverJellyfinServers()
      searching = false
    }
  }

  fun startQuickConnect(server: JellyfinServerCandidate) {
    if (startingQuick) return
    startingQuick = true
    scope.launch {
      val code = manager.startJellyfinQuickConnect(server)
      startingQuick = false
      if (code == null) onMessage(resources.getString(R.string.jellyfin_quick_connect_unavailable)) else {
        quickExpired = false
        quickCode = code
      }
    }
  }

  fun choose(server: JellyfinServerCandidate) {
    chosen = server
    quickCode = null
    if (server.quickConnect) startQuickConnect(server)
  }

  fun findAddress() {
    if (finding || address.isBlank()) return
    finding = true
    addressFailed = false
    scope.launch {
      val server = manager.findJellyfinServer(address)
      finding = false
      if (server == null) addressFailed = true else choose(server)
    }
  }

  fun finished(result: MediaServerLinkStatus) {
    when (result) {
      is MediaServerLinkStatus.Linked -> {
        reset()
        onMessage(result.accountName?.let { resources.getString(R.string.plex_connected_as, it) } ?: resources.getString(R.string.plex_connected))
      }
      MediaServerLinkStatus.Failed -> onMessage(resources.getString(R.string.jellyfin_sign_in_failed))
      else -> Unit
    }
  }

  fun signIn(server: JellyfinServerCandidate) {
    if (signingIn || username.isBlank()) return
    signingIn = true
    scope.launch {
      val result = manager.signInToJellyfin(server, username, password)
      signingIn = false
      // The password is only ever held while it is being sent.
      if (result is MediaServerLinkStatus.Linked) password = ""
      finished(result)
    }
  }

  LaunchedEffect(connecting, signedIn) {
    if (connecting && signedIn && found.isEmpty()) search()
  }

  LaunchedEffect(quickCode) {
    val code = quickCode ?: return@LaunchedEffect
    while (true) {
      delay(3_000)
      when (val result = manager.pollJellyfinQuickConnect(code)) {
        MediaServerLinkStatus.Pending -> Unit
        MediaServerLinkStatus.Expired -> { quickExpired = true; return@LaunchedEffect }
        else -> { finished(result); return@LaunchedEffect }
      }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    JellyfinHeaderCard(state, signedIn)

    if (signedIn && connecting) {
      val server = chosen
      if (server == null) {
        SettingsSection(stringResource(R.string.jellyfin_find_server)) {
          if (searching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = JellyfinPurple)
              Text(stringResource(R.string.jellyfin_searching), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
          }
          found.forEach { candidate ->
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { choose(candidate) }.padding(vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Image(painterResource(R.drawable.jellyfin_logo), contentDescription = null, modifier = Modifier.size(28.dp))
              Column(Modifier.weight(1f)) {
                Text(candidate.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                SettingsSubtitle(listOfNotNull(candidate.url.removePrefix("http://").removePrefix("https://"), candidate.version?.let { "Jellyfin $it" }).joinToString(" · "))
              }
              Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
            }
          }
          if (!searching && found.isEmpty()) PlexNote(stringResource(R.string.jellyfin_none_found))
          TextButton(onClick = ::search) { Text(stringResource(R.string.jellyfin_search_network), color = JellyfinPurple, fontWeight = FontWeight.Bold) }
        }
        SettingsSection(stringResource(R.string.jellyfin_enter_address)) {
          OutlinedTextField(
            value = address,
            onValueChange = { address = it; addressFailed = false },
            singleLine = true,
            placeholder = { Text("jellyfin.example.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { findAddress() }),
            modifier = Modifier.fillMaxWidth(),
          )
          if (addressFailed) Text(stringResource(R.string.jellyfin_address_not_found), color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
          PlexNote(stringResource(R.string.jellyfin_address_hint))
          Button(
            onClick = ::findAddress,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = JellyfinPurple, contentColor = Color.White),
          ) {
            if (finding) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            Text(stringResource(if (finding) R.string.jellyfin_searching else R.string.jellyfin_connect_address), modifier = Modifier.padding(start = if (finding) 8.dp else 0.dp), fontWeight = FontWeight.Bold)
          }
          if (adding) TextButton(onClick = ::reset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
      } else {
        SettingsSection(stringResource(R.string.jellyfin_sign_in_to, server.name)) {
          val code = quickCode
          if (server.quickConnect) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
              Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                  code == null -> Button(
                    onClick = { startQuickConnect(server) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = JellyfinPurple, contentColor = Color.White),
                  ) { Text(stringResource(if (startingQuick) R.string.plex_link_getting_code else R.string.jellyfin_use_quick_connect), fontWeight = FontWeight.Bold) }
                  quickExpired -> {
                    Text(stringResource(R.string.plex_link_expired), color = JfWarning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Button(
                      onClick = { startQuickConnect(server) },
                      modifier = Modifier.fillMaxWidth(),
                      colors = ButtonDefaults.buttonColors(containerColor = JellyfinPurple, contentColor = Color.White),
                    ) { Text(stringResource(R.string.plex_link_new_code), fontWeight = FontWeight.Bold) }
                  }
                  else -> {
                    Text(stringResource(R.string.jellyfin_quick_connect_step), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodyMedium)
                    Text(code.code, color = JellyfinPurple, letterSpacing = 8.sp, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = JfWaiting)
                      Text(stringResource(R.string.jellyfin_link_waiting), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                    }
                  }
                }
              }
            }
          } else {
            PlexNote(stringResource(R.string.jellyfin_quick_connect_off))
          }
          Text(stringResource(R.string.jellyfin_use_password), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
          OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            singleLine = true,
            label = { Text(stringResource(R.string.jellyfin_username)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            label = { Text(stringResource(R.string.jellyfin_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { signIn(server) }),
            modifier = Modifier.fillMaxWidth(),
          )
          Button(
            onClick = { signIn(server) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = JellyfinPurple, contentColor = Color.White),
          ) {
            if (signingIn) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            Text(stringResource(R.string.jellyfin_sign_in), modifier = Modifier.padding(start = if (signingIn) 8.dp else 0.dp), fontWeight = FontWeight.Bold)
          }
          TextButton(onClick = { chosen = null; quickCode = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.jellyfin_other_server)) }
        }
      }
    }

    if (signedIn && state.linked && !adding) {
      SettingsSection(stringResource(R.string.plex_servers)) {
        state.servers.forEachIndexed { index, server ->
          if (index > 0) SettingsDivider()
          PlexServerSwitch(server, accent = JellyfinPurple) { manager.setJellyfinServerEnabled(server.id, !server.enabled) }
          if (server.enabled) {
            if (server.libraries.isEmpty()) PlexNote(stringResource(R.string.plex_no_libraries), indent = true)
            server.libraries.forEach { library ->
              PlexLibrarySwitch(library, accent = JellyfinPurple) { manager.setJellyfinLibraryEnabled(server.id, library.key, !library.enabled) }
            }
          }
        }
      }
      SettingsSection(stringResource(R.string.jellyfin_page_section)) {
        PlexSwitchRow(
          title = stringResource(R.string.plex_ambient),
          detail = stringResource(R.string.jellyfin_ambient_detail),
          detailColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
          checked = ambient,
          indent = false,
          accent = JellyfinPurple,
          onToggle = { manager.setJellyfinAmbient(!ambient) },
        )
      }
      SettingsSection(stringResource(R.string.plex_manage)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = { if (!state.refreshing) scope.launch { manager.refreshJellyfin(force = true); onMessage(resources.getString(R.string.jellyfin_refreshed)) } },
            modifier = Modifier.weight(1f),
          ) { Text(stringResource(if (state.refreshing) R.string.plex_status_connecting else R.string.plex_refresh), maxLines = 1) }
          OutlinedButton(onClick = { adding = true; chosen = null }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.jellyfin_add_server), maxLines = 1)
          }
        }
        TextButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.jellyfin_sign_out), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
        }
        PlexNote(stringResource(R.string.jellyfin_sign_out_note))
      }
    }
    PlexNote(stringResource(R.string.jellyfin_logo_credit))
  }

  if (confirmSignOut) {
    AlertDialog(
      onDismissRequest = { confirmSignOut = false },
      title = { Text(stringResource(R.string.jellyfin_sign_out_title)) },
      text = { Text(stringResource(R.string.jellyfin_sign_out_body)) },
      confirmButton = {
        Button(onClick = {
          confirmSignOut = false
          scope.launch {
            manager.disconnectJellyfin()
            onMessage(resources.getString(R.string.jellyfin_signed_out))
          }
        }) { Text(stringResource(R.string.jellyfin_sign_out)) }
      },
      dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun JellyfinHeaderCard(state: MediaServerUiState, signedIn: Boolean) {
  val (status, color) = when {
    !signedIn -> stringResource(R.string.plex_not_connected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    state.linked && state.needsAttention -> stringResource(R.string.jellyfin_needs_attention) to JfWarning
    state.linked -> (state.accountName?.let { stringResource(R.string.plex_connected_as, it) } ?: stringResource(R.string.plex_connected)) to JfPositive
    else -> stringResource(R.string.plex_not_connected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
  }
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Image(painterResource(R.drawable.jellyfin_logo), contentDescription = null, modifier = Modifier.size(52.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.media_server_jellyfin), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(Modifier.size(8.dp).background(color, CircleShape))
          Text(status, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Text(
          stringResource(if (!signedIn) R.string.jellyfin_signed_out_note else R.string.jellyfin_intro_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
      }
    }
  }
}
