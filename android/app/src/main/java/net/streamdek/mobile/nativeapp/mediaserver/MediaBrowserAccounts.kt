package net.streamdek.mobile.nativeapp.mediaserver

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.EmbyConnectServer
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.EmbyConnectSession
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinAccount
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinAuthResult
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinClient
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinEndpoint
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinProvider
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinUser
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.MediaBrowserFlavor
import java.util.concurrent.ConcurrentHashMap

/**
 * The sign-ins to one family member's servers - Jellyfin's or Emby's - on this device and at
 * StreamDek. One instance per flavor; see [MediaBrowserFlavor].
 *
 * Signed in from any device or the web portal, and kept with the profile at StreamDek, so that
 * signing in once is signing in on every device the profile is used on. StreamDek holds each
 * server's addresses, user, library choices and access token (sealed, and handed only to the
 * profile's own signed-in devices); this device keeps its copy in the same encrypted vault as
 * Plex's, under its own key per profile, and talks to the servers directly.
 *
 * The profile's copy is the one followed. A change made here is saved there against the revision
 * this device last read, and a device offline at the time sends it on the next sync - unless the
 * same server was changed elsewhere since, which then wins. A sign-in is always the viewer's latest
 * word and is never refused. See [syncWithCloud].
 *
 * Moved here from [MediaServerManager], where it was Jellyfin's alone, so Emby shares every rule
 * rather than copying them. Jellyfin keeps exactly the storage keys and StreamDek routes it had.
 */
class MediaBrowserAccounts internal constructor(
    val flavor: MediaBrowserFlavor,
    internal val client: JellyfinClient,
    internal val provider: JellyfinProvider,
    private val host: Host,
) {
    /** What the manager lends: its session, storage, StreamDek calls and the shared server order. */
    internal interface Host {
        val scope: CoroutineScope
        val vault: MediaServerVault?
        val prefs: SharedPreferences?
        fun scopeKey(): String?
        fun activeScope(): String?
        fun serverOrder(): List<String>
        fun bump()
        suspend fun <T> call(type: Class<T>, method: String, path: String, body: Map<String, Any?>? = null): T?
    }

    private val providerId = flavor.providerId
    private val cloudBase = "/media-servers/$providerId"
    private val tag = "StreamDekMediaServers"

    private val _state = MutableStateFlow(MediaServerUiState(provider = providerId))
    /** This flavor's standing, in the same shape as Plex's, for its settings page and destination. */
    val state: StateFlow<MediaServerUiState> = _state.asStateFlow()

    private val preferred = ConcurrentHashMap<String, String>()

    private val _ambient = MutableStateFlow(host.prefs?.getBoolean(ambientKey(), true) ?: true)
    /** Whether this server's page and lists wear its colour wash. On until switched off. */
    val ambient: StateFlow<Boolean> = _ambient.asStateFlow()

    private fun ambientKey() = "${providerId}Ambient"

    fun setAmbient(enabled: Boolean) {
        _ambient.value = enabled
        host.prefs?.edit()?.putBoolean(ambientKey(), enabled)?.apply()
    }

    init {
        provider.onAddressChosen = { serverId, url -> preferred[serverId] = url }
    }

    private suspend inline fun <reified T> cloudGet(path: String): T? = host.call(T::class.java, "GET", path)
    private suspend inline fun <reified T> cloudPut(path: String, body: Map<String, Any?>): T? = host.call(T::class.java, "PUT", path, body)
    private suspend inline fun <reified T> cloudDelete(path: String): T? = host.call(T::class.java, "DELETE", path)

    fun accounts(): List<JellyfinAccount> = provider.accounts()

    // ── Session ─────────────────────────────────────────────────────────────────────────────────

    /** The session or profile changed: forget the last one, and show what the device holds for [key]. */
    internal fun onSessionChanged(key: String?) {
        provider.reset()
        preferred.clear()
        forgetProfileSyncState()
        if (key == null) {
            _state.value = MediaServerUiState(provider = providerId, available = false)
            return
        }
        restore(key)
    }

    /** Sign-out: sign-ins kept with the profile stay there; one only this device had is ended on its server. */
    internal fun clearDevice() {
        val deviceOnly = provider.accounts().filterNot { it.cloudSynced }
        if (deviceOnly.isNotEmpty()) host.scope.launch { deviceOnly.forEach { account -> logoutQuietly(account) } }
        provider.reset()
        _state.value = MediaServerUiState(provider = providerId)
    }

    private fun vaultKey(key: String) = "$providerId:$key"

    private fun restore(key: String) {
        val snapshot = host.vault?.load(vaultKey(key))
        val accounts = snapshot?.servers.orEmpty().mapNotNull { stored ->
            val id = stored.id ?: return@mapNotNull null
            val token = stored.accessToken ?: return@mapNotNull null
            val user = stored.userId ?: return@mapNotNull null
            stored.preferredUri?.let { preferred[id] = it }
            JellyfinAccount(
                serverId = id,
                name = stored.name ?: flavor.label,
                addresses = stored.connections.orEmpty().mapNotNull { it.uri },
                userId = user,
                userName = stored.userName,
                token = token,
                enabled = stored.enabled != false,
                libraryChoices = stored.libraryChoices.orEmpty(),
                cloudSynced = stored.cloudSynced == true,
                signedInAtMs = stored.signedInAtMs ?: 0L,
                choicesChangedAtMs = stored.choicesChangedAtMs ?: 0L,
                fromBackup = stored.fromBackup == true,
            )
        }
        loadRestored(key)
        if (accounts.isEmpty()) {
            _state.value = MediaServerUiState(provider = providerId, available = true)
            return
        }
        provider.setAccounts(accounts)
        snapshot?.servers.orEmpty().forEach { stored ->
            val id = stored.id ?: return@forEach
            provider.seedLibraries(id, stored.libraries.orEmpty().mapNotNull { library ->
                val libraryKey = library.key ?: return@mapNotNull null
                val kind = runCatching { MediaServerLibraryKind.valueOf(library.kind ?: "") }.getOrNull() ?: return@mapNotNull null
                MediaServerLibrary(id, libraryKey, library.title ?: libraryKey, kind, stored.libraryChoices?.get(libraryKey) ?: (kind != MediaServerLibraryKind.Other), library.itemCount)
            })
        }
        _state.value = MediaServerUiState(provider = providerId, available = true, linked = true, accountName = leadingUser(accounts))
        publish()
        host.bump()
    }

    /**
     * Brings this device in step with the profile's setup at StreamDek, then reaches every server and
     * reads its libraries again. A device with nothing signed in still asks, which is how a television
     * picks up a server signed in to from the phone or the web portal.
     */
    suspend fun refresh(force: Boolean) {
        val key = host.scopeKey() ?: return
        runCatching { syncWithCloud(key) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Log.w(tag, "$providerId sync failed: ${it.javaClass.simpleName}") }
        if (provider.accounts().isEmpty()) {
            publish()
            return
        }
        _state.update { it.copy(refreshing = true) }
        try {
            provider.connect(force)
            provider.accounts().filter { it.enabled }.forEach { account -> runCatching { provider.libraries(account.serverId, force) } }
            if (key == host.activeScope()) persist(key)
            reportCatalogs()
            host.bump()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w(tag, "$providerId refresh failed: ${error.javaClass.simpleName}")
        } finally {
            _state.update { it.copy(refreshing = false) }
            publish()
        }
    }

    /**
     * The name shown as "Connected as": the account on the server at the top of the viewer's list.
     * With several servers each has its own sign-in, and the one the viewer put first is the one
     * they think of as theirs. Without a chosen order, the list is alphabetical.
     */
    private fun leadingUser(accounts: List<JellyfinAccount>): String? {
        val rank = host.serverOrder().withIndex().associate { it.value to it.index }
        return accounts.minWithOrNull(
            compareBy<JellyfinAccount>({ rank[mediaServerEntryKey(providerId, it.serverId)] ?: Int.MAX_VALUE }, { it.name.lowercase() }),
        )?.userName
    }

    fun publish() {
        reportRefused()
        val accounts = provider.accounts()
        val views = accounts.map { account ->
            MediaServerView(
                id = account.serverId,
                name = account.name,
                owned = true,
                ownerName = account.userName,
                enabled = account.enabled,
                reachability = provider.reachability(account.serverId),
                libraries = provider.cachedLibraries(account.serverId),
                problem = provider.problem(account.serverId),
            )
        }.sortedBy { it.name.lowercase() }
        _state.update {
            it.copy(
                available = host.scopeKey() != null,
                linked = accounts.isNotEmpty(),
                needsAttention = views.isNotEmpty() && views.all { view -> (view.reachability as? MediaServerReachability.Offline)?.reason == OfflineReason.Unauthorized },
                accountName = leadingUser(accounts),
                servers = views,
            )
        }
    }

    private fun persist(key: String) {
        val store = host.vault ?: return
        val accounts = provider.accounts()
        if (accounts.isEmpty()) {
            store.remove(vaultKey(key))
            return
        }
        store.save(
            vaultKey(key),
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
                        libraries = provider.cachedLibraries(account.serverId).map { MediaServerVault.StoredLibrary(it.key, it.title, it.kind.name, it.itemCount) },
                        preferredUri = preferred[account.serverId],
                        userId = account.userId,
                        userName = account.userName,
                        cloudSynced = account.cloudSynced,
                        signedInAtMs = account.signedInAtMs,
                        choicesChangedAtMs = account.choicesChangedAtMs,
                        fromBackup = account.fromBackup,
                    )
                },
                savedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    // ── Finding and signing in ──────────────────────────────────────────────────────────────────

    /**
     * Looks for a server at what the viewer typed. Null when nothing there answers as one of the
     * family. A server of the other kind answers with [JellyfinServerCandidate.sibling] set, so the
     * page can say where to add it instead of failing at sign-in.
     */
    suspend fun findServer(input: String): JellyfinServerCandidate? {
        for (url in JellyfinClient.candidates(input)) {
            val info = client.publicInfo(url) ?: continue
            val id = info.id ?: continue
            return JellyfinServerCandidate(
                id = id,
                name = info.serverName?.takeIf { it.isNotBlank() } ?: flavor.label,
                url = url,
                localAddress = info.localAddress?.trimEnd('/')?.takeIf { it.isNotBlank() && !it.equals(url, true) },
                version = info.version,
                quickConnect = client.quickConnectEnabled(url),
                sibling = flavor.sibling.label.takeIf { flavor.isSibling(info.productName) },
            )
        }
        return null
    }

    /** Servers that answered this flavor's local-network discovery broadcast, checked before being offered. */
    suspend fun discoverServers(): List<JellyfinServerCandidate> =
        client.discover().mapNotNull { reply ->
            val address = reply.address?.trimEnd('/') ?: return@mapNotNull null
            findServer(address)?.takeIf { it.sibling == null }
        }.distinctBy { it.id }

    /** Starts Quick Connect (Jellyfin only): a code the viewer approves from an app already signed in. */
    suspend fun startQuickConnect(server: JellyfinServerCandidate): JellyfinQuickConnectCode? {
        if (!flavor.quickConnect) return null
        val started = client.quickConnectInitiate(server.url) ?: return null
        val code = started.code ?: return null
        val secret = started.secret ?: return null
        return JellyfinQuickConnectCode(server, code, secret, System.currentTimeMillis() + 10 * 60_000L)
    }

    /** One check of a Quick Connect code. On approval the server is added before this returns. */
    suspend fun pollQuickConnect(code: JellyfinQuickConnectCode): MediaServerLinkStatus {
        if (System.currentTimeMillis() > code.expiresAtMs) return MediaServerLinkStatus.Expired
        val state = client.quickConnectState(code.server.url, code.secret) ?: return MediaServerLinkStatus.Pending
        if (state.authenticated != true) return MediaServerLinkStatus.Pending
        val result = client.authenticateWithQuickConnect(code.server.url, code.secret) ?: return MediaServerLinkStatus.Failed
        return addAccount(code.server, result)
    }

    /**
     * Signs in with a username and password. The password is sent once, to the server itself, and
     * is not kept: only the token the server issues is stored.
     */
    suspend fun signIn(server: JellyfinServerCandidate, username: String, password: String): MediaServerLinkStatus {
        val result = try {
            client.authenticateByName(server.url, username.trim(), password)
        } catch (_: JellyfinClient.UnauthorizedException) {
            null
        } ?: return MediaServerLinkStatus.Failed
        return addAccount(server, result)
    }

    // ── Emby Connect ────────────────────────────────────────────────────────────────────────────

    /**
     * Signs in to Emby Connect and lists the servers the account is linked to. The emby.media
     * password goes to emby.media once and is not kept; neither is the Connect token, beyond the
     * [EmbyConnectSignIn] the page holds while the viewer picks servers.
     */
    suspend fun embyConnect(nameOrEmail: String, password: String): EmbyConnectResult {
        if (!flavor.embyConnect) return EmbyConnectResult.Unavailable
        val session = try {
            client.embyConnectSignIn(nameOrEmail.trim(), password)
        } catch (_: JellyfinClient.UnauthorizedException) {
            return EmbyConnectResult.Refused
        } ?: return EmbyConnectResult.Unreachable
        val servers = runCatching { client.embyConnectServers(session) }.getOrDefault(emptyList())
        return EmbyConnectResult.SignedIn(EmbyConnectSignIn(session, servers))
    }

    /**
     * Adds one Emby Connect server: its addresses are tried (local first), the access key is traded
     * for a local sign-in on whichever answers, and that is kept like any other sign-in.
     */
    internal suspend fun addEmbyConnectServer(signIn: EmbyConnectSignIn, server: EmbyConnectServer): MediaServerLinkStatus {
        val connectUserId = signIn.session.userId ?: return MediaServerLinkStatus.Failed
        val accessKey = server.accessKey ?: return MediaServerLinkStatus.Failed
        val systemId = server.systemId ?: return MediaServerLinkStatus.Failed
        val addresses = listOfNotNull(server.localAddress, server.url).map { it.trimEnd('/') }.filter { it.startsWith("http") }.distinct()
        for (url in addresses.sortedBy { if (JellyfinClient.isLocalHost(it.substringAfter("://").substringBefore('/').substringBefore(':'))) 0 else 1 }) {
            val exchange = client.embyConnectExchange(url, accessKey, connectUserId) ?: continue
            val token = exchange.accessToken ?: continue
            val userId = exchange.localUserId ?: continue
            val user = client.user(JellyfinEndpoint(url, token), userId)
            val candidate = JellyfinServerCandidate(
                id = systemId,
                name = server.name?.takeIf { it.isNotBlank() } ?: flavor.label,
                url = url,
                localAddress = addresses.firstOrNull { it != url },
                version = null,
                quickConnect = false,
            )
            return addAccount(candidate, JellyfinAuthResult(user = user ?: JellyfinUser(id = userId, name = signIn.session.displayName), accessToken = token, serverId = systemId))
        }
        return MediaServerLinkStatus.Failed
    }

    /** As above, by the server's id from [EmbyConnectSignIn.choices]. */
    suspend fun addEmbyConnectServer(signIn: EmbyConnectSignIn, systemId: String): MediaServerLinkStatus =
        signIn.server(systemId)?.let { addEmbyConnectServer(signIn, it) } ?: MediaServerLinkStatus.Failed

    private suspend fun addAccount(server: JellyfinServerCandidate, result: JellyfinAuthResult): MediaServerLinkStatus {
        val key = host.scopeKey() ?: return MediaServerLinkStatus.Failed
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
            signedInAtMs = System.currentTimeMillis(),
        )
        // Signing in again to a server already here replaces that sign-in and keeps its choices.
        val previous = provider.accounts().firstOrNull { it.serverId == serverId }
        provider.setAccounts(
            provider.accounts().filterNot { it.serverId == serverId } +
                account.copy(
                    addresses = (account.addresses + previous?.addresses.orEmpty()).distinct(),
                    enabled = previous?.enabled ?: true,
                    libraryChoices = previous?.libraryChoices.orEmpty(),
                ),
        )
        provider.connect(force = true)
        runCatching { provider.libraries(serverId, force = true) }
        persist(key)
        publish()
        host.bump()
        // Kept with the profile, so every other device - and the web portal - has it too.
        pushSignIn(key, serverId)
        return MediaServerLinkStatus.Linked(result.user.name)
    }

    // ── Choices ─────────────────────────────────────────────────────────────────────────────────

    fun setServerEnabled(serverId: String, enabled: Boolean) = change { accounts ->
        accounts.map { if (it.serverId == serverId) it.copy(enabled = enabled) else it }
    }

    fun setLibraryEnabled(serverId: String, libraryKey: String, enabled: Boolean) = change { accounts ->
        accounts.map { account ->
            if (account.serverId != serverId) account
            else account.copy(libraryChoices = provider.cachedLibraries(serverId).associate { it.key to it.enabled } + account.libraryChoices + (libraryKey to enabled))
        }
    }

    private fun change(change: (List<JellyfinAccount>) -> List<JellyfinAccount>): Boolean {
        val key = host.scopeKey() ?: return false
        val now = System.currentTimeMillis()
        val before = provider.accounts().associateBy { it.serverId }
        val after = change(before.values.toList()).map { account ->
            val old = before[account.serverId]
            if (old != null && (old.enabled != account.enabled || old.libraryChoices != account.libraryChoices)) account.copy(choicesChangedAtMs = now) else account
        }
        provider.setAccounts(after)
        persist(key)
        publish()
        host.bump()
        val changed = after.filter { it.choicesChangedAtMs == now }.map { it.serverId }
        if (changed.isNotEmpty()) host.scope.launch { changed.forEach { pushChoices(key, it) } }
        return true
    }

    private suspend fun logoutQuietly(account: JellyfinAccount) {
        val url = preferred[account.serverId] ?: account.addresses.firstOrNull() ?: return
        runCatching { client.logout(JellyfinEndpoint(url, account.token)) }
    }

    /**
     * Signs out of one server - or all of them - for this profile, on every device: the session is
     * ended on the server and the server is removed from the profile at StreamDek. If StreamDek
     * cannot be reached, the removal is remembered and sent on the next sync, so the server does not
     * come back from the profile's copy.
     */
    suspend fun disconnect(serverId: String? = null): Boolean {
        val key = host.scopeKey() ?: return false
        val leaving = provider.accounts().filter { serverId == null || it.serverId == serverId }
        leaving.forEach { account ->
            logoutQuietly(account)
            account.addresses.forEach(MediaServerAuth::forget)
            preferred.remove(account.serverId)
        }
        provider.setAccounts(provider.accounts().filterNot { account -> leaving.any { it.serverId == account.serverId } })
        persist(key)
        publish()
        host.bump()
        // Recorded before asking, so a sync running meanwhile cannot bring the server back.
        leaving.forEach { account -> rememberPendingRemoval(key, account.serverId) }
        sync.withLock {
            leaving.forEach { account ->
                if (cloudDelete<JellyfinCloudStatusDto>("$cloudBase/servers/${account.serverId}") != null) clearPendingRemoval(key, account.serverId)
            }
        }
        return true
    }

    // ── At StreamDek ────────────────────────────────────────────────────────────────────────────

    /** The profile's setup revision at StreamDek, as last read; choices are saved against it. */
    @Volatile private var revision: Int? = null

    /**
     * One exchange with StreamDek at a time: two quick toggles, or a toggle during a sync, would
     * otherwise race - the first to finish clearing the second's "not sent yet" mark.
     */
    private val sync = Mutex()

    /** Servers found refusing their token and already reported, so the report is sent once. */
    private val reportedRefused = ConcurrentHashMap.newKeySet<String>()
    private val reportedCatalog = ConcurrentHashMap<String, String>()

    /** When the profile's copy of each server last changed, and which were removed when; as last read. */
    private val cloudChangedAt = ConcurrentHashMap<String, Long>()
    private val cloudRemovedAt = ConcurrentHashMap<String, Long>()

    /** What was read about one profile, forgotten when another is chosen so it is never judged by it. */
    private fun forgetProfileSyncState() {
        revision = null
        reportedRefused.clear()
        reportedCatalog.clear()
        cloudChangedAt.clear()
        cloudRemovedAt.clear()
    }

    private fun pendingRemovalsKey(key: String) = "${providerId}PendingRemovals:$key"

    private fun rememberPendingRemoval(key: String, serverId: String) {
        val prefs = host.prefs ?: return
        val current = prefs.getStringSet(pendingRemovalsKey(key), emptySet()).orEmpty()
        prefs.edit().putStringSet(pendingRemovalsKey(key), current + serverId).apply()
    }

    private fun pendingRemovals(key: String): Set<String> =
        host.prefs?.getStringSet(pendingRemovalsKey(key), emptySet()).orEmpty()

    private fun clearPendingRemoval(key: String, serverId: String) {
        val prefs = host.prefs ?: return
        prefs.edit().putStringSet(pendingRemovalsKey(key), pendingRemovals(key) - serverId).apply()
    }

    private fun JellyfinAccount.signInBody(): Map<String, Any?> = mapOf(
        "name" to name,
        "addresses" to addresses,
        "userId" to userId,
        "userName" to userName,
        "enabled" to enabled,
        "libraries" to libraryChoices,
        "catalog" to provider.cachedLibraries(serverId).map { it.toCatalogEntry() },
        // Last, so a truncated body in any log never reaches it.
        "accessToken" to token,
    )

    /** Sends a sign-in made on this device to the profile's copy. Left to the next sync if StreamDek is away. */
    private suspend fun pushSignIn(key: String, serverId: String) = sync.withLock { pushSignInLocked(key, serverId) }

    private suspend fun pushSignInLocked(key: String, serverId: String) {
        val account = provider.accounts().firstOrNull { it.serverId == serverId } ?: return
        val sentChoicesAt = account.choicesChangedAtMs
        val saved = cloudPut<JellyfinCloudStatusDto>("$cloudBase/servers/$serverId", account.signInBody()) ?: return
        revision = saved.revision ?: revision
        // Choices changed again while this was on its way stay marked, and are sent next.
        mark(key, serverId) { it.copy(cloudSynced = true, choicesChangedAtMs = if (it.choicesChangedAtMs == sentChoicesAt) 0L else it.choicesChangedAtMs, fromBackup = false) }
        clearPendingRemoval(key, serverId)
        forgetRestored(key, serverId)
    }

    /**
     * Sends a change of choices made on this device, against the revision last read. Refused when
     * the profile moved on since; then the current setup is read (which re-applies this change on
     * top, unless the server was changed elsewhere afterwards) and sent once more.
     */
    private suspend fun pushChoices(key: String, serverId: String) = sync.withLock { pushChoicesLocked(key, serverId) }

    private suspend fun pushChoicesLocked(key: String, serverId: String) {
        repeat(2) { attempt ->
            val account = provider.accounts().firstOrNull { it.serverId == serverId } ?: return
            val sentAt = account.choicesChangedAtMs
            if (sentAt == 0L) return
            if (!account.cloudSynced) return pushSignInLocked(key, serverId)
            val body = mapOf(
                "enabled" to account.enabled,
                "libraries" to (provider.cachedLibraries(serverId).associate { it.key to it.enabled } + account.libraryChoices),
            ) + (revision?.let { mapOf("baseRevision" to it) } ?: emptyMap())
            val saved = cloudPut<JellyfinCloudStatusDto>("$cloudBase/servers/$serverId", body)
            if (saved != null) {
                revision = saved.revision ?: revision
                mark(key, serverId) { if (it.choicesChangedAtMs == sentAt) it.copy(choicesChangedAtMs = 0L) else it }
                return
            }
            if (attempt == 0) runCatching { syncWithCloudLocked(key, pushPending = false) }
        }
    }

    private fun mark(key: String, serverId: String, change: (JellyfinAccount) -> JellyfinAccount) {
        if (key != host.scopeKey()) return
        provider.setAccounts(provider.accounts().map { if (it.serverId == serverId) change(it) else it })
        persist(key)
    }

    /**
     * Makes this device's sign-ins match the profile's copy at StreamDek.
     *
     *  - A server in the profile's copy is used as it is there: its token, user, addresses (with any
     *    this device also knows) and choices - except choices changed here and not yet sent, which
     *    are kept and sent, unless the server was changed elsewhere after them.
     *  - A server only on this device that StreamDek once had was removed elsewhere, and goes. One
     *    StreamDek never had (signed in offline, or before sync existed) is sent up - unless a
     *    removal of that server was recorded after this device signed in.
     *  - Removals made here while StreamDek was away are sent first.
     *
     * If StreamDek does not answer, nothing changes: the device carries on with what it has.
     */
    internal suspend fun syncWithCloud(key: String, pushPending: Boolean = true) =
        sync.withLock { syncWithCloudLocked(key, pushPending) }

    private suspend fun syncWithCloudLocked(key: String, pushPending: Boolean) {
        pendingRemovals(key).forEach { serverId ->
            if (cloudDelete<JellyfinCloudStatusDto>("$cloudBase/servers/$serverId") != null) clearPendingRemoval(key, serverId)
        }
        val cloud = cloudGet<JellyfinCloudServersDto>("$cloudBase/servers?view=device") ?: return
        if (key != host.scopeKey()) return
        revision = cloud.status?.revision ?: revision
        val pendingRemoval = pendingRemovals(key)
        val removedAt = cloud.removed.orEmpty().mapNotNull { entry -> entry.id?.let { it to parseInstant(entry.removedAt) } }.toMap()
        cloudRemovedAt.clear()
        cloudRemovedAt.putAll(removedAt)
        cloudChangedAt.clear()
        cloud.servers.orEmpty().forEach { remote -> remote.id?.let { cloudChangedAt[it] = parseInstant(remote.updatedAt) } }
        // A server signed in to on another device no longer needs signing in to from a backup here.
        cloud.servers.orEmpty().mapNotNull { it.id }.forEach { forgetRestored(key, it) }
        val local = provider.accounts().associateBy { it.serverId }
        val next = mutableListOf<JellyfinAccount>()
        val toSignIn = mutableListOf<String>()
        val toChoose = mutableListOf<String>()
        cloud.servers.orEmpty().forEach { remote ->
            val id = remote.id ?: return@forEach
            if (id in pendingRemoval) return@forEach
            val mine = local[id]
            // No token from StreamDek (its copy could not be read): keep this device's own rather
            // than treating the server as gone.
            val token = remote.accessToken?.takeIf { it.isNotBlank() } ?: mine?.token ?: return@forEach
            val userId = remote.userId ?: return@forEach
            val addresses = remote.addresses.orEmpty().filter { it.startsWith("http") }
            if (addresses.isEmpty()) return@forEach
            val remoteChangedAt = parseInstant(remote.updatedAt)
            // A sign-in here that StreamDek has not heard of yet, newer than its copy: this device's wins.
            if (mine != null && !mine.cloudSynced && !mine.fromBackup && mine.signedInAtMs > remoteChangedAt) {
                next += mine.copy(addresses = (mine.addresses + addresses).distinct())
                toSignIn += id
                return@forEach
            }
            val keepMine = mine != null && mine.choicesChangedAtMs > remoteChangedAt
            next += JellyfinAccount(
                serverId = id,
                name = remote.name ?: mine?.name ?: flavor.label,
                addresses = (addresses + mine?.addresses.orEmpty()).distinct(),
                userId = userId,
                userName = remote.userName ?: mine?.userName,
                token = token,
                enabled = if (keepMine) mine!!.enabled else remote.enabled != false,
                libraryChoices = if (keepMine) mine!!.libraryChoices else remote.libraries.orEmpty(),
                cloudSynced = true,
                signedInAtMs = mine?.signedInAtMs ?: 0L,
                choicesChangedAtMs = if (keepMine) mine!!.choicesChangedAtMs else 0L,
                fromBackup = false,
            )
            if (keepMine) toChoose += id
            if (remote.state != "needs_sign_in") reportedRefused.remove(id)
        }
        local.values.filter { mine -> next.none { it.serverId == mine.serverId } }.forEach { mine ->
            val removal = removedAt[mine.serverId]
            when {
                // StreamDek had it and does not now: removed on another device or the portal.
                mine.cloudSynced -> Unit
                removal != null && removal >= mine.signedInAtMs -> Unit
                else -> {
                    next += mine
                    toSignIn += mine.serverId
                }
            }
        }
        val changed = next.toSet() != local.values.toSet()
        if (changed) {
            val leaving = local.values.filter { mine -> next.none { it.serverId == mine.serverId } }
            leaving.forEach { mine -> mine.addresses.forEach(MediaServerAuth::forget); preferred.remove(mine.serverId) }
            provider.setAccounts(next)
            persist(key)
            publish()
            host.bump()
        }
        if (pushPending) {
            toSignIn.forEach { pushSignInLocked(key, it) }
            toChoose.forEach { pushChoicesLocked(key, it) }
        }
    }

    /** Tells StreamDek the libraries each server has, so the web portal can name them. Only changes. */
    private suspend fun reportCatalogs() {
        provider.accounts().filter { it.cloudSynced }.forEach { account ->
            val libraries = provider.cachedLibraries(account.serverId)
            if (libraries.isEmpty()) return@forEach
            val signature = libraries.catalogSignature()
            if (reportedCatalog[account.serverId] == signature) return@forEach
            val body = mapOf("catalog" to libraries.map { it.toCatalogEntry() })
            if (cloudPut<JellyfinCloudStatusDto>("$cloudBase/servers/${account.serverId}", body) != null) {
                reportedCatalog[account.serverId] = signature
            }
        }
    }

    /** A server whose token this device found refused: every other device shows "sign in again" too. */
    private fun reportRefused() {
        provider.accounts().filter { it.cloudSynced }.forEach { account ->
            val refused = (provider.reachability(account.serverId) as? MediaServerReachability.Offline)?.reason == OfflineReason.Unauthorized
            if (refused && reportedRefused.add(account.serverId)) {
                // With a fingerprint of the refused token, so the report cannot mark a newer sign-in.
                val hint = tokenHint(account.token)
                host.scope.launch { cloudPut<JellyfinCloudStatusDto>("$cloudBase/servers/${account.serverId}", mapOf("refused" to true, "refusedTokenHint" to hint)) }
            }
        }
    }

    // ── Backup and restore ──────────────────────────────────────────────────────────────────────

    private val _restored = MutableStateFlow<List<JellyfinBackupServer>>(emptyList())
    /** Servers a backup brought back without a sign-in, waiting for the viewer to sign in again. */
    val restored: StateFlow<List<JellyfinBackupServer>> = _restored.asStateFlow()

    private fun restoredKey(key: String) = "${providerId}Restored:$key"

    private fun loadRestored(key: String) {
        val raw = host.prefs?.getString(restoredKey(key), null)
        // Stored in the backup section's server shape, under its "jellyfin" list whatever the flavor.
        _restored.value = runCatching { parseMediaServerBackup(org.json.JSONObject(raw ?: "{}")).jellyfin }.getOrDefault(emptyList())
    }

    private fun saveRestored(key: String, servers: List<JellyfinBackupServer>) {
        val prefs = host.prefs ?: return
        val json = buildMediaServerBackup(MediaServerBackupData(null, servers.map { it.copy(accessToken = null) }), includeTokens = false)
        if (json == null) prefs.edit().remove(restoredKey(key)).apply() else prefs.edit().putString(restoredKey(key), json.toString()).apply()
        if (key == host.scopeKey()) _restored.value = servers
    }

    private fun forgetRestored(key: String, serverId: String) {
        if (_restored.value.none { it.id == serverId }) return
        saveRestored(key, _restored.value.filterNot { it.id == serverId })
    }

    /** The viewer set aside a server from a backup rather than sign in to it again. */
    fun dismissRestored(serverId: String) {
        val key = host.scopeKey() ?: return
        forgetRestored(key, serverId)
    }

    /** One profile's servers as a backup carries them, read from what this device holds for [scope]. */
    internal fun backupServers(scope: String): List<JellyfinBackupServer> =
        host.vault?.load(vaultKey(scope))?.servers.orEmpty().mapNotNull { stored ->
            val id = stored.id ?: return@mapNotNull null
            JellyfinBackupServer(
                id = id,
                name = stored.name ?: flavor.label,
                addresses = stored.connections.orEmpty().mapNotNull { it.uri },
                userId = stored.userId,
                userName = stored.userName,
                enabled = stored.enabled != false,
                libraries = stored.libraryChoices.orEmpty(),
                accessToken = stored.accessToken,
            )
        }

    /**
     * Restores a backup's servers into the profile in use, under the rules of decideJellyfinRestore:
     * what changed on the profile or here after the backup stays, what was removed after it stays
     * removed, the rest takes the backup's choices. A server the profile no longer has comes back
     * from the backup's sign-in when credentials are restored, and otherwise waits to be signed in to.
     */
    internal suspend fun restoreFromBackup(
        servers: List<JellyfinBackupServer>,
        backupCreatedAt: Long,
        restoredLabels: MutableList<String>,
        kept: MutableList<String>,
        needsSignIn: MutableList<String>,
    ) {
        if (servers.isEmpty()) return
        val key = host.scopeKey() ?: return
        runCatching { syncWithCloud(key, pushPending = false) }
        val local = provider.accounts().associateBy { it.serverId }.toMutableMap()
        val waiting = _restored.value.associateBy { it.id }.toMutableMap()
        servers.forEach { server ->
            val label = "${flavor.label} · ${server.name}"
            val mine = local[server.id]
            val decision = decideJellyfinRestore(
                backupCreatedAt = backupCreatedAt,
                present = mine != null,
                sameChoices = mine != null && mine.enabled == server.enabled && server.libraries.all { (library, on) -> mine.libraryChoices[library] == on },
                hasToken = !server.accessToken.isNullOrBlank() && !server.userId.isNullOrBlank(),
                profileChangedAt = cloudChangedAt[server.id] ?: 0L,
                profileRemovedAt = cloudRemovedAt[server.id] ?: 0L,
                localChangedAt = mine?.choicesChangedAtMs ?: 0L,
            )
            when (decision) {
                MediaServerRestoreDecision.Unchanged -> restoredLabels += label
                MediaServerRestoreDecision.KeepNewer, MediaServerRestoreDecision.RemovedSince -> kept += label
                MediaServerRestoreDecision.ApplyChoices -> {
                    local[server.id] = mine!!.copy(
                        enabled = server.enabled,
                        libraryChoices = mine.libraryChoices + server.libraries,
                        choicesChangedAtMs = backupCreatedAt,
                    )
                    restoredLabels += label
                }
                MediaServerRestoreDecision.AddWithSignIn -> {
                    local[server.id] = JellyfinAccount(
                        serverId = server.id,
                        name = server.name,
                        addresses = server.addresses,
                        userId = server.userId!!,
                        userName = server.userName,
                        token = server.accessToken!!,
                        enabled = server.enabled,
                        libraryChoices = server.libraries,
                        cloudSynced = false,
                        signedInAtMs = backupCreatedAt,
                        choicesChangedAtMs = backupCreatedAt,
                        fromBackup = true,
                    )
                    restoredLabels += label
                }
                else -> {
                    waiting[server.id] = server.copy(accessToken = null)
                    needsSignIn += label
                }
            }
        }
        provider.setAccounts(local.values.toList())
        persist(key)
        saveRestored(key, waiting.values.toList())
        publish()
        host.bump()
        // Sends what the backup brought back, under the rules above; a server that turns out to
        // refuse its restored token shows as needing a sign-in like any other.
        runCatching { syncWithCloud(key) }
        host.scope.launch { refresh(force = true) }
    }
}

/** An Emby Connect account, signed in, with the servers it is linked to. Held only while the page picks. */
data class EmbyConnectSignIn internal constructor(
    internal val session: EmbyConnectSession,
    internal val servers: List<EmbyConnectServer>,
) {
    /** The linked servers, for the page to list: name and id, nothing secret. */
    val choices: List<Pair<String, String>> get() = servers.mapNotNull { server -> server.systemId?.let { it to (server.name ?: it) } }

    internal fun server(systemId: String): EmbyConnectServer? = servers.firstOrNull { it.systemId == systemId }

    override fun toString(): String = "EmbyConnectSignIn(servers=${servers.size})"
}

sealed interface EmbyConnectResult {
    data class SignedIn(val signIn: EmbyConnectSignIn) : EmbyConnectResult
    data object Refused : EmbyConnectResult
    data object Unreachable : EmbyConnectResult
    data object Unavailable : EmbyConnectResult
}
