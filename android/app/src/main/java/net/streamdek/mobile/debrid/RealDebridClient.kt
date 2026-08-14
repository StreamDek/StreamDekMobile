package net.streamdek.mobile.debrid

import kotlinx.coroutines.delay
import okhttp3.Request
import org.json.JSONObject

private const val BASE = "https://api.real-debrid.com/rest/1.0"

/** Torrent states that mean the file is on Real-Debrid now and will stream without waiting. */
private val READY_STATUSES = setOf("downloaded", "seeding")
private const val LIBRARY_PAGE_SIZE = 2500
private const val LIBRARY_MAX_PAGES = 4
private const val LIBRARY_CACHE_TTL_MS = 60_000L

/**
 * Real-Debrid.
 * Docs: https://api.real-debrid.com/
 *
 * Auth: Authorization: Bearer {apiKey}
 */
internal class RealDebridClient(
  apiKey: String,
  /**
   * Renews an expired token, or null for a typed API key, which never expires.
   *
   * Real-Debrid's device sign-in issues a token good for about an hour. Without this, an account
   * connected that way plays perfectly today and is silently dead tomorrow — so a call that comes
   * back unauthorised renews once and tries again before reporting anything.
   */
  private val renew: (suspend () -> String?)? = null,
) : DebridProviderClient {
  override val name = "real-debrid"

  @Volatile private var apiKey: String = apiKey

  private var libraryCache: Pair<Set<String>, Long>? = null

  private fun request(path: String, query: Map<String, String> = emptyMap()) =
    Request.Builder()
      .url(DebridHttp.url(BASE, path, query))
      .header("Authorization", "Bearer $apiKey")

  /**
   * Runs a call, and if the token has expired, renews it once and runs it again.
   *
   * Once only: a second failure means the credential is finished rather than stale, and retrying
   * past that turns one dead account into a loop against Real-Debrid.
   */
  private suspend fun <T> authorized(call: suspend () -> T): T = try {
    call()
  } catch (error: DebridHttpException) {
    val renewer = renew
    if ((error.statusCode == 401 || error.statusCode == 403) && renewer != null) {
      val fresh = renewer() ?: throw error
      apiKey = fresh
      call()
    } else {
      throw error
    }
  }

  override suspend fun validate(): DebridValidation = runCatching {
    val data = DebridHttp.json(request("/user").get().build())
    DebridValidation(
      valid = true,
      username = data.optString("username").ifBlank { null },
      premium = data.optString("type") == "premium",
    )
  }.getOrElse { DebridValidation(valid = false) }

  /**
   * Which of these hashes the account already holds, downloaded and instantly playable.
   *
   * Real-Debrid switched off /torrents/instantAvailability — it answers 403 disabled_endpoint —
   * and publishes no replacement, so its shared cache cannot be queried at all any more. What it
   * still answers truthfully is what this account already has, which is the half viewers act on:
   * this one starts immediately. Anything else is reported uncached, which is honest rather than
   * a guess.
   */
  override suspend fun checkCache(infoHashes: List<String>, names: Map<String, String>): Map<String, Boolean> {
    if (infoHashes.isEmpty()) return emptyMap()
    val library = runCatching { downloadedLibraryHashes() }.getOrElse { emptySet() }
    return infoHashes.associate { hash -> hash.lowercase() to library.contains(hash.lowercase()) }
  }

  /**
   * Hashes of every finished torrent on the account, briefly memoised: one stream list asks about
   * dozens of hashes and the library is the same answer for all of them. Paging is capped because
   * a very large library is not worth an unbounded walk to decorate one list of sources.
   */
  private suspend fun downloadedLibraryHashes(): Set<String> {
    libraryCache?.let { (hashes, expiresAt) -> if (expiresAt > System.currentTimeMillis()) return hashes }

    val hashes = mutableSetOf<String>()
    for (page in 1..LIBRARY_MAX_PAGES) {
      val entries = DebridHttp.jsonArray(
        request("/torrents", mapOf("page" to page.toString(), "limit" to LIBRARY_PAGE_SIZE.toString())).get().build(),
      ).objects()
      for (entry in entries) {
        if (entry.optString("status") in READY_STATUSES) {
          entry.optString("hash").takeIf { it.isNotBlank() }?.let { hashes.add(it.lowercase()) }
        }
      }
      if (entries.size < LIBRARY_PAGE_SIZE) break
    }

    libraryCache = hashes to (System.currentTimeMillis() + LIBRARY_CACHE_TTL_MS)
    return hashes
  }

  /** POST /torrents/addMagnet (form-encoded) -> { id, uri } */
  override suspend fun addMagnet(magnetLink: String): String {
    val data = authorized {
      DebridHttp.json(
        request("/torrents/addMagnet").post(DebridHttp.form("magnet" to magnetLink)).build(),
      )
    }
    return data.optString("id").ifBlank { throw IllegalStateException("Real-Debrid: no torrent id returned") }
  }

  /** GET /torrents/info/{id} */
  override suspend fun getTorrentInfo(torrentId: String): DebridTorrentInfo =
    mapTorrentInfo(authorized { DebridHttp.json(request("/torrents/info/$torrentId").get().build()) })

  /**
   * Select the files, wait for the links to appear, then unrestrict them.
   * 1. POST /torrents/selectFiles/{id}
   * 2. GET  /torrents/info/{id} until status is downloaded
   * 3. POST /unrestrict/link, once per link
   */
  override suspend fun getStreamLinks(torrentId: String, fileIds: List<String>): List<DebridStreamLink> {
    val filesParam = if (fileIds.isEmpty()) "all" else fileIds.joinToString(",")
    runCatching {
      DebridHttp.json(
        request("/torrents/selectFiles/$torrentId").post(DebridHttp.form("files" to filesParam)).build(),
      )
    }
    // Selecting files answers 204 with no body when it succeeds, which is not JSON — the failure
    // that matters shows up as the poll never completing, not as this call's return value.

    val info = pollUntilReady(torrentId, 30_000)
    val links = info.optJSONArray("links")?.strings().orEmpty()
    if (links.isEmpty()) return emptyList()

    return links.mapNotNull { link -> runCatching { unrestrictLink(link) }.getOrNull() }
  }

  /** POST /unrestrict/link (form-encoded) -> { download, filename, filesize, mimeType } */
  override suspend fun unrestrictLink(url: String): DebridStreamLink {
    val data = authorized {
      DebridHttp.json(
        request("/unrestrict/link").post(DebridHttp.form("link" to url)).build(),
      )
    }
    return DebridStreamLink(
      url = data.optString("download"),
      filename = data.optString("filename"),
      filesize = data.optLong("filesize"),
      mimeType = data.optString("mimeType").ifBlank { null },
    )
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────────────────

  private fun mapTorrentInfo(data: JSONObject): DebridTorrentInfo {
    val files = data.optJSONArray("files")?.objects().orEmpty().map { file ->
      val path = file.optString("path")
      DebridTorrentFile(
        id = file.optString("id"),
        name = path.substringAfterLast('/').ifBlank { path }.ifBlank { file.optString("id") },
        size = file.optLong("bytes"),
      )
    }
    return DebridTorrentInfo(
      id = data.optString("id"),
      hash = data.optString("hash").lowercase(),
      name = data.optString("filename"),
      status = mapStatus(data.optString("status")),
      progress = data.optInt("progress"),
      files = files,
    )
  }

  private fun mapStatus(status: String): DebridTorrentStatus = when (status) {
    "downloaded", "seeding" -> DebridTorrentStatus.Cached
    "downloading", "compressing", "uploading" -> DebridTorrentStatus.Downloading
    "error", "virus", "dead" -> DebridTorrentStatus.Error
    else -> DebridTorrentStatus.Queued
  }

  private suspend fun pollUntilReady(torrentId: String, timeoutMs: Long): JSONObject {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val data = DebridHttp.json(request("/torrents/info/$torrentId").get().build())
      val status = data.optString("status")
      if (status in READY_STATUSES) return data
      if (status in setOf("error", "virus", "dead")) throw IllegalStateException("Real-Debrid: torrent $status")
      delay(2000)
    }
    throw DebridNotReadyException("Real-Debrid: torrent did not become ready in time")
  }
}
