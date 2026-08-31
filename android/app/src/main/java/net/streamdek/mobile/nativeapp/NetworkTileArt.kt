package net.streamdek.mobile.nativeapp

import net.streamdek.mobile.R

/**
 * Bundled branded artwork for the Streaming Networks row.
 *
 * The row's classic card is the service's TMDB logo fitted onto a white tile — correct, but it
 * reduces every service to the same white rectangle. These tiles are the service's own wordmark on
 * its own colour, so the row reads as a shelf of brands rather than a grid of stamps. They ship
 * with the app rather than coming down with the row because they are branding, not content: they
 * do not change between deploys, and a tile that has to be fetched is a tile that is blank on a
 * cold start.
 *
 * The classic card carries bundled artwork too — see [networkLogoArt]. Its logo used to be the
 * one `/tmdb/networks` supplies, which is a thumbnail sized for a provider list and visibly soft
 * on a card this size.
 *
 * Only the artwork on hand is mapped. A service with neither a tile nor a logo falls back to the
 * one the row supplied (see `NetworkHomeCard`), so these tables growing or shrinking can never
 * empty a card.
 */

/**
 * Watch-provider ids, which are what `/tmdb/networks` puts in a tile's `id`.
 *
 * Every id here is one the backend already asks for by name in `DEFAULT_NETWORKS` — headline ids
 * and the `altIds` a service is carried under elsewhere (Paramount+ is split into tiers in the US,
 * Starz is Lionsgate+ outside it). Ids are matched before names because a provider id is stable
 * across regions and renames, and the name is not.
 */
private val tilesByProviderId: Map<String, Int> = mapOf(
  "8" to R.drawable.network_tile_netflix,
  "9" to R.drawable.network_tile_prime_video,
  "350" to R.drawable.network_tile_apple_tv,
  "1899" to R.drawable.network_tile_hbo_max,
  "531" to R.drawable.network_tile_paramount,
  "2303" to R.drawable.network_tile_paramount,
  "2616" to R.drawable.network_tile_paramount,
  "15" to R.drawable.network_tile_hulu,
  "337" to R.drawable.network_tile_disney,
  "386" to R.drawable.network_tile_peacock,
  "387" to R.drawable.network_tile_peacock,
  "526" to R.drawable.network_tile_amc,
  "80" to R.drawable.network_tile_amc,
  "528" to R.drawable.network_tile_amc,
  "43" to R.drawable.network_tile_starz,
  "2358" to R.drawable.network_tile_starz,
  "151" to R.drawable.network_tile_britbox,
  "197" to R.drawable.network_tile_britbox,
  "283" to R.drawable.network_tile_crunchyroll,
)

/**
 * Service names, flattened to letters and digits, for tiles the row does not currently carry.
 *
 * The curated list on the backend is not the only source of a network card — a region's provider
 * list names services differently, and the list itself is edited without an app release. Matching
 * on the name means a tile that is already bundled lights up the day its service appears, rather
 * than waiting for its id to be added here.
 */
private val tilesByName: Map<String, Int> = mapOf(
  "netflix" to R.drawable.network_tile_netflix,
  "netflixkids" to R.drawable.network_tile_netflix_kids,
  "primevideo" to R.drawable.network_tile_prime_video,
  "amazonprimevideo" to R.drawable.network_tile_prime_video,
  "amazonvideo" to R.drawable.network_tile_prime_video,
  "appletv" to R.drawable.network_tile_apple_tv,
  "appletvplus" to R.drawable.network_tile_apple_tv,
  "hbomax" to R.drawable.network_tile_hbo_max,
  "max" to R.drawable.network_tile_hbo_max,
  "paramount" to R.drawable.network_tile_paramount,
  "paramountplus" to R.drawable.network_tile_paramount,
  "hulu" to R.drawable.network_tile_hulu,
  "disney" to R.drawable.network_tile_disney,
  "disneyplus" to R.drawable.network_tile_disney,
  "peacock" to R.drawable.network_tile_peacock,
  "amc" to R.drawable.network_tile_amc,
  "amcplus" to R.drawable.network_tile_amc,
  "starz" to R.drawable.network_tile_starz,
  "lionsgateplus" to R.drawable.network_tile_starz,
  "britbox" to R.drawable.network_tile_britbox,
  "crunchyroll" to R.drawable.network_tile_crunchyroll,
  "acorntv" to R.drawable.network_tile_acorn_tv,
  "bbciplayer" to R.drawable.network_tile_bbc_iplayer,
  "crave" to R.drawable.network_tile_crave,
  "curiosity" to R.drawable.network_tile_curiosity,
  "curiositystream" to R.drawable.network_tile_curiosity,
  "discovery" to R.drawable.network_tile_discovery,
  "discoveryplus" to R.drawable.network_tile_discovery,
  "hayu" to R.drawable.network_tile_hayu,
  "mgm" to R.drawable.network_tile_mgm,
  "mgmplus" to R.drawable.network_tile_mgm,
  "plex" to R.drawable.network_tile_plex,
  "plutotv" to R.drawable.network_tile_pluto_tv,
  "shudder" to R.drawable.network_tile_shudder,
  "skyshowtime" to R.drawable.network_tile_skyshowtime,
  "skygo" to R.drawable.network_tile_sky_go,
  "channel4" to R.drawable.network_tile_channel_4,
  "all4" to R.drawable.network_tile_channel_4,
  "channel5" to R.drawable.network_tile_channel_5,
  "my5" to R.drawable.network_tile_channel_5,
  "itvx" to R.drawable.network_tile_itvx,
  "itvplayer" to R.drawable.network_tile_itvx,
  "youtube" to R.drawable.network_tile_youtube,
  "youtubepremium" to R.drawable.network_tile_youtube,
)


/**
 * Transparent service logos for the classic card, by watch-provider id.
 *
 * Covers every service in the backend's `DEFAULT_NETWORKS` — including HBO and NOW, which have no
 * branded tile, so the branded row draws those two as classic cards with a bundled logo rather
 * than a fetched one.
 */
private val logosByProviderId: Map<String, Int> = mapOf(
  "8" to R.drawable.network_logo_netflix,
  "9" to R.drawable.network_logo_prime_video,
  "350" to R.drawable.network_logo_apple_tv,
  "1899" to R.drawable.network_logo_hbo_max,
  "49" to R.drawable.network_logo_hbo,
  "531" to R.drawable.network_logo_paramount,
  "2303" to R.drawable.network_logo_paramount,
  "2616" to R.drawable.network_logo_paramount,
  "15" to R.drawable.network_logo_hulu,
  "337" to R.drawable.network_logo_disney,
  "386" to R.drawable.network_logo_peacock,
  "387" to R.drawable.network_logo_peacock,
  "526" to R.drawable.network_logo_amc,
  "80" to R.drawable.network_logo_amc,
  "528" to R.drawable.network_logo_amc,
  "39" to R.drawable.network_logo_now,
  "591" to R.drawable.network_logo_now,
  "43" to R.drawable.network_logo_starz,
  "2358" to R.drawable.network_logo_starz,
  "151" to R.drawable.network_logo_britbox,
  "197" to R.drawable.network_logo_britbox,
  "283" to R.drawable.network_logo_crunchyroll,
)

/** The same logos by flattened service name, for the reasons given above [tilesByName]. */
private val logosByName: Map<String, Int> = mapOf(
  "netflix" to R.drawable.network_logo_netflix,
  "primevideo" to R.drawable.network_logo_prime_video,
  "amazonprimevideo" to R.drawable.network_logo_prime_video,
  "amazonvideo" to R.drawable.network_logo_prime_video,
  "appletv" to R.drawable.network_logo_apple_tv,
  "appletvplus" to R.drawable.network_logo_apple_tv,
  "hbomax" to R.drawable.network_logo_hbo_max,
  "max" to R.drawable.network_logo_hbo_max,
  "hbo" to R.drawable.network_logo_hbo,
  "paramount" to R.drawable.network_logo_paramount,
  "paramountplus" to R.drawable.network_logo_paramount,
  "hulu" to R.drawable.network_logo_hulu,
  "disney" to R.drawable.network_logo_disney,
  "disneyplus" to R.drawable.network_logo_disney,
  "peacock" to R.drawable.network_logo_peacock,
  "amc" to R.drawable.network_logo_amc,
  "amcplus" to R.drawable.network_logo_amc,
  "now" to R.drawable.network_logo_now,
  "nowtv" to R.drawable.network_logo_now,
  "starz" to R.drawable.network_logo_starz,
  "lionsgateplus" to R.drawable.network_logo_starz,
  "britbox" to R.drawable.network_logo_britbox,
  "crunchyroll" to R.drawable.network_logo_crunchyroll,
)

/** Lowercase letters and digits only, so "Apple TV+" and "apple tv plus" are not two services. */
internal fun networkTileKey(name: String): String =
  name.lowercase().filter { it.isLetterOrDigit() }

/**
 * The bundled tile for a network card, or null when none ships for it.
 *
 * Null is a normal answer, not a failure: the card falls back to the logo the row supplied, which
 * is what every card used before these tiles existed.
 */
internal fun networkTileArt(id: String, title: String): Int? =
  tilesByProviderId[id.trim()] ?: tilesByName[networkTileKey(title)]

internal fun networkTileArt(item: MediaItem): Int? = networkTileArt(item.id, item.title)

/**
 * The bundled transparent logo for a network card, or null when none ships for it.
 *
 * Drawn on the classic card's white tile, in place of the logo `/tmdb/networks` hands over — that
 * one is a provider-list thumbnail, and it shows at card size.
 */
internal fun networkLogoArt(id: String, title: String): Int? =
  logosByProviderId[id.trim()] ?: logosByName[networkTileKey(title)]

internal fun networkLogoArt(item: MediaItem): Int? = networkLogoArt(item.id, item.title)
