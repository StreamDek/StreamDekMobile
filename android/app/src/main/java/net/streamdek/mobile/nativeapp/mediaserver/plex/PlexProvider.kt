package net.streamdek.mobile.nativeapp.mediaserver.plex

import android.util.Log
import net.streamdek.mobile.nativeapp.AddonStream
import net.streamdek.mobile.nativeapp.EpisodeItem
import net.streamdek.mobile.nativeapp.MediaDetail
import net.streamdek.mobile.nativeapp.MediaItem
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerEpisode
import net.streamdek.mobile.nativeapp.mediaserver.DiscoveredMediaServer
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerAuth
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerEndpoint
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerEpisodeProgress
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerProgress
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLabels
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLibrary
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLibraryKind
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerPage
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerPlaybackContext
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerPlaybackState
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerProvider
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReachability
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerResume
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRoute
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRow
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRowKind
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerSort
import net.streamdek.mobile.nativeapp.mediaserver.OfflineReason
import net.streamdek.mobile.nativeapp.mediaserver.PLEX_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.mediaServerHomeRowId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Plex, as a StreamDek media server provider.
 *
 * Talks to each server directly from the device. What it keeps:
 *
 *  - for each server, the address that answered and whether it is local, remote or relayed;
 *  - libraries, metadata and season listings for a few minutes, so moving between a title and its
 *    seasons, or back to Home, does not ask the server again;
 *  - for a server that did not answer, when to try again - thirty seconds, doubling to ten
 *    minutes - so a NAS that is asleep costs one short timeout, not one per screen.
 *
 * Nothing here throws to a caller for an unreachable server. Every read answers with what the
 * reachable servers had, which for a profile whose only server is away is simply nothing.
 */
internal class PlexProvider(
    private val client: PlexClient,
    private val labels: () -> MediaServerLabels,
    private val onStateChanged: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) : MediaServerProvider {
    override val id: String = PLEX_PROVIDER_ID
    override val label: String = "Plex"

    private data class ServerState(
        val server: DiscoveredMediaServer,
        val endpoint: MediaServerEndpoint? = null,
        val reachability: MediaServerReachability = MediaServerReachability.Unknown,
        val retryAtMs: Long = 0L,
        val failures: Int = 0,
    )

    private data class Timed<T>(val atMs: Long, val value: T)

    private val servers = ConcurrentHashMap<String, ServerState>()
    private val connectLocks = ConcurrentHashMap<String, Mutex>()
    private val libraries = ConcurrentHashMap<String, Timed<List<Pair<PlexDirectory, MediaServerLibrary>>>>()
    private val metadata = ConcurrentHashMap<String, Timed<PlexMetadata>>()
    private val children = ConcurrentHashMap<String, Timed<List<PlexMetadata>>>()
    private val rowsCache = ConcurrentHashMap<Boolean, Timed<List<MediaServerRow>>>()
    /** The transcoder session each ratingKey last played under, so the timeline can name it. */
    private val sessions = ConcurrentHashMap<String, String>()
    /** Titles already marked watched this session, so the 90% mark is sent once. */
    private val scrobbled = ConcurrentHashMap.newKeySet<String>()

    /** Called with the address that answered, so the manager can remember it between launches. */
    var onEndpointChosen: (serverId: String, uri: String) -> Unit = { _, _ -> }

    override fun setServers(servers: List<DiscoveredMediaServer>) {
        val incoming = servers.associateBy { it.id }
        this.servers.keys.retainAll(incoming.keys)
        incoming.forEach { (id, server) ->
            val previous = this.servers[id]
            // A server whose addresses or token changed is looked for afresh.
            val sameRoutes = previous != null && previous.server.accessToken == server.accessToken &&
                previous.server.connections.map { it.uri } == server.connections.map { it.uri }
            this.servers[id] = if (sameRoutes) previous!!.copy(server = server) else ServerState(server)
            server.connections.forEach { MediaServerAuth.register(it.uri, it.accessToken ?: server.accessToken) }
        }
        rowsCache.clear()
        onStateChanged()
    }

    override fun reachability(serverId: String): MediaServerReachability =
        servers[serverId]?.reachability ?: MediaServerReachability.Unknown

    override fun reset() {
        servers.clear()
        libraries.clear()
        metadata.clear()
        children.clear()
        rowsCache.clear()
        sessions.clear()
        scrobbled.clear()
        onStateChanged()
    }

    // ── Connections ─────────────────────────────────────────────────────────────────────────────

    private fun update(serverId: String, change: (ServerState) -> ServerState) {
        servers.computeIfPresent(serverId) { _, state -> change(state) }
        onStateChanged()
    }

    /**
     * Probes a server's addresses concurrently and takes the best one that answers.
     *
     * All are started at once, then read in order of preference: a local address that answers
     * wins at once even if a remote one answered first, and a dead local address costs at most
     * its own short timeout, not one per address.
     */
    private suspend fun connectServer(serverId: String, force: Boolean): MediaServerEndpoint? {
        val lock = connectLocks.getOrPut(serverId) { Mutex() }
        return lock.withLock {
            val state = servers[serverId] ?: return@withLock null
            if (!state.server.enabled) return@withLock null
            val current = state.reachability
            if (!force && current is MediaServerReachability.Online && state.endpoint != null) return@withLock state.endpoint
            if (!force && current is MediaServerReachability.Offline && now() < state.retryAtMs) return@withLock null

            val candidates = PlexConnectionRanking.order(
                state.server.connections.map { it.copy(accessToken = it.accessToken ?: state.server.accessToken) },
                state.endpoint?.uri,
            )
            if (candidates.isEmpty()) {
                update(serverId) { it.copy(reachability = MediaServerReachability.Offline(OfflineReason.NoConnections)) }
                return@withLock null
            }
            update(serverId) { it.copy(reachability = MediaServerReachability.Connecting) }
            var sawUnauthorized = false
            val chosen = coroutineScope {
                val probes = candidates.map { candidate -> candidate to async { client.probe(candidate, serverId) } }
                var winner: MediaServerEndpoint? = null
                for ((candidate, probe) in probes) {
                    when (probe.await()) {
                        PlexClient.ProbeResult.Ok -> { winner = candidate; break }
                        PlexClient.ProbeResult.Unauthorized -> sawUnauthorized = true
                        PlexClient.ProbeResult.Unreachable -> Unit
                    }
                }
                probes.forEach { (_, probe) -> probe.cancel() }
                winner
            }
            if (chosen != null) {
                update(serverId) {
                    it.copy(
                        endpoint = chosen,
                        reachability = MediaServerReachability.Online(PlexConnectionRanking.route(chosen)),
                        failures = 0,
                        retryAtMs = 0L,
                    )
                }
                onEndpointChosen(serverId, chosen.uri)
                Log.i("StreamDekPlex", "server=$serverId route=${PlexConnectionRanking.route(chosen)}")
            } else {
                update(serverId) {
                    val failures = it.failures + 1
                    it.copy(
                        endpoint = null,
                        reachability = MediaServerReachability.Offline(if (sawUnauthorized) OfflineReason.Unauthorized else OfflineReason.Unreachable),
                        failures = failures,
                        retryAtMs = now() + backoffMs(failures),
                    )
                }
                Log.w("StreamDekPlex", "server=$serverId unreachable on ${candidates.size} addresses")
            }
            chosen
        }
    }

    private fun backoffMs(failures: Int): Long = (30_000L shl (failures - 1).coerceIn(0, 5)).coerceAtMost(600_000L)

    override suspend fun connect(force: Boolean) {
        supervisorScope {
            servers.values.filter { it.server.enabled }.map { state -> async { connectServer(state.server.id, force) } }.awaitAll()
        }
    }

    /** The endpoint for a server, connecting first if need be; null when it cannot be reached. */
    private suspend fun endpoint(serverId: String): MediaServerEndpoint? {
        val state = servers[serverId] ?: return null
        if (state.reachability is MediaServerReachability.Online && state.endpoint != null) return state.endpoint
        return connectServer(serverId, force = false)
    }

    /** A request's failure is the server's failure: next time, look for it again. */
    private fun markSuspect(serverId: String) {
        update(serverId) { it.copy(reachability = MediaServerReachability.Unknown) }
    }

    private suspend fun get(
        serverId: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        start: Int? = null,
        size: Int? = null,
    ): PlexContainer? {
        val endpoint = endpoint(serverId) ?: return null
        return try {
            client.get(endpoint, path, query, start, size).also { if (it == null) markSuspect(serverId) }
        } catch (unauthorized: PlexClient.UnauthorizedException) {
            update(serverId) { it.copy(reachability = MediaServerReachability.Offline(OfflineReason.Unauthorized), retryAtMs = now() + backoffMs(3)) }
            null
        }
    }

    private fun contextFor(serverId: String): PlexMappingContext? {
        val state = servers[serverId] ?: return null
        val base = state.endpoint?.uri ?: return null
        val multiple = servers.values.count { it.server.enabled } > 1
        return PlexMappingContext(
            serverId = serverId,
            baseUri = base,
            attribution = labels().attribution(label, state.server.name, multiple),
            libraryTitles = libraries[serverId]?.value.orEmpty().associate { (_, library) -> library.key to library.title },
            seasonName = labels()::season,
            episodeName = labels()::episode,
        )
    }

    private fun enabledServerIds(): List<String> = servers.values.filter { it.server.enabled }.map { it.server.id }

    // ── Libraries ───────────────────────────────────────────────────────────────────────────────

    private fun classify(directory: PlexDirectory): MediaServerLibraryKind? = when (directory.type?.lowercase(Locale.US)) {
        "movie" -> if (directory.agent.orEmpty().contains("none", ignoreCase = true)) MediaServerLibraryKind.Other else MediaServerLibraryKind.Movies
        "show" -> MediaServerLibraryKind.Shows
        // Music and photos are not something StreamDek plays.
        else -> null
    }

    private suspend fun libraryPairs(serverId: String, force: Boolean): List<Pair<PlexDirectory, MediaServerLibrary>> {
        val cached = libraries[serverId]
        if (!force && cached != null && now() - cached.atMs < LIBRARY_TTL_MS) return cached.value
        val choices = servers[serverId]?.server?.libraryChoices.orEmpty()
        val container = get(serverId, "/library/sections") ?: return cached?.value.orEmpty()
        val pairs = container.directories.orEmpty().mapNotNull { directory ->
            val key = directory.key ?: return@mapNotNull null
            val kind = classify(directory) ?: return@mapNotNull null
            if ((directory.hidden ?: 0) != 0) return@mapNotNull null
            directory to MediaServerLibrary(
                serverId = serverId,
                key = key,
                title = directory.title?.takeIf { it.isNotBlank() } ?: key,
                kind = kind,
                // Films and series are on until switched off; personal video is off until switched on.
                enabled = choices[key] ?: (kind != MediaServerLibraryKind.Other),
            )
        }
        libraries[serverId] = Timed(now(), pairs)
        return pairs
    }

    override suspend fun libraries(serverId: String, force: Boolean): List<MediaServerLibrary> =
        libraryPairs(serverId, force).map { it.second }

    /** Seeds libraries from what the device remembered, so the Plex tab exists before any network. */
    fun seedLibraries(serverId: String, known: List<MediaServerLibrary>) {
        if (libraries.containsKey(serverId) || known.isEmpty()) return
        // Stamped as already stale: seeded values show at once and are replaced on first read.
        libraries[serverId] = Timed(0L, known.map { PlexDirectory(key = it.key, title = it.title, type = if (it.kind == MediaServerLibraryKind.Shows) "show" else "movie") to it })
    }

    fun cachedLibraries(serverId: String): List<MediaServerLibrary> = libraries[serverId]?.value.orEmpty().map { it.second }

    private suspend fun enabledLibraries(serverId: String): List<Pair<PlexDirectory, MediaServerLibrary>> =
        libraryPairs(serverId, false).filter { it.second.enabled }

    private fun typeParam(directory: PlexDirectory): String? = when (directory.type?.lowercase(Locale.US)) {
        "movie" -> "1"
        "show" -> "2"
        else -> null
    }

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
        rowsCache[includeCollections] = Timed(now(), rows)
        return rows
    }

    private suspend fun serverRows(serverId: String, includeCollections: Boolean, text: MediaServerLabels): List<MediaServerRow> {
        val enabled = enabledLibraries(serverId)
        val context = contextFor(serverId) ?: return emptyList()
        val serverName = servers[serverId]?.server?.name.orEmpty()
        return coroutineScope {
            enabled.map { (directory, library) ->
                async {
                    val type = library.mediaType ?: "movie"
                    val section = directory.key ?: return@async emptyList<MediaServerRow>()
                    buildList {
                        recentlyAdded(serverId, directory, context).takeIf { it.isNotEmpty() }?.let { items ->
                            add(MediaServerRow("", text.recentlyAdded(library.title), serverId, serverName, MediaServerRowKind.RecentlyAdded, type, items, section))
                        }
                        if (library.kind != MediaServerLibraryKind.Other) {
                            browse(serverId, section, 0, ROW_SIZE, MediaServerSort.ReleaseDate).items.takeIf { it.isNotEmpty() }?.let { items ->
                                add(MediaServerRow("", library.title, serverId, serverName, MediaServerRowKind.Library, type, items, section))
                            }
                        }
                        if (includeCollections) {
                            recentlyWatched(serverId, directory, context).takeIf { it.isNotEmpty() }?.let { items ->
                                add(MediaServerRow("", text.recentlyWatched(library.title), serverId, serverName, MediaServerRowKind.RecentlyWatched, type, items, section))
                            }
                            collections(serverId, section, context).takeIf { it.isNotEmpty() }?.let { items ->
                                add(MediaServerRow("", text.collections(library.title), serverId, serverName, MediaServerRowKind.Collections, PlexMapping.COLLECTION_TYPE, items, section))
                            }
                        }
                    }
                }
            }.awaitAll().flatten()
        }.mapIndexed { index, row ->
            // Ids from the row's own identity, so reordering one library never renames another's rows.
            val catalogue = "${row.kind.name.lowercase(Locale.US)}-${row.libraryKey}"
            row.copy(id = mediaServerHomeRowId(PLEX_PROVIDER_ID, serverId, row.mediaType, catalogue, index))
        }
    }

    private suspend fun recentlyAdded(serverId: String, directory: PlexDirectory, context: PlexMappingContext): List<MediaItem> {
        val section = directory.key ?: return emptyList()
        return if (directory.type.equals("show", true)) {
            // Episodes and seasons, gathered up into the series they belong to.
            get(serverId, "/library/sections/$section/recentlyAdded", start = 0, size = ROW_SIZE * 2)
                ?.allMetadata().orEmpty()
                .mapNotNull { PlexMapping.item(it, context, section) }
                .distinctBy { it.id }
                .take(ROW_SIZE)
        } else {
            val query = buildMap {
                put("sort", "addedAt:desc")
                put("includeGuids", "1")
                typeParam(directory)?.let { put("type", it) }
            }
            get(serverId, "/library/sections/$section/all", query, 0, ROW_SIZE)
                ?.allMetadata().orEmpty().mapNotNull { PlexMapping.item(it, context, section) }
        }
    }

    private suspend fun recentlyWatched(serverId: String, directory: PlexDirectory, context: PlexMappingContext): List<MediaItem> {
        val section = directory.key ?: return emptyList()
        val query = buildMap {
            put("sort", "lastViewedAt:desc")
            put("includeGuids", "1")
            typeParam(directory)?.let { put("type", it) }
        }
        return get(serverId, "/library/sections/$section/all", query, 0, ROW_SIZE * 2)
            ?.allMetadata().orEmpty()
            .filter { (it.viewCount ?: 0) > 0 || (it.viewedLeafCount ?: 0) > 0 }
            .mapNotNull { PlexMapping.item(it, context, section) }
            .take(ROW_SIZE)
    }

    private suspend fun collections(serverId: String, section: String, context: PlexMappingContext): List<MediaItem> =
        get(serverId, "/library/sections/$section/collections", start = 0, size = ROW_SIZE)
            ?.allMetadata().orEmpty().mapNotNull { PlexMapping.item(it, context, section) }

    override suspend fun browse(serverId: String, libraryKey: String, start: Int, size: Int, sort: MediaServerSort): MediaServerPage {
        val directory = libraryPairs(serverId, false).firstOrNull { it.first.key == libraryKey }?.first
            ?: return MediaServerPage(emptyList(), start, 0)
        val context = contextFor(serverId) ?: return MediaServerPage(emptyList(), start, 0)
        val query = buildMap {
            put("sort", when (sort) {
                MediaServerSort.RecentlyAdded -> "addedAt:desc"
                MediaServerSort.Title -> "titleSort:asc"
                MediaServerSort.ReleaseDate -> "originallyAvailableAt:desc"
            })
            put("includeGuids", "1")
            typeParam(directory)?.let { put("type", it) }
        }
        val container = get(serverId, "/library/sections/$libraryKey/all", query, start, size)
            ?: return MediaServerPage(emptyList(), start, 0)
        val items = container.allMetadata().mapNotNull { PlexMapping.item(it, context, libraryKey) }
        return MediaServerPage(items, start, container.totalSize ?: (start + items.size))
    }

    override suspend fun collection(ref: MediaServerReference, start: Int, size: Int): MediaServerPage {
        val context = contextFor(ref.serverId) ?: return MediaServerPage(emptyList(), start, 0)
        val container = get(ref.serverId, "/library/collections/${ref.itemKey}/children", start = start, size = size)
            ?: return MediaServerPage(emptyList(), start, 0)
        val items = container.allMetadata().mapNotNull { PlexMapping.item(it, context) }
        return MediaServerPage(items, start, container.totalSize ?: (start + items.size))
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
        val enabledSections = enabledLibraries(serverId).mapNotNull { it.first.key }.toSet()
        // The Continue Watching hub on current servers; On Deck on ones that predate it.
        val entries = (get(serverId, "/hubs/home/continueWatching", start = 0, size = CONTINUE_SIZE)
            ?.allMetadata()?.takeIf { it.isNotEmpty() }
            ?: get(serverId, "/library/onDeck", start = 0, size = CONTINUE_SIZE)?.allMetadata().orEmpty())
            .filter { it.librarySectionID == null || it.librarySectionID in enabledSections }
            .take(CONTINUE_SIZE)
        val context = contextFor(serverId) ?: return emptyList()
        return coroutineScope {
            entries.map { entry ->
                async {
                    // An episode's series ids are what match it against StreamDek's own row.
                    val series = entry.grandparentRatingKey?.takeIf { entry.type.equals("episode", true) }
                        ?.let { metadataFor(serverId, it) }
                    PlexMapping.resume(entry, context, series)
                }
            }.awaitAll().filterNotNull()
        }
    }

    // ── Titles ──────────────────────────────────────────────────────────────────────────────────

    private suspend fun metadataFor(serverId: String, ratingKey: String, force: Boolean = false): PlexMetadata? {
        val cacheKey = "$serverId:$ratingKey"
        if (!force) metadata[cacheKey]?.takeIf { now() - it.atMs < METADATA_TTL_MS }?.let { return it.value }
        val meta = get(serverId, "/library/metadata/$ratingKey", mapOf("includeGuids" to "1"))?.metadata?.firstOrNull()
            ?: return metadata[cacheKey]?.value
        metadata[cacheKey] = Timed(now(), meta)
        return meta
    }

    private suspend fun childrenOf(serverId: String, ratingKey: String, force: Boolean = false): List<PlexMetadata> {
        val cacheKey = "$serverId:$ratingKey"
        if (!force) children[cacheKey]?.takeIf { now() - it.atMs < METADATA_TTL_MS }?.let { return it.value }
        // Paged, as the API requires: a long-running series can have a season of several hundred.
        val collected = ArrayList<PlexMetadata>()
        var start = 0
        while (true) {
            val page = get(serverId, "/library/metadata/$ratingKey/children", start = start, size = CHILDREN_PAGE)
                ?: return children[cacheKey]?.value ?: collected
            val items = page.allMetadata()
            collected += items
            val total = page.totalSize ?: page.size ?: collected.size
            start += items.size
            if (items.isEmpty() || start >= total || start >= CHILDREN_MAX) break
        }
        children[cacheKey] = Timed(now(), collected)
        return collected
    }

    override suspend fun detail(ref: MediaServerReference): MediaDetail? {
        // Always fresh from the server: the title page is where resume position and watched state
        // are read, and a cached copy is how a newer position from another Plex app gets missed.
        val meta = metadataFor(ref.serverId, ref.itemKey, force = true) ?: return null
        val context = contextFor(ref.serverId) ?: return null
        val seasons = if (meta.type.equals("show", true)) childrenOf(ref.serverId, ref.itemKey) else emptyList()
        return PlexMapping.detail(meta, seasons, context)
    }

    private suspend fun seasonMeta(ref: MediaServerReference, seasonNumber: Int): PlexMetadata? =
        childrenOf(ref.serverId, ref.itemKey).firstOrNull { it.type.equals("season", true) && it.index == seasonNumber }

    private suspend fun episodesOf(ref: MediaServerReference, seasonNumber: Int, force: Boolean = false): List<PlexMetadata> {
        val season = seasonMeta(ref, seasonNumber)?.ratingKey ?: return emptyList()
        return childrenOf(ref.serverId, season, force).filter { it.type.equals("episode", true) }
    }

    override suspend fun season(ref: MediaServerReference, seasonNumber: Int): List<EpisodeItem>? {
        val context = contextFor(ref.serverId) ?: return null
        if (seasonMeta(ref, seasonNumber) == null) return null
        return PlexMapping.episodes(ref.encode(), episodesOf(ref, seasonNumber), context)
    }

    /** The ratingKey that actually plays: the title's own, or the episode's within a series. */
    private suspend fun playableKey(ref: MediaServerReference, episode: MediaServerEpisode?): String? {
        if (episode == null) return ref.itemKey
        return episodesOf(ref, episode.seasonNumber).firstOrNull { it.index == episode.episodeNumber }?.ratingKey
    }

    override suspend fun search(query: String, limit: Int): List<MediaItem> {
        val normalized = query.trim().takeIf { it.length >= 2 } ?: return emptyList()
        return supervisorScope {
            enabledServerIds().map { serverId ->
                async {
                    withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                        val sections = enabledLibraries(serverId).mapNotNull { it.first.key }.toSet()
                        val context = contextFor(serverId) ?: return@withTimeoutOrNull emptyList()
                        get(serverId, "/hubs/search", mapOf("query" to normalized, "limit" to limit.toString(), "includeCollections" to "0", "includeGuids" to "1"))
                            ?.allMetadata().orEmpty()
                            .filter { it.librarySectionID == null || it.librarySectionID in sections }
                            .filter { PlexMapping.mediaType(it).let { type -> type == "movie" || type == "tv" } }
                            .mapNotNull { PlexMapping.item(it, context) }
                    }.orEmpty()
                }
            }.awaitAll().flatten().distinctBy { it.id }
        }
    }

    // ── Playback ────────────────────────────────────────────────────────────────────────────────

    private fun describeQuality(media: PlexMedia): String? = when {
        (media.width ?: 0) >= 3200 || media.videoResolution.equals("4k", true) -> "4K"
        (media.height ?: 0) >= 1000 || media.videoResolution == "1080" -> "1080p"
        (media.height ?: 0) >= 700 || media.videoResolution == "720" -> "720p"
        (media.height ?: 0) > 0 -> "${media.height}p"
        else -> null
    }

    private fun describeTechnical(media: PlexMedia): String = listOfNotNull(
        describeQuality(media),
        media.videoCodec?.uppercase(Locale.US),
        media.audioCodec?.uppercase(Locale.US)?.let { codec -> media.audioChannels?.let { "$codec ${channelLayout(it)}" } ?: codec },
    ).joinToString(" · ")

    private fun channelLayout(channels: Int): String = when (channels) {
        1 -> "1.0"
        2 -> "2.0"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }

    private fun formatSize(bytes: Long?): String? {
        val value = bytes?.takeIf { it > 0 } ?: return null
        val gb = value / 1_073_741_824.0
        return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%.0f MB", value / 1_048_576.0)
    }

    override suspend fun streams(ref: MediaServerReference, episode: MediaServerEpisode?, context: MediaServerPlaybackContext): List<AddonStream> {
        val ratingKey = playableKey(ref, episode) ?: return emptyList()
        val endpoint = endpoint(ref.serverId) ?: return emptyList()
        val meta = metadataFor(ref.serverId, ratingKey, force = true) ?: return emptyList()
        val media = meta.media.orEmpty().firstOrNull() ?: return emptyList()
        val part = media.parts.orEmpty().firstOrNull() ?: return emptyList()
        val route = PlexConnectionRanking.route(endpoint)
        val caps = PlexDeviceCaps(PlexDeviceCapabilities.hardwareVideo(), context.engine)
        val plan = PlexPlaybackPlanner.plan(
            facts = PlexMediaFacts(
                container = part.container ?: media.container,
                videoCodec = media.videoCodec,
                audioCodec = media.audioCodec,
                width = media.width,
                height = media.height,
                bitrateKbps = media.bitrate,
            ),
            caps = caps,
            route = route,
            remoteMaxKbps = context.remoteMaxBitrateKbps,
        )
        val identity = PlexClientIdentity(
            clientIdentifier = context.clientIdentifier,
            product = PLEX_PRODUCT,
            version = context.appVersion,
            platform = "Android",
            deviceName = context.deviceName,
        )
        val sessionId = UUID.randomUUID().toString()
        sessions[ratingKey] = sessionId
        scrobbled.remove(ratingKey)
        val headers = identity.headers() + mapOf("X-Plex-Session-Identifier" to sessionId) +
            (endpoint.accessToken?.let { mapOf(MediaServerAuth.TOKEN_HEADER to it) } ?: emptyMap())
        val text = labels()
        val technical = describeTechnical(media)
        val filename = part.file?.substringAfterLast('/')?.substringAfterLast('\\')
        val attribution = contextFor(ref.serverId)?.attribution ?: label
        val profileExtra = PlexPlaybackPlanner.profileExtra(caps)
        Log.i("StreamDekPlex", "plan server=${ref.serverId} route=$route modes=${plan.joinToString(",") { it.mode.name }}")
        return plan.mapNotNull { option ->
            val url = when (option.mode) {
                PlexPlaybackMode.DirectPlay -> part.key?.let { PlexPlaybackUrls.directPlay(endpoint.uri, it) } ?: return@mapNotNull null
                else -> PlexPlaybackUrls.universal(endpoint.uri, ratingKey, option, sessionId, identity, route, profileExtra)
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
                title = listOf(modeLabel, technical.takeIf { option.mode != PlexPlaybackMode.Transcode }).filterNot { it.isNullOrBlank() }.joinToString(" · "),
                description = filename,
                url = url,
                infoHash = null,
                fileIdx = null,
                filename = filename,
                quality = if (option.mode == PlexPlaybackMode.Transcode) option.maxResolution?.substringAfter('x')?.let { "${it}p" } else describeQuality(media),
                size = formatSize(part.size).takeIf { option.mode == PlexPlaybackMode.DirectPlay },
                cachedBy = emptyList(),
                source = "$PLEX_PROVIDER_ID:${option.mode.name.lowercase(Locale.US)}",
                requestHeaders = headers,
            )
        }
    }

    private fun progressOf(meta: PlexMetadata): MediaServerProgress? {
        val duration = meta.duration?.takeIf { it > 0 } ?: return null
        val offset = meta.viewOffset?.takeIf { it > 0 } ?: 0L
        return MediaServerProgress(
            positionMs = offset,
            durationMs = duration,
            // A title part-way through a rewatch is not "watched" for resuming purposes.
            watched = (meta.viewCount ?: 0) > 0 && offset == 0L,
            lastViewedAtMs = (meta.lastViewedAt ?: 0L) * 1000L,
        )
    }

    override suspend fun progress(ref: MediaServerReference, episode: MediaServerEpisode?): MediaServerProgress? {
        val ratingKey = playableKey(ref, episode) ?: return null
        return metadataFor(ref.serverId, ratingKey, force = true)?.let(::progressOf)
    }

    override suspend fun seriesProgress(ref: MediaServerReference): List<MediaServerEpisodeProgress> {
        // allLeaves is every episode of the series in one listing, which is what makes a series
        // page's resume target one request rather than one per season.
        val collected = ArrayList<PlexMetadata>()
        var start = 0
        while (start < CHILDREN_MAX) {
            val page = get(ref.serverId, "/library/metadata/${ref.itemKey}/allLeaves", start = start, size = CHILDREN_PAGE) ?: break
            val items = page.allMetadata()
            collected += items
            start += items.size
            if (items.isEmpty() || start >= (page.totalSize ?: page.size ?: start)) break
        }
        return collected.mapNotNull { episode ->
            val season = episode.parentIndex ?: return@mapNotNull null
            val number = episode.index ?: return@mapNotNull null
            val progress = progressOf(episode) ?: return@mapNotNull null
            if (!progress.watched && progress.positionMs == 0L) return@mapNotNull null
            MediaServerEpisodeProgress(season, number, progress)
        }
    }

    override suspend fun reportProgress(
        ref: MediaServerReference,
        episode: MediaServerEpisode?,
        positionMs: Long,
        durationMs: Long,
        state: MediaServerPlaybackState,
    ) {
        if (positionMs <= 0 || durationMs <= 0) return
        val ratingKey = playableKey(ref, episode) ?: return
        val endpoint = endpoint(ref.serverId) ?: return
        val stateName = when (state) {
            MediaServerPlaybackState.Playing -> "playing"
            MediaServerPlaybackState.Paused -> "paused"
            MediaServerPlaybackState.Stopped -> "stopped"
        }
        val sessionHeaders = sessions[ratingKey]?.let { mapOf("X-Plex-Session-Identifier" to it) } ?: emptyMap()
        client.send(
            endpoint,
            "/:/timeline",
            mapOf(
                "ratingKey" to ratingKey,
                "key" to "/library/metadata/$ratingKey",
                "state" to stateName,
                "time" to positionMs.toString(),
                "duration" to durationMs.toString(),
                "identifier" to LIBRARY_IDENTIFIER,
            ),
            sessionHeaders,
        )
        // Past the point Plex itself calls finished, say so once, so the next episode is up next on
        // every Plex app and not only once this player reports that it stopped.
        if (positionMs >= durationMs * WATCHED_FRACTION && scrobbled.add(ratingKey)) {
            client.send(endpoint, "/:/scrobble", mapOf("identifier" to LIBRARY_IDENTIFIER, "key" to ratingKey))
            metadata.remove("${ref.serverId}:$ratingKey")
        }
        if (state == MediaServerPlaybackState.Stopped) {
            sessions.remove(ratingKey)?.let { session ->
                client.send(endpoint, "/video/:/transcode/universal/stop", mapOf("session" to session))
            }
        }
        rowsCache.clear()
    }

    override suspend fun setWatched(ref: MediaServerReference, episode: MediaServerEpisode?, watched: Boolean): Boolean {
        val ratingKey = playableKey(ref, episode) ?: return false
        val endpoint = endpoint(ref.serverId) ?: return false
        val ok = client.send(endpoint, if (watched) "/:/scrobble" else "/:/unscrobble", mapOf("identifier" to LIBRARY_IDENTIFIER, "key" to ratingKey))
        if (ok) forgetServerCaches(ref.serverId)
        return ok
    }

    override suspend fun setSeasonWatched(ref: MediaServerReference, seasonNumber: Int, watched: Boolean): Boolean {
        val season = seasonMeta(ref, seasonNumber)?.ratingKey ?: return false
        val endpoint = endpoint(ref.serverId) ?: return false
        val ok = client.send(endpoint, if (watched) "/:/scrobble" else "/:/unscrobble", mapOf("identifier" to LIBRARY_IDENTIFIER, "key" to season))
        if (ok) forgetServerCaches(ref.serverId)
        return ok
    }

    override suspend fun removeFromContinueWatching(ref: MediaServerReference, episode: MediaServerEpisode?): Boolean {
        val ratingKey = playableKey(ref, episode) ?: return false
        val endpoint = endpoint(ref.serverId) ?: return false
        // Plex's own "Remove from Continue Watching". Servers older than the action ignore it, and
        // the card then returns on the next read - which is what Plex's own apps do there too.
        val ok = client.send(endpoint, "/actions/removeFromContinueWatching", mapOf("ratingKey" to ratingKey), method = "PUT")
        if (ok) forgetServerCaches(ref.serverId)
        return ok
    }

    private fun forgetServerCaches(serverId: String) {
        metadata.keys.removeIf { it.startsWith("$serverId:") }
        children.keys.removeIf { it.startsWith("$serverId:") }
        rowsCache.clear()
    }

    companion object {
        const val PLEX_PRODUCT = "StreamDek"
        private const val LIBRARY_IDENTIFIER = "com.plexapp.plugins.library"
        private const val WATCHED_FRACTION = 0.9
        private const val ROW_SIZE = 20
        private const val CONTINUE_SIZE = 24
        private const val CHILDREN_PAGE = 200
        private const val CHILDREN_MAX = 2_000
        private const val SEARCH_TIMEOUT_MS = 6_000L
        private const val LIBRARY_TTL_MS = 10 * 60_000L
        private const val METADATA_TTL_MS = 5 * 60_000L
        private const val ROWS_TTL_MS = 2 * 60_000L
    }
}
