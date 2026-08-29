package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The identity rule behind Continue Watching removals sticking.
 *
 * These mirror `continueWatchingRemoval.test.ts` on the backend and `MediaIdentityTest` on the
 * television case for case. The three implementations have to agree, because a removal is recorded
 * by one and judged by the others; when they disagreed — the phone writing a dismissal against the
 * spelling its card carried, the provider returning the same film under another — an explicitly
 * removed title came back on the next refresh. Dune and Obsession are only the names on the
 * fixtures; nothing here is specific to them.
 */
class ContinueWatchingRemovalTest {
  private val duneTmdb = 438631
  private val duneImdb = "tt1160419"
  private val obsessionTmdb = 1064213

  @Test
  fun `the same film written three different ways is one title`() {
    val fromCatalogueCard = mediaIdentityOf("movie", duneTmdb.toString())
    val fromPrefixedAddon = mediaIdentityOf("movie", "tmdb:$duneTmdb")
    val fromCinemetaAddon = mediaIdentityOf("movie", duneImdb)
    val fromProvider = mediaIdentityOf("movie", "", duneTmdb, duneImdb)

    assertTrue(sameMediaIdentity(fromCatalogueCard, fromPrefixedAddon))
    assertTrue(sameMediaIdentity(fromCinemetaAddon, fromProvider))
    assertTrue(sameMediaIdentity(fromCatalogueCard, fromProvider))
  }

  @Test
  fun `an id belonging to another service is not read as a TMDB id`() {
    assertFalse(
      sameMediaIdentity(
        mediaIdentityOf("movie", "trakt:$duneTmdb"),
        mediaIdentityOf("movie", duneTmdb.toString()),
      ),
    )
  }

  @Test
  fun `a film and a series sharing a number are different titles`() {
    assertFalse(sameMediaIdentity(mediaIdentityOf("movie", "1399"), mediaIdentityOf("tv", "1399")))
  }

  @Test
  fun `series aliases canonicalise to one type`() {
    assertEquals(
      listOf("tv", "tv", "tv"),
      listOf("tv", "series", "show").map(::canonicalMediaIdentityType),
    )
    assertEquals("movie", canonicalMediaIdentityType("movie"))
  }

  @Test
  fun `an add-on slug matches itself and nothing numeric`() {
    val slug = mediaIdentityOf("movie", "punchplay-obsession-2026")
    assertEquals(listOf("movie:raw:punchplay-obsession-2026"), slug.keys())
    assertFalse(sameMediaIdentity(slug, mediaIdentityOf("movie", "2026")))
  }

  @Test
  fun `an imdb id embedded in a compound add-on id is still found`() {
    assertTrue(
      sameMediaIdentity(
        mediaIdentityOf("tv", "$duneImdb:1:2"),
        mediaIdentityOf("tv", "", imdbId = duneImdb),
      ),
    )
  }

  // ── Scenario A/B — the removal suppresses the provider row whatever it is called ──────────────

  @Test
  fun `a removal recorded against an imdb id suppresses the tmdb-numbered provider row`() {
    // The exact shape of the reported failure: the card the viewer pressed carried the add-on's
    // IMDb id, and Trakt returns the same film numbered by TMDB.
    val dismissal = dismissal(entityId = duneImdb, tmdbId = duneTmdb, imdbId = duneImdb)
    val providerRow = providerItem(id = duneTmdb.toString())

    assertTrue(progressRecordSuppressesProviderItem(dismissal, providerRow))
  }

  @Test
  fun `a removal recorded against a bare tmdb id suppresses the prefixed row`() {
    assertTrue(
      progressRecordSuppressesProviderItem(
        dismissal(entityId = obsessionTmdb.toString()),
        providerItem(id = "tmdb:$obsessionTmdb"),
      ),
    )
  }

  @Test
  fun `a different film is left alone`() {
    assertFalse(
      progressRecordSuppressesProviderItem(
        dismissal(entityId = duneTmdb.toString()),
        providerItem(id = obsessionTmdb.toString()),
      ),
    )
  }

  @Test
  fun `an in-progress record suppresses nothing`() {
    val stillWatching = dismissal(entityId = duneTmdb.toString()).copy(dismissed = false)

    assertFalse(
      progressRecordSuppressesProviderItem(stillWatching, providerItem(id = duneTmdb.toString())),
    )
  }

  // ── Episodes ─────────────────────────────────────────────────────────────────────────────────

  @Test
  fun `removing a whole series hides every episode of it`() {
    val seriesRemoval = dismissal(entityId = "1399", type = "tv")

    assertTrue(
      progressRecordSuppressesProviderItem(
        seriesRemoval,
        providerItem(id = "1399", type = "tv", season = 2, episode = 4),
      ),
    )
  }

  @Test
  fun `removing one episode leaves the next one alone`() {
    val episodeRemoval = dismissal(entityId = "1399", type = "tv", season = 2, episode = 4)

    assertTrue(
      progressRecordSuppressesProviderItem(
        episodeRemoval,
        providerItem(id = "1399", type = "tv", season = 2, episode = 4),
      ),
    )
    assertFalse(
      progressRecordSuppressesProviderItem(
        episodeRemoval,
        providerItem(id = "1399", type = "tv", season = 2, episode = 5),
      ),
    )
  }

  private fun dismissal(
    entityId: String,
    type: String = "movie",
    tmdbId: Int? = null,
    imdbId: String? = null,
    season: Int? = null,
    episode: Int? = null,
  ) = PlaybackProgressRecord(
    entityType = type,
    entityId = entityId,
    episodeKey = if (season != null && episode != null) "s%02de%02d".format(season, episode) else null,
    seasonNumber = season,
    episodeNumber = episode,
    title = "A Title",
    poster = null,
    backdrop = null,
    year = null,
    positionSec = 0.0,
    durationSec = 0.0,
    progress = 0.0,
    completed = false,
    dismissed = true,
    updatedAt = 0L,
    lastDevice = "StreamDek Mobile",
    lastPlatform = "mobile",
    tmdbId = tmdbId,
    imdbId = imdbId,
  )

  private fun providerItem(
    id: String,
    type: String = "movie",
    season: Int? = null,
    episode: Int? = null,
  ) = MediaItem(
    id = id,
    type = type,
    title = "A Title",
    year = null,
    poster = null,
    backdrop = null,
    rating = null,
    description = "",
    resumeSeasonNumber = season,
    resumeEpisodeNumber = episode,
  )
}
