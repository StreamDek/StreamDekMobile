package net.streamdek.mobile.debrid

import kotlinx.coroutines.delay
import okhttp3.Request
import org.json.JSONObject

private const val BASE = "https://debrid-link.com/api/v2"

/** /seedbox/cached takes a comma-separated list; kept modest so one URL stays a sane length. */
private const val CACHED_BATCH_SIZE = 40

/**
 * Debrid-Link.
 * Docs: https://debrid-link.com/api_doc/v2/introduction
 *
 * Auth: Authorization: Bearer {apiKey}
 */
internal class DebridLinkClient(private val apiKey: String) : DebridProviderClient {
  override val name = "debrid-link"

  private fun request(path: String, query: Map<String, String> = emptyMap()) =
    Request.Builder()
      .url(DebridHttp.url(BASE, path, query))
      .header("Authorization", "Bearer $apiKey")
      .header("Accept", "application/json")

  override suspend fun validate(): DebridValidation = runCatching {
    val data = DebridHttp.json(request("/account/infos").get().build())
    if (!data.optBoolean("success")) return DebridValidation(valid = false)
    val account = DebridHttp.unwrap(data)
    DebridValidation(
      valid = true,
      username = account.optString("username").ifBlank { account.optString("email") }.ifBlank { null },
      premium = account.optLong("premiumLeft") > 0L || account.optInt("accountType") > 0,
    )
  }.getOrElse { DebridValidation(valid = false) }

  /**
   * Which of these hashes Debrid-Link already has cached.
   *
   * GET /seedbox/cached answers without writing anything, which matters: an earlier version of
   * this answered the question by *adding* every hash to the seedbox and deleting it again only if
   * it turned out to be cached, so merely opening a title with forty sources fired forty adds and
   * left every uncached one behind.
   */
  override suspend fun checkCache(infoHashes: List<String>, names: Map<String, String>): Map<String, Boolean> {
    val result = infoHashes.associate { hash -> hash.lowercase() to false }.toMutableMap()
    if (infoHashes.isEmpty()) return result

    infoHashes.chunked(CACHED_BATCH_SIZE).forEach { batch ->
      runCatching {
        val data = DebridHttp.json(
          request("/seedbox/cached", mapOf("url" to batch.joinToString(",") { it.lowercase() })).get().build(),
        )
        if (!data.optBoolean("success")) return@runCatching
        val value = DebridHttp.unwrap(data)
        // Keyed by hash, with an entry present only for something cached. Its shape has varied
        // between versions, so anything truthy under a known hash counts rather than a specific
        // field that a later version might rename.
        value.keys().forEach { key ->
          val hash = key.lowercase()
          if (result.containsKey(hash) && !value.isNull(key)) result[hash] = true
        }
      }
      // A batch that failed stays false: not knowing is reported as not cached, never as an error
      // that would cost the viewer their whole stream list.
    }

    return result
  }

  /** POST /seedbox/add */
  override suspend fun addMagnet(magnetLink: String): String {
    val payload = JSONObject().put("url", magnetLink).put("wait", false).put("structureType", "list")
    val data = DebridHttp.json(
      request("/seedbox/add").post(DebridHttp.jsonBody(payload)).build(),
    )
    if (!data.optBoolean("success")) {
      throw IllegalStateException(data.optString("error").ifBlank { "Debrid-Link: failed to add seedbox item" })
    }
    return DebridHttp.unwrap(data).optString("id")
  }

  /** GET /seedbox/list?ids=ID */
  override suspend fun getTorrentInfo(torrentId: String): DebridTorrentInfo = mapTorrentInfo(fetchTorrent(torrentId))

  /**
   * Selects files and obtains streaming links through the official transcode endpoint, so playback
   * gets a stream URL rather than a plain download URL.
   */
  override suspend fun getStreamLinks(torrentId: String, fileIds: List<String>): List<DebridStreamLink> {
    val info = pollUntilReady(torrentId, 60_000)
    val targets = if (fileIds.isEmpty()) info.files else info.files.filter { fileIds.contains(it.id) }
    if (targets.isEmpty()) return emptyList()

    return targets.mapNotNull { file -> runCatching { transcodeFile(file) }.getOrNull() }
  }

  /** POST /downloader/add */
  override suspend fun unrestrictLink(url: String): DebridStreamLink {
    val data = DebridHttp.json(
      request("/downloader/add").post(DebridHttp.jsonBody(JSONObject().put("url", url))).build(),
    )
    if (!data.optBoolean("success")) {
      throw IllegalStateException(data.optString("error").ifBlank { "Debrid-Link: failed to unrestrict link" })
    }
    val link = DebridHttp.unwrap(data)
    return DebridStreamLink(
      url = link.optString("downloadUrl").ifBlank { link.optString("streamUrl") }.ifBlank { link.optString("url") }.ifBlank { url },
      filename = link.optString("name"),
      filesize = link.optLong("size"),
      mimeType = link.optString("mimeType").ifBlank { null },
    )
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────────────────

  private suspend fun fetchTorrent(torrentId: String): JSONObject {
    val data = DebridHttp.json(
      request("/seedbox/list", mapOf("ids" to torrentId, "structureType" to "list")).get().build(),
    )
    if (!data.optBoolean("success")) {
      throw IllegalStateException(data.optString("error").ifBlank { "Debrid-Link: torrent not found" })
    }
    val torrents = (data.optJSONArray("value") ?: data.optJSONArray("data"))?.objects().orEmpty()
    return torrents.firstOrNull { it.optString("id") == torrentId }
      ?: torrents.firstOrNull()
      ?: throw IllegalStateException("Debrid-Link: torrent not found")
  }

  private fun mapTorrentInfo(torrent: JSONObject): DebridTorrentInfo {
    val files = torrent.optJSONArray("files")?.objects().orEmpty().map { file ->
      DebridTorrentFile(
        id = file.optString("id"),
        name = file.optString("name").ifBlank { file.optString("id") },
        size = file.optLong("size"),
      )
    }
    return DebridTorrentInfo(
      id = torrent.optString("id"),
      hash = torrent.optString("hashString").ifBlank { torrent.optString("hash") }.lowercase(),
      name = torrent.optString("name"),
      status = mapStatus(torrent),
      progress = torrent.optDouble("downloadPercent", 0.0).toInt(),
      files = files,
    )
  }

  private fun mapStatus(torrent: JSONObject): DebridTorrentStatus = when {
    torrent.optDouble("downloadPercent", 0.0) >= 100.0 -> DebridTorrentStatus.Cached
    torrent.optBoolean("srvMaint") || torrent.optInt("status", -1) == 10 -> DebridTorrentStatus.Error
    torrent.optBoolean("wait") || torrent.optInt("status", -1) == 0 -> DebridTorrentStatus.Queued
    else -> DebridTorrentStatus.Downloading
  }

  private suspend fun transcodeFile(file: DebridTorrentFile): DebridStreamLink {
    val data = DebridHttp.json(
      request("/stream/transcode/add").post(DebridHttp.jsonBody(JSONObject().put("id", file.id))).build(),
    )
    if (!data.optBoolean("success")) {
      throw IllegalStateException(data.optString("error").ifBlank { "Debrid-Link: failed to create stream" })
    }
    val stream = DebridHttp.unwrap(data)
    return DebridStreamLink(
      url = stream.optString("streamUrl").ifBlank { stream.optString("downloadUrl") }.ifBlank { stream.optString("url") },
      filename = file.name,
      filesize = file.size,
      mimeType = stream.optString("mimetype").ifBlank { stream.optString("mimeType") }.ifBlank { null },
    )
  }

  private suspend fun pollUntilReady(torrentId: String, timeoutMs: Long): DebridTorrentInfo {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val info = getTorrentInfo(torrentId)
      if (info.status == DebridTorrentStatus.Cached) return info
      if (info.status == DebridTorrentStatus.Error) throw IllegalStateException("Debrid-Link: torrent error")
      delay(2500)
    }
    throw DebridNotReadyException("Debrid-Link: torrent did not become ready in time")
  }
}
