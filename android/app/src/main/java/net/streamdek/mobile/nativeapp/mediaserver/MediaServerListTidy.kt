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
