package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Stremio add-ons whose manifest only makes sense on this device's own network — localhost,
 * a LAN address, or a USB-tethered dev machine reached via `adb reverse` — can't be installed
 * through the normal "Add-ons" flow. That flow POSTs the manifest URL to StreamDek's backend,
 * which fetches it itself; a URL like http://127.0.0.1:11470 means something different on every
 * machine that resolves it, so the backend can never reach a manifest server running on the
 * user's own phone or PC.
 *
 * LocalAddonManager instead fetches the manifest directly from the phone — exactly like the
 * on-device Plugin scraper collections in [StreamDekPluginManager] already do — and keeps its
 * own small local store so these add-ons can sit alongside the backend-managed ones. Catalog and
 * stream calls for a local add-on are also made directly from the device (see
 * [net.streamdek.mobile.nativeapp.StreamDekApiClient]'s local-addon branches), never through the
 * backend.
 */
private fun stringList(values: JSONArray?): List<String> = buildList {
  val source = values ?: return@buildList
  for (i in 0 until source.length()) {
    when (val item = source.opt(i)) {
      is String -> item.takeIf { it.isNotBlank() }?.let(::add)
      is JSONObject -> item.optString("name").takeIf { it.isNotBlank() }?.let(::add)
    }
  }
}

private fun genreOptions(item: JSONObject): List<String> {
  val extra = item.optJSONArray("extra") ?: return stringList(item.optJSONArray("genres"))
  for (i in 0 until extra.length()) {
    val entry = extra.optJSONObject(i) ?: continue
    if (!entry.optString("name").equals("genre", ignoreCase = true)) continue
    val options = entry.optJSONArray("options") ?: continue
    return buildList { for (j in 0 until options.length()) options.optString(j).takeIf { it.isNotBlank() }?.let(::add) }
  }
  return emptyList()
}

/** Whether the catalog declares genre as a required extra, so it cannot be listed without one. */
private fun genreRequired(item: JSONObject): Boolean {
  item.optJSONArray("extra")?.let { extra ->
    for (i in 0 until extra.length()) {
      val entry = extra.optJSONObject(i) ?: continue
      if (entry.optString("name").equals("genre", ignoreCase = true) && entry.optBoolean("isRequired")) return true
    }
  }
  return stringList(item.optJSONArray("extraRequired")).any { it.equals("genre", ignoreCase = true) }
}

private fun catalogList(values: JSONArray?): List<AddonCatalog> = buildList {
  val source = values ?: return@buildList
  for (i in 0 until source.length()) {
    val item = source.optJSONObject(i) ?: continue
    val id = item.optString("id").trim()
    if (id.isEmpty()) continue
    add(AddonCatalog(type = item.optString("type").trim(), id = id, name = item.optString("name").ifBlank { id }, genreOptions = genreOptions(item), requiresGenre = genreRequired(item)))
  }
}

private fun parseLocalManifest(id: String, manifestUrl: String, json: JSONObject, enabled: Boolean, position: Int, favourite: Boolean): InstalledAddon {
  val behaviorHints = json.optJSONObject("behaviorHints") ?: JSONObject()
  val baseUrl = manifestUrl.substringBeforeLast("/manifest.json", missingDelimiterValue = manifestUrl).trimEnd('/')
  return InstalledAddon(
    id = id,
    enabled = enabled,
    position = position,
    favourite = favourite,
    url = manifestUrl,
    baseUrl = baseUrl,
    manifestUrl = manifestUrl,
    transportUrl = baseUrl,
    manifest = AddonManifest(
      id = json.optString("id").ifBlank { id },
      name = json.optString("name").ifBlank { "Local add-on" },
      version = json.optString("version").ifBlank { "unknown" },
      description = json.optString("description").ifBlank { null },
      logo = json.optString("logo").ifBlank { null },
      url = manifestUrl,
      baseUrl = baseUrl,
      manifestUrl = manifestUrl,
      transportUrl = baseUrl,
      resources = stringList(json.optJSONArray("resources")),
      types = stringList(json.optJSONArray("types")),
      catalogs = catalogList(json.optJSONArray("catalogs")),
      behaviorConfigurable = behaviorHints.optBoolean("configurable"),
      configurationRequired = behaviorHints.optBoolean("configurationRequired"),
    ),
  )
}

private data class LocalAddonRecord(
  val id: String,
  val manifestUrl: String,
  val enabled: Boolean,
  val position: Int,
  val favourite: Boolean,
  val manifestJson: String,
)

object LocalAddonManager {
  private const val PREFS_NAME = "streamdek_local_addons"
  private const val KEY_ADDONS = "addons"
  private const val ID_PREFIX = "local:"

  private var prefs: SharedPreferences? = null
  private var ownerKey: String = "guest"
  private val http = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  fun initialize(context: Context) {
    if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }
  fun selectProfileStorage(ownerKey: String) {
    this.ownerKey = ownerKey.ifBlank { "guest" }
    val storage = prefs ?: return
    val scopedKey = addonsKey()
    if (!storage.contains(scopedKey)) {
      storage.getString(KEY_ADDONS, null)?.let { legacy ->
        storage.edit().putString(scopedKey, legacy).apply()
      }
    }
  }

  fun isLocalAddonId(id: String): Boolean = id.startsWith(ID_PREFIX)

  /**
   * Joins one owner's locally-added add-ons into another's, without disturbing whichever owner is
   * currently selected. Used to move a guest's add-ons into the profile they signed into.
   *
   * A union on the manifest URL, which is what identifies a local add-on - the same manifest added
   * under both identities is one add-on, and the destination's record of it wins, since that is the
   * copy whose enabled flag and position the profile has been using. Positions are renumbered so
   * the merged list is one continuous sequence, in the destination's order with the incoming
   * add-ons behind it.
   *
   * Idempotent: running it twice adds nothing the first run did not, because the second run finds
   * every manifest URL already present.
   *
   * @return how many add-ons were actually carried over.
   */
  fun mergeProfileStorageInto(fromOwnerKey: String, toOwnerKey: String): Int {
    val storage = prefs ?: return 0
    val fromKey = "$KEY_ADDONS:${fromOwnerKey.ifBlank { "guest" }}"
    val toKey = "$KEY_ADDONS:${toOwnerKey.ifBlank { "guest" }}"
    if (fromKey == toKey) return 0
    val incoming = readAll(storage.getString(fromKey, null))
    if (incoming.isEmpty()) return 0
    val existing = readAll(storage.getString(toKey, null)).sortedBy { it.position }
    val known = existing.mapTo(HashSet()) { it.manifestUrl.lowercase() }
    val added = incoming.filter { known.add(it.manifestUrl.lowercase()) }
    if (added.isEmpty()) return 0
    val merged = (existing + added).mapIndexed { index, record -> record.copy(position = index) }
    storage.edit().putString(toKey, serialize(merged)).commit()
    return added.size
  }

  /** The manifest URLs one owner has added locally, for a caller that has to register them elsewhere. */
  fun manifestUrlsFor(ownerKey: String): List<String> =
    readAll(prefs?.getString("$KEY_ADDONS:${ownerKey.ifBlank { "guest" }}", null)).map { it.manifestUrl }

  private fun readAll(): List<LocalAddonRecord> = readAll(prefs?.getString(addonsKey(), null))

  private fun readAll(raw: String?): List<LocalAddonRecord> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
      val array = JSONArray(raw)
      List(array.length()) { index ->
        val item = array.getJSONObject(index)
        LocalAddonRecord(
          id = item.getString("id"),
          manifestUrl = item.getString("manifestUrl"),
          enabled = item.optBoolean("enabled", true),
          position = item.optInt("position", 0),
          favourite = item.optBoolean("favourite", false),
          manifestJson = item.getString("manifestJson"),
        )
      }
    }.getOrDefault(emptyList())
  }

  private fun writeAll(records: List<LocalAddonRecord>) {
    prefs?.edit()?.putString(addonsKey(), serialize(records))?.apply()
  }

  private fun serialize(records: List<LocalAddonRecord>): String {
    val array = JSONArray()
    records.forEach { record ->
      array.put(
        JSONObject()
          .put("id", record.id)
          .put("manifestUrl", record.manifestUrl)
          .put("enabled", record.enabled)
          .put("position", record.position)
          .put("favourite", record.favourite)
          .put("manifestJson", record.manifestJson),
      )
    }
    return array.toString()
  }

  private fun addonsKey(): String = "$KEY_ADDONS:$ownerKey"

  /** All locally-installed add-ons, ready to merge into the regular add-on list. */
  fun list(): List<InstalledAddon> = readAll()
    .sortedBy { it.position }
    .mapNotNull { record ->
      runCatching { parseLocalManifest(record.id, record.manifestUrl, JSONObject(record.manifestJson), record.enabled, record.position, record.favourite) }.getOrNull()
    }

  suspend fun install(rawUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val normalized = rawUrl.trim()
      val uri = runCatching { URI(normalized) }.getOrNull()
      require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Enter a valid add-on manifest URL." }
      val existing = readAll()
      require(existing.none { it.manifestUrl.equals(normalized, ignoreCase = true) }) { "This add-on is already installed." }
      val body = fetchManifestBody(normalized)
      val json = JSONObject(body)
      require(json.has("id") && json.has("name")) { "That URL doesn't look like a Stremio add-on manifest." }
      val id = ID_PREFIX + Integer.toHexString(normalized.hashCode())
      writeAll(existing + LocalAddonRecord(id = id, manifestUrl = normalized, enabled = true, position = existing.size, favourite = false, manifestJson = body))
    }
  }

  suspend fun refresh(id: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val existing = readAll()
      val target = existing.firstOrNull { it.id == id } ?: throw IllegalStateException("This add-on is no longer installed.")
      val body = fetchManifestBody(target.manifestUrl)
      writeAll(existing.map { if (it.id == id) it.copy(manifestJson = body) else it })
    }
  }

  private fun fetchManifestBody(url: String): String = try {
    http.newCall(Request.Builder().url(url).header("User-Agent", "StreamDek/1.0").build()).execute().use { response ->
      if (!response.isSuccessful) throw IllegalStateException("Request failed: ${response.code}")
      response.body?.string() ?: throw IllegalStateException("Empty response.")
    }
  } catch (e: Exception) {
    val host = runCatching { URI(url).host }.getOrNull().orEmpty()
    if (e !is IllegalStateException && isLocalNetworkHost(host)) {
      throw IllegalStateException(
        "Could not reach $host from this phone. Use your computer's LAN IP instead of localhost, or run `adb reverse tcp:<port> tcp:<port>` first.",
        e,
      )
    }
    throw e
  }

  fun setEnabled(id: String, enabled: Boolean) {
    writeAll(readAll().map { if (it.id == id) it.copy(enabled = enabled) else it })
  }

  fun setFavourite(id: String, favourite: Boolean) {
    writeAll(readAll().map { if (it.id == id) it.copy(favourite = favourite) else it })
  }

  fun remove(id: String) {
    writeAll(readAll().filterNot { it.id == id })
  }

  fun move(id: String, delta: Int) {
    val all = readAll()
    val favourite = all.firstOrNull { it.id == id }?.favourite ?: return
    val current = all.filter { it.favourite == favourite }.sortedBy { it.position }.toMutableList()
    val index = current.indexOfFirst { it.id == id }
    val target = (index + delta).coerceIn(0, current.lastIndex)
    if (index < 0 || index == target) return
    val item = current.removeAt(index)
    current.add(target, item)
    val groupPositions = all.filter { it.favourite == favourite }.map { it.position }.sorted()
    val updated = current.mapIndexed { groupIndex, record -> record.copy(position = groupPositions[groupIndex]) }.associateBy { it.id }
    writeAll(all.map { updated[it.id] ?: it })
  }
}
