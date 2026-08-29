package net.streamdek.mobile.nativeapp

import java.util.Locale

/**
 * One answer to "are these two rows the same title", matching the backend rule exactly.
 *
 * Continue Watching is assembled from sources that each name a title their own way. SyncDek stores
 * whatever id the client that wrote the row happened to be holding — a bare TMDB id from a
 * catalogue card, `tmdb:438631` from an add-on that prefixes them, an IMDb id like `tt1160419` from
 * a Cinemeta-style add-on — and a tracking provider returns its own numbering with TMDB and IMDb
 * ids alongside. None of those spellings are interchangeable as strings.
 *
 * Comparisons here used raw string equality, which is why an explicit removal did not stick: the
 * dismissal was recorded under the spelling the card carried, the provider row came back under a
 * different one, they did not match, and the title reappeared on the next refresh. The rule now
 * lives in one place on each client and mirrors `mediaIdentity.ts` on the server, so what the
 * viewer sees the instant they remove something and what the server sends back cannot disagree.
 *
 * Deliberately conservative: two rows are the same title when they agree on type and share at least
 * one *namespaced* id. A bare number is read as TMDB only when nothing says otherwise, because a
 * tracking service's own ids are bare numbers in an unrelated sequence and collapsing them would
 * merge two different films.
 */
internal data class MediaIdentity(
  val type: String,
  val tmdbId: Int?,
  val imdbId: String?,
  val rawId: String,
) {
  /** Every key this title can be recognised by. The raw id only when there is nothing better. */
  fun keys(): List<String> = buildList {
    tmdbId?.let { add("$type:tmdb:$it") }
    imdbId?.let { add("$type:imdb:$it") }
    if (isEmpty() && rawId.isNotBlank()) add("$type:raw:$rawId")
  }
}

private val ImdbPattern = Regex("""tt\d{6,}""", RegexOption.IGNORE_CASE)
private val TmdbPrefixed = Regex("""^tmdb[:\-/]?(\d+)$""", RegexOption.IGNORE_CASE)
private val ForeignPrefixed = Regex("""^(?:trakt|simkl|punchplay)[:\-/]?(\d+)$""", RegexOption.IGNORE_CASE)
private val BareNumber = Regex("""^\d+$""")

/** `movie`, and everything else that is a series under one of its several names. */
internal fun canonicalMediaIdentityType(value: String?): String =
  when (value?.trim()?.lowercase(Locale.US)) {
    "movie", "film" -> "movie"
    else -> "tv"
  }

internal fun mediaIdentityOf(
  type: String?,
  rawId: String?,
  tmdbId: Int? = null,
  imdbId: String? = null,
): MediaIdentity {
  val raw = rawId.orEmpty().trim().lowercase(Locale.US)
  val resolvedImdb = (ImdbPattern.find(imdbId.orEmpty()) ?: ImdbPattern.find(raw))?.value?.lowercase(Locale.US)
  var resolvedTmdb = tmdbId?.takeIf { it > 0 }
  if (resolvedTmdb == null) {
    resolvedTmdb = TmdbPrefixed.matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
  }
  // A bare number is the ambiguous case. Clients overwhelmingly write TMDB ids, so it is read as
  // one — but never when the id already says it belongs to a different service's numbering.
  if (resolvedTmdb == null && ForeignPrefixed.matchEntire(raw) == null && BareNumber.matches(raw)) {
    resolvedTmdb = raw.toIntOrNull()?.takeIf { it > 0 }
  }
  return MediaIdentity(canonicalMediaIdentityType(type), resolvedTmdb, resolvedImdb, raw)
}

/** Same title, by any shared namespaced id. Type must agree either way. */
internal fun sameMediaIdentity(a: MediaIdentity, b: MediaIdentity): Boolean {
  if (a.type != b.type) return false
  val other = b.keys().toSet()
  return a.keys().any(other::contains)
}

/**
 * Whether a title-or-episode removal covers a given row.
 *
 * A removal with no episode is a removal of the whole title and covers every episode of it. One
 * naming an episode covers only that episode, so dismissing the episode just finished does not
 * also hide the next one the viewer is about to want.
 */
internal fun removalCoversEpisode(
  removalSeason: Int?,
  removalEpisode: Int?,
  candidateSeason: Int?,
  candidateEpisode: Int?,
): Boolean {
  if (removalSeason == null || removalEpisode == null) return true
  return removalSeason == candidateSeason && removalEpisode == candidateEpisode
}
