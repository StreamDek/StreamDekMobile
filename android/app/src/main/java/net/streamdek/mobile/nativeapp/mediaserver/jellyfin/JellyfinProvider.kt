package net.streamdek.mobile.nativeapp.mediaserver.jellyfin

import android.util.Log
import net.streamdek.mobile.nativeapp.mediaserver.titleMatchesSearch
import net.streamdek.mobile.nativeapp.AddonStream
import net.streamdek.mobile.nativeapp.EpisodeItem
import net.streamdek.mobile.nativeapp.MediaDetail
import net.streamdek.mobile.nativeapp.MediaItem
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerEpisode
import net.streamdek.mobile.nativeapp.mediaserver.DiscoveredMediaServer
import net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerAuth
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerEpisodeProgress
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLabels
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLibrary
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLibraryKind
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerPage
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerPlaybackContext
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerPlaybackState
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerProgress
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerProvider
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReachability
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerResume
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRoute
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRow
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRowKind
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerSort
import net.streamdek.mobile.nativeapp.mediaserver.OfflineReason
import net.streamdek.mobile.nativeapp.mediaserver.mediaServerHomeRowId
import net.streamdek.mobile.nativeapp.mediaserver.plex.PlexDeviceCapabilities
import net.streamdek.mobile.nativeapp.mediaserver.plex.PlexDeviceCaps
import net.streamdek.mobile.nativeapp.mediaserver.plex.PlexMediaFacts
import net.streamdek.mobile.nativeapp.mediaserver.plex.PlexPlaybackMode
import net.streamdek.mobile.nativeapp.mediaserver.plex.PlexPlaybackPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A Jellyfin server the viewer signed in to on this device.
 *
 * [addresses] are every way to reach it, the one the viewer typed first. [token] is the signed-in
 * user's access token, which is why `toString` hides it.
 */
data class JellyfinAccount(
    val serverId: String,
    val name: String,
    val addresses: List<String>,
    val userId: String,
    val userName: String?,
    val token: String,
    val enabled: Boolean = true,
    val libraryChoices: Map<String, Boolean> = emptyMap(),
    /** Whether the profile's copy at StreamDek has this sign-in: false for one made offline, or before sync. */
    val cloudSynced: Boolean = false,
    /** When this device signed in, to tell a sign-in here from a removal made elsewhere afterwards. */
    val signedInAtMs: Long = 0L,
    /** When choices were changed here without StreamDek having heard yet; 0 when nothing is waiting. */
    val choicesChangedAtMs: Long = 0L,
    /** Put back from a backup: the profile's own sign-in, when it has one, is preferred to this one. */
    val fromBackup: Boolean = false,
) {
    override fun toString(): String = "JellyfinAccount(serverId=$serverId, name=$name, addresses=${addresses.size}, token=[redacted])"
}

/**
 * Jellyfin, as a StreamDek media server provider.
 *
 * The same contract, and the same habits, as the Plex provider: every read answers from the
 * device straight to the viewer's server, a server that is away costs one short timeout and then
 * is left alone for a while (thirty seconds, doubling to ten minutes), and nothing throws to a
 * caller - an unreachable server simply contributes nothing.
 *
 * Where Jellyfin differs it stays in here: its own sign-in, `Authorization: MediaBrowser` rather
 * than a token header, ticks rather than milliseconds, GUID item ids, and its own playback and
 * progress-reporting endpoints.
 */
internal class JellyfinProvider(
    private val client: JellyfinClient,
    private val labels: () -> MediaServerLabels,
    private val onStateChanged: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) : MediaServerProvider {
    override val id: String = JELLYFIN_PROVIDER_ID
    override val label: String = "Jellyfin"

    private data class ServerState(
        val account: JellyfinAccount,
        val baseUrl: String? = null,
        val reachability: MediaServerReachability = MediaServerReachability.Unknown,
        val retryAtMs: Long = 0L,
        val failures: Int = 0,
    )

    private data class Timed<T>(val atMs: Long, val value: T)

    /** One playback in progress: what Jellyfin's session reports have to name. */
    private data class Session(val itemId: String, val mediaSourceId: String, val playSessionId: String, val method: String, var started: Boolean = false)

    private val servers = ConcurrentHashMap<String, ServerState>()
    private val connectLocks = ConcurrentHashMap<String, Mutex>()
    private val libraries = ConcurrentHashMap<String, Timed<List<MediaServerLibrary>>>()
    private val items = ConcurrentHashMap<String, Timed<JellyfinItem>>()
    private val seasons = ConcurrentHashMap<String, Timed<List<JellyfinItem>>>()
    private val episodes = ConcurrentHashMap<String, Timed<List<JellyfinItem>>>()
    private val rowsCache = ConcurrentHashMap<Boolean, Timed<List<MediaServerRow>>>()
    private val sessions = ConcurrentHashMap<String, Session>()

    var onAddressChosen: (serverId: String, url: String) -> Unit = { _, _ -> }

    fun setAccounts(accounts: List<JellyfinAccount>) {
        val incoming = accounts.associateBy { it.serverId }
        servers.keys.retainAll(incoming.keys)
        libraries.keys.retainAll(incoming.keys)
        incoming.forEach { (id, account) ->
            val previous = servers[id]
            val same = previous != null && previous.account.token == account.token && previous.account.addresses.toSet() == account.addresses.toSet()
            servers[id] = if (same) previous!!.copy(account = account) else ServerState(account)
            // Choices are applied to libraries already read, so a switch shows at once.
            libraries[id]?.let { cached ->
                libraries[id] = cached.copy(value = cached.value.map { it.copy(enabled = account.libraryChoices[it.key] ?: defaultEnabled(it.kind)) })
            }
        }
        registerAuth()
        rowsCache.clear()
        onStateChanged()
    }

    /** Headers for every address of every signed-in server, so artwork and streams carry the token and URLs never do. */
    fun registerAuth() {
        servers.values.forEach { state ->
            val header = client.authorizationFor(state.account.token)
            state.account.addresses.forEach { MediaServerAuth.registerHeader(it, AUTH_HEADER, header) }
        }
    }

    fun accounts(): List<JellyfinAccount> = servers.values.map { it.account }

    /** Not used: Jellyfin servers are signed in to on the device, not discovered through an account. */
    override fun setServers(servers: List<DiscoveredMediaServer>) = Unit

    override fun reachability(serverId: String): MediaServerReachability =
        servers[serverId]?.reachability ?: MediaServerReachability.Unknown

    override fun reset() {
        servers.clear()
        legacyRoutes.clear()
        problems.clear()
        libraries.clear()
        items.clear()
        seasons.clear()
        episodes.clear()
        rowsCache.clear()
        sessions.clear()
        onStateChanged()
    }

    // ── Connections ─────────────────────────────────────────────────────────────────────────────

    private fun update(serverId: String, change: (ServerState) -> ServerState) {
        servers.computeIfPresent(serverId) { _, state -> change(state) }
        onStateChanged()
    }

    private fun route(url: String): MediaServerRoute =
        if (url.toHttpUrlOrNull()?.host?.let(JellyfinClient::isLocalHost) == true) MediaServerRoute.Local else MediaServerRoute.Remote

    private fun backoffMs(failures: Int): Long = (30_000L shl (failures - 1).coerceIn(0, 5)).coerceAtMost(600_000L)

    /** Tries the server's addresses together, local first, and keeps the first that accepts the sign-in. */
    private suspend fun connectServer(serverId: String, force: Boolean): String? {
        val lock = connectLocks.getOrPut(serverId) { Mutex() }
        return lock.withLock {
            val state = servers[serverId] ?: return@withLock null
            if (!state.account.enabled) return@withLock null
            if (!force && state.reachability is MediaServerReachability.Online && state.baseUrl != null) return@withLock state.baseUrl
            if (!force && state.reachability is MediaServerReachability.Offline && now() < state.retryAtMs) return@withLock null
            val candidates = state.account.addresses.distinct()
                .sortedWith(compareBy<String>({ if (it == state.baseUrl) 0 else 1 }, { if (route(it) == MediaServerRoute.Local) 0 else 1 }, { if (it.startsWith("https")) 0 else 1 }))
            if (candidates.isEmpty()) {
                update(serverId) { it.copy(reachability = MediaServerReachability.Offline(OfflineReason.NoConnections)) }
                return@withLock null
            }
            update(serverId) { it.copy(reachability = MediaServerReachability.Connecting) }
            var sawUnauthorized = false
            val chosen = coroutineScope {
                val probes = candidates.map { url -> url to async { client.probe(JellyfinEndpoint(url, state.account.token), state.account.userId) } }
                var winner: String? = null
                for ((url, probe) in probes) {
                    when (probe.await()) {
                        JellyfinClient.ProbeResult.Ok -> { winner = url; break }
                        JellyfinClient.ProbeResult.Unauthorized -> sawUnauthorized = true
                        JellyfinClient.ProbeResult.Unreachable -> Unit
                    }
                }
                probes.forEach { (_, probe) -> probe.cancel() }
                winner
            }
            if (chosen != null) {
                update(serverId) { it.copy(baseUrl = chosen, reachability = MediaServerReachability.Online(route(chosen)), failures = 0, retryAtMs = 0L) }
                onAddressChosen(serverId, chosen)
                Log.i("StreamDekJellyfin", "server=$serverId route=${route(chosen)}")
            } else {
                update(serverId) {
                    val failures = it.failures + 1
                    it.copy(
                        baseUrl = null,
                        reachability = MediaServerReachability.Offline(if (sawUnauthorized) OfflineReason.Unauthorized else OfflineReason.Unreachable),
                        failures = failures,
                        retryAtMs = now() + backoffMs(failures),
                    )
                }
                Log.w("StreamDekJellyfin", "server=$serverId unreachable on ${candidates.size} addresses")
            }
            chosen
        }
    }

    override suspend fun connect(force: Boolean) {
        supervisorScope {
            servers.values.filter { it.account.enabled }.map { async { connectServer(it.account.serverId, force) } }.awaitAll()
        }
    }

    private suspend fun endpoint(serverId: String): JellyfinEndpoint? {
        val state = servers[serverId] ?: return null
        val url = if (state.reachability is MediaServerReachability.Online && state.baseUrl != null) state.baseUrl else connectServer(serverId, false)
        return url?.let { JellyfinEndpoint(it, servers[serverId]?.account?.token) }
    }

    private fun userId(serverId: String): String? = servers[serverId]?.account?.userId

    /**
     * Servers before 10.9 have only the per-user routes (/Users/{id}/Views and the like); later ones
     * answer both. A server that says 404 to the newer route is asked the older one, and remembered.
     */
    private val legacyRoutes = ConcurrentHashMap<String, Boolean>()

    /**
     * The last thing that went wrong reading each server, for the page to show when rows do not come:
     * a route's shape and the server's status, never a URL, a query, a header or a token.
     */
    private val problems = ConcurrentHashMap<String, String>()

    /** What last went wrong reading [serverId]'s titles, or null when its rows came. */
    fun problem(serverId: String): String? = problems[serverId]

    private fun shape(path: String) = path.replace(Regex("[0-9a-fA-F]{32}|[0-9a-fA-F-]{36}"), "{id}")

    private fun describe(result: Result<*>): String = when (val error = result.exceptionOrNull()) {
        is JellyfinClient.StatusException -> "HTTP ${error.code}"
        null -> "an empty reply"
        else -> error.javaClass.simpleName
    }

    private fun legacyPath(path: String, user: String): String? = when {
        path == "/UserViews" -> "/Users/$user/Views"
        path == "/UserItems/Resume" -> "/Users/$user/Items/Resume"
        path == "/Items" -> "/Users/$user/Items"
        path.startsWith("/UserItems/") -> "/Users/$user/Items/" + path.removePrefix("/UserItems/")
        path.startsWith("/UserPlayedItems/") -> "/Users/$user/PlayedItems/" + path.removePrefix("/UserPlayedItems/")
        path.startsWith("/UserFavoriteItems/") -> "/Users/$user/FavoriteItems/" + path.removePrefix("/UserFavoriteItems/")
        path.startsWith("/Items/") && path.count { it == '/' } == 2 -> "/Users/$user/Items/" + path.removePrefix("/Items/")
        else -> null
    }

    /** The route to ask first, and the one to fall back to on a 404. */
    private fun routesFor(serverId: String, path: String): Pair<String, String?> {
        val legacy = userId(serverId)?.let { legacyPath(path, it) } ?: return path to null
        return if (legacyRoutes[serverId] == true) legacy to null else path to legacy
    }

    private fun isNotFound(result: Result<*>) = (result.exceptionOrNull() as? JellyfinClient.StatusException)?.code == 404

    private suspend fun <T> get(serverId: String, path: String, type: Class<T>, query: Map<String, String> = emptyMap()): T? {
        val endpoint = endpoint(serverId) ?: return null
        return try {
            val (first, fallback) = routesFor(serverId, path)
            var result = client.fetch(endpoint, first, type, query)
            if (fallback != null && isNotFound(result)) {
                result = client.fetch(endpoint, fallback, type, query)
                if (result.isSuccess) legacyRoutes[serverId] = true
            }
            val value = result.getOrNull()
            if (value == null) {
                problems[serverId] = "GET ${shape(path)}: ${describe(result)}"
                // A server that did not answer at all is asked again how to reach it; one that
                // answered with an error for this one route is still there.
                if (result.exceptionOrNull() !is JellyfinClient.StatusException) update(serverId) { s -> s.copy(reachability = MediaServerReachability.Unknown) }
            }
            value
        } catch (_: JellyfinClient.UnauthorizedException) {
            problems[serverId] = "GET ${shape(path)}: refused"
            // One refused route is not a refused sign-in: only the server's answer about the user says that.
            val user = userId(serverId).orEmpty()
            if (client.probe(endpoint, user) == JellyfinClient.ProbeResult.Unauthorized) {
                update(serverId) { it.copy(reachability = MediaServerReachability.Offline(OfflineReason.Unauthorized), retryAtMs = now() + backoffMs(3)) }
            }
            null
        }
    }

    private suspend fun send(serverId: String, method: String, path: String, body: Any? = null, query: Map<String, String> = emptyMap()): Boolean {
        val endpoint = endpoint(serverId) ?: return false
        return runCatching {
            val (first, fallback) = routesFor(serverId, path)
            var result = client.sendResult(endpoint, method, first, body, query)
            if (fallback != null && isNotFound(result)) {
                result = client.sendResult(endpoint, method, fallback, body, query)
                if (result.isSuccess) legacyRoutes[serverId] = true
            }
            result.isSuccess
        }.getOrDefault(false)
    }

    private fun contextFor(serverId: String): JellyfinMappingContext? {
        val state = servers[serverId] ?: return null
        val base = state.baseUrl ?: return null
        val multiple = servers.values.count { it.account.enabled } > 1
        return JellyfinMappingContext(
            serverId = serverId,
            baseUrl = base,
            attribution = labels().attribution(label, state.account.name, multiple),
            libraryTitles = libraries[serverId]?.value.orEmpty().associate { it.key to it.title },
            seasonName = labels()::season,
            episodeName = labels()::episode,
        )
    }

    /** The mapping context, reaching the server first when it has not been reached yet or was let go. */
    private suspend fun readyContext(serverId: String): JellyfinMappingContext? = contextFor(serverId) ?: endpoint(serverId)?.let { contextFor(serverId) }

    private fun enabledServerIds(): List<String> = servers.values.filter { it.account.enabled }.map { it.account.serverId }

    // ── Libraries ───────────────────────────────────────────────────────────────────────────────

    private fun defaultEnabled(kind: MediaServerLibraryKind) = kind != MediaServerLibraryKind.Other

    override suspend fun libraries(serverId: String, force: Boolean): List<MediaServerLibrary> {
        val cached = libraries[serverId]
        if (!force && cached != null && now() - cached.atMs < LIBRARY_TTL_MS) return cached.value
        val user = userId(serverId) ?: return cached?.value.orEmpty()
        val choices = servers[serverId]?.account?.libraryChoices.orEmpty()
        // UserViews is already the signed-in user's own set: a library they may not open is not in it.
        val views = get(serverId, "/UserViews", JellyfinItems::class.java, mapOf("userId" to user))?.items
            ?: return cached?.value.orEmpty()
        val list = views.mapNotNull { view ->
            val key = view.id ?: return@mapNotNull null
            val kind = JellyfinMapping.libraryKind(view) ?: return@mapNotNull null
            MediaServerLibrary(
                serverId = serverId,
                key = key,
                title = view.name?.takeIf { it.isNotBlank() } ?: key,
                kind = kind,
                enabled = choices[key] ?: defaultEnabled(kind),
                itemCount = view.childCount,
            )
        }
        libraries[serverId] = Timed(now(), list)
        onStateChanged()
        return list
    }

    fun seedLibraries(serverId: String, known: List<MediaServerLibrary>) {
        if (libraries.containsKey(serverId) || known.isEmpty()) return
        libraries[serverId] = Timed(0L, known)
    }

    fun cachedLibraries(serverId: String): List<MediaServerLibrary> = libraries[serverId]?.value.orEmpty()

    private suspend fun enabledLibraries(serverId: String): List<MediaServerLibrary> = libraries(serverId, false).filter { it.enabled }

    // ── Queries ─────────────────────────────────────────────────────────────────────────────────

    private fun itemQuery(serverId: String, extra: Map<String, String>): Map<String, String> = buildMap {
        userId(serverId)?.let { put("userId", it) }
        put("fields", FIELDS)
        put("enableImageTypes", "Primary,Backdrop,Logo,Thumb")
        put("imageTypeLimit", "1")
        putAll(extra)
    }

    private suspend fun query(serverId: String, extra: Map<String, String>): JellyfinItems? =
        get(serverId, "/Items", JellyfinItems::class.java, itemQuery(serverId, extra))

    /** A page from one query result. Where the server gave no total, a full page means there may be more. */
    private fun pageOf(result: JellyfinItems?, start: Int, size: Int, map: (JellyfinItem) -> MediaItem?): MediaServerPage {
        result ?: return MediaServerPage(emptyList(), start, start)
        val raw = result.items.orEmpty()
        val total = result.totalRecordCount ?: if (raw.size >= size) Int.MAX_VALUE else start + raw.size
        return MediaServerPage(raw.mapNotNull(map).distinctBy { it.id }, start, total, returned = raw.size)
    }

    private fun sortParams(sort: MediaServerSort): Map<String, String> = when (sort) {
        MediaServerSort.RecentlyAdded -> mapOf("sortBy" to "DateCreated,SortName", "sortOrder" to "Descending,Ascending")
        MediaServerSort.Title -> mapOf("sortBy" to "SortName", "sortOrder" to "Ascending")
        MediaServerSort.ReleaseDate -> mapOf("sortBy" to "PremiereDate,SortName", "sortOrder" to "Descending,Ascending")
    }

    private fun libraryParams(library: MediaServerLibrary, start: Int, size: Int) = mapOf(
        "parentId" to library.key,
        "recursive" to "true",
        "includeItemTypes" to JellyfinMapping.includeTypes(library.kind),
        "startIndex" to start.toString(),
        "limit" to size.toString(),
    )

    private suspend fun libraryOf(serverId: String, key: String?): MediaServerLibrary? =
        key?.let { k -> libraries(serverId, false).firstOrNull { it.key == k } }

    // ── Rows ────────────────────────────────────────────────────────────────────────────────────

    override suspend fun rows(includeCollections: Boolean): List<MediaServerRow> {
        rowsCache[includeCollections]?.takeIf { now() - it.atMs < ROWS_TTL_MS }?.let { return it.value }
        val text = labels()
        val rows = supervisorScope {
            enabledServerIds().map { serverId ->
                async {
                    runCatching { serverRows(serverId, includeCollections, text) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        // An empty set is what a server not reached yet gives: it is not kept, so the next ask tries again.
        if (rows.isNotEmpty()) rowsCache[includeCollections] = Timed(now(), rows)
        return rows
    }

    private suspend fun serverRows(serverId: String, includeCollections: Boolean, text: MediaServerLabels): List<MediaServerRow> {
        val before = problems[serverId]
        val enabled = enabledLibraries(serverId)
        if (enabled.isEmpty()) return emptyList()
        val context = readyContext(serverId) ?: return emptyList()
        val serverName = servers[serverId]?.account?.name.orEmpty()
        val showsKey = enabled.firstOrNull { it.kind == MediaServerLibraryKind.Shows }?.key
        fun row(kind: MediaServerRowKind, title: String, type: String, items: List<MediaItem>, key: String?) =
            items.takeIf { it.isNotEmpty() }?.let { MediaServerRow("", title, serverId, serverName, kind, type, it, key) }
        // Each row within its own time: one slow query (Next Up can be, on a big library) costs only its own row.
        suspend fun within(kind: MediaServerRowKind, library: MediaServerLibrary?): List<MediaItem> =
            withTimeoutOrNull(ROW_TIMEOUT_MS) { rowItems(serverId, kind, library, 0, ROW_SIZE, context).items }
                ?: emptyList<MediaItem>().also { problems[serverId] = "${kind.name}: no answer within ${ROW_TIMEOUT_MS / 1000}s" }
        val built = coroutineScope {
            val nextUp = async {
                if (showsKey == null) null
                else row(MediaServerRowKind.NextUp, text.nextUp(), "tv", within(MediaServerRowKind.NextUp, null), showsKey)
            }
            val perLibrary = enabled.map { library ->
                async {
                    val type = library.mediaType ?: "movie"
                    listOfNotNull(
                        row(MediaServerRowKind.RecentlyAdded, text.recentlyAdded(library.title), type,
                            within(MediaServerRowKind.RecentlyAdded, library), library.key),
                        row(MediaServerRowKind.Library, library.title, type,
                            within(MediaServerRowKind.Library, library), library.key),
                        if (includeCollections) row(MediaServerRowKind.RecentlyWatched, text.recentlyWatched(library.title), type,
                            within(MediaServerRowKind.RecentlyWatched, library), library.key) else null,
                    )
                }
            }
            val favourites = async {
                if (!includeCollections) null
                else row(MediaServerRowKind.Favourites, text.favourites(), "movie", within(MediaServerRowKind.Favourites, null), null)
            }
            val collections = async {
                if (!includeCollections) null
                else row(MediaServerRowKind.Collections, text.collections(serverName), net.streamdek.mobile.nativeapp.mediaserver.plex.PlexMapping.COLLECTION_TYPE,
                    within(MediaServerRowKind.Collections, null), null)
            }
            listOfNotNull(nextUp.await()) + perLibrary.awaitAll().flatten() + listOfNotNull(favourites.await(), collections.await())
        }
        if (built.isNotEmpty()) problems.remove(serverId)
        else if (problems[serverId] == null) problems[serverId] = "${enabled.size} libraries, no titles came back"
        if (problems[serverId] != before) onStateChanged()
        return built.mapIndexed { index, row ->
            val catalogue = "${row.kind.name.lowercase(Locale.US)}-${row.libraryKey ?: "all"}"
            row.copy(id = mediaServerHomeRowId(JELLYFIN_PROVIDER_ID, serverId, row.mediaType, catalogue, index))
        }
    }

    /** One stretch of a row, in the row's own order, whether for its first showing or for more of it. */
    private suspend fun rowItems(serverId: String, kind: MediaServerRowKind, library: MediaServerLibrary?, start: Int, size: Int, context: JellyfinMappingContext): MediaServerPage {
        val map: (JellyfinItem) -> MediaItem? = { JellyfinMapping.item(it, context, library?.key) }
        val paging = mapOf("startIndex" to start.toString(), "limit" to size.toString())
        return when (kind) {
            MediaServerRowKind.NextUp ->
                pageOf(get(serverId, "/Shows/NextUp", JellyfinItems::class.java, itemQuery(serverId, paging + mapOf("enableResumable" to "false", "disableFirstEpisode" to "false"))), start, size, map)
            MediaServerRowKind.RecentlyAdded -> {
                library ?: return MediaServerPage(emptyList(), start, start)
                // A series is as new as its newest episode: sort shows by when content last arrived.
                val sort = if (library.kind == MediaServerLibraryKind.Shows) mapOf("sortBy" to "DateLastContentAdded,DateCreated", "sortOrder" to "Descending,Descending")
                    else sortParams(MediaServerSort.RecentlyAdded)
                pageOf(query(serverId, libraryParams(library, start, size) + sort), start, size, map)
            }
            MediaServerRowKind.Library -> {
                library ?: return MediaServerPage(emptyList(), start, start)
                val result = query(serverId, libraryParams(library, start, size) + sortParams(MediaServerSort.ReleaseDate))
                pageOf(result, start, size, map).also { page ->
                    val raw = result?.items.orEmpty()
                    if (page.items.isEmpty() && raw.isNotEmpty()) problems[serverId] = "${raw.size} titles came back, none readable (${raw.mapNotNull { it.type }.distinct().joinToString()})"
                }
            }
            MediaServerRowKind.RecentlyWatched -> {
                library ?: return MediaServerPage(emptyList(), start, start)
                val types = if (library.kind == MediaServerLibraryKind.Shows) "Episode" else JellyfinMapping.includeTypes(library.kind)
                pageOf(query(serverId, libraryParams(library, start, size) + mapOf("includeItemTypes" to types, "filters" to "IsPlayed", "sortBy" to "DatePlayed", "sortOrder" to "Descending")), start, size, map)
            }
            MediaServerRowKind.Favourites ->
                pageOf(query(serverId, paging + mapOf("recursive" to "true", "filters" to "IsFavorite", "includeItemTypes" to "Movie,Series,Video", "sortBy" to "SortName", "sortOrder" to "Ascending")), start, size, map)
            MediaServerRowKind.Collections ->
                pageOf(query(serverId, paging + mapOf("recursive" to "true", "includeItemTypes" to "BoxSet", "sortBy" to "SortName", "sortOrder" to "Ascending")), start, size, map)
        }
    }

    override suspend fun rowPage(row: MediaServerRow, start: Int, size: Int): MediaServerPage {
        val context = readyContext(row.serverId) ?: return MediaServerPage(emptyList(), start, start)
        return rowItems(row.serverId, row.kind, libraryOf(row.serverId, row.libraryKey), start, size, context)
    }

    override suspend fun browse(serverId: String, libraryKey: String, start: Int, size: Int, sort: MediaServerSort): MediaServerPage {
        val library = libraryOf(serverId, libraryKey) ?: return MediaServerPage(emptyList(), start, 0)
        val context = readyContext(serverId) ?: return MediaServerPage(emptyList(), start, 0)
        return pageOf(query(serverId, libraryParams(library, start, size) + sortParams(sort)), start, size) { JellyfinMapping.item(it, context, libraryKey) }
    }

    override suspend fun collection(ref: MediaServerReference, start: Int, size: Int): MediaServerPage {
        val context = readyContext(ref.serverId) ?: return MediaServerPage(emptyList(), start, 0)
        val result = query(ref.serverId, mapOf("parentId" to ref.itemKey, "startIndex" to start.toString(), "limit" to size.toString(), "sortBy" to "PremiereDate,SortName", "sortOrder" to "Ascending"))
        return pageOf(result, start, size) { JellyfinMapping.item(it, context) }
    }

    // ── Continue Watching ───────────────────────────────────────────────────────────────────────

    override suspend fun continueWatching(): List<MediaServerResume> = supervisorScope {
        enabledServerIds().map { serverId ->
            async {
                runCatching { serverContinueWatching(serverId) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(emptyList())
            }
        }.awaitAll().flatten().sortedByDescending { it.lastViewedAtMs }
    }

    private suspend fun serverContinueWatching(serverId: String): List<MediaServerResume> {
        val context = readyContext(serverId) ?: return emptyList()
        val entries = get(serverId, "/UserItems/Resume", JellyfinItems::class.java,
            itemQuery(serverId, mapOf("limit" to CONTINUE_SIZE.toString(), "mediaTypes" to "Video", "enableTotalRecordCount" to "false")))?.items.orEmpty()
        return coroutineScope {
            entries.map { entry ->
                async {
                    val series = entry.seriesId?.takeIf { entry.type.equals("Episode", true) }?.let { itemFor(serverId, it) }
                    JellyfinMapping.resume(entry, context, series)
                }
            }.awaitAll().filterNotNull()
        }
    }

    // ── Titles ──────────────────────────────────────────────────────────────────────────────────

    private suspend fun itemFor(serverId: String, itemId: String, force: Boolean = false): JellyfinItem? {
        val key = "$serverId:$itemId"
        if (!force) items[key]?.takeIf { now() - it.atMs < METADATA_TTL_MS }?.let { return it.value }
        val user = userId(serverId) ?: return null
        val item = get(serverId, "/Items/$itemId", JellyfinItem::class.java, mapOf("userId" to user, "fields" to "$FIELDS,People,Taglines,MediaSources,MediaStreams"))
            ?: return items[key]?.value
        items[key] = Timed(now(), item)
        return item
    }

    private suspend fun seasonsOf(serverId: String, seriesId: String, force: Boolean = false): List<JellyfinItem> {
        val key = "$serverId:$seriesId"
        if (!force) seasons[key]?.takeIf { now() - it.atMs < METADATA_TTL_MS }?.let { return it.value }
        val list = get(serverId, "/Shows/$seriesId/Seasons", JellyfinItems::class.java, itemQuery(serverId, emptyMap()))?.items
            ?: return seasons[key]?.value.orEmpty()
        seasons[key] = Timed(now(), list)
        return list
    }

    private suspend fun episodesOf(serverId: String, seriesId: String, seasonId: String?, force: Boolean = false): List<JellyfinItem> {
        val key = "$serverId:$seriesId:${seasonId ?: "all"}"
        if (!force) episodes[key]?.takeIf { now() - it.atMs < METADATA_TTL_MS }?.let { return it.value }
        val list = get(serverId, "/Shows/$seriesId/Episodes", JellyfinItems::class.java,
            itemQuery(serverId, buildMap { seasonId?.let { put("seasonId", it) } }))?.items
            ?: return episodes[key]?.value.orEmpty()
        episodes[key] = Timed(now(), list)
        return list
    }

    override suspend fun detail(ref: MediaServerReference): MediaDetail? {
        // Always fresh: the title page is where resume position and watched state are read.
        val item = itemFor(ref.serverId, ref.itemKey, force = true) ?: return null
        val context = readyContext(ref.serverId) ?: return null
        val seasonList = if (item.type.equals("Series", true)) seasonsOf(ref.serverId, ref.itemKey) else emptyList()
        return JellyfinMapping.detail(item, seasonList, context)
    }

    private suspend fun seasonItem(ref: MediaServerReference, seasonNumber: Int): JellyfinItem? =
        seasonsOf(ref.serverId, ref.itemKey).firstOrNull { it.indexNumber == seasonNumber }

    override suspend fun season(ref: MediaServerReference, seasonNumber: Int): List<EpisodeItem>? {
        val context = readyContext(ref.serverId) ?: return null
        val season = seasonItem(ref, seasonNumber) ?: return null
        return JellyfinMapping.episodes(ref.encode(), episodesOf(ref.serverId, ref.itemKey, season.id), context)
    }

    /** The item that actually plays: the title itself, or the episode within a series. */
    private suspend fun playableId(ref: MediaServerReference, episode: MediaServerEpisode?, force: Boolean = false): String? {
        if (episode == null) return ref.itemKey
        val season = seasonItem(ref, episode.seasonNumber) ?: return null
        return episodesOf(ref.serverId, ref.itemKey, season.id, force).firstOrNull { it.indexNumber == episode.episodeNumber }?.id
    }

    override suspend fun search(query: String, limit: Int): List<MediaItem> {
        val normalized = query.trim().takeIf { it.length >= 2 } ?: return emptyList()
        return supervisorScope {
            enabledServerIds().map { serverId ->
                async {
                    withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                        val context = readyContext(serverId) ?: return@withTimeoutOrNull emptyList()
                        query(serverId, mapOf("searchTerm" to normalized, "recursive" to "true", "includeItemTypes" to "Movie,Series,Video", "limit" to limit.toString()))
                            ?.items.orEmpty()
                            .mapNotNull { JellyfinMapping.item(it, context) }
                            // Jellyfin matches names, but can also answer with titles that only
                            // share a person or a tag with the query; see MediaServerSearchMatch.kt.
                            .filter { titleMatchesSearch(normalized, it.title) }
                    }.orEmpty()
                }
            }.awaitAll().flatten().distinctBy { it.id }
        }
    }

    // ── Playback ────────────────────────────────────────────────────────────────────────────────

    private fun describeQuality(video: JellyfinMediaStream?): String? {
        val width = video?.width ?: 0
        val height = video?.height ?: 0
        return when {
            width >= 3200 -> "4K"
            height >= 1000 || width >= 1900 -> "1080p"
            height >= 700 || width >= 1260 -> "720p"
            height > 0 -> "${height}p"
            else -> null
        }
    }

    private fun describeTechnical(source: JellyfinMediaSource): String {
        val streams = source.mediaStreams.orEmpty()
        val video = streams.firstOrNull { it.type.equals("Video", true) }
        val audio = streams.firstOrNull { it.type.equals("Audio", true) }
        val hdr = video?.videoRangeType?.takeIf { !it.equals("SDR", true) && it.isNotBlank() }
        return listOfNotNull(
            describeQuality(video),
            hdr,
            video?.codec?.uppercase(Locale.US),
            audio?.codec?.uppercase(Locale.US),
            source.container?.uppercase(Locale.US),
        ).joinToString(" · ")
    }

    private fun formatSize(bytes: Long?): String? {
        val value = bytes?.takeIf { it > 0 } ?: return null
        val gb = value / 1_073_741_824.0
        return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%.0f MB", value / 1_048_576.0)
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    override suspend fun streams(ref: MediaServerReference, episode: MediaServerEpisode?, context: MediaServerPlaybackContext): List<AddonStream> {
        val itemId = playableId(ref, episode) ?: return emptyList()
        val endpoint = endpoint(ref.serverId) ?: return emptyList()
        val user = userId(ref.serverId) ?: return emptyList()
        // PlaybackInfo is where Jellyfin describes an item's playable versions and opens a play
        // session for it; the item's own media sources are the fallback on a server that refuses it.
        val info = runCatching {
            client.post(endpoint, "/Items/$itemId/PlaybackInfo", mapOf("UserId" to user, "AutoOpenLiveStream" to false), JellyfinPlaybackInfo::class.java, mapOf("userId" to user))
        }.getOrNull()
        val sources = info?.mediaSources?.takeIf { it.isNotEmpty() }
            ?: itemFor(ref.serverId, itemId, force = true)?.mediaSources.orEmpty()
        if (sources.isEmpty()) return emptyList()
        val playSessionId = info?.playSessionId ?: UUID.randomUUID().toString().replace("-", "")
        val route = route(endpoint.baseUrl)
        val caps = PlexDeviceCaps(PlexDeviceCapabilities.hardwareVideo(), context.engine)
        val headers = mapOf(AUTH_HEADER to client.authorizationFor(endpoint.token))
        val text = labels()
        val attribution = contextFor(ref.serverId)?.attribution ?: label
        val deviceId = context.clientIdentifier
        val multipleVersions = sources.size > 1
        val base = endpoint.baseUrl.trimEnd('/')
        val result = sources.take(MAX_VERSIONS).flatMap { source ->
            val sourceId = source.id ?: return@flatMap emptyList()
            val streams = source.mediaStreams.orEmpty()
            val video = streams.firstOrNull { it.type.equals("Video", true) }
            val audio = streams.firstOrNull { it.type.equals("Audio", true) }
            val plan = PlexPlaybackPlanner.plan(
                facts = PlexMediaFacts(
                    container = source.container,
                    videoCodec = video?.codec,
                    audioCodec = audio?.codec,
                    width = video?.width,
                    height = video?.height,
                    bitrateKbps = source.bitrate?.div(1000),
                ),
                caps = caps,
                route = route,
                remoteMaxKbps = context.remoteMaxBitrateKbps,
            )
            // The server's own Supports* flags describe a generic client, not this device, so the
            // plan is StreamDek's; a mode the server then refuses fails over to the next source.
            val technical = describeTechnical(source)
            val filename = source.path?.substringAfterLast('/')?.substringAfterLast('\\')
            val versionName = source.name?.takeIf { multipleVersions && it.isNotBlank() }
            plan.map { option ->
                val url = when (option.mode) {
                    PlexPlaybackMode.DirectPlay ->
                        "$base/Videos/$itemId/stream?static=true&mediaSourceId=${enc(sourceId)}&deviceId=${enc(deviceId)}&playSessionId=${enc(playSessionId)}"
                    PlexPlaybackMode.DirectStream -> hlsUrl(base, itemId, sourceId, playSessionId, deviceId,
                        videoCodec = listOfNotNull(PlexPlaybackPlanner.normaliseCodec(video?.codec), "h264").distinct().joinToString(","),
                        audioCodec = "aac,ac3,eac3", copyVideo = true, maxKbps = null, maxResolution = null, channels = 6)
                    PlexPlaybackMode.Transcode -> hlsUrl(base, itemId, sourceId, playSessionId, deviceId,
                        videoCodec = if ("hevc" in caps.hardwareVideo) "h264,hevc" else "h264",
                        audioCodec = "aac", copyVideo = false, maxKbps = option.maxBitrateKbps, maxResolution = option.maxResolution, channels = 2)
                }
                val modeLabel = when (option.mode) {
                    PlexPlaybackMode.DirectPlay -> text.directPlay()
                    PlexPlaybackMode.DirectStream -> text.directStream()
                    PlexPlaybackMode.Transcode -> text.transcode(option.maxResolution?.substringAfter('x')?.let { "${it}p" } ?: "")
                }
                AddonStream(
                    addonId = ref.sourceId,
                    addonName = attribution,
                    name = attribution,
                    title = listOf(versionName, modeLabel, technical.takeIf { option.mode != PlexPlaybackMode.Transcode }).filterNot { it.isNullOrBlank() }.joinToString(" · "),
                    description = filename,
                    url = url,
                    infoHash = null,
                    fileIdx = null,
                    filename = filename,
                    quality = if (option.mode == PlexPlaybackMode.Transcode) option.maxResolution?.substringAfter('x')?.let { "${it}p" } else describeQuality(video),
                    size = formatSize(source.size).takeIf { option.mode == PlexPlaybackMode.DirectPlay },
                    cachedBy = emptyList(),
                    source = "$JELLYFIN_PROVIDER_ID:${option.mode.name.lowercase(Locale.US)}",
                    requestHeaders = headers,
                )
            }
        }
        val first = sources.firstOrNull()?.id
        if (first != null) {
            val method = when {
                result.firstOrNull()?.source?.endsWith("directplay") == true -> "DirectPlay"
                result.firstOrNull()?.source?.endsWith("directstream") == true -> "DirectStream"
                else -> "Transcode"
            }
            sessions[sessionKey(ref.serverId, itemId)] = Session(itemId, first, playSessionId, method)
        }
        Log.i("StreamDekJellyfin", "plan server=${ref.serverId} route=$route options=${result.size}")
        return result
    }

    private fun hlsUrl(
        base: String,
        itemId: String,
        sourceId: String,
        playSessionId: String,
        deviceId: String,
        videoCodec: String,
        audioCodec: String,
        copyVideo: Boolean,
        maxKbps: Int?,
        maxResolution: String?,
        channels: Int,
    ): String {
        val params = linkedMapOf(
            "mediaSourceId" to sourceId,
            "playSessionId" to playSessionId,
            "deviceId" to deviceId,
            "videoCodec" to videoCodec,
            "audioCodec" to audioCodec,
            "segmentContainer" to "ts",
            "transcodingMaxAudioChannels" to channels.toString(),
            "allowVideoStreamCopy" to copyVideo.toString(),
            "allowAudioStreamCopy" to "true",
            "breakOnNonKeyFrames" to "true",
            "subtitleMethod" to "External",
        )
        maxKbps?.let { params["videoBitRate"] = (it * 1000L).toString(); params["maxStreamingBitrate"] = (it * 1000L).toString() }
        maxResolution?.split('x')?.takeIf { it.size == 2 }?.let { (w, h) -> params["maxWidth"] = w; params["maxHeight"] = h }
        return "$base/Videos/$itemId/master.m3u8?" + params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    }

    private fun sessionKey(serverId: String, itemId: String) = "$serverId:$itemId"

    private fun progressOf(item: JellyfinItem): MediaServerProgress? {
        val duration = JellyfinMapping.ms(item.runTimeTicks) ?: return null
        val position = JellyfinMapping.ms(item.userData?.playbackPositionTicks) ?: 0L
        return MediaServerProgress(
            positionMs = position,
            durationMs = duration,
            watched = item.userData?.played == true && position == 0L,
            lastViewedAtMs = JellyfinMapping.instantMs(item.userData?.lastPlayedDate),
        )
    }

    override suspend fun progress(ref: MediaServerReference, episode: MediaServerEpisode?): MediaServerProgress? {
        val itemId = playableId(ref, episode, force = true) ?: return null
        return itemFor(ref.serverId, itemId, force = true)?.let(::progressOf)
    }

    override suspend fun seriesProgress(ref: MediaServerReference): List<MediaServerEpisodeProgress> =
        episodesOf(ref.serverId, ref.itemKey, null, force = true).mapNotNull { episode ->
            val season = episode.parentIndexNumber ?: return@mapNotNull null
            val number = episode.indexNumber ?: return@mapNotNull null
            val progress = progressOf(episode) ?: return@mapNotNull null
            if (!progress.watched && progress.positionMs == 0L) return@mapNotNull null
            MediaServerEpisodeProgress(season, number, progress)
        }

    override suspend fun reportProgress(
        ref: MediaServerReference,
        episode: MediaServerEpisode?,
        positionMs: Long,
        durationMs: Long,
        state: MediaServerPlaybackState,
    ) {
        if (positionMs <= 0 || durationMs <= 0) return
        val itemId = playableId(ref, episode) ?: return
        val key = sessionKey(ref.serverId, itemId)
        val session = sessions[key]
        val body = buildMap<String, Any> {
            put("ItemId", itemId)
            session?.let {
                put("MediaSourceId", it.mediaSourceId)
                put("PlaySessionId", it.playSessionId)
                put("PlayMethod", it.method)
            }
            put("PositionTicks", JellyfinMapping.ticks(positionMs))
            put("IsPaused", state == MediaServerPlaybackState.Paused)
            put("CanSeek", true)
        }
        when (state) {
            MediaServerPlaybackState.Stopped -> {
                send(ref.serverId, "POST", "/Sessions/Playing/Stopped", body)
                sessions.remove(key)?.let { ended ->
                    // A transcode keeps running on the server until it is told the viewer has gone.
                    if (ended.method != "DirectPlay") {
                        send(ref.serverId, "DELETE", "/Videos/ActiveEncodings", query = mapOf("deviceId" to client.deviceId(), "playSessionId" to ended.playSessionId))
                    }
                }
                forgetServerCaches(ref.serverId)
            }
            else -> {
                if (session != null && !session.started) {
                    session.started = send(ref.serverId, "POST", "/Sessions/Playing", body)
                } else {
                    send(ref.serverId, "POST", "/Sessions/Playing/Progress", body + mapOf("EventName" to if (state == MediaServerPlaybackState.Paused) "Pause" else "TimeUpdate"))
                }
                rowsCache.clear()
            }
        }
    }

    override suspend fun setWatched(ref: MediaServerReference, episode: MediaServerEpisode?, watched: Boolean): Boolean {
        val itemId = playableId(ref, episode) ?: return false
        val user = userId(ref.serverId) ?: return false
        val ok = send(ref.serverId, if (watched) "POST" else "DELETE", "/UserPlayedItems/$itemId", query = mapOf("userId" to user))
        if (ok) forgetServerCaches(ref.serverId)
        return ok
    }

    override suspend fun setSeasonWatched(ref: MediaServerReference, seasonNumber: Int, watched: Boolean): Boolean {
        val season = seasonItem(ref, seasonNumber)?.id ?: return false
        val user = userId(ref.serverId) ?: return false
        val ok = send(ref.serverId, if (watched) "POST" else "DELETE", "/UserPlayedItems/$season", query = mapOf("userId" to user))
        if (ok) forgetServerCaches(ref.serverId)
        return ok
    }

    /** Jellyfin has no "remove from Continue Watching"; clearing the resume point is what its own apps do. */
    override suspend fun removeFromContinueWatching(ref: MediaServerReference, episode: MediaServerEpisode?): Boolean {
        val itemId = playableId(ref, episode) ?: return false
        val user = userId(ref.serverId) ?: return false
        val ok = send(ref.serverId, "POST", "/UserItems/$itemId/UserData", mapOf("PlaybackPositionTicks" to 0), mapOf("userId" to user))
        if (ok) forgetServerCaches(ref.serverId)
        return ok
    }

    /** Marks or unmarks a title as a favourite on the server. */
    suspend fun setFavourite(ref: MediaServerReference, favourite: Boolean): Boolean {
        val user = userId(ref.serverId) ?: return false
        val ok = send(ref.serverId, if (favourite) "POST" else "DELETE", "/UserFavoriteItems/${ref.itemKey}", query = mapOf("userId" to user))
        if (ok) forgetServerCaches(ref.serverId)
        return ok
    }

    private fun forgetServerCaches(serverId: String) {
        items.keys.removeIf { it.startsWith("$serverId:") }
        seasons.keys.removeIf { it.startsWith("$serverId:") }
        episodes.keys.removeIf { it.startsWith("$serverId:") }
        rowsCache.clear()
    }

    companion object {
        const val AUTH_HEADER = "Authorization"
        private const val FIELDS = "ProviderIds,Overview,Genres,DateCreated,ParentId,ChildCount,RecursiveItemCount"
        private const val ROW_SIZE = 20
        private const val ROW_TIMEOUT_MS = 12_000L
        private const val CONTINUE_SIZE = 24
        private const val MAX_VERSIONS = 3
        private const val SEARCH_TIMEOUT_MS = 6_000L
        private const val LIBRARY_TTL_MS = 10 * 60_000L
        private const val METADATA_TTL_MS = 5 * 60_000L
        private const val ROWS_TTL_MS = 2 * 60_000L
    }
}
