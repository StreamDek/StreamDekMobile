package net.streamdek.mobile.debrid

import kotlinx.coroutines.delay
import okhttp3.Request

private const val BASE = "https://api.torbox.app/v1/api"

/**
 * TorBox.
 * Docs: https://api.torbox.app/
 *
 * Auth: Authorization: Bearer {apiKey}
 */
internal class TorBoxClient(private val apiKey: String) : DebridProviderClient {
  override val name = "torbox"

  private fun request(path: String, query: Map<String, String> = emptyMap()) =
    Request.Builder()
      .url(DebridHttp.url(BASE, path, query))
      .header("Authorization", "Bearer $apiKey")

  override suspend fun validate(): DebridValidation = runCatching {
    val data = DebridHttp.json(request("/user/me").get().build())
    if (!data.optBoolean("success")) return DebridValidation(valid = false)
    val user = data.optJSONObject("data")
    DebridValidation(
      valid = true,
      username = user?.optString("email")?.ifBlank { null } ?: user?.optString("customer_id")?.ifBlank { null },
      premium = (user?.optInt("plan") ?: 0) > 0,
    )
  }.getOrElse { DebridValidation(valid = false) }

  /**
   * Instant availability, and the only provider here that still offers one.
   * GET /torrents/checkcached?hash=H1,H2&format=list — only cached hashes come back.
   */
  override suspend fun checkCache(infoHashes: List<String>, names: Map<String, String>): Map<String, Boolean> {
    if (infoHashes.isEmpty()) return emptyMap()
    val cached = runCatching {
      val data = DebridHttp.json(
        request(
          "/torrents/checkcached",
          mapOf(
            "hash" to infoHashes.joinToString(",") { it.lowercase() },
            "format" to "list",
            "list_files" to "false",
          ),
        ).get().build(),
      )
      data.optJSONArray("data")?.objects().orEmpty()
        .mapNotNull { entry -> entry.optString("hash").takeIf { it.isNotBlank() }?.lowercase() }
        .toSet()
    }.getOrElse { emptySet() }

    return infoHashes.associate { hash -> hash.lowercase() to cached.contains(hash.lowercase()) }
  }

  /** POST /torrents/createtorrent (multipart) -> { data: { torrent_id } } */
  override suspend fun addMagnet(magnetLink: String): String {
    val data = DebridHttp.json(
      request("/torrents/createtorrent").post(DebridHttp.multipart("magnet" to magnetLink)).build(),
    )
    val id = data.optJSONObject("data")?.optString("torrent_id").orEmpty()
    if (!data.optBoolean("success") || id.isBlank()) throw IllegalStateException("TorBox: failed to add magnet")
    return id
  }

  /** GET /torrents/mylist?id=ID&bypass_cache=true */
  override suspend fun getTorrentInfo(torrentId: String): DebridTorrentInfo {
    val data = DebridHttp.json(
      request("/torrents/mylist", mapOf("id" to torrentId, "bypass_cache" to "true")).get().build(),
    )
    val torrent = data.optJSONObject("data") ?: throw IllegalStateException("TorBox: torrent not found")
    val files = torrent.optJSONArray("files")?.objects().orEmpty().map { file ->
      DebridTorrentFile(
        id = file.optString("id"),
        name = file.optString("short_name").ifBlank { file.optString("name") }.ifBlank { file.optString("id") },
        size = file.optLong("size"),
      )
    }
    return DebridTorrentInfo(
      id = torrent.optString("id"),
      hash = torrent.optString("hash").lowercase(),
      name = torrent.optString("name"),
      status = mapStatus(torrent.optString("download_state")),
      progress = (torrent.optDouble("progress", 0.0) * 100).toInt(),
      files = files,
    )
  }

  /** GET /torrents/requestdl per file -> { data: "https://cdn.torbox.app/…" } */
  override suspend fun getStreamLinks(torrentId: String, fileIds: List<String>): List<DebridStreamLink> {
    val info = pollUntilReady(torrentId, 60_000)
    val targets = if (fileIds.isEmpty()) info.files else info.files.filter { fileIds.contains(it.id) }
    if (targets.isEmpty()) return emptyList()

    return targets.mapNotNull { file ->
      runCatching {
        val data = DebridHttp.json(
          request(
            "/torrents/requestdl",
            mapOf(
              "token" to apiKey,
              "torrent_id" to torrentId,
              "file_id" to file.id,
              "zip_link" to "false",
            ),
          ).get().build(),
        )
        val url = data.optString("data")
        if (!data.optBoolean("success") || url.isBlank()) throw IllegalStateException("TorBox: no download link returned")
        DebridStreamLink(url = url, filename = file.name, filesize = file.size)
      }.getOrNull()
    }
  }

  /** TorBox is a torrent service only; it unrestricts no hoster links. */
  override suspend fun unrestrictLink(url: String): DebridStreamLink =
    throw IllegalStateException("TorBox does not support hoster link unrestricting")

  // ── Helpers ───────────────────────────────────────────────────────────────────────────────

  private fun mapStatus(state: String): DebridTorrentStatus {
    if (state.isBlank()) return DebridTorrentStatus.Queued
    val value = state.lowercase()
    return when {
      value == "cached" || value == "completed" -> DebridTorrentStatus.Cached
      value.contains("error") || value.contains("stalled") -> DebridTorrentStatus.Error
      value == "queued" || value == "metadl" || value == "checkingresumedata" -> DebridTorrentStatus.Queued
      else -> DebridTorrentStatus.Downloading
    }
  }

  private suspend fun pollUntilReady(torrentId: String, timeoutMs: Long): DebridTorrentInfo {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val info = getTorrentInfo(torrentId)
      if (info.status == DebridTorrentStatus.Cached) return info
      if (info.status == DebridTorrentStatus.Error) throw IllegalStateException("TorBox: torrent in error state")
      delay(3000)
    }
    throw DebridNotReadyException("TorBox: torrent did not become ready in time")
  }
}
