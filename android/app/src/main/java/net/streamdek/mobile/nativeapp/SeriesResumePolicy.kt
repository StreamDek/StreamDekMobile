package net.streamdek.mobile.nativeapp

data class SeriesEpisodeSlot(val seasonNumber: Int, val episodeNumber: Int)

data class SeriesProgressEvent(
  val seasonNumber: Int,
  val episodeNumber: Int,
  val positionSec: Double = 0.0,
  val status: String = "in-progress",
  val updatedAtMillis: Long = 0L,
)

data class SeriesResumeState(
  val target: SeriesEpisodeSlot?,
  val watchedEpisodeKeys: Set<String>,
  val resumePositionSec: Double = 0.0,
)

private fun resumeWatchedKey(seriesId: String, seasonNumber: Int, episodeNumber: Int): String =
  "$seriesId:s$seasonNumber:e$episodeNumber"

fun getSeriesResumeState(
  seriesId: String,
  episodes: List<SeriesEpisodeSlot>,
  progressEvents: List<SeriesProgressEvent>,
  watchedEpisodeKeys: Set<String>,
): SeriesResumeState {
  val ordered = episodes.distinct().sortedWith(compareBy(SeriesEpisodeSlot::seasonNumber, SeriesEpisodeSlot::episodeNumber))
  if (ordered.isEmpty()) return SeriesResumeState(null, watchedEpisodeKeys)
  val latestByEpisode = progressEvents.groupBy { it.seasonNumber to it.episodeNumber }
    .mapValues { (_, events) -> events.maxByOrNull(SeriesProgressEvent::updatedAtMillis)!! }
  val explicitUnwatched = latestByEpisode.values.filter { it.status == "unwatched" }
    .map { resumeWatchedKey(seriesId, it.seasonNumber, it.episodeNumber) }.toSet()
  val watched = (watchedEpisodeKeys + latestByEpisode.values.filter { it.status == "completed" }
    .map { resumeWatchedKey(seriesId, it.seasonNumber, it.episodeNumber) }) - explicitUnwatched
  val latestEvent = latestByEpisode.values.maxByOrNull(SeriesProgressEvent::updatedAtMillis)
  if (latestEvent != null && latestEvent.status in setOf("in-progress", "unwatched")) {
    val target = SeriesEpisodeSlot(latestEvent.seasonNumber, latestEvent.episodeNumber).takeIf { it in ordered }
    if (target != null) return SeriesResumeState(
      target,
      watched,
      latestEvent.positionSec.takeIf { latestEvent.status == "in-progress" } ?: 0.0,
    )
  }
  val highestWatched = ordered.indexOfLast { resumeWatchedKey(seriesId, it.seasonNumber, it.episodeNumber) in watched }
  val target = when {
    highestWatched < 0 -> ordered.first()
    highestWatched + 1 < ordered.size -> ordered[highestWatched + 1]
    else -> ordered.last()
  }
  return SeriesResumeState(target, watched)
}

fun seriesEpisodeSlots(seasons: List<SeasonSummary>): List<SeriesEpisodeSlot> = seasons.flatMap { season ->
  (1..season.episodeCount.coerceAtLeast(0)).map { episode -> SeriesEpisodeSlot(season.seasonNumber, episode) }
}
