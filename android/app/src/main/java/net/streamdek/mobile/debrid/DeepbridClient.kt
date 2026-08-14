package net.streamdek.mobile.debrid

import kotlinx.coroutines.delay
import okhttp3.Request
import org.json.JSONObject

private const val BASE = "https://www.deepbrid.com/api/v1"
private const val TORRENT_LIST_TTL_MS = 15_000L

/**
 * Deepbrid.
 * Docs: https://www.deepbrid.com/api-docs
 *
 * Auth: Authorization: Bearer {apiKey}
 *
 * Three things about this API shape everything below, all of them verified against the live
 * service:
 *
 *  1. Failure arrives in the body of an HTTP 200 as a numeric `error` (0 = OK, 1 = no data,
 *     2 = not premium), so a request that "succeeded" still has to be inspected.
 *  2. POST bodies are form-encoded; a JSON body is ignored entirely.
 *  3. The torrent endpoints are premium-only. A free key validates perfectly and then fails every
 *     torrent call with `error: 2`.
 */
internal class DeepbridClient(private val apiKey: String) : DebridProviderClient {
  override val name = "deepbrid"

  private var torrentListCache: Pair<List<DeepbridTorrent>, Long>? = null

  private data class DeepbridTorrent(
    val id: String,
    val filename: String,
    val progress: Int,
    val links: List<String>,
  )

  private fun request(path: String, query: Map<String, String> = emptyMap()) =
    Request.Builder()
      .url(DebridHttp.url(BASE, path, query))
      .header("Authorization", "Bearer $apiKey")
      .header("Accept", "application/json")

  override suspend fun validate(): DebridValidation = runCatching {
    val data = DebridHttp.json(request("/user").get().build())
    if (data.optInt("error", 0) != 0) return DebridValidation(valid = false)
    val username = data.optString("username").ifBlank { data.optString("email") }.ifBlank { null }
      ?: return DebridValidation(valid = false)
    DebridValidation(
      valid = true,
      username = username,
      premium = data.optString("type").lowercase() == "premium",
    )
  }.getOrElse { DebridValidation(valid = false) }

  /**
   * Which of these are already sitting in the account's Deepbrid cloud.
   *
   * Deepbrid publishes no instant-availability endpoint and never exposes an info-hash — not on a
   * torrent it holds, not on one it just accepted — so a hash can only be answered if the caller
   * also says what it is *called*. Given a name, one request returns the whole cloud and the
   * answer is a name match against it. Without names everything is reported uncached, which is
   * honest rather than a guess.
   */
  override suspend fun checkCache(infoHashes: List<String>, names: Map<String, String>): Map<String, Boolean> {
    val answer = infoHashes.associate { hash -> hash.lowercase() to false }.toMutableMap()
    if (names.isEmpty()) return answer

    val wanted = answer.keys.filter { hash -> nameFor(names, hash) != null }
    if (wanted.isEmpty()) return answer

    val finished = listTorrents().filter { it.progress >= 100 && it.links.isNotEmpty() }
    if (finished.isEmpty()) return answer

    wanted.forEach { hash ->
      val name = nameFor(names, hash) ?: return@forEach
      answer[hash] = finished.any { torrent -> releaseNamesMatch(torrent.filename, name) }
    }
    return answer
  }

  /**
   * POST /torrents/add
   *
   * A torrent the account already holds is reused rather than added again. Deepbrid identifies
   * nothing by hash, so re-adding the same release was invisible and produced a duplicate cloud
   * entry plus a fresh wait every time a viewer replayed something.
   */
  override suspend fun addMagnet(magnetLink: String): String {
    magnetDisplayName(magnetLink)?.let { displayName ->
      listTorrents()
        .firstOrNull { it.progress >= 100 && it.links.isNotEmpty() && releaseNamesMatch(it.filename, displayName) }
        ?.let { return it.id }
    }

    val data = DebridHttp.json(
      request("/torrents/add").post(DebridHttp.form("magnet" to magnetLink)).build(),
    )
    assertOk(data, "failed to add magnet")
    val id = data.optString("id").ifBlank { data.optJSONObject("torrent")?.optString("id").orEmpty() }
    if (id.isBlank()) throw IllegalStateException("Deepbrid: torrent add returned no id")
    torrentListCache = null
    return id
  }

  /** GET /torrents/info?id=ID */
  override suspend fun getTorrentInfo(torrentId: String): DebridTorrentInfo {
    val data = DebridHttp.json(request("/torrents/info", mapOf("id" to torrentId)).get().build())
    assertOk(data, "torrent not found")
    return mapTorrentInfo(data, torrentId)
  }

  /**
   * Direct links for a finished torrent's files.
   *
   * The entries in `links` are the playable URLs, and are handed to the player as-is. They were
   * once exchanged through `/generate/link` on the theory that they were account-page handles, but
   * that endpoint only takes third-party hoster URLs and answers a Deepbrid one with
   * `{"error":10,"message":"Filehoster not supported"}` — so every Deepbrid resolution failed,
   * including torrents sitting fully cached in the account's own cloud. Requested directly, one of
   * these answers 302 to the media with no credentials and honours byte ranges.
   */
  override suspend fun getStreamLinks(torrentId: String, fileIds: List<String>): List<DebridStreamLink> {
    val info = pollUntilReady(torrentId, 60_000)
    val targets = if (fileIds.isEmpty()) info.files else info.files.filter { fileIds.contains(it.id) }

    val links = targets.map { file ->
      // The redirect names the file Deepbrid will actually deliver. The listing calls a
      // single-file torrent by the *torrent's* name, which carries no extension, so without this
      // every file looks like a non-video and packs cannot be matched by filename at all.
      val delivered = DebridHttp.redirectTarget(file.id)?.let(::filenameFromUrl).orEmpty()
      DebridStreamLink(
        url = file.id,
        filename = delivered.ifBlank { file.name },
        filesize = file.size,
      )
    }

    val playable = links.filterNot { isArchiveFile(it.filename) }
    if (playable.isEmpty() && links.isNotEmpty()) {
      // Deepbrid packs some torrents — anything with a mixed bag of files — into a single .rar.
      // Nothing unpacks it, so saying so is the only useful answer; returning nothing would be
      // reported as "still downloading" and the viewer would wait for something never coming.
      throw IllegalStateException(
        "Deepbrid: this torrent was delivered as an archive (${links.first().filename}) and cannot be streamed",
      )
    }
    return playable
  }

  /** POST /generate/link — for third-party hosters only; see [getStreamLinks]. */
  override suspend fun unrestrictLink(url: String): DebridStreamLink {
    val data = DebridHttp.json(
      request("/generate/link").post(DebridHttp.form("link" to url)).build(),
    )
    assertOk(data, "failed to unrestrict link")
    val generated = data.optString("link").ifBlank { null }
      ?: throw IllegalStateException("Deepbrid: no download link returned")
    return DebridStreamLink(
      url = generated,
      filename = data.optString("filename"),
      filesize = parseSizeToBytes(data.optString("size")),
    )
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────────────────

  /**
   * The whole cloud in one request, briefly memoised.
   *
   * `/torrents/info` with no id answers with an object keyed "1", "2", … rather than an array, and
   * a non-premium key answers the same route with `{error: 2}` — neither is a list, so both are
   * filtered out rather than trusted.
   */
  private suspend fun listTorrents(): List<DeepbridTorrent> {
    torrentListCache?.let { (value, expiresAt) -> if (expiresAt > System.currentTimeMillis()) return value }

    val value = runCatching {
      val data = DebridHttp.json(request("/torrents/info").get().build())
      // A free account answers this route with an error body rather than a listing. Not a fault
      // worth raising: the caller asked what is cached, and the answer is nothing.
      if (data.optInt("error", 0) != 0) return@runCatching emptyList()
      data.objectValues().mapNotNull { entry ->
        val id = entry.optString("id").ifBlank { null } ?: return@mapNotNull null
        DeepbridTorrent(
          id = id,
          filename = entry.optString("filename"),
          progress = entry.optInt("progress"),
          links = entry.optJSONArray("links")?.strings().orEmpty(),
        )
      }
      // Never fatal: not knowing what the cloud holds only costs an extra add.
    }.getOrElse { emptyList() }

    torrentListCache = value to (System.currentTimeMillis() + TORRENT_LIST_TTL_MS)
    return value
  }

  private fun mapTorrentInfo(payload: JSONObject, torrentId: String): DebridTorrentInfo {
    val links = payload.optJSONArray("links")?.strings().orEmpty()
    val torrentName = payload.optString("filename")
    val files = links.mapIndexed { index, link ->
      DebridTorrentFile(
        id = link,
        name = if (links.size == 1 || torrentName.isBlank()) torrentName else "$torrentName (${index + 1})",
        size = 0L,
      )
    }
    val progress = payload.optInt("progress")
    return DebridTorrentInfo(
      id = payload.optString("id").ifBlank { torrentId },
      // The API never exposes the info-hash back to the caller.
      hash = "",
      name = torrentName,
      status = when {
        progress >= 100 -> DebridTorrentStatus.Cached
        progress > 0 -> DebridTorrentStatus.Downloading
        else -> DebridTorrentStatus.Queued
      },
      progress = progress,
      files = files,
    )
  }

  private suspend fun pollUntilReady(torrentId: String, timeoutMs: Long): DebridTorrentInfo {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val info = getTorrentInfo(torrentId)
      // Links appear only once the transfer completes, so both conditions matter: a torrent can
      // report 100% for a moment before its links are populated.
      if (info.status == DebridTorrentStatus.Cached && info.files.isNotEmpty()) return info
      if (info.status == DebridTorrentStatus.Error) throw IllegalStateException("Deepbrid: torrent error")
      delay(2500)
    }
    throw DebridNotReadyException("Deepbrid: torrent did not become ready in time")
  }
}

/**
 * Deepbrid answers HTTP 200 with `{error: 2}` for a premium-only route rather than 403, so
 * failures have to be raised from the body. Messages are worded to hit [debridFailureFor], which
 * reads them to pick a failure code.
 */
private fun assertOk(data: JSONObject, context: String) {
  val code = data.optInt("error", 0)
  if (code == 0) return
  if (code == 2) throw IllegalStateException("Deepbrid: this action requires a premium subscription")
  // Documented as an HTTP status, but it arrives in the body of a 200 often enough that a
  // status-based check misses it — and a missed rate limit means no cooldown.
  if (code == 429) throw IllegalStateException("Deepbrid: rate limited — too many requests")
  throw IllegalStateException("Deepbrid: " + data.optString("message").ifBlank { context })
}

private val ARCHIVE_EXTENSIONS = listOf(".rar", ".zip", ".7z", ".tar", ".gz", ".r00", ".001")

internal fun isArchiveFile(filename: String): Boolean =
  ARCHIVE_EXTENSIONS.any { filename.lowercase().endsWith(it) }

/**
 * The last path segment of a URL, percent- and plus-decoded.
 *
 * Plain JDK rather than `android.net.Uri`, which is a stub outside an emulator: this is the piece
 * of Deepbrid's behaviour most worth having under test, and a helper that only works on a device
 * cannot be tested at all.
 */
internal fun filenameFromUrl(url: String): String = runCatching {
  val path = java.net.URI(url).path.orEmpty()
  val segment = path.split('/').lastOrNull { it.isNotEmpty() }.orEmpty()
  java.net.URLDecoder.decode(segment, "UTF-8")
}.getOrDefault("")

/** The `dn` of a magnet link, which is the release name when the caller supplied one. */
internal fun magnetDisplayName(magnetLink: String): String? {
  val match = Regex("[?&]dn=([^&]+)", RegexOption.IGNORE_CASE).find(magnetLink) ?: return null
  return runCatching { java.net.URLDecoder.decode(match.groupValues[1], "UTF-8").trim() }
    .getOrNull()
    ?.takeIf { it.isNotEmpty() }
}

/**
 * Whether two release names describe the same thing.
 *
 * Scene names differ only in punctuation between sources — dots, underscores and spaces are used
 * interchangeably — so both sides are flattened before comparing. Containment is accepted either
 * way so an episode matches the pack holding it, but only past a length a coincidence could not
 * reach: a short name like "Up" would otherwise match most of a cloud.
 */
internal fun releaseNamesMatch(left: String, right: String): Boolean {
  val a = normalizeReleaseName(left)
  val b = normalizeReleaseName(right)
  if (a.isEmpty() || b.isEmpty()) return false
  if (a == b) return true
  if (a.length < 12 || b.length < 12) return false
  return a.contains(b) || b.contains(a)
}

internal fun normalizeReleaseName(value: String): String =
  value.lowercase()
    .replace(Regex("\\.[a-z0-9]{2,4}$"), "")
    .replace(Regex("[._\\-+()\\[\\]]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

/** Case-insensitive lookup, since callers key these by hash and casing varies. */
private fun nameFor(names: Map<String, String>, hash: String): String? =
  (names[hash] ?: names[hash.lowercase()] ?: names[hash.uppercase()])?.trim()?.takeIf { it.isNotEmpty() }

/** Converts Deepbrid's human-readable size ("1.50 GB") into bytes; 0 when absent. */
internal fun parseSizeToBytes(value: String): Long {
  val match = Regex("([\\d.]+)\\s*(TB|GB|MB|KB|B)", RegexOption.IGNORE_CASE).find(value) ?: return 0L
  val amount = match.groupValues[1].toDoubleOrNull() ?: return 0L
  val multiplier = when (match.groupValues[2].uppercase()) {
    "TB" -> 1024.0 * 1024 * 1024 * 1024
    "GB" -> 1024.0 * 1024 * 1024
    "MB" -> 1024.0 * 1024
    "KB" -> 1024.0
    else -> 1.0
  }
  return (amount * multiplier).toLong()
}
