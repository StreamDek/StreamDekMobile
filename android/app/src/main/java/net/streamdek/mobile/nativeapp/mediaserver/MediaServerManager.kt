package net.streamdek.mobile.nativeapp.mediaserver

import net.streamdek.mobile.nativeapp.durableSettingsPreferences
import android.content.Context
import android.util.Log
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinClient
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinClientIdentity
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinProvider
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.MediaBrowserFlavor
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

    /** The profile's Plex setup revision at StreamDek, as last read; a choice is saved against it. */
    @Volatile private var plexRevision: Int? = null
    /** When the profile's Plex setup last changed at StreamDek, as last read; 0 when unknown. */
    @Volatile private var plexChangedAt: Long = 0L
    /** The libraries last reported to StreamDek per server, so an unchanged list is not sent again. */
    private val reportedPlexCatalog = ConcurrentHashMap<String, String>()

    private suspend inline fun <reified T> cloudGet(path: String): T? = call(T::class.java, "GET", path)
    private suspend inline fun <reified T> cloudPut(path: String, body: Map<String, Any?>): T? = call(T::class.java, "PUT", path, body)
    private suspend inline fun <reified T> cloudDelete(path: String): T? = call(T::class.java, "DELETE", path)

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

    private val displayPrefs = context?.durableSettingsPreferences(DISPLAY_PREFS)

    private fun mediaBrowserIdentity(): JellyfinClientIdentity {
        val plexIdentity = identity()
        return JellyfinClientIdentity(
            client = PlexProvider.PLEX_PRODUCT,
            deviceName = plexIdentity.deviceName,
            deviceId = plexIdentity.clientIdentifier,
            version = plexIdentity.version,
        )
    }

    /** What the Jellyfin and Emby sign-ins borrow from here; see [MediaBrowserAccounts.Host]. */
    private val accountsHost = object : MediaBrowserAccounts.Host {
        override val scope: CoroutineScope get() = this@MediaServerManager.scope
        override val vault: MediaServerVault? get() = this@MediaServerManager.vault
        override val prefs get() = displayPrefs
        override fun scopeKey(): String? = this@MediaServerManager.scopeKey()
        override fun activeScope(): String? = this@MediaServerManager.activeScope
        override fun serverOrder(): List<String> = _serverOrder.value
        override fun bump() = this@MediaServerManager.bump()
        override suspend fun <T> call(type: Class<T>, method: String, path: String, body: Map<String, Any?>?): T? =
            this@MediaServerManager.call(type, method, path, body)
    }

    private fun mediaBrowserAccounts(flavor: MediaBrowserFlavor, labels: () -> MediaServerLabels): MediaBrowserAccounts {
        val client = JellyfinClient(::mediaBrowserIdentity, flavor = flavor)
        var accounts: MediaBrowserAccounts? = null
        val provider = JellyfinProvider(client = client, labels = labels, onStateChanged = { accounts?.publish() })
        return MediaBrowserAccounts(flavor, client, provider, accountsHost).also { accounts = it }
    }

    /** Jellyfin's sign-ins, on this device and at StreamDek. */
    val jellyfinAccounts: MediaBrowserAccounts = mediaBrowserAccounts(MediaBrowserFlavor.Jellyfin, labels)

    /** Emby's: the same code as Jellyfin's, in Emby's own terms. */
    val embyAccounts: MediaBrowserAccounts = mediaBrowserAccounts(MediaBrowserFlavor.Emby, labels)

    /** Both, for what treats them alike. */
    val mediaBrowserAccounts: List<MediaBrowserAccounts> get() = listOf(jellyfinAccounts, embyAccounts)

    /** The sign-ins for [provider], when it is Jellyfin or Emby. */
    fun accountsFor(provider: String): MediaBrowserAccounts? = mediaBrowserAccounts.firstOrNull { it.flavor.providerId == provider }

    internal val jellyfin: JellyfinProvider get() = jellyfinAccounts.provider
    internal val emby: JellyfinProvider get() = embyAccounts.provider

    /** Jellyfin's standing, in the same shape as Plex's [state], for its settings page and destination. */
    val jellyfinState: StateFlow<MediaServerUiState> get() = jellyfinAccounts.state
    val embyState: StateFlow<MediaServerUiState> get() = embyAccounts.state

    /** Whether the Jellyfin page and its lists wear the Jellyfin colour wash. On until switched off. */
    val jellyfinAmbient: StateFlow<Boolean> get() = jellyfinAccounts.ambient

    /** Which server the media page showed last, so it opens there again when both are connected. */
    var lastPageProvider: String
        get() = displayPrefs?.getString(KEY_LAST_PAGE_PROVIDER, null) ?: PLEX_PROVIDER_ID
        set(value) { displayPrefs?.edit()?.putString(KEY_LAST_PAGE_PROVIDER, value)?.apply() }

    fun setJellyfinAmbient(enabled: Boolean) = jellyfinAccounts.setAmbient(enabled)

    // ── The server list in Settings, on this device ──────────────────────────────────────────────

    private val _removedEntries = MutableStateFlow<Set<String>>(emptySet())
    /** What has been taken off this device's server list; see MediaServerListTidy.kt. */
    val removedEntries: StateFlow<Set<String>> = _removedEntries.asStateFlow()

    private val _collapsedServers = MutableStateFlow<Set<String>>(emptySet())
    /** The servers whose libraries are folded away in Settings. */
    val collapsedServers: StateFlow<Set<String>> = _collapsedServers.asStateFlow()

    private val _serverOrder = MutableStateFlow<List<String>>(emptyList())
    /**
     * The viewer's order for their servers, most preferred first, as entry keys - see
     * [mediaServerEntryKey] and [inServerOrder]. Settings lists the servers in it, and the Plex and
     * Jellyfin pages show each server's rows in it. Kept on this device, like folding and removing.
     */
    val serverOrder: StateFlow<List<String>> = _serverOrder.asStateFlow()

    private val _libraryOrder = MutableStateFlow<List<String>>(emptyList())
    /**
     * The viewer's order for each server's libraries, as [mediaServerLibraryOrderKey]s. A library is
     * a set of rows on the page - its Recently Added, the library itself, Recently Watched and
     * Collections - so ordering the libraries in Settings is ordering the page. Each key names its
     * server, so one server's order never touches another's; a library switched off keeps its
     * place for when it comes back. Kept on this device with the server order.
     */
    val libraryOrder: StateFlow<List<String>> = _libraryOrder.asStateFlow()

    /** Told when the viewer moves a server or a library here, so the order is kept with the profile. */
    var onOrderChanged: ((serverOrder: List<String>, libraryOrder: List<String>) -> Unit)? = null

    /**
     * An order kept with the profile, moved on another device. Either part may be absent, which
     * leaves that part as it is here. Not reported back through [onOrderChanged].
     */
    fun applySyncedOrder(serverOrder: List<String>?, libraryOrder: List<String>?) {
        var changed = false
        serverOrder?.filter { it.isNotBlank() }?.takeIf { it != _serverOrder.value }?.let { _serverOrder.value = it; changed = true }
        libraryOrder?.filter { it.isNotBlank() }?.takeIf { it != _libraryOrder.value }?.let { _libraryOrder.value = it; changed = true }
        if (!changed) return
        saveListTidy()
        // The server at the top names the Jellyfin or Emby account; see MediaBrowserAccounts.leadingUser.
        mediaBrowserAccounts.forEach { it.publish() }
    }

    /** Moves [libraryKey] on [serverId] from where it is among [shownLibraryKeys] to [to]. */
    fun moveLibrary(serverId: String, shownLibraryKeys: List<String>, libraryKey: String, to: Int) {
        val shown = shownLibraryKeys.map { mediaServerLibraryOrderKey(serverId, it) }
        val key = mediaServerLibraryOrderKey(serverId, libraryKey)
        val moved = movedServerOrder(_libraryOrder.value, shown, key, to - shown.indexOf(key))
        if (moved == _libraryOrder.value) return
        _libraryOrder.value = moved
        saveListTidy()
        onOrderChanged?.invoke(_serverOrder.value, _libraryOrder.value)
    }

    private fun loadListTidy(key: String?) {
        _removedEntries.value = key?.let { displayPrefs?.getStringSet("$KEY_REMOVED_ENTRIES:$it", null) }.orEmpty().toSet()
        _collapsedServers.value = key?.let { displayPrefs?.getStringSet("$KEY_COLLAPSED_SERVERS:$it", null) }.orEmpty().toSet()
        // A list, so not a string set: the order is the point. Entry keys are URL-encoded and
        // never contain a line break.
        _serverOrder.value = key?.let { displayPrefs?.getString("$KEY_SERVER_ORDER:$it", null) }
            ?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        _libraryOrder.value = key?.let { scope ->
            displayPrefs?.getString("$KEY_LIBRARY_ORDER:$scope", null)?.split('\n')?.filter { it.isNotBlank() }
                // A row order saved by the first version of this, which ordered page rows: the
                // libraries those rows belonged to, in the order their rows came.
                ?: displayPrefs?.getString("$KEY_ROW_ORDER:$scope", null)?.split('\n')?.let(::libraryOrderFromRowOrder)
        }.orEmpty()
    }

    private fun saveListTidy() {
        val key = scopeKey() ?: return
        displayPrefs?.edit()
            ?.putStringSet("$KEY_REMOVED_ENTRIES:$key", _removedEntries.value)
            ?.putStringSet("$KEY_COLLAPSED_SERVERS:$key", _collapsedServers.value)
            ?.putString("$KEY_SERVER_ORDER:$key", _serverOrder.value.joinToString("\n"))
            ?.putString("$KEY_LIBRARY_ORDER:$key", _libraryOrder.value.joinToString("\n"))
            ?.remove("$KEY_ROW_ORDER:$key")
            ?.apply()
    }

    /**
     * Moves one server up ([offset] -1) or down (+1) among [shownServerIds], the servers of
     * [provider] as Settings lists them right now.
     */
    fun moveServer(provider: String, shownServerIds: List<String>, serverId: String, offset: Int) {
        val moved = movedServerOrder(
            order = _serverOrder.value,
            shown = shownServerIds.map { mediaServerEntryKey(provider, it) },
            key = mediaServerEntryKey(provider, serverId),
            offset = offset,
        )
        if (moved == _serverOrder.value) return
        _serverOrder.value = moved
        saveListTidy()
        onOrderChanged?.invoke(_serverOrder.value, _libraryOrder.value)
        // The server at the top names the Jellyfin or Emby account; see MediaBrowserAccounts.leadingUser.
        accountsFor(provider)?.publish()
    }

    fun setServerCollapsed(provider: String, serverId: String, collapsed: Boolean) {
        val entry = mediaServerEntryKey(provider, serverId)
        _collapsedServers.value = if (collapsed) _collapsedServers.value + entry else _collapsedServers.value - entry
        saveListTidy()
    }

    /**
     * Takes one library off the list: it is switched off for the profile, and this device stops
     * listing it. Every other library and server is left exactly as it was.
     */
    suspend fun removeLibrary(provider: String, serverId: String, libraryKey: String): Boolean {
        val off = accountsFor(provider)?.setLibraryEnabled(serverId, libraryKey, false) ?: setLibraryEnabled(serverId, libraryKey, false)
        if (!off) return false
        _removedEntries.value = _removedEntries.value + mediaServerEntryKey(provider, serverId, libraryKey)
        saveListTidy()
        return true
    }

    /** Brings a removed library back, switched on. */
    suspend fun restoreLibrary(provider: String, serverId: String, libraryKey: String): Boolean {
        val on = accountsFor(provider)?.setLibraryEnabled(serverId, libraryKey, true) ?: setLibraryEnabled(serverId, libraryKey, true)
        if (!on) return false
        _removedEntries.value = _removedEntries.value - mediaServerEntryKey(provider, serverId, libraryKey)
        saveListTidy()
        return true
    }

    /**
     * Takes one server off the list and leaves the others connected.
     *
     * A Jellyfin server has its own sign-in, so this signs out of that server alone. A Plex server
     * comes with the Plex account and cannot be unlinked by itself, so it is switched off for the
     * profile and no longer listed here, and can be brought back from "Removed".
     */
    suspend fun removeServer(provider: String, serverId: String): Boolean {
        val entry = mediaServerEntryKey(provider, serverId)
        val accounts = accountsFor(provider)
        if (accounts != null) {
            if (!accounts.disconnect(serverId)) return false
            // Nothing of a signed-out server is kept: not what was removed from it, nor how it was folded.
            _removedEntries.value = _removedEntries.value.filterNot { it == entry || it.startsWith("$entry/") }.toSet()
            _collapsedServers.value = _collapsedServers.value - entry
            saveListTidy()
            return true
        }
        if (!setServerEnabled(serverId, false)) return false
        // What had been removed from this server goes with it. The server now stands for all of
        // it under "Removed", so its libraries are not kept there as entries of their own - and
        // bringing the server back brings back the whole server, not one with pieces still missing.
        _removedEntries.value = _removedEntries.value.filterNot { it.startsWith("$entry/") }.toSet() + entry
        saveListTidy()
        return true
    }

    /**
     * Brings a removed Plex server back, switched on, with all of its libraries listed again. One
     * that had been removed before the server was comes back switched off, as removing left it.
     */
    suspend fun restoreServer(provider: String, serverId: String): Boolean {
        if (accountsFor(provider) != null) return false
        if (!setServerEnabled(serverId, true)) return false
        _removedEntries.value = _removedEntries.value - mediaServerEntryKey(provider, serverId)
        saveListTidy()
        return true
    }

    private val providers: Map<String, MediaServerProvider> by lazy {
        mapOf(PLEX_PROVIDER_ID to plex, JELLYFIN_PROVIDER_ID to jellyfin, EMBY_PROVIDER_ID to emby)
    }

    fun provider(id: String): MediaServerProvider? = providers[id]

    fun providerFor(ref: MediaServerReference): MediaServerProvider? = providers[ref.provider]

    /** Every provider with something linked, for merged reads (search, continue watching, Home). */
    fun activeProviders(): List<MediaServerProvider> = buildList {
        if (_state.value.navigationVisible || _state.value.linked) add(plex)
        if (jellyfinState.value.linked) add(jellyfin)
        if (embyState.value.linked) add(emby)
    }

    /** The providers whose own page the navigation offers, in the order the page's switch lists them. */
    fun navigableProviders(): List<String> = buildList {
        if (_state.value.navigationVisible) add(PLEX_PROVIDER_ID)
        if (jellyfinState.value.navigationVisible) add(JELLYFIN_PROVIDER_ID)
        if (embyState.value.navigationVisible) add(EMBY_PROVIDER_ID)
    }

    /** The state of one provider, by id. */
    fun stateOf(provider: String): StateFlow<MediaServerUiState> = accountsFor(provider)?.state ?: state

    // ── Restore and refresh ─────────────────────────────────────────────────────────────────────

    /**
     * Called when the app starts, the viewer signs in or out, or the profile changes. Shows what the
     * device remembers at once, then asks StreamDek for the current picture in the background.
     */
    fun onSessionChanged() {
        val key = scopeKey()
        if (key == activeScope && _state.value.available == (key != null)) return
        activeScope = key
        loadListTidy(key)
        refreshJob?.cancel()
        plex.reset()
        MediaServerAuth.clear()
        preferredUris.clear()
        forgetProfileSyncState()
        mediaBrowserAccounts.forEach { it.onSessionChanged(key) }
        if (key == null) {
            _state.value = MediaServerUiState(available = false)
            bump()
            return
        }
        restore(key)
        refreshJob = scope.launch {
            mediaBrowserAccounts.forEach { accounts -> launch { accounts.refresh(force = false) } }
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
                plexRevision = discovery?.status?.revision ?: status.revision ?: plexRevision
                plexChangedAt = parseInstant(discovery?.status?.choicesUpdatedAt ?: status.choicesUpdatedAt).takeIf { it > 0 } ?: plexChangedAt
                if (discovery != null) {
                    plex.setServers(discovery.servers.orEmpty().mapNotNull { it.toDiscovered() })
                }
                plex.connect(force)
                discovery?.servers.orEmpty().filter { it.enabled != false }.mapNotNull { it.id }.forEach { serverId ->
                    runCatching { plex.libraries(serverId, force) }
                }
                reportPlexCatalog()
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
        mediaBrowserAccounts.forEach { accounts -> scope.launch { accounts.refresh(force) } }
    }

    private fun unlinkLocally(key: String) {
        plex.reset()
        MediaServerAuth.clear()
        // Plex going does not sign the device out of Jellyfin or Emby.
        mediaBrowserAccounts.forEach { it.provider.registerAuth() }
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
                applyRestoredPlexChoices()
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

    /**
     * Saves a change of choices for the profile, against the revision this device last read.
     *
     * If the profile moved on in the meantime - a choice made on the television or the web portal -
     * StreamDek refuses the save rather than let this device overwrite it. The current setup is then
     * read, and this same change applied to it and saved once more, so the viewer's latest action
     * lands without undoing anyone else's.
     */
    private suspend fun saveChoices(retry: Boolean = true, change: (List<DiscoveredMediaServer>) -> List<DiscoveredMediaServer>): Boolean {
        val key = scopeKey() ?: return false
        repeat(if (retry) 2 else 1) { attempt ->
            val next = change(plexServers.values.toList())
            val body = mapOf(
                "servers" to next.associate { server ->
                    // Every library currently known is written, not only the changed one, so a second
                    // device reads the same defaults this one is showing.
                    val libraries = plex.cachedLibraries(server.id).associate { it.key to it.enabled } + server.libraryChoices
                    server.id to mapOf("enabled" to server.enabled, "libraries" to libraries)
                },
            ) + (plexRevision?.let { mapOf("baseRevision" to it) } ?: emptyMap())
            val saved = call(MediaServerStatusDto::class.java, "PUT", "/media-servers/$PLEX_PROVIDER_ID/selection", body)
            if (saved != null) {
                plexRevision = saved.revision ?: plexRevision
                plex.setServers(next.map { it.remember() })
                next.filter { it.enabled }.forEach { server -> runCatching { plex.libraries(server.id, force = true) } }
                persist(key)
                publishServers()
                bump()
                return true
            }
            // Refused (changed elsewhere) or not reached: read the profile's current setup and try once more.
            if (attempt == 0) refresh(force = false)
        }
        return false
    }

    /**
     * Tells StreamDek which libraries this device found on the profile's Plex servers, so the web
     * portal can name them beside their switches. Only when the list changed.
     */
    private suspend fun reportPlexCatalog() {
        val servers = plexServers.keys.associateWith { plex.cachedLibraries(it) }.filterValues { it.isNotEmpty() }
        val changed = servers.filter { (id, libraries) -> reportedPlexCatalog[id] != libraries.catalogSignature() }
        if (changed.isEmpty()) return
        val body = mapOf("servers" to changed.mapValues { (_, libraries) -> libraries.map { it.toCatalogEntry() } })
        if (cloudPut<Map<String, Any?>>("/media-servers/$PLEX_PROVIDER_ID/catalog", body) != null) {
            changed.forEach { (id, libraries) -> reportedPlexCatalog[id] = libraries.catalogSignature() }
        }
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
        // A sign-in kept with the profile is shared by every device on it, so signing out of StreamDek
        // here only forgets it here. One that never reached StreamDek belongs to this device alone and
        // is ended on its server.
        mediaBrowserAccounts.forEach { it.clearDevice() }
        plex.reset()
        MediaServerAuth.clear()
        vault?.clear()
        plexServers.clear()
        activeScope = null
        _state.value = MediaServerUiState()
        bump()
    }

    // ── Jellyfin ────────────────────────────────────────────────────────────────────────────────
    //
    // Kept in MediaBrowserAccounts, which Emby shares; these are the names callers already use.

    suspend fun refreshJellyfin(force: Boolean) = jellyfinAccounts.refresh(force)

    suspend fun findJellyfinServer(input: String): JellyfinServerCandidate? = jellyfinAccounts.findServer(input)

    suspend fun discoverJellyfinServers(): List<JellyfinServerCandidate> = jellyfinAccounts.discoverServers()

    suspend fun startJellyfinQuickConnect(server: JellyfinServerCandidate): JellyfinQuickConnectCode? = jellyfinAccounts.startQuickConnect(server)

    suspend fun pollJellyfinQuickConnect(code: JellyfinQuickConnectCode): MediaServerLinkStatus = jellyfinAccounts.pollQuickConnect(code)

    suspend fun signInToJellyfin(server: JellyfinServerCandidate, username: String, password: String): MediaServerLinkStatus =
        jellyfinAccounts.signIn(server, username, password)

    fun setJellyfinServerEnabled(serverId: String, enabled: Boolean) = jellyfinAccounts.setServerEnabled(serverId, enabled)

    fun setJellyfinLibraryEnabled(serverId: String, libraryKey: String, enabled: Boolean) = jellyfinAccounts.setLibraryEnabled(serverId, libraryKey, enabled)

    suspend fun disconnectJellyfin(serverId: String? = null): Boolean = jellyfinAccounts.disconnect(serverId)

    val jellyfinRestored: StateFlow<List<JellyfinBackupServer>> get() = jellyfinAccounts.restored

    fun dismissRestoredJellyfin(serverId: String) = jellyfinAccounts.dismissRestored(serverId)

    /** What was read about one profile's Plex setup, forgotten when another is chosen so it is never judged by it. */
    private fun forgetProfileSyncState() {
        plexRevision = null
        plexChangedAt = 0L
        reportedPlexCatalog.clear()
    }

    // ── Backup and restore ──────────────────────────────────────────────────────────────────────
    //
    // A backup carries each profile's Plex and Jellyfin setup (see MediaServerBackup.kt), read from
    // this device's vault so that every backed-up profile is covered, not only the one in use. A
    // restore goes into the profile in use, and is reconciled three ways: the backup, the profile's
    // copy at StreamDek, and this device.

    private fun plexRestoredKey(key: String) = "plexRestored:$key"

    /**
     * One profile's setup as a backup carries it, read from what this device holds for that profile.
     * [scope] is the profile's "<user>:<profile>" key. Jellyfin tokens only with [includeTokens].
     */
    fun backupSection(scope: String, includeTokens: Boolean): org.json.JSONObject? {
        val store = vault ?: return null
        val plexSnapshot = store.load(scope)
        val plex = plexSnapshot?.takeIf { it.linked == true }?.let { snapshot ->
            PlexBackupState(
                linked = true,
                accountName = snapshot.accountName,
                servers = snapshot.servers.orEmpty().mapNotNull { stored ->
                    val id = stored.id ?: return@mapNotNull null
                    PlexBackupServer(id, stored.name ?: "Plex Media Server", stored.enabled != false, stored.libraryChoices.orEmpty())
                },
            )
        }
        return buildMediaServerBackup(
            MediaServerBackupData(plex, jellyfinAccounts.backupServers(scope), embyAccounts.backupServers(scope)),
            includeTokens,
        )
    }

    /**
     * Restores a backup's Plex and Jellyfin setup into the profile in use.
     *
     * The profile's copy is read first. Then, server by server (decideJellyfinRestore,
     * decidePlexRestore): what changed on the profile or on this device after the backup was made
     * stays; what was removed after it stays removed; the rest takes the backup's choices, which are
     * sent to the profile stamped with the backup's time - so a device that saw a newer change still
     * keeps it. A Jellyfin server the profile no longer has comes back from the backup's sign-in when
     * credentials are restored, and otherwise waits in Settings to be signed in to again. Plex, which
     * a backup cannot sign in to, keeps the backup's choices until it is reconnected.
     */
    suspend fun restoreFromBackup(section: org.json.JSONObject, backupCreatedAt: Long, withCredentials: Boolean): MediaServerRestoreOutcome {
        val key = scopeKey() ?: return MediaServerRestoreOutcome()
        val backup = parseMediaServerBackup(section).let { if (withCredentials) it else it.withoutTokens() }
        val restored = mutableListOf<String>()
        val kept = mutableListOf<String>()
        val needsSignIn = mutableListOf<String>()

        jellyfinAccounts.restoreFromBackup(backup.jellyfin, backupCreatedAt, restored, kept, needsSignIn)
        embyAccounts.restoreFromBackup(backup.emby, backupCreatedAt, restored, kept, needsSignIn)

        backup.plex?.takeIf { it.linked }?.let { plexBackup ->
            runCatching { refresh(force = false) }
            val known = plexServers.values.associateBy { it.id }
            val sameChoices = plexBackup.servers.all { server ->
                val current = known[server.id]
                current == null || (current.enabled == server.enabled && server.libraries.all { (library, on) -> current.libraryChoices[library] == on })
            }
            when (decidePlexRestore(backupCreatedAt, _state.value.linked, sameChoices, plexChangedAt)) {
                MediaServerRestoreDecision.Unchanged -> restored += "Plex"
                MediaServerRestoreDecision.KeepNewer -> kept += "Plex"
                MediaServerRestoreDecision.ApplyChoices -> {
                    // Not retried: a refusal means the profile changed meanwhile, and that newer choice stays.
                    val saved = saveChoices(retry = false) { servers -> servers.map { current -> plexBackup.servers.firstOrNull { it.id == current.id }?.let { current.withBackupChoices(it) } ?: current } }
                    if (saved) restored += "Plex" else kept += "Plex"
                }
                else -> {
                    val stash = buildMediaServerBackup(MediaServerBackupData(plexBackup, emptyList()), includeTokens = false)
                    stash?.put("createdAt", backupCreatedAt)
                    displayPrefs?.edit()?.putString(plexRestoredKey(key), stash?.toString())?.apply()
                    needsSignIn += "Plex"
                }
            }
        }
        return MediaServerRestoreOutcome(restored, kept, needsSignIn)
    }

    private fun DiscoveredMediaServer.withBackupChoices(backup: PlexBackupServer): DiscoveredMediaServer =
        copy(enabled = backup.enabled, libraryChoices = libraryChoices + backup.libraries)

    /**
     * After Plex is connected: choices a backup brought, for servers the profile has made no choice
     * about yet. A server already chosen about - the same account reconnected, say - keeps its choices.
     */
    private suspend fun applyRestoredPlexChoices() {
        val key = scopeKey() ?: return
        val raw = displayPrefs?.getString(plexRestoredKey(key), null) ?: return
        // Kept until the servers could be read, so a discovery that failed does not lose them.
        if (plexServers.isEmpty()) return
        displayPrefs?.edit()?.remove(plexRestoredKey(key))?.apply()
        val backup = runCatching { parseMediaServerBackup(org.json.JSONObject(raw)).plex }.getOrNull() ?: return
        val untouched = plexServers.values.filter { it.libraryChoices.isEmpty() && it.enabled }.map { it.id }.toSet()
        val applicable = backup.servers.filter { it.id in untouched }
        if (applicable.isEmpty()) return
        saveChoices(retry = false) { servers -> servers.map { current -> applicable.firstOrNull { it.id == current.id }?.let { current.withBackupChoices(it) } ?: current } }
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
            // A plain local address after each plex.direct one, for routers that will not resolve it.
            connections = withPlexLanFallbacks(connections.orEmpty().mapNotNull { connection ->
                val uri = connection.uri ?: return@mapNotNull null
                MediaServerEndpoint(serverId, uri, connection.local == true, connection.relay == true, token)
            }),
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
            // A plain local address after each plex.direct one, for routers that will not resolve it.
            connections = withPlexLanFallbacks(connections.orEmpty().mapNotNull { connection ->
                val uri = connection.uri ?: return@mapNotNull null
                MediaServerEndpoint(serverId, uri, connection.local == true, connection.relay == true, accessToken)
            }),
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
    /**
     * Set when the server that answered is the other member of the family - a Jellyfin server typed
     * in as Emby, or the other way round - to the name of the page to add it on instead.
     */
    val sibling: String? = null,
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
    val revision: Int? = null,
    val updatedAt: String? = null,
    /** When the viewer's choices last changed: not a renewal or a library report. */
    val choicesUpdatedAt: String? = null,
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

internal data class JellyfinCloudStatusDto(
    val connected: Boolean? = null,
    val status: String? = null,
    val revision: Int? = null,
    val updatedAt: String? = null,
)

internal data class JellyfinCloudLibraryDto(val key: String? = null, val title: String? = null, val kind: String? = null)

/** One server in the profile's Jellyfin setup at StreamDek. [accessToken] is never printed. */
internal data class JellyfinCloudServerDto(
    val id: String? = null,
    val name: String? = null,
    val addresses: List<String>? = null,
    val userId: String? = null,
    val userName: String? = null,
    val enabled: Boolean? = null,
    val libraries: Map<String, Boolean>? = null,
    val catalog: List<JellyfinCloudLibraryDto>? = null,
    val state: String? = null,
    val addedAt: String? = null,
    val updatedAt: String? = null,
    val accessToken: String? = null,
) {
    override fun toString(): String = "JellyfinCloudServerDto(id=$id, name=$name)"
}

internal data class JellyfinCloudRemovedDto(val id: String? = null, val removedAt: String? = null)

internal data class JellyfinCloudServersDto(
    val status: JellyfinCloudStatusDto? = null,
    val removed: List<JellyfinCloudRemovedDto>? = null,
    val servers: List<JellyfinCloudServerDto>? = null,
)

/** A library as StreamDek records it for the web portal: key, name and kind, nothing else. */
internal fun MediaServerLibrary.toCatalogEntry(): Map<String, Any?> = mapOf(
    "key" to key,
    "title" to title,
    "kind" to when (kind) {
        MediaServerLibraryKind.Movies -> "movies"
        MediaServerLibraryKind.Shows -> "shows"
        MediaServerLibraryKind.Other -> "other"
    },
)

internal fun List<MediaServerLibrary>.catalogSignature(): String = joinToString("|") { "${it.key}:${it.title}:${it.kind}" }

/** A short one-way fingerprint of a token (the first 8 hex of its SHA-256), as StreamDek computes it. */
internal fun tokenHint(token: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }.take(8)

/** An ISO-8601 time from StreamDek as epoch milliseconds; 0 when absent or unreadable. */
internal fun parseInstant(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

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
private const val KEY_REMOVED_ENTRIES = "removedEntries"
private const val KEY_COLLAPSED_SERVERS = "collapsedServers"
private const val KEY_SERVER_ORDER = "serverOrder"
/** Only read, to carry over an order saved by the first version of row ordering. */
private const val KEY_ROW_ORDER = "rowOrder"
private const val KEY_LIBRARY_ORDER = "libraryOrder"
private const val KEY_LAST_PAGE_PROVIDER = "lastPageProvider"
