package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import net.streamdek.mobile.BuildConfig
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

  /** Fallback identity for panels that only serve playlists to a recognised player. */
  private const val PLAYER_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

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
      requireNotNull(parsePlaylistUrl(normalized)) { "Enter a valid M3U playlist URL." }
      val existing = readAll()
      require(existing.none { it.url.equals(normalized, ignoreCase = true) }) { "This playlist is already added." }
      val source = M3uPlaylistSource(
        id = ID_PREFIX + Integer.toHexString(normalized.hashCode()),
        name = name?.trim()?.takeIf { it.isNotBlank() } ?: "Playlist ${existing.size + 1}",
        url = normalized,
        enabled = true,
        position = existing.size,
      )
      val items = streamAndParse(normalized, source.id, source.name, onProgress)
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
      streamAndParse(source.url, source.id, source.name, onProgress).toM3uPlaylistItems()
    }
  }

  /**
   * Accepts any absolute http(s) playlist URL, including the query-string forms IPTV panels use
   * (`…/get.php?username=…&type=m3u_plus&output=ts`, `…?d=TOKEN&type=m3u&output=ts`).
   *
   * This deliberately uses OkHttp's parser rather than [java.net.URI]. URI follows RFC 2396
   * strictly and throws on characters that appear routinely in provider tokens — `|`, `[`, `]`,
   * `{`, `}`, spaces — so a perfectly fetchable link was rejected at the input field with
   * "Enter a valid M3U playlist URL" and never even attempted. HttpUrl encodes those instead,
   * and is the same parser the request itself will use.
   */
  internal fun parsePlaylistUrl(rawUrl: String): HttpUrl? =
    rawUrl.toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }

  /**
   * Downloads and parses in a single pass.
   *
   * The playlist is never held in memory as a whole. A 200k-channel list is 50-80 MB of text,
   * which as a byte buffer plus the UTF-16 String it was decoded into came to roughly triple
   * that before parsing had even started — reliably an OutOfMemoryError. Lines are pulled off
   * the socket and turned into items as they arrive, so the only thing that grows is the item
   * list itself.
   */
  private fun streamAndParse(
    url: String,
    sourceId: String,
    sourceName: String,
    onProgress: (M3uLoadProgress) -> Unit,
  ): List<MediaItem> {
    openPlaylist(url).use { response ->
      val body = response.body
      val total = body.contentLength().takeIf { it > 0L }
      val counting = CountingInputStream(body.byteStream())
      // A large read buffer matters here: at ~2 lines per channel this reader is asked for a
      // line 400k times for a single big playlist.
      val reader = BufferedReader(InputStreamReader(counting, StandardCharsets.UTF_8), 128 * 1024)
      var lastReported = 0L
      val items = parseM3uLines(reader.lineSequence(), sourceId, sourceName) { parsed, _ ->
        val read = counting.bytesRead
        if (read - lastReported >= 512 * 1024) {
          lastReported = read
          val fraction = total?.let { (read.toDouble() / it).toFloat().coerceIn(0f, 0.99f) }
          onProgress(M3uLoadProgress("Reading $sourceName: ${parsed.formattedM3uCount()} items found", fraction, parsed))
        }
      }
      if (counting.bytesRead == 0L) throw IllegalStateException("Empty playlist response.")
      onProgress(M3uLoadProgress("Loaded ${items.size.formattedM3uCount()} items from $sourceName", 1f, items.size))
      return items
    }
  }

  /**
   * Opens the playlist, retrying once with a player user-agent on an auth-style rejection.
   *
   * Several IPTV panels gate `get.php` on a recognised client and answer an unknown user-agent
   * with 403 even when the credentials in the URL are valid. Retrying only on 401/403 keeps the
   * app's own identifier as the default for every provider that does not care.
   */
  private fun openPlaylist(url: String): Response {
    fun call(userAgent: String) = http
      .newCall(Request.Builder().url(url).header("User-Agent", userAgent).build())
      .execute()

    var response = call("StreamDek/${BuildConfig.VERSION_NAME}")
    if (response.code == 401 || response.code == 403) {
      response.close()
      response = call(PLAYER_USER_AGENT)
    }
    if (!response.isSuccessful) {
      val code = response.code
      response.close()
      throw IllegalStateException("Request failed: $code")
    }
    return response
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

/** Wraps a stream so download progress can be reported without buffering what was read. */
private class CountingInputStream(delegate: InputStream) : FilterInputStream(delegate) {
  var bytesRead: Long = 0L
    private set

  override fun read(): Int = super.read().also { if (it >= 0) bytesRead += 1 }

  override fun read(b: ByteArray, off: Int, len: Int): Int =
    super.read(b, off, len).also { if (it > 0) bytesRead += it }
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

/**
 * The path component of an absolute URL, lowercased, without allocating a URL object.
 *
 * [java.net.URI] was used here, which meant constructing (and for malformed provider links,
 * throwing from) one parser instance per entry. At 200k entries that alone dominated parse time,
 * and it silently fell back to matching against the whole URL — query string included — whenever
 * a link had a character URI refused.
 */
private fun m3uUrlPath(url: String): String {
  val schemeEnd = url.indexOf("://")
  val afterAuthority = url.indexOf('/', if (schemeEnd >= 0) schemeEnd + 3 else 0)
  if (afterAuthority < 0) return ""
  var end = url.length
  for (index in afterAuthority until url.length) {
    val char = url[index]
    if (char == '?' || char == '#') {
      end = index
      break
    }
  }
  return url.substring(afterAuthority, end).lowercase()
}

private val m3uVodTypeMarkers = listOf("vod", "movie", "film", "series", "episode", "show")
private val m3uVodCategoryMarkers = listOf("vod", "movies", "movie", "films", "film", "series", "tv shows", "episodes")

/**
 * Each test is evaluated only if the cheaper ones ahead of it did not already decide. The checks
 * used to be computed eagerly into locals, so every entry paid for three lowercased copies and a
 * list allocation whether or not the first condition had already answered the question.
 */
private fun isM3uVodEntry(title: String, group: String?, declaredType: String?, duration: Double?, url: String): Boolean {
  if (duration != null && duration > 0.0) return true
  if (!declaredType.isNullOrEmpty()) {
    val typeText = declaredType.lowercase()
    if (m3uVodTypeMarkers.any { it in typeText }) return true
  }
  val categoryText = if (group.isNullOrEmpty()) title.lowercase() else "$group $title".lowercase()
  if (m3uVodCategoryMarkers.any { it in categoryText }) return true
  if (m3uEpisodePattern.containsMatchIn(title)) return true
  val path = m3uUrlPath(url)
  if (path.substringAfterLast('/').substringAfterLast('.', "") in m3uVodExtensions) return true
  return "/movie/" in path || "/series/" in path
}

/** Parses a Kodi-style `#KODIPROP:inputstream.adaptive.license_key=` value into key-id -> key
 * pairs. Real playlists hex-encode both halves; multiple pairs for multi-key content are
 * separated by `&` (e.g. `keyid1:key1&keyid2:key2`), matching inputstream.adaptive's convention. */
private fun parseClearKeyPairs(value: String): Map<String, String> =
  value.split('&').mapNotNull { pair ->
    val separator = pair.indexOf(':')
    if (separator <= 0) return@mapNotNull null
    val keyId = pair.substring(0, separator).trim().lowercase()
    val key = pair.substring(separator + 1).trim().lowercase()
    if (keyId.isBlank() || key.isBlank()) null else keyId to key
  }.toMap()

/** Parses live and VOD entries from an extended M3U playlist. Finite/VOD entries are exposed as
 * directly playable movie items so they use the normal detail/player flow without being mistaken
 * for live TV. Common `url|User-Agent=...&Referer=...` headers are preserved for IPTV providers,
 * and `#KODIPROP:inputstream.adaptive.license_type`/`license_key` ClearKey DRM credentials
 * (common on Indian/Tamil IPTV playlists) are captured for Media3 playback. */
internal fun parseM3u(
  body: String,
  sourceId: String,
  sourceName: String = "M3U Playlist",
  onProgress: (itemsParsed: Int, linesProcessed: Int, totalLines: Int) -> Unit = { _, _, _ -> },
): List<MediaItem> {
  val totalLines = body.lineSequence().count().coerceAtLeast(1)
  return parseM3uLines(body.lineSequence(), sourceId, sourceName) { parsed, processed ->
    onProgress(parsed, processed, totalLines)
  }
}

/**
 * Streaming form of [parseM3u], and where the work actually happens.
 *
 * Takes lines rather than a body so a playlist can be parsed straight off the network without
 * ever existing in memory as one string — see M3uPlaylistManager.streamAndParse. Progress is
 * reported as (items parsed, lines processed); a line count is not known in advance when the
 * source is a socket, so the caller decides what to make of it.
 *
 * Repeated attribute values are interned. A provider list groups its channels into a few hundred
 * categories, and the derived description / catalog-name / genre strings are built from those, so
 * without interning a 200k-entry playlist holds close to a million near-duplicate strings.
 */
internal fun parseM3uLines(
  lines: Sequence<String>,
  sourceId: String,
  sourceName: String = "M3U Playlist",
  onProgress: (itemsParsed: Int, linesProcessed: Int) -> Unit = { _, _ -> },
): List<MediaItem> {
  val items = mutableListOf<MediaItem>()
  var pendingTitle: String? = null
  var pendingLogo: String? = null
  var pendingGroup: String? = null
  var pendingMediaType: String? = null
  var pendingDuration: Double? = null
  val pendingHeaders = linkedMapOf<String, String>()
  var pendingDrmLicenseType: String? = null
  val pendingDrmClearKeys = linkedMapOf<String, String>()
  var index = 0
  var processedLines = 0
  var charactersSeen = 0L
  // Providers that answer a bad token with an HTML error page still return 200. Rather than
  // reading the whole thing looking for a marker that will never come, give up once it is clear
  // this is not a playlist.
  var sawPlaylistMarker = false

  val pool = HashMap<String, String>()
  fun intern(value: String): String = pool.getOrPut(value) { value }
  // One shared single-element list per group, rather than one list per entry. Scoped to this
  // parse so nothing survives between playlists or races a concurrent parse.
  val genreLists = HashMap<String, List<String>>()

  val liveCatalogId = "$sourceId:live"
  val vodCatalogId = "$sourceId:vod"
  val vodFallbackCatalogName = "$sourceName VOD"

  lines.forEach { rawLine ->
    processedLines += 1
    charactersSeen += rawLine.length + 1
    // A UTF-8 BOM would otherwise leave the first line as "﻿#EXTM3U" and defeat the marker
    // check below.
    val line = (if (processedLines == 1) rawLine.removePrefix("﻿") else rawLine).trim()
    when {
      line.isEmpty() -> Unit
      line.startsWith("#EXTINF", ignoreCase = true) -> {
        sawPlaylistMarker = true
        val comma = line.indexOf(',')
        pendingTitle = if (comma >= 0) line.substring(comma + 1).trim().takeIf { it.isNotEmpty() } else null
        pendingLogo = m3uTvgLogoRegex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
        pendingGroup = m3uGroupTitleRegex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.let(::intern)
        pendingMediaType = m3uMediaTypeRegex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.let(::intern)
        pendingDuration = m3uDurationRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
        pendingHeaders.clear()
      }
      line.startsWith("#EXTM3U", ignoreCase = true) -> sawPlaylistMarker = true
      line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) -> pendingHeaders["User-Agent"] = line.substringAfter('=').trim()
      line.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) || line.startsWith("#EXTVLCOPT:http-referer=", ignoreCase = true) -> pendingHeaders["Referer"] = line.substringAfter('=').trim()
      line.startsWith("#EXTVLCOPT:http-origin=", ignoreCase = true) -> pendingHeaders["Origin"] = line.substringAfter('=').trim()
      // KODIPROP directives can precede or follow their entry's #EXTINF line (both conventions
      // appear in the wild), so unlike the #EXTINF-scoped fields above they're only cleared once
      // an entry is actually emitted below - never on #EXTINF itself.
      line.startsWith("#KODIPROP:inputstream.adaptive.license_type=", ignoreCase = true) ->
        pendingDrmLicenseType = line.substringAfter('=').trim().takeIf { it.isNotEmpty() }
      line.startsWith("#KODIPROP:inputstream.adaptive.license_key=", ignoreCase = true) ->
        pendingDrmClearKeys.putAll(parseClearKeyPairs(line.substringAfter('=').trim()))
      line.startsWith("#") -> Unit
      else -> {
        val (streamUrl, inlineHeaders) = parseInlineM3uHeaders(line)
        val title = pendingTitle ?: "Item ${index + 1}"
        val isVod = isM3uVodEntry(title, pendingGroup, pendingMediaType, pendingDuration, streamUrl)
        val group = pendingGroup
        items.add(
          MediaItem(
            id = "$sourceId:${if (isVod) "vod" else "live"}:${index++}",
            type = if (isVod) "movie" else "tv",
            title = title,
            year = null,
            poster = pendingLogo,
            backdrop = pendingLogo,
            rating = null,
            description = if (group == null) sourceName else intern("$group • $sourceName"),
            genres = if (group == null) emptyList() else genreLists.getOrPut(group) { listOf(group) },
            titleLogo = pendingLogo,
            sourceAddonId = sourceId,
            sourceAddonName = sourceName,
            sourceCatalogType = if (isVod) "movie" else "tv",
            sourceCatalogId = if (isVod) vodCatalogId else liveCatalogId,
            sourceCatalogName = group ?: if (isVod) vodFallbackCatalogName else sourceName,
            directStreamUrl = streamUrl,
            // Both maps are empty for the overwhelming majority of entries; sharing the one
            // immutable empty map avoids 200k throwaway allocations.
            requestHeaders = if (pendingHeaders.isEmpty() && inlineHeaders.isEmpty()) emptyMap() else pendingHeaders + inlineHeaders,
            drmLicenseType = pendingDrmLicenseType,
            drmClearKeys = if (pendingDrmClearKeys.isEmpty()) emptyMap() else pendingDrmClearKeys.toMap(),
          ),
        )
        pendingTitle = null
        pendingLogo = null
        pendingGroup = null
        pendingMediaType = null
        pendingDuration = null
        pendingHeaders.clear()
        pendingDrmLicenseType = null
        pendingDrmClearKeys.clear()
      }
    }
    if (!sawPlaylistMarker && charactersSeen > 256 * 1024) {
      throw IllegalArgumentException("That URL doesn't look like an M3U playlist.")
    }
    if (processedLines % 1_000 == 0) onProgress(items.size, processedLines)
  }
  // The marker check happens here rather than up front because the body is consumed as a stream:
  // there is nothing to scan ahead of time. A playlist missing "#EXTM3U" but full of "#EXTINF"
  // entries — which some panels emit — is still accepted.
  if (!sawPlaylistMarker) throw IllegalArgumentException("That URL doesn't look like an M3U playlist.")
  onProgress(items.size, processedLines)
  return items
}
