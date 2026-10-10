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
import net.streamdek.mobile.nativeapp.mediaserver.JellyfinBackupServer
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerManager
import net.streamdek.mobile.nativeapp.mediaserver.MediaBrowserAccounts
import net.streamdek.mobile.nativeapp.mediaserver.EmbyConnectResult
import net.streamdek.mobile.nativeapp.mediaserver.EmbyConnectSignIn
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerUiState

/**
 * The sentences on a Jellyfin or Emby settings page that name the server. Everything else on the
 * page is the same words for both; see [MediaBrowserSettingsPage].
 */
internal data class MediaBrowserPageText(
  val refreshed: Int,
  val signedOutNote: Int,
  val introBody: Int,
  val needsAttention: Int,
  val noneFound: Int,
  val signInFailed: Int,
  val pageSection: Int,
  val ambientDetail: Int,
  val signOut: Int,
  val signOutNote: Int,
  val signOutTitle: Int,
  val signOutBody: Int,
  val signedOut: Int,
  val addressHint: Int,
  val addressNotFound: Int,
  /** Shown where Quick Connect would be when the server has none. */
  val noQuickConnect: Int,
  /** The logo's licence line, for a logo that needs one. */
  val logoCredit: Int?,
  val addressPlaceholder: String,
)

private val JellyfinPageText = MediaBrowserPageText(
  refreshed = R.string.jellyfin_refreshed,
  signedOutNote = R.string.jellyfin_signed_out_note,
  introBody = R.string.jellyfin_intro_body,
  needsAttention = R.string.jellyfin_needs_attention,
  noneFound = R.string.jellyfin_none_found,
  signInFailed = R.string.jellyfin_sign_in_failed,
  pageSection = R.string.jellyfin_page_section,
  ambientDetail = R.string.jellyfin_ambient_detail,
  signOut = R.string.jellyfin_sign_out,
  signOutNote = R.string.jellyfin_sign_out_note,
  signOutTitle = R.string.jellyfin_sign_out_title,
  signOutBody = R.string.jellyfin_sign_out_body,
  signedOut = R.string.jellyfin_signed_out,
  addressHint = R.string.jellyfin_address_hint,
  addressNotFound = R.string.jellyfin_address_not_found,
  noQuickConnect = R.string.jellyfin_quick_connect_off,
  logoCredit = R.string.jellyfin_logo_credit,
  addressPlaceholder = "jellyfin.example.com",
)

private val EmbyPageText = MediaBrowserPageText(
  refreshed = R.string.emby_refreshed,
  signedOutNote = R.string.emby_signed_out_note,
  introBody = R.string.emby_intro_body,
  needsAttention = R.string.emby_needs_attention,
  noneFound = R.string.emby_none_found,
  signInFailed = R.string.emby_sign_in_failed,
  pageSection = R.string.emby_page_section,
  ambientDetail = R.string.emby_ambient_detail,
  signOut = R.string.emby_sign_out,
  signOutNote = R.string.emby_sign_out_note,
  signOutTitle = R.string.emby_sign_out_title,
  signOutBody = R.string.emby_sign_out_body,
  signedOut = R.string.emby_signed_out,
  addressHint = R.string.emby_address_hint,
  addressNotFound = R.string.emby_address_not_found,
  noQuickConnect = R.string.emby_password_note,
  logoCredit = null,
  addressPlaceholder = "emby.example.com",
)
private val JfPositive = Color(0xFF22C55E)
private val JfWarning = Color(0xFFF59E0B)
private val JfWaiting = Color(0xFF60A5FA)

/** Settings > Sources: the Jellyfin row. */
@Composable
internal fun JellyfinSettingsNavRow(state: MediaServerUiState, onClick: () -> Unit) =
  MediaBrowserSettingsNavRow(net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID, JellyfinPageText, state, onClick)

/** Settings > Sources: the Emby row. */
@Composable
internal fun EmbySettingsNavRow(state: MediaServerUiState, onClick: () -> Unit) =
  MediaBrowserSettingsNavRow(net.streamdek.mobile.nativeapp.mediaserver.EMBY_PROVIDER_ID, EmbyPageText, state, onClick)

@Composable
private fun MediaBrowserSettingsNavRow(provider: String, text: MediaBrowserPageText, state: MediaServerUiState, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101014)), contentAlignment = Alignment.Center) {
      MediaServerLogo(provider, 30.dp)
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(stringResource(mediaServerBrand(provider).name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      SettingsSubtitle(
        when {
          state.linked && state.needsAttention -> stringResource(text.needsAttention)
          state.linked -> stringResource(R.string.plex_libraries_on, state.usableLibraries.size)
          else -> stringResource(R.string.plex_not_connected)
        },
      )
    }
    Icon(StreamDekSettingsIcons.Forward, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
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
  continueLocation: MediaServerContinueLocation = MediaServerContinueLocation.StreamDek,
  onContinueLocationChange: (MediaServerContinueLocation) -> Unit = {},
) = MediaBrowserSettingsPage(manager, manager.jellyfinAccounts, JellyfinPageText, signedIn, onMessage, continueLocation, onContinueLocationChange)

/**
 * Settings > Emby: the Jellyfin page in Emby's terms. Emby has no Quick Connect, so signing in is a
 * username and password - or Emby Connect, which lists the servers on the viewer's emby.media
 * account and signs in to the one they pick.
 */
@Composable
internal fun EmbySettingsPage(
  manager: MediaServerManager,
  signedIn: Boolean,
  onMessage: (String) -> Unit,
  continueLocation: MediaServerContinueLocation = MediaServerContinueLocation.StreamDek,
  onContinueLocationChange: (MediaServerContinueLocation) -> Unit = {},
) = MediaBrowserSettingsPage(manager, manager.embyAccounts, EmbyPageText, signedIn, onMessage, continueLocation, onContinueLocationChange)

/** One page for either member of the family; see [net.streamdek.mobile.nativeapp.mediaserver.jellyfin.MediaBrowserFlavor]. */
@Composable
private fun MediaBrowserSettingsPage(
  manager: MediaServerManager,
  accounts: MediaBrowserAccounts,
  text: MediaBrowserPageText,
  signedIn: Boolean,
  onMessage: (String) -> Unit,
  continueLocation: MediaServerContinueLocation,
  onContinueLocationChange: (MediaServerContinueLocation) -> Unit,
) {
  val provider = accounts.flavor.providerId
  val accent = mediaServerBrand(provider).accent
  val providerName = stringResource(mediaServerBrand(provider).name)
  val state by accounts.state.collectAsState()
  val ambient by accounts.ambient.collectAsState()
  val restored by accounts.restored.collectAsState()
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
  // Emby Connect: the account's sign-in is held only while the viewer picks servers from it.
  var connectUser by remember { mutableStateOf("") }
  var connectPassword by remember { mutableStateOf("") }
  var connectBusy by remember { mutableStateOf(false) }
  var connectSignIn by remember { mutableStateOf<EmbyConnectSignIn?>(null) }

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
      found = accounts.discoverServers()
      searching = false
    }
  }

  fun startQuickConnect(server: JellyfinServerCandidate) {
    if (startingQuick) return
    startingQuick = true
    scope.launch {
      val code = accounts.startQuickConnect(server)
      startingQuick = false
      if (code == null) onMessage(resources.getString(R.string.jellyfin_quick_connect_unavailable)) else {
        quickExpired = false
        quickCode = code
      }
    }
  }

  fun choose(server: JellyfinServerCandidate) {
    // The other member of the family answered: say where it belongs instead of failing at sign-in.
    server.sibling?.let { sibling ->
      onMessage(resources.getString(R.string.media_server_sibling, sibling))
      return
    }
    chosen = server
    quickCode = null
    if (server.quickConnect) startQuickConnect(server)
  }

  fun signInToEmbyConnect() {
    if (connectBusy || connectUser.isBlank()) return
    connectBusy = true
    scope.launch {
      val result = accounts.embyConnect(connectUser, connectPassword)
      connectBusy = false
      // The emby.media password is only ever held while it is being sent.
      connectPassword = ""
      when (result) {
        is EmbyConnectResult.SignedIn -> {
          connectSignIn = result.signIn
          if (result.signIn.choices.isEmpty()) onMessage(resources.getString(R.string.emby_connect_none))
        }
        EmbyConnectResult.Refused -> onMessage(resources.getString(R.string.emby_connect_refused))
        else -> onMessage(resources.getString(R.string.emby_connect_unreachable))
      }
    }
  }

  fun addFromEmbyConnect(signIn: EmbyConnectSignIn, systemId: String, name: String) {
    if (connectBusy) return
    connectBusy = true
    scope.launch {
      val result = accounts.addEmbyConnectServer(signIn, systemId)
      connectBusy = false
      when (result) {
        is MediaServerLinkStatus.Linked -> {
          connectSignIn = null
          connectUser = ""
          reset()
          onMessage(result.accountName?.let { resources.getString(R.string.plex_connected_as, it) } ?: resources.getString(R.string.plex_connected))
        }
        else -> onMessage(resources.getString(R.string.emby_connect_add_failed, name))
      }
    }
  }

  fun findAddress() {
    if (finding || address.isBlank()) return
    finding = true
    addressFailed = false
    scope.launch {
      val server = accounts.findServer(address)
      finding = false
      if (server == null) addressFailed = true else choose(server)
    }
  }

  /** A server a backup brought back: found at the addresses it had, with its user filled in. */
  fun resume(server: JellyfinBackupServer) {
    if (finding) return
    finding = true
    addressFailed = false
    scope.launch {
      val candidate = server.addresses.firstNotNullOfOrNull { accounts.findServer(it) }
      finding = false
      if (candidate == null) {
        adding = true
        address = server.addresses.firstOrNull().orEmpty()
        addressFailed = true
      } else {
        adding = true
        username = server.userName.orEmpty()
        choose(candidate)
      }
    }
  }

  fun finished(result: MediaServerLinkStatus) {
    when (result) {
      is MediaServerLinkStatus.Linked -> {
        reset()
        onMessage(result.accountName?.let { resources.getString(R.string.plex_connected_as, it) } ?: resources.getString(R.string.plex_connected))
      }
      MediaServerLinkStatus.Failed -> onMessage(resources.getString(text.signInFailed))
      else -> Unit
    }
  }

  fun signIn(server: JellyfinServerCandidate) {
    if (signingIn || username.isBlank()) return
    signingIn = true
    scope.launch {
      val result = accounts.signIn(server, username, password)
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
      when (val result = accounts.pollQuickConnect(code)) {
        MediaServerLinkStatus.Pending -> Unit
        MediaServerLinkStatus.Expired -> { quickExpired = true; return@LaunchedEffect }
        else -> { finished(result); return@LaunchedEffect }
      }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    MediaBrowserHeaderCard(provider, text, state, signedIn)

    if (signedIn && chosen == null && restored.isNotEmpty()) {
      SettingsSection(stringResource(R.string.jellyfin_restored_title)) {
        PlexNote(stringResource(R.string.jellyfin_restored_note))
        restored.forEach { entry ->
          Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { resume(entry) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            MediaServerLogo(provider, 28.dp)
            Column(Modifier.weight(1f)) {
              Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
              SettingsSubtitle(listOfNotNull(entry.addresses.firstOrNull()?.removePrefix("http://")?.removePrefix("https://"), entry.userName).joinToString(" · "))
            }
            TextButton(onClick = { accounts.dismissRestored(entry.id) }) {
              Text(stringResource(R.string.jellyfin_restored_dismiss), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
          }
        }
      }
    }
    if (signedIn && connecting) {
      val server = chosen
      if (server == null) {
        SettingsSection(stringResource(R.string.jellyfin_find_server)) {
          if (searching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
              Text(stringResource(R.string.jellyfin_searching), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
          }
          found.forEach { candidate ->
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { choose(candidate) }.padding(vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              MediaServerLogo(provider, 28.dp)
              Column(Modifier.weight(1f)) {
                Text(candidate.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                SettingsSubtitle(listOfNotNull(candidate.url.removePrefix("http://").removePrefix("https://"), candidate.version?.let { "$providerName $it" }).joinToString(" · "))
              }
              Icon(StreamDekSettingsIcons.Forward, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
            }
          }
          if (!searching && found.isEmpty()) PlexNote(stringResource(text.noneFound))
          TextButton(onClick = ::search) { Text(stringResource(R.string.jellyfin_search_network), color = accent, fontWeight = FontWeight.Bold) }
        }
        SettingsSection(stringResource(R.string.jellyfin_enter_address)) {
          OutlinedTextField(
            value = address,
            onValueChange = { address = it; addressFailed = false },
            singleLine = true,
            placeholder = { Text(text.addressPlaceholder) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { findAddress() }),
            modifier = Modifier.fillMaxWidth(),
          )
          if (addressFailed) Text(stringResource(text.addressNotFound), color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
          PlexNote(stringResource(text.addressHint))
          Button(
            onClick = ::findAddress,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
          ) {
            if (finding) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            Text(stringResource(if (finding) R.string.jellyfin_searching else R.string.jellyfin_connect_address), modifier = Modifier.padding(start = if (finding) 8.dp else 0.dp), fontWeight = FontWeight.Bold)
          }
          if (adding) TextButton(onClick = ::reset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
        if (accounts.flavor.embyConnect) {
          SettingsSection(stringResource(R.string.emby_connect_title)) {
            val signIn = connectSignIn
            if (signIn == null) {
              PlexNote(stringResource(R.string.emby_connect_note))
              OutlinedTextField(
                value = connectUser,
                onValueChange = { connectUser = it },
                singleLine = true,
                label = { Text(stringResource(R.string.emby_connect_user)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = connectPassword,
                onValueChange = { connectPassword = it },
                singleLine = true,
                label = { Text(stringResource(R.string.jellyfin_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { signInToEmbyConnect() }),
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedButton(onClick = ::signInToEmbyConnect, modifier = Modifier.fillMaxWidth()) {
                if (connectBusy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                Text(stringResource(R.string.emby_connect_sign_in), modifier = Modifier.padding(start = if (connectBusy) 8.dp else 0.dp), color = accent, fontWeight = FontWeight.Bold)
              }
            } else {
              Text(stringResource(R.string.emby_connect_servers), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
              if (signIn.choices.isEmpty()) PlexNote(stringResource(R.string.emby_connect_none))
              signIn.choices.forEach { (systemId, name) ->
                Row(
                  modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { addFromEmbyConnect(signIn, systemId, name) }.padding(vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  MediaServerLogo(provider, 28.dp)
                  Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                  if (connectBusy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                  else Icon(StreamDekSettingsIcons.Forward, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
                }
              }
              TextButton(onClick = { connectSignIn = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
            }
          }
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
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                  ) { Text(stringResource(if (startingQuick) R.string.plex_link_getting_code else R.string.jellyfin_use_quick_connect), fontWeight = FontWeight.Bold) }
                  quickExpired -> {
                    Text(stringResource(R.string.plex_link_expired), color = JfWarning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Button(
                      onClick = { startQuickConnect(server) },
                      modifier = Modifier.fillMaxWidth(),
                      colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                    ) { Text(stringResource(R.string.plex_link_new_code), fontWeight = FontWeight.Bold) }
                  }
                  else -> {
                    Text(stringResource(R.string.jellyfin_quick_connect_step), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodyMedium)
                    Text(code.code, color = accent, letterSpacing = 8.sp, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = JfWaiting)
                      Text(stringResource(R.string.jellyfin_link_waiting), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                    }
                  }
                }
              }
            }
          } else {
            PlexNote(stringResource(text.noQuickConnect))
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
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
          ) {
            if (signingIn) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            Text(stringResource(R.string.jellyfin_sign_in), modifier = Modifier.padding(start = if (signingIn) 8.dp else 0.dp), fontWeight = FontWeight.Bold)
          }
          TextButton(onClick = { chosen = null; quickCode = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.jellyfin_other_server)) }
        }
      }
    }

    if (signedIn && state.linked && !adding) {
      MediaServerGroups(
        provider = provider,
        manager = manager,
        state = state,
        accent = accent,
        emptyNote = null,
        onToggleServer = { server -> accounts.setServerEnabled(server.id, !server.enabled) },
        onToggleLibrary = { server, library -> accounts.setLibraryEnabled(server.id, library.key, !library.enabled) },
        onMessage = onMessage,
      )
      SettingsSection(stringResource(text.pageSection)) {
        PlexSwitchRow(
          title = stringResource(R.string.plex_ambient),
          detail = stringResource(text.ambientDetail),
          detailColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
          checked = ambient,
          indent = false,
          accent = accent,
          onToggle = { accounts.setAmbient(!ambient) },
        )
      }
      MediaServerContinueLocationSection(providerName, continueLocation, accent, onContinueLocationChange)
      SettingsSection(stringResource(R.string.plex_manage)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = { if (!state.refreshing) scope.launch { accounts.refresh(force = true); onMessage(resources.getString(text.refreshed)) } },
            modifier = Modifier.weight(1f),
          ) { Text(stringResource(if (state.refreshing) R.string.plex_status_connecting else R.string.plex_refresh), maxLines = 1) }
          OutlinedButton(onClick = { adding = true; chosen = null }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.jellyfin_add_server), maxLines = 1)
          }
        }
        TextButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(text.signOut), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
        }
        PlexNote(stringResource(text.signOutNote))
      }
    }
    text.logoCredit?.let { PlexNote(stringResource(it)) }
  }

  if (confirmSignOut) {
    AlertDialog(
      onDismissRequest = { confirmSignOut = false },
      title = { Text(stringResource(text.signOutTitle)) },
      text = { Text(stringResource(text.signOutBody)) },
      confirmButton = {
        Button(onClick = {
          confirmSignOut = false
          scope.launch {
            accounts.disconnect()
            onMessage(resources.getString(text.signedOut))
          }
        }) { Text(stringResource(text.signOut)) }
      },
      dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun MediaBrowserHeaderCard(provider: String, text: MediaBrowserPageText, state: MediaServerUiState, signedIn: Boolean) {
  val (status, color) = when {
    !signedIn -> stringResource(R.string.plex_not_connected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    state.linked && state.needsAttention -> stringResource(text.needsAttention) to JfWarning
    state.linked -> (state.accountName?.let { stringResource(R.string.plex_connected_as, it) } ?: stringResource(R.string.plex_connected)) to JfPositive
    else -> stringResource(R.string.plex_not_connected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
  }
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      MediaServerLogo(provider, 52.dp)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(mediaServerBrand(provider).name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(Modifier.size(8.dp).background(color, CircleShape))
          Text(status, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Text(
          stringResource(if (!signedIn) text.signedOutNote else text.introBody),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
      }
    }
  }
}
