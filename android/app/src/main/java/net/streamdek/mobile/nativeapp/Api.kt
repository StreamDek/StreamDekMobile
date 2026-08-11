package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import net.streamdek.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val SESSION_PREFS = "streamdek_native_session"
private const val SESSION_TOKEN_KEY = "token"
private const val SESSION_USER_JSON_KEY = "user_json"
private const val PROFILE_PREFS = "streamdek_native_profiles"
private const val GUEST_PROFILE_PREFS = "streamdek_native_guest_profiles"
private const val WATCHLIST_PREFS = "streamdek_native_watchlist"
private const val FAVOURITE_CHANNELS_PREFS = "streamdek_native_favourite_channels"
private const val CLIENT_IDENTITY_PREFS = "streamdek_native_client_identity"
private const val CLIENT_DEVICE_ID_KEY = "device_id"
private const val CLIENT_PREVIOUS_DEVICE_ID_KEY = "previous_device_id"
private const val HOME_CATALOG_PREVIEW_LIMIT = 20

private data class ClientIdentity(
  val deviceId: String,
  val sessionId: String,
  val previousDeviceId: String?,
  val deviceName: String,
)

private class ClientIdentityStore(context: Context) {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(CLIENT_IDENTITY_PREFS, Context.MODE_PRIVATE)

  fun load(): ClientIdentity {
    val androidId = android.provider.Settings.Secure.getString(appContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
      ?.takeIf { it.isNotBlank() }
      ?: android.os.Build.FINGERPRINT
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("streamdek:phone:$androidId".toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
    val deviceId = "sd-phone-${digest.take(32)}"
    val stored = prefs.getString(CLIENT_DEVICE_ID_KEY, null)?.takeIf(String::isNotBlank)
    if (stored != null && stored != deviceId && prefs.getString(CLIENT_PREVIOUS_DEVICE_ID_KEY, null).isNullOrBlank()) {
      prefs.edit().putString(CLIENT_PREVIOUS_DEVICE_ID_KEY, stored).apply()
    }
    if (stored != deviceId) prefs.edit().putString(CLIENT_DEVICE_ID_KEY, deviceId).apply()
    val sessionDigest = MessageDigest.getInstance("SHA-256")
      .digest("streamdek-session:$deviceId".toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
    val maker = android.os.Build.MANUFACTURER.trim().replaceFirstChar { it.uppercase() }
    val model = android.os.Build.MODEL.trim()
    val baseName = listOf(maker, model).filter { it.isNotBlank() }.distinct().joinToString(" ").ifBlank { "Android phone" }
    return ClientIdentity(
      deviceId = deviceId,
      sessionId = "session-${sessionDigest.take(32)}",
      previousDeviceId = prefs.getString(CLIENT_PREVIOUS_DEVICE_ID_KEY, null)?.takeIf { it.isNotBlank() && it != deviceId },
      deviceName = "$baseName [${deviceId.takeLast(6).uppercase()}]",
    )
  }
}

class SessionStore(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)

  fun load(): AuthSession? {
    val token = prefs.getString(SESSION_TOKEN_KEY, null) ?: return null
    val userJson = prefs.getString(SESSION_USER_JSON_KEY, null) ?: return null
    return runCatching {
      AuthSession(token = token, user = parseSessionUser(JSONObject(userJson), token))
    }.getOrNull()
  }

  fun save(session: AuthSession) {
    prefs.edit()
      .putString(SESSION_TOKEN_KEY, session.token)
      .putString(SESSION_USER_JSON_KEY, serializeSessionUser(session.user).toString())
      .apply()
  }

  fun clear() {
    prefs.edit().clear().apply()
  }
}

class ProfileSelectionStore(context: Context) {
  private val prefs = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)

  fun save(userId: String, profileId: String?) {
    if (profileId.isNullOrBlank()) {
      prefs.edit().remove(key(userId)).apply()
    } else {
      prefs.edit().putString(key(userId), profileId).apply()
    }
  }

  fun load(userId: String): String? = prefs.getString(key(userId), null)

  private fun key(userId: String): String = "streamdek_active_profile_$userId"
}

class GuestProfileStore(context: Context) {
  private val prefs = context.getSharedPreferences(GUEST_PROFILE_PREFS, Context.MODE_PRIVATE)

  fun load(): List<StreamProfile> {
    val raw = prefs.getString("profiles", null) ?: return emptyList()
    return runCatching {
      val source = JSONArray(raw)
      buildList {
        for (index in 0 until source.length()) {
          val profile = source.optJSONObject(index) ?: continue
          val id = profile.optString("id")
          val name = profile.optString("name")
          if (id.isBlank() || name.isBlank()) continue
          add(StreamProfile(id, "guest", name, profile.optInt("avatarIndex").coerceIn(0, 11), false, profile.optBoolean("isDefault"), null, null))
        }
      }
    }.getOrDefault(emptyList())
  }

  fun save(profiles: List<StreamProfile>) {
    val array = JSONArray()
    profiles.forEach { profile ->
      array.put(JSONObject().put("id", profile.id).put("name", profile.name).put("avatarIndex", profile.avatarIndex).put("isDefault", profile.isDefault))
    }
    prefs.edit().putString("profiles", array.toString()).apply()
  }
}

class WatchlistStore(context: Context) {
  private val prefs = context.getSharedPreferences(WATCHLIST_PREFS, Context.MODE_PRIVATE)

  fun load(ownerKey: String): List<MediaItem> {
    val raw = prefs.getString(ownerKey, null) ?: return emptyList()
    return runCatching {
      val source = JSONArray(raw)
      buildList {
        for (index in 0 until source.length()) {
          val item = source.optJSONObject(index) ?: continue
          add(parseMediaItem(item))
        }
      }
    }.getOrDefault(emptyList())
  }

  fun save(ownerKey: String, items: List<MediaItem>) {
    val array = JSONArray()
    items.forEach { item ->
      array.put(
        JSONObject()
          .put("id", item.id)
          .put("tmdbId", item.id.toIntOrNull())
          .put("type", item.type)
          .put("title", item.title)
          .put("year", item.year)
          .put("poster", item.poster)
          .put("backdrop", item.backdrop)
          .put("rating", item.rating)
          .put("description", item.description)
          .put("genres", JSONArray(item.genres))
          .put("addedAt", item.addedAt)
          .put("updatedAt", item.updatedAt),
      )
    }
    prefs.edit().putString(ownerKey, array.toString()).apply()
  }

  fun clear(ownerKey: String) {
    prefs.edit().remove(ownerKey).apply()
  }
}

class FavouriteChannelStore(context: Context) {
  private val prefs = context.getSharedPreferences(FAVOURITE_CHANNELS_PREFS, Context.MODE_PRIVATE)

  fun load(ownerKey: String): List<MediaItem> {
    val raw = prefs.getString(ownerKey, null) ?: return emptyList()
    return runCatching {
      val source = JSONArray(raw)
      buildList {
        for (index in 0 until source.length()) {
          val item = source.optJSONObject(index) ?: continue
          add(parseFavouriteChannel(item))
        }
      }
        // Channel id is the identity everything else keys on — the star, the drawer and the
        // favourite badges all ask "is any entry this id". The same channel can nonetheless have
        // been stored more than once with different source metadata (a card and the player
        // disagree about which add-on it came from), and a duplicate is invisible until it makes
        // removing a favourite take as many taps as there are copies. Collapse on read so a
        // stored list can only ever hold one entry per channel.
        .distinctBy { it.id }
    }.getOrDefault(emptyList())
  }

  fun save(ownerKey: String, items: List<MediaItem>) {
    val array = JSONArray()
    items.forEach { item ->
      array.put(
        JSONObject()
          .put("id", item.id)
          .put("type", item.type)
          .put("title", item.title)
          .put("year", item.year)
          .put("poster", item.poster)
          .put("backdrop", item.backdrop)
          .put("rating", item.rating)
          .put("description", item.description)
          .put("genres", JSONArray(item.genres))
          .put("addedAt", item.addedAt)
          .put("updatedAt", item.updatedAt)
          .put("sourceAddonId", item.sourceAddonId)
          .put("sourceAddonName", item.sourceAddonName)
          .put("sourceCatalogType", item.sourceCatalogType)
          .put("sourceCatalogId", item.sourceCatalogId)
          .put("sourceCatalogName", item.sourceCatalogName)
          .put("directStreamUrl", item.directStreamUrl),
      )
    }
    prefs.edit().putString(ownerKey, array.toString()).apply()
  }

  fun clear(ownerKey: String) {
    prefs.edit().remove(ownerKey).apply()
  }
}

private fun parseFavouriteChannel(item: JSONObject): MediaItem = MediaItem(
  id = item.optString("id"),
  type = item.optString("type").ifBlank { "tv" },
  title = item.optString("title"),
  year = item.optString("year").takeIf { it.isNotBlank() && it != "null" },
  poster = item.optString("poster").takeIf { it.isNotBlank() },
  backdrop = item.optString("backdrop").takeIf { it.isNotBlank() },
  rating = parseRatingValue(item),
  description = item.optString("description"),
  genres = parseGenreNames(item),
  addedAt = parseFlexibleTimestamp(item, "addedAt"),
  updatedAt = parseFlexibleTimestamp(item, "updatedAt"),
  sourceAddonId = item.optString("sourceAddonId").takeIf { it.isNotBlank() && it != "null" },
  sourceAddonName = item.optString("sourceAddonName").takeIf { it.isNotBlank() && it != "null" },
  sourceCatalogType = item.optString("sourceCatalogType").takeIf { it.isNotBlank() && it != "null" },
  sourceCatalogId = item.optString("sourceCatalogId").takeIf { it.isNotBlank() && it != "null" },
  sourceCatalogName = item.optString("sourceCatalogName").takeIf { it.isNotBlank() && it != "null" },
  directStreamUrl = item.optString("directStreamUrl").takeIf { it.isNotBlank() && it != "null" },
)

data class FavouriteChannelsEnvelope(val items: List<MediaItem>, val updatedAt: Long)

data class TraktScrobblePayload(
  val mediaId: String,
  val mediaType: String,
  val title: String,
  val year: Int?,
  val progress: Double,
  val seasonNumber: Int? = null,
  val episodeNumber: Int? = null,
  val episodeTitle: String? = null,
)

data class DiscoverGenre(
  val id: Int,
  val name: String,
)

data class DiscoverPage(
  val items: List<MediaItem>,
  val page: Int,
  val totalPages: Int,
)

private const val ADDON_CACHE_DIRECTORY = "addon-http"
private const val ADDON_CACHE_MAX_BYTES = 12L * 1024L * 1024L

/**
 * How long an add-on's answer may be reused. Deliberately short: many add-ons hand back
 * time-limited playback URLs, so this is only meant to absorb opening a title, backing out and
 * returning to it within one sitting - not to hold results across a session.
 */
private const val ADDON_CACHE_SECONDS = 90

/**
 * Gives add-on responses a cache lifetime they almost never declare themselves.
 *
 * Stremio add-ons overwhelmingly send no caching headers at all, and OkHttp will not store a
 * response it has not been told it may store - so without this the cache above would sit empty
 * and every re-open of a title would hit the add-on again. That is what drains the per-IP request
 * quotas some add-ons enforce.
 *
 * An add-on that does express an opinion keeps it, `no-store` included. `Pragma: no-cache` is
 * dropped only when we are supplying the policy, since HTTP/1.0's spelling of "never store this"
 * would otherwise override the header being added here.
 */
private object AddonResponseCacheInterceptor : Interceptor {
  override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
    val response = chain.proceed(chain.request())
    if (!response.isSuccessful) return response
    val declared = response.header("Cache-Control").orEmpty()
    val addonHasAnOpinion = declared.contains("no-store", ignoreCase = true) ||
      declared.contains("max-age", ignoreCase = true) ||
      declared.contains("s-maxage", ignoreCase = true)
    if (addonHasAnOpinion) return response
    return response.newBuilder()
      .header("Cache-Control", "private, max-age=$ADDON_CACHE_SECONDS")
      .removeHeader("Pragma")
      .build()
  }
}

/** Per-account capabilities that decide how streams are fetched. */
data class AddonEntitlements(val ultra: Boolean = false, val serverSideStreams: Boolean = false)

/**
 * The chosen torrent is not on the user's debrid service yet — a provider accepted the magnet and
 * is fetching it onto its own servers now.
 *
 * Typed rather than a plain failure because it is not one: the source is fine and will usually
 * play in a few minutes. It also has to survive the torrent-engine fallback, which reports its
 * own unrelated errors on the way to giving up and would otherwise be the message shown.
 */
class DebridDownloadingException(message: String) : IllegalStateException(message)

class StreamDekApiClient(context: Context? = null) {
  private val appContext = context?.applicationContext
  private val clientIdentity = appContext?.let { ClientIdentityStore(it).load() }
  private val client = OkHttpClient()
  private val directStreamClient = client.newBuilder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .callTimeout(150, TimeUnit.SECONDS)
    // Without a Cache installed, OkHttp does not store responses at all, so removing the old
    // cache-busting query parameter on its own would have changed nothing.
    .apply {
      appContext?.let { context ->
        runCatching { cache(Cache(File(context.cacheDir, ADDON_CACHE_DIRECTORY), ADDON_CACHE_MAX_BYTES)) }
      }
    }
    .addNetworkInterceptor(AddonResponseCacheInterceptor)
    .build()
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
  val apiBaseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

  suspend fun restoreSession(sessionStore: SessionStore): AuthSession? {
    val existing = sessionStore.load() ?: return null
    val response = execute(
      Request.Builder()
        .url("$apiBaseUrl/auth/me")
        .headers(authHeaders(existing, includeContentType = true))
        .build(),
    )
    if (!response.ok) {
      sessionStore.clear()
      return null
    }
    val user = parseSessionUser(response.json.optJSONObject("user") ?: JSONObject(), existing.token)
    return AuthSession(existing.token, user).also(sessionStore::save)
  }

  suspend fun login(email: String, password: String): Result<AuthSession> =
    authPost("/auth/login", JSONObject().put("email", email).put("password", password))

  suspend fun register(email: String, password: String): Result<AuthSession> =
    authPost("/auth/register", JSONObject().put("email", email).put("password", password))

  suspend fun requestPasswordReset(email: String): Result<String?> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/auth/password-reset/request",
        JSONObject().put("email", email),
      )
      ensureOk(response, "Could not request password reset")
      response.json.optString("devResetCode").ifBlank { null }
    }
  }

  suspend fun confirmPasswordReset(email: String, token: String, newPassword: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = executeJson(
          "/auth/password-reset/confirm",
          JSONObject()
            .put("email", email)
            .put("token", token)
            .put("newPassword", newPassword),
        )
        ensureOk(response, "Could not reset password")
      }
    }

  suspend fun fetchHomeSections(session: AuthSession?, addons: List<InstalledAddon> = emptyList(), profileId: String? = null): Result<List<MediaSection>> = withContext(Dispatchers.IO) {
    runCatching {
      val builtInRequests = listOf(
        Triple("new_movies", "New Movies", "/tmdb/discover?type=movie&sort_by=primary_release_date.desc"),
        Triple("new_series", "New Series", "/tmdb/discover?type=tv&sort_by=first_air_date.desc"),
        Triple("streaming_networks", "Streaming Networks", "/tmdb/networks"),
        Triple("trending_movies", "Trending Movies", "/tmdb/trending/movie"),
        Triple("trending_series", "Trending Series", "/tmdb/trending/tv"),
      )
      val sections = supervisorScope {
        builtInRequests.map { (id, title, path) -> async { MediaSection(id, title, fetchMediaList(path)) } }.awaitAll().toMutableList()
      }
      sections += fetchAddonHomeSections(session, addons, profileId)
      sections
    }
  }

  suspend fun search(query: String, page: Int = 1): Result<SearchPage> = withContext(Dispatchers.IO) {
    runCatching {
      if (query.isBlank()) {
        SearchPage(items = emptyList(), page = 1, totalPages = 1)
      } else {
        val encoded = encodeQuery(query)
        val pageParam = encodeQuery(page.toString())
        val candidates = listOf("/tmdb/search?q=$encoded&page=$pageParam", "/tmdb/search?query=$encoded&page=$pageParam")
        var lastError: Throwable? = null
        for (path in candidates) {
          val result = runCatching {
            val response = execute(Request.Builder().url("$apiBaseUrl$path").build())
            ensureOk(response, "Failed to search titles")
            SearchPage(
              items = (response.json.optJSONArray("results") ?: JSONArray()).toMediaItems(),
              page = response.json.optInt("page").takeIf { it > 0 } ?: page,
              totalPages = response.json.optInt("total_pages").takeIf { it > 0 } ?: 1,
            )
          }
          result.onSuccess {
            if (it.items.isNotEmpty() || it.page > 1 || it.totalPages > 1) return@runCatching it
          }
          result.onFailure { lastError = it }
        }
        lastError?.let { throw it }
        SearchPage(items = emptyList(), page = page, totalPages = 1)
      }
    }
  }

  suspend fun fetchNetworkCatalog(
    networkId: String,
    page: Int,
    type: String,
    genreId: Int?,
    year: String?,
    sort: String,
    query: String,
  ): Result<DiscoverPage> = withContext(Dispatchers.IO) {
    runCatching {
      val params = mutableListOf("page=" + encodeQuery(page.toString()), "sort=" + encodeQuery(sort))
      if (type != "all") params += "type=" + encodeQuery(type)
      genreId?.let { params += "genre_id=" + encodeQuery(it.toString()) }
      if (!year.isNullOrBlank()) params += "year=" + encodeQuery(year)
      if (query.isNotBlank()) params += "search=" + encodeQuery(query)
      val response = execute(Request.Builder().url(apiBaseUrl + "/tmdb/network/" + encodeQuery(networkId) + "?" + params.joinToString("&")).build())
      ensureOk(response, "Failed to load network catalog")
      DiscoverPage(
        items = (response.json.optJSONArray("results") ?: JSONArray()).toMediaItems(),
        page = response.json.optInt("page").takeIf { it > 0 } ?: page,
        totalPages = response.json.optInt("total_pages").takeIf { it > 0 } ?: 1,
      )
    }
  }
  suspend fun fetchDiscoverGenres(type: String): Result<List<DiscoverGenre>> = withContext(Dispatchers.IO) {
    runCatching {
      val normalizedType = normalizeMediaType(type)
      val response = execute(Request.Builder().url("$apiBaseUrl/tmdb/genres/${encodeQuery(normalizedType)}").build())
      ensureOk(response, "Failed to load genres")
      val genres = response.json.optJSONArray("genres") ?: JSONArray()
      buildList {
        for (index in 0 until genres.length()) {
          val item = genres.optJSONObject(index) ?: continue
          val id = item.optInt("id")
          val name = item.optString("name").trim()
          if (id > 0 && name.isNotBlank()) add(DiscoverGenre(id = id, name = name))
        }
      }
    }
  }

  suspend fun fetchDiscover(type: String, page: Int, genreId: Int?, year: String?): Result<DiscoverPage> = withContext(Dispatchers.IO) {
    runCatching {
      val requestedType = type.trim().lowercase()
      val isDocumentary = requestedType == "documentary"
      val effectiveType = if (isDocumentary) "movie" else normalizeMediaType(requestedType)
      val params = mutableListOf(
        "type=${encodeQuery(effectiveType)}",
        "page=${encodeQuery(page.toString())}"
      )
      if (isDocumentary) {
        params += "genre_id=99"
      } else if (genreId != null) {
        params += "genre_id=${encodeQuery(genreId.toString())}"
      }
      if (!year.isNullOrBlank()) {
        if (year.startsWith("before:")) {
          val beforeYear = year.removePrefix("before:").trim()
          if (beforeYear.isNotBlank()) {
            val cutoff = encodeQuery("${beforeYear}-12-31")
            if (effectiveType == "tv") {
              params += "first_air_date.lte=$cutoff"
            } else {
              params += "primary_release_date.lte=$cutoff"
            }
          }
        } else {
          params += "year=${encodeQuery(year)}"
        }
      }
      val response = execute(Request.Builder().url("$apiBaseUrl/tmdb/discover?${params.joinToString("&")}").build())
      ensureOk(response, "Failed to load discover titles")
      DiscoverPage(
        items = (response.json.optJSONArray("results") ?: JSONArray()).toMediaItems(),
        page = response.json.optInt("page").takeIf { it > 0 } ?: page,
        totalPages = response.json.optInt("total_pages").takeIf { it > 0 } ?: 1,
      )
    }
  }
  suspend fun resolvePluginMediaId(type: String, id: String): String = withContext(Dispatchers.IO) {
    val candidate = id.substringBefore(":").trim()
    if (!candidate.startsWith("tt", ignoreCase = true)) return@withContext candidate
    resolveImdbToTmdb(candidate, normalizeMediaType(type))?.id ?: candidate
  }
  suspend fun fetchDetails(type: String, id: String, fallbackTitle: String? = null, fallbackYear: String? = null): Result<MediaDetail> = withContext(Dispatchers.IO) {
    runCatching {
      val normalizedType = normalizeMediaType(type)
      val idCandidates = buildDetailIdCandidates(id)
      fetchDetailsByCandidates(normalizedType, idCandidates)?.let { return@runCatching it }

      idCandidates.firstNotNullOfOrNull { candidate -> resolveImdbToTmdb(candidate, normalizedType) }?.let { resolved ->
        fetchDetailsByCandidates(resolved.type, listOf(resolved.id))?.let { return@runCatching it }
      }

      val title = fallbackTitle?.trim().orEmpty()
      // Some bridge catalogs hand out placeholder titles (a bare "_", "-", "?" or similar) for
      // items they don't actually have metadata for yet. Querying TMDB search with that junk
      // string never resolves anything (and previously fired repeatedly whenever the retry loop
      // above re-triggered), so bail out before spending a request on it.
      val isRealTitle = title.isNotBlank() && title.any { it.isLetterOrDigit() }
      if (isRealTitle) {
        val queries = listOf(title, cleanSearchTitle(title)).filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        for (query in queries) {
          val encoded = encodeQuery(query)
          val searchItems = listOf("/tmdb/search?q=$encoded", "/tmdb/search?query=$encoded")
            .firstNotNullOfOrNull { path -> runCatching { fetchMediaList(path) }.getOrNull()?.takeIf { it.isNotEmpty() } }
            .orEmpty()
          val resolved = searchItems
            .filter { normalizeMediaType(it.type) == normalizedType }
            .sortedWith(
              compareByDescending<MediaItem> { it.year != null && fallbackYear != null && it.year.take(4) == fallbackYear.take(4) }
                .thenByDescending { normalizeTitle(it.title) == normalizeTitle(title) }
                .thenByDescending { it.title.equals(title, ignoreCase = true) }
                .thenByDescending { it.rating ?: 0.0 },
            )
            .firstOrNull()
          if (resolved != null) {
            fetchDetailsByCandidates(resolved.type, buildDetailIdCandidates(resolved.id))?.let { return@runCatching it }
          }
        }
      }

      throw IllegalStateException("Failed to load details")
    }
  }

  private fun buildDetailIdCandidates(id: String): List<String> = buildList {
    val trimmed = id.trim()
    if (trimmed.isBlank()) return@buildList
    add(trimmed)
    add(trimmed.substringAfter("tmdb:", trimmed))
    add(trimmed.substringAfterLast(":", trimmed))
    Regex("tt\\d+", RegexOption.IGNORE_CASE).find(trimmed)?.value?.let(::add)
  }.filter { it.isNotBlank() }.distinct()

  private fun normalizeMediaType(type: String): String = when (type.trim().lowercase()) {
    "series", "show" -> "tv"
    else -> type.trim().lowercase().ifBlank { "movie" }
  }

  private data class ResolvedTmdbId(val id: String, val type: String)

  private fun resolveImdbToTmdb(candidate: String, hintType: String): ResolvedTmdbId? {
    val imdbId = Regex("tt\\d+", RegexOption.IGNORE_CASE).find(candidate)?.value ?: return null
    val hint = normalizeMediaType(hintType)
    val typeCandidates = listOf(hint, if (hint == "tv") "series" else hint, "tv", "movie").distinct()
    for (type in typeCandidates) {
      val response = execute(Request.Builder().url("$apiBaseUrl/tmdb/find/imdb/${encodeQuery(imdbId)}?type=${encodeQuery(normalizeMediaType(type))}").build())
      if (!response.ok) continue
      val id = response.json.opt("id")?.toString().orEmpty()
      if (id.isBlank()) continue
      return ResolvedTmdbId(id = id, type = normalizeMediaType(response.json.optString("type").ifBlank { type }))
    }
    return null
  }

  private fun cleanSearchTitle(title: String): String = title
    .replace(Regex("\\bS\\d{1,2}\\b.*$", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s*\\([^)]*\\)"), "")
    .replace(Regex("\\s*\\|.*$"), "")
    .trim()

  private fun normalizeTitle(title: String): String = cleanSearchTitle(title)
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

  private fun fetchDetailsByCandidates(type: String, idCandidates: List<String>): MediaDetail? {
    val normalizedType = normalizeMediaType(type)
    val typeCandidates = listOf(normalizedType, if (normalizedType == "tv") "series" else normalizedType).distinct()
    for (candidateType in typeCandidates) {
      for (candidateId in idCandidates) {
        val response = execute(Request.Builder().url("$apiBaseUrl/tmdb/details/$candidateType/${encodeQuery(candidateId)}").build())
        if (response.ok) return parseMediaDetail(response.json)
      }
    }
    return null
  }

  suspend fun fetchFusionBadgeSource(url: String): Result<JSONObject> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(Request.Builder().url(url).build())
      ensureOk(response, "Failed to load Fusion badge source")
      response.json
    }
  }

  suspend fun fetchMdblistRatings(type: String, tmdbId: String, imdbId: String?, apiKey: String, providers: List<String> = listOf("imdb", "tmdb", "tomatoes", "metacritic", "trakt", "letterboxd", "audience")): Result<List<ExternalRating>> = withContext(Dispatchers.IO) {
    runCatching {
      val mediaType = if (type == "tv" || type == "series") "show" else "movie"
      val normalizedImdbId = imdbId?.let { Regex("tt\\d+", RegexOption.IGNORE_CASE).find(it)?.value }
      val mergedRatings = linkedMapOf<String, ExternalRating>()
      if (!normalizedImdbId.isNullOrBlank()) {
        val selectedProviders = providers.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        selectedProviders.mapNotNull { provider ->
          val payload = JSONObject().put("ids", JSONArray().put(normalizedImdbId)).put("provider", "imdb")
          val response = execute(
            Request.Builder()
              .url("https://api.mdblist.com/rating/$mediaType/$provider?apikey=${encodeQuery(apiKey)}")
              .post(payload.toString().toRequestBody(jsonMediaType))
              .addHeader("Accept", "application/json")
              .build(),
          )
          if (!response.ok) return@mapNotNull null
          val rating = parseMdblistProviderRating(response.json, allowPercent = providerAllowsPercent(provider)) ?: return@mapNotNull null
          ExternalRating(provider = normalizeMdblistProviderId(provider), rating = rating)
        }.forEach { rating ->
          mergedRatings.putIfAbsent(rating.provider.lowercase(), rating)
        }
      }

      val candidates = buildList {
        if (tmdbId.isNotBlank()) add("https://api.mdblist.com/tmdb/$mediaType/${encodeQuery(tmdbId)}?apikey=${encodeQuery(apiKey)}")
        if (!imdbId.isNullOrBlank()) add("https://api.mdblist.com/imdb/$mediaType/${encodeQuery(imdbId)}?apikey=${encodeQuery(apiKey)}")
      }
      for (url in candidates) {
        val response = execute(Request.Builder().url(url).build())
        if (response.ok) {
          val ratings = parseExternalRatings(response.json)
          ratings.forEach { rating ->
            mergedRatings.putIfAbsent(normalizeMdblistProviderId(rating.provider).lowercase(), rating)
          }
        }
      }
      mergedRatings.values.toList()
    }
  }

  suspend fun fetchTraktComments(session: AuthSession?, type: String, tmdbId: String, imdbId: String?): Result<List<TraktComment>> = withContext(Dispatchers.IO) {
    runCatching {
      val mediaType = if (type == "tv" || type == "series") "shows" else "movies"
      val id = imdbId?.takeIf { it.isNotBlank() } ?: tmdbId
      val candidates = listOf(
        "$apiBaseUrl/trakt/$mediaType/${encodeQuery(id)}/comments",
        "$apiBaseUrl/trakt/comments/$type/${encodeQuery(tmdbId)}",
        "$apiBaseUrl/trakt/comments?type=${encodeQuery(type)}&tmdbId=${encodeQuery(tmdbId)}",
      )
      for (url in candidates) {
        val response = execute(Request.Builder().url(url).headers(authHeaders(session, includeContentType = false)).build())
        if (response.ok) {
          val comments = parseTraktComments(response.json)
          if (comments.isNotEmpty()) return@runCatching comments
        }
      }
      emptyList()
    }
  }

  suspend fun fetchPerson(personId: String): Result<PersonDetail> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(Request.Builder().url("$apiBaseUrl/tmdb/person/$personId").build())
      ensureOk(response, "Failed to load cast details")
      parsePersonDetail(response.json)
    }
  }
  suspend fun fetchSeason(tvId: String, seasonNumber: Int): Result<List<EpisodeItem>> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(Request.Builder().url("$apiBaseUrl/tmdb/season/$tvId/$seasonNumber").build())
      ensureOk(response, "Failed to load season")
      val episodes = response.json.optJSONArray("episodes") ?: JSONArray()
      buildList {
        for (index in 0 until episodes.length()) {
          add(parseEpisode(episodes.optJSONObject(index) ?: JSONObject(), fallbackSeasonNumber = seasonNumber))
        }
      }
    }
  }

  suspend fun fetchProfiles(session: AuthSession): Result<List<StreamProfile>> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/profiles/")
          .headers(authHeaders(session))
          .build(),
      )
      ensureOk(response, "Failed to load profiles")
      val profiles = response.json.optJSONArray("profiles") ?: JSONArray()
      buildList {
        for (index in 0 until profiles.length()) {
          add(parseProfile(profiles.optJSONObject(index) ?: JSONObject()))
        }
      }
    }
  }

  suspend fun fetchLiveFavouriteChannels(session: AuthSession, profileId: String): Result<FavouriteChannelsEnvelope> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/profiles/${encodeQuery(profileId)}/live-favourites")
          .headers(authHeaders(session, includeContentType = false, profileId = profileId))
          .build(),
      )
      ensureOk(response, "Failed to load live favourites")
      val items = response.json.optJSONArray("items") ?: JSONArray()
      FavouriteChannelsEnvelope(
        items = buildList {
          for (index in 0 until items.length()) {
            items.optJSONObject(index)?.let { add(parseFavouriteChannel(it)) }
          }
        },
        updatedAt = response.json.optLong("updatedAt"),
      )
    }
  }

  suspend fun saveLiveFavouriteChannels(session: AuthSession, profileId: String, items: List<MediaItem>): Result<FavouriteChannelsEnvelope> = withContext(Dispatchers.IO) {
    runCatching {
      val payload = JSONArray().apply {
        items.forEach { item ->
          put(JSONObject()
            .put("id", item.id).put("type", "live").put("title", item.title)
            .put("year", item.year).put("poster", item.poster).put("backdrop", item.backdrop)
            .put("rating", item.rating).put("description", item.description)
            .put("sourceAddonId", item.sourceAddonId).put("sourceAddonName", item.sourceAddonName)
            .put("sourceCatalogType", item.sourceCatalogType).put("sourceCatalogId", item.sourceCatalogId)
            .put("sourceCatalogName", item.sourceCatalogName).put("directStreamUrl", item.directStreamUrl)
            .put("requestHeaders", JSONObject(item.requestHeaders)).put("addedAt", item.addedAt).put("updatedAt", item.updatedAt))
        }
      }
      val request = Request.Builder()
        .url("$apiBaseUrl/profiles/${encodeQuery(profileId)}/live-favourites")
        .put(JSONObject().put("items", payload).toString().toRequestBody(jsonMediaType))
        .headers(authHeaders(session, profileId = profileId))
        .build()
      val response = execute(request)
      ensureOk(response, "Failed to sync live favourites")
      FavouriteChannelsEnvelope(items, response.json.optLong("updatedAt"))
    }
  }
  suspend fun createProfile(session: AuthSession, name: String, avatarIndex: Int = 0): Result<StreamProfile> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = executeJson(
          "/profiles/",
          JSONObject().put("name", name).put("avatarIndex", avatarIndex),
          session = session,
        )
        ensureOk(response, "Failed to create profile")
        parseProfile(response.json.optJSONObject("profile") ?: JSONObject())
      }
    }

  suspend fun updateProfile(session: AuthSession, profileId: String, name: String, avatarIndex: Int): Result<StreamProfile> =
    withContext(Dispatchers.IO) {
      runCatching {
        val request = Request.Builder()
          .url("$apiBaseUrl/profiles/$profileId")
          .patch(JSONObject().put("name", name).put("avatarIndex", avatarIndex).toString().toRequestBody(jsonMediaType))
          .headers(authHeaders(session))
          .build()
        val response = execute(request)
        ensureOk(response, "Failed to update profile")
        parseProfile(response.json.optJSONObject("profile") ?: response.json)
      }
    }

  suspend fun updateProfileAudioLanguage(session: AuthSession, profileId: String, audioLanguage: String): Result<StreamProfile> =
    withContext(Dispatchers.IO) {
      runCatching {
        val request = Request.Builder()
          .url("$apiBaseUrl/profiles/$profileId")
          .patch(JSONObject().put("audioLanguage", audioLanguage).toString().toRequestBody(jsonMediaType))
          .headers(authHeaders(session))
          .build()
        val response = execute(request)
        ensureOk(response, "Failed to update profile audio language")
        parseProfile(response.json.optJSONObject("profile") ?: response.json)
      }
    }
  suspend fun deleteProfile(session: AuthSession, profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/profiles/$profileId")
          .delete()
          .headers(authHeaders(session, includeContentType = false))
          .build(),
      )
      ensureOk(response, "Failed to delete profile")
    }
  }

  suspend fun setDefaultProfile(session: AuthSession, profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$apiBaseUrl/profiles/$profileId/set-default")
        .post("{}".toRequestBody(jsonMediaType))
        .headers(authHeaders(session))
        .build()
      val response = execute(request)
      ensureOk(response, "Failed to set default profile")
    }
  }

  suspend fun setProfilePin(session: AuthSession, profileId: String, pin: String?): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/profiles/$profileId/pin",
        JSONObject().put("pin", pin),
        session = session,
      )
      ensureOk(response, "Failed to update profile PIN")
    }
  }

  suspend fun verifyProfilePin(session: AuthSession, profileId: String, pin: String): Result<Boolean> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/profiles/$profileId/verify-pin",
        JSONObject().put("pin", pin),
        session = session,
      )
      response.ok && response.json.optBoolean("valid")
    }
  }

  suspend fun fetchAddons(session: AuthSession?, profileId: String? = null): Result<List<InstalledAddon>> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$apiBaseUrl/addons/manifests")
        .headers(authHeaders(session, includeContentType = false, profileId = profileId))
        .build()
      val response = execute(request)
      ensureOk(response, "Failed to load add-ons")
      val addons = response.json.optJSONArray("__array")
        ?: response.json.optJSONArray("data")
        ?: response.json.optJSONArray("addons")
        ?: JSONArray()
      buildList {
        for (index in 0 until addons.length()) {
          val item = addons.optJSONObject(index) ?: continue
          add(parseAddon(item))
        }
      }
    }
  }

  suspend fun installAddon(session: AuthSession?, url: String, profileId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/addons/install",
        JSONObject().put("url", url),
        session = session,
        profileId = profileId,
      )
      ensureOk(response, "Failed to install add-on")
    }
  }

  suspend fun toggleAddon(session: AuthSession?, addonId: String, enabled: Boolean, profileId: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = executeJson(
          "/addons/toggle",
          JSONObject().put("id", addonId).put("enabled", enabled),
          session = session,
          profileId = profileId,
        )
        ensureOk(response, "Failed to update add-on")
      }
    }

  suspend fun uninstallAddon(session: AuthSession?, addonId: String, profileId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$apiBaseUrl/addons/uninstall")
        .delete(JSONObject().put("id", addonId).toString().toRequestBody(jsonMediaType))
        .headers(authHeaders(session, profileId = profileId))
        .build()
      val response = execute(request)
      ensureOk(response, "Failed to uninstall add-on")
    }
  }

  suspend fun reorderAddons(session: AuthSession?, orderedIds: List<String>, profileId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/addons/reorder",
        JSONObject().put("order", JSONArray(orderedIds)),
        session = session,
        profileId = profileId,
      )
      ensureOk(response, "Failed to reorder add-ons")
    }
  }


  // ── IPTV playlists ─────────────────────────────────────────────────────────────────────────
  // The account holds the list of playlists; the channels themselves are still fetched and parsed
  // on the device by M3uPlaylistManager. Only the pointer travels.

  /** Playlists saved against this profile, in display order. */
  suspend fun fetchPlaylists(session: AuthSession, profileId: String): Result<List<RemotePlaylist>> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/playlists")
          .headers(authHeaders(session, includeContentType = false, profileId = profileId))
          .build(),
      )
      ensureOk(response, "Failed to load playlists")
      parseRemotePlaylists(response.json)
    }
  }

  suspend fun addPlaylist(session: AuthSession, profileId: String, url: String, name: String?): Result<List<RemotePlaylist>> = withContext(Dispatchers.IO) {
    runCatching {
      val payload = JSONObject().put("url", url).apply { name?.takeIf { it.isNotBlank() }?.let { put("name", it) } }
      val response = executeJson("/playlists", payload, session = session, profileId = profileId)
      ensureOk(response, "Failed to add playlist")
      parseRemotePlaylists(response.json)
    }
  }

  suspend fun updatePlaylist(
    session: AuthSession,
    profileId: String,
    id: String,
    name: String? = null,
    enabled: Boolean? = null,
    position: Int? = null,
  ): Result<List<RemotePlaylist>> = withContext(Dispatchers.IO) {
    runCatching {
      val payload = JSONObject().apply {
        name?.let { put("name", it) }
        enabled?.let { put("enabled", it) }
        position?.let { put("position", it) }
      }
      val request = Request.Builder()
        .url(apiBaseUrl + "/playlists/" + encodeQuery(id))
        .patch(payload.toString().toRequestBody(jsonMediaType))
        .headers(authHeaders(session, profileId = profileId))
        .build()
      val response = execute(request)
      ensureOk(response, "Failed to update playlist")
      parseRemotePlaylists(response.json)
    }
  }

  suspend fun removePlaylist(session: AuthSession, profileId: String, id: String): Result<List<RemotePlaylist>> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url(apiBaseUrl + "/playlists/" + encodeQuery(id))
        .delete()
        .headers(authHeaders(session, profileId = profileId))
        .build()
      val response = execute(request)
      ensureOk(response, "Failed to remove playlist")
      parseRemotePlaylists(response.json)
    }
  }

  /**
   * Uploads playlists this device holds locally. URLs already on the account are left untouched:
   * the copy there may have been renamed or turned off from another device.
   */
  suspend fun importPlaylists(session: AuthSession, profileId: String, playlists: List<RemotePlaylist>): Result<List<RemotePlaylist>> = withContext(Dispatchers.IO) {
    runCatching {
      val array = JSONArray()
      playlists.forEach { playlist ->
        array.put(JSONObject().put("url", playlist.url).put("name", playlist.name).put("enabled", playlist.enabled))
      }
      val response = executeJson("/playlists/import", JSONObject().put("playlists", array), session = session, profileId = profileId)
      ensureOk(response, "Failed to upload playlists")
      parseRemotePlaylists(response.json)
    }
  }

  private fun parseRemotePlaylists(body: JSONObject): List<RemotePlaylist> {
    val array = body.optJSONArray("playlists") ?: return emptyList()
    return buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val url = item.optString("url").trim().takeIf { it.isNotBlank() } ?: continue
        add(
          RemotePlaylist(
            id = item.optString("id"),
            name = item.optString("name").ifBlank { url },
            url = url,
            enabled = item.optBoolean("enabled", true),
            position = item.optInt("position", index),
          ),
        )
      }
    }
  }

  private suspend fun fetchAddonHomeSections(session: AuthSession?, addons: List<InstalledAddon>, profileId: String?): List<MediaSection> {
    val enabledAddons = addons.filter { it.enabled }.sortedBy { it.position }
    if (enabledAddons.isEmpty()) return emptyList()

    val sections = mutableListOf<MediaSection>()
    val duplicateCatalogNames = enabledAddons
      .flatMap { addon -> addon.manifest.catalogs.map { catalog -> catalog.name.trim().lowercase() to mapHomeCatalogType(catalog.type) } }
      .filter { (_, type) -> type != null }
      .groupBy({ it.first }, { it.second!! })
      .mapValues { (_, values) -> values.toSet().size > 1 }

    val catalogGate = Semaphore(6)
    val catalogResults = supervisorScope {
      enabledAddons.flatMap { addon ->
        addon.manifest.catalogs.mapIndexedNotNull { index, catalog ->
          val mappedType = mapHomeCatalogType(catalog.type) ?: return@mapIndexedNotNull null
          // A catalog that declares genre as required has nothing to answer with until one is
          // picked, so its preview row uses the add-on's own first option. Catalogs with an
          // optional genre are left unfiltered.
          val defaultGenre = catalog.genreOptions.firstOrNull()?.takeIf { catalog.requiresGenre }
          async {
            val items = catalogGate.withPermit {
              runCatching {
                if (LocalAddonManager.isLocalAddonId(addon.id)) {
                  fetchLocalAddonCatalog(addon, catalog.type, catalog.id, catalog.name, defaultGenre)
                } else {
                  fetchAddonCatalog(addon.id, addon.manifest.name, catalog.type, catalog.id, catalog.name, session, profileId, defaultGenre)
                }
              }.getOrDefault(emptyList()).take(HOME_CATALOG_PREVIEW_LIMIT)
            }
            Triple(addon to catalog, index, mappedType to items)
          }
        }
      }.awaitAll()
    }
    for ((addonAndCatalog, index, mappedAndItems) in catalogResults) {
      val (addon, catalog) = addonAndCatalog
      val (mappedType, items) = mappedAndItems
      if (items.isEmpty()) continue
        val title = buildAddonSectionTitle(
          addon.manifest.name,
          catalog.name,
          if (duplicateCatalogNames[catalog.name.trim().lowercase()] == true) {
            if (mappedType == "movie") "Movies" else "Series"
          } else {
            null
          },
        )
        sections += MediaSection(
          id = "addon:${addon.id}:${catalog.type.trim().lowercase()}:${catalog.id}:$index",
          title = title,
          items = items,
        )
    }
    return sections
  }

  private suspend fun fetchAddonCatalog(addonId: String, addonName: String, rawType: String, catalogId: String, catalogName: String, session: AuthSession?, profileId: String?, genre: String? = null, skip: Int = 0): List<MediaItem> {
    val queryParams = buildList {
      genre?.takeIf { it.isNotBlank() }?.let { add("genre=" + encodeQuery(it)) }
      if (skip > 0) add("skip=$skip")
    }
    val query = queryParams.takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "?").orEmpty()
    val request = Request.Builder()
      .url("$apiBaseUrl/addons/${encodeQuery(addonId)}/catalog/${encodeQuery(rawType)}/${encodeQuery(catalogId)}$query")
      .headers(authHeaders(session, includeContentType = false, profileId = profileId))
      .build()
    val response = execute(request)
    ensureOk(response, "Failed to load add-on catalog")
    val body = response.json
    val items = body.optJSONArray("metas")
      ?: body.optJSONArray("results")
      ?: body.optJSONArray("items")
      ?: body.optJSONArray("data")
      ?: body.optJSONArray("__array")
      ?: JSONArray()
    return buildList {
      for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        if (isPlaceholderCatalogMeta(item)) continue
        val normalizedCatalogType = rawType.trim().lowercase()
        val mediaItem = parseMediaItem(item).copy(
          id = parseAddonCatalogItemId(item),
          type = when (normalizedCatalogType) {
            "series", "show" -> "tv"
            else -> normalizedCatalogType
          },
          sourceAddonId = addonId,
          sourceAddonName = addonName,
          sourceCatalogType = normalizedCatalogType,
          sourceCatalogId = catalogId,
          sourceCatalogName = catalogName,
          sourceCatalogGenre = genre,
          directStreamUrl = parseDirectMediaUrl(item),
          requestHeaders = parseStringMap(item.optJSONObject("headers")) + parseStringMap(item.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders")?.optJSONObject("request")),
        )
        if (mediaItem.id.isNotBlank()) add(mediaItem)
      }
    }
  }

  // Local add-ons (installed from a localhost/LAN manifest via LocalAddonManager) have no
  // meaning to the backend, which can't reach a URL like http://127.0.0.1:11470 on the phone's
  // own network. Their catalogs are instead fetched directly from the addon, on-device, exactly
  // like fetchFreshStreamsFromAddon already does for streams.
  /**
   * Asks one of an add-on's catalogs to answer a text query.
   *
   * The only way an add-on's own titles are findable at all. TMDB search — the app's entire
   * search until now — has no idea what a live channel is, and a catalog is never held complete
   * in memory to filter locally, so a channel like "Sky Cinema Action" simply could not be found.
   *
   * Goes straight to the add-on rather than through StreamDek's servers, matching how streams are
   * now fetched, so it needs no backend change and works for local add-ons unmodified.
   */
  suspend fun searchAddonCatalog(
    addon: InstalledAddon,
    catalog: AddonCatalog,
    query: String,
  ): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
    runCatching {
      if (query.isBlank()) return@runCatching emptyList()
      fetchAddonCatalogDirect(
        addon = addon,
        rawType = catalog.type,
        catalogId = catalog.id,
        catalogName = catalog.name,
        // A catalog that insists on a genre still needs one alongside the query.
        genre = catalog.genreOptions.firstOrNull()?.takeIf { catalog.requiresGenre },
        search = query.trim(),
      )
    }
  }

  private suspend fun fetchLocalAddonCatalog(addon: InstalledAddon, rawType: String, catalogId: String, catalogName: String, genre: String? = null, skip: Int = 0): List<MediaItem> =
    fetchAddonCatalogDirect(addon, rawType, catalogId, catalogName, genre, skip)

  private suspend fun fetchAddonCatalogDirect(addon: InstalledAddon, rawType: String, catalogId: String, catalogName: String, genre: String? = null, skip: Int = 0, search: String? = null): List<MediaItem> {
    val base = addonRequestBaseUrl(addon) ?: return emptyList()
    // Stremio's catalog protocol takes "extra" properties (genre, skip, search) as one more
    // path segment before .json, e.g. /catalog/other/{id}/genre=Comedy&skip=100.json — not a
    // query string. Some catalogs (see the manifest that prompted this) only return meaningful,
    // distinct results once a genre is picked; without it every such catalog can come back
    // with the same generic/default response. "skip" is what lets "View All" page in more
    // results past whatever the home row already fetched.
    //
    // This talks directly to a third-party HTTP server (not StreamDek's own backend), so this
    // needs real percent-encoding (Uri.encode: spaces -> %20) rather than encodeQuery's
    // form-encoding (spaces -> "+"), which most servers won't turn back into a space when
    // decoding a path segment — that mismatch alone would make every catalog with a space in
    // its id (e.g. "cnc_CNC Verse_Netflix_other") fail to match its own route and fall through
    // to whatever default the server returns, which looks exactly like "every catalog returns
    // the same thing".
    fun pathSegment(value: String) = Uri.encode(value)
    val extraParams = buildList {
      genre?.takeIf { it.isNotBlank() }?.let { add("genre=" + pathSegment(it)) }
      search?.takeIf { it.isNotBlank() }?.let { add("search=" + pathSegment(it)) }
      if (skip > 0) add("skip=$skip")
    }
    val extraSegment = extraParams.takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "/").orEmpty()
    val request = Request.Builder()
      .url("$base/catalog/${pathSegment(rawType)}/${pathSegment(catalogId)}$extraSegment.json")
      .header("User-Agent", "Stremio/4.4.168")
      .build()
    val response = execute(request, directStreamClient)
    ensureOk(response, "Failed to load add-on catalog")
    val body = response.json
    val items = body.optJSONArray("metas")
      ?: body.optJSONArray("results")
      ?: body.optJSONArray("items")
      ?: body.optJSONArray("data")
      ?: body.optJSONArray("__array")
      ?: JSONArray()
    return buildList {
      for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        if (isPlaceholderCatalogMeta(item)) continue
        val normalizedCatalogType = rawType.trim().lowercase()
        val mediaItem = parseMediaItem(item).copy(
          id = parseAddonCatalogItemId(item),
          type = when (normalizedCatalogType) {
            "series", "show" -> "tv"
            else -> normalizedCatalogType
          },
          sourceAddonId = addon.id,
          sourceAddonName = addon.manifest.name,
          sourceCatalogType = normalizedCatalogType,
          sourceCatalogId = catalogId,
          sourceCatalogName = catalogName,
          sourceCatalogGenre = genre,
          directStreamUrl = parseDirectMediaUrl(item),
          requestHeaders = parseStringMap(item.optJSONObject("headers")) + parseStringMap(item.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders")?.optJSONObject("request")),
        )
        if (mediaItem.id.isNotBlank()) add(mediaItem)
      }
    }
  }

  /**
   * The address an add-on's own resources hang off, with any `/manifest.json` removed.
   *
   * `transportUrl` is the manifest's own address and usually ends in `/manifest.json`. Appending
   * `/catalog/...` to it unchanged produced `.../manifest.json/catalog/...`, which every add-on
   * answers with a 404 — the catalog path only ever worked for the local add-ons it was written
   * for, whose stored URL happened to carry no suffix. [fetchFreshStreamsFromAddon] already
   * trimmed it this way for streams; catalogs and search now share that behaviour.
   */
  private fun addonRequestBaseUrl(addon: InstalledAddon): String? {
    val raw = addon.transportUrl ?: addon.manifestUrl ?: addon.baseUrl ?: addon.url ?: return null
    return raw.substringBeforeLast("/manifest.json", missingDelimiterValue = raw)
      .trimEnd('/')
      .takeIf { it.isNotBlank() }
  }

  /**
   * Resolves the id an add-on's own /stream endpoint actually expects, by asking its
   * /meta/{type}/{id}.json first — the same round-trip every working Stremio client makes
   * before requesting streams. A self-hosted bridge (like a local CNCVerse-style server) can
   * use one id for browsing a catalog and a different canonical one (often the real IMDb id)
   * once you look the item up individually; StreamDek used to skip straight from the catalog
   * id to /stream, which is fine for addons where those ids match but returns nothing for
   * addons where they don't. Falls back to the original [id] on any failure or when the
   * response has nothing more specific, so this never behaves worse than before.
   */
  suspend fun resolveLocalAddonStreamId(addon: InstalledAddon, rawType: String, id: String): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val base = addonRequestBaseUrl(addon)
          ?: return@runCatching id
        fun pathSegment(value: String) = Uri.encode(value)
        val request = Request.Builder()
          .url("$base/meta/${pathSegment(rawType.trim().lowercase())}/${pathSegment(id)}.json")
          .header("User-Agent", "Stremio/4.4.168")
          .build()
        val response = execute(request, directStreamClient)
        if (!response.ok) return@runCatching id
        val meta = response.json.optJSONObject("meta") ?: return@runCatching id
        meta.optString("imdb_id").takeIf { it.isNotBlank() }
          ?: meta.optString("id").takeIf { it.isNotBlank() }
          ?: id
      }
    }

  /**
   * The add-on's own description of one of its items, for any add-on publishing a `meta` resource.
   *
   * Metadata add-ons (aiometadata and similar) exist to answer exactly this call, and until now
   * nothing did: only local add-ons were ever asked for `/meta`, so a card from a metadata add-on
   * opened onto a TMDB lookup for an id — `tmdb:1234`, a provider slug — that TMDB has no way to
   * resolve, and the detail page came up empty. Local add-ons are still queried on-device because
   * the backend cannot reach a LAN transport URL; everything else is proxied, since the backend is
   * what holds the add-on's configured transport URL.
   */
  suspend fun fetchAddonMeta(
    session: AuthSession?,
    profileId: String?,
    addon: InstalledAddon,
    rawType: String,
    id: String,
  ): Result<LocalAddonMeta> {
    if (LocalAddonManager.isLocalAddonId(addon.id)) return fetchLocalAddonMeta(addon, rawType, id)
    return withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/addons/${Uri.encode(addon.id)}/meta/${Uri.encode(rawType.trim().lowercase())}/${Uri.encode(id)}")
            .headers(authHeaders(session, includeContentType = false, profileId = profileId))
            .build(),
        )
        ensureOk(response, "Failed to load add-on details")
        parseLocalAddonMetaResponse(response.json, rawType, id)
      }
    }
  }

  /** Fetches the add-on's canonical meta before TMDB enrichment or stream lookup. */
  suspend fun fetchLocalAddonMeta(addon: InstalledAddon, rawType: String, id: String): Result<LocalAddonMeta> =
    withContext(Dispatchers.IO) {
      runCatching {
        val base = addonRequestBaseUrl(addon)
          ?: error("Addon transport URL is unavailable")
        val response = execute(
          Request.Builder()
            .url("$base/meta/${Uri.encode(rawType.trim().lowercase())}/${Uri.encode(id)}.json")
            .header("User-Agent", "Stremio/4.4.168")
            .build(),
          directStreamClient,
        )
        ensureOk(response, "Failed to load add-on details")
        parseLocalAddonMetaResponse(response.json, rawType, id)
      }
    }

  /**
   * Pages in more items for a single already-loaded add-on/catalog combo — what backs "View
   * All"'s infinite scroll. [skip] is the number of items already shown for this exact
   * catalog+genre combo; add-ons that don't support paging simply keep returning the same page,
   * so callers should stop once a page comes back with nothing new.
   */
  suspend fun fetchMoreCatalogItems(
    session: AuthSession?,
    addon: InstalledAddon,
    catalogType: String,
    catalogId: String,
    catalogName: String,
    genre: String?,
    skip: Int,
    profileId: String? = null,
  ): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
    runCatching {
      if (LocalAddonManager.isLocalAddonId(addon.id)) {
        fetchLocalAddonCatalog(addon, catalogType, catalogId, catalogName, genre, skip)
      } else {
        fetchAddonCatalog(addon.id, addon.manifest.name, catalogType, catalogId, catalogName, session, profileId, genre, skip)
      }
    }
  }

  private fun parseDirectMediaUrl(item: JSONObject): String? {
    val behaviorHints = item.optJSONObject("behaviorHints")
    return sequenceOf(item.opt("url"), item.opt("externalUrl"), behaviorHints?.opt("url"), behaviorHints?.opt("externalUrl"))
      .mapNotNull { value ->
        when (value) {
          is JSONObject -> value.optString("url").ifBlank { value.optString("href") }.ifBlank { null }
          else -> value?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        }
      }
      .firstOrNull()
  }
  suspend fun fetchProfilePlugins(session: AuthSession, profileId: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(Request.Builder().url("$apiBaseUrl/profiles/${encodeQuery(profileId)}/plugins").headers(authHeaders(session, includeContentType = false, profileId = profileId)).build())
      ensureOk(response, "Failed to restore profile plugins")
      (response.json.optJSONObject("plugins") ?: JSONObject()).toString()
    }
  }

  suspend fun putProfilePlugins(session: AuthSession, profileId: String, pluginsJson: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val plugins = runCatching { JSONObject(pluginsJson) }.getOrElse { JSONObject() }
      val request = Request.Builder()
        .url("$apiBaseUrl/profiles/${encodeQuery(profileId)}/plugins")
        .put(JSONObject().put("plugins", plugins).toString().toRequestBody(jsonMediaType))
        .headers(authHeaders(session, profileId = profileId))
        .build()
      ensureOk(execute(request), "Failed to sync profile plugins")
    }
  }
  suspend fun patchCloudPreferences(
    session: AuthSession,
    preferences: CloudPlaybackPreferences,
    profileId: String? = null,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val app = JSONObject()
        .put("appAppearance", preferences.appAppearance)
        .put("themePreset", preferences.themePreset)
        .put("headerStyle", preferences.headerStyle)
        .put("showNavLabels", preferences.showNavLabels)
        .put("collapsibleNavigationEnabled", preferences.collapsibleNavigationEnabled)
        .put("navigationAutoCollapseSeconds", preferences.navigationAutoCollapseSeconds)
        .put("syncOverCellular", preferences.syncOnCellular)
      val home = JSONObject()
        .put("detailPageStyle", preferences.detailPageStyle)
        .put("continueWatchingStyle", preferences.continueWatchingStyle)
        .put("liveLandscapeCards", preferences.liveLandscapeCards)
        .put("liveFavouriteDrawerCards", preferences.liveFavouriteDrawerCards)
        .put("liveCategoriesEnabled", preferences.liveCategoriesEnabled)
        .put("primarySyncService", preferences.primarySyncService)
        .put("showHeroSynopsis", preferences.showHeroSynopsis)
        .put("vividAmbient", preferences.vividAmbient)
        .put("ambientTintPercent", preferences.ambientTintPercent)
        .put("defaultAppCatalogsEnabled", preferences.defaultAppCatalogsEnabled)
        .put("homeCatalogRows", preferences.homeCatalogRowsJson?.let(::JSONArray))
      val detail = JSONObject()
        .put("seasonTabStyle", preferences.seasonTabStyle)
        .put("heroTrailerAutoplay", preferences.heroTrailerAutoplay)
        .put("heroTrailerResolution", preferences.heroTrailerResolution)
        .put("ratingsEnabled", preferences.ratingsEnabled)
        .put("externalRatingsEnabled", preferences.externalRatingsEnabled)
        .put("enabledRatingProviders", preferences.enabledRatingProviders?.let(::JSONArray))
        .put("mdblistApiKey", preferences.mdblistApiKey)
        // Also written under `streams` below: the setting is presented on Title Pages now, but
        // clients that have not moved yet still read the older location.
        .put("blurUnwatchedEpisodes", preferences.blurUnwatchedEpisodes)
      val playback = JSONObject()
        .put("pictureInPictureEnabled", preferences.pictureInPictureEnabled)
        .put("decoderMode", preferences.decoderMode)
        .put("renderSurface", preferences.renderSurface)
        .put("playerEngine", preferences.playerEngine)
        .put("preferredAudioLanguage", preferences.preferredAudioLanguage)
        .put("preferredQuality", preferences.preferredQuality)
        .put("maxFileSizeGB", preferences.maxFileSizeGb)
        .put("skipSegmentsEnabled", preferences.skipIntroEnabled == true || preferences.skipRecapEnabled == true || preferences.skipEndingEnabled == true)
        .put("skipIntroEnabled", preferences.skipIntroEnabled)
        .put("skipRecapEnabled", preferences.skipRecapEnabled)
        .put("skipEndingEnabled", preferences.skipEndingEnabled)
        .put("introdbApiKey", preferences.introdbApiKey)
        .put("autoPlayNextEpisodeEnabled", preferences.autoPlayNextEpisode)
        .put("autoplayNextEpisode", preferences.autoPlayNextEpisode)
        .put("preferBingeGroupNextEpisode", preferences.preferBingeGroup)
        .put("autoLoadSubtitles", preferences.autoLoadSubtitles)
        .put("nextEpisodeThresholdMode", preferences.nextEpisodeThresholdMode)
        .put("nextEpisodeThresholdPercent", preferences.nextEpisodeThresholdPercent)
        .put("nextEpisodeThresholdMinutes", preferences.nextEpisodeThresholdMinutes)
      val streams = JSONObject()
        .put("showStreamsList", preferences.showStreamsList)
        .put("rememberLastSource", preferences.rememberLastSource)
        .put("blurUnwatchedEpisodes", preferences.blurUnwatchedEpisodes)
        .put("fusionBadgesEnabled", preferences.fusionBadgesEnabled)
        .put("streamDekFormattingEnabled", preferences.streamDekFormattingEnabled)
        .put("showSizeBadges", preferences.showSizeBadges)
        .put("showAddonTmdbRatings", preferences.showAddonTmdbRatings)
        .put("badgePosition", preferences.badgePosition)
        .put("fusionBadgeUrls", preferences.fusionBadgeUrls?.let(::JSONArray))
        .put("activeFusionBadgeUrl", preferences.activeFusionBadgeUrl)
      val updates = JSONObject().put("autoUpdateChecksEnabled", preferences.autoUpdateChecksEnabled)
      val payload = JSONObject()
        .put("app", app)
        .put("home", home)
        .put("detail", detail)
        .put("playback", playback)
        .put("streams", streams)
        .put("updates", updates)
      val request = Request.Builder()
        .url("$apiBaseUrl/account/preferences")
        .patch(JSONObject().put("preferences", payload).toString().toRequestBody(jsonMediaType))
        .headers(authHeaders(session))
        .build()
      ensureOk(execute(request), "Failed to sync app preferences")
      if (!profileId.isNullOrBlank()) {
        val profileDetail = JSONObject(detail.toString()).apply { remove("mdblistApiKey") }
        // The IntroDB key stays out of this payload for the same reason the MDBList one is removed
        // above: both are account-level in the cloud and profile-scoped on the device.
        val profilePlayback = JSONObject()
          .put("preferredQuality", preferences.preferredQuality)
          .put("maxFileSizeGB", preferences.maxFileSizeGb)
          .put("skipSegmentsEnabled", preferences.skipIntroEnabled == true || preferences.skipRecapEnabled == true || preferences.skipEndingEnabled == true)
          .put("skipIntroEnabled", preferences.skipIntroEnabled)
          .put("skipRecapEnabled", preferences.skipRecapEnabled)
          .put("skipEndingEnabled", preferences.skipEndingEnabled)
          .put("autoPlayNextEpisodeEnabled", preferences.autoPlayNextEpisode)
          .put("autoplayNextEpisode", preferences.autoPlayNextEpisode)
          .put("preferBingeGroupNextEpisode", preferences.preferBingeGroup)
          .put("autoLoadSubtitles", preferences.autoLoadSubtitles)
          .put("nextEpisodeThresholdMode", preferences.nextEpisodeThresholdMode)
          .put("nextEpisodeThresholdPercent", preferences.nextEpisodeThresholdPercent)
          .put("nextEpisodeThresholdMinutes", preferences.nextEpisodeThresholdMinutes)
        val profilePayload = JSONObject()
          .put("home", home)
          .put("detail", profileDetail)
          .put("playback", profilePlayback)
          .put("streams", streams)
        val profileRequest = Request.Builder()
          .url(apiBaseUrl + "/profiles/" + encodeQuery(profileId) + "/preferences")
          .put(JSONObject().put("preferences", profilePayload).toString().toRequestBody(jsonMediaType))
          .headers(authHeaders(session, profileId = profileId))
          .build()
        ensureOk(execute(profileRequest), "Failed to sync profile preferences")
      }
    }
  }

  suspend fun fetchCloudPlaybackPreferences(session: AuthSession, profileId: String? = null): Result<CloudPlaybackPreferences> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(Request.Builder().url("$apiBaseUrl/account/bootstrap").headers(authHeaders(session, includeContentType = false)).build())
      ensureOk(response, "Failed to refresh cloud settings")
      val accountPreferences = response.json.optJSONObject("preferences") ?: JSONObject()
      val profilePreferences = if (profileId.isNullOrBlank()) {
        JSONObject()
      } else {
        val profileResponse = execute(
          Request.Builder()
            .url(apiBaseUrl + "/profiles/" + encodeQuery(profileId) + "/preferences")
            .headers(authHeaders(session, includeContentType = false, profileId = profileId))
            .build(),
        )
        ensureOk(profileResponse, "Failed to refresh profile settings")
        profileResponse.json.optJSONObject("preferences") ?: JSONObject()
      }
      fun mergedSection(name: String): JSONObject {
        val merged = JSONObject((accountPreferences.optJSONObject(name) ?: JSONObject()).toString())
        val scoped = profilePreferences.optJSONObject(name) ?: JSONObject()
        val keys = scoped.keys()
        while (keys.hasNext()) {
          val key = keys.next()
          merged.put(key, scoped.opt(key))
        }
        return merged
      }
      val app = accountPreferences.optJSONObject("app") ?: JSONObject()
      val home = mergedSection("home")
      val detail = mergedSection("detail")
      val playback = mergedSection("playback")
      val streams = mergedSection("streams")
      val updates = accountPreferences.optJSONObject("updates") ?: JSONObject()
      fun optionalBoolean(source: JSONObject, key: String): Boolean? = if (source.has(key) && !source.isNull(key)) source.optBoolean(key) else null
      fun optionalInt(source: JSONObject, key: String): Int? = if (source.has(key) && !source.isNull(key)) source.optInt(key) else null
      fun optionalString(source: JSONObject, key: String): String? = if (source.has(key) && !source.isNull(key)) source.optString(key).takeIf(String::isNotBlank) else null
      fun optionalStringAllowEmpty(source: JSONObject, key: String): String? = if (source.has(key) && !source.isNull(key)) source.optString(key) else null
      fun optionalStringList(source: JSONObject, key: String): List<String>? {
        if (!source.has(key) || source.isNull(key)) return null
        val values = source.optJSONArray(key) ?: return null
        return List(values.length()) { index -> values.optString(index) }.filter(String::isNotBlank)
      }
      CloudPlaybackPreferences(
        appAppearance = optionalString(app, "appAppearance"),
        themePreset = optionalString(app, "themePreset"),
        headerStyle = optionalString(app, "headerStyle"),
        showNavLabels = optionalBoolean(app, "showNavLabels"),
        collapsibleNavigationEnabled = optionalBoolean(app, "collapsibleNavigationEnabled"),
        navigationAutoCollapseSeconds = optionalInt(app, "navigationAutoCollapseSeconds"),
        syncOnCellular = optionalBoolean(app, "syncOverCellular"),
        detailPageStyle = optionalString(home, "detailPageStyle"),
        continueWatchingStyle = optionalString(home, "continueWatchingStyle"),
        liveLandscapeCards = optionalBoolean(home, "liveLandscapeCards"),
        liveFavouriteDrawerCards = optionalBoolean(home, "liveFavouriteDrawerCards"),
        liveCategoriesEnabled = optionalBoolean(home, "liveCategoriesEnabled"),
        primarySyncService = optionalString(home, "primarySyncService"),
        showHeroSynopsis = optionalBoolean(home, "showHeroSynopsis"),
        vividAmbient = optionalBoolean(home, "vividAmbient"),
        ambientTintPercent = optionalInt(home, "ambientTintPercent"),
        defaultAppCatalogsEnabled = optionalBoolean(home, "defaultAppCatalogsEnabled"),
        homeCatalogRowsJson = home.optJSONArray("homeCatalogRows")?.toString(),
        seasonTabStyle = optionalString(detail, "seasonTabStyle"),
        heroTrailerAutoplay = optionalBoolean(detail, "heroTrailerAutoplay"),
        heroTrailerResolution = optionalInt(detail, "heroTrailerResolution"),
        ratingsEnabled = optionalBoolean(detail, "ratingsEnabled"),
        externalRatingsEnabled = optionalBoolean(detail, "externalRatingsEnabled"),
        enabledRatingProviders = optionalStringList(detail, "enabledRatingProviders"),
        mdblistApiKey = optionalStringAllowEmpty(detail, "mdblistApiKey"),
        pictureInPictureEnabled = optionalBoolean(playback, "pictureInPictureEnabled"),
        decoderMode = optionalString(playback, "decoderMode"),
        renderSurface = optionalString(playback, "renderSurface"),
        playerEngine = optionalString(playback, "playerEngine"),
        preferredAudioLanguage = optionalString(playback, "preferredAudioLanguage"),
        introdbApiKey = optionalStringAllowEmpty(playback, "introdbApiKey"),
        skipIntroEnabled = optionalBoolean(playback, "skipIntroEnabled"),
        skipRecapEnabled = optionalBoolean(playback, "skipRecapEnabled"),
        skipEndingEnabled = optionalBoolean(playback, "skipEndingEnabled"),
        autoPlayNextEpisode = optionalBoolean(playback, "autoPlayNextEpisodeEnabled") ?: optionalBoolean(playback, "autoplayNextEpisode"),
        preferBingeGroup = optionalBoolean(playback, "preferBingeGroupNextEpisode"),
        autoLoadSubtitles = optionalBoolean(playback, "autoLoadSubtitles"),
        nextEpisodeThresholdMode = optionalString(playback, "nextEpisodeThresholdMode"),
        nextEpisodeThresholdPercent = optionalInt(playback, "nextEpisodeThresholdPercent"),
        nextEpisodeThresholdMinutes = optionalInt(playback, "nextEpisodeThresholdMinutes"),
        showStreamsList = optionalBoolean(streams, "showStreamsList"),
        rememberLastSource = optionalBoolean(streams, "rememberLastSource"),
        blurUnwatchedEpisodes = optionalBoolean(detail, "blurUnwatchedEpisodes") ?: optionalBoolean(streams, "blurUnwatchedEpisodes"),
        fusionBadgesEnabled = optionalBoolean(streams, "fusionBadgesEnabled"),
        streamDekFormattingEnabled = optionalBoolean(streams, "streamDekFormattingEnabled"),
        showSizeBadges = optionalBoolean(streams, "showSizeBadges"),
        showAddonTmdbRatings = optionalBoolean(streams, "showAddonTmdbRatings"),
        preferredQuality = optionalString(playback, "preferredQuality") ?: optionalString(streams, "preferredQuality"),
        maxFileSizeGb = optionalInt(playback, "maxFileSizeGB") ?: optionalInt(streams, "maxFileSizeGB"),
        badgePosition = optionalString(streams, "badgePosition"),
        fusionBadgeUrls = optionalStringList(streams, "fusionBadgeUrls"),
        activeFusionBadgeUrl = optionalString(streams, "activeFusionBadgeUrl"),
        autoUpdateChecksEnabled = optionalBoolean(updates, "autoUpdateChecksEnabled"),
      )
    }
  }

  suspend fun fetchLatestMobileUpdate(): Result<UpdateManifest> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(Request.Builder().url("$apiBaseUrl/public/updates/android-mobile/latest").header("Accept", "application/json").build())
      ensureOk(response, "Unable to check for updates right now")
      val json = response.json
      val versionCode = json.optInt("versionCode")
      val versionName = json.optString("versionName")
      val packageName = json.optString("packageName")
      require(json.optString("platform") == "android-mobile") { "The update service returned the wrong platform" }
      require(versionCode > 0 && versionName.isNotBlank() && packageName == BuildConfig.APPLICATION_ID) { "The update service returned invalid app metadata" }
      UpdateManifest(
        versionCode = versionCode,
        versionName = versionName,
        apkUrl = trustedUpdateUrl(json.optString("apkUrl")),
        releaseNotes = json.optString("releaseNotes"),
        required = json.optBoolean("required"),
        minSupportedVersionCode = json.optInt("minSupportedVersionCode").takeIf { json.has("minSupportedVersionCode") && it > 0 },
        requiredReason = json.optString("requiredReason").takeIf(String::isNotBlank),
        packageName = packageName,
        assetName = json.optString("assetName").takeIf(String::isNotBlank),
        fileSizeBytes = json.optLong("fileSizeBytes").takeIf { json.has("fileSizeBytes") && it > 0L },
        checksumSha256 = json.optString("checksumSha256").trim().lowercase().takeIf(String::isNotBlank),
      )
    }
  }

  suspend fun downloadUpdate(release: UpdateManifest, destination: File, onProgress: (Long, Long?) -> Unit): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      destination.parentFile?.mkdirs()
      client.newCall(Request.Builder().url(trustedUpdateUrl(release.apkUrl)).build()).execute().use { response ->
        if (!response.isSuccessful) error("Update download failed with HTTP ${response.code}")
        val body = response.body ?: error("The update download was empty")
        val total = body.contentLength().takeIf { it > 0L } ?: release.fileSizeBytes
        body.byteStream().use { input ->
          FileOutputStream(destination).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded = 0L
            while (true) {
              val count = input.read(buffer)
              if (count < 0) break
              output.write(buffer, 0, count)
              downloaded += count
              onProgress(downloaded, total)
            }
          }
        }
      }
      release.checksumSha256?.let { expected ->
        val digest = MessageDigest.getInstance("SHA-256")
        destination.inputStream().use { input ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
          }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual.equals(expected, ignoreCase = true)) { "The downloaded update failed its integrity check" }
      }
      destination
    }.onFailure { destination.delete() }
  }

  private fun trustedUpdateUrl(rawUrl: String): String {
    require(rawUrl.isNotBlank()) { "The update service did not provide a download URL" }
    val parsed = URI(rawUrl)
    val allowedHosts = setOf(URI(apiBaseUrl).host.orEmpty(), "github.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    require(parsed.scheme == "https" && parsed.host in allowedHosts) { "The update download URL is not trusted" }
    return parsed.toString()
  }

  suspend fun fetchStreams(session: AuthSession?, type: String, videoId: String, profileId: String? = null): Result<List<AddonStream>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/addons/streams/$type/${encodeQuery(videoId)}")
            .headers(authHeaders(session, includeContentType = false, profileId = profileId))
            .build(),
        )
        ensureOk(response, "Failed to load streams")
        parseStreamsResponse(response.json)
      }
    }

  /**
   * The per-account capabilities the app needs before deciding how to fetch streams.
   *
   * `serverSideStreams` off - the default - means this account queries add-ons itself, from the
   * device, so its own IP is what an add-on sees.
   */
  suspend fun fetchAddonEntitlements(session: AuthSession?): Result<AddonEntitlements> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/addons/entitlements")
            .headers(authHeaders(session, includeContentType = false))
            .build(),
        )
        ensureOk(response, "Failed to load account entitlements")
        AddonEntitlements(
          ultra = response.json.optBoolean("ultra", false),
          serverSideStreams = response.json.optBoolean("serverSideStreams", false),
        )
      }
    }

  /**
   * Translates a catalogue id into the one an add-on's /stream endpoint expects (usually TMDB to
   * IMDb). Contacts no add-on: the mapping needs a TMDB key that only the server holds, so this
   * stays server-side even for accounts that fetch every stream themselves.
   */
  suspend fun resolveAddonVideoId(session: AuthSession?, type: String, videoId: String): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/addons/resolve-id/${encodeQuery(type)}/${encodeQuery(videoId)}")
            .headers(authHeaders(session, includeContentType = false))
            .build(),
        )
        ensureOk(response, "Failed to resolve the add-on id")
        response.json.optString("videoId").takeIf { it.isNotBlank() } ?: videoId
      }
    }

  suspend fun fetchStreamsFromAddon(session: AuthSession?, addonId: String, type: String, videoId: String, profileId: String? = null): Result<List<AddonStream>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/addons/streams/single/${encodeQuery(addonId)}/${encodeQuery(type)}/${encodeQuery(videoId)}")
            .headers(authHeaders(session, includeContentType = false, profileId = profileId))
            .build(),
        )
        ensureOk(response, "Failed to load streams")
        parseStreamsResponse(response.json)
      }
    }

  /**
   * Asks an add-on for its streams directly from this device.
   *
   * [forceNetwork] bypasses the short response cache. Playback uses it: a cached list is fine for
   * deciding what to show, but the URL actually about to be opened must be one the add-on stands
   * behind right now.
   */
  suspend fun fetchFreshStreamsFromAddon(
    addon: InstalledAddon,
    type: String,
    videoId: String,
    forceNetwork: Boolean = false,
  ): Result<List<AddonStream>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val manifestUrl = addon.transportUrl ?: addon.manifestUrl ?: addon.url
          ?: throw IllegalArgumentException("Addon transport URL is unavailable")
        val baseUrl = manifestUrl.substringBeforeLast("/manifest.json", missingDelimiterValue = manifestUrl.trimEnd('/'))
        val streamType = type.trim().lowercase()
        // Path segments need proper percent-encoding (spaces -> %20), not form/query
        // encoding (spaces -> "+"). Add-ons whose native ids contain spaces or other
        // reserved characters (as CNCVerse Bridge's do) will 404/fall through to a
        // default route on most HTTP routers if "+" is sent instead of "%20" -- the
        // same class of bug that caused duplicate catalog rows for this add-on.
        // No cache-busting parameter and no no-cache header: both were making every request a
        // unique, uncacheable one, so re-opening a title always spent another request against
        // whatever per-IP quota the add-on enforces. Freshness is handled where it matters
        // instead - by [forceNetwork] at playback.
        val streamUrl = "$baseUrl/stream/${Uri.encode(streamType)}/${Uri.encode(videoId)}.json"
        val request = Request.Builder()
          .url(streamUrl)
          .header("User-Agent", "Stremio/4.4.168")
          .apply { if (forceNetwork) cacheControl(CacheControl.FORCE_NETWORK) }
          .build()
        val response = execute(request, directStreamClient)
        android.util.Log.d("StreamDekStreams", "GET $streamUrl -> ok=${response.ok} code=${response.statusCode}")
        ensureOk(response, "Failed to refresh addon stream")
        val streams = parseStreamsResponse(response.json).map { stream ->
          stream.copy(
            addonId = stream.addonId.ifBlank { addon.id },
            addonName = stream.addonName.ifBlank { addon.manifest.name },
          )
        }
        android.util.Log.d("StreamDekStreams", "$streamUrl -> ${streams.size} streams; urls=${streams.mapNotNull { it.url }.take(3)}")
        streams
      }
    }

  suspend fun prepareHlsForPlayback(url: String, headers: Map<String, String>): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val context = appContext ?: return@runCatching url
      val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
      if (!path.endsWith(".m3u8", ignoreCase = true)) return@runCatching url
      val requestBuilder = Request.Builder().url(url)
      headers.forEach { (name, value) -> if (name.isNotBlank() && value.isNotBlank()) requestBuilder.header(name, value) }
      val response = directStreamClient.newCall(requestBuilder.build()).execute()
      response.use {
        if (!it.isSuccessful) return@runCatching url
        val original = it.body?.string().orEmpty()
        val sanitized = stripHlsSubtitleRenditions(original, url)
        if (sanitized == original) return@runCatching url
        val directory = File(context.cacheDir, "playback-manifests").apply { mkdirs() }
        val destination = File(directory, "${url.hashCode().toUInt().toString(16)}.m3u8")
        destination.writeText(sanitized, Charsets.UTF_8)
        android.util.Log.d("StreamDekHls", "Prepared subtitle-safe HLS master ${destination.name} from $url")
        destination.absolutePath
      }
    }
  }

  /**
   * Which of [infoHashes] the user's own debrid providers already hold, as hash -> provider names.
   *
   * Only hashes leave the device and no add-on is involved, so this works the same whether the
   * streams themselves came from StreamDek's servers or straight from an add-on.
   */
  suspend fun fetchDebridCachedHashes(session: AuthSession, infoHashes: List<String>): Result<Map<String, List<String>>> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (infoHashes.isEmpty()) return@runCatching emptyMap()
        val payload = JSONObject().put("infoHashes", JSONArray(infoHashes))
        val response = executeJson("/debrid/cache-check", payload, session = session)
        ensureOk(response, "Failed to check debrid cache")
        val cachedBy = response.json.optJSONObject("cachedBy") ?: JSONObject()
        buildMap {
          cachedBy.keys().forEach { hash ->
            val providers = cachedBy.optJSONArray(hash) ?: return@forEach
            val names = buildList {
              for (index in 0 until providers.length()) {
                providers.optString(index).takeIf { it.isNotBlank() }?.let(::add)
              }
            }
            if (names.isNotEmpty()) put(hash.lowercase(), names)
          }
        }
      }
    }

  suspend fun fetchDebridAccounts(session: AuthSession): Result<List<DebridAccount>> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/debrid/accounts")
          .headers(authHeaders(session))
          .build(),
      )
      ensureOk(response, "Failed to load debrid accounts")
      val accounts = response.json.optJSONArray("accounts") ?: JSONArray()
      buildList {
        for (index in 0 until accounts.length()) {
          add(parseDebridAccount(accounts.optJSONObject(index) ?: JSONObject()))
        }
      }
    }
  }

  suspend fun addDebridAccount(session: AuthSession, provider: String, apiKey: String): Result<String?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = executeJson(
          "/debrid/accounts",
          JSONObject().put("provider", provider).put("apiKey", apiKey),
          session = session,
        )
        ensureOk(response, "Failed to add debrid account")
        response.json.optString("username").ifBlank { null }
      }
    }

  suspend fun removeDebridAccount(session: AuthSession, provider: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/debrid/accounts/$provider")
          .delete()
          .headers(authHeaders(session, includeContentType = false))
          .build(),
      )
      ensureOk(response, "Failed to remove debrid account")
    }
  }

  suspend fun setDebridAccountEnabled(session: AuthSession, provider: String, enabled: Boolean): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val payload = JSONObject().put("enabled", enabled)
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/debrid/accounts/${encodeQuery(provider)}")
            .patch(payload.toString().toRequestBody(jsonMediaType))
            .headers(authHeaders(session))
            .build(),
        )
        ensureOk(response, "Failed to ${if (enabled) "enable" else "disable"} debrid account")
      }
    }

  suspend fun reorderDebridAccounts(session: AuthSession, orderedProviders: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/debrid/accounts/reorder",
        JSONObject().put("order", JSONArray(orderedProviders)),
        session = session,
      )
      ensureOk(response, "Failed to reorder debrid accounts")
    }
  }

  suspend fun resolveStream(
    session: AuthSession,
    stream: AddonStream,
    maxSizeBytes: Long? = null,
  ): Result<DebridResolvedStream?> = withContext(Dispatchers.IO) {
    runCatching {
      if (stream.infoHash.isNullOrBlank()) return@runCatching null
      val magnet = buildMagnet(stream.infoHash, stream.filename)
      val payload = JSONObject()
        .put("infoHash", stream.infoHash)
        .put("magnetLink", magnet)
      stream.filename?.let { payload.put("filename", it) }
      stream.cachedBy.firstOrNull()?.let { payload.put("providerHint", it) }
      maxSizeBytes?.let { payload.put("maxSize", it) }
      val response = executeJson("/debrid/resolve", payload, session = session)
      if (!response.ok && response.json.optBoolean("downloading")) {
        throw DebridDownloadingException(
          response.json.optString("error").ifBlank {
            "Not cached yet — your debrid service has started downloading it. Try this source again in a few minutes."
          },
        )
      }
      ensureOk(response, "Could not resolve stream")
      val json = response.json
      if (json.optString("url").isBlank()) return@runCatching null
      DebridResolvedStream(
        provider = json.optString("provider"),
        url = json.optString("url"),
        filename = json.optString("filename"),
        filesize = json.optLong("filesize"),
      )
    }
  }

  suspend fun streamTorrent(stream: AddonStream): Result<String?> = withContext(Dispatchers.IO) {
    runCatching {
      if (stream.infoHash.isNullOrBlank()) return@runCatching null
      val payload = JSONObject()
        .put("infoHash", stream.infoHash)
        .put("magnetLink", buildMagnet(stream.infoHash, stream.filename))
      stream.filename?.let { payload.put("filename", it) }
      val response = executeJson("/stream/torrent/add", payload)
      ensureOk(response, "Could not start torrent stream")
      response.json.optString("streamUrl").ifBlank { null }
    }
  }

  suspend fun fetchTraktStatus(session: AuthSession, profileId: String): Result<TraktStatus> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/trakt/auth/status")
          .headers(authHeaders(session, profileId = profileId))
          .build(),
      )
      ensureOk(response, "Failed to load Trakt status")
      TraktStatus(
        connected = response.json.optBoolean("connected"),
        username = response.json.optString("username").ifBlank { null },
      )
    }
  }

  suspend fun fetchTraktContinueWatching(session: AuthSession, profileId: String): Result<List<TraktItem>> =
    traktList(session, profileId, "/trakt/sync/playback")

  suspend fun fetchTraktWatchlist(session: AuthSession, profileId: String): Result<List<TraktItem>> =
    traktList(session, profileId, "/trakt/sync/watchlist/enriched")

  suspend fun fetchTraktRecommendations(session: AuthSession, profileId: String): Result<List<TraktItem>> =
    traktList(session, profileId, "/trakt/recommendations/movies")

  suspend fun fetchTraktTrending(): Result<List<TraktItem>> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(Request.Builder().url("$apiBaseUrl/trakt/trending/movies").build())
      ensureOk(response, "Failed to load trending titles")
      val results = response.json.optJSONArray("results") ?: JSONArray()
      buildList {
        for (index in 0 until results.length()) {
          add(parseTraktItem(results.optJSONObject(index) ?: JSONObject()))
        }
      }
    }
  }

  suspend fun requestTraktDeviceCode(): Result<DeviceCodeInfo> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson("/trakt/auth/device/code", JSONObject())
      ensureOk(response, "Failed to start Trakt authentication")
      DeviceCodeInfo(
        deviceCode = response.json.optString("device_code"),
        userCode = response.json.optString("user_code"),
        verificationUrl = response.json.optString("verification_url"),
        expiresIn = response.json.optInt("expires_in"),
        interval = response.json.optInt("interval"),
      )
    }
  }

  suspend fun pollTraktDeviceCode(
    session: AuthSession,
    profileId: String,
    deviceCode: String,
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/trakt/auth/device/poll",
        JSONObject().put("device_code", deviceCode),
        session = session,
        profileId = profileId,
      )
      response.json.optString("status").ifBlank { "error" }
    }
  }

  suspend fun disconnectTrakt(session: AuthSession, profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/trakt/auth/disconnect")
          .delete("{}".toRequestBody(jsonMediaType))
          .headers(authHeaders(session, profileId = profileId))
          .build(),
      )
      ensureOk(response, "Failed to disconnect Trakt")
    }
  }

  suspend fun scrobbleTrakt(
    session: AuthSession,
    profileId: String,
    action: String,
    payload: TraktScrobblePayload,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/trakt/scrobble/$action",
        buildScrobbleJson(payload),
        session = session,
        profileId = profileId,
      )
      ensureOk(response, "Failed to update Trakt scrobble")
    }
  }

  suspend fun syncWatchedMovie(session: AuthSession, profileId: String, detail: MediaDetail, watchedAt: String = Instant.now().toString()): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val entry = JSONObject()
        .put("title", detail.title)
        .put("ids", JSONObject().put("tmdb", detail.id.toIntOrNull()))
        .put("watched_at", watchedAt)
      detail.year?.toIntOrNull()?.let { entry.put("year", it) }
      val payload = JSONObject().put("movies", JSONArray().put(entry)).put("shows", JSONArray())
      val response = executeJson("/trakt/sync/watched", payload, session = session, profileId = profileId)
      ensureOk(response, "Failed to mark movie as watched")
    }
  }

  suspend fun syncWatchlist(
    session: AuthSession,
    profileId: String,
    item: MediaItem,
    remove: Boolean,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val entry = JSONObject()
        .put("title", item.title)
      item.year?.toIntOrNull()?.let { entry.put("year", it) }
      entry.put("ids", JSONObject().put("tmdb", item.id.toIntOrNull()))
      val payload = if (item.type.trim().lowercase() in setOf("tv", "series", "show")) {
        JSONObject().put("movies", JSONArray()).put("shows", JSONArray().put(entry))
      } else {
        JSONObject().put("movies", JSONArray().put(entry)).put("shows", JSONArray())
      }
      val endpoint = if (remove) "/trakt/sync/watchlist/remove" else "/trakt/sync/watchlist/add"
      val response = executeJson(endpoint, payload, session = session, profileId = profileId)
      ensureOk(response, "Failed to update watchlist")
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Additional tracking services (SIMKL, MDBList)
  //
  // These mirror the Trakt endpoints one for one, under the service's own path prefix, and carry
  // the same profile header — a connection belongs to a profile, not to the account. SIMKL uses
  // the device-code flow Trakt uses; MDBList authenticates with a user-supplied API key.
  // ---------------------------------------------------------------------------------------------

  suspend fun fetchSyncServiceStatus(session: AuthSession, profileId: String, service: String): Result<SyncServiceStatus> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/${encodeQuery(service)}/auth/status")
            .headers(authHeaders(session, profileId = profileId))
            .build(),
        )
        ensureOk(response, "Failed to load $service status")
        val capabilities = response.json.optJSONObject("capabilities")
        SyncServiceStatus(
          connected = response.json.optBoolean("connected"),
          username = response.json.optString("username").ifBlank { null },
          // Older backends predate these fields; assume available and fully capable so they
          // behave exactly as they did before capabilities were reported.
          available = response.json.optBoolean("available", true),
          checked = true,
          supportsWatchlist = capabilities?.optBoolean("watchlist", true) ?: true,
          supportsPlayback = capabilities?.optBoolean("playback", true) ?: true,
        )
      }
    }

  suspend fun requestSyncServiceDeviceCode(service: String): Result<DeviceCodeInfo> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson("/${encodeQuery(service)}/auth/device/code", JSONObject())
      ensureOk(response, "Failed to start $service authentication")
      DeviceCodeInfo(
        deviceCode = response.json.optString("device_code"),
        userCode = response.json.optString("user_code"),
        verificationUrl = response.json.optString("verification_url"),
        expiresIn = response.json.optInt("expires_in"),
        interval = response.json.optInt("interval"),
      )
    }
  }

  suspend fun pollSyncServiceDeviceCode(
    session: AuthSession,
    profileId: String,
    service: String,
    deviceCode: String,
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/${encodeQuery(service)}/auth/device/poll",
        JSONObject().put("device_code", deviceCode),
        session = session,
        profileId = profileId,
      )
      response.json.optString("status").ifBlank { "error" }
    }
  }

  /** Key-based connect, used by MDBList. The key is stored server-side against the profile. */
  suspend fun connectSyncServiceApiKey(
    session: AuthSession,
    profileId: String,
    service: String,
    apiKey: String,
  ): Result<SyncServiceStatus> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/${encodeQuery(service)}/auth/apikey",
        JSONObject().put("api_key", apiKey).put("apiKey", apiKey),
        session = session,
        profileId = profileId,
      )
      ensureOk(response, "Failed to connect $service")
      SyncServiceStatus(
        connected = response.json.optBoolean("connected", true),
        username = response.json.optString("username").ifBlank { null },
      )
    }
  }

  suspend fun disconnectSyncService(session: AuthSession, profileId: String, service: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/${encodeQuery(service)}/auth/disconnect")
            .delete("{}".toRequestBody(jsonMediaType))
            .headers(authHeaders(session, profileId = profileId))
            .build(),
        )
        ensureOk(response, "Failed to disconnect $service")
      }
    }

  /** Same payload shape as [syncWatchlist] so one backend contract covers every service. */
  suspend fun syncServiceWatchlist(
    session: AuthSession,
    profileId: String,
    service: String,
    item: MediaItem,
    remove: Boolean,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val entry = JSONObject().put("title", item.title)
      item.year?.toIntOrNull()?.let { entry.put("year", it) }
      entry.put("ids", JSONObject().put("tmdb", item.id.toIntOrNull()))
      val payload = if (item.type.trim().lowercase() in setOf("tv", "series", "show")) {
        JSONObject().put("movies", JSONArray()).put("shows", JSONArray().put(entry))
      } else {
        JSONObject().put("movies", JSONArray().put(entry)).put("shows", JSONArray())
      }
      val endpoint = if (remove) "/${encodeQuery(service)}/sync/watchlist/remove" else "/${encodeQuery(service)}/sync/watchlist/add"
      val response = executeJson(endpoint, payload, session = session, profileId = profileId)
      ensureOk(response, "Failed to update $service watchlist")
    }
  }

  /** Watchlist for a non-Trakt service, used when a profile makes it the primary source. */
  suspend fun fetchSyncServiceWatchlist(session: AuthSession, profileId: String, service: String): Result<List<TraktItem>> =
    traktList(session, profileId, "/${encodeQuery(service)}/sync/watchlist/enriched")

  /** Continue Watching for a non-Trakt service. */
  suspend fun fetchSyncServicePlayback(session: AuthSession, profileId: String, service: String): Result<List<TraktItem>> =
    traktList(session, profileId, "/${encodeQuery(service)}/sync/playback")

  private suspend fun traktList(session: AuthSession, profileId: String, path: String): Result<List<TraktItem>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl$path")
            .headers(authHeaders(session, profileId = profileId))
            .build(),
        )
        ensureOk(response, "Failed to load Trakt data")
        val results = response.json.optJSONArray("results") ?: JSONArray()
        buildList {
          for (index in 0 until results.length()) {
            add(parseTraktItem(results.optJSONObject(index) ?: JSONObject()))
          }
        }
      }
    }
  suspend fun fetchLinkedTvDevices(
    session: AuthSession,
    profileId: String?,
  ): Result<List<LinkedTvDevice>> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/account/bootstrap")
          .headers(authHeaders(session, includeContentType = false, profileId = profileId))
          .build(),
      )
      ensureOk(response, "Could not load linked TVs")
      val devices = response.json.optJSONArray("devices") ?: JSONArray()
      buildList {
        for (index in 0 until devices.length()) {
          val device = devices.optJSONObject(index) ?: continue
          val platform = device.optString("platform").ifBlank { null }
          val deviceType = device.optString("deviceType").ifBlank { null }
          if (!platform.orEmpty().contains("tv", true) && !deviceType.orEmpty().contains("tv", true)) continue
          val id = device.optString("id")
          if (id.isBlank()) continue
          add(
            LinkedTvDevice(
              id = id,
              name = device.optString("name").ifBlank { "StreamDek TV" },
              platform = platform,
              deviceType = deviceType,
              lastSeenAt = device.optString("lastSeenAt").ifBlank { null },
              isCurrent = device.optBoolean("isCurrent"),
              handoffPublicKey = device.optJSONObject("capabilities")?.optString("handoffPublicKey")?.ifBlank { null },
            ),
          )
        }
      }
    }
  }

  suspend fun sendPlaybackHandoff(
    session: AuthSession,
    profileId: String?,
    targetDevice: LinkedTvDevice,
    player: PlayerSession,
    positionSeconds: Double,
  ): Result<PlaybackHandoffReceipt> = withContext(Dispatchers.IO) {
    runCatching {
      val stream = player.currentStream
      val streamJson = JSONObject()
        .put("addonId", stream?.addonId)
        .put("addonName", stream?.addonName)
        .put("name", stream?.name)
        .put("title", stream?.title)
        .put("description", stream?.description)
        // Use the already-resolved playable URL so the TV resumes the exact source selected on mobile.
        .put("url", player.url)
        .put("infoHash", stream?.infoHash)
        .put("fileIdx", stream?.fileIdx)
        .put("filename", stream?.filename)
        .put("quality", stream?.quality ?: player.qualityLabel)
        .put("size", stream?.size ?: player.sizeLabel)
        .put("bingeGroup", stream?.bingeGroup)
        .put("source", stream?.source)
        .put("requestHeaders", JSONObject(player.requestHeaders))
      val payload = JSONObject()
        .put("mediaId", player.mediaId)
        .put("mediaType", player.mediaType)
        .put("imdbId", player.imdbId)
        .put("title", player.title)
        .put("year", player.year)
        .put("seasonNumber", player.seasonNumber)
        .put("episodeNumber", player.episodeNumber)
        .put("episodeTitle", player.episodeTitle)
        .put("positionSeconds", positionSeconds.coerceAtLeast(0.0))
        .put("sourceLabel", player.sourceLabel)
        .put("quality", player.qualityLabel)
        .put("stream", streamJson)
      val publicKey = targetDevice.handoffPublicKey
        ?: throw IllegalStateException("That TV must update or reconnect before it can receive secure handoffs.")
      val encryptedPayload = encryptPlaybackHandoff(payload.toString(), publicKey)
      val response = executeJson(
        "/handoffs",
        JSONObject().put("targetDeviceId", targetDevice.id).put("encryptedPayload", encryptedPayload),
        session = session,
        profileId = profileId,
      )
      ensureOk(response, "Could not send playback to that TV")
      val handoff = response.json.optJSONObject("handoff") ?: throw IllegalStateException("The TV handoff was not created.")
      PlaybackHandoffReceipt(handoff.optString("id"), handoff.optString("expiresAt").ifBlank { null })
    }
  }
  suspend fun activateTvCode(session: AuthSession, userCode: String): Result<String?> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/auth/tv/activate",
        JSONObject().put("user_code", userCode),
        session = session,
      )
      ensureOk(response, "Could not link this TV")
      response.json.optString("deviceName").ifBlank { null }
    }
  }

  suspend fun disconnectAccountDevice(session: AuthSession, deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$apiBaseUrl/account/devices/${encodeQuery(deviceId)}")
        .delete()
        .headers(authHeaders(session, includeContentType = false))
        .build()
      val response = execute(request)
      ensureOk(response, "Could not disconnect this TV")
      Unit
    }
  }


  private suspend fun authPost(path: String, payload: JSONObject): Result<AuthSession> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = executeJson(path, payload)
        ensureOk(response, "Authentication failed")
        val token = response.json.optString("token")
        val user = parseSessionUser(response.json.optJSONObject("user") ?: JSONObject(), token)
        AuthSession(token, user)
      }
    }

  private fun fetchMediaList(path: String, page: Int = 1): List<MediaItem> {
    val pagedPath = if (page > 1) path + (if ("?" in path) "&" else "?") + "page=$page" else path
    android.util.Log.d("StreamDekPaging", "fetchMediaList GET $apiBaseUrl$pagedPath")
    val response = execute(Request.Builder().url("$apiBaseUrl$pagedPath").build())
    ensureOk(response, "Request failed")
    val items = response.json.optJSONArray("results").toMediaItems()
    android.util.Log.d("StreamDekPaging", "fetchMediaList $pagedPath -> ok=${response.ok} items=${items.size}")
    return items
  }

  // The built-in home rows (New Movies, Trending, etc.) are single unpaginated TMDB requests —
  // TMDB's discover/trending endpoints default to a 20-item page, which is exactly why these
  // rows used to stop dead at 20 with no way to fetch more. Kept as its own map (rather than
  // reusing fetchHomeSections' builtInRequests) since "streaming_networks" is a fixed small list
  // of networks, not a paginable content catalog, and has no entry here.
  private val builtInSectionPaths: Map<String, String> = mapOf(
    "new_movies" to "/tmdb/discover?type=movie&sort_by=primary_release_date.desc",
    "new_series" to "/tmdb/discover?type=tv&sort_by=first_air_date.desc",
    "trending_movies" to "/tmdb/trending/movie",
    "trending_series" to "/tmdb/trending/tv",
  )

  /** Pages in more items for a built-in (non-add-on) home row by TMDB page number. */
  suspend fun fetchMoreBuiltInSection(sectionId: String, page: Int): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
    runCatching {
      val path = builtInSectionPaths[sectionId] ?: return@runCatching emptyList()
      fetchMediaList(path, page)
    }
  }

  private fun executeJson(
    path: String,
    payload: JSONObject,
    session: AuthSession? = null,
    profileId: String? = null,
  ): JsonResponse {
    val request = Request.Builder()
      .url("$apiBaseUrl$path")
      .post(payload.toString().toRequestBody(jsonMediaType))
      .headers(authHeaders(session, profileId = profileId))
      .build()
    return execute(request)
  }

  private fun execute(request: Request, httpClient: OkHttpClient = client): JsonResponse {
    httpClient.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      val json = runCatching { JSONObject(body) }.getOrElse {
        if (body.trimStart().startsWith("[")) {
          JSONObject().put("__array", JSONArray(body))
        } else {
          JSONObject()
        }
      }
      return JsonResponse(response.isSuccessful, json, response.code)
    }
  }

  private fun authHeaders(
    session: AuthSession?,
    includeContentType: Boolean = true,
    profileId: String? = null,
  ): okhttp3.Headers {
    val builder = okhttp3.Headers.Builder()
    if (includeContentType) {
      builder.add("Content-Type", "application/json")
    }
    session?.let {
      builder.add("Authorization", "Bearer ${it.token}")
      builder.add("x-user-id", it.user.uid)
      if (!profileId.isNullOrBlank()) {
        builder.add("x-profile-id", profileId)
      }
    }
    clientIdentity?.let { identity ->
      builder.add("x-client-session-id", identity.sessionId)
      builder.add("x-client-device-id", identity.deviceId)
      builder.add("x-client-name", "StreamDek Mobile")
      builder.add("x-client-platform", "android")
      builder.add("x-device-name", identity.deviceName)
      builder.add("x-device-type", "phone")
      identity.previousDeviceId?.let { builder.add("x-previous-device-id", it) }
      builder.add("x-app-version", BuildConfig.VERSION_NAME)
    }
    return builder.build()
  }

  private fun ensureOk(response: JsonResponse, fallback: String) {
    if (!response.ok) {
      throw IllegalStateException(response.json.optString("error").ifBlank { if (response.statusCode > 0) "$fallback (${response.statusCode})" else fallback })
    }
  }

  private fun encodeQuery(query: String): String = URLEncoder.encode(query, Charsets.UTF_8.name())

  private fun buildMagnet(infoHash: String, filename: String?): String {
    val suffix = filename?.takeIf { it.isNotBlank() }?.let { "&dn=${encodeQuery(it)}" }.orEmpty()
    return "magnet:?xt=urn:btih:$infoHash$suffix"
  }

  private fun buildScrobbleJson(payload: TraktScrobblePayload): JSONObject {
    val json = JSONObject().put("progress", payload.progress.coerceIn(0.0, 100.0))
    val ids = JSONObject()
    payload.mediaId.toIntOrNull()?.let { ids.put("tmdb", it) }
    Regex("tt\\d+", RegexOption.IGNORE_CASE).find(payload.mediaId)?.value?.let { ids.put("imdb", it) }
    if (payload.mediaType == "tv") {
      json.put(
        "show",
        JSONObject()
          .put("title", payload.title)
          .put("year", payload.year)
          .put("ids", ids),
      )
      if (payload.seasonNumber != null && payload.episodeNumber != null) {
        json.put(
          "episode",
          JSONObject()
            .put("season", payload.seasonNumber)
            .put("number", payload.episodeNumber)
            .put("title", payload.episodeTitle),
        )
      }
    } else {
      json.put(
        "movie",
        JSONObject()
          .put("title", payload.title)
          .put("year", payload.year)
          .put("ids", ids),
      )
    }
    return json
  }
}

private data class JsonResponse(val ok: Boolean, val json: JSONObject, val statusCode: Int)

private fun parseSessionUser(user: JSONObject, token: String): SessionUser =
  SessionUser(
    uid = user.opt("id")?.toString() ?: user.optString("uid"),
    email = user.optString("email").ifBlank { null },
    displayName = user.optString("displayName").ifBlank { null },
    subscriptionStatus = user.optString("subscriptionStatus").ifBlank { "free" },
    accessToken = token,
  )

private fun serializeSessionUser(user: SessionUser): JSONObject =
  JSONObject()
    .put("id", user.uid)
    .put("email", user.email)
    .put("displayName", user.displayName)
    .put("subscriptionStatus", user.subscriptionStatus)

private fun JSONArray?.toMediaItems(): List<MediaItem> {
  if (this == null) return emptyList()
  return buildList(length()) {
    for (index in 0 until length()) {
      val item = optJSONObject(index) ?: continue
      add(parseMediaItem(item))
    }
  }
}

private fun tmdbImageUrl(value: String?, size: String = "w500"): String? {
  val raw = value?.takeIf { it.isNotBlank() } ?: return null
  return if (raw.startsWith("http")) raw else "https://image.tmdb.org/t/p/$size$raw"
}

/**
 * Artwork off a tracking-service row (Trakt / SIMKL / MDBList).
 *
 * Trakt's enriched endpoints hand back ready-made `poster`/`backdrop` URLs, but the other services
 * spell the field differently and some nest it under `images`, which left those rows with no image
 * at all. Only absolute URLs and TMDB-style paths are accepted — a service-local path would just
 * produce a broken image.tmdb.org URL, so it is left for the artwork backfill to resolve instead.
 */
private fun parseTrackingArtwork(json: JSONObject, size: String, vararg keys: String): String? {
  fun usable(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (raw.startsWith("http") || raw.startsWith("/")) tmdbImageUrl(raw, size) else null
  }
  keys.forEach { key -> usable(json.optString(key))?.let { return it } }
  val images = json.optJSONObject("images") ?: return null
  keys.forEach { key ->
    val candidate = when (val nested = images.opt(key)) {
      is String -> nested
      is JSONArray -> nested.optString(0)
      is JSONObject -> listOf("full", "url", "medium", "thumb").firstNotNullOfOrNull { nested.optString(it).ifBlank { null } }
      else -> null
    }
    usable(candidate)?.let { return it }
  }
  return null
}


private fun parseRatingText(raw: String): Double? {
  raw.toDoubleOrNull()?.let { return it }
  val fraction = Regex("""(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)""").find(raw)
  if (fraction != null) {
    val value = fraction.groupValues[1].toDoubleOrNull()
    val scale = fraction.groupValues[2].toDoubleOrNull()
    if (value != null && scale != null && scale > 0.0) return (value / scale) * 10.0
  }
  return null
}

private fun normalizeRatingValue(value: Double, allowPercent: Boolean = false): Double? {
  if (value.isNaN() || value <= 0.0) return null
  if (value <= 10.0) return value
  if (allowPercent && value <= 100.0) return value / 10.0
  return null
}

private fun parseRatingValue(json: JSONObject, allowPercent: Boolean = false): Double? {
  val keys = listOf("rating", "value", "Value", "vote_average", "imdbRating", "tmdbRating", "score", "averageRating")
  for (key in keys) {
    if (!json.has(key) || json.isNull(key)) continue
    val raw = json.opt(key)
    val value = when (raw) {
      is Number -> raw.toDouble()
      is String -> parseRatingText(raw)
      else -> null
    } ?: continue
    normalizeRatingValue(value, allowPercent)?.let { return it }
  }
  return null
}

private fun parseRatingValue(json: JSONObject, keys: List<String>, allowPercent: Boolean = false): Double? {
  for (key in keys) {
    if (!json.has(key) || json.isNull(key)) continue
    val raw = json.opt(key)
    val value = when (raw) {
      is Number -> raw.toDouble()
      is String -> parseRatingText(raw)
      else -> null
    } ?: continue
    normalizeRatingValue(value, allowPercent)?.let { return it }
  }
  return null
}

private fun providerAllowsPercent(provider: String): Boolean {
  val normalized = provider.lowercase()
  return "audience" in normalized || "tomato" in normalized || "popcorn" in normalized || normalized == "rt"
}

private fun parseRatingFromObjects(json: JSONObject, providerNames: List<String>): Double? {
  val providerKeys = providerNames.map { it.lowercase() }
  val directKeys = providerKeys + providerKeys.map { "${it}Rating" } + providerKeys.map { "${it}_rating" }
  val allowPercent = providerKeys.any(::providerAllowsPercent)
  directKeys.forEach { key ->
    val nested = json.optJSONObject(key) ?: return@forEach
    parseRatingValue(nested, allowPercent)?.let { return it }
  }
  val ratings = json.optJSONArray("ratings") ?: json.optJSONArray("Ratings") ?: return null
  for (index in 0 until ratings.length()) {
    val item = ratings.optJSONObject(index) ?: continue
    val provider = item.optString("provider")
      .ifBlank { item.optString("source") }
      .ifBlank { item.optString("Source") }
      .lowercase()
    if (providerKeys.none { provider.contains(it) }) continue
    parseRatingValue(item, allowPercent || providerAllowsPercent(provider))?.let { return it }
  }
  return null
}

private fun parseMdblistProviderRating(json: JSONObject, allowPercent: Boolean = false): Double? {
  val array = json.optJSONArray("__array") ?: json.optJSONArray("ratings")
  if (array != null) {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      parseRatingValue(item, listOf("rating", "score", "value"), allowPercent)?.let { return it }
    }
  }
  return parseRatingValue(json, listOf("rating", "score", "value"), allowPercent)
}

private fun normalizeMdblistProviderId(value: String): String {
  val key = value.trim().lowercase().replace(Regex("[^a-z]"), "")
  return when (key) {
    "imdb" -> "IMDb"
    "tmdb", "themoviedb" -> "TMDB"
    "rt", "tomatoes", "rottentomatoes", "rottentomato", "rtomatoes" -> "Rotten Tomatoes"
    "mc", "metacritic", "metascore" -> "Metacritic"
    "trakt" -> "Trakt"
    "lb", "letterbox", "letterboxd" -> "Letterboxd"
    "aud", "audience", "audiencescore", "rtaudience", "popcornmeter" -> "Audience Score"
    else -> value
  }
}

private fun parseExternalRatingItem(item: JSONObject): ExternalRating? {
  val provider = item.optString("provider")
    .ifBlank { item.optString("source") }
    .ifBlank { item.optString("Source") }
    .ifBlank { item.optString("name") }
  if (provider.isBlank()) return null
  val rating = parseRatingValue(item, allowPercent = providerAllowsPercent(provider)) ?: return null
  val label = item.optString("label")
    .ifBlank { item.optString("value") }
    .ifBlank { item.optString("Value") }
    .ifBlank { null }
  return ExternalRating(provider = normalizeMdblistProviderId(provider), rating = rating, label = label)
}

private fun parseExternalRatings(json: JSONObject): List<ExternalRating> {
  val arrays = listOfNotNull(
    json.optJSONArray("ratings"),
    json.optJSONArray("Ratings"),
    json.optJSONArray("externalRatings"),
  )
  val parsed = arrays.flatMap { source ->
    buildList {
      for (index in 0 until source.length()) {
        parseExternalRatingItem(source.optJSONObject(index) ?: continue)?.let(::add)
      }
    }
  }
  return buildList {
    addAll(parsed)
    parseImdbRatingValue(json)?.let { add(ExternalRating("IMDb", it)) }
    parseTmdbRatingValue(json)?.let { add(ExternalRating("TMDB", it)) }
    parseRatingFromObjects(json, listOf("trakt"))?.let { add(ExternalRating("Trakt", it)) }
    parseRatingFromObjects(json, listOf("tomatoes", "rotten", "rottentomatoes"))?.let { add(ExternalRating("Rotten Tomatoes", it)) }
    parseRatingFromObjects(json, listOf("metacritic"))?.let { add(ExternalRating("Metacritic", it)) }
    parseRatingFromObjects(json, listOf("letterboxd"))?.let { add(ExternalRating("Letterboxd", it)) }
    parseRatingFromObjects(json, listOf("audience", "audiencescore", "rtaudience", "popcornmeter"))?.let { add(ExternalRating("Audience Score", it)) }
  }.distinctBy { it.provider.lowercase() }
}

private fun parseTraktComments(json: JSONObject): List<TraktComment> {
  val source = json.optJSONArray("comments")
    ?: json.optJSONArray("results")
    ?: json.optJSONArray("__array")
    ?: return emptyList()
  return buildList {
    for (index in 0 until source.length()) {
      val item = source.optJSONObject(index) ?: continue
      val user = item.optJSONObject("user")
      val author = item.optString("author")
        .ifBlank { item.optString("username") }
        .ifBlank { user?.optString("username").orEmpty() }
        .ifBlank { user?.optString("name").orEmpty() }
      val body = item.optString("comment")
        .ifBlank { item.optString("body") }
        .ifBlank { item.optString("text") }
      if (body.isBlank()) continue
      add(
        TraktComment(
          id = item.opt("id")?.toString().orEmpty().ifBlank { "$index-${author.hashCode()}" },
          author = author.ifBlank { "Trakt user" },
          rating = parseTraktCommentRating(item, user),
          body = body,
          likes = item.optInt("likes", item.optInt("like_count", 0)),
        )
      )
    }
  }
}

private fun parseTraktCommentRating(item: JSONObject, user: JSONObject?): Int? {
  val directRating = item.optInt("rating", 0).takeIf { it > 0 }
  if (directRating != null) return directRating
  val nestedCandidates = listOfNotNull(
    item.optJSONObject("review"),
    item.optJSONObject("comment"),
    item.optJSONObject("item"),
    item.optJSONObject("episode"),
    item.optJSONObject("movie"),
    item.optJSONObject("show"),
    user?.optJSONObject("rating"),
  )
  nestedCandidates.forEach { nested ->
    nested.optInt("rating", 0).takeIf { it > 0 }?.let { return it }
    nested.optInt("value", 0).takeIf { it > 0 }?.let { return it }
  }
  return null
}

private fun parseImdbRatingValue(json: JSONObject): Double? =
  parseRatingValue(json, listOf("imdbRating", "imdb_rating", "imdbVoteAverage", "imdb_vote_average", "averageRating"))
    ?: parseRatingFromObjects(json, listOf("imdb"))

private fun parseTmdbRatingValue(json: JSONObject): Double? =
  parseRatingValue(json, listOf("tmdbRating", "tmdb_rating", "vote_average", "rating"))
    ?: parseRatingFromObjects(json, listOf("tmdb", "themoviedb"))


private val tmdbGenreNames = mapOf(
  12 to "Adventure", 14 to "Fantasy", 16 to "Animation", 18 to "Drama", 27 to "Horror", 28 to "Action",
  35 to "Comedy", 36 to "History", 37 to "Western", 53 to "Thriller", 80 to "Crime", 99 to "Documentary",
  878 to "Science Fiction", 9648 to "Mystery", 10402 to "Music", 10749 to "Romance", 10751 to "Family",
  10752 to "War", 10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News", 10764 to "Reality",
  10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics", 10770 to "TV Movie",
)

private fun genreLabel(value: String): String? {
  val cleaned = value.trim()
  if (cleaned.isBlank() || cleaned == "null") return null
  return cleaned.toIntOrNull()?.let { tmdbGenreNames[it] } ?: cleaned
}

private fun parseGenreNames(json: JSONObject): List<String> {
  val arrays = listOfNotNull(json.optJSONArray("genres"), json.optJSONArray("genre_names"), json.optJSONArray("genreNames"), json.optJSONArray("genre_ids"), json.optJSONArray("genreIds"))
  for (source in arrays) {
    val names = buildList {
      for (index in 0 until source.length()) {
        val obj = source.optJSONObject(index)
        val name = if (obj != null) {
          obj.optString("name").ifBlank { genreLabel(obj.opt("id")?.toString().orEmpty()).orEmpty() }
        } else {
          genreLabel(source.opt(index)?.toString().orEmpty()).orEmpty()
        }
        if (name.isNotBlank()) add(name)
      }
    }
    if (names.isNotEmpty()) return names.distinct()
  }
  return emptyList()
}

private fun parseMediaItemId(item: JSONObject): String {
  val ids = item.optJSONObject("ids")
  return listOfNotNull(
    item.opt("tmdbId")?.toString(),
    item.opt("tmdb_id")?.toString(),
    item.opt("tmdb")?.toString(),
    item.opt("moviedb_id")?.toString(),
    item.opt("moviedbId")?.toString(),
    ids?.opt("tmdb")?.toString(),
    ids?.opt("tmdbId")?.toString(),
    ids?.opt("moviedb_id")?.toString(),
    item.opt("id")?.toString(),
    item.opt("imdb_id")?.toString(),
    item.opt("imdbId")?.toString(),
    ids?.opt("imdb")?.toString(),
  ).firstOrNull { it.isNotBlank() }.orEmpty()
}

/**
 * Add-on resource routes are keyed by the exact `id` published in their catalog response.
 * Metadata add-ons commonly include a numeric `tmdbId` alongside a canonical id such as
 * `tmdb:tv:1399`; the general TMDB parser prefers that numeric field, but using it for the
 * add-on's `/meta` and `/stream` routes breaks the resource chain.
 */
internal fun parseAddonCatalogItemId(item: JSONObject): String =
  item.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    ?: parseMediaItemId(item)

/**
 * Whether a catalog entry is an add-on reporting a failure rather than describing a real title.
 *
 * AIOStreams drops a card into the row whenever one of the catalog providers behind it errors —
 * "[❌] Bingecat / Failed to parse meta for Bingecat" — under its own `aiostreamserror` id prefix,
 * which its manifest openly declares. That is a diagnostic for the add-on's operator, not
 * something to put on a viewer's home screen: it has no artwork and opening it leads nowhere.
 */
internal fun isPlaceholderCatalogMeta(item: JSONObject): Boolean {
  val id = item.opt("id")?.toString()?.trim().orEmpty()
  if (id.isBlank() || id.equals("null", ignoreCase = true)) return true
  if (id.startsWith("aiostreamserror", ignoreCase = true)) return true
  val name = item.optString("name").ifBlank { item.optString("title") }.trim()
  return name.startsWith("[❌]")
}

private fun parseMediaItemType(item: JSONObject): String {
  if (item.has("logo") && !item.has("title") && !item.has("poster")) return "network"
  val rawType = item.optString("type").ifBlank { item.optString("media_type").ifBlank { item.optString("kind") } }
  return when (rawType.trim().lowercase()) {
    "series", "show", "tv" -> "tv"
    "movie" -> "movie"
    else -> if (item.has("first_air_date") || item.has("tvdb_id")) "tv" else "movie"
  }
}

private fun parseMediaItemYear(item: JSONObject): String? = listOf(
  item.opt("year")?.toString(),
  item.optString("release_date").take(4),
  item.optString("first_air_date").take(4),
).firstOrNull { !it.isNullOrBlank() && it != "null" }

private fun parseMediaItem(item: JSONObject): MediaItem =
  MediaItem(
    id = parseMediaItemId(item),
    type = parseMediaItemType(item),
    title = item.optString("title").ifBlank { item.optString("name") },
    year = parseMediaItemYear(item),
    poster = item.optString("poster").ifBlank { item.optString("logo") }.ifBlank { tmdbImageUrl(item.optString("poster_path"), "w500") },
    backdrop = item.optString("backdrop").ifBlank { item.optString("background") }.ifBlank { item.optString("banner") }.ifBlank { item.optString("fanart") }.ifBlank { tmdbImageUrl(item.optString("backdrop_path"), "w780") },
    rating = parseRatingValue(item),
    description = item.optString("description").ifBlank { item.optString("overview") },
    genres = parseGenreNames(item),
    titleLogo = parseTitleLogo(item),
    addedAt = parseFlexibleTimestamp(item, "addedAt", "added_at", "listedAt", "listed_at"),
    updatedAt = parseFlexibleTimestamp(item, "updatedAt", "updated_at"),
  )

internal fun parseLocalAddonMetaResponse(root: JSONObject, rawType: String, fallbackId: String): LocalAddonMeta {
  val meta = root.optJSONObject("meta") ?: root.optJSONObject("data")?.optJSONObject("meta") ?: root
  val videos = meta.optJSONArray("videos") ?: JSONArray()
  val episodes = buildList {
    for (index in 0 until videos.length()) {
      val video = videos.optJSONObject(index) ?: continue
      val streamId = video.optString("id").takeIf { it.isNotBlank() } ?: continue
      val season = video.optInt("season").takeIf { it > 0 } ?: 1
      val episode = video.optInt("episode").takeIf { it > 0 } ?: index + 1
      add(
        EpisodeItem(
          id = streamId,
          episodeNumber = episode,
          seasonNumber = season,
          name = video.optString("title").ifBlank { video.optString("name") }.ifBlank { "Episode $episode" },
          overview = video.optString("overview").ifBlank { video.optString("description") },
          still = video.optString("thumbnail").ifBlank { video.optString("poster") }.ifBlank { null },
          runtime = video.optInt("runtime").takeIf { it > 0 },
          airDate = video.optString("released").ifBlank { video.optString("releaseDate") }.ifBlank { null },
          sourceStreamId = streamId,
        ),
      )
    }
  }
  val declaredType = meta.optString("type").ifBlank { rawType }.trim().lowercase()
  val inferredType = when {
    episodes.isNotEmpty() -> "tv"
    declaredType in setOf("series", "show", "tv") -> "tv"
    declaredType == "movie" -> "movie"
    else -> "movie"
  }
  val releaseInfo = meta.optString("releaseInfo").ifBlank { null }
  val year = meta.opt("year")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    ?: releaseInfo?.let { Regex("\\b(19|20)\\d{2}\\b").find(it)?.value }
  val cast = when (val castValue = meta.opt("cast")) {
    is JSONArray -> buildList {
      for (index in 0 until castValue.length()) {
        when (val value = castValue.opt(index)) {
          is JSONObject -> value.optString("name").takeIf { it.isNotBlank() }?.let { name ->
            add(CastMember(id = value.opt("id")?.toString().orEmpty(), name = name, character = value.optString("character").ifBlank { null }, photo = value.optString("photo").ifBlank { null }))
          }
          else -> value?.toString()?.takeIf { it.isNotBlank() }?.let { name -> add(CastMember(id = "addon-cast-$index", name = name, character = null, photo = null)) }
        }
      }
    }
    is String -> castValue.split(',').map(String::trim).filter(String::isNotBlank).mapIndexed { index, name ->
      CastMember(id = "addon-cast-$index", name = name, character = null, photo = null)
    }
    else -> emptyList()
  }
  return LocalAddonMeta(
    id = meta.optString("id").ifBlank { fallbackId },
    imdbId = meta.optString("imdb_id").ifBlank { meta.optString("imdbId") }.ifBlank { null },
    type = inferredType,
    title = meta.optString("name").ifBlank { meta.optString("title") },
    year = year,
    releaseDate = meta.optString("released").ifBlank { meta.optString("releaseDate") }.ifBlank { null },
    description = meta.optString("description").ifBlank { meta.optString("overview") },
    poster = meta.optString("poster").ifBlank { null },
    backdrop = meta.optString("background").ifBlank { meta.optString("backdrop") }.ifBlank { null },
    genres = parseGenreNames(meta),
    runtimeMinutes = meta.optInt("runtime").takeIf { it > 0 },
    cast = cast,
    episodes = episodes,
  )
}

internal fun stripHlsSubtitleRenditions(manifest: String, baseUrl: String? = null): String {
  val start = manifest.indexOf("#EXTM3U")
  if (start < 0) return manifest
  val playlist = manifest.substring(start)
  val lines = playlist.lines()
  val hasSubtitles = lines.any { it.trimStart().startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES") }
  if (!hasSubtitles) return manifest
  val baseUri = baseUrl?.let { runCatching { URI(it) }.getOrNull() }
  val uriAttribute = Regex("URI=\"([^\"]+)\"")
  return lines.asSequence()
    .filterNot { it.trimStart().startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES") }
    .map { line ->
      val withoutSubtitles = line.replace(Regex(",?SUBTITLES=\"[^\"]+\""), "")
      if (withoutSubtitles.isNotBlank() && !withoutSubtitles.startsWith("#")) {
        baseUri?.resolve(withoutSubtitles)?.toString() ?: withoutSubtitles
      } else {
        uriAttribute.replace(withoutSubtitles) { match ->
          val resolved = baseUri?.resolve(match.groupValues[1])?.toString() ?: match.groupValues[1]
          "URI=\"$resolved\""
        }
      }
    }
    .joinToString("\n")
    .let { if (playlist.endsWith("\n")) "$it\n" else it }
}

private fun parseFlexibleTimestamp(json: JSONObject, vararg keys: String): Long? {
  keys.forEach { key ->
    val raw = json.opt(key) ?: return@forEach
    when (raw) {
      is Number -> {
        val value = raw.toLong()
        if (value > 0L) return if (value < 1_000_000_000_000L) value * 1000L else value
      }
      is String -> {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) return@forEach
        trimmed.toLongOrNull()?.let { numeric ->
          if (numeric > 0L) return if (numeric < 1_000_000_000_000L) numeric * 1000L else numeric
        }
        runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()?.let { if (it > 0L) return it }
      }
    }
  }
  return null
}
private fun parseCastList(source: JSONArray?): List<CastMember> {
  if (source == null) return emptyList()
  return buildList {
    for (index in 0 until source.length()) {
      val member = source.optJSONObject(index) ?: continue
      val name = member.optString("name")
      if (name.isBlank()) continue
      add(
        CastMember(
          id = member.opt("id")?.toString().orEmpty(),
          name = name,
          character = member.optString("character").ifBlank { null },
          photo = member.optString("photo").ifBlank { tmdbImageUrl(member.optString("profile_path"), "w185") },
        )
      )
    }
  }
}

private fun parsePersonDetail(json: JSONObject): PersonDetail {
  val person = json.optJSONObject("person") ?: json
  val works = json.optJSONArray("popularWorks") ?: json.optJSONArray("knownFor") ?: json.optJSONArray("credits") ?: JSONArray()
  return PersonDetail(
    id = person.opt("id")?.toString().orEmpty(),
    name = person.optString("name"),
    photo = person.optString("photo").ifBlank { tmdbImageUrl(person.optString("profile_path"), "w342") },
    biography = person.optString("biography").ifBlank { null },
    birthday = person.optString("birthday").ifBlank { null },
    placeOfBirth = person.optString("placeOfBirth").ifBlank { person.optString("place_of_birth").ifBlank { null } },
    knownFor = person.optString("knownForDepartment").ifBlank { person.optString("known_for_department").ifBlank { null } },
    popularWorks = works.toMediaItems(),
  )
}
private fun parseTitleLogo(json: JSONObject): String? {
  json.optString("titleLogo").ifBlank { null }?.let { return tmdbImageUrl(it, "w500") }
  json.optString("title_logo").ifBlank { null }?.let { return tmdbImageUrl(it, "w500") }
  json.optString("logo").ifBlank { null }?.let { return tmdbImageUrl(it, "w500") }
  val logos = json.optJSONObject("images")?.optJSONArray("logos") ?: json.optJSONArray("logos") ?: return null
  for (index in 0 until logos.length()) {
    val logo = logos.optJSONObject(index) ?: continue
    val language = logo.optString("iso_639_1")
    if (language.isBlank() || language == "en") return tmdbImageUrl(logo.optString("file_path"), "w500")
  }
  return null
}

private fun trailerUrlFor(site: String?, key: String?): String? {
  if (key.isNullOrBlank()) return null
  return when {
    site.equals("YouTube", ignoreCase = true) -> "https://www.youtube.com/watch?v=$key"
    site.equals("Vimeo", ignoreCase = true) -> "https://vimeo.com/$key"
    else -> null
  }
}

private fun parseTrailerKeys(json: JSONObject): List<String> {
  val directKeys = json.optJSONArray("trailerKeys")
  if (directKeys != null && directKeys.length() > 0) {
    return buildList {
      for (index in 0 until directKeys.length()) {
        directKeys.optString(index).ifBlank { null }?.let(::add)
      }
    }
  }
  val videos = json.optJSONObject("videos")?.optJSONArray("results") ?: json.optJSONArray("videos") ?: return emptyList()
  return buildList {
    for (index in 0 until videos.length()) {
      val video = videos.optJSONObject(index) ?: continue
      if (!video.optString("site").equals("YouTube", ignoreCase = true)) continue
      video.optString("key").ifBlank { null }?.let(::add)
    }
  }
}

private fun parseTrailerUrl(json: JSONObject): String? {
  json.optString("trailerUrl").ifBlank { null }?.let { return it }
  json.optString("trailer").ifBlank { null }?.let { return it }
  trailerUrlFor(json.optString("trailerSite"), json.optString("trailerKey"))?.let { return it }
  parseTrailerKeys(json).firstOrNull()?.let { return trailerUrlFor("YouTube", it) }
  val videos = json.optJSONObject("videos")?.optJSONArray("results") ?: json.optJSONArray("videos") ?: return null
  for (index in 0 until videos.length()) {
    val video = videos.optJSONObject(index) ?: continue
    val site = video.optString("site")
    val key = video.optString("key")
    val type = video.optString("type")
    if (key.isBlank()) continue
    if (site.equals("YouTube", ignoreCase = true) && type.contains("Trailer", ignoreCase = true)) return "https://www.youtube.com/watch?v=$key"
    video.optString("url").ifBlank { null }?.let { return it }
  }
  return null
}
private fun parseStringMap(source: JSONObject?): Map<String, String> = buildMap {
  source?.keys()?.forEach { key -> source.optString(key).trim().takeIf { it.isNotBlank() }?.let { put(key, it) } }
}

private fun parseWatchProviderList(source: JSONArray?): List<WatchProvider> {
  if (source == null) return emptyList()
  return buildList {
    for (index in 0 until source.length()) {
      val item = source.optJSONObject(index) ?: continue
      val name = item.optString("name").ifBlank { item.optString("provider_name") }
      if (name.isBlank()) continue
      add(
        WatchProvider(
          id = item.opt("id")?.toString() ?: item.opt("provider_id")?.toString() ?: name,
          name = name,
          logo = item.optString("logo").ifBlank { tmdbImageUrl(item.optString("logo_path"), "w500") },
          url = item.optString("url").ifBlank { item.optString("link") }.ifBlank { item.optString("web_url") }.ifBlank { null },
        )
      )
    }
  }
}

private fun parseProviderBucket(source: JSONObject?): List<WatchProvider> {
  if (source == null) return emptyList()
  return buildList {
    addAll(parseWatchProviderList(source.optJSONArray("flatrate")))
    addAll(parseWatchProviderList(source.optJSONArray("subscription")))
    addAll(parseWatchProviderList(source.optJSONArray("rent")))
  }
}

private fun parseWatchProviders(json: JSONObject): List<WatchProvider> {
  val normalized = buildList {
    addAll(parseWatchProviderList(json.optJSONArray("availableOn")))
    addAll(parseWatchProviderList(json.optJSONArray("streamingProviders")))
    addAll(parseWatchProviderList(json.optJSONArray("streaming_providers")))
    addAll(parseWatchProviderList(json.optJSONArray("rentProviders")))
    addAll(parseWatchProviderList(json.optJSONArray("rent_providers")))
    addAll(parseWatchProviderList(json.optJSONArray("providers")))
    addAll(parseWatchProviderList(json.optJSONArray("watchProviders")))
    addAll(parseWatchProviderList(json.optJSONArray("watch_providers")))
    addAll(parseWatchProviderList(json.optJSONArray("networks")))
    addAll(parseProviderBucket(json.optJSONObject("providers")))
    addAll(parseProviderBucket(json.optJSONObject("watchProviders")))
    addAll(parseProviderBucket(json.optJSONObject("watch_providers")))
  }
  if (normalized.isNotEmpty()) return normalized.distinctBy { it.id.ifBlank { it.name } }

  val watchProviders = json.optJSONObject("watch/providers") ?: return emptyList()
  val regions = watchProviders.optJSONObject("results") ?: watchProviders
  val region = regions.optJSONObject("US")
    ?: regions.optJSONObject("GB")
    ?: regions.optJSONObject("NG")
    ?: regions.keys().asSequence().mapNotNull { regions.optJSONObject(it) }.firstOrNull()
    ?: return emptyList()
  return parseProviderBucket(region).distinctBy { it.id.ifBlank { it.name } }
}

private fun parseReleaseDate(json: JSONObject): String? =
  json.optString("releaseDate").ifBlank {
    json.optString("release_date").ifBlank {
      json.optString("firstAirDate").ifBlank {
        json.optString("first_air_date").ifBlank { null }
      }
    }
  }

private fun parseMediaDetail(json: JSONObject): MediaDetail =
  MediaDetail(
    id = json.opt("tmdbId")?.toString() ?: json.opt("id")?.toString().orEmpty(),
    type = parseMediaItemType(json),
    title = json.optString("title").ifBlank { json.optString("name") },
    titleLogo = parseTitleLogo(json),
    tagline = json.optString("tagline").ifBlank { null },
    year = parseMediaItemYear(json),
    releaseDate = parseReleaseDate(json),
    description = json.optString("description").ifBlank { json.optString("overview") },
    poster = json.optString("poster").ifBlank { tmdbImageUrl(json.optString("poster_path"), "w500") },
    backdrop = json.optString("backdrop").ifBlank { tmdbImageUrl(json.optString("backdrop_path"), "w780") },
    trailerUrl = parseTrailerUrl(json),
    trailerSite = json.optString("trailerSite").ifBlank { null },
    trailerKeys = parseTrailerKeys(json),
    rating = parseRatingValue(json),
    imdbRating = parseImdbRatingValue(json) ?: parseRatingValue(json),
    tmdbRating = parseTmdbRatingValue(json),
    externalRatings = parseExternalRatings(json),
    genres = parseGenreNames(json),
    runtimeMinutes = json.optInt("runtime").takeIf { it > 0 },
    seasonsCount = json.optInt("numberOfSeasons").takeIf { it > 0 },
    imdbId = json.optString("imdbId").ifBlank { json.optString("imdb_id").ifBlank { json.optJSONObject("external_ids")?.optString("imdb_id").orEmpty().ifBlank { null } } },
    seasons = buildList {
      val source = json.optJSONArray("seasons") ?: JSONArray()
      for (index in 0 until source.length()) {
        val season = source.optJSONObject(index) ?: continue
        add(
          SeasonSummary(
            seasonNumber = season.optInt("season_number"),
            name = season.optString("name").ifBlank { "Season ${season.optInt("season_number")}" },
            episodeCount = season.optInt("episode_count"),
            poster = season.optString("poster").ifBlank { tmdbImageUrl(season.optString("poster_path"), "w342") },
          )
        )
      }
    },
    cast = parseCastList(json.optJSONArray("cast") ?: json.optJSONObject("credits")?.optJSONArray("cast")),
    similarTitles = (json.optJSONArray("similarTitles") ?: json.optJSONArray("similar") ?: json.optJSONArray("recommendations")).toMediaItems(),
    availableOn = parseWatchProviders(json),
    traktComments = parseTraktComments(json),
  )

private fun parseEpisode(json: JSONObject, fallbackSeasonNumber: Int? = null): EpisodeItem =
  EpisodeItem(
    id = json.opt("id")?.toString().orEmpty(),
    episodeNumber = json.optInt("episode_number"),
    seasonNumber = json.optInt("season_number").takeIf { it > 0 } ?: fallbackSeasonNumber ?: 0,
    name = json.optString("name").ifBlank { "Episode ${json.optInt("episode_number")}" },
    overview = json.optString("overview"),
    still = json.optString("still").ifBlank { null },
    runtime = json.optInt("runtime").takeIf { it > 0 },
    airDate = json.optString("air_date").ifBlank { json.optString("airDate").ifBlank { json.optString("release_date").ifBlank { null } } },
  )

private fun parseProfile(json: JSONObject): StreamProfile =
  StreamProfile(
    id = json.optString("id"),
    userId = json.optString("userId"),
    name = json.optString("name"),
    avatarIndex = json.optInt("avatarIndex"),
    hasPinSet = json.optBoolean("hasPinSet"),
    isDefault = json.optBoolean("isDefault"),
    subtitleLanguage = json.optString("subtitleLanguage").ifBlank { null },
    audioLanguage = json.optString("audioLanguage").ifBlank { null },
  )

private fun parseAddonStringList(values: JSONArray?): List<String> = buildList {
  val source = values ?: return@buildList
  for (index in 0 until source.length()) {
    val item = source.opt(index)
    when (item) {
      is String -> item.ifBlank { null }?.let(::add)
      is JSONObject -> item.optString("name").ifBlank { null }?.let(::add)
    }
  }
}

// Some catalogs (esp. "browse this network" style ones) declare a "genre" extra property
// with a list of named sub-lists instead of one flat listing — without picking one of those
// options, the addon has nothing to key its response on. See fetchLocalAddonCatalog / the
// genre handling in fetchAddonCatalog below for where the first option gets used.
/**
 * Whether the catalog accepts a `search` extra.
 *
 * Both manifest spellings are accepted: the structured `extra: [{ name: "search" }]` form and the
 * older flat `extraSupported: ["search"]` list, which plenty of installed add-ons still use.
 */
private fun parseAddonSearchSupported(item: JSONObject): Boolean {
  item.optJSONArray("extra")?.let { extra ->
    for (index in 0 until extra.length()) {
      val entry = extra.optJSONObject(index) ?: continue
      if (entry.optString("name").equals("search", ignoreCase = true)) return true
    }
  }
  return parseAddonStringList(item.optJSONArray("extraSupported"))
    .any { it.equals("search", ignoreCase = true) }
}

private fun parseAddonGenreOptions(item: JSONObject): List<String> {
  val extra = item.optJSONArray("extra")
  if (extra != null) {
    for (index in 0 until extra.length()) {
      val entry = extra.optJSONObject(index) ?: continue
      if (!entry.optString("name").equals("genre", ignoreCase = true)) continue
      val options = entry.optJSONArray("options") ?: continue
      return parseAddonStringList(options)
    }
  }
  // Older manifests list the options under a top-level "genres" alongside extraSupported/
  // extraRequired rather than inside a structured "extra" entry.
  return parseAddonStringList(item.optJSONArray("genres"))
}

/**
 * Whether the catalog cannot be listed at all without a genre.
 *
 * Only a *required* genre gets filled in automatically. A catalog with an optional genre filter
 * answers perfectly well without one, and forcing the first option there would silently turn a
 * "Popular" row into "Popular · Action".
 */
private fun parseAddonGenreRequired(item: JSONObject): Boolean {
  item.optJSONArray("extra")?.let { extra ->
    for (index in 0 until extra.length()) {
      val entry = extra.optJSONObject(index) ?: continue
      if (entry.optString("name").equals("genre", ignoreCase = true) && entry.optBoolean("isRequired")) return true
    }
  }
  return parseAddonStringList(item.optJSONArray("extraRequired")).any { it.equals("genre", ignoreCase = true) }
}

private fun parseAddonCatalogs(values: JSONArray?): List<AddonCatalog> = buildList {
  val source = values ?: return@buildList
  for (index in 0 until source.length()) {
    val item = source.optJSONObject(index) ?: continue
    val id = item.optString("id").trim()
    if (id.isEmpty()) continue
    add(
      AddonCatalog(
        type = item.optString("type").trim(),
        id = id,
        name = item.optString("name").ifBlank { id },
        genreOptions = parseAddonGenreOptions(item),
        requiresGenre = parseAddonGenreRequired(item),
        supportsSearch = parseAddonSearchSupported(item),
      ),
    )
  }
}

/** Maps an add-on's own catalog type onto the app's, or null when the app has nowhere to show it.
 * Also used by the search screen to decide which catalogs can serve the type being browsed. */
internal fun mapHomeCatalogType(rawType: String): String? = when (rawType.trim().lowercase()) {
  "movie" -> "movie"
  "series", "tv" -> "tv"
  "sport", "sports", "channel", "live", "other" -> rawType.trim().lowercase()
  else -> null
}

// Home rows show only the catalog's own name \u2014 the provider/addon name and any
// account email the addon embeds in its labels are stripped out.
internal fun cleanAddonCatalogLabel(rawName: String, addonName: String = ""): String {
  var label = rawName.trim()
  // Drop email addresses and any bracketed/parenthesised wrapper left behind.
  label = label.replace(Regex("""[\w.+-]+@[\w-]+\.[\w.]+"""), "")
  // Note: ] and } must be escaped — Android's ICU regex engine rejects them bare.
  label = label.replace(Regex("""\(\s*\)|\[\s*\]|\{\s*\}"""), "")
  val addon = addonName.trim()
  if (addon.isNotEmpty()) {
    label = label.replace(Regex("(?i)(?<![\\w])" + Regex.escape(addon) + "(?![\\w])"), "")
  }
  // Tidy up separators left dangling by the removals above.
  label = label.replace(Regex("""\s*[-\u2013\u2014|\u2022\u00b7:]\s*"""), " - ").trim()
  label = label.trim(' ', '-', '\u2013', '\u2014', '|', '\u2022', '\u00b7', ':', ',')
  return label.replace(Regex("""\s{2,}"""), " ").trim()
}

private fun buildAddonSectionTitle(addonName: String, catalogName: String?, differentiator: String? = null): String {
  val addon = addonName.trim()
  val catalog = cleanAddonCatalogLabel(catalogName.orEmpty(), addon)
  // Fall back to the addon name only when nothing else identifies the row.
  val base = catalog.ifBlank { cleanAddonCatalogLabel(addon).ifBlank { addon } }
  return if (differentiator.isNullOrBlank()) base else "$base \u2022 $differentiator"
}

private fun parseAddon(json: JSONObject): InstalledAddon {
  val manifest = json.optJSONObject("manifest") ?: JSONObject()
  val behaviorHints = manifest.optJSONObject("behaviorHints") ?: JSONObject()
  return InstalledAddon(
    id = json.optString("id"),
    enabled = json.optBoolean("enabled"),
    position = json.optInt("position"),
    url = json.optString("url").ifBlank { null },
    baseUrl = json.optString("baseUrl").ifBlank { null },
    manifestUrl = json.optString("manifestUrl").ifBlank { null },
    transportUrl = json.optString("transportUrl").ifBlank { null },
    manifest = AddonManifest(
      id = manifest.optString("id").ifBlank { json.optString("id") },
      name = manifest.optString("name").ifBlank { json.optString("id") },
      version = manifest.optString("version").ifBlank { "unknown" },
      description = manifest.optString("description").ifBlank { null },
      logo = manifest.optString("logo").ifBlank { null },
      url = manifest.optString("url").ifBlank { json.optString("url").ifBlank { null } },
      baseUrl = manifest.optString("baseUrl").ifBlank { json.optString("baseUrl").ifBlank { null } },
      manifestUrl = manifest.optString("manifestUrl").ifBlank { json.optString("manifestUrl").ifBlank { null } },
      transportUrl = manifest.optString("transportUrl").ifBlank { json.optString("transportUrl").ifBlank { null } },
      resources = parseAddonStringList(manifest.optJSONArray("resources")),
      types = parseAddonStringList(manifest.optJSONArray("types")),
      catalogs = parseAddonCatalogs(manifest.optJSONArray("catalogs")),
      behaviorConfigurable = behaviorHints.optBoolean("configurable"),
      configurationRequired = behaviorHints.optBoolean("configurationRequired"),
    ),
  )
}

private fun parseStreamsResponse(json: JSONObject): List<AddonStream> {
  val streams = json.optJSONArray("streams")
    ?: json.optJSONArray("results")
    ?: json.optJSONArray("items")
    ?: json.optJSONArray("__array")
    ?: JSONArray()
  return buildList {
    for (index in 0 until streams.length()) {
      add(parseAddonStream(streams.optJSONObject(index) ?: JSONObject()))
    }
  }
}

private fun extractInfoHash(rawInfoHash: String?, rawUrl: String?): String? {
  rawInfoHash?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
  val url = rawUrl?.trim().orEmpty()
  if (!url.startsWith("magnet:?", ignoreCase = true)) return null
  val match = Regex("""btih:([A-Fa-f0-9]{32,40})""").find(url) ?: return null
  return match.groupValues[1]
}

private fun parseAddonStream(json: JSONObject): AddonStream =
  json.run {
    val parsedUrl = sequenceOf(opt("url"), opt("externalUrl"), optJSONObject("behaviorHints")?.opt("url"))
      .mapNotNull { value ->
        when (value) {
          is JSONObject -> value.optString("url").ifBlank { value.optString("href") }.ifBlank { null }
          else -> value?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        }
      }
      .firstOrNull()
    AddonStream(
      addonId = optString("addonId"),
      addonName = optString("addonName").ifBlank { optString("addonId") },
      name = optString("name").ifBlank { null },
      title = optString("title").ifBlank { null },
      description = optString("description").ifBlank { null },
      url = parsedUrl,
      infoHash = extractInfoHash(optString("infoHash").ifBlank { null }, parsedUrl),
      fileIdx = optInt("fileIdx").takeIf { has("fileIdx") },
      filename = optJSONObject("behaviorHints")?.optString("filename")?.ifBlank { null },
      quality = optString("quality").ifBlank { null },
      size = optString("size").ifBlank { null },
      bingeGroup = optJSONObject("behaviorHints")?.optString("bingeGroup")?.ifBlank { null },
      requestHeaders = parseStreamRequestHeaders(optJSONObject("behaviorHints"), optJSONObject("headers")),
      source = optString("source").ifBlank { optString("provider") }.ifBlank { null },
      nzbUrl = optString("nzbUrl").ifBlank { null },
      nntpServers = buildList {
        val servers = optJSONArray("servers") ?: JSONArray()
        for (index in 0 until servers.length()) {
          servers.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
      },
      cachedBy = buildList {
        val providers = optJSONArray("cachedBy") ?: JSONArray()
        for (index in 0 until providers.length()) {
          add(providers.optString(index))
        }
      },
    )
  }


private fun parseStreamRequestHeaders(behaviorHints: JSONObject?, directHeaders: JSONObject?): Map<String, String> {
  val candidates = listOfNotNull(
    behaviorHints?.optJSONObject("proxyHeaders")?.optJSONObject("request"),
    behaviorHints?.optJSONObject("requestHeaders"),
    directHeaders,
  )
  return buildMap {
    candidates.forEach { source ->
      source.keys().forEach { key ->
        source.optString(key).trim().takeIf { it.isNotBlank() }?.let { put(key, it) }
      }
    }
  }
}private fun parseDebridAccount(json: JSONObject): DebridAccount =
  DebridAccount(
    provider = json.optString("provider"),
    enabled = json.optBoolean("enabled"),
    priority = json.optInt("priority"),
    username = json.optString("username").ifBlank { null },
  )

private fun parseTraktItem(json: JSONObject): TraktItem =
  TraktItem(
    id = json.optString("id").ifBlank { json.opt("tmdbId")?.toString().orEmpty() },
    tmdbId = json.optInt("tmdbId").takeIf { it > 0 },
    title = json.optString("title").ifBlank { json.optString("name") },
    type = parseMediaItemType(json),
    year = parseMediaItemYear(json),
    rating = parseRatingValue(json),
    poster = parseTrackingArtwork(json, "w500", "poster", "poster_path", "posterUrl", "poster_url", "image"),
    backdrop = parseTrackingArtwork(json, "w780", "backdrop", "backdrop_path", "backdropUrl", "backdrop_url", "fanart"),
    description = json.optString("description").ifBlank { null },
    progress = json.optDouble("progress").takeUnless { it.isNaN() || it == 0.0 },
    addedAt = parseFlexibleTimestamp(json, "listed_at", "listedAt", "added_at", "addedAt"),
    updatedAt = parseFlexibleTimestamp(json, "updated_at", "updatedAt", "paused_at", "pausedAt", "last_watched_at", "lastWatchedAt", "watched_at", "watchedAt"),
  )















