package net.streamdek.mobile.nativeapp.mediaserver

import net.streamdek.mobile.nativeapp.AddonStream
import net.streamdek.mobile.nativeapp.EpisodeItem
import net.streamdek.mobile.nativeapp.MediaDetail
import net.streamdek.mobile.nativeapp.MediaItem

/**
 * One kind of personal media server, as StreamDek Mobile uses it.
 *
 * The same contract the television implements, in the phone's own models: every screen - Home,
 * Search, the title page, the sources list, the player - keeps working in the terms it already
 * understands, and never learns which product a title came from. A second provider (Jellyfin,
 * Emby) implements this and nothing else.
 *
 * Linking and discovery are StreamDek-backend work ([MediaServerManager]). Everything here answers
 * from the device, times out quickly, returns empty rather than throwing for a server that is
 * away, and never puts a token in a URL it returns - see [MediaServerAuth].
 */
interface MediaServerProvider {
    val id: String
    val label: String

    fun setServers(servers: List<DiscoveredMediaServer>)
    suspend fun connect(force: Boolean = false)
    fun reachability(serverId: String): MediaServerReachability
    suspend fun libraries(serverId: String, force: Boolean = false): List<MediaServerLibrary>
    suspend fun rows(includeCollections: Boolean): List<MediaServerRow>
    suspend fun continueWatching(): List<MediaServerResume>
    suspend fun browse(serverId: String, libraryKey: String, start: Int, size: Int, sort: MediaServerSort): MediaServerPage
    suspend fun collection(ref: MediaServerReference, start: Int, size: Int): MediaServerPage
    suspend fun detail(ref: MediaServerReference): MediaDetail?
    suspend fun season(ref: MediaServerReference, seasonNumber: Int): List<EpisodeItem>?
    suspend fun search(query: String, limit: Int): List<MediaItem>

    /** Direct Play, then Direct Stream, then Transcode: the order is the decision. */
    suspend fun streams(ref: MediaServerReference, episode: MediaServerEpisode?, context: MediaServerPlaybackContext): List<AddonStream>

    suspend fun progress(ref: MediaServerReference, episode: MediaServerEpisode?): MediaServerProgress?
    suspend fun seriesProgress(ref: MediaServerReference): List<MediaServerEpisodeProgress>

    /** Live playback only. Nothing stored is ever pushed to a server. */
    suspend fun reportProgress(ref: MediaServerReference, episode: MediaServerEpisode?, positionMs: Long, durationMs: Long, state: MediaServerPlaybackState)

    suspend fun setWatched(ref: MediaServerReference, episode: MediaServerEpisode?, watched: Boolean): Boolean
    suspend fun setSeasonWatched(ref: MediaServerReference, seasonNumber: Int, watched: Boolean): Boolean
    suspend fun removeFromContinueWatching(ref: MediaServerReference, episode: MediaServerEpisode?): Boolean
    fun reset()
}

enum class MediaServerSort { RecentlyAdded, Title, ReleaseDate }

/** The words a provider puts on rows and sources, supplied by the app so they follow its language. */
interface MediaServerLabels {
    fun recentlyAdded(library: String): String
    fun recentlyWatched(library: String): String
    fun collections(library: String): String
    fun directPlay(): String
    fun directStream(): String
    fun transcode(quality: String): String
    fun attribution(provider: String, serverName: String, multipleServers: Boolean): String
    /** "Season 3", for a season the server gave no name of its own. */
    fun season(number: Int): String = "Season $number"
    /** "Episode 5", for an episode the server gave no title of its own. */
    fun episode(number: Int): String = "Episode $number"
}

data class MediaServerPlaybackContext(
    /** "Auto", "ExoPlayer" or "MPV". */
    val engine: String,
    val remoteMaxBitrateKbps: Int?,
    val deviceName: String,
    val clientIdentifier: String,
    val appVersion: String,
)
