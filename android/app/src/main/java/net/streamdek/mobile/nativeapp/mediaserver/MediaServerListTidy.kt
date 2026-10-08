package net.streamdek.mobile.nativeapp.mediaserver

/*
 * Tidying the server list in Settings: taking one library or one server off the list without
 * disconnecting everything, and folding a server's libraries away.
 *
 * "Removed" is two things together. The library or server is switched off - that part is the
 * profile's, saved the way every other switch is, so it stops appearing anywhere in StreamDek on
 * every device. And this device stops listing it in Settings, which is what makes it removed
 * rather than merely off. It is kept under "Removed" so it can be brought back; nothing is deleted
 * from the server itself, which StreamDek has no business doing.
 *
 * A Jellyfin server is the exception: it has its own sign-in, so removing one really does sign out
 * of that server alone.
 */

/**
 * The name a removed or folded entry is remembered under. A null [libraryKey] means the server
 * itself. Each part is encoded, so an id containing the separator cannot be mistaken for two parts
 * and nothing unprintable reaches the preferences file.
 */
internal fun mediaServerEntryKey(provider: String, serverId: String, libraryKey: String? = null): String =
    listOfNotNull(provider, serverId, libraryKey).joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }

/**
 * Whether an entry belongs in the list. Something removed here but switched back on from another
 * device is on, and so is shown: the list never hides a thing that is in use.
 */
internal fun mediaServerEntryListed(removed: Set<String>, key: String, enabled: Boolean): Boolean =
    enabled || key !in removed

/** One thing under "Removed", ready to be named and brought back. */
internal data class RemovedMediaServerEntry(
    val key: String,
    val serverId: String,
    val serverName: String,
    /** Null when the whole server was removed. */
    val library: MediaServerLibrary?,
)

/** The servers still on the list, each with only the libraries still on it. */
internal fun listedMediaServers(provider: String, servers: List<MediaServerView>, removed: Set<String>): List<MediaServerView> =
    servers
        .filter { mediaServerEntryListed(removed, mediaServerEntryKey(provider, it.id), it.enabled) }
        .map { server ->
            server.copy(
                libraries = server.libraries.filter {
                    mediaServerEntryListed(removed, mediaServerEntryKey(provider, server.id, it.key), it.enabled)
                },
            )
        }

/**
 * [servers] in the viewer's chosen order. [order] holds entry keys - see [mediaServerEntryKey] -
 * most preferred first; a server it does not mention keeps its place after those it does, in the
 * order the servers arrived, so a newly added server simply joins the end.
 */
internal fun List<MediaServerView>.inServerOrder(provider: String, order: List<String>): List<MediaServerView> {
    if (order.isEmpty() || size < 2) return this
    val rank = order.withIndex().associate { it.value to it.index }
    return withIndex()
        .sortedWith(compareBy({ rank[mediaServerEntryKey(provider, it.value.id)] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
}

/**
 * The same state with its servers in the viewer's chosen order, and each server's libraries in the
 * order chosen for that server; see [withLibrariesInOrder].
 */
internal fun MediaServerUiState.inServerOrder(order: List<String>, libraryOrder: List<String> = emptyList()): MediaServerUiState =
    if (order.isEmpty() && libraryOrder.isEmpty()) this
    else copy(servers = servers.inServerOrder(provider, order).map { it.withLibrariesInOrder(libraryOrder) })

/** What a library's place is remembered by. It names the server, so each server keeps its own order. */
internal fun mediaServerLibraryOrderKey(serverId: String, libraryKey: String): String =
    mediaServerEntryKey("library", serverId, libraryKey)

/**
 * This server's libraries in the viewer's chosen order. Libraries [order] names come first, in that
 * order; any it does not - one newly added to the server - follow in the order the server gave.
 * Switched-off libraries are ordered like the rest, so one switched back on is where it was left.
 */
internal fun MediaServerView.withLibrariesInOrder(order: List<String>): MediaServerView {
    if (order.isEmpty() || libraries.size < 2) return this
    val rank = order.withIndex().associate { it.value to it.index }
    val sorted = libraries.withIndex()
        .sortedWith(compareBy({ rank[mediaServerLibraryOrderKey(id, it.value.key)] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
    return if (sorted == libraries) this else copy(libraries = sorted)
}

/**
 * Page rows in the order Settings shows them: by server, then by library within a server, keeping
 * each library's own rows (Recently Added, the library, Recently Watched, Collections) in the order
 * they came. A row that belongs to no one library - Next Up, Favourites - keeps its place at the top
 * of its server's rows; rows of a server or library not in [servers] follow the rest.
 */
internal fun List<MediaServerRow>.inPageOrder(servers: List<MediaServerView>): List<MediaServerRow> {
    if (size < 2) return this
    val serverRank = servers.withIndex().associate { it.value.id to it.index }
    val libraryRank = servers.associate { server -> server.id to server.libraries.withIndex().associate { it.value.key to it.index } }
    return withIndex()
        .sortedWith(
            compareBy(
                { serverRank[it.value.serverId] ?: Int.MAX_VALUE },
                { row -> row.value.libraryKey?.let { libraryRank[row.value.serverId]?.get(it) ?: Int.MAX_VALUE } ?: -1 },
                { it.index },
            ),
        )
        .map { it.value }
}

/**
 * A library order recovered from the first version of this, which remembered page rows. Each row
 * key named its server and its library, so the libraries come out in the order their first row
 * was placed. Rows with no library (Next Up, Favourites) had no library to give.
 */
internal fun libraryOrderFromRowOrder(rowKeys: List<String>): List<String> = rowKeys.mapNotNull { key ->
    val parts = key.split('/').map { java.net.URLDecoder.decode(it, "UTF-8") }
    if (parts.size != 3 || parts[0] != "row") return@mapNotNull null
    val library = parts[2].substringAfter(':', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
    mediaServerLibraryOrderKey(parts[1], library)
}.distinct()

/**
 * The order after moving [key] by [offset] places among [shown] - the servers as the list shows
 * them now. What the viewer sees decides the move, so a hidden or removed server between two
 * visible ones never makes a press appear to do nothing; servers in [order] that are not shown
 * keep their keys, after the shown ones, so nothing remembered is lost.
 */
internal fun movedServerOrder(order: List<String>, shown: List<String>, key: String, offset: Int): List<String> {
    val from = shown.indexOf(key)
    if (from < 0) return order
    val to = (from + offset).coerceIn(0, shown.lastIndex)
    if (to == from) return order
    val moved = shown.toMutableList().apply { add(to, removeAt(from)) }
    return moved + order.filterNot { it in moved }
}

/** What has been taken off the list and can be brought back, servers first. */
internal fun removedMediaServerEntries(provider: String, servers: List<MediaServerView>, removed: Set<String>): List<RemovedMediaServerEntry> = buildList {
    servers.forEach { server ->
        val serverKey = mediaServerEntryKey(provider, server.id)
        if (!mediaServerEntryListed(removed, serverKey, server.enabled)) {
            add(RemovedMediaServerEntry(serverKey, server.id, server.name, null))
            // Its libraries went with it and come back with it; none is listed here as well. One
            // removed before the server was is forgotten when the server is removed.
            return@forEach
        }
        server.libraries.forEach { library ->
            val key = mediaServerEntryKey(provider, server.id, library.key)
            if (!mediaServerEntryListed(removed, key, library.enabled)) add(RemovedMediaServerEntry(key, server.id, server.name, library))
        }
    }
}
