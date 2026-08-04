package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class M3uPlaylistSource(
  val id: String,
  val name: String,
  val url: String,
  val enabled: Boolean = true,
  val position: Int = 0,
)

/** Stores and parses raw M3U/M3U8 IPTV playlist URLs into [MediaItem]s that flow through the
 * existing Live TV pipeline via [MediaItem.directStreamUrl] - the same direct-URL mechanism
 * already used for non-add-on "Sports source" style channels, so no new playback code is
 * needed. Modeled after [LocalAddonManager] (owner-scoped storage, module-level singleton). */
object M3uPlaylistManager {
  private const val PREFS_NAME = "streamdek_m3u_playlists"
  private const val KEY_SOURCES = "sources"
  private const val ID_PREFIX = "m3u:"

  private var prefs: SharedPreferences? = null
  private var ownerKey: String = "guest"
  private val http = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  fun initialize(context: Context) {
    if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun selectProfileStorage(ownerKey: String) {
    this.ownerKey = ownerKey.ifBlank { "guest" }
  }

  fun isM3uSourceId(id: String): Boolean = id.startsWith(ID_PREFIX)

  fun list(): List<M3uPlaylistSource> = readAll().sortedBy { it.position }

  suspend fun add(rawUrl: String, name: String?): Result<M3uPlaylistSource> = withContext(Dispatchers.IO) {
    runCatching {
      val normalized = rawUrl.trim()
      val uri = runCatching { URI(normalized) }.getOrNull()
      require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Enter a valid M3U playlist URL." }
      val existing = readAll()
      require(existing.none { it.url.equals(normalized, ignoreCase = true) }) { "This playlist is already added." }
      val body = fetchBody(normalized)
      val channelCount = parseM3u(body, ID_PREFIX + Integer.toHexString(normalized.hashCode())).size
      require(channelCount > 0) { "No channels were found in that playlist." }
      val source = M3uPlaylistSource(
        id = ID_PREFIX + Integer.toHexString(normalized.hashCode()),
        name = name?.trim()?.takeIf { it.isNotBlank() } ?: "Playlist ${existing.size + 1}",
        url = normalized,
        enabled = true,
        position = existing.size,
      )
      writeAll(existing + source)
      source
    }
  }

  fun remove(id: String) {
    writeAll(readAll().filterNot { it.id == id })
  }

  fun setEnabled(id: String, enabled: Boolean) {
    writeAll(readAll().map { if (it.id == id) it.copy(enabled = enabled) else it })
  }

  fun move(id: String, delta: Int) {
    val current = readAll().sortedBy { it.position }.toMutableList()
    val index = current.indexOfFirst { it.id == id }
    val target = (index + delta).coerceIn(0, current.lastIndex)
    if (index < 0 || index == target) return
    val item = current.removeAt(index)
    current.add(target, item)
    writeAll(current.mapIndexed { newPosition, record -> record.copy(position = newPosition) })
  }

  suspend fun fetchChannels(source: M3uPlaylistSource): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
    runCatching { parseM3u(fetchBody(source.url), source.id, source.name) }
  }

  private fun fetchBody(url: String): String {
    http.newCall(Request.Builder().url(url).header("User-Agent", "StreamDek/1.0").build()).execute().use { response ->
      require(response.isSuccessful) { "Request failed: ${response.code}" }
      return response.body?.string()?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Empty playlist response.")
    }
  }

  private fun sourcesKey(): String = "$KEY_SOURCES:$ownerKey"

  private fun readAll(): List<M3uPlaylistSource> {
    val raw = prefs?.getString(sourcesKey(), null) ?: return emptyList()
    return runCatching {
      val array = JSONArray(raw)
      List(array.length()) { index ->
        val item = array.getJSONObject(index)
        M3uPlaylistSource(
          id = item.getString("id"),
          name = item.getString("name"),
          url = item.getString("url"),
          enabled = item.optBoolean("enabled", true),
          position = item.optInt("position", 0),
        )
      }
    }.getOrDefault(emptyList())
  }

  private fun writeAll(sources: List<M3uPlaylistSource>) {
    val array = JSONArray()
    sources.forEach { source ->
      array.put(
        JSONObject()
          .put("id", source.id)
          .put("name", source.name)
          .put("url", source.url)
          .put("enabled", source.enabled)
          .put("position", source.position),
      )
    }
    prefs?.edit()?.putString(sourcesKey(), array.toString())?.apply()
  }
}

private val m3uTvgLogoRegex = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
private val m3uGroupTitleRegex = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

/** Parses `#EXTM3U` playlist text into playable channel [MediaItem]s. Each entry's stream URL
 * is carried on [MediaItem.directStreamUrl], which the existing detail/player pipeline already
 * treats as a directly-playable live source (see `isLiveCatalogItem()`). */
internal fun parseM3u(body: String, sourceId: String, sourceName: String = "M3U Playlist"): List<MediaItem> {
  require(body.contains("#EXTM3U", ignoreCase = true)) { "That URL doesn't look like an M3U playlist." }
  val items = mutableListOf<MediaItem>()
  var pendingTitle: String? = null
  var pendingLogo: String? = null
  var pendingGroup: String? = null
  var index = 0
  body.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
    when {
      line.startsWith("#EXTINF", ignoreCase = true) -> {
        val comma = line.indexOf(',')
        pendingTitle = if (comma >= 0) line.substring(comma + 1).trim().takeIf { it.isNotBlank() } else null
        pendingLogo = m3uTvgLogoRegex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        pendingGroup = m3uGroupTitleRegex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
      }
      line.startsWith("#") -> Unit
      else -> {
        val title = pendingTitle ?: "Channel ${index + 1}"
        items.add(
          MediaItem(
            id = "$sourceId:${index++}",
            type = "tv",
            title = title,
            year = null,
            poster = pendingLogo,
            backdrop = pendingLogo,
            rating = null,
            description = pendingGroup?.let { "$it • $sourceName" } ?: sourceName,
            titleLogo = pendingLogo,
            sourceAddonId = sourceId,
            sourceAddonName = sourceName,
            sourceCatalogType = "tv",
            sourceCatalogId = sourceId,
            sourceCatalogName = pendingGroup ?: sourceName,
            directStreamUrl = line,
          ),
        )
        pendingTitle = null
        pendingLogo = null
        pendingGroup = null
      }
    }
  }
  return items
}
