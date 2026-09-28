package net.streamdek.mobile.nativeapp.mediaserver.plex

import net.streamdek.mobile.nativeapp.CastMember
import net.streamdek.mobile.nativeapp.EpisodeItem
import net.streamdek.mobile.nativeapp.MediaDetail
import net.streamdek.mobile.nativeapp.MediaItem
import net.streamdek.mobile.nativeapp.SeasonSummary
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerIdentities
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerResume
import net.streamdek.mobile.nativeapp.mediaserver.PLEX_PROVIDER_ID
import java.net.URLEncoder

/**
 * Plex metadata into StreamDek Mobile's own models.
 *
 * The same rules as the television's mapping: a title's `id` is a [MediaServerReference] so it
 * routes back to its server; an episode is shown as its series; artwork goes through the server's
 * photo transcoder at card size, and no URL carries a token. The phone's cards have no TMDB or IMDb
 * field, so those ids are recorded in [MediaServerIdentities] beside the card instead - that is what
 * lets Continue Watching and the Watchlist recognise a Plex film as the film it is.
 */
internal data class PlexMappingContext(
    val serverId: String,
    val baseUri: String,
    val attribution: String,
    val libraryTitles: Map<String, String> = emptyMap(),
)

internal object PlexImages {
    fun url(context: PlexMappingContext, path: String?, width: Int, height: Int): String? {
        val value = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        val encoded = URLEncoder.encode(value, "UTF-8")
        return "${context.baseUri}/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1&url=$encoded"
    }

    fun poster(context: PlexMappingContext, path: String?) = url(context, path, 342, 513)
    fun backdrop(context: PlexMappingContext, path: String?) = url(context, path, 1280, 720)
    fun still(context: PlexMappingContext, path: String?) = url(context, path, 480, 270)
    fun portrait(context: PlexMappingContext, path: String?) = url(context, path, 200, 200)
}

internal object PlexMapping {
    private val tmdbGuid = Regex("^tmdb://(\\d{1,12})$")
    private val imdbGuid = Regex("^imdb://(tt\\d{5,12})$")
    private val legacyImdb = Regex("imdb://(tt\\d{5,12})")
    private val legacyTmdb = Regex("themoviedb://(\\d{1,12})")

    const val COLLECTION_TYPE = "collection"

    fun reference(context: PlexMappingContext, ratingKey: String): MediaServerReference =
        MediaServerReference(PLEX_PROVIDER_ID, context.serverId, ratingKey)

    fun tmdbId(meta: PlexMetadata): Int? =
        meta.guids.orEmpty().firstNotNullOfOrNull { tag -> tag.id?.let { tmdbGuid.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() } }
            ?: meta.guid?.let { legacyTmdb.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    fun imdbId(meta: PlexMetadata): String? =
        meta.guids.orEmpty().firstNotNullOfOrNull { tag -> tag.id?.let { imdbGuid.matchEntire(it.trim())?.groupValues?.get(1) } }
            ?: meta.guid?.let { legacyImdb.find(it)?.groupValues?.get(1) }

    fun mediaType(meta: PlexMetadata): String? = when (meta.type?.lowercase()) {
        "movie", "clip", "video" -> "movie"
        "show", "season", "episode" -> "tv"
        "collection" -> COLLECTION_TYPE
        else -> null
    }

    private fun seriesKey(meta: PlexMetadata): String? = when (meta.type?.lowercase()) {
        "episode" -> meta.grandparentRatingKey
        "season" -> meta.parentRatingKey
        else -> meta.ratingKey
    }

    private fun percent(offsetMs: Long?, durationMs: Long?): Double? {
        if (offsetMs == null || durationMs == null || offsetMs <= 0 || durationMs <= 0) return null
        return (offsetMs.toDouble() / durationMs * 100.0).coerceIn(0.0, 100.0)
    }

    private fun year(meta: PlexMetadata): String? =
        meta.year?.takeIf { it > 0 }?.toString() ?: meta.originallyAvailableAt?.take(4)?.takeIf { it.all(Char::isDigit) }

    fun item(meta: PlexMetadata, context: PlexMappingContext, libraryKey: String? = meta.librarySectionID): MediaItem? {
        val type = mediaType(meta) ?: return null
        val episodic = meta.type.equals("episode", true) || meta.type.equals("season", true)
        val key = seriesKey(meta) ?: return null
        val title = (if (episodic) meta.grandparentTitle ?: meta.parentTitle else meta.title)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val posterPath = when {
            meta.type.equals("episode", true) -> meta.grandparentThumb ?: meta.parentThumb ?: meta.thumb
            meta.type.equals("season", true) -> meta.parentThumb ?: meta.thumb
            else -> meta.thumb
        }
        val id = reference(context, key).encode()
        if (!episodic) MediaServerIdentities.remember(id, tmdbId(meta), imdbId(meta))
        return MediaItem(
            id = id,
            type = type,
            title = title,
            year = if (episodic) null else year(meta),
            poster = PlexImages.poster(context, posterPath),
            backdrop = PlexImages.backdrop(context, if (episodic) meta.grandparentArt ?: meta.art else meta.art),
            rating = (meta.audienceRating ?: meta.rating)?.takeIf { it > 0 },
            description = meta.summary?.takeIf { !episodic }.orEmpty(),
            progress = if (episodic) null else percent(meta.viewOffset, meta.duration),
            genres = meta.genres.orEmpty().mapNotNull { it.tag?.trim()?.takeIf(String::isNotEmpty) },
            addedAt = meta.addedAt?.times(1000L),
            sourceAddonId = MediaServerReference.sourceIdOf(PLEX_PROVIDER_ID, context.serverId),
            sourceAddonName = context.attribution,
            sourceMediaType = type,
            sourceCatalogId = libraryKey,
            sourceCatalogName = libraryKey?.let(context.libraryTitles::get) ?: meta.librarySectionTitle,
        )
    }

    fun detail(meta: PlexMetadata, seasons: List<PlexMetadata>, context: PlexMappingContext): MediaDetail? {
        val type = mediaType(meta)?.takeIf { it == "movie" || it == "tv" } ?: return null
        val key = meta.ratingKey ?: return null
        val title = meta.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val id = reference(context, key).encode()
        MediaServerIdentities.remember(id, tmdbId(meta), imdbId(meta))
        val seasonSummaries = seasons
            .filter { it.type.equals("season", true) && it.index != null && it.index >= 0 }
            .sortedBy { it.index }
            .map { season ->
                SeasonSummary(
                    seasonNumber = season.index!!,
                    name = season.title?.takeIf { it.isNotBlank() } ?: "Season ${season.index}",
                    episodeCount = season.leafCount ?: 0,
                    poster = PlexImages.poster(context, season.thumb),
                    airDate = season.originallyAvailableAt,
                )
            }
        val rating = (meta.audienceRating ?: meta.rating)?.takeIf { it > 0 }
        return MediaDetail(
            id = id,
            type = type,
            title = title,
            titleLogo = null,
            tagline = meta.tagline?.takeIf { it.isNotBlank() },
            year = year(meta),
            releaseDate = meta.originallyAvailableAt,
            description = meta.summary.orEmpty(),
            poster = PlexImages.poster(context, meta.thumb),
            backdrop = PlexImages.backdrop(context, meta.art),
            trailerUrl = null,
            rating = rating,
            imdbRating = null,
            tmdbRating = null,
            genres = meta.genres.orEmpty().mapNotNull { it.tag?.trim()?.takeIf(String::isNotEmpty) },
            runtimeMinutes = meta.duration?.takeIf { it > 0 && type == "movie" }?.let { (it / 60_000L).toInt() },
            seasonsCount = seasonSummaries.count { it.seasonNumber > 0 }.takeIf { type == "tv" },
            imdbId = imdbId(meta),
            seasons = seasonSummaries,
            cast = meta.roles.orEmpty().take(24).mapNotNull { role ->
                val name = role.tag?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                // Plex's own person ids mean nothing to TMDB, so no person page is claimed.
                CastMember(id = "", name = name, character = role.role, photo = PlexImages.portrait(context, role.thumb))
            },
            certification = meta.contentRating?.takeIf { it.isNotBlank() },
        )
    }

    fun episodes(showId: String, episodes: List<PlexMetadata>, context: PlexMappingContext): List<EpisodeItem> = episodes
        .filter { it.index != null && it.parentIndex != null }
        .sortedBy { it.index }
        .map { episode ->
            EpisodeItem(
                // The phone's own spelling for an episode, so its watched and resume keys line up.
                id = "$showId:${episode.parentIndex}:${episode.index}",
                episodeNumber = episode.index!!,
                seasonNumber = episode.parentIndex!!,
                name = episode.title?.takeIf { it.isNotBlank() } ?: "Episode ${episode.index}",
                overview = episode.summary.orEmpty(),
                still = PlexImages.still(context, episode.thumb),
                runtime = episode.duration?.takeIf { it > 0 }?.let { (it / 60_000L).toInt() },
                airDate = episode.originallyAvailableAt,
            )
        }

    fun resume(meta: PlexMetadata, context: PlexMappingContext, seriesGuids: PlexMetadata? = null): MediaServerResume? {
        val offset = meta.viewOffset?.takeIf { it > 0 } ?: return null
        val duration = meta.duration?.takeIf { it > 0 } ?: return null
        val lastViewed = (meta.lastViewedAt ?: meta.updatedAt ?: 0L) * 1000L
        return when (meta.type?.lowercase()) {
            "episode" -> {
                val showKey = meta.grandparentRatingKey ?: return null
                val season = meta.parentIndex ?: return null
                val number = meta.index ?: return null
                val id = reference(context, showKey).encode()
                val tmdb = seriesGuids?.let(::tmdbId)
                val imdb = seriesGuids?.let(::imdbId)
                MediaServerIdentities.remember(id, tmdb, imdb)
                MediaServerResume(
                    item = MediaItem(
                        id = id,
                        type = "tv",
                        title = meta.grandparentTitle ?: meta.title ?: return null,
                        year = null,
                        poster = PlexImages.poster(context, meta.grandparentThumb ?: meta.parentThumb),
                        backdrop = PlexImages.still(context, meta.thumb) ?: PlexImages.backdrop(context, meta.grandparentArt ?: meta.art),
                        rating = null,
                        description = meta.summary.orEmpty(),
                        progress = percent(offset, duration),
                        updatedAt = lastViewed.takeIf { it > 0 },
                        sourceAddonId = MediaServerReference.sourceIdOf(PLEX_PROVIDER_ID, context.serverId),
                        sourceAddonName = context.attribution,
                        resumeSeasonNumber = season,
                        resumeEpisodeNumber = number,
                    ),
                    lastViewedAtMs = lastViewed,
                    tmdbId = tmdb,
                    imdbId = imdb,
                )
            }
            "movie", "clip", "video" -> {
                val key = meta.ratingKey ?: return null
                val id = reference(context, key).encode()
                val tmdb = tmdbId(meta)
                val imdb = imdbId(meta)
                MediaServerIdentities.remember(id, tmdb, imdb)
                MediaServerResume(
                    item = MediaItem(
                        id = id,
                        type = "movie",
                        title = meta.title ?: return null,
                        year = year(meta),
                        poster = PlexImages.poster(context, meta.thumb),
                        backdrop = PlexImages.backdrop(context, meta.art),
                        rating = (meta.audienceRating ?: meta.rating)?.takeIf { it > 0 },
                        description = meta.summary.orEmpty(),
                        progress = percent(offset, duration),
                        updatedAt = lastViewed.takeIf { it > 0 },
                        sourceAddonId = MediaServerReference.sourceIdOf(PLEX_PROVIDER_ID, context.serverId),
                        sourceAddonName = context.attribution,
                    ),
                    lastViewedAtMs = lastViewed,
                    tmdbId = tmdb,
                    imdbId = imdb,
                )
            }
            else -> null
        }
    }
}
