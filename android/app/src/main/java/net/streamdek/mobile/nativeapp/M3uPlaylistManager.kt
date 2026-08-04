package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import net.streamdek.mobile.BuildConfig
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
  val liveItemCount: Int? = null,
  val vodItemCount: Int? = null,
)
data class M3uLoadProgress(
  val message: String,
  val fraction: Float?,
  val itemsParsed: Int = 0,
)

data class M3uPlaylistItems(val liveChannels: List<MediaItem>, val vodItems: List<MediaItem>) {
  val size: Int get() = liveChannels.size + vodItems.size
}

data class M3uAddResult(val source: M3uPlaylistSource, val content: M3uPlaylistItems)

private fun List<MediaItem>.toM3uPlaylistItems(): M3uPlaylistItems {
  val (vod, live) = partition { it.sourceCatalogType == "movie" }
  return M3uPlaylistItems(liveChannels = live, vodItems = vod)
}

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

  suspend fun add(
    rawUrl: String,
    name: String?,
    onProgress: (M3uLoadProgress) -> Unit = {},
  ): Result<M3uAddResult> = withContext(Dispatchers.IO) {
    runCatching {
      val normalized = rawUrl.trim()
      val uri = runCatching { URI(normalized) }.getOrNull()
      require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Enter a valid M3U playlist URL." }
      val existing = readAll()
      require(existing.none { it.url.equals(normalized, ignoreCase = true) }) { "This playlist is already added." }
      val source = M3uPlaylistSource(
        id = ID_PREFIX + Integer.toHexString(normalized.hashCode()),
        name = name?.trim()?.takeIf { it.isNotBlank() } ?: "Playlist ${existing.size + 1}",
        url = normalized,
        enabled = true,
        position = existing.size,
      )
      val body = fetchBody(normalized) { downloaded, total ->
        val fraction = total?.takeIf { it > 0L }?.let { (downloaded.toDouble() / it).toFloat().coerceIn(0f, 1f) }
        onProgress(M3uLoadProgress("Downloading ${source.name}…", fraction?.times(0.55f)))
      }
      val items = parseM3u(body, source.id, source.name) { parsed, processed, total ->
        val parseFraction = if (total > 0) processed.toFloat() / total else 0f
        onProgress(M3uLoadProgress("Reading ${source.name}: ${parsed.formattedM3uCount()} items found", 0.55f + parseFraction * 0.45f, parsed))
      }
      require(items.isNotEmpty()) { "No playable items were found in that playlist." }
      val summarizedSource = source.copy(
        liveItemCount = items.count { it.sourceCatalogType != "movie" },
        vodItemCount = items.count { it.sourceCatalogType == "movie" },
      )
      writeAll(existing + summarizedSource)
      onProgress(M3uLoadProgress("Loaded ${items.size.formattedM3uCount()} items from ${source.name}", 1f, items.size))
      M3uAddResult(summarizedSource, items.toM3uPlaylistItems())
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

  fun updateContentSummary(id: String, content: M3uPlaylistItems) {
    writeAll(readAll().map { source ->
      if (source.id == id) source.copy(
        liveItemCount = content.liveChannels.size,
        vodItemCount = content.vodItems.size,
      ) else source
    })
  }

  suspend fun fetchItems(
    source: M3uPlaylistSource,
    onProgress: (M3uLoadProgress) -> Unit = {},
  ): Result<M3uPlaylistItems> = withContext(Dispatchers.IO) {
    runCatching {
      val body = fetchBody(source.url) { downloaded, total ->
        val fraction = total?.takeIf { it > 0L }?.let { (downloaded.toDouble() / it).toFloat().coerceIn(0f, 1f) }
        onProgress(M3uLoadProgress("Downloading ${source.name}…", fraction?.times(0.55f)))
      }
      parseM3u(body, source.id, source.name) { parsed, processed, total ->
        val parseFraction = if (total > 0) processed.toFloat() / total else 0f
        onProgress(M3uLoadProgress("Reading ${source.name}: ${parsed.formattedM3uCount()} items found", 0.55f + parseFraction * 0.45f, parsed))
      }.also { items ->
        onProgress(M3uLoadProgress("Loaded ${items.size.formattedM3uCount()} items from ${source.name}", 1f, items.size))
      }.toM3uPlaylistItems()
    }
  }

  private fun fetchBody(url: String, onProgress: (Long, Long?) -> Unit): String {
    http.newCall(Request.Builder().url(url).header("User-Agent", "StreamDek/${BuildConfig.VERSION_NAME}").build()).execute().use { response ->
      require(response.isSuccessful) { "Request failed: ${response.code}" }
      val body = response.body ?: throw IllegalStateException("Empty playlist response.")
      val total = body.contentLength().takeIf { it > 0L }
      // Avoid reserving the entire advertised playlist size up front. Some providers expose
      // very large or inaccurate Content-Length values.
      val initialCapacity = total?.coerceAtMost(4L * 1024 * 1024)?.toInt() ?: DEFAULT_BUFFER_SIZE
      val output = ByteArrayOutputStream(initialCapacity)
      body.byteStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var downloaded = 0L
        var lastReported = 0L
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          output.write(buffer, 0, count)
          downloaded += count
          if (downloaded - lastReported >= 256 * 1024 || downloaded == total) {
            onProgress(downloaded, total)
            lastReported = downloaded
          }
        }
        if (downloaded == 0L) throw IllegalStateException("Empty playlist response.")
        onProgress(downloaded, total)
      }
      return output.toString(StandardCharsets.UTF_8.name())
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
          liveItemCount = item.optInt("liveItemCount").takeIf { item.has("liveItemCount") },
          vodItemCount = item.optInt("vodItemCount").takeIf { item.has("vodItemCount") },
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
          .put("position", source.position)
          .apply {
            source.liveItemCount?.let { put("liveItemCount", it) }
            source.vodItemCount?.let { put("vodItemCount", it) }
          },
      )
    }
    prefs?.edit()?.putString(sourcesKey(), array.toString())?.apply()
  }
}

private val m3uTvgLogoRegex = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
private val m3uGroupTitleRegex = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
private val m3uMediaTypeRegex = Regex("(?:tvg-type|media-type|content-type|type)=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
private val m3uDurationRegex = Regex("^#EXTINF:([\\d.-]+)", RegexOption.IGNORE_CASE)
private val m3uEpisodePattern = Regex("(?:^|[ ._\\-])S\\d{1,2}E\\d{1,3}(?:$|[ ._\\-])", RegexOption.IGNORE_CASE)
private val m3uVodExtensions = setOf("mp4", "m4v", "mkv", "avi", "mov", "webm", "wmv")

private fun Int.formattedM3uCount(): String = String.format("%,d", this)

private fun parseInlineM3uHeaders(raw: String): Pair<String, Map<String, String>> {
  val url = raw.substringBefore('|').trim()
  val encodedHeaders = raw.substringAfter('|', "")
  if (encodedHeaders.isBlank()) return url to emptyMap()
  val headers = linkedMapOf<String, String>()
  encodedHeaders.split('&').forEach { pair ->
    val key = pair.substringBefore('=', "").trim()
    val value = pair.substringAfter('=', "").trim()
    if (key.isBlank() || value.isBlank()) return@forEach
    val headerName = when (key.lowercase()) {
      "user-agent", "useragent" -> "User-Agent"
      "referer", "referrer" -> "Referer"
      "origin" -> "Origin"
      else -> key
    }
    headers[headerName] = runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
  }
  return url to headers
}

private fun isM3uVodEntry(title: String, group: String?, declaredType: String?, duration: Double?, url: String): Boolean {
  val typeText = declaredType.orEmpty().lowercase()
  val categoryText = listOfNotNull(group, title).joinToString(" ").lowercase()
  val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault(url.lowercase())
  val extension = path.substringAfterLast('.', "").substringBefore('/')
  return duration?.let { it > 0.0 } == true ||
    listOf("vod", "movie", "film", "series", "episode", "show").any { it in typeText } ||
    listOf("vod", "movies", "movie", "films", "film", "series", "tv shows", "episodes").any { it in categoryText } ||
    m3uEpisodePattern.containsMatchIn(title) ||
    extension in m3uVodExtensions ||
    "/movie/" in path || "/series/" in path
}

/** Parses live and VOD entries from an extended M3U playlist. Finite/VOD entries are exposed as
 * directly playable movie items so they use the normal detail/player flow without being mistaken
 * for live TV. Common `url|User-Agent=...&Referer=...` headers are preserved for IPTV providers. */
internal fun parseM3u(
  body: String,
  sourceId: String,
  sourceName: String = "M3U Playlist",
  onProgress: (itemsParsed: Int, linesProcessed: Int, totalLines: Int) -> Unit = { _, _, _ -> },
): List<MediaItem> {
  require(body.contains("#EXTM3U", ignoreCase = true)) { "That URL doesn't look like an M3U playlist." }
  val items = mutableListOf<MediaItem>()
  var pendingTitle: String? = null
  var pendingLogo: String? = null
  var pendingGroup: String? = null
  var pendingMediaType: String? = null
  var pendingDuration: Double? = null
  val pendingHeaders = linkedMapOf<String, String>()
  var index = 0
  val totalLines = body.lineSequence().count().coerceAtLeast(1)
  var processedLines = 0
  body.lineSequence().forEach { rawLine ->
    processedLines += 1
    val line = rawLine.trim()
    when {
      line.isBlank() -> Unit
      line.startsWith("#EXTINF", ignoreCase = true) -> {
        val comma = line.indexOf(',')
        pendingTitle = if (comma >= 0) line.substring(comma + 1).trim().takeIf { it.isNotBlank() } else null
        pendingLogo = m3uTvgLogoRegex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        pendingGroup = m3uGroupTitleRegex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        pendingMediaType = m3uMediaTypeRegex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        pendingDuration = m3uDurationRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
        pendingHeaders.clear()
      }
      line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) -> pendingHeaders["User-Agent"] = line.substringAfter('=').trim()
      line.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) || line.startsWith("#EXTVLCOPT:http-referer=", ignoreCase = true) -> pendingHeaders["Referer"] = line.substringAfter('=').trim()
      line.startsWith("#EXTVLCOPT:http-origin=", ignoreCase = true) -> pendingHeaders["Origin"] = line.substringAfter('=').trim()
      line.startsWith("#") -> Unit
      else -> {
        val (streamUrl, inlineHeaders) = parseInlineM3uHeaders(line)
        val uri = runCatching { URI(streamUrl) }.getOrNull()
        if (uri?.scheme !in setOf("http", "https") || uri?.host.isNullOrBlank()) return@forEach
        val title = pendingTitle ?: "Item ${index + 1}"
        val isVod = isM3uVodEntry(title, pendingGroup, pendingMediaType, pendingDuration, streamUrl)
        val catalogKind = if (isVod) "vod" else "live"
        items.add(
          MediaItem(
            id = "$sourceId:$catalogKind:${index++}",
            type = if (isVod) "movie" else "tv",
            title = title,
            year = null,
            poster = pendingLogo,
            backdrop = pendingLogo,
            rating = null,
            description = pendingGroup?.let { "$it • $sourceName" } ?: sourceName,
            genres = listOfNotNull(pendingGroup),
            titleLogo = pendingLogo,
            sourceAddonId = sourceId,
            sourceAddonName = sourceName,
            sourceCatalogType = if (isVod) "movie" else "tv",
            sourceCatalogId = "$sourceId:$catalogKind",
            sourceCatalogName = pendingGroup ?: if (isVod) "$sourceName VOD" else sourceName,
            directStreamUrl = streamUrl,
            requestHeaders = pendingHeaders + inlineHeaders,
          ),
        )
        pendingTitle = null
        pendingLogo = null
        pendingGroup = null
        pendingMediaType = null
        pendingDuration = null
        pendingHeaders.clear()
      }
    }
    if (processedLines % 1_000 == 0) onProgress(items.size, processedLines, totalLines)
  }
  onProgress(items.size, processedLines, totalLines)
  return items
}
