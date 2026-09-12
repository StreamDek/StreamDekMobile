package net.streamdek.mobile.nativeapp

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicInteger
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

/**
 * What kind of thing a resolved URL is, so the player does not have to guess from the string.
 *
 * It used to guess, and YouTube gave it nothing to guess from: HLS manifests are served from
 * `manifest.googlevideo.com/api/manifest/...` with no `.m3u8` anywhere in them, so the sniffing had
 * to be taught each new shape by hand. The resolver already knows which branch produced the URL,
 * and stating it costs one field.
 */
enum class TrailerSourceKind {
  /** A manifest or variant playlist. Played through Media3's HLS source. */
  HLS,

  /** One file carrying both picture and sound. */
  PROGRESSIVE,

  /** Separate video and audio streams, merged at playback. [TrailerPlaybackSource.audioUrl] is set. */
  ADAPTIVE,
}

data class TrailerPlaybackSource(
  val url: String,
  val audioUrl: String? = null,
  val height: Int? = null,
  val kind: TrailerSourceKind = TrailerSourceKind.PROGRESSIVE,
  val requestHeaders: Map<String, String> = emptyMap(),
  /** Where playback should begin. Only ever non-zero when [seekable]. */
  val startPositionMs: Long = 0L,
  /**
   * Whether this URL will serve a span that does not start at byte zero.
   *
   * The player reads these files through googlevideo's own `&range=` query parameter, and a
   * range-capped client's URLs answer 403 to any range that starts mid-file — so for those,
   * playback can only ever run from the beginning. See [YoutubeClient.rangeCapped].
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
private const val KINOCHECK_PRIORITY_TIMEOUT_MS = 8_000L

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
   * The first choice. KinoCheck supplies one curated trailer for the title, while [url] and
   * [alternates] can contain the studio's full promotional run. The existing ranked metadata search
   * remains the fallback when KinoCheck has no answer or its chosen video cannot be resolved.
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
  val resolution = withTimeoutOrNull(20_000) {
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

    // KinoCheck first, with its own ceiling so a blocked YouTube client cannot consume the entire
    // resolve window and prevent the existing metadata path from acting as fallback.
    val preferredResolution = withTimeoutOrNull(KINOCHECK_PRIORITY_TIMEOUT_MS) {
      trailerClientLadder.forEach { client ->
        attemptPreferred(listOf(client))?.let { return@withTimeoutOrNull it }
      }
      null
    }
    if (preferredResolution != null) return@withTimeoutOrNull preferredResolution

    // A trailer served as a plain file needs no ranking or client ladder and is the first fallback.
    if (trimmed.isNotBlank() && isNativePlayableTrailerUrl(trimmed)) {
      return@withTimeoutOrNull TrailerPlaybackResolution(source = TrailerPlaybackSource(trimmed))
    }
    if (preferredKey == null && candidateKeys.isEmpty()) return@withTimeoutOrNull TrailerPlaybackResolution()

    // Then the existing ranked search, one client at a time all the way down. Running time tells a
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

    TrailerPlaybackResolution(youtubeLoginRequired = loginRequired)
  } ?: TrailerPlaybackResolution()

  // One last check that the URL about to be handed to the player is one this device can actually
  // fetch. See [reachableGoogleVideoUrl].
  val source = resolution.source ?: return@withContext resolution
  val reachable = reachableGoogleVideoUrl(source.url) ?: return@withContext run {
    Log.w(trailerResolverTag, "chosen source unreachable, reporting no trailer: ${source.kind}")
    TrailerPlaybackResolution(youtubeLoginRequired = resolution.youtubeLoginRequired)
  }
  val reachableAudio = source.audioUrl?.let { reachableGoogleVideoUrl(it) ?: it }
  resolution.copy(source = source.copy(url = reachable, audioUrl = reachableAudio))
}

/**
 * Confirms a googlevideo URL answers, moving it to a sibling CDN node if it does not.
 *
 * These URLs are issued against one edge server and the answer is not always yes: a node that is
 * shedding load, or that this network reaches badly, refuses the very first request and the trailer
 * dies before its first frame with nothing in the log but a Media3 source error. YouTube names the
 * alternates itself, in the `mn` parameter — two server names for the same file — and the host
 * carries one of them, so the other can be substituted and tried.
 *
 * Probed through the `&range=` query parameter rather than an HTTP `Range` header, because that is
 * how ChunkedGoogleVideoDataSource reads these files; a header probe answers for a request the
 * player will never make. The candidates are raced and the first to answer wins, under a short
 * ceiling — this sits in front of every trailer, so it has to be cheap or not be here at all.
 *
 * Non-googlevideo URLs, and HLS manifests, are returned untouched: nothing above serves them and
 * an HLS manifest's segments are fetched separately anyway.
 */
private suspend fun reachableGoogleVideoUrl(url: String): String? {
  if (!url.contains("googlevideo.com", ignoreCase = true)) return url
  val parameters = trailerQueryParameters(url)
  if (parameters["clen"] == null) return url // A manifest or a segment, not a whole-file media URL.
  val host = url.substringAfter("://", missingDelimiterValue = "").substringBefore('/')
  val alternates = parameters["mn"].orEmpty().split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .mapNotNull { server ->
      val swapped = host.replaceFirst(Regex("sn-[a-z0-9-]+"), server)
      url.replace(host, swapped).takeIf { swapped != host }
    }
  val candidates = (listOf(url) + alternates).distinct()

  val winner = withTimeoutOrNull(TRAILER_REACHABILITY_TIMEOUT_MS) {
    coroutineScope {
      // A genuine race rather than a walk down the list: awaiting each in turn would let the
      // slowest node set the pace even when a sibling had already answered. The deferred completes
      // on the first success, or with null once every probe has failed, so a URL that is simply
      // dead costs one round trip rather than the whole ceiling.
      val firstReachable = CompletableDeferred<String?>()
      val outstanding = AtomicInteger(candidates.size)
      candidates.forEach { candidate ->
        launch {
          if (servesOpeningBytes(candidate)) {
            firstReachable.complete(candidate)
          } else if (outstanding.decrementAndGet() == 0) {
            firstReachable.complete(null)
          }
        }
      }
      firstReachable.await()
    }
  }
  if (winner == null) Log.w(trailerResolverTag, "no reachable node among ${candidates.size} candidates")
  return winner
}

private const val TRAILER_REACHABILITY_TIMEOUT_MS = 2_500L

/**
 * A separate client for the probes, with its own short ceiling.
 *
 * The probes are raced, and a blocking OkHttp call does not stop when its coroutine is cancelled —
 * so the only thing that actually bounds a losing probe is its own timeout. Two seconds, safely
 * inside [TRAILER_REACHABILITY_TIMEOUT_MS], means a stuck node cannot hold the whole resolve open
 * after the race has already been won.
 */
private val trailerProbeHttpClient = trailerHttpClient.newBuilder()
  .connectTimeout(1500, TimeUnit.MILLISECONDS)
  .readTimeout(1500, TimeUnit.MILLISECONDS)
  .callTimeout(2, TimeUnit.SECONDS)
  .build()

/** Whether googlevideo will serve the first bytes of [url] the way the player will ask for them. */
private fun servesOpeningBytes(url: String): Boolean = runCatching {
  val separator = if ('?' in url) "&" else "?"
  val request = Request.Builder().url("$url${separator}range=0-1").build()
  trailerProbeHttpClient.newCall(request).execute().use { response -> response.isSuccessful }
}.getOrDefault(false)

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

// Client selection is the whole ball game. The WEB and TVHTML5 clients sit behind YouTube's
// proof-of-origin (PO) token check and answer the "confirm you're not a bot" wall even with valid
// sign-in cookies, so they are intentionally absent: that wall is about proving which player is
// asking, not who, and no amount of signing in answers it.
//
// Of the clients that do answer, the one that matters is whether its media URLs are *range-capped*.
// See [YoutubeClient.rangeCapped] — it is the difference between a 1080p trailer and a 360p one.

/**
 * The headset client, and the first that is tried.
 *
 * The reason it leads is [rangeCapped]: alone among the clients here, the URLs it hands back serve
 * a span anywhere in the file, so a two-minute 1080p trailer can be played to its last frame. Every
 * other client returns a response that looks identical and whose URLs refuse any request past the
 * first few megabytes, which is what forced [SERVABLE_TRAILER_BYTES] and, with it, 360p.
 *
 * Measured against the live service on 12 Sep 2026 across four videos: VISIONOS answered `OK` every
 * time, offered renditions to 2160p and an HLS manifest, and served a `&range=` request at the very
 * end of a 1.3 GB file. On the same videos and in the same session, IOS and ANDROID answered `OK`
 * with the same rendition list but answered 403 to the identical tail request, and ANDROID_VR
 * answered `LOGIN_REQUIRED`.
 *
 * Every field is load-bearing: the client identity, version, device model and user agent are all
 * checked against each other, and a mismatch between the payload and the headers is exactly what a
 * scraper looks like. Note the absence of `platform` — this client is refused when it is sent.
 */
private val visionOsClient = YoutubeClient(
  "VISIONOS",
  "1.02",
  osName = "visionOS",
  osVersion = "26.5.23O471",
  deviceMake = "Apple",
  deviceModel = "RealityDevice17,1",
  userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
  clientId = "101",
)

/**
 * The first fallback. Answers reliably; its URLs are capped, so quality drops when it is used.
 */
private val iosClient = YoutubeClient(
  "IOS",
  "20.10.4",
  osName = "iOS",
  osVersion = "18.3.2.22D82",
  deviceMake = "Apple",
  deviceModel = "iPhone16,2",
  userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
  clientId = "5",
  rangeCapped = true,
)

/**
 * The second fallback, and the only one that publishes a muxed progressive rendition.
 *
 * Capped like IOS, but it is worth having behind it: the two are walled independently, and a
 * progressive stream needs no merging, so on a device whose decoder struggles with two sources it
 * is the one that plays.
 */
private val androidClient = YoutubeClient(
  "ANDROID",
  "20.10.35",
  osName = "Android",
  osVersion = "14",
  deviceMake = "Google",
  deviceModel = "Pixel 8",
  userAgent = "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip",
  androidSdkVersion = 34,
  clientId = "3",
  platform = "MOBILE",
  rangeCapped = true,
)

/**
 * Last, and usually walled — but uncapped on the days it answers, so it is worth asking.
 *
 * Every field here is load-bearing too. An earlier version differed in small ways — `osVersion` of
 * "12L" rather than "12", no `platform`, a user agent missing the locale and device fields — and
 * YouTube answered it with the bot wall unconditionally. Corrected, it answers when it is not
 * being walled for other reasons.
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
 * The clients, in the order a trailer is attempted through them.
 *
 * All four are anonymous. TVHTML5 used to be in here, and when a YouTube cookie existed it was
 * tried first. That position is worse than useless: it answers `status=OK` and hands back a 360p
 * URL that then answers 403 when the player fetches it, so resolution reported success, playback
 * died a few seconds later, and the client that would have worked was never reached. The cookie
 * that triggered it is one this app creates itself — the iframe fallback runs in a WebView with
 * cookies enabled — so using that fallback once poisoned every trailer after it.
 *
 * The order is uncapped clients first, because a capped one produces a source that *looks* fine
 * and is quietly limited to whatever fits in [SERVABLE_TRAILER_BYTES]. Putting the capped IOS
 * client at the head is what held trailers at 360p: it answered, so nothing below it was ever
 * asked, and the budget then threw away every rendition above 360p as unfinishable.
 *
 * A ladder rather than a fan-out across all four. Pooling every client's formats would buy a
 * slightly wider choice at the cost of three extra round trips on every attempt — and this runs
 * once per candidate video while ranking a title's list, so it is three extra requests multiplied
 * by up to [TRAILER_CANDIDATE_LIMIT]. The first client answers almost always; paying for the other
 * three against that is not a trade worth making on a phone.
 */
private val trailerClientLadder = listOf(visionOsClient, iosClient, androidClient, androidVrClient)

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
   * Whether this client's media URLs refuse a span that does not start near the beginning.
   *
   * The single most consequential property of a client, and one nothing in the player response
   * announces — a capped client's `streamingData` is indistinguishable from an uncapped one's,
   * right down to the 2160p renditions it lists. Ask for a `&range=` near the end of one of its
   * files and the answer is 403.
   *
   * Two things follow. A capped client's renditions are held to [SERVABLE_TRAILER_BYTES], since
   * anything larger stops mid-scene; and its sources are marked not [TrailerPlaybackSource.seekable],
   * since a mid-file start is refused outright rather than merely being slow.
   */
  val rangeCapped: Boolean = false,
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
      // Only a client whose URLs stop partway needs its rendition sized to fit; the rest are free
      // to take the best picture available.
      val byteBudget = if (client.rangeCapped) SERVABLE_TRAILER_BYTES else Long.MAX_VALUE
      val adaptiveVideo = selectAdaptiveVideo(adaptiveFormats, maxHeight, byteBudget)
      val adaptiveAudio = selectAdaptiveAudio(adaptiveFormats)
      val progressive = selectProgressiveTrailer(formats, maxHeight)
      val adaptivePair = if (adaptiveVideo != null && adaptiveAudio != null) {
        TrailerPlaybackSource(
          url = adaptiveVideo.first,
          audioUrl = adaptiveAudio.url,
          height = adaptiveVideo.second,
          kind = TrailerSourceKind.ADAPTIVE,
        )
      } else {
        null
      }
      // HLS is judged rather than taken on sight.
      //
      // Previously any non-blank `hlsManifestUrl` short-circuited everything below it, on the
      // theory that HLS is the easy path. It is, but a master manifest states nothing about what is
      // inside it, so the resolution ceiling was applied by writing `maxHeight` into the result and
      // hoping — and a manifest whose variants topped out at 360p still beat a 1080p adaptive pair
      // that was right there. See [resolveHlsTrailerSource] for what reading it settles.
      val hls = resolveHlsTrailerSource(
        streamingData.optString("hlsManifestUrl"),
        maxHeight,
        hasMultipleAudioTracks(adaptiveFormats),
        playbackHeaders,
      )

      // Ranked on height first, then on kind. Height first because no preference between shapes is
      // worth a visibly softer picture — preferring progressive on sight is what answered a client
      // offering 1080p adaptive with its lone 360p muxed stream. Kind second, in the order
      // HLS, progressive, adaptive: at equal quality a single adaptive-bitrate stream is steadier
      // than a single file, and a single file is steadier than two streams merged at playback.
      val source = listOfNotNull(hls, progressive, adaptivePair)
        .maxWithOrNull(compareBy({ it.height ?: 0 }, { -trailerSourceKindRank(it.kind) }))
      // Says which rendition actually won, so "is this really playing in 4K" is answerable from a
      // log line rather than by guessing at which decoder the device happened to spin up.
      Log.d(
        trailerResolverTag,
        "${client.name}: selected kind=${source?.kind} height=${source?.height ?: -1} cap=$maxHeight " +
          "capped=${client.rangeCapped} bestAdaptive=${adaptiveVideo?.second ?: -1} " +
          "audioDefaultTrack=${adaptiveAudio?.isDefaultTrack} separateAudio=${source?.audioUrl != null}",
      )
      YoutubePlayerProbe(
        TrailerPlaybackResolution(
          source = source?.copy(requestHeaders = playbackHeaders, seekable = !client.rangeCapped),
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
    selected = TrailerPlaybackSource(url, height = height, kind = TrailerSourceKind.PROGRESSIVE)
  }
  return selected
}

/**
 * Which shape of source is preferred when two are the same height. Lower wins.
 *
 * Only ever a tiebreak — see the selection in [requestYoutubePlayer]. Nothing here is worth taking
 * a shorter rendition for.
 */
internal fun trailerSourceKindRank(kind: TrailerSourceKind): Int = when (kind) {
  TrailerSourceKind.HLS -> 0
  TrailerSourceKind.PROGRESSIVE -> 1
  TrailerSourceKind.ADAPTIVE -> 2
}

/**
 * The HLS manifest as a playable source, or null if HLS is not the right shape for this video.
 *
 * Two things are settled here, both of them measured against live manifests rather than assumed.
 *
 * **The master is what gets played, never a variant.** YouTube's manifests come from
 * `manifest.googlevideo.com/api/manifest/hls_variant/...`, and in them the audio is not inside the
 * video variants: it sits in separate `#EXT-X-MEDIA:TYPE=AUDIO` renditions that each
 * `#EXT-X-STREAM-INF` refers to by an `AUDIO="234"` group id. Resolving down to a variant playlist
 * and handing that over — the obvious thing to do, and what a naive reading of the manifest invites —
 * produces a trailer that plays in silence. So the master is passed through whole and Media3 wires
 * the renditions together; the manifest is read here only to learn what is actually inside it.
 *
 * **A video with dubs does not go down this path at all.** On a multi-language upload every
 * language is a rendition in the same audio group and *not one of them* is marked `DEFAULT=YES` —
 * measured on a trailer with eight: all eight said `DEFAULT=NO,AUTOSELECT=YES`. Media3 then falls
 * back to picking by device locale, and on a device whose language is not among them it takes
 * whichever rendition came first, which is alphabetical rather than original. The adaptive path has
 * an unambiguous answer for this — `audioTrack.audioIsDefault`, see [selectAdaptiveAudio] — so
 * multi-language videos are left to it.
 *
 * A failure to read the manifest is not a failure of the trailer: null drops HLS out of the running
 * and the progressive and adaptive candidates answer instead. That matters because this fetch is
 * the one part of resolution whose host differs from the player API's, and a network that blocks it
 * should not cost the viewer a trailer.
 */
internal fun resolveHlsTrailerSource(
  manifestUrl: String?,
  maxHeight: Int,
  hasMultipleAudioTracks: Boolean,
  playbackHeaders: Map<String, String>,
): TrailerPlaybackSource? {
  if (!isHlsTrailerCandidate(manifestUrl, hasMultipleAudioTracks)) return null
  val manifest = manifestUrl.orEmpty()
  val body = runCatching {
    val request = Request.Builder().url(manifest).apply {
      playbackHeaders.forEach { (name, value) -> header(name, value) }
    }.build()
    trailerHttpClient.newCall(request).execute().use { response ->
      if (response.isSuccessful) response.body?.string().orEmpty() else ""
    }
  }.onFailure { Log.w(trailerResolverTag, "hls manifest: ${it.message}") }.getOrDefault("")
  // The variant is found only to read its height, which is what lets HLS be compared honestly
  // against the other candidates instead of being assumed to be whatever the ceiling was.
  val height = pickHlsVariant(body, manifest, maxHeight)?.second
  if (height == null) {
    Log.d(trailerResolverTag, "hls manifest: unreadable, leaving HLS out of the running")
    return null
  }
  return TrailerPlaybackSource(manifest, height = height, kind = TrailerSourceKind.HLS)
}

/**
 * Whether HLS is worth reading for this video at all — the decision half of
 * [resolveHlsTrailerSource], kept apart from the fetching half so it can be tested directly.
 */
internal fun isHlsTrailerCandidate(manifestUrl: String?, hasMultipleAudioTracks: Boolean): Boolean =
  !manifestUrl.isNullOrBlank() && !hasMultipleAudioTracks

/**
 * Whether this response offers the same audio in more than one language.
 *
 * A dubbed upload gives every language its own `adaptiveFormats` entry carrying an `audioTrack`.
 * One track, or none at all, means there is no language to get wrong.
 */
internal fun hasMultipleAudioTracks(formats: JSONArray?): Boolean {
  if (formats == null) return false
  val tracks = HashSet<String>()
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    if (!item.optString("mimeType").startsWith("audio/", true)) continue
    val track = item.optJSONObject("audioTrack") ?: continue
    tracks += track.optString("id").ifBlank { track.optString("displayName") }
    if (tracks.size > 1) return true
  }
  return false
}

/**
 * The tallest variant at or below [maxHeight], as (url, height).
 *
 * Falls back to the *shortest* variant when every one of them exceeds the ceiling, rather than to
 * nothing: a viewer who set 360p and is offered only 720p should see a trailer.
 */
internal fun pickHlsVariant(manifestBody: String, manifestUrl: String, maxHeight: Int): Pair<String, Int>? {
  if (manifestBody.isBlank()) return null
  val lines = manifestBody.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
  var best: Pair<String, Int>? = null
  var bestBandwidth = -1L
  var smallest: Pair<String, Int>? = null
  for (index in lines.indices) {
    val line = lines[index]
    if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
    val target = lines.getOrNull(index + 1)?.takeIf { !it.startsWith("#") } ?: continue
    val attributes = parseHlsAttributes(line)
    val height = attributes["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull() ?: 0
    val bandwidth = attributes["BANDWIDTH"]?.toLongOrNull() ?: 0L
    val url = absolutizeHlsUrl(manifestUrl, target)
    if (smallest == null || height < smallest.second) smallest = url to height
    if (height > maxHeight) continue
    val better = best == null || height > best.second || (height == best.second && bandwidth > bestBandwidth)
    if (!better) continue
    best = url to height
    bestBandwidth = bandwidth
  }
  return best ?: smallest
}

/** `KEY=value` pairs off an `#EXT-X-` line, with quoted values kept whole so commas survive. */
internal fun parseHlsAttributes(line: String): Map<String, String> {
  val raw = line.substringAfter(':', missingDelimiterValue = "")
  if (raw.isEmpty()) return emptyMap()
  val attributes = LinkedHashMap<String, String>()
  val current = StringBuilder()
  var name: String? = null
  var quoted = false
  fun flush() {
    val key = name?.trim()
    if (!key.isNullOrEmpty()) attributes[key] = current.toString().trim()
    name = null
    current.setLength(0)
  }
  raw.forEach { ch ->
    when {
      ch == '"' -> quoted = !quoted
      ch == '=' && name == null && !quoted -> {
        name = current.toString()
        current.setLength(0)
      }
      ch == ',' && !quoted -> flush()
      else -> current.append(ch)
    }
  }
  flush()
  return attributes
}

private fun absolutizeHlsUrl(manifestUrl: String, target: String): String = when {
  target.startsWith("http://") || target.startsWith("https://") -> target
  target.startsWith("/") -> {
    val scheme = manifestUrl.substringBefore("://", missingDelimiterValue = "https")
    val host = manifestUrl.substringAfter("://", missingDelimiterValue = "").substringBefore('/')
    if (host.isNotEmpty()) "$scheme://$host$target" else target
  }
  else -> manifestUrl.substringBeforeLast('/', missingDelimiterValue = manifestUrl) + "/" + target
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
 * Applies only to the clients marked [YoutubeClient.rangeCapped], and it is still 7 MiB. Bounded
 * requests inside the first few megabytes are answered and everything past roughly 8 MiB is
 * refused, which is why trailers taken from those clients used to play for half a minute and stop
 * mid-scene.
 *
 * Raised to 64 MiB on 15 Aug 2026 on the strength of a measurement showing deep offsets being
 * served, and put back the same evening: that measurement used HTTP `Range` headers, and the player
 * does not read these URLs that way. ChunkedGoogleVideoDataSource asks through googlevideo's own
 * `&range=` query parameter, and through *that* the old ceiling is very much still there. The
 * larger budget let the selector choose a 1440p rendition, whose second chunk was refused, so every
 * trailer resolved through IOS died a few seconds in. Seven rather than eight because the exact
 * ceiling drifts between requests, and a trailer that stops early is worse than one that starts
 * smaller.
 *
 * This is a floor on quality, not a feature, and the reason the ladder leads with an uncapped
 * client: a 148-second trailer at 1080p is about 50 MB, so a capped client can offer nothing above
 * 360p for it. Whenever VISIONOS answers, this constant is not consulted at all.
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
  var selectedThrottled = false
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
    val throttled = isThrottledGoogleVideoUrl(url)
    val current = selected
    val better = when {
      current == null -> true
      // Anything playable to the end beats anything that would cut out partway.
      fits != selectedFits -> fits
      height != current.second -> height > current.second
      // An untouched URL beats a throttled one at the same height. See [isThrottledGoogleVideoUrl].
      throttled != selectedThrottled -> !throttled
      else -> rank > selectedRank
    }
    if (!better) continue
    selected = url to height
    selectedRank = rank
    selectedFits = fits
    selectedThrottled = throttled
  }
  return selected
}

/**
 * Whether googlevideo will rate-limit this URL until its `n` parameter is deciphered.
 *
 * YouTube puts a short ciphertext in `n` on some of the URLs it issues and throttles any request
 * carrying it to a fraction of real speed unless the value has been transformed by a function in
 * the player's own JavaScript. Running that JavaScript is not something this app does, so a URL
 * with an `n` is one that will very likely stall — and a stalled trailer looks broken rather than
 * slow.
 *
 * It is a tiebreak rather than a filter: the clients in use here mostly issue URLs without one,
 * and refusing every `n`-bearing URL outright would turn a probably-slow trailer into no trailer.
 */
internal fun isThrottledGoogleVideoUrl(url: String): Boolean =
  trailerQueryParameters(url)["n"]?.isNotBlank() == true

/**
 * The audio track chosen to pair with an adaptive video rendition.
 *
 * [isDefaultTrack] is carried out so it can be logged. Whether the right language was picked is
 * otherwise invisible until somebody watches a trailer and hears German.
 */
internal data class TrailerAudioChoice(
  val url: String,
  val isDefaultTrack: Boolean,
  val isMp4: Boolean,
  val bitrate: Int,
)

/**
 * Best audio track to pair with the chosen video.
 *
 * The original-language track wins above everything else. A major studio's trailer is uploaded with
 * its dubs attached — eight of them is ordinary — and each one appears as its own entry in
 * `adaptiveFormats`, distinguished only by an `audioTrack` object carrying `audioIsDefault`. Picking
 * purely on bitrate, as this used to, therefore picked a language at random: measured against a
 * live trailer with eight tracks, the German dub was encoded at 130557 bps and the English original
 * at 130515, so the dub won by 42 bps and the trailer played in German. An entry with no
 * `audioTrack` at all is the only audio the video has, so it counts as the default.
 *
 * Past language, m4a is preferred, but Opus in WebM is accepted as a fallback: now that video
 * selection can pick a VP9 rendition, a response whose only audio is WebM would otherwise leave the
 * pair incomplete and drop the whole result. The player merges the two streams regardless of
 * container.
 */
internal fun selectAdaptiveAudio(formats: JSONArray?): TrailerAudioChoice? {
  if (formats == null) return null
  var selected: TrailerAudioChoice? = null
  for (index in 0 until formats.length()) {
    val item = formats.optJSONObject(index) ?: continue
    val url = item.optString("url")
    val mime = item.optString("mimeType")
    val bitrate = item.optInt("bitrate", 0)
    if (url.isBlank() || !mime.startsWith("audio/", true)) continue
    val isMp4 = mime.contains("audio/mp4", true)
    if (!isMp4 && !mime.contains("audio/webm", true)) continue
    val audioTrack = item.optJSONObject("audioTrack")
    val isDefaultTrack = audioTrack == null || audioTrack.optBoolean("audioIsDefault", false)
    val current = selected
    val better = when {
      current == null -> true
      // The video's own language, whatever it costs in bitrate or container.
      isDefaultTrack != current.isDefaultTrack -> isDefaultTrack
      isMp4 != current.isMp4 -> isMp4
      else -> bitrate > current.bitrate
    }
    if (!better) continue
    selected = TrailerAudioChoice(url, isDefaultTrack, isMp4, bitrate)
  }
  return selected
}
