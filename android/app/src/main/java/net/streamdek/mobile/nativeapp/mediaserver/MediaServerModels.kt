package net.streamdek.mobile.nativeapp.mediaserver

import net.streamdek.mobile.nativeapp.MediaItem

/**
 * StreamDek's own picture of a personal media server, whichever product it is.
 *
 * Everything the screens read is here, in terms that mean the same thing for Plex, Jellyfin or
 * Emby: a server, whether it can be reached and how, the libraries on it and which of those the
 * viewer switched on. Provider-specific detail stays inside the provider.
 *
 * Nothing in this file holds a credential except [MediaServerEndpoint], whose `toString` is
 * overridden so the token cannot reach a log line by accident.
 */

/** What a library holds, as far as StreamDek's pages are concerned. */
enum class MediaServerLibraryKind { Movies, Shows, Other }

data class MediaServerLibrary(
    val serverId: String,
    val key: String,
    val title: String,
    val kind: MediaServerLibraryKind,
    /** The viewer's choice, or the default for [kind] when they have not made one. */
    val enabled: Boolean,
    val itemCount: Int? = null,
) {
    /** StreamDek's media type for what this library holds, or null for mixed/personal media. */
    val mediaType: String? get() = when (kind) {
        MediaServerLibraryKind.Movies -> "movie"
        MediaServerLibraryKind.Shows -> "tv"
        MediaServerLibraryKind.Other -> null
    }
}

/** How a server is reached right now. Decided on the device, because only the device is on the viewer's network. */
sealed interface MediaServerReachability {
    data object Unknown : MediaServerReachability
    data object Connecting : MediaServerReachability
    data class Online(val route: MediaServerRoute) : MediaServerReachability
    data class Offline(val reason: OfflineReason) : MediaServerReachability
}

enum class MediaServerRoute { Local, Remote, Relay }

enum class OfflineReason { Unreachable, Unauthorized, NoConnections }

/** One server as the settings page and the Plex page show it. */
data class MediaServerView(
    val id: String,
    val name: String,
    val owned: Boolean,
    /** Who shared it, for a server that is not the viewer's own. */
    val ownerName: String?,
    val enabled: Boolean,
    val reachability: MediaServerReachability,
    val libraries: List<MediaServerLibrary>,
    /** What last went wrong reading its titles (a route and a status only), for when its rows do not come. */
    val problem: String? = null,
)

/**
 * A way to reach one server, with the token that server accepts.
 *
 * Kept on the device in [MediaServerVault] and in memory for requests. Never serialised to a log:
 * see [toString].
 */
data class MediaServerEndpoint(
    val serverId: String,
    val uri: String,
    val local: Boolean,
    val relay: Boolean,
    val accessToken: String?,
) {
    override fun toString(): String = "MediaServerEndpoint(serverId=$serverId, uri=$uri, local=$local, relay=$relay, token=${if (accessToken.isNullOrBlank()) "none" else "[redacted]"})"
}

/** A server as discovered by the account, with every route plex.tv knows for it. */
data class DiscoveredMediaServer(
    val id: String,
    val name: String,
    val owned: Boolean,
    val ownerName: String?,
    val enabled: Boolean,
    val presence: Boolean,
    val accessToken: String?,
    val connections: List<MediaServerEndpoint>,
    /** The viewer's library choices for this server, keyed by library key. */
    val libraryChoices: Map<String, Boolean>,
) {
    override fun toString(): String = "DiscoveredMediaServer(id=$id, name=$name, owned=$owned, enabled=$enabled, connections=${connections.size})"
}

/**
 * The whole integration, as the navigation, the settings page and the Plex page need it.
 *
 * [navigationVisible] is the rule the brief sets for the Plex destination: linked, and at least one
 * server with a switched-on library. It deliberately does not require a server to be *reachable*:
 * a sleeping NAS should show its offline state on the Plex page, not make the tab vanish and the
 * layout jump under the viewer's remote.
 */
data class MediaServerUiState(
    val provider: String = PLEX_PROVIDER_ID,
    /** Signed in to StreamDek. A media server is linked to a StreamDek profile, so this comes first. */
    val available: Boolean = false,
    val linked: Boolean = false,
    val needsAttention: Boolean = false,
    val accountName: String? = null,
    val accountThumb: String? = null,
    val servers: List<MediaServerView> = emptyList(),
    val refreshing: Boolean = false,
    /** A short, viewer-safe reason the last refresh failed. Never an upstream body. */
    val error: String? = null,
) {
    val usableLibraries: List<MediaServerLibrary>
        get() = servers.filter { it.enabled }.flatMap { server -> server.libraries.filter { it.enabled } }

    val navigationVisible: Boolean get() = linked && usableLibraries.isNotEmpty()
}

/** One row on Home or on the Plex page. */
data class MediaServerRow(
    /** A Home Rows id in the add-on shape - see [mediaServerHomeRowId] - so row settings apply to it. */
    val id: String,
    val title: String,
    val serverId: String,
    val serverName: String,
    val kind: MediaServerRowKind,
    val mediaType: String,
    val items: List<MediaItem>,
    /** The library a "view all" opens, when the row is one library's. */
    val libraryKey: String? = null,
)

enum class MediaServerRowKind {
    RecentlyAdded, Library, Collections, RecentlyWatched,
    /** The next unwatched episode of each series in progress (Jellyfin's Next Up). */
    NextUp,
    /** Titles the viewer marked as favourites on the server. */
    Favourites,
}

/** One page of a library, for grids that load as they scroll. */
data class MediaServerPage(
    val items: List<MediaItem>,
    val start: Int,
    val total: Int,
    /**
     * How many entries the server gave for this page, before any were set aside (a title it could
     * not map, one that was never watched). The next page starts after these, not after [items].
     */
    val returned: Int = items.size,
) {
    val nextStart: Int get() = start + returned
    val end: Boolean get() = returned == 0 || nextStart >= total
}

/**
 * An episode, as the phone names one: by season and number. The phone's own models carry these as
 * two integers on [net.streamdek.mobile.nativeapp.EpisodeItem] and on cards; this is the pair.
 */
data class MediaServerEpisode(val seasonNumber: Int, val episodeNumber: Int)

/**
 * A critic's review of a title, as the server holds it: who wrote it, where it ran, the review
 * itself and where to read it in full. [positive] is the critic's verdict when the server gives
 * one (fresh or rotten, say), and null when it does not.
 */
data class MediaServerReview(
    val author: String,
    val publication: String?,
    val text: String,
    val link: String?,
    val positive: Boolean?,
)

/** A title's in-progress state on the server, for the unified Continue Watching. */
data class MediaServerResume(
    /** A Continue Watching card: progress, resume season and episode, and when it was watched. */
    val item: MediaItem,
    /** When the server last saw it played, epoch millis; what reconciliation compares. */
    val lastViewedAtMs: Long,
    val tmdbId: Int?,
    val imdbId: String?,
)

/** Where one title stands on the server: the resume point, and whether it counts as watched. */
data class MediaServerProgress(
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val lastViewedAtMs: Long,
)

/** One episode's standing, for a series page's resume target and watched marks. */
data class MediaServerEpisodeProgress(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val progress: MediaServerProgress,
)

/** What the player reports, mapped onto what a server's timeline understands. */
enum class MediaServerPlaybackState { Playing, Paused, Stopped }

const val PLEX_PROVIDER_ID = "plex"
const val JELLYFIN_PROVIDER_ID = "jellyfin"
/** Emby: the same family as Jellyfin, served by the same provider code; see MediaBrowserFlavor. */
const val EMBY_PROVIDER_ID = "emby"

/**
 * The Home Rows id for a media server row.
 *
 * Shaped like an add-on row - `addon:<source>:<type>:<catalogue>:<index>` - on purpose: that is
 * the shape Home Rows settings, the phone's By Add-on organisation and StreamDek Fuse already
 * understand, so a Plex row can be reordered, switched off and grouped under its server with no
 * special case anywhere. `<source>` is `mediaserver.<provider>.<server>`, which cannot collide with
 * an add-on id (those never start with `mediaserver.`).
 */
fun mediaServerHomeRowId(provider: String, serverId: String, mediaType: String, catalogue: String, index: Int): String {
    val type = if (mediaType == "tv") "series" else mediaType
    val safeCatalogue = catalogue.replace(':', '-')
    return "addon:$HOME_ROW_SOURCE_PREFIX$provider.$serverId:$type:$safeCatalogue:$index"
}

const val HOME_ROW_SOURCE_PREFIX = "mediaserver."

/** Whether a Home Rows add-on id belongs to a media server rather than an add-on. */
fun isMediaServerHomeRowSource(addonId: String?): Boolean = addonId?.startsWith(HOME_ROW_SOURCE_PREFIX) == true
