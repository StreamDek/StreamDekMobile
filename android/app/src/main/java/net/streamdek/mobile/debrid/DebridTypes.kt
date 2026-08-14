package net.streamdek.mobile.debrid

/**
 * On-device premium service (debrid) support.
 *
 * These are ports of StreamDek's server-side providers, and the contract is deliberately identical
 * — same call sequence, same status mapping, same failure codes — so the two paths behave the same
 * and either can serve a given account. What differs is who makes the request: with "stream
 * directly" set, the phone talks to Real-Debrid or TorBox itself, using a key held on the device,
 * so the provider sees the viewer's own address rather than one shared server one.
 */

/** One file inside a torrent held by a provider. */
data class DebridTorrentFile(val id: String, val name: String, val size: Long)

enum class DebridTorrentStatus { Cached, Downloading, Queued, Error }

data class DebridTorrentInfo(
  val id: String,
  val hash: String,
  val name: String,
  val status: DebridTorrentStatus,
  val progress: Int,
  val files: List<DebridTorrentFile>,
)

data class DebridStreamLink(
  val url: String,
  val filename: String,
  val filesize: Long,
  val mimeType: String? = null,
)

data class DebridValidation(val valid: Boolean, val username: String? = null, val premium: Boolean? = null)

/**
 * Why one provider could not serve a source.
 *
 * Mirrors the server's DebridFailure codes so the messages a viewer sees do not change with the
 * path that produced them.
 */
enum class DebridFailureCode {
  SubscriptionRequired,
  UnsupportedHost,
  AccessDenied,
  RateLimited,
  Timeout,
  UpstreamError,
  NotConfigured,
  /** Accepted the magnet and is fetching it, just not inside the window we wait. Not a fault. */
  Downloading,
  Unknown,
}

data class DebridFailure(val provider: String, val code: DebridFailureCode, val message: String)

/**
 * The provider took the magnet but had not finished fetching it in time.
 *
 * Typed so it is distinguishable from an actual failure: this is the ordinary uncached path, and
 * reporting it as a dead source is what made "try again in a few minutes" look like "this is
 * broken".
 */
class DebridNotReadyException(message: String) : IllegalStateException(message)

/** Raised for an HTTP status a provider answered with, so the classifier can read it. */
class DebridHttpException(val statusCode: Int, message: String) : IllegalStateException(message)

/** Every provider implements this; DebridManager is the only thing that talks to them. */
interface DebridProviderClient {
  val name: String

  suspend fun validate(): DebridValidation

  /**
   * Which of [infoHashes] this provider already holds, as lowercase hash -> cached.
   *
   * [names] maps a hash to what the release is called. Only Deepbrid reads it — it exposes no
   * info-hash anywhere in its API, so a hash on its own is a question it cannot answer.
   */
  suspend fun checkCache(infoHashes: List<String>, names: Map<String, String> = emptyMap()): Map<String, Boolean>

  suspend fun addMagnet(magnetLink: String): String

  suspend fun getTorrentInfo(torrentId: String): DebridTorrentInfo

  suspend fun getStreamLinks(torrentId: String, fileIds: List<String> = emptyList()): List<DebridStreamLink>

  suspend fun unrestrictLink(url: String): DebridStreamLink
}

/** Providers the app can talk to, and what to call them. Mirrors SUPPORTED_DEBRID_PROVIDERS. */
internal val SUPPORTED_DEBRID_PROVIDERS = listOf(
  "real-debrid" to "Real-Debrid",
  "alldebrid" to "AllDebrid",
  "premiumize" to "Premiumize",
  "torbox" to "TorBox",
  "debrid-link" to "Debrid-Link",
  "deepbrid" to "Deepbrid",
)
