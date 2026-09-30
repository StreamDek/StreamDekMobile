package net.streamdek.mobile.nativeapp.mediaserver.jellyfin

import net.streamdek.mobile.nativeapp.CastMember
import net.streamdek.mobile.nativeapp.EpisodeItem
import net.streamdek.mobile.nativeapp.MediaDetail
import net.streamdek.mobile.nativeapp.MediaItem
import net.streamdek.mobile.nativeapp.SeasonSummary
import net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerIdentities
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLibraryKind
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerResume
import net.streamdek.mobile.nativeapp.mediaserver.plex.PlexMapping
import java.time.Instant
import java.util.Locale

internal data class JellyfinMappingContext(
  val serverId: String,
  val baseUrl: String,
  /** "Jellyfin", or "Jellyfin · Home Server" when the profile uses more than one server. */
  val attribution: String,
  val libraryTitles: Map<String, String> = emptyMap(),
  val seasonName: (Int) -> String = { "Season $it" },
  val episodeName: (Int) -> String = { "Episode $it" },
)

/**
 * Jellyfin artwork at the size each surface draws. The image endpoint needs no token, and the URL
 * carries none: requests to a signed-in server get their header from MediaServerAuth.
 */
internal object JellyfinImages {
  private fun url(context: JellyfinMappingContext, itemId: String?, type: String, tag: String?, width: Int, height: Int, index: Int? = null): String? {
    if (itemId.isNullOrBlank() || tag.isNullOrBlank()) return null
    val path = if (index != null) "/Items/$itemId/Images/$type/$index" else "/Items/$itemId/Images/$type"
    return "${context.baseUrl.trimEnd('/')}$path?fillWidth=$width&fillHeight=$height&quality=90&tag=$tag"
  }

  fun poster(context: JellyfinMappingContext, itemId: String?, tag: String?) = url(context, itemId, "Primary", tag, 342, 513)
  fun backdrop(context: JellyfinMappingContext, itemId: String?, tag: String?) = url(context, itemId, "Backdrop", tag, 1280, 720, 0)
  fun still(context: JellyfinMappingContext, itemId: String?, tag: String?) = url(context, itemId, "Primary", tag, 480, 270)
  fun portrait(context: JellyfinMappingContext, itemId: String?, tag: String?) = url(context, itemId, "Primary", tag, 200, 200)
  fun logo(context: JellyfinMappingContext, itemId: String?, tag: String?) = url(context, itemId, "Logo", tag, 640, 180)
}

/**
 * Jellyfin items into the phone's own models. Pure, so it is tested without a server. A title's id
 * is a [MediaServerReference] that routes back to the server that owns it; its TMDB and IMDb ids
 * are remembered beside it, which is what lets Continue Watching and the Watchlist recognise the
 * same film from Jellyfin, Plex and StreamDek as one title.
 */
internal object JellyfinMapping {
  private const val TICKS_PER_MS = 10_000L

  fun reference(context: JellyfinMappingContext, itemId: String): MediaServerReference =
    MediaServerReference(JELLYFIN_PROVIDER_ID, context.serverId, itemId)

  fun ms(ticks: Long?): Long? = ticks?.takeIf { it > 0 }?.div(TICKS_PER_MS)

  fun ticks(ms: Long): Long = ms * TICKS_PER_MS

  fun tmdbId(item: JellyfinItem): Int? =
    item.providerIds.orEmpty().entries.firstOrNull { it.key.equals("Tmdb", true) }?.value?.trim()?.toIntOrNull()?.takeIf { it > 0 }

  fun imdbId(item: JellyfinItem): String? =
    item.providerIds.orEmpty().entries.firstOrNull { it.key.equals("Imdb", true) }?.value?.trim()?.takeIf { it.matches(Regex("tt\\d{5,12}")) }

  fun mediaType(item: JellyfinItem): String? = when (item.type?.lowercase(Locale.US)) {
    "movie", "video", "musicvideo" -> "movie"
    "series", "season", "episode" -> "tv"
    "boxset" -> PlexMapping.COLLECTION_TYPE
    else -> null
  }

  private fun isEpisodic(item: JellyfinItem) = item.type.equals("Episode", true) || item.type.equals("Season", true)

  private fun seriesKey(item: JellyfinItem): String? = if (isEpisodic(item)) item.seriesId else item.id

  fun year(item: JellyfinItem): String? =
    item.productionYear?.takeIf { it > 0 }?.toString() ?: item.premiereDate?.take(4)?.takeIf { it.all(Char::isDigit) }

  private fun percent(positionMs: Long?, durationMs: Long?): Double? {
    if (positionMs == null || durationMs == null || positionMs <= 0 || durationMs <= 0) return null
    return (positionMs.toDouble() / durationMs * 100.0).coerceIn(0.0, 100.0)
  }

  private fun posterOf(item: JellyfinItem, context: JellyfinMappingContext): String? = when {
    isEpisodic(item) -> JellyfinImages.poster(context, item.seriesId, item.seriesPrimaryImageTag)
    else -> JellyfinImages.poster(context, item.id, item.imageTags?.get("Primary"))
  }

  private fun backdropOf(item: JellyfinItem, context: JellyfinMappingContext): String? =
    item.backdropImageTags?.firstOrNull()?.let { JellyfinImages.backdrop(context, item.id, it) }
      ?: item.parentBackdropImageTags?.firstOrNull()?.let { JellyfinImages.backdrop(context, item.parentBackdropItemId, it) }

  private fun logoOf(item: JellyfinItem, context: JellyfinMappingContext): String? =
    item.imageTags?.get("Logo")?.let { JellyfinImages.logo(context, item.id, it) }
      ?: JellyfinImages.logo(context, item.parentLogoItemId, item.parentLogoImageTag)

  fun rating(item: JellyfinItem): Double? = item.communityRating?.takeIf { it > 0 }

  fun instantMs(value: String?): Long = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

  /** A card. Episodes and seasons become their series. */
  fun item(item: JellyfinItem, context: JellyfinMappingContext, libraryKey: String? = null): MediaItem? {
    val type = mediaType(item) ?: return null
    val episodic = isEpisodic(item)
    val key = seriesKey(item) ?: return null
    val title = (if (episodic) item.seriesName else item.name)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val id = reference(context, key).encode()
    if (!episodic) MediaServerIdentities.remember(id, tmdbId(item), imdbId(item))
    return MediaItem(
      id = id,
      type = type,
      title = title,
      year = if (episodic) null else year(item),
      poster = posterOf(item, context),
      backdrop = backdropOf(item, context),
      rating = rating(item),
      description = item.overview?.takeIf { !episodic }.orEmpty(),
      progress = if (episodic) null else percent(ms(item.userData?.playbackPositionTicks), ms(item.runTimeTicks)),
      genres = item.genres.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) },
      addedAt = instantMs(item.dateCreated).takeIf { it > 0 },
      sourceAddonId = MediaServerReference.sourceIdOf(JELLYFIN_PROVIDER_ID, context.serverId),
      sourceAddonName = context.attribution,
      sourceMediaType = type,
      sourceCatalogId = libraryKey,
      sourceCatalogName = libraryKey?.let(context.libraryTitles::get),
    )
  }

  fun detail(item: JellyfinItem, seasons: List<JellyfinItem>, context: JellyfinMappingContext): MediaDetail? {
    val type = mediaType(item)?.takeIf { it == "movie" || it == "tv" } ?: return null
    val key = item.id ?: return null
    val title = item.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val id = reference(context, key).encode()
    MediaServerIdentities.remember(id, tmdbId(item), imdbId(item))
    val summaries = seasons
      .filter { it.type.equals("Season", true) && (it.indexNumber ?: -1) >= 0 }
      .sortedBy { it.indexNumber }
      .map { season ->
        SeasonSummary(
          seasonNumber = season.indexNumber!!,
          name = season.name?.takeIf { it.isNotBlank() } ?: context.seasonName(season.indexNumber),
          episodeCount = season.childCount ?: season.recursiveItemCount ?: 0,
          poster = JellyfinImages.poster(context, season.id, season.imageTags?.get("Primary")),
          airDate = season.premiereDate?.take(10),
        )
      }
    return MediaDetail(
      id = id,
      type = type,
      title = title,
      titleLogo = logoOf(item, context),
      tagline = item.taglines?.firstOrNull()?.takeIf { it.isNotBlank() },
      year = year(item),
      releaseDate = item.premiereDate?.take(10),
      description = item.overview.orEmpty(),
      poster = posterOf(item, context),
      backdrop = backdropOf(item, context),
      trailerUrl = null,
      rating = rating(item),
      imdbRating = null,
      tmdbRating = null,
      genres = item.genres.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) },
      runtimeMinutes = ms(item.runTimeTicks)?.takeIf { type == "movie" }?.let { (it / 60_000L).toInt() },
      seasonsCount = summaries.count { it.seasonNumber > 0 }.takeIf { type == "tv" },
      imdbId = imdbId(item),
      seasons = summaries,
      cast = item.people.orEmpty()
        .filter { it.type.equals("Actor", true) || it.type.equals("GuestStar", true) }
        .take(24)
        .mapNotNull { person ->
          val name = person.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
          // Jellyfin's person ids mean nothing to TMDB, so no person page is claimed.
          CastMember(id = "", name = name, character = person.role?.takeIf { it.isNotBlank() }, photo = JellyfinImages.portrait(context, person.id, person.primaryImageTag))
        },
      certification = item.officialRating?.takeIf { it.isNotBlank() },
    )
  }

  fun episodes(showId: String, episodes: List<JellyfinItem>, context: JellyfinMappingContext): List<EpisodeItem> = episodes
    .filter { it.indexNumber != null && it.parentIndexNumber != null }
    .sortedBy { it.indexNumber }
    .map { episode ->
      EpisodeItem(
        // The phone's own spelling for an episode, so its watched and resume keys line up.
        id = "$showId:${episode.parentIndexNumber}:${episode.indexNumber}",
        episodeNumber = episode.indexNumber!!,
        seasonNumber = episode.parentIndexNumber!!,
        name = episode.name?.takeIf { it.isNotBlank() } ?: context.episodeName(episode.indexNumber),
        overview = episode.overview.orEmpty(),
        still = JellyfinImages.still(context, episode.id, episode.imageTags?.get("Primary")),
        runtime = ms(episode.runTimeTicks)?.let { (it / 60_000L).toInt() },
        airDate = episode.premiereDate?.take(10),
      )
    }

  fun resume(item: JellyfinItem, context: JellyfinMappingContext, series: JellyfinItem? = null): MediaServerResume? {
    val positionMs = ms(item.userData?.playbackPositionTicks) ?: return null
    val durationMs = ms(item.runTimeTicks) ?: return null
    val lastViewed = instantMs(item.userData?.lastPlayedDate)
    return when (item.type?.lowercase(Locale.US)) {
      "episode" -> {
        val seriesId = item.seriesId ?: return null
        val season = item.parentIndexNumber ?: return null
        val number = item.indexNumber ?: return null
        val id = reference(context, seriesId).encode()
        val tmdb = series?.let(::tmdbId)
        val imdb = series?.let(::imdbId)
        MediaServerIdentities.remember(id, tmdb, imdb)
        MediaServerResume(
          item = MediaItem(
            id = id,
            type = "tv",
            title = item.seriesName ?: item.name ?: return null,
            year = null,
            poster = posterOf(item, context),
            backdrop = JellyfinImages.still(context, item.id, item.imageTags?.get("Primary")) ?: backdropOf(item, context),
            rating = null,
            description = item.overview.orEmpty(),
            progress = percent(positionMs, durationMs),
            updatedAt = lastViewed.takeIf { it > 0 },
            sourceAddonId = MediaServerReference.sourceIdOf(JELLYFIN_PROVIDER_ID, context.serverId),
            sourceAddonName = context.attribution,
            resumeSeasonNumber = season,
            resumeEpisodeNumber = number,
          ),
          lastViewedAtMs = lastViewed,
          tmdbId = tmdb,
          imdbId = imdb,
        )
      }
      "movie", "video", "musicvideo" -> {
        val key = item.id ?: return null
        val id = reference(context, key).encode()
        val tmdb = tmdbId(item)
        val imdb = imdbId(item)
        MediaServerIdentities.remember(id, tmdb, imdb)
        MediaServerResume(
          item = MediaItem(
            id = id,
            type = "movie",
            title = item.name ?: return null,
            year = year(item),
            poster = posterOf(item, context),
            backdrop = backdropOf(item, context),
            rating = rating(item),
            description = item.overview.orEmpty(),
            progress = percent(positionMs, durationMs),
            updatedAt = lastViewed.takeIf { it > 0 },
            sourceAddonId = MediaServerReference.sourceIdOf(JELLYFIN_PROVIDER_ID, context.serverId),
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

  /** Which libraries StreamDek shows. Music, books, photos, live TV and playlists are not played here. */
  fun libraryKind(view: JellyfinItem): MediaServerLibraryKind? = when (view.collectionType?.lowercase(Locale.US)) {
    "movies" -> MediaServerLibraryKind.Movies
    "tvshows" -> MediaServerLibraryKind.Shows
    "homevideos", "mixed", "folders", null, "" -> MediaServerLibraryKind.Other
    else -> null
  }

  fun includeTypes(kind: MediaServerLibraryKind): String = when (kind) {
    MediaServerLibraryKind.Movies -> "Movie"
    MediaServerLibraryKind.Shows -> "Series"
    MediaServerLibraryKind.Other -> "Movie,Video,Series"
  }
}
