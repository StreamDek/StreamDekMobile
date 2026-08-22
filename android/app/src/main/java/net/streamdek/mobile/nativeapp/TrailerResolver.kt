package net.streamdek.mobile.nativeapp

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

/**
 * Prefers IPv4 when a host offers both.
 *
 * The playback URLs YouTube hands back are tied to the address that asked for them, and a
 * dual-stack phone will happily resolve the player API over IPv6 and then reach the media host over
 * IPv4, or the reverse. The URL is then being used from an address it was not issued to, and the
 * answer is 403 — intermittently, depending on which way each connection happened to go.
 */
private val ipv4FirstDns = okhttp3.Dns { hostname ->
  okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
}

private val trailerHttpClient = OkHttpClient.Builder()
  .dns(ipv4FirstDns)
  .connectTimeout(4, TimeUnit.SECONDS)
  .readTimeout(5, TimeUnit.SECONDS)
  .callTimeout(8, TimeUnit.SECONDS)
  .build()
private val trailerJsonMediaType = "application/json; charset=utf-8".toMediaType()
private const val trailerResolverTag = "TrailerResolver"

data class TrailerPlaybackSource(
  val url: String,
  val audioUrl: String? = null,
  val height: Int? = null,
  val requestHeaders: Map<String, String> = emptyMap(),
  /** Where playback should begin. Only ever non-zero when [seekable]. */
  val startPositionMs: Long = 0L,
  /**
   * Whether this URL will serve a span that does not start at byte zero.
   *
   * The player reads these files through googlevideo's own `&range=` query parameter, and the
   * gated client's URLs answer 403 to any range that starts mid-file — so for those, playback can
   * only ever run from the beginning. The headset client's URLs have no such limit.
   */
  val seekable: Boolean = true,
  /**
   * How much of the tail to leave unplayed.
   *
   * KinoCheck close their uploads with a channel outro. Stopping short of it ends the hero on the
   * trailer's own last frame rather than on a card asking the viewer to subscribe.
   */
  val endTrimMs: Long = 0L,
)
data class TrailerPlaybackResolution(val source: TrailerPlaybackSource? = null, val youtubeLoginRequired: Boolean = false)

/** How many of a title's videos are worth looking at. Beyond this the list is archive material. */
private const val TRAILER_CANDIDATE_LIMIT = 14

/** Metadata probes are small, but a dozen at once on a phone is not worth the contention. */
private val trailerProbeGate = Semaphore(6)

/**
 * Resolves a playable source for a title's trailer.
 *
 * [alternates] are the title's other videos, in the order the metadata service returned them.
 * They matter because that order is not the useful one: it is roughly newest first, which puts the
 * promotional run — "Now Playing" stings, ticket-sale spots, ASMR cutdowns — ahead of the actual
 * trailer. Picking the first entry is what made a title open on a fifteen-second theatre notice.
 * See [pickBestTrailerCandidate] for how the real trailer is identified.
 */
suspend fun resolveTrailerPlaybackSource(
  url: String,
  maxHeight: Int = 720,
  youtubeCookies: String? = null,
  alternates: List<String> = emptyList(),
  /**
   * A trailer somebody has already identified as the right one — today, KinoCheck's pick.
   *
   * The last thing tried, not the first. The ranked search over [url] and [alternates] reads each
   * candidate's running time and picks the real trailer out of the adverts, and that judgement is
   * better than a third party's for the titles it can see — it is working from the videos the
   * studio actually published. KinoCheck is the answer for the titles it cannot: nothing readable
   * in the list, every candidate walled, or no candidates at all.
   *
   * It is still kept out of the ranking rather than thrown in with the others. A curated answer
   * should not have to win a competition scored on running time; it either gets used whole, sting
   * trimmed, or not at all.
   */
  preferredUrl: String? = null,
  /**
   * Whether a Short may be played when it is the only thing a title has.
   *
   * True here, on a handset. The television passes false: a vertical sixty-second clip stretched
   * across a living-room screen reads as a broken trailer rather than a short one.
   */
  allowShortForm: Boolean = true,
): TrailerPlaybackResolution = withContext(Dispatchers.IO) {
  withTimeoutOrNull(20_000) {
    val trimmed = url.trim()
    val preferredKey = preferredUrl?.trim()?.takeIf { it.isNotBlank() }?.let(::extractYoutubeTrailerKey)
    val candidateKeys = (listOfNotNull(extractYoutubeTrailerKey(trimmed)) + alternates.mapNotNull(::extractYoutubeTrailerKey))
      .distinct()
      .take(TRAILER_CANDIDATE_LIMIT)
    val cap = normalizeTrailerMaxHeight(maxHeight)
    var loginRequired = false

    /** The curated pick, with KinoCheck's own branded sting trimmed off where the URL allows it. */
    suspend fun attemptPreferred(clients: List<YoutubeClient>): TrailerPlaybackResolution? {
      val key = preferredKey ?: return null
      val resolved = resolveTrailerCandidates(listOf(key), cap, youtubeCookies, clients)
      loginRequired = loginRequired || resolved.youtubeLoginRequired
      val source = resolved.source ?: return null
      // The player reads these files through googlevideo's `&range=` query parameter, and the
      // capped client refuses any span that does not start at byte zero — asking it to start part
      // way in does not skip the sting, it kills the trailer outright. The headset client's URLs
      // have no such limit, so the skip applies there and is simply not attempted on the other.
      // (An earlier measurement suggested deep offsets were fine everywhere; it used HTTP Range
      // headers, which is not the mechanism the player uses.)
      return if (source.seekable) {
        resolved.copy(source = source.copy(startPositionMs = KINOCHECK_START_MS, endTrimMs = KINOCHECK_END_TRIM_MS))
      } else {
        resolved
      }
    }

    // A trailer served as a plain file needs none of this, and is unambiguous — no ranking, no
    // client ladder, no third party. First, because it is both the cheapest and the most certain.
    if (trimmed.isNotBlank() && isNativePlayableTrailerUrl(trimmed)) {
      return@withTimeoutOrNull TrailerPlaybackResolution(source = TrailerPlaybackSource(trimmed))
    }
    if (preferredKey == null && candidateKeys.isEmpty()) return@withTimeoutOrNull TrailerPlaybackResolution()

    // Then the ranked search, one client at a time all the way down. Running time is what tells a
    // two-minute trailer from a fifteen-second ticket advert, and no marketing language changes
    // that — see [trailerCandidateScore]. Each client gets a full pass over the candidates before
    // the next one is tried, so a title whose videos are all walled on the first client is still
    // ranked properly on the second rather than falling through on its first refusal.
    if (candidateKeys.isNotEmpty()) {
      trailerClientLadder.forEach { client ->
        val resolved = resolveTrailerCandidates(candidateKeys, cap, youtubeCookies, listOf(client), allowShortForm)
        loginRequired = loginRequired || resolved.youtubeLoginRequired
        if (resolved.source != null) return@withTimeoutOrNull resolved
      }
    }

    // KinoCheck last. By here the title's own videos could not be read or played on any client, so
    // a curated pick is the difference between a trailer and a blank hero. It gets the same
    // one-client-at-a-time treatment rather than being abandoned on a single refusal.
    trailerClientLadder.forEach { client ->
      attemptPreferred(listOf(client))?.let { return@withTimeoutOrNull it }
    }

    TrailerPlaybackResolution(youtubeLoginRequired = loginRequired)
  } ?: TrailerPlaybackResolution()
}

private suspend fun resolveTrailerCandidates(
  keys: List<String>,
  maxHeight: Int,
  cookies: String?,
  clients: List<YoutubeClient> = trailerClientLadder,
  allowShortForm: Boolean = true,
): TrailerPlaybackResolution {
  val session = youtubeSession(keys.first())
  // A single candidate used to skip all of this and play, which meant a title whose only video was
  // a Short played the Short without anything ever looking at it. One video still gets judged; it
  // just has nothing to be judged against.
  //
  // Which of the candidates is the trailer only has to be worked out once per title; after that it
  // is a single request for a fresh playback URL rather than a fan-out across all of them.
  cachedTrailerChoice(keys)?.let { return resolveYoutubePlaybackSource(it, maxHeight, cookies, session) }

  // One fan-out serves both jobs. The player response carries the title and running time the pick
  // is made on *and* the streaming data for playback, so identifying the right trailer costs
  // nothing extra once the winner is known — its response is already in hand.
  val probes = coroutineScope {
    keys.map { key ->
      async {
        // Probed with the same client that will serve the playback, so the response that decides
        // the pick is also the one played. Probing with a different client meant the winner arrived
        // with a URL from the gated one, which is how the client ordering below was bypassed.
        //
        // It is the pass's own client rather than a fixed one. Hard-coded to the headset client,
        // this fan-out returned nothing at all whenever that client was walled — no titles, no
        // running times — so [pickBestTrailerCandidate] had nothing to rank and fell back to the
        // metadata service's own order, which is roughly newest first. That is precisely the order
        // this whole mechanism exists to avoid, and it is why titles opened on ticket adverts.
        val probe = trailerProbeGate.withPermit { requestYoutubePlayer(key, session, clients.first(), maxHeight, cookies) }
        key to probe
      }
    }.awaitAll()
  }
  val best = pickBestTrailerCandidate(
    probes.map { (key, probe) -> TrailerCandidate(key, probe.title, probe.durationSeconds) },
    allowShortForm = allowShortForm,
  ) ?: run {
    // Nothing here is a trailer and short-form is not allowed to stand in. Reported as no result
    // so the caller moves on to the next client and, in the end, to the curated pick.
    Log.d(trailerResolverTag, "no full-length candidate among ${keys.size}: " + probes.joinToString { (key, probe) -> "$key(${probe.durationSeconds}s ${probe.title})" })
    return TrailerPlaybackResolution()
  }
  Log.d(trailerResolverTag, "picked $best from ${keys.size} candidates: " + probes.joinToString { (key, probe) -> "$key(${probe.durationSeconds}s ${probe.title})" })
  cacheTrailerChoice(keys, best)

  val chosen = probes.firstOrNull { (key, _) -> key == best }?.second
  chosen?.resolution?.source?.let { return TrailerPlaybackResolution(source = it) }
  // The chosen video needs the rest of the client ladder — age-restricted trailers land here.
  return resolveYoutubePlaybackSource(best, maxHeight, cookies, session, clients)
}

internal data class TrailerCandidate(val key: String, val title: String?, val durationSeconds: Int?)

/**
 * Which of a title's videos is actually its trailer.
 *
 * Running time is the honest signal and the title text only refines it. Studios label promotional
 * cutdowns exactly like trailers — "Now Playing", "Tickets on Sale Now", and the like appear on
 * genuine trailers too — but a theatre notice is fifteen seconds and a trailer is two minutes, and
 * no amount of marketing language changes that.
 */
/**
 * Whether a candidate is short-form rather than a trailer.
 *
 * Runtime is the signal, as everywhere else here: a minute is not enough to trail a film, whatever
 * the upload was labelled. The hashtag is checked too because a Short cut to exactly sixty seconds
 * is otherwise indistinguishable from a brief teaser by duration alone.
 *
 * An unknown runtime is not short-form. It is unknown, and punishing it would throw away perfectly
 * good trailers whose metadata the client could not read.
 */
internal fun isShortFormTrailerCandidate(title: String?, durationSeconds: Int?): Boolean {
  val name = title.orEmpty().lowercase()
  if (name.contains("#short") || name.contains("#reel")) return true
  return durationSeconds != null && durationSeconds < 60
}

/**
 * Which of a title's videos to play, or none of them.
 *
 * Short-form is held back rather than merely ranked low. Ranking alone still plays a Short when it
 * is the only thing on offer, because the best of a bad set still wins — which is exactly how a
 * hero came to open on a sixty-second vertical clip.
 *
 * @param allowShortForm what to do when short-form is all there is. True on a handset, where a
 *   clip is better than a still frame and the whole thing is the size of a postcard anyway. False
 *   on a television, where a vertical Short blown up across a wall looks broken — there the search
 *   gives up and lets the curated fallback answer instead.
 */
internal fun pickBestTrailerCandidate(
  candidates: List<TrailerCandidate>,
  allowShortForm: Boolean = true,
): String? {
  val fullLength = candidates.filterNot { isShortFormTrailerCandidate(it.title, it.durationSeconds) }
  val pool = when {
    fullLength.isNotEmpty() -> fullLength
    allowShortForm -> candidates
    else -> return null
  }
  return pool.maxWithOrNull(
    compareBy<TrailerCandidate> { trailerCandidateScore(it.title, it.durationSeconds) }
      // Between two real trailers, the longer one is the fuller cut.
      .thenBy { it.durationSeconds ?: 0 },
  )?.key
}

internal fun trailerCandidateScore(title: String?, durationSeconds: Int?): Int {
  val name = title.orEmpty().lowercase()
  var score = when {
    durationSeconds == null -> 0 // Unknown: neither trusted nor punished.
    durationSeconds < 45 -> -100 // Theatre stings, ticket spots, social cutdowns.
    durationSeconds < 75 -> 10
    durationSeconds <= 240 -> 40 // Where an actual trailer lands.
    durationSeconds <= 420 -> 5
    else -> -40 // Featurettes, full scenes, whole panels.
  }
  score += when {
    name.contains("official trailer") -> 30
    name.contains("trailer") -> 20
    name.contains("teaser") -> 10
    else -> 0
  }
  // These name a different kind of video outright, rather than describing a trailer's release.
  val otherFormat = listOf("featurette", "behind the scenes", "bloopers", "blooper", "interview", "tv spot", "opening scene", "first 10 minutes")
  if (otherFormat.any { name.contains(it) }) score -= 35
  // A Short that calls itself a trailer is still a Short. Without this the +20 for the word puts
  // a sixty-second vertical clip above a two-minute trailer that happens not to say "official".
  if (isShortFormTrailerCandidate(title, durationSeconds)) score -= 60
  return score
}

/**
 * Which video is a title's trailer, once worked out.
 *
 * This caches the *decision*, not the media URLs. The expensive half of resolving is the fan-out
 * that reads every candidate's running time to find the real trailer among the promos; that answer
 * is a property of the title and never goes stale, so it is worth keeping. The playback URLs are
 * the opposite: googlevideo hands out short-lived, single-use links, and an earlier version of this
 * cache handed a stored one back to the player on a second viewing, which answered 403 and dropped
 * the trailer to the broken iframe fallback. Those are re-fetched every time, which costs one
 * request and is always valid.
 */
private val chosenTrailerCandidates = LinkedHashMap<String, String>()
private const val CHOSEN_TRAILER_CACHE_SIZE = 48

private fun trailerCacheKey(keys: List<String>): String = keys.joinToString(",")

@Synchronized
private fun cachedTrailerChoice(keys: List<String>): String? = chosenTrailerCandidates[trailerCacheKey(keys)]

/**
 * Forgets which video was chosen for each title.
 *
 * Called when trailer state is cleared. The decision itself does not go stale, but a decision
 * reached while the pipeline was failing can be a fallback rather than the real trailer — and a
 * viewer who has just cleared the cache to fix trailers should not have to restart the app to get
 * past a choice made during the broken period.
 */
@Synchronized
internal fun resetTrailerResolverMemory() {
  chosenTrailerCandidates.clear()
}

@Synchronized
private fun cacheTrailerChoice(keys: List<String>, chosen: String) {
  if (chosenTrailerCandidates.size >= CHOSEN_TRAILER_CACHE_SIZE) {
    chosenTrailerCandidates.keys.firstOrNull()?.let(chosenTrailerCandidates::remove)
  }
  chosenTrailerCandidates[trailerCacheKey(keys)] = chosen
}

internal fun normalizeTrailerMaxHeight(maxHeight: Int): Int = maxHeight.coerceIn(360, 2160)

private fun isNativePlayableTrailerUrl(url: String): Boolean {
  val lower = url.lowercase()
  return lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".webm") || lower.contains(".m3u8") || lower.contains(".mpd")
}

private fun extractYoutubeTrailerKey(url: String): String? {
  val raw = url.trim()
  if (raw.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return raw
  return runCatching {
    val uri = Uri.parse(raw)
    when {
      uri.host?.contains("youtu.be", ignoreCase = true) == true -> uri.lastPathSegment
      uri.host?.contains("youtube", ignoreCase = true) == true && uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1)
      uri.host?.contains("youtube", ignoreCase = true) == true && uri.path?.startsWith("/embed/") == true -> uri.pathSegments.getOrNull(1)
      uri.host?.contains("youtube", ignoreCase = true) == true -> uri.getQueryParameter("v")
      else -> null
    }
  }.getOrNull()?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{11}$")) }
}

// Client selection matters: WEB/ANDROID clients are gated behind YouTube's proof-of-origin (PO)
// token and return the "confirm you're not a bot" wall even with valid sign-in cookies, so they are
// intentionally excluded.
//
// IOS is tried first because at the time of writing it is the only one of the three that answers at
// all — ANDROID_VR and TVHTML5 both come back "Sign in to confirm you're not a bot" anonymously.
// The other two stay in the list because which client YouTube is currently serving moves around,
// and an unattended fallback is the difference between a missing trailer and a working one.
//
// Note that the media URLs IOS hands back have to be fetched in bounded spans; see
// ChunkedGoogleVideoDataSource. Requesting one of them the ordinary way answers 403.
/**
 * The headset client, and the one that actually works.
 *
 * Every field here is load-bearing, which is why it looks over-specified. The previous version of
 * this client differed in small ways — `osVersion` of "12L" rather than "12", no `platform`, a
 * user agent missing the locale and device fields — and YouTube answered it with the
 * "Sign in to confirm you're not a bot" wall. Corrected, it answers OK anonymously.
 *
 * It is first because it is the only client whose media URLs are served in full. The IOS client
 * below returns a playable-looking response whose URLs stop at roughly 8 MiB, which is what made
 * trailers play for half a minute and then die mid-scene.
 */
private val androidVrClient = YoutubeClient(
  "ANDROID_VR",
  "1.56.21",
  osName = "Android",
  osVersion = "12",
  deviceMake = "Oculus",
  deviceModel = "Quest 3",
  userAgent = "com.google.android.apps.youtube.vr.oculus/1.56.21 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1) gzip",
  androidSdkVersion = 32,
  clientId = "28",
  platform = "MOBILE",
)

/**
 * The client that currently answers when the headset one is refused, and the reason trailers play
 * at all on a bot-walled network.
 *
 * Its URLs used to be gated a few megabytes in, which is what [SERVABLE_TRAILER_BYTES] is for. That
 * gate has lifted — see the constant — so the budget is now a wide safety margin rather than the
 * thing deciding picture quality.
 */
private val iosClient = YoutubeClient("IOS", "20.10.4", osName = "iOS", osVersion = "18.3.2.22D82", deviceMake = "Apple", deviceModel = "iPhone16,2", userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)", clientId = "5", servableBytes = SERVABLE_TRAILER_BYTES)

/**
 * The clients, in the order a trailer is attempted through them.
 *
 * Both are anonymous. TVHTML5 used to be in here, and when a YouTube cookie existed it was tried
 * ahead of IOS. That position is worse than useless: it answers `status=OK` and hands back a 360p
 * URL that then answers 403 when the player fetches it, so resolution reported success, playback
 * died a few seconds later, and the client that would have worked was never reached. The cookie
 * that triggered it is one this app creates itself — the iframe fallback runs in a WebView with
 * cookies enabled — so using that fallback once poisoned every trailer after it.
 *
 * Measured against the live service on 21 Aug 2026, over three trailers, sending the same
 * `visitorData` this resolver scrapes: ANDROID_VR answered `LOGIN_REQUIRED` — "sign in to confirm
 * you're not a bot" — every single time, while IOS answered OK every time with 1080p renditions
 * and the title and running time the candidate ranking needs. TVHTML5 was walled too, and WEB
 * reported the video simply unavailable.
 *
 * That wall is a proof-of-origin check rather than an identity one, so signing in does not answer
 * it; only a client YouTube does not gate does. Hence IOS first. ANDROID_VR stays behind it rather
 * than being deleted: which client is gated moves around, and when it does answer it is the better
 * of the two — its URLs are uncapped and seekable, which is what the KinoCheck sting-skip needs.
 */
private val trailerClientLadder = listOf(iosClient, androidVrClient)

private fun resolveYoutubePlaybackSource(
  videoId: String,
  maxHeight: Int,
  cookies: String?,
  session: YoutubeSession,
  clients: List<YoutubeClient> = trailerClientLadder,
): TrailerPlaybackResolution {
  var loginRequired = false
  for (client in clients) {
    val probe = requestYoutubePlayer(videoId, session, client, maxHeight, cookies)
    probe.resolution.source?.let { return probe.resolution }
    loginRequired = loginRequired || probe.resolution.youtubeLoginRequired
  }
  return TrailerPlaybackResolution(youtubeLoginRequired = loginRequired)
}

/**
 * The API key and visitor id the player endpoint is called with.
 *
 * Scraping these means downloading a watch page, which is around 650 KB — far and away the most
 * expensive part of resolving a trailer, and it was being paid again for every single title. The
 * values are not per-video, so one fetch is held for the life of the process and every later
 * trailer starts straight at the player request. The built-in key is a working fallback, so a
 * failed or slow fetch costs nothing but the visitor id.
 */
internal data class YoutubeSession(val apiKey: String, val visitorData: String?)

private const val youtubeFallbackApiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

@Volatile private var cachedYoutubeSession: YoutubeSession? = null

@Synchronized
private fun youtubeSession(videoId: String): YoutubeSession {
  cachedYoutubeSession?.let { return it }
  // Fetched without the viewer's cookies on purpose. The visitor id scraped from a signed-in watch
  // page belongs to that account, and presenting it on an otherwise anonymous client request is a
  // mismatch YouTube reads as a bot — which is exactly what happened: the headset client answered
  // "Sign in to confirm you're not a bot" on a signed-in device while the identical request
  // succeeded from one that had never logged in. Trailers need no account at all.
  val watchHtml = fetchYoutubeWatchHtml(videoId, cookies = null)
  val session = YoutubeSession(
    apiKey = Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""").find(watchHtml)?.groupValues?.getOrNull(1) ?: youtubeFallbackApiKey,
    visitorData = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""").find(watchHtml)?.groupValues?.getOrNull(1),
  )
  // Only hold on to it once the page actually answered — otherwise a single failure while the
  // network was down would pin the fallback key for the rest of the session.
  if (watchHtml.isNotBlank()) cachedYoutubeSession = session
  return session
}

private fun fetchYoutubeWatchHtml(videoId: String, cookies: String?): String = runCatching {
  val builder = Request.Builder()
    .url("https://www.youtube.com/watch?v=$videoId&hl=en")
    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
  if (!cookies.isNullOrBlank()) builder.header("Cookie", cookies)
  val request = builder.build()
  trailerHttpClient.newCall(request).execute().use { response -> if (response.isSuccessful) response.body?.string().orEmpty() else "" }
}.getOrDefault("")

private data class YoutubeClient(
  val name: String,
  val version: String,
  val osName: String,
  val osVersion: String,
  val deviceMake: String,
  val deviceModel: String,
  val userAgent: String,
  val androidSdkVersion: Int? = null,
  /** YouTube's numeric id for the client, sent as `X-YouTube-Client-Name`. */
  val clientId: String? = null,
  val platform: String? = null,
  /**
   * How many bytes of this client's media URLs can be fetched, or null for no limit.
   *
   * Only the IOS client is capped; see [SERVABLE_TRAILER_BYTES].
   */
  val servableBytes: Long? = null,
)

private const val youtubeOrigin = "https://www.youtube.com"

private fun youtubeAuthorizationHeader(cookies: String): String? {
  val cookieValues = cookies.split(';').mapNotNull { entry ->
    val separator = entry.indexOf('=')
    if (separator <= 0) null else entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
  }.toMap()
  val sapisid = cookieValues["SAPISID"]
    ?: cookieValues["__Secure-3PAPISID"]
    ?: cookieValues["__Secure-1PAPISID"]
    ?: return null
  val timestamp = System.currentTimeMillis() / 1000L
  val input = "$timestamp $sapisid $youtubeOrigin"
  val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
  return "SAPISIDHASH ${timestamp}_$digest"
}
/** A player response, read both for playback and for deciding whether this video is the trailer. */
internal data class YoutubePlayerProbe(
  val resolution: TrailerPlaybackResolution = TrailerPlaybackResolution(),
  val title: String? = null,
  val durationSeconds: Int? = null,
)

private fun requestYoutubePlayer(videoId: String, session: YoutubeSession, client: YoutubeClient, maxHeight: Int, cookies: String?): YoutubePlayerProbe {
  val apiKey = session.apiKey
  val visitorData = session.visitorData
  val clientJson = JSONObject()
    .put("clientName", client.name)
    .put("clientVersion", client.version)
    .put("osName", client.osName)
    .put("osVersion", client.osVersion)
    .put("deviceMake", client.deviceMake)
    .put("deviceModel", client.deviceModel)
    .put("userAgent", client.userAgent)
    .put("hl", "en")
    .put("gl", "US")
  client.androidSdkVersion?.let { clientJson.put("androidSdkVersion", it) }
  client.platform?.let { clientJson.put("platform", it) }
  if (!visitorData.isNullOrBlank()) clientJson.put("visitorData", visitorData)
  val payload = JSONObject()
    .put("videoId", videoId)
    .put("contentCheckOk", true)
    .put("racyCheckOk", true)
    .put("playbackContext", JSONObject().put("contentPlaybackContext", JSONObject().put("html5Preference", "HTML5_PREF_WANTS")))
    .put("context", JSONObject().put("client", clientJson))

  val requestBuilder = Request.Builder()
    .url("https://www.youtube.com/youtubei/v1/player?key=${Uri.encode(apiKey)}")
    .post(payload.toString().toRequestBody(trailerJsonMediaType))
    .header("User-Agent", client.userAgent)
    .header("Accept", "application/json")
    .header("Accept-Language", "en-US,en;q=0.9")
  // Identifying the client in the headers as well as the payload is part of what keeps these
  // requests off the bot wall; a mismatch between the two is exactly what a scraper looks like.
  client.clientId?.let { requestBuilder.header("X-YouTube-Client-Name", it) }
  requestBuilder.header("X-YouTube-Client-Version", client.version)
  requestBuilder.header("Origin", youtubeOrigin)
  if (!visitorData.isNullOrBlank()) requestBuilder.header("X-Goog-Visitor-Id", visitorData)
  if (!cookies.isNullOrBlank() && (client.name == "WEB" || client.name == "TVHTML5")) {
    requestBuilder.header("Origin", youtubeOrigin)
    requestBuilder.header("X-Origin", youtubeOrigin)
    requestBuilder.header("Cookie", cookies)
    youtubeAuthorizationHeader(cookies)?.let { requestBuilder.header("Authorization", it) }
    requestBuilder.header("X-Goog-AuthUser", "0")
    requestBuilder.header("X-YouTube-Client-Name", if (client.name == "TVHTML5") "7" else "1")
    requestBuilder.header("X-YouTube-Client-Version", client.version)
  }
  val request = requestBuilder.build()

  return runCatching {
    trailerHttpClient.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        Log.w(trailerResolverTag, "${client.name}: HTTP ${response.code}")
        return@use YoutubePlayerProbe()
      }
      val json = JSONObject(body)
      val playability = json.optJSONObject("playabilityStatus")
      val streamingData = json.optJSONObject("streamingData")
      val videoDetails = json.optJSONObject("videoDetails")
      val title = videoDetails?.optString("title")?.ifBlank { null }
      val durationSeconds = videoDetails?.optString("lengthSeconds")?.toIntOrNull()
      if (streamingData == null) {
        val status = playability?.optString("status").orEmpty()
        val reason = playability?.optString("reason").orEmpty()
        val loginRequired = status.equals("LOGIN_REQUIRED", ignoreCase = true) || reason.contains("sign in", ignoreCase = true) || reason.contains("not a bot", ignoreCase = true)
        Log.w(trailerResolverTag, "${client.name}: status=$status reason=$reason")
        return@use YoutubePlayerProbe(TrailerPlaybackResolution(youtubeLoginRequired = loginRequired), title, durationSeconds)
      }
      val formats = streamingData.optJSONArray("formats")
      val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
      Log.d(trailerResolverTag, "${client.name}: status=${playability?.optString("status")} formats=${formats?.length() ?: 0} adaptive=${adaptiveFormats?.length() ?: 0} hls=${streamingData.optString("hlsManifestUrl").isNotBlank()}")
      val playbackHeaders = buildMap {
        put("User-Agent", client.userAgent)
        put("Referer", "$youtubeOrigin/")
      }
      val hlsManifest = streamingData.optString("hlsManifestUrl")
      if (maxHeight > 360 && hlsManifest.isNotBlank()) {
        return@use YoutubePlayerProbe(
          TrailerPlaybackResolution(source = TrailerPlaybackSource(hlsManifest, height = maxHeight, requestHeaders = playbackHeaders)),
          title,
          durationSeconds,
        )
      }
      // Only a client whose URLs stop partway needs its rendition sized to fit; the rest are free
      // to take the best picture available.
      val adaptiveVideo = selectAdaptiveVideo(adaptiveFormats, maxHeight, client.servableBytes ?: Long.MAX_VALUE)
      val adaptiveAudio = selectAdaptiveAudio(adaptiveFormats)
      val progressive = selectProgressiveTrailer(formats, maxHeight)
      val adaptivePair = if (adaptiveVideo != null && adaptiveAudio != null) {
        TrailerPlaybackSource(adaptiveVideo.first, adaptiveAudio, adaptiveVideo.second)
      } else {
        null
      }
      // Whichever is actually taller, rather than a fixed preference. A muxed progressive stream is
      // simpler to play, but the only one YouTube still publishes is 360p — and preferring it on
      // sight meant a client offering 1080p adaptive was answered with 360p, ignoring the viewer's
      // quality setting entirely.
      val source = listOfNotNull(progressive, adaptivePair).maxByOrNull { it.height ?: 0 }
        ?: hlsManifest.takeIf { it.isNotBlank() }?.let { TrailerPlaybackSource(it) }
      // Says which rendition actually won, so "is this really playing in 4K" is answerable from a
      // log line rather than by guessing at which decoder the device happened to spin up.
      Log.d(
        trailerResolverTag,
        "${client.name}: selected height=${source?.height ?: -1} cap=$maxHeight " +
          "bestAdaptive=${adaptiveVideo?.second ?: -1} separateAudio=${source?.audioUrl != null}",
      )
      YoutubePlayerProbe(
        TrailerPlaybackResolution(
          // Only the capped client's URLs refuse a mid-file span, and `servableBytes` is exactly
          // the flag for "this client's URLs are gated" — so it answers both questions.
          source = source?.copy(requestHeaders = playbackHeaders, seekable = client.servableBytes == null),
        ),
        title,
        durationSeconds,
      )
    }
  }.onFailure { Log.w(trailerResolverTag, "${client.name}: ${it.message}") }.getOrElse { YoutubePlayerProbe() }
}
internal fun selectProgressiveTrailer(formats: JSONArray?, maxHeight: Int): TrailerPlaybackSource? {
  if (formats == null) return null
  var selected: TrailerPlaybackSource? = null
  var selectedHeight = -1
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    val url = item.optString("url")
    val mime = item.optString("mimeType")
    val height = item.optInt("height", 0)
    val hasAudio = item.optString("audioQuality").isNotBlank() || item.optInt("audioChannels", 0) > 0
    if (url.isBlank() || !hasAudio || !mime.contains("avc1", true) || height > maxHeight || height <= selectedHeight) continue
    selectedHeight = height
    selected = TrailerPlaybackSource(url, height = height)
  }
  return selected
}

/**
 * How much a codec is preferred at the *same* height. Higher wins.
 *
 * AVC decodes everywhere, so it stays the first choice whenever it can match the resolution.
 * It is also the reason this used to cap at 1080p: YouTube publishes nothing above that in AVC,
 * and accepting only avc1 silently threw away every 1440p and 2160p rendition the iOS client
 * offers. VP9 is hardware-decoded on essentially anything modern; AV1 is accepted last because
 * on mid-range hardware it can fall back to a software decoder.
 */
internal fun trailerCodecRank(mime: String): Int = when {
  mime.contains("avc1", true) -> 3
  mime.contains("vp9", true) || mime.contains("vp09", true) -> 2
  mime.contains("av01", true) -> 1
  else -> 0
}

/**
 * How much of one of these URLs googlevideo will actually serve.
 *
 * Applies to the IOS client alone, and it is still 7 MiB. Bounded requests inside the first few
 * megabytes are answered and everything past roughly 8 MiB is refused, which is why trailers taken
 * from that client used to play for half a minute and stop mid-scene.
 *
 * Raised to 64 MiB on 15 Aug 2026 on the strength of a measurement showing deep offsets being
 * served, and put back the same evening: that measurement used HTTP `Range` headers, and the player
 * does not read these URLs that way. ChunkedGoogleVideoDataSource asks through googlevideo's own
 * `&range=` query parameter, and through *that* the old ceiling is very much still there. The
 * larger budget let the selector choose a 1440p rendition, whose second chunk was refused, so every
 * trailer resolved through IOS died a few seconds in.
 *
 * It costs nothing when the headset client answers, since that one is uncapped — the budget is only
 * consulted for the client whose URLs are gated. Seven rather than eight because the exact ceiling
 * drifts between requests, and a trailer that stops early is worse than one that starts smaller.
 */
private const val SERVABLE_TRAILER_BYTES = 7L * 1024 * 1024

/**
 * Picks the best rendition that can actually be played from beginning to end.
 *
 * Resolution is no longer the first consideration, because the tallest rendition is routinely one
 * that cannot be finished: a two-minute trailer is about 20 MB at 1080p against a hard ceiling of
 * roughly 8 MB. So the file size decides what is eligible, and among the renditions that fit, the
 * tallest wins — which lands around 360–480p for a full trailer and higher for a short one.
 *
 * A rendition that does not declare its size is treated as eligible only if nothing else is: it is
 * better to try one than to show no trailer at all.
 */
internal fun selectAdaptiveVideo(formats: JSONArray?, maxHeight: Int, byteBudget: Long = SERVABLE_TRAILER_BYTES): Pair<String, Int>? {
  if (formats == null) return null
  var selected: Pair<String, Int>? = null
  var selectedRank = 0
  var selectedFits = false
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    val url = item.optString("url")
    val mime = item.optString("mimeType")
    val height = item.optInt("height", 0)
    if (url.isBlank() || !mime.startsWith("video/", true) || height !in 1..maxHeight) continue
    val rank = trailerCodecRank(mime)
    if (rank == 0) continue
    val contentLength = item.optString("contentLength").toLongOrNull()
    val fits = contentLength != null && contentLength <= byteBudget
    val current = selected
    val better = when {
      current == null -> true
      // Anything playable to the end beats anything that would cut out partway.
      fits != selectedFits -> fits
      height != current.second -> height > current.second
      else -> rank > selectedRank
    }
    if (!better) continue
    selected = url to height
    selectedRank = rank
    selectedFits = fits
  }
  return selected
}

/**
 * Best audio track to pair with the chosen video.
 *
 * m4a is preferred, but Opus in WebM is accepted as a fallback: now that video selection can pick
 * a VP9 rendition, a response whose only audio is WebM would otherwise leave the pair incomplete
 * and drop the whole result. The player merges the two streams regardless of container.
 */
internal fun selectAdaptiveAudio(formats: JSONArray?): String? {
  if (formats == null) return null
  var selectedUrl: String? = null
  var selectedBitrate = -1
  var selectedIsMp4 = false
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    val url = item.optString("url")
    val mime = item.optString("mimeType")
    val bitrate = item.optInt("bitrate", 0)
    if (url.isBlank() || !mime.startsWith("audio/", true)) continue
    val isMp4 = mime.contains("audio/mp4", true)
    if (!isMp4 && !mime.contains("audio/webm", true)) continue
    // An m4a track always beats a WebM one; past that, take the highest bitrate.
    val better = selectedUrl == null || (isMp4 && !selectedIsMp4) || (isMp4 == selectedIsMp4 && bitrate > selectedBitrate)
    if (!better) continue
    selectedUrl = url
    selectedBitrate = bitrate
    selectedIsMp4 = isMp4
  }
  return selectedUrl
}