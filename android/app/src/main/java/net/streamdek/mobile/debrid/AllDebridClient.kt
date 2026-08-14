package net.streamdek.mobile.debrid

import kotlinx.coroutines.delay
import okhttp3.Request
import org.json.JSONObject

private const val BASE = "https://api.alldebrid.com/v4"

/** magnet/status only: v4 still answers but reports itself deprecated, v4.1 does not. */
private const val STATUS_BASE = "https://api.alldebrid.com/v4.1"
private const val AGENT = "streamdek"
private const val STATUS_CACHE_TTL_MS = 60_000L

/**
 * AllDebrid.
 * Docs: https://docs.alldebrid.com/
 *
 * Auth: apikey + agent query params on every request.
 */
internal class AllDebridClient(private val apiKey: String) : DebridProviderClient {
  override val name = "alldebrid"

  private var statusCache: Pair<Set<String>, Long>? = null

  private fun auth(extra: Map<String, String> = emptyMap()) =
    mapOf("agent" to AGENT, "apikey" to apiKey) + extra

  private fun request(path: String, query: Map<String, String> = emptyMap(), base: String = BASE) =
    Request.Builder().url(DebridHttp.url(base, path, auth(query)))

  override suspend fun validate(): DebridValidation = runCatching {
    val data = DebridHttp.json(request("/user").get().build())
    if (data.optString("status") != "success") return DebridValidation(valid = false)
    val user = data.optJSONObject("data")?.optJSONObject("user")
    DebridValidation(
      valid = true,
      username = user?.optString("username")?.ifBlank { null },
      premium = user?.optBoolean("isPremium"),
    )
  }.getOrElse { DebridValidation(valid = false) }

  /**
   * Which of these hashes the account already holds, ready to stream.
   *
   * GET /magnet/instant is gone — it answers 404 — and AllDebrid publishes no replacement for
   * querying its shared cache. The account's own magnet list gives the signal the markers are
   * actually read for: this one is already here, it will start immediately.
   */
  override suspend fun checkCache(infoHashes: List<String>, names: Map<String, String>): Map<String, Boolean> {
    if (infoHashes.isEmpty()) return emptyMap()
    val ready = runCatching { readyMagnetHashes() }.getOrElse { emptySet() }
    return infoHashes.associate { hash -> hash.lowercase() to ready.contains(hash.lowercase()) }
  }

  /**
   * Hashes of the account's finished magnets, briefly memoised so one stream list costs one call
   * rather than one per hash.
   *
   * statusCode 4 is AllDebrid's "Ready"; the textual status is matched too because the API has
   * historically localised it and a version that omits the code should still work.
   */
  private suspend fun readyMagnetHashes(): Set<String> {
    statusCache?.let { (hashes, expiresAt) -> if (expiresAt > System.currentTimeMillis()) return hashes }

    val data = DebridHttp.json(request("/magnet/status", base = STATUS_BASE).get().build())
    val magnets = data.optJSONObject("data")?.optJSONArray("magnets")?.objects().orEmpty()
    val hashes = magnets.mapNotNullTo(mutableSetOf()) { magnet ->
      val ready = magnet.optInt("statusCode", -1) == 4 || magnet.optString("status").lowercase() == "ready"
      magnet.optString("hash").takeIf { ready && it.isNotBlank() }?.lowercase()
    }

    statusCache = hashes to (System.currentTimeMillis() + STATUS_CACHE_TTL_MS)
    return hashes
  }

  /** POST /magnet/upload, body magnets[]=MAGNET -> { data: { magnets: [{ id, … }] } } */
  override suspend fun addMagnet(magnetLink: String): String {
    val data = DebridHttp.json(
      Request.Builder()
        .url(DebridHttp.url(BASE, "/magnet/upload", auth()))
        .post(DebridHttp.form("magnets[]" to magnetLink))
        .build(),
    )
    val magnet = data.optJSONObject("data")?.optJSONArray("magnets")?.objects()?.firstOrNull()
    val id = magnet?.optString("id").orEmpty()
    if (id.isBlank()) throw IllegalStateException("AllDebrid: no magnet ID returned")
    return id
  }

  /** GET /magnet/status?id=ID */
  override suspend fun getTorrentInfo(torrentId: String): DebridTorrentInfo {
    val data = DebridHttp.json(request("/magnet/status", mapOf("id" to torrentId)).get().build())
    return mapTorrentInfo(data.optJSONObject("data")?.optJSONObject("magnets"), torrentId)
  }

  /** For cached content, unlock the resolved links directly. */
  override suspend fun getStreamLinks(torrentId: String, fileIds: List<String>): List<DebridStreamLink> {
    val magnet = pollUntilReady(torrentId, 30_000)
    val links = magnet.optJSONArray("links")?.objects().orEmpty()
    return links.mapNotNull { entry ->
      val link = entry.optString("link").ifBlank { null } ?: return@mapNotNull null
      runCatching { unrestrictLink(link) }.getOrNull()
    }
  }

  /** GET /link/unlock?link=URL -> { data: { link, filename, filesize } } */
  override suspend fun unrestrictLink(url: String): DebridStreamLink {
    val data = DebridHttp.json(request("/link/unlock", mapOf("link" to url)).get().build())
    val unlocked = data.optJSONObject("data")
    return DebridStreamLink(
      url = unlocked?.optString("link")?.ifBlank { null } ?: url,
      filename = unlocked?.optString("filename").orEmpty(),
      filesize = unlocked?.optLong("filesize") ?: 0L,
    )
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────────────────

  private fun mapTorrentInfo(magnet: JSONObject?, torrentId: String): DebridTorrentInfo {
    val files = magnet?.optJSONArray("links")?.objects().orEmpty().mapIndexed { index, link ->
      DebridTorrentFile(
        id = index.toString(),
        name = link.optString("filename").ifBlank { index.toString() },
        size = link.optLong("size"),
      )
    }
    return DebridTorrentInfo(
      id = magnet?.optString("id")?.ifBlank { null } ?: torrentId,
      hash = magnet?.optString("hash").orEmpty().lowercase(),
      name = magnet?.optString("filename").orEmpty(),
      status = mapStatus(magnet?.optInt("statusCode", -1) ?: -1),
      progress = if (magnet?.optBoolean("downloaded") == true) 100 else 0,
      files = files,
    )
  }

  /** AllDebrid status codes: 0 = queued, 1 = processing, 4 = complete, 10+ = error. */
  private fun mapStatus(code: Int): DebridTorrentStatus = when {
    code == 4 -> DebridTorrentStatus.Cached
    code >= 10 -> DebridTorrentStatus.Error
    code == 0 -> DebridTorrentStatus.Queued
    else -> DebridTorrentStatus.Downloading
  }

  private suspend fun pollUntilReady(torrentId: String, timeoutMs: Long): JSONObject {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val data = DebridHttp.json(request("/magnet/status", mapOf("id" to torrentId)).get().build())
      val magnet = data.optJSONObject("data")?.optJSONObject("magnets")
      val code = magnet?.optInt("statusCode", -1) ?: -1
      if (code == 4 && magnet != null) return magnet
      if (code >= 10) throw IllegalStateException("AllDebrid torrent error: code $code")
      delay(2000)
    }
    throw DebridNotReadyException("AllDebrid: torrent did not become ready in time")
  }
}
