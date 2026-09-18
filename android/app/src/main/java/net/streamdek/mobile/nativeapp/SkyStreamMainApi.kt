@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.streamdek.mobile.nativeapp

import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import kotlin.uuid.Uuid

/**
 * One SkyStream source as a CloudStream provider.
 *
 * A `.sky` plugin is the same kind of thing a `.cs3` is — a catalogue (`getHome`), a search, a
 * title page (`load`) and a link resolver (`loadStreams`) — written in JavaScript instead of
 * compiled Kotlin. Presenting it through CloudStream's [MainAPI] is what lets every place the app
 * already uses CloudStream providers use these too, with nothing written twice: Home rows, the Fuse
 * page's catalogues and search, detail pages opened from a row, live channels, and the title
 * search behind a detail page's sources. Before this they were only ever asked for streams, through
 * a driver of their own that guessed at what `loadStreams` wanted.
 *
 * The mapping follows SkyStream's own app: `getHome()` answers every row in one call (so rows are
 * served from one cached reply), and `loadStreams` is given the chosen episode's `url` — for a film,
 * the first episode's — exactly as SkyStream's player does.
 *
 * The models are built with their constructors, which CloudStream marks deprecated in favour of its
 * `newX` builders: those are top-level functions, and the trimmed runtime jar carries no
 * `.kotlin_module` index for Kotlin to find top-level functions by — only classes resolve.
 */
internal class SkyStreamMainApi(
  val source: SkySource,
  displayName: String,
  private val manager: SkyStreamPluginManager,
) : MainAPI() {
  override var name: String = displayName
  override var mainUrl: String = source.baseUrl
  override var lang: String = source.language

  override val supportedTypes: Set<TvType> = skyProviderTypes(source.categories)

  // Rows are named by the plugin's own getHome() reply, which is only known once it has answered
  // once; see SkyStreamPluginManager.probe. Read on every pass so a probe that lands later shows up.
  override val hasMainPage: Boolean get() = manager.homeSections(source).isNotEmpty()
  override val mainPage: List<MainPageData> get() = manager.homeSections(source).map { MainPageData(it, it, false) }

  override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
    // getHome() has no paging: the whole row comes back on the first page.
    if (page > 1) return HomePageResponse(emptyList(), false)
    val items = manager.home(source)[request.name] ?: return HomePageResponse(emptyList(), false)
    return HomePageResponse(listOf(HomePageList(request.name, items.mapNotNull(::toSearchResponse), request.horizontalImages)), false)
  }

  override suspend fun search(query: String): List<SearchResponse> {
    val raw = manager.call(source, "search", listOf(query), SkyStreamPluginManager.SEARCH_TIMEOUT_MS) ?: return emptyList()
    val list = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let(::toSearchResponse) }
  }

  override suspend fun search(query: String, page: Int): SearchResponseList? =
    SearchResponseList(if (page > 1) emptyList() else search(query), false)

  override suspend fun load(url: String): LoadResponse? {
    val raw = manager.call(source, "load", listOf(url), SkyStreamPluginManager.LOAD_TIMEOUT_MS) ?: return null
    val item = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val (title, titleYear) = skyCleanTitle(item.optString("title")).takeIf { it.first.isNotBlank() } ?: return null
    val pageUrl = item.optString("url").ifBlank { url }
    val episodes = item.optJSONArray("episodes").objects()
    val type = skyItemType(item.optString("type")) ?: supportedTypes.firstOrNull() ?: TvType.Movie
    val poster = item.optString("posterUrl").ifBlank { null }
    val background = item.optString("backgroundPosterUrl").ifBlank { item.optString("bannerUrl") }.ifBlank { null }
    val plot = item.optString("description").ifBlank { null }
    val year = item.optInt("year", 0).takeIf { it > 0 } ?: titleYear
    val tags = item.optJSONArray("tags").strings()
    val firstData = episodes.firstOrNull()?.optString("url")?.ifBlank { null } ?: pageUrl

    val response: LoadResponse = when {
      type == TvType.Live -> LiveStreamLoadResponse(
        name = title, url = pageUrl, apiName = name, dataUrl = firstData, posterUrl = poster, year = year, plot = plot,
      ).apply { backgroundPosterUrl = background }
      // SkyStream's own test for "plays as one item": a film type, or a single entry to play.
      type in FILM_TYPES || episodes.size <= 1 && type !in SERIES_TYPES -> MovieLoadResponse(
        name = title, url = pageUrl, apiName = name, type = type, dataUrl = firstData, posterUrl = poster, year = year, plot = plot,
      ).apply {
        backgroundPosterUrl = background
        this.tags = tags
      }
      else -> {
        // A plugin that never numbers its seasons leaves every episode at the host default of 0;
        // those are one season, not a run of specials.
        val unnumbered = episodes.all { it.optInt("season", 0) <= 0 }
        val mapped = episodes.mapIndexedNotNull { index, episode ->
          val data = episode.optString("url").ifBlank { return@mapIndexedNotNull null }
          Episode(
            data = data,
            name = episode.optString("name").ifBlank { null },
            season = if (unnumbered) 1 else episode.optInt("season", 0),
            episode = episode.optInt("episode", 0).takeIf { it > 0 } ?: (index + 1),
            posterUrl = episode.optString("posterUrl").ifBlank { null },
            description = episode.optString("description").ifBlank { null },
            runTime = (episode.optInt("runtime", 0).takeIf { it > 0 } ?: episode.optInt("duration", 0)).takeIf { it > 0 },
          )
        }
        TvSeriesLoadResponse(
          name = title, url = pageUrl, apiName = name, type = type, episodes = mapped, posterUrl = poster, year = year, plot = plot,
        ).apply {
          backgroundPosterUrl = background
          this.tags = tags
        }
      }
    }
    item.optString("imdbId").takeIf { it.startsWith("tt") }?.let { response.addImdbId(it) }
    item.optInt("tmdbId", 0).takeIf { it > 0 }?.let { response.addTMDbId(it.toString()) }
    return response
  }

  override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
  ): Boolean {
    val raw = manager.call(source, "loadStreams", listOf(data), SkyStreamPluginManager.STREAM_TIMEOUT_MS) ?: return false
    val list = runCatching { JSONArray(raw) }.getOrNull() ?: return false
    var found = false
    list.objects().take(MAX_STREAMS).forEach { stream ->
      val link = runCatching { toExtractorLink(stream) }
        .onFailure { Log.w(TAG, "$name: unusable stream ${stream.optString("url").take(80)}", it) }
        .getOrNull() ?: return@forEach
      callback(link)
      found = true
      stream.optJSONArray("subtitles").objects().forEach { subtitle ->
        val url = subtitle.optString("url").ifBlank { return@forEach }
        val label = subtitle.optString("label").ifBlank { subtitle.optString("lang") }.ifBlank { subtitle.optString("language") }.ifBlank { "Subtitles" }
        runCatching { subtitleCallback(SubtitleFile(label, url)) }
      }
    }
    return found
  }

  /**
   * A `StreamResult` as a link the player can open. SkyStream's player unwraps its three special
   * URL forms through a local proxy; StreamDek's applies request headers to every request an HLS
   * stream makes, so the first two only need unwrapping into a URL plus headers.
   */
  private fun toExtractorLink(stream: JSONObject): ExtractorLink? {
    var url = stream.optString("url").trim()
    if (url.isEmpty()) return null
    val headers = linkedMapOf<String, String>()
    stream.optJSONObject("headers")?.let { json -> json.keys().forEach { key -> json.optString(key).takeIf { it.isNotBlank() }?.let { headers[key] = it } } }
    when {
      url.startsWith(MAGIC_PROXY_V2) -> {
        val config = JSONObject(String(skyBase64(url.removePrefix(MAGIC_PROXY_V2)), Charsets.UTF_8))
        url = config.getString("url")
        config.optJSONObject("headers")?.let { json -> headers.clear(); json.keys().forEach { key -> headers[key] = json.optString(key) } }
      }
      url.startsWith(MAGIC_PROXY_V1) || url.startsWith(MAGIC_PROXY_COLON) -> {
        val encoded = if (url.startsWith(MAGIC_PROXY_V1)) url.removePrefix(MAGIC_PROXY_V1) else url.removePrefix(MAGIC_PROXY_COLON)
        url = String(skyBase64(encoded), Charsets.UTF_8)
      }
      url.startsWith(MAGIC_M3U8, ignoreCase = true) -> {
        val playlist = String(skyBase64(url.substring(MAGIC_M3U8.length)), Charsets.UTF_8)
        url = manager.writeLocalPlaylist(playlist).toURI().toString()
      }
    }
    val magnet = url.startsWith("magnet:", ignoreCase = true)
    if (!magnet && headers.keys.none { it.equals("User-Agent", true) }) headers["User-Agent"] = SkyStreamRuntime.BROWSER_USER_AGENT
    val label = stream.optString("source").ifBlank { stream.optString("name") }.ifBlank { stream.optString("title") }.ifBlank { name }
    val quality = skyQualityOf(stream.optString("quality").ifBlank { null } ?: label)
    val type = when {
      magnet -> ExtractorLinkType.MAGNET
      url.substringBefore('?').endsWith(".mpd", true) -> ExtractorLinkType.DASH
      url.substringBefore('?').endsWith(".m3u8", true) || url.contains(".m3u8?", true) -> ExtractorLinkType.M3U8
      else -> null
    }
    val kid = stream.optString("drmKid").ifBlank { null }
    val key = stream.optString("drmKey").ifBlank { null }
    val licenseUrl = stream.optString("licenseUrl").ifBlank { null }
    val referer = headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value.orEmpty()
    if ((kid != null && key != null) || licenseUrl != null) {
      return DrmExtractorLink(
        source = name, name = label, url = url, referer = referer, quality = quality,
        type = type ?: ExtractorLinkType.VIDEO, headers = headers,
        kid = kid, key = key, uuid = if (licenseUrl != null) WIDEVINE_UUID else CLEARKEY_UUID, licenseUrl = licenseUrl,
      )
    }
    return ExtractorLink(
      source = name, name = label, url = url, referer = referer, quality = quality, headers = headers,
      type = type ?: if (url.substringBefore('?').endsWith(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
    )
  }

  private fun toSearchResponse(item: JSONObject): SearchResponse? {
    val (title, titleYear) = skyCleanTitle(item.optString("title")).takeIf { it.first.isNotBlank() } ?: return null
    val url = item.optString("url").trim().ifBlank { return null }
    // Null when the plugin did not say: the CloudStream search walk treats an untyped result as a
    // candidate for both films and series, where a guessed type would rule half of them out.
    val type = item.optString("type").ifBlank { null }?.let(::skyItemType)
    val poster = item.optString("posterUrl").ifBlank { null }
    val year = item.optInt("year", 0).takeIf { it > 0 } ?: titleYear
    return when {
      type == TvType.Live -> LiveSearchResponse(name = title, url = url, apiName = name, type = TvType.Live, posterUrl = poster)
      type != null && type in SERIES_TYPES -> TvSeriesSearchResponse(name = title, url = url, apiName = name, type = type, posterUrl = poster, year = year)
      else -> MovieSearchResponse(name = title, url = url, apiName = name, type = type, posterUrl = poster, year = year)
    }
  }

  companion object {
    private const val TAG = "SkyStreamMainApi"
    private const val MAX_STREAMS = 200
    private const val MAGIC_PROXY_V1 = "MAGIC_PROXY_v1"
    private const val MAGIC_PROXY_V2 = "MAGIC_PROXY_v2"
    private const val MAGIC_PROXY_COLON = "MAGIC_PROXY:"
    private const val MAGIC_M3U8 = "magic_m3u8:"
    private val CLEARKEY_UUID = Uuid.parse("e2719d58-a985-b3c9-781a-b030af78d30e")
    private val WIDEVINE_UUID = Uuid.parse("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    private val FILM_TYPES = setOf(TvType.Movie, TvType.AnimeMovie, TvType.Documentary, TvType.Torrent)
    private val SERIES_TYPES = setOf(TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA, TvType.AsianDrama)
  }
}

/** One source a `.sky` bundle provides: the plugin itself, or one of the sub-providers it lists. */
internal data class SkySource(
  val repoUrl: String,
  val packageName: String,
  val pluginName: String,
  /** A sub-provider's id, or null for the plugin itself. */
  val subId: String?,
  val subName: String?,
  val baseUrl: String,
  val categories: List<String>,
  val language: String,
  val filePath: String,
  val version: Int,
  /**
   * The id the script is told through `manifest.providerId` — for a sub-provider that has no
   * address of its own and is picked out by id instead, as SkyStream does. Null otherwise.
   */
  val providerId: String? = null,
) {
  /** Stable across launches; what cached rows and home sections are stored under. */
  val key: String get() = listOfNotNull(repoUrl, packageName, subId).joinToString("|")
  val displayName: String get() = subName?.takeIf { it.isNotBlank() } ?: pluginName
}

/** SkyStream's category words (`Movie`, `TvSeries`, `LiveTv`, `Anime`…) as CloudStream types. */
internal fun skyProviderTypes(categories: List<String>): Set<TvType> {
  val types = categories.flatMap { category ->
    when (category.trim().lowercase().replace(Regex("[^a-z]"), "")) {
      "movie", "movies", "film", "films" -> listOf(TvType.Movie)
      "tv", "series", "tvseries", "tvshow", "tvshows", "shows" -> listOf(TvType.TvSeries)
      "anime" -> listOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
      "cartoon", "cartoons" -> listOf(TvType.Cartoon)
      "livetv", "live", "livestream", "iptv", "sports", "sport" -> listOf(TvType.Live)
      "documentary", "documentaries" -> listOf(TvType.Documentary)
      "asiandrama", "kdrama", "drama", "dramas" -> listOf(TvType.AsianDrama)
      "others", "other" -> listOf(TvType.Others)
      else -> emptyList()
    }
  }.toSet()
  return types.ifEmpty { setOf(TvType.Movie, TvType.TvSeries) }
}

/** An item's own `type`, or null for one SkyStream does not recognise. */
internal fun skyItemType(value: String): TvType? = when (value.trim().lowercase().replace(Regex("[^a-z]"), "")) {
  "movie", "movies", "film" -> TvType.Movie
  "series", "tv", "tvseries", "tvshow", "show" -> TvType.TvSeries
  "anime" -> TvType.Anime
  "animemovie" -> TvType.AnimeMovie
  "ova" -> TvType.OVA
  "cartoon" -> TvType.Cartoon
  "livestream", "live", "livetv", "iptv", "channel" -> TvType.Live
  "documentary" -> TvType.Documentary
  "asiandrama", "drama", "kdrama" -> TvType.AsianDrama
  "torrent" -> TvType.Torrent
  "others", "other" -> TvType.Others
  else -> null
}

/**
 * A title as a catalogue would name it, and the year a release name carries.
 *
 * Scrapers of release sites hand back the release name — "Avengers: Endgame (2019) BluRay [Hindi
 * (DD5.1) & English] 1080p 720p" or "Avengers: Endgame Hindi Dubbed". The stream search matches a
 * catalogue title to provider results by exact normalised title, as it does for CloudStream, so
 * those never matched and these sources answered nothing for any title opened from the catalogue.
 * Cut at the year, then drop dub and release tags; what is left is compared as before, so a genuinely
 * different title still does not match.
 */
internal fun skyCleanTitle(raw: String): Pair<String, Int?> {
  var title = raw.replace(Regex("\\s+"), " ").trim()
  var year: Int? = null
  Regex("""^(.{2,}?)[\s\-–:]*[(\[]((?:19|20)\d{2})[)\]]""").find(title)?.let { match ->
    title = match.groupValues[1]
    year = match.groupValues[2].toInt()
  }
  title = title
    .replace(Regex("""\s+(?:Unofficial\s+)?(?:Hindi|Tamil|Telugu|Malayalam|Kannada|Bengali|Punjabi|Marathi|Urdu|English)\s+Dub(?:bed)?\b.*$""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s+(?:Dual|Multi)\s+Audio\b.*$""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s+(?:WEB-?DL|WEB-?Rip|BluRay|BRRip|HDRip|HDTC|HDCAM|HDTS|CAMRip|DVDRip|480p|720p|1080p|2160p)\b.*$""", RegexOption.IGNORE_CASE), "")
    .trim()
    .trimEnd('-', '–', ':', '|')
    .trim()
  return title.ifBlank { raw.trim() } to year
}

/** A height out of free text such as "1080p [MKV]" or "4K HDR"; 0 when there is none. */
internal fun skyQualityOf(text: String): Int {
  val value = text.lowercase()
  Regex("""(\d{3,4})p""").find(value)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
  return when {
    "2160" in value || "4k" in value || "uhd" in value -> 2160
    "1440" in value || "2k" in value -> 1440
    "1080" in value || "fhd" in value -> 1080
    "720" in value -> 720
    "480" in value -> 480
    "360" in value -> 360
    else -> 0
  }
}

internal fun skyBase64(value: String): ByteArray {
  var clean = value.trim().replace(Regex("\\s+"), "").replace('-', '+').replace('_', '/')
  while (clean.length % 4 != 0) clean += "="
  return Base64.getDecoder().decode(clean)
}

private fun JSONArray?.objects(): List<JSONObject> =
  if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
  if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
