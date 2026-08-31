package net.streamdek.mobile.nativeapp

/**
 * Getting to episode 176 of a 200-episode season.
 *
 * A season is a horizontal strip of cards, which is a fine way to look at twelve episodes and a
 * terrible way to reach the hundred-and-seventy-sixth: the list is already lazy, so nothing is
 * slow, but the distance is real and there is no way to cross it except to keep swiping.
 *
 * The fix is to stop treating one long season as one long list. Past a threshold the season is cut
 * into blocks of twenty, the viewer picks a block, and the strip shows only that block - so the
 * furthest any episode ever sits from the start of the strip is nineteen cards. Anything shorter
 * than the threshold is left exactly as it was: a season of ten does not need navigating, and
 * wrapping it in machinery would make the common case worse to serve the rare one.
 *
 * All of it is plain arithmetic over episode numbers, with no Android or Compose types, so the TV
 * and mobile apps can hold the same behaviour and both can test it without a device.
 */

/** Seasons longer than this get range navigation. At or below it, nothing changes. */
const val EPISODE_RANGE_THRESHOLD = 20

/** How many episodes one range holds. */
const val EPISODE_RANGE_SIZE = 20

/**
 * One block of a long season.
 *
 * [label] is built from the episode numbers actually present rather than from the block's position,
 * so a season that starts at episode 0, skips numbers, or carries specials still describes itself
 * honestly - "0-19" and "45-64" are both things a real season produces, and a label of "1-20" over
 * a block containing neither would be a lie the viewer has to work around.
 */
data class EpisodeRange(
  val label: String,
  val firstEpisodeNumber: Int,
  val lastEpisodeNumber: Int,
  /** Index into the season's episode list, inclusive. */
  val fromIndex: Int,
  /** Index into the season's episode list, inclusive. */
  val toIndex: Int,
) {
  val size: Int get() = toIndex - fromIndex + 1
}

/**
 * Cuts a season into navigable blocks, or returns nothing if it does not need cutting.
 *
 * An empty result is the signal to leave the season alone, which is what keeps the change invisible
 * for the ordinary case: callers render ranges when there are ranges and the plain strip when there
 * are not, rather than each deciding the threshold for itself.
 *
 * Blocks are cut by position in the list, not by arithmetic on the numbers, so a season missing
 * episode 7 still produces even blocks instead of one short one.
 */
fun buildEpisodeRanges(
  episodeNumbers: List<Int>,
  rangeSize: Int = EPISODE_RANGE_SIZE,
  threshold: Int = EPISODE_RANGE_THRESHOLD,
): List<EpisodeRange> {
  if (rangeSize < 1) return emptyList()
  if (episodeNumbers.size <= threshold) return emptyList()
  return episodeNumbers.indices.chunked(rangeSize).map { indices ->
    val from = indices.first()
    val to = indices.last()
    val first = episodeNumbers[from]
    val last = episodeNumbers[to]
    EpisodeRange(
      // An en dash, and no space around it: this sits in a chip that has to stay narrow enough for
      // several to be on screen at once.
      label = if (first == last) "$first" else "$first–$last",
      firstEpisodeNumber = first,
      lastEpisodeNumber = last,
      fromIndex = from,
      toIndex = to,
    )
  }
}

/**
 * Which block holds [episodeNumber], or 0 when nothing does.
 *
 * Falling back to the first block rather than to -1 because every caller wants a block to show, and
 * an out-of-range answer would only be turned into the first one anyway.
 */
fun episodeRangeIndexFor(ranges: List<EpisodeRange>, episodeNumber: Int?): Int {
  if (ranges.isEmpty() || episodeNumber == null) return 0
  val index = ranges.indexOfFirst { episodeNumber >= it.firstEpisodeNumber && episodeNumber <= it.lastEpisodeNumber }
  return if (index >= 0) index else 0
}

/**
 * The episode a season should open on.
 *
 * In priority order: whatever the viewer already had selected, then whatever they are part-way
 * through, then the first one they have not watched, and failing all of that the first episode.
 * The point is that opening a long season lands on the part of it the viewer is actually in, so the
 * common case - carry on where I was - costs no navigation at all.
 *
 * [inProgressEpisodeNumber] is resolved by the caller rather than worked out here: which of several
 * part-watched episodes is "the" one depends on timestamps that live with the progress records, and
 * this stays arithmetic over numbers.
 */
fun focusEpisodeNumber(
  episodeNumbers: List<Int>,
  selectedEpisodeNumber: Int? = null,
  inProgressEpisodeNumber: Int? = null,
  watchedEpisodeNumbers: Set<Int> = emptySet(),
): Int? {
  if (episodeNumbers.isEmpty()) return null
  selectedEpisodeNumber?.takeIf { it in episodeNumbers }?.let { return it }
  inProgressEpisodeNumber?.takeIf { it in episodeNumbers }?.let { return it }
  episodeNumbers.firstOrNull { it !in watchedEpisodeNumbers }?.let { return it }
  return episodeNumbers.first()
}

/**
 * The first episode the viewer has not watched, for the "Next Up" marker and jump target.
 *
 * Null when the season is finished, which is the honest answer: a season with nothing left to watch
 * has no next episode, and pointing at the last one again would be worse than pointing at nothing.
 */
fun nextUnwatchedEpisodeNumber(
  episodeNumbers: List<Int>,
  watchedEpisodeNumbers: Set<Int>,
): Int? = episodeNumbers.firstOrNull { it !in watchedEpisodeNumbers }

/**
 * Where a jump should land, given whatever the viewer typed.
 *
 * Accepts an exact episode number and otherwise gives the nearest one that exists, so typing 7 into
 * a season that skips 7, or 500 into a season of 200, still goes somewhere sensible instead of
 * rejecting the input. Null only when there is nothing to jump to at all.
 */
fun resolveJumpTarget(episodeNumbers: List<Int>, requested: Int?): Int? {
  if (episodeNumbers.isEmpty() || requested == null) return null
  if (requested in episodeNumbers) return requested
  return episodeNumbers.minByOrNull { kotlin.math.abs(it - requested) }
}
