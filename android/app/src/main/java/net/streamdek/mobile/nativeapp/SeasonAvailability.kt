package net.streamdek.mobile.nativeapp

import java.time.LocalDate

fun isSeasonAvailable(season: SeasonSummary, today: LocalDate = LocalDate.now()): Boolean {
  if (season.seasonNumber <= 0 || season.episodeCount <= 0) return false
  val firstAirDate = season.airDate?.trim()?.takeIf { it.isNotEmpty() }
    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
  return firstAirDate == null || !firstAirDate.isAfter(today)
}

fun availableSeasons(seasons: List<SeasonSummary>, today: LocalDate = LocalDate.now()): List<SeasonSummary> =
  seasons.filter { isSeasonAvailable(it, today) }
