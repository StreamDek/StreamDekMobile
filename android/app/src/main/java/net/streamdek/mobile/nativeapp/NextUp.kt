package net.streamdek.mobile.nativeapp

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/** Strict eligibility: unknown dates never create a Continue Watching invitation. */
internal fun nextUpHasReleased(date: String?, now: Instant = Instant.now(), today: LocalDate = LocalDate.now()): Boolean {
  val value = date?.trim()?.takeIf { it.isNotEmpty() } ?: return false
  return if (value.length == 10) runCatching { !LocalDate.parse(value).isAfter(today) }.getOrDefault(false)
  else runCatching { !OffsetDateTime.parse(value).toInstant().isAfter(now) }.getOrDefault(false)
}

/** Never skip a missing, watched or unaired episode to find something further ahead. */
internal fun immediateNextUpEpisode(
  season: Int, episode: Int, seasons: List<SeasonSummary>, episodes: List<EpisodeItem>,
): EpisodeItem? {
  episodes.firstOrNull { it.seasonNumber == season && it.episodeNumber == episode + 1 }?.let { return it }
  val count = seasons.firstOrNull { it.seasonNumber == season }?.episodeCount ?: return null
  if (count <= 0 || episode != count) return null
  val nextSeason = seasons.filter { it.seasonNumber > season && it.seasonNumber > 0 }.minByOrNull { it.seasonNumber } ?: return null
  return episodes.firstOrNull { it.seasonNumber == nextSeason.seasonNumber && it.episodeNumber == 1 }
}

internal fun nextUpSeriesKey(record: PlaybackProgressRecord): String =
  mediaIdentityOf("tv", record.entityId, record.tmdbId, record.imdbId).keys().firstOrNull() ?: "tv:raw:${record.entityId}"

internal fun nextUpAnchors(records: List<PlaybackProgressRecord>): List<PlaybackProgressRecord> =
  records.filter { canonicalMediaIdentityType(it.entityType) == "tv" }
    .groupBy(::nextUpSeriesKey).values.mapNotNull { events ->
      // Reconcile each episode before choosing the series position. An explicit unwatched write
      // retires that episode's completion; it is not playback of a newer episode and must not
      // hide the invitation following the last completed episode (for example E4 -> unwatched E5).
      val current = events.groupBy { it.seasonNumber to it.episodeNumber }
        .map { (_, versions) -> versions.maxBy { it.updatedAt } }
      val latest = current.filter { it.dismissed || (!it.unwatched && (it.completed || it.progress > 0.0)) }
        .maxWithOrNull(compareBy<PlaybackProgressRecord> { it.updatedAt }
          .thenBy { it.seasonNumber ?: 0 }.thenBy { it.episodeNumber ?: 0 }) ?: return@mapNotNull null
      latest.takeIf { !it.dismissed && (it.completed || it.progress >= 95.0) &&
        it.seasonNumber != null && it.episodeNumber != null }
    }.sortedByDescending { it.updatedAt }

/** A profile-local history survives removal of completed positions from the resume store. */
internal class NextUpHistory(context: Context) {
  private val prefs = context.getSharedPreferences("streamdek_next_up_history", Context.MODE_PRIVATE)

  fun clear(owner: String) {
    prefs.edit().remove(owner).putLong("cleared:$owner", System.currentTimeMillis()).apply()
  }

  fun merge(owner: String, incoming: List<PlaybackProgressRecord>): List<PlaybackProgressRecord> {
    val saved = runCatching {
      val array = JSONArray(prefs.getString(owner, "[]"))
      (0 until array.length()).map { index ->
        val o = array.getJSONObject(index)
        fun text(key: String) = o.optString(key).takeIf { it.isNotBlank() && it != "null" }
        PlaybackProgressRecord("tv", o.getString("id"), text("key"),
          o.optInt("season").takeIf { it > 0 }, o.optInt("episode").takeIf { it > 0 },
          text("title"), text("poster"), text("backdrop"), text("year"), 0.0, 0.0,
          o.optDouble("progress", 0.0), o.optBoolean("completed"),
          unwatched = o.optBoolean("unwatched"), dismissed = o.optBoolean("dismissed"),
          tmdbId = o.optInt("tmdb").takeIf { it > 0 }, imdbId = text("imdb"), updatedAt = o.getLong("at"))
      }
    }.getOrDefault(emptyList())
    val cleared = prefs.getLong("cleared:$owner", 0L)
    val records = (saved + incoming).filter { canonicalMediaIdentityType(it.entityType) == "tv" && it.updatedAt > cleared }
      .groupBy { listOf(nextUpSeriesKey(it), it.seasonNumber, it.episodeNumber) }
      .map { (_, versions) -> versions.maxBy { it.updatedAt } }
      .sortedByDescending { it.updatedAt }.take(400)
    val json = JSONArray(records.map { r -> JSONObject().put("id", r.entityId).put("key", r.episodeKey)
      .put("season", r.seasonNumber).put("episode", r.episodeNumber).put("title", r.title)
      .put("poster", r.poster).put("backdrop", r.backdrop).put("year", r.year)
      .put("progress", r.progress).put("completed", r.completed).put("unwatched", r.unwatched)
      .put("dismissed", r.dismissed).put("tmdb", r.tmdbId).put("imdb", r.imdbId).put("at", r.updatedAt) }).toString()
    if (json != prefs.getString(owner, "[]")) prefs.edit().putString(owner, json).apply()
    return records
  }
}

internal class NextUpResolver(private val api: StreamDekApiClient) {
  private data class Cached(val at: Long, val detail: MediaDetail, val episode: EpisodeItem?)
  private val cache = mutableMapOf<String, Cached>()

  suspend fun resolve(anchor: PlaybackProgressRecord): Pair<MediaDetail, EpisodeItem>? {
    val key = "${anchor.entityId}:${anchor.seasonNumber}:${anchor.episodeNumber}"
    val now = System.currentTimeMillis()
    val cached = cache[key]?.takeIf { now - it.at < 15 * 60_000 }
    val result = cached ?: run {
      val detail = api.fetchDetails("tv", anchor.entityId, anchor.title, anchor.year).getOrNull() ?: return null
      val season = anchor.seasonNumber ?: return null
      val episode = anchor.episodeNumber ?: return null
      val current = api.fetchSeason(detail.id, season).getOrNull() ?: return null
      val sameSeason = current.firstOrNull { it.episodeNumber == episode + 1 }
      val nextSeason = detail.seasons.filter { it.seasonNumber > season }.minByOrNull { it.seasonNumber }
      val next = if (sameSeason == null && episode == detail.seasons.firstOrNull { it.seasonNumber == season }?.episodeCount && nextSeason != null) {
        api.fetchSeason(detail.id, nextSeason.seasonNumber).getOrNull() ?: return null
      } else emptyList()
      Cached(now, detail, immediateNextUpEpisode(season, episode, detail.seasons, current + next)).also {
        if (cache.size >= 100) cache.clear()
        cache[key] = it
      }
    }
    val episode = result.episode?.takeIf { nextUpHasReleased(it.airDate) } ?: return null
    return result.detail to episode
  }
}

/** A real resume wins over Next Up; ambiguous provider placeholders do not block advancement. */
internal fun mergeNextUpContinueWatching(resume: List<MediaItem>, next: List<MediaItem>): List<MediaItem> {
  val partial = resume.filter { (it.progress ?: 0.0) > 0.0 && (it.progress ?: 0.0) < 95.0 }
  return (partial + next + resume).distinctBy { mediaIdentityOf(it.type, it.id).keys().firstOrNull() ?: "${it.type}:${it.id}" }
    .sortedByDescending { it.updatedAt ?: 0L }
}

/** A newer explicit unwatched event overrides historical watched flags, but never a dismissal. */
internal fun nextUpTargetIsWatched(latest: PlaybackProgressRecord?, historicalWatched: Boolean): Boolean =
  latest?.dismissed == true || (latest?.unwatched != true &&
    (latest?.completed == true || (latest?.progress ?: 0.0) >= 95.0 || historicalWatched))
