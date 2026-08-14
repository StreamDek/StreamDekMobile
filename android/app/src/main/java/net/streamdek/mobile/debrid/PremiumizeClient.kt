package net.streamdek.mobile.debrid

import kotlinx.coroutines.delay
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val BASE = "https://www.premiumize.me/api"

/**
 * Premiumize.
 * Docs: https://www.premiumize.me/api
 *
 * Auth: `Authorization: Bearer`, which Premiumize accepts for a typed API key and for a token from
 * the device sign-in alike. That is what lets [PremiumizeDeviceAuth] exist without anything
 * downstream needing to know which of the two it is holding. The older `apikey` query parameter
 * works only for the former, so it is not used.
 *
 * The odd one out: a cached magnet resolves to its links in the same call that adds it, so there
 * is nothing to poll and no torrent id to hold. That result is carried in the "torrent id" as
 * JSON, exactly as the server-side provider does, so the shared call sequence still fits.
 */
internal class PremiumizeClient(private val apiKey: String) : DebridProviderClient {
  override val name = "premiumize"

  private fun request(path: String, query: Map<String, String> = emptyMap()) =
    Request.Builder()
      .url(DebridHttp.url(BASE, path, query))
      .header("Authorization", "Bearer $apiKey")

  override suspend fun validate(): DebridValidation = runCatching {
    val data = DebridHttp.json(request("/account/info").get().build())
    if (data.optString("status") != "success") return DebridValidation(valid = false)
    DebridValidation(
      valid = true,
      username = data.optString("customer_id").ifBlank { null },
      premium = data.optDouble("premium_until", 0.0) > 0.0,
    )
  }.getOrElse { DebridValidation(valid = false) }

  /** POST /cache/check, body items[0]=HASH… -> { response: [true|false, …] } */
  override suspend fun checkCache(infoHashes: List<String>, names: Map<String, String>): Map<String, Boolean> {
    if (infoHashes.isEmpty()) return emptyMap()
    val fields = mutableListOf<Pair<String, String>>()
    infoHashes.forEachIndexed { index, hash -> fields.add("items[$index]" to hash) }
    val answers = runCatching {
      DebridHttp.json(
        request("/cache/check").post(DebridHttp.form(*fields.toTypedArray())).build(),
      ).optJSONArray("response") ?: JSONArray()
    }.getOrElse { JSONArray() }

    return infoHashes.mapIndexed { index, hash ->
      hash.lowercase() to answers.optBoolean(index, false)
    }.toMap()
  }

  /**
   * Cached content resolves immediately through /transfer/directdl; anything else is queued with
   * /transfer/create and polled.
   */
  override suspend fun addMagnet(magnetLink: String): String {
    runCatching {
      val data = DebridHttp.json(
        request("/transfer/directdl").post(DebridHttp.form("src" to magnetLink)).build(),
      )
      val content = data.optJSONArray("content")
      if (data.optString("status") == "success" && (content?.length() ?: 0) > 0) {
        return JSONObject().put("type", "direct").put("content", content).toString()
      }
    }

    val data = DebridHttp.json(
      request("/transfer/create").post(DebridHttp.form("src" to magnetLink)).build(),
    )
    if (data.optString("status") != "success") throw IllegalStateException("Premiumize: failed to add transfer")
    return JSONObject().put("type", "queued").put("id", data.optString("id")).toString()
  }

  override suspend fun getTorrentInfo(torrentId: String): DebridTorrentInfo {
    val parsed = runCatching { JSONObject(torrentId) }.getOrNull()
    if (parsed?.optString("type") == "direct") {
      val files = parsed.optJSONArray("content")?.objects().orEmpty().mapIndexed { index, entry ->
        DebridTorrentFile(
          id = index.toString(),
          name = entry.optString("path").substringAfterLast('/').ifBlank { index.toString() },
          size = entry.optLong("size"),
        )
      }
      return DebridTorrentInfo(torrentId, "", "", DebridTorrentStatus.Cached, 100, files)
    }

    val transfer = findTransfer(parsed?.optString("id").orEmpty())
    return DebridTorrentInfo(
      id = torrentId,
      hash = "",
      name = transfer?.optString("name").orEmpty(),
      status = if (transfer?.optString("status") == "finished") DebridTorrentStatus.Cached else DebridTorrentStatus.Downloading,
      progress = ((transfer?.optDouble("progress", 0.0) ?: 0.0) * 100).toInt(),
      files = emptyList(),
    )
  }

  override suspend fun getStreamLinks(torrentId: String, fileIds: List<String>): List<DebridStreamLink> {
    val parsed = runCatching { JSONObject(torrentId) }.getOrNull()

    if (parsed?.optString("type") == "direct") {
      return parsed.optJSONArray("content")?.objects().orEmpty().mapNotNull { entry -> toLink(entry) }
    }

    val transfer = pollUntilReady(parsed?.optString("id").orEmpty(), 60_000)
    val folderId = transfer.optString("folder_id").ifBlank { return emptyList() }
    val data = DebridHttp.json(request("/folder/list", mapOf("id" to folderId)).get().build())
    return data.optJSONArray("content")?.objects().orEmpty()
      .filter { it.optString("type") == "file" && it.optString("stream_link").isNotBlank() }
      .map { file ->
        DebridStreamLink(
          url = file.optString("stream_link"),
          filename = file.optString("name"),
          filesize = file.optLong("size"),
        )
      }
  }

  /** Unrestricts a hoster URL through the same directdl endpoint. */
  override suspend fun unrestrictLink(url: String): DebridStreamLink {
    val data = DebridHttp.json(
      request("/transfer/directdl").post(DebridHttp.form("src" to url)).build(),
    )
    val file = data.optJSONArray("content")?.objects()?.firstOrNull()
      ?: throw IllegalStateException("Premiumize: unrestrict returned no content")
    return toLink(file) ?: throw IllegalStateException("Premiumize: unrestrict returned no link")
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────────────────

  private fun toLink(entry: JSONObject): DebridStreamLink? {
    val url = entry.optString("stream_link").ifBlank { entry.optString("link") }.ifBlank { null }
      ?: return null
    return DebridStreamLink(
      url = url,
      filename = entry.optString("path").substringAfterLast('/'),
      filesize = entry.optLong("size"),
    )
  }

  private suspend fun findTransfer(transferId: String): JSONObject? {
    val data = DebridHttp.json(request("/transfer/list").get().build())
    return data.optJSONArray("transfers")?.objects()?.firstOrNull { it.optString("id") == transferId }
  }

  private suspend fun pollUntilReady(transferId: String, timeoutMs: Long): JSONObject {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val transfer = findTransfer(transferId) ?: throw IllegalStateException("Premiumize: transfer not found")
      when (transfer.optString("status")) {
        "finished" -> return transfer
        "error" -> throw IllegalStateException("Premiumize: transfer error")
      }
      delay(3000)
    }
    throw DebridNotReadyException("Premiumize: transfer did not complete in time")
  }
}
