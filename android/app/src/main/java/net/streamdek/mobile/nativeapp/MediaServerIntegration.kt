package net.streamdek.mobile.nativeapp

import android.content.Context
import net.streamdek.mobile.R
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerIdentities
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerLabels
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerResume
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRow
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRowKind

/**
 * Where a personal media server's content meets the phone's own, kept apart from the view model -
 * which is close to the size the dex verifier will take - so the rules can be read and tested alone.
 *
 * Nothing here knows it is Plex: it speaks [MediaServerRow], [MediaServerResume] and the phone's
 * own [MediaItem], so a second provider arrives through the same doors.
 */

/** Whether a title, by its id, belongs to a personal media server rather than to StreamDek or an add-on. */
internal fun isMediaServerId(id: String?): Boolean = MediaServerReference.isReference(id)

/** A browsable row holding one media server collection's titles; the rest of the id is the collection's card id. */
internal const val MEDIA_SERVER_COLLECTION_ROW_PREFIX = "mediaserver-collection:"

/**
 * Whether a "View all" list is a media server's: one of its library rows - add-on shaped, with a
 * `mediaserver.` source (see [net.streamdek.mobile.nativeapp.mediaserver.mediaServerHomeRowId]) - or
 * one of its collections. Such a list pages through the server and wears its mark.
 */
internal fun isMediaServerBrowseRowId(id: String): Boolean =
  id.startsWith(MEDIA_SERVER_COLLECTION_ROW_PREFIX) ||
    (id.startsWith("addon:") && id.split(':').getOrNull(1)?.startsWith(net.streamdek.mobile.nativeapp.mediaserver.HOME_ROW_SOURCE_PREFIX) == true)

/**
 * The media server rows Home last received, for Home Rows.
 *
 * Kept here rather than in the view model's state because Home Rows is assembled from several
 * places - loading Home, the settings page, a provider list changing - and every one of them has to
 * offer the same rows. Nothing in it is secret: titles and artwork addresses without tokens.
 */
internal object MediaServerHomeRows {
  @Volatile
  var rows: List<MediaServerRow> = emptyList()
}

/**
 * Media server rows as Home Rows entries, in the add-on shape (see
 * [net.streamdek.mobile.nativeapp.mediaserver.mediaServerHomeRowId]) so ordering, visibility and
 * By Add-on grouping treat them like any other source's.
 *
 * Recently Added starts switched on: it is the row a viewer who has just connected Plex expects to
 * find. Whole-library rows start off, so a large library does not take over Home. Collections are
 * left to the Plex page, because a collection card opens a list, not a title.
 */
internal fun mediaServerHomeCatalogCandidates(rows: List<MediaServerRow> = MediaServerHomeRows.rows): List<HomeCatalogRow> = rows
  .filter { it.kind != MediaServerRowKind.Collections }
  .map { row ->
    HomeCatalogRow(
      id = row.id,
      title = row.title,
      subtitleRes = R.string.home_row_from_addon,
      subtitleArg = row.items.firstOrNull()?.sourceAddonName ?: row.serverName,
      builtin = false,
      enabled = row.kind == MediaServerRowKind.RecentlyAdded,
    )
  }

/** Every media server row with something in it, as Home sections. The layout decides which show. */
internal fun mediaServerHomeSections(rows: List<MediaServerRow>): List<MediaSection> = rows
  .filter { it.kind != MediaServerRowKind.Collections && it.items.isNotEmpty() }
  .map { row -> MediaSection(id = row.id, title = row.title, items = row.items) }

/**
 * Whether a Continue Watching card and a media server's in-progress title are the same title, and
 * so must appear once.
 *
 * Same id is the same title. Otherwise the server's TMDB or IMDb id against the card's: a film
 * started through an add-on and carried on in Plex is one film. A series is one card whichever
 * episode each side is on.
 */
internal fun sameContinueTitle(card: MediaItem, server: MediaServerResume): Boolean {
  if (card.id == server.item.id) return true
  if (normalizedMediaType(card.type) != normalizedMediaType(server.item.type)) return false
  val tmdb = server.tmdbId?.takeIf { it > 0 }
  if (tmdb != null && (card.id == tmdb.toString() || card.id == "tmdb:$tmdb")) return true
  val imdb = server.imdbId?.takeIf { it.isNotBlank() }
  return imdb != null && card.id.equals(imdb, ignoreCase = true)
}

/**
 * The phone's Continue Watching with the media servers' in-progress titles folded in.
 *
 * One card per title: where both sides know it, the more recently watched stays, so the card
 * resumes at the newest position. A title only the server knows is placed by when it was last
 * watched, without reordering the cards the phone already had - those carry their own rules (Next
 * Up among them) that this must not undo. The server never overwrites something newer.
 */
internal fun reconcileContinueWatching(local: List<MediaItem>, servers: List<MediaServerResume>): List<MediaItem> {
  if (servers.isEmpty()) return local
  val result = local.toMutableList()
  for (resume in servers.sortedByDescending { it.lastViewedAtMs }) {
    val existing = result.indexOfFirst { sameContinueTitle(it, resume) }
    if (existing >= 0) {
      if (resume.lastViewedAtMs > (result[existing].updatedAt ?: 0L)) result[existing] = resume.item
      continue
    }
    if (result.any { it.id == resume.item.id }) continue
    val insertAt = result.indexOfFirst { card -> (card.updatedAt ?: return@indexOfFirst false) < resume.lastViewedAtMs }
    if (insertAt < 0) result.add(resume.item) else result.add(insertAt, resume.item)
  }
  return result
}

/**
 * The Watchlist and Continue Watching, together, for the Library page shown when a media server is
 * connected: in-progress titles first, then the watchlist, and a title that is in both shown once -
 * under Continue Watching, where it can be resumed.
 */
internal data class UnifiedLibrary(val continueWatching: List<MediaItem>, val watchlist: List<MediaItem>) {
  val isEmpty: Boolean get() = continueWatching.isEmpty() && watchlist.isEmpty()
}

internal fun unifiedLibrary(continueWatching: List<MediaItem>, watchlist: List<MediaItem>): UnifiedLibrary {
  fun identities(item: MediaItem): Set<String> {
    val type = normalizedMediaType(item.type)
    val ids = mutableSetOf("$type:${item.id}")
    MediaServerIdentities.of(item.id)?.let { known ->
      known.tmdbId?.let { ids += "$type:$it"; ids += "$type:tmdb:$it" }
      known.imdbId?.let { ids += "$type:${it.lowercase()}" }
    }
    return ids
  }
  val seen = continueWatching.flatMapTo(hashSetOf(), ::identities)
  return UnifiedLibrary(
    continueWatching = continueWatching,
    watchlist = watchlist.filter { item -> identities(item).none { it in seen } },
  )
}

/**
 * A stream's request headers with anything a media server gave it removed, for every place a stream
 * is written down: remembered sources, backups, and hand-offs to another device. A server token is
 * sent to that server by [net.streamdek.mobile.nativeapp.mediaserver.MediaServerAuth] at request
 * time; it is never part of what is stored or sent on.
 */
internal fun withoutMediaServerHeaders(headers: Map<String, String>): Map<String, String> =
  if (headers.keys.none { it.startsWith("X-Plex-", ignoreCase = true) }) headers
  else headers.filterKeys { !it.startsWith("X-Plex-", ignoreCase = true) }

/** Words the provider puts on rows and sources, in the app's language. Read per call so a language change is honoured. */
internal class AppMediaServerLabels(private val context: () -> Context) : MediaServerLabels {
  private fun s(id: Int, vararg args: Any) = localizedAppContext(context()).resources.getString(id, *args)
  override fun recentlyAdded(library: String) = s(R.string.media_server_row_recently_added, library)
  override fun recentlyWatched(library: String) = s(R.string.media_server_row_recently_watched, library)
  override fun collections(library: String) = s(R.string.media_server_row_collections, library)
  override fun directPlay() = s(R.string.media_server_direct_play)
  override fun directStream() = s(R.string.media_server_direct_stream)
  override fun transcode(quality: String) = s(R.string.media_server_transcode_quality, quality)
  override fun attribution(provider: String, serverName: String, multipleServers: Boolean) =
    if (multipleServers) s(R.string.media_server_attribution, provider, serverName) else provider
  override fun season(number: Int) = s(R.string.detail_season_number, number)
  override fun episode(number: Int) = s(R.string.detail_episode_number, number)
}
