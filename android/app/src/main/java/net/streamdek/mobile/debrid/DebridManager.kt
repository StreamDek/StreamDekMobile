package net.streamdek.mobile.debrid

import android.content.Context
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private const val TAG = "StreamDekDebrid"

/** How long a provider is left alone after it answers "too many requests". */
private const val RATE_LIMIT_COOLDOWN_MS = 60_000L

/** A resolved source, and which service produced it. */
data class DebridResolvedStream(val provider: String, val link: DebridStreamLink)

data class DebridResolution(val stream: DebridResolvedStream?, val failures: List<DebridFailure>)

/**
 * The device's own premium services, asked in the account holder's chosen order.
 *
 * This is the on-device half of "stream directly": rather than posting an info-hash to StreamDek
 * and having a server contact Real-Debrid on the viewer's behalf, the phone holds the keys and
 * makes those calls itself. The order, the cooldowns, the file picking and the failure codes all
 * match the server-side manager, so an account switched between the two modes behaves the same.
 */
class DebridManager private constructor(private val providers: List<DebridProviderClient>) {

  companion object {
    /**
     * Cooldowns are process-wide rather than per-manager: a rate limit belongs to the API key,
     * and a manager rebuilt on the next screen would otherwise walk straight back into it.
     */
    private val cooldownUntil = mutableMapOf<String, Long>()

    /** Builds from what this device has stored, honouring priority and skipping disabled ones. */
    fun fromStoredKeys(context: Context): DebridManager {
      val clients = DebridKeyStore.load(context)
        .filter { it.enabled && it.apiKey.isNotBlank() }
        .sortedBy { it.priority }
        .mapNotNull { stored ->
          if (stored.provider == "real-debrid" && stored.refreshToken != null) {
            RealDebridClient(stored.apiKey) { renewRealDebrid(context, stored) }
          } else {
            build(stored.provider, stored.apiKey)
          }
        }
      return DebridManager(clients)
    }

    /**
     * Renews a Real-Debrid token and writes it back, so the next launch starts with a live one.
     *
     * Persisting matters as much as renewing: a token refreshed only in memory would be renewed
     * again on every cold start, and the refresh token Real-Debrid hands back each time would be
     * thrown away with it.
     */
    private suspend fun renewRealDebrid(context: Context, stored: DebridKeyStore.StoredKey): String? {
      val clientId = stored.oauthClientId ?: return null
      val clientSecret = stored.oauthClientSecret ?: return null
      val refreshToken = stored.refreshToken ?: return null
      val renewed = runCatching {
        RealDebridDeviceAuth.refresh(clientId, clientSecret, refreshToken)
      }.getOrElse {
        Log.w(TAG, "Real-Debrid token could not be renewed: ${it.message}")
        return null
      }
      val updated = DebridKeyStore.load(context).map { key ->
        if (key.provider != "real-debrid") key
        else key.copy(apiKey = renewed.accessToken, refreshToken = renewed.refreshToken)
      }
      DebridKeyStore.save(context, updated)
      return renewed.accessToken
    }

    fun build(provider: String, apiKey: String): DebridProviderClient? = when (provider) {
      "real-debrid" -> RealDebridClient(apiKey)
      "alldebrid" -> AllDebridClient(apiKey)
      "premiumize" -> PremiumizeClient(apiKey)
      "torbox" -> TorBoxClient(apiKey)
      "debrid-link" -> DebridLinkClient(apiKey)
      "deepbrid" -> DeepbridClient(apiKey)
      else -> null
    }

    @Synchronized
    private fun isCoolingDown(provider: String): Boolean =
      (cooldownUntil[provider] ?: 0L) > System.currentTimeMillis()

    @Synchronized
    private fun markRateLimited(provider: String) {
      cooldownUntil[provider] = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
    }
  }

  val hasProviders: Boolean get() = providers.isNotEmpty()

  val providerNames: List<String> get() = providers.map { it.name }

  /**
   * Every provider asked at once for the whole list of hashes, merged into hash -> provider names.
   *
   * Sorted by the account holder's priority so the first entry is the service that would actually
   * serve it — the badge and the stream ordering both read that.
   */
  suspend fun checkCacheAll(
    infoHashes: List<String>,
    names: Map<String, String> = emptyMap(),
  ): Map<String, List<String>> {
    if (infoHashes.isEmpty() || !hasProviders) return emptyMap()

    val answers = coroutineScope {
      providers.map { provider ->
        async {
          provider.name to runCatching { provider.checkCache(infoHashes, names) }.getOrElse { emptyMap() }
        }
      }.awaitAll()
    }

    val merged = mutableMapOf<String, MutableList<String>>()
    infoHashes.forEach { hash -> merged[hash.lowercase()] = mutableListOf() }
    answers.forEach { (providerName, result) ->
      result.forEach { (hash, cached) ->
        if (cached) merged[hash.lowercase()]?.add(providerName)
      }
    }

    val order = providerNames
    return merged.mapValues { (_, list) -> list.sortedBy { order.indexOf(it) } }.filterValues { it.isNotEmpty() }
  }

  /** The first provider, in priority order, that already holds this hash. */
  private suspend fun bestCachedProvider(infoHash: String, filename: String?): DebridProviderClient? {
    // Passed straight through: a provider matching on hash ignores it, and the one that can only
    // match on name is otherwise forced to answer "not cached" and lose its turn at the front.
    val names = filename?.let { mapOf(infoHash.lowercase() to it) }.orEmpty()
    for (provider in providers) {
      if (isCoolingDown(provider.name)) continue
      val cached = runCatching { provider.checkCache(listOf(infoHash), names) }.getOrNull() ?: continue
      if (cached[infoHash.lowercase()] == true) return provider
    }
    return null
  }

  /**
   * Turns an info-hash into something playable, trying the cached provider first and then the
   * rest in order.
   *
   * @param filename the add-on's filename hint, used to pick the right file out of an episode pack
   */
  suspend fun resolve(infoHash: String, magnetLink: String, filename: String?): DebridResolution {
    if (!hasProviders) {
      return DebridResolution(
        stream = null,
        failures = listOf(DebridFailure("none", DebridFailureCode.NotConfigured, "No premium services connected")),
      )
    }

    val preferred = bestCachedProvider(infoHash, filename)
    val ordered = listOfNotNull(preferred) + providers.filterNot { it === preferred }
    val failures = mutableListOf<DebridFailure>()

    for (provider in ordered) {
      if (isCoolingDown(provider.name)) {
        failures += DebridFailure(
          provider.name,
          DebridFailureCode.RateLimited,
          "Provider is temporarily cooling down after a rate limit response",
        )
        continue
      }

      try {
        val torrentId = provider.addMagnet(magnetLink)
        val links = provider.getStreamLinks(torrentId)
        if (links.isEmpty()) {
          // The magnet was accepted, so the provider is fetching it on its own servers — it just
          // did not finish inside the readiness window. Reporting that as a plain failure was
          // actively misleading: the viewer saw "could not be resolved" while a transfer they did
          // not ask for was starting on their account.
          failures += DebridFailure(provider.name, DebridFailureCode.Downloading, DOWNLOADING_MESSAGE)
          continue
        }

        val best = pickBestLink(links, filename)
        return DebridResolution(DebridResolvedStream(provider.name, best), failures)
      } catch (error: DebridNotReadyException) {
        failures += DebridFailure(provider.name, DebridFailureCode.Downloading, DOWNLOADING_MESSAGE)
      } catch (error: Throwable) {
        val failure = debridFailureFor(provider.name, error)
        if (failure.code == DebridFailureCode.RateLimited) markRateLimited(provider.name)
        Log.w(TAG, "resolve via ${provider.name} failed: ${error.message}")
        failures += failure
      }
    }

    return DebridResolution(stream = null, failures = failures)
  }

  /** Unrestricts a premium hoster URL with the highest-priority provider that can. */
  suspend fun unrestrict(url: String): DebridResolution {
    val failures = mutableListOf<DebridFailure>()
    for (provider in providers) {
      try {
        return DebridResolution(DebridResolvedStream(provider.name, provider.unrestrictLink(url)), failures)
      } catch (error: Throwable) {
        failures += debridFailureFor(provider.name, error)
      }
    }
    return DebridResolution(stream = null, failures = failures)
  }
}

internal const val DOWNLOADING_MESSAGE =
  "Not cached yet — your debrid service has started downloading it. Try this source again in a few minutes."

private val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".mov", ".m4v", ".ts", ".wmv")

internal fun isVideoFile(filename: String): Boolean =
  VIDEO_EXTENSIONS.any { filename.lowercase().endsWith(it) }

/**
 * Which of a torrent's files is the one that was asked for.
 *
 * An exact filename match first, then a match on the name without its extension, and only then the
 * largest video. The fallbacks matter for episode packs, where every file is a plausible video and
 * only the name says which episode; the largest-file rule alone would hand back whichever episode
 * happened to be biggest.
 */
internal fun pickBestLink(links: List<DebridStreamLink>, filename: String?): DebridStreamLink {
  val videoLinks = links.filter { isVideoFile(it.filename) }

  if (!filename.isNullOrBlank()) {
    videoLinks.firstOrNull { it.filename.equals(filename, ignoreCase = true) }?.let { return it }
    val stem = filename.substringBeforeLast('.').lowercase()
    if (stem.length > 4) {
      videoLinks.firstOrNull { it.filename.lowercase().contains(stem) }?.let { return it }
    }
  }

  return videoLinks.maxByOrNull { it.filesize } ?: links.first()
}

/**
 * Reads a provider's error and decides what to tell the viewer.
 *
 * The wording of these messages is load-bearing: providers report a dead subscription, an
 * unsupported host and a rate limit in prose rather than in a status code, so the text is what
 * distinguishes "renew your subscription" from "try again shortly".
 */
internal fun debridFailureFor(provider: String, error: Throwable): DebridFailure {
  val message = error.message ?: "Unknown Debrid error"
  val upper = message.uppercase()
  val status = (error as? DebridHttpException)?.statusCode

  val code = when {
    status == 429 || upper.contains("RATE LIMIT") || upper.contains("TOO MANY REQUESTS") -> DebridFailureCode.RateLimited
    status == 401 || status == 403 || upper.contains("ACCESS DENIED") || upper.contains("FORBIDDEN") -> DebridFailureCode.AccessDenied
    upper.contains("PREMIUM") || upper.contains("SUBSCRIPTION") || upper.contains("EXPIRED") -> DebridFailureCode.SubscriptionRequired
    upper.contains("UNSUPPORTED") || upper.contains("HOSTER") || upper.contains("HOST") -> DebridFailureCode.UnsupportedHost
    upper.contains("TIMEOUT") || upper.contains("TIMED OUT") -> DebridFailureCode.Timeout
    status != null && status >= 500 -> DebridFailureCode.UpstreamError
    else -> DebridFailureCode.Unknown
  }
  return DebridFailure(provider, code, message)
}
