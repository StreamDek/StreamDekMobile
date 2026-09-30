package net.streamdek.mobile.nativeapp.mediaserver

import org.json.JSONArray
import org.json.JSONObject

/**
 * Plex and Jellyfin in a StreamDek backup, and how a backup is reconciled when it comes back.
 *
 * What a backup carries for each profile:
 *
 * ```
 * "mediaServers": {
 *   "plex":     { "linked": true, "accountName": "Henry",
 *                 "servers": [ { "id", "name", "enabled", "libraries": { key: on } } ] },
 *   "jellyfin": { "servers": [ { "id", "name", "addresses", "userId", "userName",
 *                                "enabled", "libraries", "accessToken"? } ] }
 * }
 * ```
 *
 * Plex's credential is never in a backup: it lives only at StreamDek, which renews it, and no
 * device ever holds it. A Jellyfin server's access token is carried only in an encrypted backup -
 * the same rule as every other credential a backup holds - and only restored when the viewer
 * chooses to restore credentials. Without it, the server comes back as one to sign in to again,
 * with its address and choices ready.
 *
 * A backup is a copy from one moment, and the profile's setup at StreamDek may have moved on since.
 * So nothing in it overwrites a newer setup: each server is compared with the time the backup was
 * made. A server changed on the profile after that keeps the profile's version; one removed after
 * that stays removed; one the profile has not touched since takes the backup's choices. The device's
 * own state is the third party: a choice changed here and not yet sent after the backup was made
 * also wins. These decisions are the pure functions at the bottom, tested directly.
 */

data class PlexBackupServer(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val libraries: Map<String, Boolean>,
)

data class PlexBackupState(
    val linked: Boolean,
    val accountName: String?,
    val servers: List<PlexBackupServer>,
)

data class JellyfinBackupServer(
    val id: String,
    val name: String,
    val addresses: List<String>,
    val userId: String?,
    val userName: String?,
    val enabled: Boolean,
    val libraries: Map<String, Boolean>,
    /** Only ever filled from, or written to, an encrypted backup. */
    val accessToken: String? = null,
) {
    override fun toString(): String = "JellyfinBackupServer(id=$id, name=$name, token=${if (accessToken == null) "none" else "[redacted]"})"
}

data class MediaServerBackupData(
    val plex: PlexBackupState?,
    val jellyfin: List<JellyfinBackupServer>,
) {
    val isEmpty: Boolean get() = (plex == null || !plex.linked) && jellyfin.isEmpty()
    val tokenCount: Int get() = jellyfin.count { !it.accessToken.isNullOrBlank() }
}

private fun booleanMap(json: JSONObject?): Map<String, Boolean> = buildMap {
    json?.keys()?.forEach { key -> if (json.opt(key) is Boolean) put(key, json.optBoolean(key)) }
}

private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

/** A profile's setup as a backup section. Tokens only when [includeTokens]. Null when there is nothing. */
fun buildMediaServerBackup(data: MediaServerBackupData, includeTokens: Boolean): JSONObject? {
    if (data.isEmpty) return null
    val root = JSONObject()
    data.plex?.takeIf { it.linked }?.let { plex ->
        root.put(
            "plex",
            JSONObject()
                .put("linked", true)
                .put("accountName", plex.accountName ?: JSONObject.NULL)
                .put("servers", JSONArray().apply {
                    plex.servers.forEach { server ->
                        put(JSONObject().put("id", server.id).put("name", server.name).put("enabled", server.enabled).put("libraries", JSONObject(server.libraries)))
                    }
                }),
        )
    }
    if (data.jellyfin.isNotEmpty()) {
        root.put(
            "jellyfin",
            JSONObject().put("servers", JSONArray().apply {
                data.jellyfin.forEach { server ->
                    val item = JSONObject()
                        .put("id", server.id)
                        .put("name", server.name)
                        .put("addresses", JSONArray(server.addresses))
                        .put("userId", server.userId ?: JSONObject.NULL)
                        .put("userName", server.userName ?: JSONObject.NULL)
                        .put("enabled", server.enabled)
                        .put("libraries", JSONObject(server.libraries))
                    if (includeTokens && !server.accessToken.isNullOrBlank()) item.put("accessToken", server.accessToken)
                    put(item)
                }
            }),
        )
    }
    return root
}

/** Reads a backup section back; a damaged or missing part is simply absent. */
fun parseMediaServerBackup(json: JSONObject?): MediaServerBackupData {
    json ?: return MediaServerBackupData(null, emptyList())
    val plex = json.optJSONObject("plex")?.let { section ->
        val servers = section.optJSONArray("servers")
        PlexBackupState(
            linked = section.optBoolean("linked"),
            accountName = section.text("accountName"),
            servers = (0 until (servers?.length() ?: 0)).mapNotNull { index ->
                val item = servers?.optJSONObject(index) ?: return@mapNotNull null
                val id = item.text("id") ?: return@mapNotNull null
                PlexBackupServer(id, item.text("name") ?: "Plex Media Server", item.optBoolean("enabled", true), booleanMap(item.optJSONObject("libraries")))
            },
        )
    }
    val servers = json.optJSONObject("jellyfin")?.optJSONArray("servers")
    val jellyfin = (0 until (servers?.length() ?: 0)).mapNotNull { index ->
        val item = servers?.optJSONObject(index) ?: return@mapNotNull null
        val id = item.text("id") ?: return@mapNotNull null
        val addressArray = item.optJSONArray("addresses")
        val addresses = (0 until (addressArray?.length() ?: 0)).mapNotNull { addressArray?.optString(it)?.takeIf { url -> url.startsWith("http://") || url.startsWith("https://") } }
        if (addresses.isEmpty()) return@mapNotNull null
        JellyfinBackupServer(
            id = id,
            name = item.text("name") ?: "Jellyfin",
            addresses = addresses.distinct(),
            userId = item.text("userId"),
            userName = item.text("userName"),
            enabled = item.optBoolean("enabled", true),
            libraries = booleanMap(item.optJSONObject("libraries")),
            accessToken = item.text("accessToken"),
        )
    }
    return MediaServerBackupData(plex, jellyfin)
}

/** Leaves the tokens out, for a restore made without credentials. */
fun MediaServerBackupData.withoutTokens(): MediaServerBackupData = copy(jellyfin = jellyfin.map { it.copy(accessToken = null) })

// ── Reconciling ─────────────────────────────────────────────────────────────────────────────────

/** What a restore does with one server from a backup. */
enum class MediaServerRestoreDecision {
    /** The profile (or this device) changed it after the backup was made: that newer setup stays. */
    KeepNewer,
    /** Removed from the profile after the backup was made: it stays removed. */
    RemovedSince,
    /** Already as the backup has it. */
    Unchanged,
    /** Its choices are put back as they were in the backup. */
    ApplyChoices,
    /** Put back from the backup's (encrypted) sign-in, and sent to the profile. */
    AddWithSignIn,
    /** Not on the profile and the backup carries no sign-in: offered to sign in to again. */
    NeedsSignIn,
    /** Plex is not connected on the profile: reconnect, and the backup's choices are applied then. */
    NeedsReconnect,
}

/**
 * One Jellyfin server from a backup made at [backupCreatedAt].
 *
 * [present] is whether this device (after reading the profile) has the server; [sameChoices]
 * whether its choices already match the backup's. [profileChangedAt] is when the profile's copy of
 * the server last changed, [profileRemovedAt] when it was removed from the profile if it was, and
 * [localChangedAt] when choices were changed on this device and not yet sent (0 when none).
 */
fun decideJellyfinRestore(
    backupCreatedAt: Long,
    present: Boolean,
    sameChoices: Boolean,
    hasToken: Boolean,
    profileChangedAt: Long,
    profileRemovedAt: Long,
    localChangedAt: Long,
): MediaServerRestoreDecision = when {
    present && sameChoices -> MediaServerRestoreDecision.Unchanged
    present && (profileChangedAt > backupCreatedAt || localChangedAt > backupCreatedAt) -> MediaServerRestoreDecision.KeepNewer
    present -> MediaServerRestoreDecision.ApplyChoices
    profileRemovedAt > backupCreatedAt -> MediaServerRestoreDecision.RemovedSince
    hasToken -> MediaServerRestoreDecision.AddWithSignIn
    else -> MediaServerRestoreDecision.NeedsSignIn
}

/** Plex from a backup made at [backupCreatedAt], against the profile's link and when it last changed. */
fun decidePlexRestore(
    backupCreatedAt: Long,
    linked: Boolean,
    sameChoices: Boolean,
    profileChangedAt: Long,
): MediaServerRestoreDecision = when {
    !linked -> MediaServerRestoreDecision.NeedsReconnect
    sameChoices -> MediaServerRestoreDecision.Unchanged
    profileChangedAt > backupCreatedAt -> MediaServerRestoreDecision.KeepNewer
    else -> MediaServerRestoreDecision.ApplyChoices
}

/** What a restore did, by server name, for the summary afterwards. */
data class MediaServerRestoreOutcome(
    val restored: List<String> = emptyList(),
    val keptNewer: List<String> = emptyList(),
    val needsSignIn: List<String> = emptyList(),
) {
    val touched: Int get() = restored.size + keptNewer.size + needsSignIn.size
}
