package net.streamdek.mobile.nativeapp.mediaserver

import android.content.Context
import android.util.Log
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinAccount
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinClient
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinClientIdentity
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinProvider
import com.google.gson.Gson
import net.streamdek.mobile.nativeapp.mediaserver.plex.PlexClient
import net.streamdek.mobile.nativeapp.mediaserver.plex.PlexClientIdentity
import net.streamdek.mobile.nativeapp.mediaserver.plex.PlexProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * The media-server integration for the whole app: linking, discovery, choices, and the provider
 * that talks to the servers.
 *
 * Split along the line the brief draws. StreamDek's backend owns the *account* - the plex.tv link,
 * the credential and its renewal, the list of servers the account can use, and which of them and
 * which libraries each profile switched on - so a second television on the same profile starts with
 * Plex already there. The device owns the *servers* - reaching them, reading them, playing from
 * them - because they are on the viewer's network and nothing else can.
 *
 * [state] is what the navigation, the settings page and the Plex page read. It is restored from the
 * device's encrypted snapshot before any network call, so the Plex destination does not blink into
 * existence a second after launch.
 */
/**
 * StreamDek's API, as the manager needs it: an authenticated, profile-scoped request that answers
 * with the body, or null when it did not succeed. Supplied by the app, which owns the session.
 */
interface MediaServerBackend {
    suspend fun request(method: String, path: String, body: Map<String, Any?>?): String?
}

class MediaServerManager internal constructor(
    private val context: Context?,
    private val backend: MediaServerBackend,
    /** "<user>:<profile>", or null when nobody is signed in. */
    private val scopeKey: () -> String?,
    private val identity: () -> PlexClientIdentity,
    labels: () -> MediaServerLabels,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private suspend fun <T> call(type: Class<T>, method: String, path: String, body: Map<String, Any?>? = null): T? {
        val raw = runCatching { backend.request(method, path, body) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull() ?: return null
        // Never logged on failure: a server list carries per-server tokens.
        return runCatching { gson.fromJson(raw.ifBlank { "{}" }, type) }.getOrNull()
    }
    private val vault = context?.let { MediaServerVault(it) }
    private val refreshMutex = Mutex()
    private var activeScope: String? = null
    private var refreshJob: Job? = null
    /** The servers as last discovered, kept here so choices can be edited and saved. */
    private val plexServers = linkedMapOf<String, DiscoveredMediaServer>()

    /** Addresses that answered, per server, remembered between launches. */
    private val preferredUris = ConcurrentHashMap<String, String>()

    private val _state = MutableStateFlow(MediaServerUiState())
    val state: StateFlow<MediaServerUiState> = _state.asStateFlow()

    /** Bumped whenever what Plex rows would show may have changed, so Home knows to rebuild. */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    internal val plex: PlexProvider = PlexProvider(
        client = PlexClient(identity),
        labels = labels,
        onStateChanged = { publishServers() },
    ).also { provider ->
        provider.onEndpointChosen = { serverId, uri -> preferredUris[serverId] = uri }
    }

    private val displayPrefs = context?.getSharedPreferences(DISPLAY_PREFS, Context.MODE_PRIVATE)

    private val jellyfinClient = JellyfinClient({
        val plexIdentity = identity()
        JellyfinClientIdentity(
            client = PlexProvider.PLEX_PRODUCT,
            deviceName = plexIdentity.deviceName,
            deviceId = plexIdentity.clientIdentifier,
            version = plexIdentity.version,
        )
    })

    internal val jellyfin: JellyfinProvider = JellyfinProvider(
        client = jellyfinClient,
        labels = labels,
        onStateChanged = { publishJellyfin() },
    ).also { provider ->
        provider.onAddressChosen = { serverId, url -> jellyfinPreferred[serverId] = url }
    }

    /** Jellyfin's standing, in the same shape as Plex's [state], for its settings page and destination. */
    private val _jellyfinState = MutableStateFlow(MediaServerUiState(provider = JELLYFIN_PROVIDER_ID))
    val jellyfinState: StateFlow<MediaServerUiState> = _jellyfinState.asStateFlow()
    private val jellyfinPreferred = ConcurrentHashMap<String, String>()

    private val _jellyfinAmbient = MutableStateFlow(displayPrefs?.getBoolean(KEY_JELLYFIN_AMBIENT, true) ?: true)
    /** Whether the Jellyfin page and its lists wear the Jellyfin colour wash. On until switched off. */
    val jellyfinAmbient: StateFlow<Boolean> = _jellyfinAmbient.asStateFlow()

    /** Which server the media page showed last, so it opens there again when both are connected. */
    var lastPageProvider: String
        get() = displayPrefs?.getString(KEY_LAST_PAGE_PROVIDER, null) ?: PLEX_PROVIDER_ID
        set(value) { displayPrefs?.edit()?.putString(KEY_LAST_PAGE_PROVIDER, value)?.apply() }

    fun setJellyfinAmbient(enabled: Boolean) {
        _jellyfinAmbient.value = enabled
        displayPrefs?.edit()?.putBoolean(KEY_JELLYFIN_AMBIENT, enabled)?.apply()
    }

    private val providers: Map<String, MediaServerProvider> = mapOf(PLEX_PROVIDER_ID to plex, JELLYFIN_PROVIDER_ID to jellyfin)

    fun provider(id: String): MediaServerProvider? = providers[id]

    fun providerFor(ref: MediaServerReference): MediaServerProvider? = providers[ref.provider]

    /** Every provider with something linked, for merged reads (search, continue watching, Home). */
    fun activeProviders(): List<MediaServerProvider> = buildList {
        if (_state.value.navigationVisible || _state.value.linked) add(plex)
        if (_jellyfinState.value.linked) add(jellyfin)
    }

    /** The providers whose own page the navigation offers, in the order the page's switch lists them. */
    fun navigableProviders(): List<String> = buildList {
        if (_state.value.navigationVisible) add(PLEX_PROVIDER_ID)
        if (_jellyfinState.value.navigationVisible) add(JELLYFIN_PROVIDER_ID)
    }

    /** The state of one provider, by id. */
    fun stateOf(provider: String): StateFlow<MediaServerUiState> = if (provider == JELLYFIN_PROVIDER_ID) jellyfinState else state

    // ── Restore and refresh ─────────────────────────────────────────────────────────────────────

    /**
     * Called when the app starts, the viewer signs in or out, or the profile changes. Shows what the
     * device remembers at once, then asks StreamDek for the current picture in the background.
     */
    fun onSessionChanged() {
        val key = scopeKey()
        if (key == activeScope && _state.value.available == (key != null)) return
        activeScope = key
        refreshJob?.cancel()
        plex.reset()
        jellyfin.reset()
        MediaServerAuth.clear()
        preferredUris.clear()
        jellyfinPreferred.clear()
        if (key == null) {
            _state.value = MediaServerUiState(available = false)
            _jellyfinState.value = MediaServerUiState(provider = JELLYFIN_PROVIDER_ID, available = false)
            bump()
            return
        }
        restore(key)
        restoreJellyfin(key)
        refreshJob = scope.launch {
            launch { refreshJellyfin(force = false) }
            refresh(force = false)
        }
    }

    private fun restore(key: String) {
        val snapshot = vault?.load(key)
        if (snapshot == null || snapshot.linked != true) {
            _state.value = MediaServerUiState(available = true)
            return
        }
        val servers = snapshot.servers.orEmpty().mapNotNull { stored -> stored.toDiscovered() }
        snapshot.servers.orEmpty().forEach { stored -> stored.preferredUri?.let { uri -> stored.id?.let { preferredUris[it] = uri } } }
        plex.setServers(servers)
        snapshot.servers.orEmpty().forEach { stored ->
            val id = stored.id ?: return@forEach
            plex.seedLibraries(id, stored.libraries.orEmpty().mapNotNull { library ->
                val libraryKey = library.key ?: return@mapNotNull null
                val kind = runCatching { MediaServerLibraryKind.valueOf(library.kind ?: "") }.getOrNull() ?: return@mapNotNull null
                MediaServerLibrary(
                    serverId = id,
                    key = libraryKey,
                    title = library.title ?: libraryKey,
                    kind = kind,
                    enabled = stored.libraryChoices?.get(libraryKey) ?: (kind != MediaServerLibraryKind.Other),
                    itemCount = library.itemCount,
                )
            })
        }
        _state.value = MediaServerUiState(
            available = true,
            linked = true,
            needsAttention = snapshot.needsAttention == true,
            accountName = snapshot.accountName,
            accountThumb = snapshot.accountThumb,
        )
        publishServers()
        bump()
    }

    /** Asks StreamDek which servers this profile can use, then reaches them. Safe to call often. */
    suspend fun refresh(force: Boolean) {
        val key = scopeKey() ?: return
        refreshMutex.withLock {
            if (key != activeScope) return
            _state.update { it.copy(refreshing = true, error = null) }
            try {
                val status = call(MediaServerStatusDto::class.java, "GET", "/media-servers/$PLEX_PROVIDER_ID")
                if (status == null) {
                    // StreamDek unreachable: keep what the device remembers and carry on with it.
                    _state.update { it.copy(refreshing = false) }
                    if (_state.value.linked) plex.connect(force)
                    return
                }
                if (status.connected != true) {
                    unlinkLocally(key)
                    return
                }
                val discovery = call(MediaServerServersDto::class.java, "GET", "/media-servers/$PLEX_PROVIDER_ID/servers" + if (force) "?refresh=1" else "")
                val needsAttention = status.status == "needs_attention" || discovery == null && status.status != "connected"
                _state.update {
                    it.copy(
                        available = true,
                        linked = true,
                        needsAttention = needsAttention,
                        accountName = status.account?.name ?: status.account?.username,
                        accountThumb = status.account?.thumb,
                    )
                }
                if (discovery != null) {
                    plex.setServers(discovery.servers.orEmpty().mapNotNull { it.toDiscovered() })
                }
                plex.connect(force)
                discovery?.servers.orEmpty().filter { it.enabled != false }.mapNotNull { it.id }.forEach { serverId ->
                    runCatching { plex.libraries(serverId, force) }
                }
                persist(key)
                bump()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w("StreamDekMediaServers", "refresh failed: ${error.javaClass.simpleName}")
            } finally {
                _state.update { it.copy(refreshing = false) }
                publishServers()
            }
        }
    }

    fun refreshInBackground(force: Boolean = false) {
        scope.launch { refresh(force) }
        scope.launch { refreshJellyfin(force) }
    }

    private fun unlinkLocally(key: String) {
        plex.reset()
        MediaServerAuth.clear()
        // Plex going does not sign the device out of Jellyfin.
        jellyfin.registerAuth()
        vault?.remove(key)
        _state.value = MediaServerUiState(available = true)
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    /** The provider's view of each server, folded into [state]. */
    private fun publishServers() {
        val views = plexServers.values.map { server ->
            MediaServerView(
                id = server.id,
                name = server.name,
                owned = server.owned,
                ownerName = server.ownerName,
                enabled = server.enabled,
                reachability = plex.reachability(server.id),
                libraries = plex.cachedLibraries(server.id),
            )
        }.sortedWith(compareByDescending<MediaServerView> { it.owned }.thenBy { it.name.lowercase() })
        _state.update { it.copy(servers = views) }
    }

    private fun DiscoveredMediaServer.remember(): DiscoveredMediaServer = also { plexServers[id] = it }

    private fun persist(key: String) {
        val store = vault ?: return
        val current = _state.value
        store.save(
            key,
            MediaServerVault.Snapshot(
                linked = current.linked,
                needsAttention = current.needsAttention,
                accountName = current.accountName,
                accountThumb = current.accountThumb,
                servers = plexServers.values.map { server ->
                    MediaServerVault.StoredServer(
                        id = server.id,
                        name = server.name,
                        owned = server.owned,
                        ownerName = server.ownerName,
                        enabled = server.enabled,
                        presence = server.presence,
                        accessToken = server.accessToken,
                        connections = server.connections.map { MediaServerVault.StoredConnection(it.uri, it.local, it.relay) },
                        libraryChoices = server.libraryChoices,
                        libraries = plex.cachedLibraries(server.id).map { MediaServerVault.StoredLibrary(it.key, it.title, it.kind.name, it.itemCount) },
                        preferredUri = preferredUris[server.id],
                    )
                },
                savedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    // ── Linking ─────────────────────────────────────────────────────────────────────────────────

    /** Starts a plex.tv/link code. Null when StreamDek could not be reached. */
    suspend fun startLink(): MediaServerLinkCode? {
        val identity = identity()
        val response = call(
            MediaServerLinkStartDto::class.java,
            "POST",
            "/media-servers/$PLEX_PROVIDER_ID/link",
            mapOf("deviceName" to identity.deviceName, "platform" to identity.platform, "appVersion" to identity.version),
        ) ?: return null
        val handle = response.handle ?: return null
        val code = response.code ?: return null
        return MediaServerLinkCode(
            handle = handle,
            code = code,
            linkUrl = response.linkUrl ?: "https://plex.tv/link",
            directUrl = response.directUrl ?: "https://plex.tv/link/?pin=$code",
            expiresAtMs = response.expiresAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: (System.currentTimeMillis() + 10 * 60_000L),
            pollIntervalMs = ((response.pollIntervalSec ?: 2).coerceIn(1, 10)) * 1000L,
        )
    }

    /** One check of a pending code. On success the integration is refreshed before this returns. */
    suspend fun pollLink(code: MediaServerLinkCode): MediaServerLinkStatus {
        if (System.currentTimeMillis() > code.expiresAtMs) return MediaServerLinkStatus.Expired
        val response = call(MediaServerLinkPollDto::class.java, "POST", "/media-servers/$PLEX_PROVIDER_ID/link/poll", mapOf("handle" to code.handle))
            ?: return MediaServerLinkStatus.Pending
        return when (response.status) {
            "authorized" -> {
                refresh(force = true)
                MediaServerLinkStatus.Linked(response.connection?.account?.name)
            }
            "expired" -> MediaServerLinkStatus.Expired
            "error" -> MediaServerLinkStatus.Failed
            else -> MediaServerLinkStatus.Pending
        }
    }

    // ── Choices ─────────────────────────────────────────────────────────────────────────────────

    /** Switches a server on or off for this profile, on every device. */
    suspend fun setServerEnabled(serverId: String, enabled: Boolean): Boolean =
        saveChoices { servers -> servers.map { if (it.id == serverId) it.copy(enabled = enabled) else it } }

    /** Switches a library on or off for this profile, on every device. */
    suspend fun setLibraryEnabled(serverId: String, libraryKey: String, enabled: Boolean): Boolean =
        saveChoices { servers ->
            servers.map { if (it.id == serverId) it.copy(libraryChoices = it.libraryChoices + (libraryKey to enabled)) else it }
        }

    private suspend fun saveChoices(change: (List<DiscoveredMediaServer>) -> List<DiscoveredMediaServer>): Boolean {
        val key = scopeKey() ?: return false
        val next = change(plexServers.values.toList())
        val body = mapOf(
            "servers" to next.associate { server ->
                // Every library currently known is written, not only the changed one, so a second
                // device reads the same defaults this one is showing.
                val libraries = plex.cachedLibraries(server.id).associate { it.key to it.enabled } + server.libraryChoices
                server.id to mapOf("enabled" to server.enabled, "libraries" to libraries)
            },
        )
        val saved = call(MediaServerStatusDto::class.java, "PUT", "/media-servers/$PLEX_PROVIDER_ID/selection", body) != null
        if (!saved) return false
        plex.setServers(next.map { it.remember() })
        next.filter { it.enabled }.forEach { server -> runCatching { plex.libraries(server.id, force = true) } }
        persist(key)
        publishServers()
        bump()
        return true
    }

    /** Removes the link for this profile, on every device. */
    suspend fun disconnect(): Boolean {
        val key = scopeKey() ?: return false
        val ok = call(MediaServerStatusDto::class.java, "DELETE", "/media-servers/$PLEX_PROVIDER_ID") != null
        if (ok) {
            unlinkLocally(key)
            plexServers.clear()
        }
        return ok
    }

    /** Sign-out: forget everything on this device. */
    fun clearDevice() {
        // Jellyfin sessions belong to this device, so they are ended on the servers too.
        val signedIn = jellyfin.accounts()
        if (signedIn.isNotEmpty()) scope.launch { signedIn.forEach { account -> logoutQuietly(account) } }
        plex.reset()
        jellyfin.reset()
        MediaServerAuth.clear()
        vault?.clear()
        plexServers.clear()
        activeScope = null
        _state.value = MediaServerUiState()
        _jellyfinState.value = MediaServerUiState(provider = JELLYFIN_PROVIDER_ID)
        bump()
    }

    // ── Jellyfin ────────────────────────────────────────────────────────────────────────────────
    //
    // Signed in on the device, not through StreamDek's backend: a Jellyfin server is the viewer's
    // own, reached by an address they give, and its token is issued to this device. The sign-in
    // (servers, user, token, library choices) is kept in the same encrypted vault as Plex's, under
    // its own key per profile, and never leaves the device - not in backups, not in cloud sync.

    private fun jellyfinVaultKey(key: String) = "jellyfin:$key"

    private fun restoreJellyfin(key: String) {
        val snapshot = vault?.load(jellyfinVaultKey(key))
        val accounts = snapshot?.servers.orEmpty().mapNotNull { stored ->
            val id = stored.id ?: return@mapNotNull null
            val token = stored.accessToken ?: return@mapNotNull null
            val user = stored.userId ?: return@mapNotNull null
            stored.preferredUri?.let { jellyfinPreferred[id] = it }
            JellyfinAccount(
                serverId = id,
                name = stored.name ?: "Jellyfin",
                addresses = stored.connections.orEmpty().mapNotNull { it.uri },
                userId = user,
                userName = stored.userName,
                token = token,
                enabled = stored.enabled != false,
                libraryChoices = stored.libraryChoices.orEmpty(),
            )
        }
        if (accounts.isEmpty()) {
            _jellyfinState.value = MediaServerUiState(provider = JELLYFIN_PROVIDER_ID, available = true)
            return
        }
        jellyfin.setAccounts(accounts)
        snapshot?.servers.orEmpty().forEach { stored ->
            val id = stored.id ?: return@forEach
            jellyfin.seedLibraries(id, stored.libraries.orEmpty().mapNotNull { library ->
                val libraryKey = library.key ?: return@mapNotNull null
                val kind = runCatching { MediaServerLibraryKind.valueOf(library.kind ?: "") }.getOrNull() ?: return@mapNotNull null
                MediaServerLibrary(id, libraryKey, library.title ?: libraryKey, kind, stored.libraryChoices?.get(libraryKey) ?: (kind != MediaServerLibraryKind.Other), library.itemCount)
            })
        }
        _jellyfinState.value = MediaServerUiState(provider = JELLYFIN_PROVIDER_ID, available = true, linked = true, accountName = accounts.firstOrNull()?.userName)
        publishJellyfin()
        bump()
    }

    /** Reaches every signed-in Jellyfin server and reads its libraries again. */
    suspend fun refreshJellyfin(force: Boolean) {
        val key = scopeKey() ?: return
        if (jellyfin.accounts().isEmpty()) return
        _jellyfinState.update { it.copy(refreshing = true) }
        try {
            jellyfin.connect(force)
            jellyfin.accounts().filter { it.enabled }.forEach { account -> runCatching { jellyfin.libraries(account.serverId, force) } }
            if (key == activeScope) persistJellyfin(key)
            bump()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w("StreamDekMediaServers", "jellyfin refresh failed: ${error.javaClass.simpleName}")
        } finally {
            _jellyfinState.update { it.copy(refreshing = false) }
            publishJellyfin()
        }
    }

    private fun publishJellyfin() {
        val accounts = jellyfin.accounts()
        val views = accounts.map { account ->
            MediaServerView(
                id = account.serverId,
                name = account.name,
                owned = true,
                ownerName = account.userName,
                enabled = account.enabled,
                reachability = jellyfin.reachability(account.serverId),
                libraries = jellyfin.cachedLibraries(account.serverId),
                problem = jellyfin.problem(account.serverId),
            )
        }.sortedBy { it.name.lowercase() }
        _jellyfinState.update {
            it.copy(
                available = scopeKey() != null,
                linked = accounts.isNotEmpty(),
                needsAttention = views.isNotEmpty() && views.all { view -> (view.reachability as? MediaServerReachability.Offline)?.reason == OfflineReason.Unauthorized },
                accountName = accounts.firstOrNull()?.userName,
                servers = views,
            )
        }
    }

    private fun persistJellyfin(key: String) {
        val store = vault ?: return
        val accounts = jellyfin.accounts()
        if (accounts.isEmpty()) {
            store.remove(jellyfinVaultKey(key))
            return
        }
        store.save(
            jellyfinVaultKey(key),
            MediaServerVault.Snapshot(
                linked = true,
                servers = accounts.map { account ->
                    MediaServerVault.StoredServer(
                        id = account.serverId,
                        name = account.name,
                        owned = true,
                        enabled = account.enabled,
                        accessToken = account.token,
                        connections = account.addresses.map { MediaServerVault.StoredConnection(it, JellyfinClient.isLocalHost(it.substringAfter("://").substringBefore('/').substringBefore(':')), false) },
                        libraryChoices = account.libraryChoices,
                        libraries = jellyfin.cachedLibraries(account.serverId).map { MediaServerVault.StoredLibrary(it.key, it.title, it.kind.name, it.itemCount) },
                        preferredUri = jellyfinPreferred[account.serverId],
                        userId = account.userId,
                        userName = account.userName,
                    )
                },
                savedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    /** Looks for a Jellyfin server at what the viewer typed. Null when nothing there answers as Jellyfin. */
    suspend fun findJellyfinServer(input: String): JellyfinServerCandidate? {
        for (url in JellyfinClient.candidates(input)) {
            val info = jellyfinClient.publicInfo(url) ?: continue
            return JellyfinServerCandidate(
                id = info.id ?: continue,
                name = info.serverName?.takeIf { it.isNotBlank() } ?: "Jellyfin",
                url = url,
                localAddress = info.localAddress?.trimEnd('/')?.takeIf { it.isNotBlank() && !it.equals(url, true) },
                version = info.version,
                quickConnect = jellyfinClient.quickConnectEnabled(url),
            )
        }
        return null
    }

    /** Jellyfin servers that answered the local-network discovery broadcast, checked before being offered. */
    suspend fun discoverJellyfinServers(): List<JellyfinServerCandidate> =
        jellyfinClient.discover().mapNotNull { reply ->
            val address = reply.address?.trimEnd('/') ?: return@mapNotNull null
            findJellyfinServer(address)
        }.distinctBy { it.id }

    /** Starts Quick Connect: a code the viewer approves from a Jellyfin app where they are already signed in. */
    suspend fun startJellyfinQuickConnect(server: JellyfinServerCandidate): JellyfinQuickConnectCode? {
        val started = jellyfinClient.quickConnectInitiate(server.url) ?: return null
        val code = started.code ?: return null
        val secret = started.secret ?: return null
        return JellyfinQuickConnectCode(server, code, secret, System.currentTimeMillis() + 10 * 60_000L)
    }

    /** One check of a Quick Connect code. On approval the server is added before this returns. */
    suspend fun pollJellyfinQuickConnect(code: JellyfinQuickConnectCode): MediaServerLinkStatus {
        if (System.currentTimeMillis() > code.expiresAtMs) return MediaServerLinkStatus.Expired
        val state = jellyfinClient.quickConnectState(code.server.url, code.secret) ?: return MediaServerLinkStatus.Pending
        if (state.authenticated != true) return MediaServerLinkStatus.Pending
        val result = jellyfinClient.authenticateWithQuickConnect(code.server.url, code.secret) ?: return MediaServerLinkStatus.Failed
        return addJellyfinAccount(code.server, result)
    }

    /**
     * Signs in with a Jellyfin username and password. The password is sent once, to the server
     * itself, and is not kept: only the token the server issues is stored.
     */
    suspend fun signInToJellyfin(server: JellyfinServerCandidate, username: String, password: String): MediaServerLinkStatus {
        val result = try {
            jellyfinClient.authenticateByName(server.url, username.trim(), password)
        } catch (_: JellyfinClient.UnauthorizedException) {
            null
        } ?: return MediaServerLinkStatus.Failed
        return addJellyfinAccount(server, result)
    }

    private suspend fun addJellyfinAccount(server: JellyfinServerCandidate, result: net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinAuthResult): MediaServerLinkStatus {
        val key = scopeKey() ?: return MediaServerLinkStatus.Failed
        val token = result.accessToken ?: return MediaServerLinkStatus.Failed
        val userId = result.user?.id ?: return MediaServerLinkStatus.Failed
        val serverId = result.serverId ?: server.id
        val account = JellyfinAccount(
            serverId = serverId,
            name = server.name,
            addresses = listOfNotNull(server.url, server.localAddress).distinct(),
            userId = userId,
            userName = result.user.name,
            token = token,
        )
        // Signing in again to a server already here replaces that sign-in and keeps its choices.
        val previous = jellyfin.accounts().firstOrNull { it.serverId == serverId }
        jellyfin.setAccounts(jellyfin.accounts().filterNot { it.serverId == serverId } + account.copy(libraryChoices = previous?.libraryChoices.orEmpty()))
        jellyfin.connect(force = true)
        runCatching { jellyfin.libraries(serverId, force = true) }
        persistJellyfin(key)
        publishJellyfin()
        bump()
        return MediaServerLinkStatus.Linked(result.user.name)
    }

    fun setJellyfinServerEnabled(serverId: String, enabled: Boolean) = changeJellyfin { accounts ->
        accounts.map { if (it.serverId == serverId) it.copy(enabled = enabled) else it }
    }

    fun setJellyfinLibraryEnabled(serverId: String, libraryKey: String, enabled: Boolean) = changeJellyfin { accounts ->
        accounts.map { account ->
            if (account.serverId != serverId) account
            else account.copy(libraryChoices = jellyfin.cachedLibraries(serverId).associate { it.key to it.enabled } + account.libraryChoices + (libraryKey to enabled))
        }
    }

    private fun changeJellyfin(change: (List<JellyfinAccount>) -> List<JellyfinAccount>): Boolean {
        val key = scopeKey() ?: return false
        jellyfin.setAccounts(change(jellyfin.accounts()))
        persistJellyfin(key)
        publishJellyfin()
        bump()
        return true
    }

    private suspend fun logoutQuietly(account: JellyfinAccount) {
        val url = jellyfinPreferred[account.serverId] ?: account.addresses.firstOrNull() ?: return
        runCatching { jellyfinClient.logout(net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinEndpoint(url, account.token)) }
    }

    /** Signs this device out of one Jellyfin server - or all of them - ending the session on the server too. */
    suspend fun disconnectJellyfin(serverId: String? = null): Boolean {
        val key = scopeKey() ?: return false
        val leaving = jellyfin.accounts().filter { serverId == null || it.serverId == serverId }
        leaving.forEach { account ->
            logoutQuietly(account)
            account.addresses.forEach(MediaServerAuth::forget)
            jellyfinPreferred.remove(account.serverId)
        }
        jellyfin.setAccounts(jellyfin.accounts().filterNot { account -> leaving.any { it.serverId == account.serverId } })
        persistJellyfin(key)
        publishJellyfin()
        bump()
        return true
    }


    // ── Wire shapes ─────────────────────────────────────────────────────────────────────────────

    private fun MediaServerServerDto.toDiscovered(): DiscoveredMediaServer? {
        val serverId = id ?: return null
        val token = accessToken
        return DiscoveredMediaServer(
            id = serverId,
            name = name ?: "Plex Media Server",
            owned = owned == true,
            ownerName = sourceTitle,
            enabled = enabled != false,
            presence = presence == true,
            accessToken = token,
            connections = connections.orEmpty().mapNotNull { connection ->
                val uri = connection.uri ?: return@mapNotNull null
                MediaServerEndpoint(serverId, uri, connection.local == true, connection.relay == true, token)
            },
            libraryChoices = libraries.orEmpty(),
        ).remember()
    }

    private fun MediaServerVault.StoredServer.toDiscovered(): DiscoveredMediaServer? {
        val serverId = id ?: return null
        return DiscoveredMediaServer(
            id = serverId,
            name = name ?: "Plex Media Server",
            owned = owned == true,
            ownerName = ownerName,
            enabled = enabled != false,
            presence = presence == true,
            accessToken = accessToken,
            connections = connections.orEmpty().mapNotNull { connection ->
                val uri = connection.uri ?: return@mapNotNull null
                MediaServerEndpoint(serverId, uri, connection.local == true, connection.relay == true, accessToken)
            },
            libraryChoices = libraryChoices.orEmpty(),
        ).remember()
    }
}

/** A Jellyfin server found at an address the viewer gave, or on their network. */
data class JellyfinServerCandidate(
    val id: String,
    val name: String,
    val url: String,
    /** The address the server reports for its own network, tried first when it answers. */
    val localAddress: String?,
    val version: String?,
    val quickConnect: Boolean,
)

/** A Quick Connect code on screen. [secret] is what claims the sign-in, so it is never printed. */
data class JellyfinQuickConnectCode(
    val server: JellyfinServerCandidate,
    val code: String,
    val secret: String,
    val expiresAtMs: Long,
) {
    override fun toString(): String = "JellyfinQuickConnectCode(code=$code, server=${server.id})"
}

/** A plex.tv/link code on screen. [handle] is StreamDek's sealed record of it and opaque here. */
data class MediaServerLinkCode(
    val handle: String,
    val code: String,
    val linkUrl: String,
    val directUrl: String,
    val expiresAtMs: Long,
    val pollIntervalMs: Long,
) {
    override fun toString(): String = "MediaServerLinkCode(code=$code, expiresAtMs=$expiresAtMs)"
}

sealed interface MediaServerLinkStatus {
    data object Pending : MediaServerLinkStatus
    data object Expired : MediaServerLinkStatus
    data object Failed : MediaServerLinkStatus
    data class Linked(val accountName: String?) : MediaServerLinkStatus
}

internal data class MediaServerAccountDto(val name: String? = null, val username: String? = null, val thumb: String? = null)

internal data class MediaServerStatusDto(
    val provider: String? = null,
    val connected: Boolean? = null,
    val status: String? = null,
    val account: MediaServerAccountDto? = null,
    val authMode: String? = null,
    val linkedAt: String? = null,
)

internal data class MediaServerConnectionDto(
    val uri: String? = null,
    val local: Boolean? = null,
    val relay: Boolean? = null,
)

internal data class MediaServerServerDto(
    val id: String? = null,
    val name: String? = null,
    val owned: Boolean? = null,
    val sourceTitle: String? = null,
    val presence: Boolean? = null,
    val connections: List<MediaServerConnectionDto>? = null,
    val enabled: Boolean? = null,
    val libraries: Map<String, Boolean>? = null,
    val accessToken: String? = null,
) {
    override fun toString(): String = "MediaServerServerDto(id=$id, name=$name)"
}

internal data class MediaServerServersDto(
    val status: MediaServerStatusDto? = null,
    val servers: List<MediaServerServerDto>? = null,
)

internal data class MediaServerLinkStartDto(
    val handle: String? = null,
    val code: String? = null,
    val linkUrl: String? = null,
    val directUrl: String? = null,
    val expiresAt: String? = null,
    val pollIntervalSec: Int? = null,
)

internal data class MediaServerLinkPollDto(
    val status: String? = null,
    val error: String? = null,
    val connection: MediaServerStatusDto? = null,
)

private const val DISPLAY_PREFS = "streamdek_media_servers"
private const val KEY_JELLYFIN_AMBIENT = "jellyfinAmbient"
private const val KEY_LAST_PAGE_PROVIDER = "lastPageProvider"
