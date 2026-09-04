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

/** The version prefix every canonical StreamDek API path carries. */
const val API_PATH_PREFIX = "/api/v1"


private const val SESSION_PREFS = "streamdek_native_session"
private const val SESSION_TOKEN_KEY = "token"
private const val SESSION_USER_JSON_KEY = "user_json"
private const val SESSION_REFRESH_TOKEN_KEY = "refresh_token"
private const val PROFILE_PREFS = "streamdek_native_profiles"
private const val GUEST_PROFILE_PREFS = "streamdek_native_guest_profiles"
private const val WATCHLIST_PREFS = "streamdek_native_watchlist"
private const val FAVOURITE_CHANNELS_PREFS = "streamdek_native_favourite_channels"
private const val CLIENT_IDENTITY_PREFS = "streamdek_native_client_identity"
private const val CLIENT_DEVICE_ID_KEY = "device_id"
private const val CLIENT_PREVIOUS_DEVICE_ID_KEY = "previous_device_id"
/** How many items of an add-on catalog a Home row previews before "View All" takes over. */
internal const val HOME_ROW_PREVIEW_LIMIT = 20

/**
 * How much of an add-on catalog's answer is kept.
 *
 * This used to be the preview length, so 20 items were kept and the rest of a response the
 * add-on had already sent in full was thrown away. "View All" then had to ask for those items
 * again with a Stremio `skip` offset — which only works on add-ons that implement `skip`. A live
 * TV add-on typically does not: TvVoo answers every request with its entire country list (684 UK
 * channels, and the same 684 whatever `skip` says) and declares no `skip` extra at all, so its
 * "View All" could never show more than the twenty the preview had kept.
 *
 * The whole response is kept now, bounded only so that one enormous catalog cannot sit in memory
 * unchecked. Catalogs that do support `skip` still page past this from "View All".
 */
private const val ADDON_CATALOG_MAX_ITEMS = 2_000

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
      AuthSession(
        token = token,
        user = parseSessionUser(JSONObject(userJson), token),
        refreshToken = prefs.getString(SESSION_REFRESH_TOKEN_KEY, null),
      )
    }.getOrNull()
  }

  fun save(session: AuthSession) {
    val editor = prefs.edit()
      .putString(SESSION_TOKEN_KEY, session.token)
      .putString(SESSION_USER_JSON_KEY, serializeSessionUser(session.user).toString())
    // Carried across rather than cleared when a save does not carry one: several writes here are
    // metadata refreshes that rebuild the session from /auth/me, and dropping the refresh token
    // on one of those would leave the session unable to renew.
    session.refreshToken?.let { editor.putString(SESSION_REFRESH_TOKEN_KEY, it) }
    editor.apply()
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
          if (isAdultCatalogEntry(item)) continue
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

/**
 * Where a client's own settings live inside the shared preferences document.
 *
 * Mirrors the backend's convention (settingsSchema.ts) rather than inventing a second one: a
 * setting the schema marks as per-platform is read from and written to this section, and
 * everything else stays exactly where it has always been.
 */
internal const val PLATFORM_PREFERENCES_KEY = "platforms"
internal const val PLATFORM_PREFERENCES_PLATFORM = "mobile"

/** TMDB serves twenty results per page on every list endpoint the catalogs use. */
internal const val CATALOG_PAGE_SIZE = 20

/**
 * Trackers used when an add-on supplies none of its own.
 *
 * A bare info-hash is answerable only through DHT, which on a mobile connection is slow at best
 * and blocked at worst — so a source that arrived without trackers used to find no peers, produce
 * no metadata, and fail as though it were dead. These are the long-standing public announce URLs
 * that peer-to-peer clients ship with; they are a floor under such sources, not a replacement for the
 * add-on's own list, which is always preferred and always included.
 */
private val FALLBACK_TRACKERS = listOf(
  // UDP first: it is what trackers answer fastest on when a network allows it.
  "udp://tracker.opentrackr.org:1337/announce",
  "udp://open.demonii.com:1337/announce",
  "udp://open.stealth.si:80/announce",
  "udp://tracker.torrent.eu.org:451/announce",
  "udp://exodus.desync.com:6969/announce",
  "udp://tracker.openbittorrent.com:6969/announce",
  "udp://explodie.org:6969/announce",
  "udp://tracker1.bt.moack.co.kr:80/announce",
  // Not redundant with the list above. A network that blocks peer-to-peer traffic — a VPN on a
  // server that does not carry it, a router dropping unsolicited UDP — takes every tracker above
  // and the DHT with it, and these are then the only way left to find a peer at all.
  //
  // Plain HTTP on purpose. The OpenSSL compiled into the peer engine has its certificate directory
  // baked in as the path it was built at in 2018 — a path that exists on no Android device — so it
  // can verify nobody, and every https:// announce fails the handshake before it is sent. Two such
  // trackers used to sit here doing nothing but adding a timeout to the wait.
  "http://tracker.openbittorrent.com:80/announce",
  "http://tracker.files.fm:6969/announce",
  "http://open.acgnxtracker.com:80/announce",
)

/**
 * Announce URLs for a stream, from the add-on's `sources` list.
 *
 * Stremio add-ons publish these as `tracker:<url>` and `dht:<hash>`; only the trackers are of use
 * here, since DHT is already on. The public list is always appended, so a source arrives with the
 * add-on's trackers and a floor under them rather than with one or the other.
 */
internal fun streamTrackers(sources: List<String>): List<String> {
  val declared = sources.asSequence()
    .map { it.trim() }
    .filter { it.startsWith("tracker:", ignoreCase = true) }
    .map { it.removePrefix("tracker:").removePrefix("TRACKER:").trim() }
    .filter { it.isNotBlank() }
    .distinct()
    .toList()
  // Merged, not chosen between. An add-on that declares a single stale announce URL used to
  // replace the whole fallback list with it, which is the case this function exists to protect
  // against — the add-on's own trackers still come first, because they are the ones that know
  // about this particular swarm.
  return (declared + FALLBACK_TRACKERS).distinct()
}

/**
 * A magnet link a peer swarm can actually answer: the hash, a display name, and every announce URL
 * known for it.
 */
internal fun buildMagnetLink(infoHash: String, filename: String?, sources: List<String>): String {
  val builder = StringBuilder("magnet:?xt=urn:btih:").append(infoHash.trim())
  filename?.takeIf { it.isNotBlank() }?.let { builder.append("&dn=").append(URLEncoder.encode(it, Charsets.UTF_8.name())) }
  streamTrackers(sources).forEach { tracker ->
    builder.append("&tr=").append(URLEncoder.encode(tracker, Charsets.UTF_8.name()))
  }
  return builder.toString()
}

/** Reads the catalog registry out of a `/tmdb/catalogs` payload, skipping anything unusable. */
internal fun parseCatalogManifest(json: JSONObject): List<CatalogDefinition> {
  val catalogs = json.optJSONArray("catalogs") ?: return emptyList()
  return buildList {
    for (index in 0 until catalogs.length()) {
      val entry = catalogs.optJSONObject(index) ?: continue
      val id = entry.optString("id").trim()
      val title = entry.optString("title").trim()
      if (id.isEmpty() || title.isEmpty()) continue
      add(
        CatalogDefinition(
          id = id,
          title = title,
          mediaType = entry.optString("media_type").ifBlank { "movie" },
          group = entry.optString("group").ifBlank { "other" },
          previewLimit = entry.optInt("preview_limit").takeIf { it > 0 } ?: CATALOG_PAGE_SIZE,
          maxItems = entry.optInt("max_items").takeIf { it > 0 },
          paginated = entry.optBoolean("paginated", true),
        ),
      )
    }
  }
}

/**
 * Reads home rows out of a `/tmdb/home` payload.
 *
 * A row that came back with nothing usable in it is dropped here rather than handed on: an empty
 * carousel is worse than no row at all, and this way no caller has to remember to check.
 */
internal fun parseCatalogHomeSections(json: JSONObject): List<MediaSection> {
  val sections = json.optJSONArray("sections") ?: return emptyList()
  return buildList {
    for (index in 0 until sections.length()) {
      val entry = sections.optJSONObject(index) ?: continue
      val id = entry.optString("id").trim()
      if (id.isEmpty()) continue
      val items = (entry.optJSONArray("results") ?: JSONArray()).toMediaItems()
      if (items.isEmpty()) continue
      add(
        MediaSection(
          id = id,
          title = entry.optString("title").ifBlank { id },
          items = items,
          nextPage = entry.optInt("next_page").takeIf { it > 1 },
          totalPages = entry.optInt("total_pages"),
        ),
      )
    }
  }
}

private const val CATALOG_MANIFEST_TTL_MS = 6L * 60L * 60L * 1000L

private class CatalogManifestCacheEntry(
  val definitions: List<CatalogDefinition>,
  private val fetchedAt: Long = System.currentTimeMillis(),
) {
  fun isFresh(): Boolean = System.currentTimeMillis() - fetchedAt < CATALOG_MANIFEST_TTL_MS
}

/**
 * Short-lived memory of catalog pages already fetched.
 *
 * Its job is the walk a viewer actually makes — open a row, scroll, open a title, come back, back
 * out to home, open the row again — which without it re-requests every page each time. The
 * backend holds the authoritative per-catalog lifetimes (long for studio and archival rows, short
 * for trending and theatrical); this side only needs to be short enough never to contradict them.
 */
private class CatalogPageCache(
  private val ttlMs: Long = 5L * 60L * 1000L,
  private val maxEntries: Int = 120,
) {
  private class Entry(val page: DiscoverPage, val storedAt: Long)

  private val entries = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
  }

  private fun key(catalogId: String, region: String, page: Int) = "$catalogId|$region|$page"

  @Synchronized
  fun get(catalogId: String, region: String, page: Int): DiscoverPage? {
    val entry = entries[key(catalogId, region, page)] ?: return null
    if (System.currentTimeMillis() - entry.storedAt > ttlMs) {
      entries.remove(key(catalogId, region, page))
      return null
    }
    return entry.page
  }

  @Synchronized
  fun put(catalogId: String, region: String, page: Int, value: DiscoverPage) {
    entries[key(catalogId, region, page)] = Entry(value, System.currentTimeMillis())
  }
}

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
 * The chosen source is not on the user's debrid service yet — a provider accepted the magnet and
 * is fetching it onto its own servers now.
 *
 * Typed rather than a plain failure because it is not one: the source is fine and will usually
 * play in a few minutes. It also has to survive the peer-to-peer engine fallback, which reports its
 * own unrelated errors on the way to giving up and would otherwise be the message shown.
 */
class DebridDownloadingException(message: String) : IllegalStateException(message)

/**
 * One title's place, as the account keeps it.
 *
 * The shape the backend already stores and the television already writes -- this app is the one
 * that never joined in, which is why a film finished on the TV still showed as half-watched here.
 */
data class PlaybackProgressRecord(
  val entityType: String,
  val entityId: String,
  val episodeKey: String?,
  val seasonNumber: Int?,
  val episodeNumber: Int?,
  val title: String?,
  val poster: String?,
  val backdrop: String?,
  val year: String?,
  val positionSec: Double,
  val durationSec: Double,
  val progress: Double,
  val completed: Boolean,
  val unwatched: Boolean = false,
  val dismissed: Boolean = false,
  /**
   * The ids this row can also be recognised by, when SyncDek recorded them.
   *
   * `entityId` alone is whatever spelling the device that wrote the row was holding, so matching on
   * it is what let a removal made here fail to suppress the same title arriving from a provider
   * under a different id. See [mediaIdentityOf].
   */
  val tmdbId: Int? = null,
  val imdbId: String? = null,
  val updatedAt: Long,
  val lastDevice: String? = null,
  val lastPlatform: String? = null,
)

/** Why a session ended without the person asking. */
enum class SessionEndReason { EXPIRED, SUSPENDED }

/**
 * Raised when the account has been suspended.
 *
 * Distinct from an ordinary failure so a caller can tell the difference. Before this, a banned
 * account's requests came back as generic errors, the app kept its interface up, and it kept
 * firing requests that were all refused -- which reads as the app being broken rather than the
 * account being stopped, and gives the person nothing to act on.
 */
class AccountSuspendedException(message: String) : IllegalStateException(message)

class StreamDekApiClient(context: Context? = null) {
  private val appContext = context?.applicationContext
  private val clientIdentity = appContext?.let { ClientIdentityStore(it).load() }

  /**
   * The session store, so the HTTP layer can renew a token and clear a suspended session without
   * every call site having to be taught about either.
   */
  private val sessionStore: SessionStore? = appContext?.let { SessionStore(it) }

  /**
   * Told when a session ends by itself. Set once, by the app.
   *
   * A callback rather than a return value because this happens underneath sixty different call
   * sites, and threading "the session is over" back through all of them is how half of them end
   * up not handling it.
   */
  var onSessionEnded: ((SessionEndReason, String) -> Unit)? = null

  /**
   * The viewer's own content-service keys, for the device-only case.
   *
   * Held here rather than passed in per call because the attachment happens in an interceptor:
   * TMDB and MDBList requests are built in sixty different places in this file, and threading a
   * key through every one of them is how half of them end up not carrying it.
   */
  internal val serviceCredentials = appContext?.let { ServiceCredentialManager(it) }

  /**
   * The host of StreamDek's own API, resolved once.
   *
   * Only used to decide whether a request may carry a content-service key. Parsed as a URL rather
   * than matched as a string, and null when the configured base URL cannot be parsed — the safe
   * answer to "is this our host" when we cannot tell is no, and null compares equal to nothing.
   */
  private val apiHost: String? =
    runCatching { java.net.URI(BuildConfig.API_BASE_URL).host }.getOrNull()?.takeIf { it.isNotBlank() }
  private val client = OkHttpClient.Builder()
    .apply { appContext?.let { dns(StreamDekDns(it)) } }
    /**
     * Attaches a device-only content-service key to the requests that need one.
     *
     * A key the viewer chose to keep on this device is still needed by the backend, because the
     * backend is what talks to TMDB. It travels on the request that needs it, over TLS, is used
     * for that request, and is never stored server-side — which is exactly what "this device
     * only" means here, and is said in those words on the screen where the choice is made.
     *
     * Scoped tightly on purpose: only StreamDek's own API host, only the paths that spend the
     * credential. An add-on, a plugin's scraper or a poster CDN never sees a header.
     *
     * The host is compared as a host, not as a URL prefix. A prefix test would also match
     * `https://api.streamdek.net.example.com/`, and a plugin is free to return a stream URL —
     * which this same client fetches — so that is a way to be handed somebody's API key.
     */
    /**
     * Identity on every request to StreamDek's own API, including the ones built by hand.
     *
     * Most calls go through executeJson/executePut, which attach authHeaders(session). Fourteen
     * did not: the enrichment reads behind the media details page -- /tmdb/details, /tmdb/find,
     * /tmdb/search, /tmdb/home, /tmdb/catalogs and the rest -- were built as bare requests and
     * carried no token, no device id, nothing.
     *
     * That was invisible until the API gained rate limits, because an unidentified caller is
     * bucketed by address in the anonymous tier: thirty requests a minute across every device
     * behind one address, where a signed-in account gets two hundred and forty of its own. One
     * media details page fans out to several of these at once, so the About section filled in or
     * came back empty depending on what else the phone had just done. Every TMDB request from
     * this client was landing in that bucket, and 1,740 of them were refused.
     *
     * An interceptor rather than fourteen edits: the next request built by hand is covered too,
     * which is the failure mode worth designing out. It never overwrites an Authorization header
     * a caller set deliberately, and it is scoped to StreamDek's API host by host comparison for
     * the reason spelled out below -- a plugin can hand this same client a URL to fetch.
     *
     * x-user-id is deliberately not sent here. It is the insecure impersonation header, now
     * strictly opt-in and off by default on the server; the bearer token is the credential.
     */
    .addInterceptor { chain ->
      val request = chain.request()
      if (apiHost == null
        || !request.url.host.equals(apiHost, ignoreCase = true)
        || !request.header("Authorization").isNullOrBlank()
      ) {
        chain.proceed(request)
      } else {
        val builder = request.newBuilder()
        sessionStore?.load()?.let { stored ->
          builder.header("Authorization", "Bearer ${stored.token}")
        }
        clientIdentity?.let { identity ->
          builder.header("x-client-session-id", identity.sessionId)
          builder.header("x-client-device-id", identity.deviceId)
          builder.header("x-client-name", "StreamDek Mobile")
          builder.header("x-client-platform", "android")
          builder.header("x-app-version", BuildConfig.VERSION_NAME)
        }
        chain.proceed(builder.build())
      }
    }
    .addInterceptor { chain ->
      val request = chain.request()
      val manager = serviceCredentials
      if (manager == null || apiHost == null || !request.url.host.equals(apiHost, ignoreCase = true)) {
        chain.proceed(request)
      } else {
        // Matched without the version prefix so these stay written as the domain paths they
        // describe. removePrefix is a no-op on a path that does not carry it, so a request built
        // against a bare prefix still gets its credential.
        val path = request.url.encodedPath.removePrefix(API_PATH_PREFIX)
        val builder = request.newBuilder()
        if (path.startsWith("/tmdb/") || path == "/tmdb" || path.startsWith("/addons/resolve-id/")) {
          manager.requestKey(ContentService.Tmdb)?.let { builder.header("x-tmdb-api-key", it) }
        }
        if (path.startsWith("/mdblist/") || path.startsWith("/sync/")) {
          manager.requestKey(ContentService.Mdblist)?.let { builder.header("x-mdblist-api-key", it) }
        }
        chain.proceed(builder.build())
      }
    }
    // Every request the app makes to StreamDek's own API passes through here. Only failures are
    // written, and only the method, path and status: enough to tell a refusal from the API apart
    // from a gateway that never reached it, which is otherwise invisible from a device — the
    // screen says the same "something went wrong" either way.
    .addInterceptor { chain ->
      val request = chain.request()
      val started = System.currentTimeMillis()
      try {
        chain.proceed(request).also { response ->
          if (!response.isSuccessful) {
            android.util.Log.w(
              "StreamDekApi",
              "${request.method} ${request.url.encodedPath} -> ${response.code} in ${System.currentTimeMillis() - started}ms" +
                (response.header("server")?.let { " (via $it)" } ?: ""),
            )
          }
        }
      } catch (error: Throwable) {
        android.util.Log.w(
          "StreamDekApi",
          "${request.method} ${request.url.encodedPath} -> ${error.javaClass.simpleName}: ${error.message} after ${System.currentTimeMillis() - started}ms",
        )
        throw error
      }
    }
    .build()
  /**
   * Credential checks can legitimately outlive OkHttp's ten-second default because the backend
   * gives MDBList up to ten seconds to answer before it can return a useful service-unavailable
   * result. A separate client keeps ordinary app requests snappy while allowing that considered
   * response to reach the settings screen instead of racing the phone's read timeout.
   */
  private val credentialValidationClient = client.newBuilder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(25, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()
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
  /**
   * The canonical prefix every StreamDek API path now carries.
   *
   * The bare prefixes this app grew up on -- /tmdb, /sync, /auth -- still work: the backend
   * rewrites them onto the canonical paths and answers with a Deprecation header. They are
   * aliases kept so that an old build keeps working, not a second API, and every request that
   * arrives on one is recorded as deprecated traffic whose only purpose is to say when the alias
   * can finally be removed. This app should not be the reason it never can.
   *
   * Applied in one place, on the base URL, rather than at sixty call sites: a prefix threaded
   * through by hand is one somebody forgets, and the forgotten one is invisible because it still
   * works.
   */
  val apiBaseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/') + API_PATH_PREFIX

  /**
   * Region used for theatrical listings and watch-provider catalogs. Taken from the device, which
   * is as close to "where the viewer is" as the app knows; the backend falls back to US for a
   * service that does not operate here rather than handing back an empty row.
   */
  private val catalogRegion: String = runCatching {
    java.util.Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase()
  }.getOrNull() ?: "US"
  private val catalogPageCache = CatalogPageCache()
  @Volatile private var cachedCatalogManifest: CatalogManifestCacheEntry? = null

  // The four original built-in rows, by id, as title-and-path pairs. Only reached when the
  // backend has no catalog registry: normally every default row comes from /tmdb/home and pages
  // through /tmdb/catalog/:id. "streaming_networks" is a fixed list of service tiles rather than
  // a paginable catalog, so it appears here for the home fallback but never for paging.
  private val legacyBuiltInSections: Map<String, Pair<String, String>> = mapOf(
    "trending_movies" to ("Trending Movies" to "/tmdb/trending/movie"),
    "trending_series" to ("Trending Series" to "/tmdb/trending/tv"),
    "new_movies" to ("New Movies" to "/tmdb/discover?type=movie&sort_by=primary_release_date.desc"),
    "new_series" to ("New Series" to "/tmdb/discover?type=tv&sort_by=first_air_date.desc"),
    "streaming_networks" to ("Streaming Networks" to "/tmdb/networks"),
  )

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
    return AuthSession(existing.token, user, refreshToken = existing.refreshToken).also(sessionStore::save)
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

  /**
   * The default catalogs the backend offers, in the order it wants them shown.
   *
   * Held in memory for the session's working life: the registry changes on backend deploys, not
   * minute to minute, and every home load and row-management screen would otherwise re-ask for it.
   */
  /**
   * Reads the platform's content policy and hands it to [AdultContentFilter].
   *
   * Needs no session: the block applies before anyone signs in. A failure here deliberately
   * leaves the filter on rather than reporting an error, because the safe state and the
   * unknown state are the same one.
   */
  suspend fun refreshContentPolicy() = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(Request.Builder().url("$apiBaseUrl/public/content-policy").build())
      ensureOk(response, "Failed to load content policy")
      val terms = response.json.optJSONArray("terms")
      AdultContentFilter.applyPolicy(
        blockAdult = response.json.optBoolean("blockAdult", true),
        terms = buildList {
          if (terms != null) for (index in 0 until terms.length()) {
            terms.optString(index).takeIf { it.isNotBlank() }?.let(::add)
          }
        },
      )
    }.onFailure { AdultContentFilter.applyPolicy(null, null) }
  }

  suspend fun fetchCatalogManifest(): Result<List<CatalogDefinition>> = withContext(Dispatchers.IO) {
    cachedCatalogManifest?.takeIf { it.isFresh() }?.let { return@withContext Result.success(it.definitions) }
    runCatching {
      val response = execute(Request.Builder().url("$apiBaseUrl/tmdb/catalogs?region=${encodeQuery(catalogRegion)}").build())
      ensureOk(response, "Failed to load catalogs")
      parseCatalogManifest(response.json).also { definitions ->
        if (definitions.isNotEmpty()) cachedCatalogManifest = CatalogManifestCacheEntry(definitions)
      }
    }
  }

  /**
   * Home previews for the default catalogs, plus whatever the installed add-ons contribute.
   *
   * [catalogIds] limits the request to the rows the viewer actually has switched on, so a trimmed
   * home screen costs less rather than the same — an empty list skips the default catalogs
   * altogether, null asks for the backend's full default set.
   *
   * Every default row arrives in one response: the backend fetches them in parallel behind its
   * own cache, drops rows that came back empty, and isolates failures to the row that failed —
   * one dead catalog costs its own row and nothing else. If that endpoint is unavailable — an
   * older backend, say — the original five rows are fetched the way they always were, so the home
   * screen degrades rather than disappears.
   */
  /**
   * @param onDefaults the built-in catalog rows, handed over the moment they arrive rather than
   *   when the whole screen is ready. The add-on fan-out is the slow half by a wide margin — it
   *   was measured at 15.6 s against 0.9 s for the defaults on the same load — and there is no
   *   dependency between the two, so making the viewer wait for the second to see the first was
   *   costing the entire difference.
   */
  suspend fun fetchHomeSections(
    session: AuthSession?,
    addons: List<InstalledAddon> = emptyList(),
    profileId: String? = null,
    catalogIds: List<String>? = null,
    onDefaults: suspend (List<MediaSection>) -> Unit = {},
  ): Result<List<MediaSection>> = withContext(Dispatchers.IO) {
    runCatching {
      val perf = Perf.span("homeSections")
      val sections = supervisorScope {
        val defaults = async {
          fetchDefaultCatalogSections(catalogIds).also {
            perf.mark("defaults", "count=${it.size}")
            // Never let a failure in the progressive hand-off take the whole load down with it:
            // the complete result below is still returned either way.
            runCatching { onDefaults(it) }
          }
        }
        val addonSections = async {
          fetchAddonHomeSections(session, addons, profileId).also { perf.mark("addons", "count=${it.size}") }
        }
        defaults.await() + addonSections.await()
      }
      perf.end("ok", "total=${sections.size}")
      sections
    }
  }

  private suspend fun fetchDefaultCatalogSections(catalogIds: List<String>?): List<MediaSection> {
    val wanted = catalogIds?.filter { it.isNotBlank() }
    if (wanted != null && wanted.isEmpty()) return emptyList()
    val idsParam = wanted?.let { "&ids=" + encodeQuery(it.joinToString(",")) }.orEmpty()
    val batched = runCatching {
      val response = execute(Request.Builder().url("$apiBaseUrl/tmdb/home?region=${encodeQuery(catalogRegion)}$idsParam").build())
      ensureOk(response, "Failed to load home catalogs")
      parseCatalogHomeSections(response.json)
    }.getOrElse { error ->
      android.util.Log.w("StreamDekCatalogs", "batched home catalogs unavailable, falling back to legacy rows", error)
      emptyList()
    }
    if (batched.isNotEmpty()) return batched
    return legacyHomeSections()
  }

  /** The original five rows, kept as the safety net for a backend without the catalog registry. */
  private suspend fun legacyHomeSections(): List<MediaSection> = supervisorScope {
    legacyBuiltInSections.map { (id, row) ->
      async {
        runCatching { MediaSection(id, row.first, fetchMediaList(row.second), nextPage = 2) }
          .getOrElse { error ->
            android.util.Log.w("StreamDekCatalogs", "legacy home row $id failed", error)
            null
          }
      }
    }.awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
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
      val params = mutableListOf(
        "page=" + encodeQuery(page.toString()),
        "sort=" + encodeQuery(sort),
        "region=" + encodeQuery(catalogRegion),
      )
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
            .filter { item ->
              normalizeMediaType(item.type) == normalizedType &&
                normalizeTitle(item.title) == normalizeTitle(title) &&
                (fallbackYear == null || item.year == null || item.year.take(4) == fallbackYear.take(4))
            }
            .sortedWith(
              compareByDescending<MediaItem> { it.year != null && fallbackYear != null && it.year.take(4) == fallbackYear.take(4) }
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

  /**
   * MDBList ratings for one title.
   *
   * Goes through StreamDek rather than straight to MDBList, and the reason is the storage choice:
   * a key kept on this device travels on the request (see the interceptor above), a key saved to
   * the account is resolved server-side, and neither case needs a second code path here. The
   * merging below is unchanged -- only where the answers come from moved.
   *
   * [session] is passed so an account-saved key can be found. A signed-out viewer with a device
   * key still gets ratings; one with neither gets an empty list rather than an error.
   */
  suspend fun fetchMdblistRatings(
    session: AuthSession?,
    type: String,
    tmdbId: String,
    imdbId: String?,
    providers: List<String> = listOf("imdb", "tmdb", "tomatoes", "metacritic", "trakt", "letterboxd", "audience"),
  ): Result<List<ExternalRating>> = withContext(Dispatchers.IO) {
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
              .url("$apiBaseUrl/mdblist/rating/$mediaType/${encodeQuery(provider)}")
              .post(payload.toString().toRequestBody(jsonMediaType))
              .headers(authHeaders(session, includeContentType = false))
              .addHeader("Accept", "application/json")
              .build(),
          )
          if (!response.ok) {
            noteMdblistRefusal(response)
            return@mapNotNull null
          }
          val rating = parseMdblistProviderRating(response.json, allowPercent = providerAllowsPercent(provider)) ?: return@mapNotNull null
          ExternalRating(provider = normalizeMdblistProviderId(provider), rating = rating)
        }.forEach { rating ->
          mergedRatings.putIfAbsent(rating.provider.lowercase(), rating)
        }
      }

      val candidates = buildList {
        if (tmdbId.isNotBlank()) add("$apiBaseUrl/mdblist/lookup/tmdb/$mediaType/${encodeQuery(tmdbId)}")
        if (!normalizedImdbId.isNullOrBlank()) add("$apiBaseUrl/mdblist/lookup/imdb/$mediaType/${encodeQuery(normalizedImdbId)}")
      }
      for (url in candidates) {
        val response = execute(
          Request.Builder().url(url).headers(authHeaders(session, includeContentType = false)).build(),
        )
        if (response.ok) {
          val ratings = parseExternalRatings(response.json)
          ratings.forEach { rating ->
            mergedRatings.putIfAbsent(normalizeMdblistProviderId(rating.provider).lowercase(), rating)
          }
        } else {
          noteMdblistRefusal(response)
        }
      }
      mergedRatings.values.toList()
    }
  }

  /**
   * Marks a device-held MDBList key that the service has refused.
   *
   * Only an explicit refusal counts. A 502 means MDBList was unreachable, which says nothing
   * about the key, and treating the two alike is how a working key gets flagged as broken
   * during an outage the viewer can do nothing about.
   */
  private fun noteMdblistRefusal(response: JsonResponse) {
    if (response.statusCode != 401) return
    if (!response.json.optBoolean("credentialRejected", false)) return
    serviceCredentials?.markDeviceKeyRejected(ContentService.Mdblist)
  }

  suspend fun fetchTraktComments(session: AuthSession?, type: String, tmdbId: String, imdbId: String?): Result<List<TraktComment>> = withContext(Dispatchers.IO) {
    runCatching {
      val canonicalType = if (type == "tv" || type == "series") "tv" else "movie"
      val url = "$apiBaseUrl/trakt/comments/$canonicalType/${encodeQuery(tmdbId)}"
      val response = execute(Request.Builder().url(url).headers(authHeaders(session, includeContentType = false)).build())
      ensureOk(response, "Failed to load Trakt comments")
      parseTraktComments(response.json)
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

  suspend fun setAddonFavourite(session: AuthSession?, addonId: String, favourite: Boolean, profileId: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = executeJson(
          "/addons/favourite",
          JSONObject().put("id", addonId).put("favourite", favourite),
          session = session,
          profileId = profileId,
        )
        ensureOk(response, "Failed to update favourite add-on")
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
        // Fastify rejects an empty request advertised as JSON. This DELETE has no payload, so it
        // must not inherit the Content-Type header used by the write helpers.
        .headers(authHeaders(session, includeContentType = false, profileId = profileId))
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
              }.getOrDefault(emptyList()).take(ADDON_CATALOG_MAX_ITEMS)
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
        if (isAdultCatalogEntry(item)) continue
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
        if (isAdultCatalogEntry(item)) continue
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

  /**
   * Where the viewer got to, shared with every other device on the account.
   *
   * Scoped to the viewing profile, not the account: the profile travels in the `x-profile-id`
   * header and the server keys the row on it, so two people sharing an account keep their own
   * resume points.
   *
   * Sent alongside the local save rather than instead of it. The local store stays the source of
   * truth for what is on screen -- it answers instantly and works offline -- and this is the copy
   * the other devices read.
   */
  suspend fun putPlaybackProgress(
    session: AuthSession,
    profileId: String?,
    entityType: String,
    entityId: String,
    episodeKey: String?,
    positionSec: Double,
    durationSec: Double,
    title: String? = null,
    poster: String? = null,
    backdrop: String? = null,
    year: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    completed: Boolean = false,
    unwatched: Boolean = false,
    dismissed: Boolean = false,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val metadata = JSONObject()
        .put("title", title)
        // posterUrl/backdropUrl are the names the account stores these under, and the names the
        // television has always sent. This app sent poster/backdrop, which nothing read, so every
        // position it wrote arrived with no artwork and came back as a blank card -- on Home here,
        // on the television, and on the portal. Both spellings go up: the server now reads either,
        // but an older server reads only these.
        .put("posterUrl", poster)
        .put("backdropUrl", backdrop)
        .put("poster", poster)
        .put("backdrop", backdrop)
        .put("year", year)
        .put("seasonNumber", seasonNumber)
        .put("episodeNumber", episodeNumber)
        // Keep both representations on the wire. Current SyncDek stores the scalar fields for
        // indexing and returns this nested shape to playback clients; older backends may only
        // preserve metadata, so sending both keeps Mobile -> TV episode identity lossless.
        .put(
          "episode",
          if (seasonNumber != null && episodeNumber != null) {
            JSONObject().put("seasonNumber", seasonNumber).put("episodeNumber", episodeNumber)
          } else null,
        )
      val body = JSONObject()
        .put("entityType", if (entityType.equals("movie", true)) "movie" else "tv")
        .put("entityId", entityId)
        .put("episodeKey", episodeKey)
        .put("positionSec", positionSec)
        .put("durationSec", durationSec)
        .put("updatedAt", java.time.Instant.now().toString())
        .put("lastDevice", clientIdentity?.deviceName ?: "StreamDek Mobile")
        .put("lastPlatform", "mobile")
        // Said outright rather than inferred from the position: "mark as watched" is pressed on a
        // card, so there is no position and usually no runtime for the server to work it out from.
        .put("completed", completed)
        .put("unwatched", unwatched)
        .put("dismissed", dismissed)
        .put("metadata", metadata)
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/sync/progress")
          .headers(authHeaders(session, profileId = profileId))
          .put(body.toString().toRequestBody(jsonMediaType))
          .build(),
      )
      ensureOk(response, "Failed to save playback progress")
      Unit
    }
  }

  /**
   * Removes one title from Continue Watching. The canonical operation, shared with the television.
   *
   * This used to be a dismissed progress write assembled here, and the television assembled its own
   * — subtly different — version of the same thing. The server owns the whole lifecycle now:
   * recording the intent under an identity that every source can be matched against, dropping the
   * provider caches that would otherwise replay the title for the next few minutes, and suppressing
   * the provider row on every device from then on. A removal made here and one made on the
   * television are the same operation.
   *
   * The ids matter. A removal recorded only against the spelling this card happened to carry could
   * not be matched to the same title arriving from a provider under a different one, which is why
   * removed films came back.
   */
  suspend fun removeFromContinueWatching(
    session: AuthSession?,
    profileId: String?,
    entityType: String,
    entityId: String,
    episodeKey: String?,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    tmdbId: Int? = null,
    imdbId: String? = null,
    title: String? = null,
    poster: String? = null,
    backdrop: String? = null,
    year: String? = null,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val body = JSONObject()
        .put("entityType", if (entityType.equals("movie", true)) "movie" else "tv")
        .put("entityId", entityId)
        .put("episodeKey", episodeKey)
        .put("seasonNumber", seasonNumber)
        .put("episodeNumber", episodeNumber)
        .put("tmdbId", tmdbId)
        .put("imdbId", imdbId)
        .put("title", title)
        .put("posterUrl", poster)
        .put("backdropUrl", backdrop)
        .put("year", year)
        .put("removedAt", java.time.Instant.now().toString())
        .put("lastDevice", clientIdentity?.deviceName ?: "StreamDek Mobile")
        .put("lastPlatform", "mobile")
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/sync/continue-watching/remove")
          .headers(authHeaders(session, profileId = profileId))
          .post(body.toString().toRequestBody(jsonMediaType))
          .build(),
      )
      ensureOk(response, "Failed to remove this title from Continue Watching")
      Unit
    }
  }

  /**
   * Writes one logical watched-state change as a single request.
   *
   * A season action can contain many episodes. Sending those as unrelated requests allowed a
   * transient failure to leave SyncDek with only part of the season while the optimistic local UI
   * showed all of it as watched. The endpoint is idempotent per episode key, so callers may retry
   * the complete batch safely.
   */
  suspend fun putPlaybackProgressBatch(
    session: AuthSession,
    profileId: String?,
    records: List<PlaybackProgressRecord>,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val items = JSONArray()
      records.forEach { record ->
        val metadata = JSONObject()
          .put("title", record.title)
          .put("posterUrl", record.poster)
          .put("backdropUrl", record.backdrop)
          .put("poster", record.poster)
          .put("backdrop", record.backdrop)
          .put("year", record.year)
          .put("seasonNumber", record.seasonNumber)
          .put("episodeNumber", record.episodeNumber)
          .put(
            "episode",
            if (record.seasonNumber != null && record.episodeNumber != null) {
              JSONObject().put("seasonNumber", record.seasonNumber).put("episodeNumber", record.episodeNumber)
            } else null,
          )
        items.put(
          JSONObject()
            .put("entityType", if (record.entityType.equals("movie", true)) "movie" else "tv")
            .put("entityId", record.entityId)
            .put("episodeKey", record.episodeKey)
            .put("positionSec", record.positionSec)
            .put("durationSec", record.durationSec)
            .put("updatedAt", java.time.Instant.ofEpochMilli(record.updatedAt).toString())
            .put("lastDevice", clientIdentity?.deviceName ?: record.lastDevice ?: "StreamDek Mobile")
            .put("lastPlatform", record.lastPlatform ?: "mobile")
            .put("completed", record.completed)
            .put("unwatched", record.unwatched)
            .put("dismissed", record.dismissed)
            .put("metadata", metadata),
        )
      }
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/sync/progress/batch")
          .headers(authHeaders(session, profileId = profileId))
          .post(JSONObject().put("items", items).toString().toRequestBody(jsonMediaType))
          .build(),
      )
      ensureOk(response, "Failed to save season watched state")
      Unit
    }
  }

  /** Everything the account knows about where things were left, newest first. */
  suspend fun fetchPlaybackProgress(
    session: AuthSession,
    profileId: String?,
    limit: Int = 100,
    entityType: String? = null,
    entityId: String? = null,
  ): Result<List<PlaybackProgressRecord>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val filters = buildString {
          append("limit=").append(limit)
          entityType?.takeIf { it.isNotBlank() }?.let { append("&entityType=").append(encodeQuery(it)) }
          entityId?.takeIf { it.isNotBlank() }?.let { append("&entityId=").append(encodeQuery(it)) }
        }
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/sync/progress?$filters")
            .headers(authHeaders(session, includeContentType = false, profileId = profileId))
            .build(),
        )
        ensureOk(response, "Failed to load playback progress")
        val results = response.json.optJSONArray("results") ?: JSONArray()
        buildList {
          for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val entityId = item.optString("entityId").takeIf { it.isNotBlank() } ?: continue
            val metadata = item.optJSONObject("metadata") ?: JSONObject()
            add(
              PlaybackProgressRecord(
                entityType = item.optStringOrNull("entityType") ?: "movie",
                entityId = entityId,
                episodeKey = item.optStringOrNull("episodeKey"),
                seasonNumber = item.opt("seasonNumber").asOptionalInt() ?: metadata.opt("seasonNumber").asOptionalInt(),
                episodeNumber = item.opt("episodeNumber").asOptionalInt() ?: metadata.opt("episodeNumber").asOptionalInt(),
                title = item.optStringOrNull("title") ?: metadata.optStringOrNull("title"),
                poster = item.optStringOrNull("poster") ?: metadata.optStringOrNull("poster"),
                backdrop = item.optStringOrNull("backdrop") ?: metadata.optStringOrNull("backdrop"),
                year = item.optStringOrNull("year") ?: metadata.optStringOrNull("year"),
                positionSec = item.optDouble("positionSec", 0.0),
                durationSec = item.optDouble("durationSec", 0.0),
                progress = item.optDouble("progress", 0.0),
                completed = item.optString("status").equals("completed", true),
                unwatched = item.optString("status").equals("unwatched", true),
                dismissed = item.optString("status").equals("dismissed", true),
                // Read so a dismissal can be matched against a provider row that spells the same
                // title differently. Without these the record is only findable by `entityId`.
                tmdbId = item.opt("tmdbId")?.toString()?.toIntOrNull(),
                imdbId = item.opt("imdbId")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                // Compared against the local entry's own stamp when the two disagree, so the
                // newer of them wins rather than whichever happened to be read last.
                updatedAt = parseIsoInstantMillis(item.optString("updatedAt")),
                lastDevice = item.optStringOrNull("lastDevice"),
                lastPlatform = item.optStringOrNull("lastPlatform"),
              ),
            )
          }
        }
      }
    }

  /**
   * Empties this profile's Continue Watching on the account.
   *
   * Clearing used to be a local gesture: the phone hid its rows and the server kept them, so the
   * television still listed everything and the phone's own store filled back up on the next sync.
   */
  suspend fun clearPlaybackProgress(session: AuthSession, profileId: String?): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/sync/progress")
            .headers(authHeaders(session, includeContentType = false, profileId = profileId))
            .delete()
            .build(),
        )
        ensureOk(response, "Failed to clear playback progress")
        Unit
      }
    }

  /** Forgets one title's place everywhere, for "remove from Continue Watching" and "start over". */
  suspend fun deletePlaybackProgress(
    session: AuthSession,
    profileId: String?,
    entityType: String,
    entityId: String,
    episodeKey: String? = null,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val type = if (entityType.equals("movie", true)) "movie" else "tv"
      val query = episodeKey?.takeIf { it.isNotBlank() }?.let { "?episodeKey=${encodeQuery(it)}" }.orEmpty()
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/sync/progress/$type/${encodeQuery(entityId)}$query")
          .headers(authHeaders(session, includeContentType = false, profileId = profileId))
          .delete()
          .build(),
      )
      ensureOk(response, "Failed to clear playback progress")
      Unit
    }
  }

  /**
   * Just the stamp on the plugin document.
   *
   * Small enough to ask for on a timer, which the document itself is not: it carries every source
   * and every settings schema. See startWatchingProfilePlugins.
   */
  suspend fun fetchProfilePluginsVersion(session: AuthSession, profileId: String): Result<Long> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/profiles/${encodeQuery(profileId)}/plugins/version")
          .headers(authHeaders(session, includeContentType = false, profileId = profileId))
          .build(),
      )
      ensureOk(response, "Failed to check plugin collections")
      response.json.optLong("updatedAt", 0L)
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
        .put("homeCardTextMode", preferences.homeCardTextMode)
        .put("networkCardStyle", preferences.networkCardStyle)
        .put("liveLandscapeCards", preferences.liveLandscapeCards)
        .put("liveFavouriteDrawerCards", preferences.liveFavouriteDrawerCards)
        .put("liveCategoriesEnabled", preferences.liveCategoriesEnabled)
        .put("primarySyncService", preferences.primarySyncService)
        .put("showHeroSynopsis", preferences.showHeroSynopsis)
        .put("vividAmbient", preferences.vividAmbient)
        .put("homeBackgroundMode", preferences.homeBackgroundMode)
        .put("ambientTintPercent", preferences.ambientTintPercent)
        .put("defaultAppCatalogsEnabled", preferences.defaultAppCatalogsEnabled)
        .put("homeCatalogRows", preferences.homeCatalogRowsJson?.let(::JSONArray))
      val detail = JSONObject()
        .put("seasonTabStyle", preferences.seasonTabStyle)
        .put("episodeLayout", preferences.episodeLayout)
        // Trailer playback choices are this device's own — see [PLATFORM_PREFERENCES_KEY]. They are
        // deliberately no longer written to the shared section: a television's idea of when a
        // trailer should start has nothing to do with a phone's, and while both wrote here the last
        // client to save quietly changed the other one's settings.
        .put("trailerCacheClearHours", preferences.trailerCacheClearHours)
        .put("detailBackgroundMode", preferences.detailBackgroundMode)
        // Beside the mode it belongs to. The home screen's copy of this stays under `home`; the
        // two pages carry their own strength now, so one of them had to move out of that section.
        .put("ambientTintPercent", preferences.detailAmbientTintPercent)
        .put("ratingsEnabled", preferences.ratingsEnabled)
        .put("externalRatingsEnabled", preferences.externalRatingsEnabled)
        .put("enabledRatingProviders", preferences.enabledRatingProviders?.let(::JSONArray))
        // The MDBList key is deliberately not written here any more. It was a secret riding on
        // an ordinary settings document; it lives in the encrypted credential store now. The key
        // is omitted rather than sent as an empty string, because a blank would overwrite a
        // legacy value the backend has not migrated out of this document yet -- and that
        // migration is the only thing that knows how to keep it.
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
        .put("autoSkipIntroEnabled", preferences.autoSkipIntroEnabled)
        .put("autoSkipRecapEnabled", preferences.autoSkipRecapEnabled)
        .put("autoSkipEndingEnabled", preferences.autoSkipEndingEnabled)
        .put("introdbApiKey", preferences.introdbApiKey)
        .put("autoPlayNextEpisodeEnabled", preferences.autoPlayNextEpisode)
        .put("autoplayNextEpisode", preferences.autoPlayNextEpisode)
        .put("preferBingeGroupNextEpisode", preferences.preferBingeGroup)
        .put("autoLoadSubtitles", preferences.autoLoadSubtitles)
        .put("secondaryAudioLanguage", preferences.secondaryAudioLanguage)
        .put("preferredSubtitleLanguage", preferences.preferredSubtitleLanguage)
        .put("secondarySubtitleLanguage", preferences.secondarySubtitleLanguage)
        .put("useForcedSubtitles", preferences.useForcedSubtitles)
        .put("showOnlyPreferredSubtitleLanguages", preferences.showOnlyPreferredSubtitleLanguages)
        .put("addonSubtitleLoading", preferences.addonSubtitleLoading)
        .put("nextEpisodeThresholdMode", preferences.nextEpisodeThresholdMode)
        .put("nextEpisodeThresholdPercent", preferences.nextEpisodeThresholdPercent)
        .put("nextEpisodeThresholdMinutes", preferences.nextEpisodeThresholdMinutes)
        .put("endOfPlaybackRecommendationsEnabled", preferences.endOfPlaybackRecommendationsEnabled)
        .put("recommendationTiming", preferences.recommendationTiming)
        .put("recommendationItemCount", preferences.recommendationItemCount)
        .put("timingProvider", preferences.timingProvider)
        .put("timingProviderFallbackEnabled", preferences.timingProviderFallbackEnabled)
      val streams = JSONObject()
        .put("showStreamsList", preferences.showStreamsList)
        .put("rememberLastSource", preferences.rememberLastSource)
        .put("favoriteSourceKeys", preferences.favoriteSourceKeys?.let(::JSONArray))
        .put("blurUnwatchedEpisodes", preferences.blurUnwatchedEpisodes)
        .put("fusionBadgesEnabled", preferences.fusionBadgesEnabled)
        .put("streamDekFormattingEnabled", preferences.streamDekFormattingEnabled)
        .put("showSizeBadges", preferences.showSizeBadges)
        .put("badgePosition", preferences.badgePosition)
        .put("fusionBadgeUrls", preferences.fusionBadgeUrls?.let(::JSONArray))
        .put("activeFusionBadgeUrl", preferences.activeFusionBadgeUrl)
      val updates = JSONObject().put("autoUpdateChecksEnabled", preferences.autoUpdateChecksEnabled)
      // This client's own settings, kept apart from the shared ones. The backend defines the
      // convention (settingsSchema.ts: perPlatformSettingKeys / PER_PLATFORM_PREFERENCES_KEY) so a
      // phone, a television and the portal all agree on where to look.
      val platformPreferences = JSONObject()
        .put(
          PLATFORM_PREFERENCES_PLATFORM,
          JSONObject()
            .put("heroTrailerAutoplay", preferences.heroTrailerAutoplay)
            .put("heroTrailerResolution", preferences.heroTrailerResolution)
            .put("heroTrailerDelaySeconds", preferences.heroTrailerDelaySeconds),
        )
      val payload = JSONObject()
        .put("app", app)
        .put("home", home)
        .put("detail", detail)
        .put("playback", playback)
        .put("streams", streams)
        .put("updates", updates)
        .put(PLATFORM_PREFERENCES_KEY, platformPreferences)
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
          .put("autoSkipIntroEnabled", preferences.autoSkipIntroEnabled)
          .put("autoSkipRecapEnabled", preferences.autoSkipRecapEnabled)
          .put("autoSkipEndingEnabled", preferences.autoSkipEndingEnabled)
          .put("autoPlayNextEpisodeEnabled", preferences.autoPlayNextEpisode)
          .put("autoplayNextEpisode", preferences.autoPlayNextEpisode)
          .put("preferBingeGroupNextEpisode", preferences.preferBingeGroup)
          .put("autoLoadSubtitles", preferences.autoLoadSubtitles)
          .put("nextEpisodeThresholdMode", preferences.nextEpisodeThresholdMode)
          .put("nextEpisodeThresholdPercent", preferences.nextEpisodeThresholdPercent)
          .put("nextEpisodeThresholdMinutes", preferences.nextEpisodeThresholdMinutes)
          .put("endOfPlaybackRecommendationsEnabled", preferences.endOfPlaybackRecommendationsEnabled)
          .put("recommendationTiming", preferences.recommendationTiming)
          .put("recommendationItemCount", preferences.recommendationItemCount)
          .put("timingProvider", preferences.timingProvider)
          .put("timingProviderFallbackEnabled", preferences.timingProviderFallbackEnabled)
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
      // Settings this client holds for itself. Anything missing here falls back to the shared
      // section, so an account configured before trailer settings were split keeps what it had.
      val platform = accountPreferences.optJSONObject(PLATFORM_PREFERENCES_KEY)
        ?.optJSONObject(PLATFORM_PREFERENCES_PLATFORM)
        ?: JSONObject()
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
        homeCardTextMode = optionalString(home, "homeCardTextMode"),
        networkCardStyle = optionalString(home, "networkCardStyle"),
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
        episodeLayout = optionalString(detail, "episodeLayout"),
        heroTrailerAutoplay = optionalBoolean(platform, "heroTrailerAutoplay") ?: optionalBoolean(detail, "heroTrailerAutoplay"),
        trailerCacheClearHours = optionalInt(detail, "trailerCacheClearHours"),
        detailBackgroundMode = optionalString(detail, "detailBackgroundMode"),
        // Falls back to the home value, which is where the single shared setting used to live, so
        // a profile written by an older client arrives with both pages on the strength it chose.
        detailAmbientTintPercent = optionalInt(detail, "ambientTintPercent") ?: optionalInt(home, "ambientTintPercent"),
        homeBackgroundMode = optionalString(home, "homeBackgroundMode"),
        secondaryAudioLanguage = optionalString(playback, "secondaryAudioLanguage"),
        preferredSubtitleLanguage = optionalString(playback, "preferredSubtitleLanguage"),
        secondarySubtitleLanguage = optionalString(playback, "secondarySubtitleLanguage"),
        useForcedSubtitles = optionalBoolean(playback, "useForcedSubtitles"),
        showOnlyPreferredSubtitleLanguages = optionalBoolean(playback, "showOnlyPreferredSubtitleLanguages"),
        addonSubtitleLoading = optionalString(playback, "addonSubtitleLoading"),
        heroTrailerResolution = optionalInt(platform, "heroTrailerResolution") ?: optionalInt(detail, "heroTrailerResolution"),
        heroTrailerDelaySeconds = optionalInt(platform, "heroTrailerDelaySeconds") ?: optionalInt(detail, "heroTrailerDelaySeconds"),
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
        autoSkipIntroEnabled = optionalBoolean(playback, "autoSkipIntroEnabled"),
        autoSkipRecapEnabled = optionalBoolean(playback, "autoSkipRecapEnabled"),
        autoSkipEndingEnabled = optionalBoolean(playback, "autoSkipEndingEnabled"),
        autoPlayNextEpisode = optionalBoolean(playback, "autoPlayNextEpisodeEnabled") ?: optionalBoolean(playback, "autoplayNextEpisode"),
        preferBingeGroup = optionalBoolean(playback, "preferBingeGroupNextEpisode"),
        autoLoadSubtitles = optionalBoolean(playback, "autoLoadSubtitles"),
        nextEpisodeThresholdMode = optionalString(playback, "nextEpisodeThresholdMode"),
        nextEpisodeThresholdPercent = optionalInt(playback, "nextEpisodeThresholdPercent"),
        nextEpisodeThresholdMinutes = optionalInt(playback, "nextEpisodeThresholdMinutes"),
        endOfPlaybackRecommendationsEnabled = optionalBoolean(playback, "endOfPlaybackRecommendationsEnabled"),
        recommendationTiming = optionalString(playback, "recommendationTiming"),
        recommendationItemCount = optionalInt(playback, "recommendationItemCount"),
        timingProvider = optionalString(playback, "timingProvider"),
        timingProviderFallbackEnabled = optionalBoolean(playback, "timingProviderFallbackEnabled"),
        showStreamsList = optionalBoolean(streams, "showStreamsList"),
        rememberLastSource = optionalBoolean(streams, "rememberLastSource"),
        favoriteSourceKeys = optionalStringList(streams, "favoriteSourceKeys"),
        blurUnwatchedEpisodes = optionalBoolean(detail, "blurUnwatchedEpisodes") ?: optionalBoolean(streams, "blurUnwatchedEpisodes"),
        fusionBadgesEnabled = optionalBoolean(streams, "fusionBadgesEnabled"),
        streamDekFormattingEnabled = optionalBoolean(streams, "streamDekFormattingEnabled"),
        showSizeBadges = optionalBoolean(streams, "showSizeBadges"),
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

  /**
   * Posts a batch of client funnel events.
   *
   * Returns whether the backend accepted them so the caller can requeue on a transient failure.
   * Deliberately never throws: telemetry is not allowed to surface an error to a caller that is
   * in the middle of doing something the user asked for.
   */
  suspend fun sendTelemetry(session: AuthSession?, events: JSONArray): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/telemetry/events")
            .post(JSONObject().put("events", events).toString().toRequestBody(jsonMediaType))
            .headers(authHeaders(session))
            .build(),
        )
        response.ok
      }.getOrDefault(false)
    }

  suspend fun fetchStreams(
    session: AuthSession?,
    type: String,
    videoId: String,
    profileId: String? = null,
    correlationId: String? = null,
  ): Result<List<AddonStream>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val headers = authHeaders(session, includeContentType = false, profileId = profileId)
          // Joins this device's playback outcome to the add-on calls and debrid resolves the
          // backend makes because of it. Without it the two halves of the funnel are separate
          // piles of events that cannot be lined up.
          .let { base ->
            if (correlationId.isNullOrBlank()) base
            else base.newBuilder().add("x-correlation-id", correlationId).build()
          }

        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/addons/streams/$type/${encodeQuery(videoId)}")
            .headers(headers)
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
   * Only hashes and release names leave the device and no add-on is involved, so this works the
   * same whether the streams themselves came from StreamDek's servers or straight from an add-on.
   *
   * [releaseNames] maps a hash to what that release is called. Optional, and only one provider
   * reads it: Deepbrid publishes no info-hash anywhere in its API, so a hash alone is a question
   * it cannot answer and it has to report everything as uncached. The name lets it compare against
   * what the account already holds. Every other provider matches on the hash and ignores it.
   */
  suspend fun fetchDebridCachedHashes(
    session: AuthSession,
    infoHashes: List<String>,
    releaseNames: Map<String, String> = emptyMap(),
  ): Result<Map<String, List<String>>> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (infoHashes.isEmpty()) return@runCatching emptyMap()
        val payload = JSONObject().put("infoHashes", JSONArray(infoHashes))
        if (releaseNames.isNotEmpty()) {
          payload.put("names", JSONObject().apply { releaseNames.forEach { (hash, name) -> put(hash, name) } })
        }
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

  /**
   * This account's own premium-service keys, so the device can reach those services itself.
   *
   * Fetched only when the account streams directly. The keys go straight into the device's
   * encrypted store and are never held in UI state or written to a log; the server keeps its
   * encrypted copy so the same account still syncs to a TV.
   */
  suspend fun fetchDebridKeys(session: AuthSession): Result<List<DebridKey>> = withContext(Dispatchers.IO) {
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/debrid/accounts/keys")
          .headers(authHeaders(session))
          .build(),
      )
      ensureOk(response, "Failed to load premium service keys")
      val accounts = response.json.optJSONArray("accounts") ?: JSONArray()
      buildList {
        for (index in 0 until accounts.length()) {
          val entry = accounts.optJSONObject(index) ?: continue
          val provider = entry.optString("provider").takeIf { it.isNotBlank() } ?: continue
          val apiKey = entry.optString("apiKey").takeIf { it.isNotBlank() } ?: continue
          add(
            DebridKey(
              provider = provider,
              apiKey = apiKey,
              priority = entry.optInt("priority"),
              enabled = entry.optBoolean("enabled", true),
              username = entry.optString("username").ifBlank { null },
            ),
          )
        }
      }
    }
  }

  /**
   * Saves a provider's key to the account.
   *
   * [enabled] and [priority] carry the state a device already holds for this service, for the one
   * caller that has it: uploading what this device knows when the account holder turns cloud sync
   * on. Left null when connecting a service for the first time, where the server decides — on, and
   * last in the order.
   */
  suspend fun addDebridAccount(
    session: AuthSession,
    provider: String,
    apiKey: String,
    enabled: Boolean? = null,
    priority: Int? = null,
  ): Result<String?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val payload = JSONObject().put("provider", provider).put("apiKey", apiKey)
        enabled?.let { payload.put("enabled", it) }
        priority?.let { payload.put("priority", it) }
        val response = executeJson("/debrid/accounts", payload, session = session)
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
      val magnet = buildMagnet(stream)
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

  suspend fun fetchTraktWatchedEpisodeKeys(session: AuthSession, profileId: String, seriesId: String): Result<Set<String>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder().url("$apiBaseUrl/trakt/sync/history")
            .headers(authHeaders(session, includeContentType = false, profileId = profileId)).build(),
        )
        ensureOk(response, "Failed to load watched episodes")
        val results = response.json.optJSONArray("results") ?: JSONArray()
        buildSet {
          for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            if (!item.optString("type").equals("episode", true)) continue
            val showId = item.optJSONObject("show")?.optJSONObject("ids")?.opt("tmdb").asOptionalInt()?.toString() ?: continue
            if (showId != seriesId) continue
            val node = item.optJSONObject("episode") ?: continue
            val season = node.opt("season").asOptionalInt() ?: continue
            val episode = node.opt("number").asOptionalInt() ?: continue
            add("$seriesId:s$season:e$episode")
          }
        }
      }
    }

  suspend fun syncWatchedEpisode(
    session: AuthSession,
    profileId: String,
    detail: MediaDetail,
    episode: EpisodeItem,
    watched: Boolean,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val episodeNode = JSONObject().put("number", episode.episodeNumber).put("watched_at", Instant.now().toString())
      val show = JSONObject()
        .put("title", detail.title)
        .put("ids", JSONObject().put("tmdb", detail.id.toIntOrNull()).put("imdb", detail.imdbId))
        .put("seasons", JSONArray().put(JSONObject().put("number", episode.seasonNumber).put("episodes", JSONArray().put(episodeNode))))
      val payload = JSONObject().put("movies", JSONArray()).put("shows", JSONArray().put(show))
      val endpoint = if (watched) "/trakt/sync/watched" else "/trakt/sync/history/remove"
      val response = executeJson(endpoint, payload, session = session, profileId = profileId)
      ensureOk(response, "Failed to update episode watched state")
    }
  }

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

  /**
   * When the next and most recent episodes of each of these series air.
   *
   * One call for the whole followed list rather than a detail fetch per series: this drives both
   * the episode reminders and the television's New Episodes row, and both of those run over every
   * series a viewer follows at once. The backend caps and caches the batch; anything it could not
   * answer for is simply absent from the result rather than reported as a failure, because a row
   * built from the rest is better than no row.
   */
  suspend fun fetchSeriesEpisodeStatus(tmdbIds: List<Int>): Result<List<SeriesEpisodeStatus>> = withContext(Dispatchers.IO) {
    runCatching {
      val ids = tmdbIds.filter { it > 0 }.distinct()
      if (ids.isEmpty()) return@runCatching emptyList()
      val response = executeJson("/tmdb/series/episode-status", JSONObject().put("ids", JSONArray(ids)))
      ensureOk(response, "Failed to load episode dates")
      val series = response.json.optJSONArray("series") ?: JSONArray()
      buildList {
        for (index in 0 until series.length()) {
          val item = series.optJSONObject(index) ?: continue
          val tmdbId = item.optInt("tmdbId").takeIf { it > 0 } ?: continue
          add(
            SeriesEpisodeStatus(
              tmdbId = tmdbId,
              title = item.optString("title").ifBlank { null },
              poster = item.optString("poster").ifBlank { null },
              backdrop = item.optString("backdrop").ifBlank { null },
              status = item.optString("status").ifBlank { null },
              nextEpisode = parseAiringEpisode(item.optJSONObject("nextEpisodeToAir")),
              lastEpisode = parseAiringEpisode(item.optJSONObject("lastEpisodeToAir")),
              episodes = item.optJSONArray("episodes")?.let { values ->
                buildList {
                  for (episodeIndex in 0 until values.length()) parseAiringEpisode(values.optJSONObject(episodeIndex))?.let(::add)
                }
              }.orEmpty(),
            ),
          )
        }
      }
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

  // --- Content services (TMDB, MDBList) --------------------------------------------------------
  //
  // Three calls, and the split between them is the storage choice the viewer makes:
  //
  //   validate  checks a key and stores nothing, anywhere. This is what a "This device only"
  //             key goes through, so the viewer gets the same green tick without StreamDek
  //             keeping a copy.
  //   save      checks a key and stores it, encrypted, against the account.
  //   remove    deletes the account copy. The device's own key, if any, is untouched.
  //
  // Nothing here ever reads a key back: the backend has no route that returns one.

  /** Whatever the account holds, masked. Also reports whether the shared fallback is still on. */
  suspend fun fetchContentServiceCredentials(session: AuthSession): Result<AccountCredentials> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/services/credentials")
            .headers(authHeaders(session, includeContentType = false))
            .build(),
        )
        ensureOk(response, "Could not load your content services")
        parseAccountCredentials(response.json)
      }
    }

  /**
   * Asks the service whether this key works, without saving it.
   *
   * A refused key and an unreachable service come back as different failures, because one is the
   * viewer's to fix and the other is nobody's — and telling someone their correct key is wrong
   * because TMDB had a bad minute is how a working key gets deleted.
   */
  /**
   * Checks a key by asking the service itself, from this device.
   *
   * Used when nobody is signed in: there is no StreamDek account to check the key through, and
   * storing it unverified would mean telling the viewer a key works when nothing had established
   * that. The request goes to the service that issued the key and carries nothing else.
   *
   * TMDB hands out two different things people both call an API key -- a v3 key, which goes in a
   * query parameter, and a v4 read access token, which goes in a bearer header. Sending one as
   * the other is a 401 that looks exactly like a typo, so the shape of the key decides.
   */
  suspend fun validateContentServiceKeyDirect(
    service: ContentService,
    apiKey: String,
  ): Result<CredentialCheck> = withContext(Dispatchers.IO) {
    runCatching {
      val trimmed = apiKey.trim()
      if (service == ContentService.TheIntroDb &&
        !Regex("^theintrodb:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+$").matches(trimmed)
      ) {
        return@runCatching CredentialCheck.Failed(CredentialFailure.Malformed)
      }
      if (service == ContentService.IntroDb &&
        !Regex("^idb_[A-Za-z0-9_-]{8,}$").matches(trimmed)
      ) {
        return@runCatching CredentialCheck.Failed(CredentialFailure.Malformed)
      }
      val request = when (service) {
        ContentService.Tmdb -> {
          val builder = Request.Builder().addHeader("Accept", "application/json")
          if (Regex("^[A-Fa-f0-9]{32}$").matches(trimmed)) {
            builder.url("https://api.themoviedb.org/3/authentication?api_key=" + encodeQuery(trimmed))
          } else {
            builder
              .url("https://api.themoviedb.org/3/authentication")
              .addHeader("Authorization", "Bearer " + trimmed)
          }
          builder.build()
        }
        ContentService.Mdblist -> Request.Builder()
          .url("https://api.mdblist.com/user?apikey=" + encodeQuery(trimmed))
          .addHeader("Accept", "application/json")
          .build()
        ContentService.IntroDb -> Request.Builder()
          .url("https://api.introdb.app/submit")
          .addHeader("Accept", "application/json")
          .addHeader("X-API-Key", trimmed)
          .post("{}".toRequestBody("application/json".toMediaType()))
          .build()
        ContentService.TheIntroDb -> Request.Builder()
          .url("https://api.theintrodb.org/v3/media?tmdb_id=949")
          .addHeader("Accept", "application/json")
          .addHeader("Authorization", "Bearer $trimmed")
          .build()
      }

      val response = execute(request, credentialValidationClient)
      if (service == ContentService.IntroDb) {
        return@runCatching when (response.statusCode) {
          400 -> CredentialCheck.Valid(null)
          401, 403 -> CredentialCheck.Failed(CredentialFailure.InvalidKey)
          else -> CredentialCheck.Failed(CredentialFailure.ServiceUnavailable)
        }
      }
      if (service == ContentService.TheIntroDb && response.ok) {
        // V3 media reads are public, so an unknown bearer also receives HTTP 200. Compare its
        // allowance with a public request: a recognised account key receives its own higher
        // limit, while an invalid key silently remains on the public allowance.
        val anonymous = execute(
          Request.Builder()
            .url("https://api.theintrodb.org/v3/media?tmdb_id=949")
            .addHeader("Accept", "application/json")
            .build(),
          credentialValidationClient,
        )
        val publicLimit = anonymous.headers["x-usagelimit-limit"]?.toLongOrNull()
        val accountLimit = response.headers["x-usagelimit-limit"]?.toLongOrNull()
        val recognized = anonymous.ok && publicLimit != null && accountLimit != null && accountLimit > publicLimit
        return@runCatching if (recognized && response.json.optInt("tmdb_id") == 949) {
          CredentialCheck.Valid(null)
        } else {
          CredentialCheck.Failed(CredentialFailure.InvalidKey)
        }
      }
      when {
        // A refusal is the viewer's to fix. Anything else is not, and saying "check your key"
        // during an outage is how a correct key gets thrown away and typed in again.
        response.statusCode == 401 || response.statusCode == 403 || response.statusCode == 404 ->
          CredentialCheck.Failed(CredentialFailure.InvalidKey)
        !response.ok -> CredentialCheck.Failed(CredentialFailure.ServiceUnavailable)
        // MDBList answers 200 with an error body for a key it does not recognise.
        response.json.optString("error").isNotBlank() ->
          CredentialCheck.Failed(CredentialFailure.InvalidKey)
        response.json.has("success") && !response.json.optBoolean("success", true) ->
          CredentialCheck.Failed(CredentialFailure.InvalidKey)
        else -> CredentialCheck.Valid(response.json.optString("username").ifBlank { null })
      }
    }
  }

  suspend fun validateContentServiceKey(
    session: AuthSession,
    service: ContentService,
    apiKey: String,
  ): Result<CredentialCheck> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executeJson(
        "/services/credentials/${encodeQuery(service.id)}/validate",
        JSONObject().put("apiKey", apiKey),
        session = session,
        httpClient = credentialValidationClient,
      )
      when {
        // Anything that is not a considered answer from this route -- a 404 from a deployment
        // that predates it, a gateway error, an outage -- says nothing about the key.
        !response.ok -> CredentialCheck.Failed(CredentialFailure.ServiceUnavailable)
        response.json.optBoolean("valid", false) ->
          CredentialCheck.Valid(response.json.optString("label").ifBlank { null })
        else -> CredentialCheck.Failed(CredentialFailure.fromId(response.json.optString("failure")))
      }
    }
  }

  /** Saves the key to the StreamDek account, so every signed-in device can use it. */
  suspend fun saveContentServiceKey(
    session: AuthSession,
    service: ContentService,
    apiKey: String,
  ): Result<AccountCredentialState> = withContext(Dispatchers.IO) {
    runCatching {
      val response = executePut(
        "/services/credentials/${encodeQuery(service.id)}",
        JSONObject().put("apiKey", apiKey),
        session = session,
        httpClient = credentialValidationClient,
      )
      if (!response.ok) {
        // The backend refuses a key it has checked with a 400 and a `failure`; everything else is
        // StreamDek being unreachable or out of date, which is not the viewer's key to fix.
        val checked = response.statusCode == 400 && response.json.has("failure")
        throw IllegalStateException(
          when {
            checked -> response.json.optString("error").ifBlank {
              CredentialFailure.fromId(response.json.optString("failure")).message
            }
            else -> CredentialFailure.ServiceUnavailable.message
          },
        )
      }
      parseAccountCredentialState(service, response.json.optJSONObject("service"))
    }
  }

  /** Removes the account copy. Every other device on the account loses it too, and is told so. */
  suspend fun removeContentServiceKey(session: AuthSession, service: ContentService): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = execute(
          Request.Builder()
            .url("$apiBaseUrl/services/credentials/${encodeQuery(service.id)}")
            .delete("{}".toRequestBody(jsonMediaType))
            .headers(authHeaders(session))
            .build(),
        )
        ensureOk(response, "Could not remove the key from your StreamDek account")
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

  /**
   * StreamDek's own watchlist -- the list SyncDek serves.
   *
   * Written on every watchlist change whatever service the profile has chosen, alongside the
   * fan-out to the connected providers. That is what makes switching to SyncDek show a list that
   * is already there: it has been kept up to date all along rather than started from empty.
   */
  suspend fun syncDekWatchlist(
    session: AuthSession,
    profileId: String,
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
      val endpoint = if (remove) "/sync/watchlist/remove" else "/sync/watchlist/add"
      val response = executeJson(endpoint, payload, session = session, profileId = profileId)
      ensureOk(response, "Failed to update your watchlist")
    }
  }

  /** The same list, read back and enriched the way a provider's would be. */
  suspend fun fetchSyncDekWatchlist(session: AuthSession, profileId: String): Result<List<TraktItem>> =
    traktList(session, profileId, "/sync/watchlist")

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
            .headers(authHeaders(session, includeContentType = false, profileId = profileId))
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
        // The refresh token has been in every sign-in response since the backend shipped it; this
        // is the client learning to keep it. Both spellings are read because the response carries
        // both -- the OAuth routes use snake_case on the wire and everything else here is camel.
        AuthSession(token, user, refreshToken = response.json.optString("refreshToken").ifBlank {
          response.json.optString("refresh_token")
        }.ifBlank { null })
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

  /**
   * One page of a default catalog.
   *
   * Pages are as deep as the provider allows — the backend reports how many there are and returns
   * nothing past the end, which is how "View All" knows to stop. Catalogs defined by their size
   * (Top 100) report a page count that adds up to exactly that many items.
   */
  suspend fun fetchCatalogPage(catalogId: String, page: Int): Result<DiscoverPage> = withContext(Dispatchers.IO) {
    catalogPageCache.get(catalogId, catalogRegion, page)?.let { return@withContext Result.success(it) }
    runCatching {
      val response = execute(
        Request.Builder()
          .url("$apiBaseUrl/tmdb/catalog/${encodeQuery(catalogId)}?page=$page&region=${encodeQuery(catalogRegion)}")
          .build(),
      )
      // An older backend does not know this catalog at all; the legacy paths below still answer
      // for the four original rows, and anything else genuinely has no more pages to give.
      if (response.statusCode == 404) return@runCatching legacyCatalogPage(catalogId, page)
      ensureOk(response, "Failed to load catalog")
      DiscoverPage(
        items = (response.json.optJSONArray("results") ?: JSONArray()).toMediaItems(),
        page = response.json.optInt("page").takeIf { it > 0 } ?: page,
        totalPages = response.json.optInt("total_pages"),
      ).also { catalogPageCache.put(catalogId, catalogRegion, page, it) }
    }
  }

  private fun legacyCatalogPage(catalogId: String, page: Int): DiscoverPage {
    if (catalogId == "streaming_networks") return DiscoverPage(emptyList(), page, 1)
    val path = legacyBuiltInSections[catalogId]?.second ?: return DiscoverPage(emptyList(), page, 0)
    return DiscoverPage(items = fetchMediaList(path, page), page = page, totalPages = 0)
  }

  /** Pages in more items for a default (non-add-on) home row by page number. */
  suspend fun fetchMoreBuiltInSection(sectionId: String, page: Int): Result<List<MediaItem>> =
    fetchCatalogPage(sectionId, page).map { it.items }

  private fun executeJson(
    path: String,
    payload: JSONObject,
    session: AuthSession? = null,
    profileId: String? = null,
    httpClient: OkHttpClient = client,
  ): JsonResponse {
    val request = Request.Builder()
      .url("$apiBaseUrl$path")
      .post(payload.toString().toRequestBody(jsonMediaType))
      .headers(authHeaders(session, profileId = profileId))
      .build()
    return execute(request, httpClient)
  }

  private fun executePut(
    path: String,
    payload: JSONObject,
    session: AuthSession? = null,
    profileId: String? = null,
    httpClient: OkHttpClient = client,
  ): JsonResponse {
    val request = Request.Builder()
      .url("$apiBaseUrl$path")
      .put(payload.toString().toRequestBody(jsonMediaType))
      .headers(authHeaders(session, profileId = profileId))
      .build()
    return execute(request, httpClient)
  }

  /**
   * Every request this client makes.
   *
   * Two answers are handled here rather than at each call site, because there are dozens of call
   * sites and neither is something a call site can sensibly decide about.
   *
   * **403 with ACCOUNT_SUSPENDED** ends the session immediately and throws, so the app signs out
   * and says why. Never retried: a suspended account has nothing to renew, and asking again is a
   * client politely requesting to be refused a second time.
   *
   * **401** is retried once with a renewed token. Access tokens do not expire yet, so that path
   * is dormant today -- it ships first so that on the day AUTH_TOKEN_TTL is set, this app renews
   * rather than signing people out mid-use.
   */
  private fun execute(request: Request, httpClient: OkHttpClient = client): JsonResponse {
    val first = performRequest(request, httpClient)

    if (first.statusCode == 403 && errorCodeOf(first.json) == "ACCOUNT_SUSPENDED") {
      val message = errorMessageOf(first.json) ?: "This account has been suspended."
      endSession(SessionEndReason.SUSPENDED, message)
      throw AccountSuspendedException(message)
    }

    // Only a request that carried a token can have an expired one.
    if (first.statusCode != 401 || request.header("Authorization").isNullOrBlank()) return first

    val renewed = renewAccessToken() ?: return first
    val retried = request.newBuilder().header("Authorization", "Bearer $renewed").build()
    // Once, and once only: a second 401 on a fresh token is not a timing problem, and looping
    // would turn one refused request into a hot loop against the API.
    return performRequest(retried, httpClient)
  }

  private fun performRequest(request: Request, httpClient: OkHttpClient): JsonResponse {
    httpClient.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      val json = runCatching { JSONObject(body) }.getOrElse {
        if (body.trimStart().startsWith("[")) {
          JSONObject().put("__array", JSONArray(body))
        } else {
          JSONObject()
        }
      }
      return JsonResponse(response.isSuccessful, json, response.code, response.headers)
    }
  }

  /**
   * Reads the error code out of either envelope.
   *
   * Legacy paths answer `{ "error": "message", "errorDetail": { "code" } }` and /api/v1 answers
   * `{ "error": { "code", "message" } }`. This app still calls legacy paths for almost everything,
   * so both have to be understood -- and will for as long as those aliases exist.
   */
  private fun errorCodeOf(json: JSONObject): String? {
    json.optJSONObject("error")?.optString("code")?.takeIf { it.isNotBlank() }?.let { return it }
    return json.optJSONObject("errorDetail")?.optString("code")?.takeIf { it.isNotBlank() }
  }

  private fun errorMessageOf(json: JSONObject): String? {
    json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.let { return it }
    return json.optString("error").takeIf { it.isNotBlank() }
  }

  /**
   * Renews the access token, at most once at a time.
   *
   * Synchronised because this app fires several requests at once on almost every screen. Eight
   * concurrent 401s would otherwise rotate the refresh token eight times, which the server reads
   * as reuse and answers by revoking the whole chain -- turning a recoverable expiry into a
   * forced sign-out.
   */
  @Synchronized
  private fun renewAccessToken(): String? {
    val store = sessionStore ?: return null
    val current = store.load() ?: return null
    val refreshToken = current.refreshToken ?: return null

    val response = performRequest(
      Request.Builder()
        .url("$apiBaseUrl/auth/refresh")
        .post(JSONObject().put("refresh_token", refreshToken).toString().toRequestBody(jsonMediaType))
        .headers(authHeaders(null))
        .build(),
      client,
    )

    if (!response.ok) {
      val suspended = errorCodeOf(response.json) == "ACCOUNT_SUSPENDED"
      endSession(
        if (suspended) SessionEndReason.SUSPENDED else SessionEndReason.EXPIRED,
        errorMessageOf(response.json)
          ?: if (suspended) "This account has been suspended." else "Your session has ended. Please sign in again.",
      )
      return null
    }

    val token = response.json.optString("token").takeIf { it.isNotBlank() } ?: return null
    val rotated = response.json.optString("refreshToken").ifBlank {
      response.json.optString("refresh_token")
    }.takeIf { it.isNotBlank() } ?: return null

    // The rotated token replaces the one that was spent. Keeping the old one would mean the next
    // renewal presents a used token, which the server reads as theft.
    store.save(current.copy(token = token, refreshToken = rotated))
    return token
  }

  private fun endSession(reason: SessionEndReason, message: String) {
    // The stored session goes first: whatever the app does in response, the token must already be
    // gone, or a request already in flight can put it back.
    sessionStore?.clear()
    runCatching { onSessionEnded?.invoke(reason, message) }
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

  private fun buildMagnet(stream: AddonStream): String =
    buildMagnetLink(stream.infoHash.orEmpty(), stream.filename, stream.sources)

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

private data class JsonResponse(
  val ok: Boolean,
  val json: JSONObject,
  val statusCode: Int,
  val headers: okhttp3.Headers,
)

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
      if (isAdultCatalogEntry(item)) continue
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

/**
 * One service's account state, as the backend reports it.
 *
 * Only ever the masked form. There is no branch here that could produce the key itself, because
 * no response the backend sends contains one.
 */
private fun parseAccountCredentialState(service: ContentService, json: JSONObject?): AccountCredentialState {
  if (json == null) return AccountCredentialState(service, configured = false)
  return AccountCredentialState(
    service = service,
    configured = json.optBoolean("configured", false),
    maskedKey = json.optString("maskedKey").ifBlank { null },
    label = json.optString("label").ifBlank { null },
    needsAttention = json.optString("status") == "needs_attention",
    lastValidatedAt = json.optString("lastValidatedAt").ifBlank { null },
  )
}

private fun parseAccountCredentials(json: JSONObject): AccountCredentials {
  val services = json.optJSONArray("services") ?: JSONArray()
  var tmdb: AccountCredentialState? = null
  var mdblist: AccountCredentialState? = null
  var introDb: AccountCredentialState? = null
  var theIntroDb: AccountCredentialState? = null
  for (index in 0 until services.length()) {
    val entry = services.optJSONObject(index) ?: continue
    when (ContentService.fromId(entry.optString("service"))) {
      ContentService.Tmdb -> tmdb = parseAccountCredentialState(ContentService.Tmdb, entry)
      ContentService.Mdblist -> mdblist = parseAccountCredentialState(ContentService.Mdblist, entry)
      ContentService.IntroDb -> introDb = parseAccountCredentialState(ContentService.IntroDb, entry)
      ContentService.TheIntroDb -> theIntroDb = parseAccountCredentialState(ContentService.TheIntroDb, entry)
      null -> Unit
    }
  }
  return AccountCredentials(
    tmdb = tmdb,
    mdblist = mdblist,
    introDb = introDb,
    theIntroDb = theIntroDb,
    sharedFallbackAvailable = json.optBoolean("sharedFallbackAvailable", true),
  )
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

/**
 * Whether a catalogue entry is pornography.
 *
 * Read off the raw payload rather than the parsed item so the source's own `adult` flag counts:
 * TMDB sets it, and it is the one signal here that involves no guessing. The description is
 * deliberately not searched -- a plot summary mentioning pornography is usually a documentary
 * about it, and hiding those is how a filter earns a reputation for being wrong.
 */
internal fun isAdultCatalogEntry(item: JSONObject): Boolean = AdultContentFilter.isBlockedItem(
  adultFlag = item.optBoolean("adult", false),
  title = item.optString("title").ifBlank { item.optString("name") },
  genres = parseGenreNames(item),
)

private fun parseAiringEpisode(json: JSONObject?): AiringEpisode? {
  val airDate = json?.optString("airDate")?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
  return AiringEpisode(
    id = json.optInt("id").takeIf { it > 0 },
    name = json.optString("name").ifBlank { null },
    season = json.optInt("season").takeIf { json.has("season") && !json.isNull("season") },
    episode = json.optInt("episode").takeIf { json.has("episode") && !json.isNull("episode") },
    airDate = airDate,
    still = json.optString("still").ifBlank { null },
  )
}

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

private fun parseMediaDetail(json: JSONObject): MediaDetail {
  val releasedSeasons = buildList {
    val source = json.optJSONArray("seasons") ?: JSONArray()
    for (index in 0 until source.length()) {
      val season = source.optJSONObject(index) ?: continue
      val summary = SeasonSummary(
        seasonNumber = season.optInt("season_number"),
        name = season.optString("name").ifBlank { "Season ${season.optInt("season_number")}" },
        episodeCount = season.optInt("episode_count"),
        poster = season.optString("poster").ifBlank { tmdbImageUrl(season.optString("poster_path"), "w342") },
        airDate = season.optString("air_date").ifBlank { null },
      )
      if (isSeasonAvailable(summary)) add(summary)
    }
  }
  return MediaDetail(
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
    seasonsCount = releasedSeasons.size.takeIf { it > 0 },
    imdbId = json.optString("imdbId").ifBlank { json.optString("imdb_id").ifBlank { json.optJSONObject("external_ids")?.optString("imdb_id").orEmpty().ifBlank { null } } },
    seasons = releasedSeasons,
    cast = parseCastList(json.optJSONArray("cast") ?: json.optJSONObject("credits")?.optJSONArray("cast")),
    similarTitles = (json.optJSONArray("similarTitles") ?: json.optJSONArray("similar") ?: json.optJSONArray("recommendations")).toMediaItems(),
    availableOn = parseWatchProviders(json),
    traktComments = parseTraktComments(json),
  )
}

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
    favourite = json.optBoolean("favourite", false),
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
      sources = buildList {
        val sources = optJSONArray("sources") ?: JSONArray()
        for (index in 0 until sources.length()) {
          sources.optString(index).takeIf { it.isNotBlank() }?.let(::add)
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
    positionSec = json.optDouble("positionSec").takeIf { json.has("positionSec") && it.isFinite() && it >= 0.0 },
    durationSec = json.optDouble("durationSec").takeIf { json.has("durationSec") && it.isFinite() && it > 0.0 },
    seasonNumber = json.optInt("seasonNumber").takeIf { it > 0 },
    episodeNumber = json.optInt("episodeNumber").takeIf { it > 0 },
    addedAt = parseFlexibleTimestamp(json, "listed_at", "listedAt", "added_at", "addedAt"),
    updatedAt = parseFlexibleTimestamp(json, "updated_at", "updatedAt", "paused_at", "pausedAt", "last_watched_at", "lastWatchedAt", "watched_at", "watchedAt"),
  )

/**
 * A string field that may legitimately be absent.
 *
 * Android's `optString` returns the four characters "null" for a JSON null rather than an empty
 * string, which no amount of `ifBlank` will catch. Poster and backdrop went through it, so a
 * record with no artwork arrived as the URL "null", was stored, and drew an empty card that
 * looked for all the world like missing data rather than a bad string.
 */
private fun JSONObject.optStringOrNull(name: String): String? =
  if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() && it != "null" && it != "undefined" }

/** A JSON value that may legitimately be absent, as a nullable Int. */
private fun Any?.asOptionalInt(): Int? = when (this) {
  null, JSONObject.NULL -> null
  is Number -> toInt()
  else -> toString().trim().toIntOrNull()
}

/** An ISO-8601 instant as epoch millis, or zero when it cannot be read. */
private fun parseIsoInstantMillis(value: String?): Long =
  runCatching { java.time.Instant.parse(value.orEmpty()).toEpochMilli() }.getOrDefault(0L)
