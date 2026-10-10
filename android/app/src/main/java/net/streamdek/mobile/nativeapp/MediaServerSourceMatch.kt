package net.streamdek.mobile.nativeapp

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerEpisode
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerIdentities
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference

/*
 * Plex and Jellyfin as sources for any title, not only for titles opened from their own pages.
 *
 * A catalogue title is looked for on every connected server, by name, and a server title counts
 * as the same one only when the two share a TMDB or IMDb id - a name finds candidates, it never
 * confirms one. That is what keeps a remake, a same-named series or a different cut from being
 * offered as the title on screen. Episodes are then picked by season and episode number on the
 * server's own copy of the series, so a missing episode simply offers nothing.
 *
 * Kept out of the view model so the rule can be read and tested on its own.
 */

/** The ids a title is known by. Either may be missing; with neither, nothing is matched. */
internal data class TitleIds(val tmdbId: Int?, val imdbId: String?) {
  val isEmpty: Boolean get() = tmdbId == null && imdbId == null

  companion object {
    private val imdbPattern = Regex("tt\\d{5,12}", RegexOption.IGNORE_CASE)

    /**
     * A catalogue title's ids: StreamDek keeps a TMDB title under its TMDB number (bare or as
     * `tmdb:123`), and the IMDb id comes from the detail when known or from an IMDb-keyed id.
     */
    fun ofCatalogue(id: String, imdbId: String?): TitleIds {
      val tmdb = id.removePrefix("tmdb:").toIntOrNull()?.takeIf { it > 0 }
      val imdb = (imdbId ?: id).let { imdbPattern.find(it)?.value?.lowercase() }
      return TitleIds(tmdb, imdb)
    }
  }
}

/** Whether a server title is the catalogue title: same kind, and at least one id in common. */
internal fun isSameServerTitle(wantedType: String, wanted: TitleIds, candidateType: String, candidate: TitleIds?): Boolean {
  if (candidate == null || wanted.isEmpty) return false
  if (normalizedMediaType(wantedType) != normalizedMediaType(candidateType)) return false
  if (wanted.tmdbId != null && wanted.tmdbId == candidate.tmdbId) return true
  return wanted.imdbId != null && candidate.imdbId != null && wanted.imdbId.equals(candidate.imdbId, ignoreCase = true)
}

/** One server's copy of a catalogue title, and the episode wanted from it when it is a series. */
internal data class MediaServerSourceMatch(val ref: MediaServerReference, val episode: MediaServerEpisode?)

/**
 * Finds a catalogue title's copies on the connected servers.
 *
 * Results are remembered for a few minutes per title, so choosing another episode, reopening the
 * page or refreshing sources asks the servers nothing new. An unreachable server times out on its
 * own without holding up the others, and it is never what the add-ons wait on.
 */
internal object MediaServerSourceFinder {
  private const val TTL_MS = 10L * 60L * 1000L
  private const val SEARCH_LIMIT = 20
  private const val SEARCH_TIMEOUT_MS = 8_000L

  private class Entry(val refs: List<MediaServerReference>, val storedAt: Long)

  private val cache = java.util.concurrent.ConcurrentHashMap<String, Entry>()

  fun clear() = cache.clear()

  suspend fun find(
    providers: List<net.streamdek.mobile.nativeapp.mediaserver.MediaServerProvider>,
    type: String,
    title: String,
    ids: TitleIds,
  ): List<MediaServerReference> {
    if (ids.isEmpty || title.isBlank() || providers.isEmpty()) return emptyList()
    val key = "${normalizedMediaType(type)}|${ids.tmdbId}|${ids.imdbId}|${providers.joinToString(",") { it.id }}"
    cache[key]?.takeIf { System.currentTimeMillis() - it.storedAt < TTL_MS }?.let { return it.refs }
    val found = supervisorScope {
      providers.map { provider ->
        async {
          withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
            runCatching { provider.search(title, SEARCH_LIMIT) }.getOrNull().orEmpty()
              .filter { item ->
                val known = MediaServerIdentities.of(item.id)?.let { TitleIds(it.tmdbId, it.imdbId?.lowercase()) }
                isSameServerTitle(type, ids, item.type, known)
              }
              .mapNotNull { MediaServerReference.decode(it.id) }
          }.orEmpty()
        }
      }.awaitAll().flatten().distinct()
    }
    if (cache.size > 400) cache.clear()
    cache[key] = Entry(found, System.currentTimeMillis())
    return found
  }
}
