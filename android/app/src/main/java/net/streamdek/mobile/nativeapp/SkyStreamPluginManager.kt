package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Installs and runs SkyStream provider collections (`.sky` extensions).
 *
 * SkyStream repos advertise themselves with the same manifest shape CloudStream uses —
 * `{name, pluginLists: [urls to a plugins.json]}`, optionally behind an aggregate `{repos: […]}`
 * — which is exactly why these ended up in [CloudStreamRepoManager] and failed there with
 * "No manifest.json inside …". They are a different thing entirely: a `.cs3` is compiled dex, a
 * `.sky` is a zip of
 *
 *   plugin.json   metadata: packageName, name, version, baseUrl, categories
 *   plugin.js     a bundled script that assigns its entry points onto globalThis
 *
 * so it runs in the same QuickJS sandbox [StreamDekPluginManager] already uses, not a ClassLoader.
 *
 * The script expects a small host surface, which [SkyStreamPluginRuntime] provides:
 *
 *   http_get(url, headers)         → { status, body, headers }
 *   http_post(url, headers, body)  → same
 *   manifest                       → the plugin.json object
 *   StreamResult / MultimediaItem  → model constructors the script news up
 *   getPreference(key)             → stored setting value, or null
 *
 * and answers through a callback rather than a return value:
 *
 *   loadStreams(JSON.stringify({imdbId, type, season, episode}), cb)
 *   cb({ success: true, data: [StreamResult…] })  |  cb({ success: false, error })
 *
 * Only the streams path is wired up. `getHome`/`search` exist in these bundles too, but StreamDek
 * sources catalogues from add-ons and TMDB — it only needs these plugins to answer "where can I
 * play this".
 *
 * Providers default to *disabled* on install, matching the CloudStream manager: one aggregate can
 * list dozens, and downloading every bundle because a URL was pasted would be slow and unwanted.
 */
data class SkyRepo(
  val url: String,
  val name: String,
  val description: String?,
  val enabled: Boolean = true,
)

data class SkyProvider(
  val repoUrl: String,
  val packageName: String,
  val name: String,
  val version: Int,
  val downloadUrl: String,
  val description: String?,
  val categories: List<String> = emptyList(),
  val enabled: Boolean = false,
  val installedFilePath: String? = null,
)

data class SkyPluginState(
  val repos: List<SkyRepo> = emptyList(),
  val providers: List<SkyProvider> = emptyList(),
  val updatedAt: Long = 0L,
)

/** A `.sky` bundle unpacked into the two things the runtime needs. */
internal data class SkyBundle(val manifestJson: String, val script: String)

/** Thrown when a collection contains no `.sky` entries, so the caller can try another format. */
class NotASkyStreamRepo(message: String) : IllegalArgumentException(message)

class SkyStreamPluginManager(private val context: Context) {
  private companion object {
    const val TAG = "SkyStreamPlugins"
    const val LEGACY_STORAGE_KEY = "state"
    const val STREAM_TIMEOUT_MS = 30_000L
  }

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("streamdek_sky_plugins", Context.MODE_PRIVATE)
  private val http = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  // Unlike a .cs3 this is never handed to a ClassLoader — the zip is opened and its plugin.js read
  // as text — so ordinary private storage is fine and avoids relying on external storage existing.
  private val pluginDir: File by lazy { File(context.applicationContext.filesDir, "sky_plugins").apply { mkdirs() } }

  private var storageKey = LEGACY_STORAGE_KEY

  @Volatile var state: SkyPluginState = load()
    private set
  var onStateChanged: ((SkyPluginState) -> Unit)? = null

  fun selectProfileStorage(ownerKey: String) {
    val nextKey = "state:$ownerKey"
    if (nextKey == storageKey) return
    storageKey = nextKey
    if (!prefs.contains(nextKey)) {
      prefs.getString(LEGACY_STORAGE_KEY, null)?.let { legacy ->
        prefs.edit().putString(nextKey, legacy).remove(LEGACY_STORAGE_KEY).apply()
      }
    }
    state = load()
    onStateChanged?.invoke(state)
  }

  // ── Collections ────────────────────────────────────────────────────────────────────────────

  suspend fun addRepo(rawUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val url = rawUrl.trim()
      require(url.startsWith("http://") || url.startsWith("https://")) { "Enter a valid repo URL." }
      require(state.repos.none { it.url.equals(url, ignoreCase = true) }) { "This collection is already installed." }
      val (repo, providers) = fetchRepo(url)
      state = state.copy(repos = state.repos + repo, providers = state.providers + providers)
      save()
    }
  }

  suspend fun refreshRepo(url: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val previous = state.providers.filter { it.repoUrl == url }.associateBy { it.packageName }
      val (repo, fresh) = fetchRepo(url)
      val merged = fresh.map { entry ->
        val existing = previous[entry.packageName] ?: return@map entry
        // A version bump makes the cached bundle stale — drop it so the next enable refetches.
        if (existing.version != entry.version) {
          existing.installedFilePath?.let { runCatching { File(it).delete() } }
          entry.copy(enabled = existing.enabled, installedFilePath = null)
        } else {
          entry.copy(enabled = existing.enabled, installedFilePath = existing.installedFilePath)
        }
      }
      val existingRepo = state.repos.firstOrNull { it.url == url }
      state = state.copy(
        repos = state.repos.map { if (it.url == url) repo.copy(enabled = existingRepo?.enabled ?: true) else it },
        providers = state.providers.filterNot { it.repoUrl == url } + merged,
      )
      save()
    }
  }

  fun removeRepo(url: String) {
    state.providers.filter { it.repoUrl == url }.forEach { entry ->
      entry.installedFilePath?.let { runCatching { File(it).delete() } }
    }
    state = state.copy(
      repos = state.repos.filterNot { it.url == url },
      providers = state.providers.filterNot { it.repoUrl == url },
    )
    save()
  }

  fun enableRepo(url: String, enabled: Boolean) {
    state = state.copy(repos = state.repos.map { if (it.url == url) it.copy(enabled = enabled) else it })
    save()
  }

  /** Downloads the bundle if needed, then marks the provider on (or off). */
  suspend fun setProviderEnabled(repoUrl: String, packageName: String, enabled: Boolean): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val entry = state.providers.firstOrNull { it.repoUrl == repoUrl && it.packageName == packageName }
          ?: throw IllegalStateException("This source is no longer listed in its collection.")
        if (!enabled) {
          state = state.copy(
            providers = state.providers.map {
              if (it.repoUrl == repoUrl && it.packageName == packageName) it.copy(enabled = false) else it
            },
          )
          save()
          return@runCatching
        }
        val file = entry.installedFilePath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
          ?: downloadPlugin(entry)
        // Unpack once here so a bundle that is not really a .sky fails at the moment the user
        // turns it on, with a message about this file, rather than silently at stream time.
        readBundle(file)
        state = state.copy(
          providers = state.providers.map {
            if (it.repoUrl == repoUrl && it.packageName == packageName) {
              it.copy(enabled = true, installedFilePath = file.absolutePath)
            } else {
              it
            }
          },
        )
        save()
      }
    }

  /** Providers that are switched on, inside collections that are switched on. */
  fun activeProviders(): List<SkyProvider> {
    val enabledRepos = state.repos.filter { it.enabled }.mapTo(mutableSetOf()) { it.url }
    return state.providers.filter { it.enabled && it.repoUrl in enabledRepos }
  }

  // ── Streams ────────────────────────────────────────────────────────────────────────────────

  /**
   * Asks every enabled provider for streams, publishing each one's results as they land so the
   * list fills in progressively — the same contract [StreamDekPluginManager.streams] offers.
   *
   * [imdbId] is required: these plugins key on IMDb ids and answer with an error when one is
   * missing, so there is nothing to ask with otherwise.
   */
  suspend fun streams(
    title: String?,
    year: Int?,
    imdbId: String?,
    type: String,
    season: Int?,
    episode: Int?,
    onProviderResults: suspend (List<AddonStream>) -> Unit = {},
  ): List<AddonStream> {
    val query = title?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val providers = activeProviders()
    if (providers.isEmpty()) return emptyList()

    val isMovie = normalizeSkyType(type) == "movie"
    val payload = JSONObject()
      .put("title", query)
      .put("type", if (isMovie) "movie" else "series")
      .apply {
        year?.let { put("year", it) }
        imdbId?.trim()?.takeIf { it.startsWith("tt", ignoreCase = true) }?.let { put("imdbId", it) }
        if (!isMovie) {
          put("season", season ?: 1)
          put("episode", episode ?: 1)
        }
      }
      .toString()

    Log.i(TAG, "Loading streams title=$query year=$year type=$type providers=${providers.size}")
    // Everything below runs off the main thread. `quickJs {}` evaluates on whichever thread calls
    // it — the dispatcher it takes is only for its internal job queue — and the HTTP bridge inside
    // it blocks that thread for the duration of each request. streams() is called from the view
    // model's main-dispatcher scope, so without this the plugins ran their scraping, and their
    // network calls, on the UI thread: one provider whose analytics host was being blackholed sat
    // on a 15s connect timeout and took the whole app down with an ANR.
    return withContext(Dispatchers.IO) {
      supervisorScope {
        val gate = Semaphore(4)
        providers.map { provider ->
          async {
            val results = gate.withPermit {
              runCatching { loadProviderStreams(provider, payload) }
                .onFailure { Log.w(TAG, "${provider.name} failed", it) }
                .getOrDefault(emptyList())
            }
            if (results.isNotEmpty()) onProviderResults(results)
            results
          }
        }.awaitAll().flatten()
      }
    }
  }

  private suspend fun loadProviderStreams(provider: SkyProvider, payload: String): List<AddonStream> {
    // streams() is called from the view model's main-dispatcher scope, and async inherits that
    // context — so fetching a missing bundle and unzipping it would otherwise both run on the UI
    // thread. Only the QuickJS evaluation moves itself off main on its own.
    val bundle = withContext(Dispatchers.IO) {
      val file = provider.installedFilePath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
        ?: downloadPlugin(provider)
      readBundle(file)
    }
    val raw = SkyStreamPluginRuntime.loadStreams(
      bundle = bundle,
      payload = payload,
      preferences = providerPreferences(provider.packageName),
      http = http,
      timeoutMs = STREAM_TIMEOUT_MS,
      log = { message -> Log.i(TAG, "[${provider.name}] $message") },
    )
    val streams = parseSkyStreams(raw, provider)
    skyStreamError(raw)
      ?.let { Log.i(TAG, "[${provider.name}] $it") }
      ?: Log.i(TAG, "[${provider.name}] ${streams.size} stream(s)")
    return streams
  }

  /**
   * The plugin's own settings schema.
   *
   * These declare their options at runtime rather than in the manifest — Torrentio's Debrid
   * Service picker is one — so the only way to know what a source can be configured with is to
   * load it and ask. The shape maps onto [PluginSettingField] so the JS-plugin settings dialog
   * can render both.
   */
  suspend fun settingsSchema(provider: SkyProvider): Result<List<PluginSettingField>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val bundle = withContext(Dispatchers.IO) {
          val file = provider.installedFilePath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
            ?: downloadPlugin(provider)
          readBundle(file)
        }
        val raw = SkyStreamPluginRuntime.getSettings(
          bundle = bundle,
          preferences = providerPreferences(provider.packageName),
          http = http,
          timeoutMs = STREAM_TIMEOUT_MS,
          log = { message -> Log.i(TAG, "[${provider.name}] $message") },
        )
        parseSkySettingsSchema(raw)
      }
    }

  /** Stored values for a plugin's settings, as the dialog reads them. */
  fun providerSettings(packageName: String): Map<String, Any> = providerPreferences(packageName)

  fun saveProviderSettings(packageName: String, values: Map<String, Any?>) {
    val json = JSONObject()
    values.forEach { (key, value) -> if (value != null) json.put(key, value.toString()) }
    prefs.edit().putString("settings:$storageKey:$packageName", json.toString()).apply()
  }

  /** Values previously stored for this plugin's own `getSettings()` fields. */
  private fun providerPreferences(packageName: String): Map<String, String> {
    val raw = prefs.getString("settings:$storageKey:$packageName", null) ?: return emptyMap()
    return runCatching {
      val json = JSONObject(raw)
      buildMap { json.keys().forEach { key -> put(key, json.optString(key)) } }
    }.getOrDefault(emptyMap())
  }

  fun setProviderPreference(packageName: String, key: String, value: String?) {
    val current = providerPreferences(packageName).toMutableMap()
    if (value.isNullOrBlank()) current.remove(key) else current[key] = value
    prefs.edit().putString("settings:$storageKey:$packageName", JSONObject(current as Map<*, *>).toString()).apply()
  }

  // ── Fetching ───────────────────────────────────────────────────────────────────────────────

  private fun fetchRepo(url: String): Pair<SkyRepo, List<SkyProvider>> {
    val manifest = JSONObject(text(url))
    val name = manifest.optString("name").ifBlank { "SkyStream collection" }
    val pluginListUrls = collectRepoPluginListUrls(url, manifest) { childUrl ->
      runCatching { JSONObject(text(childUrl)) }
        .onFailure { Log.w(TAG, "Skipping unreachable repo $childUrl inside $url", it) }
        .getOrNull()
    }
    require(pluginListUrls.isNotEmpty()) { "Repo manifest has no pluginLists." }

    val seen = mutableSetOf<String>()
    var sawNonSkyEntry = false
    val providers = buildList {
      for (listUrl in pluginListUrls) {
        val entries = runCatching { JSONArray(text(listUrl)) }.getOrDefault(JSONArray())
        for (index in 0 until entries.length()) {
          val item = entries.optJSONObject(index) ?: continue
          val downloadUrl = item.optString("url").trim()
          if (downloadUrl.isEmpty()) continue
          if (!isSkyDownloadUrl(downloadUrl)) {
            sawNonSkyEntry = true
            continue
          }
          val packageName = item.optString("packageName").ifBlank { item.optString("name") }.trim()
          if (packageName.isEmpty() || !seen.add(packageName)) continue
          add(
            SkyProvider(
              repoUrl = url,
              packageName = packageName,
              name = item.optString("name").ifBlank { packageName },
              version = item.optInt("version", 0),
              downloadUrl = downloadUrl,
              description = item.optString("description").ifBlank { null },
              categories = stringArray(item.optJSONArray("categories")),
              enabled = false,
            ),
          )
        }
      }
    }
    if (providers.isEmpty()) {
      throw NotASkyStreamRepo(
        if (sawNonSkyEntry) "That collection does not publish SkyStream (.sky) sources."
        else "No providers found in that collection.",
      )
    }
    return SkyRepo(
      url = url,
      name = name,
      description = manifest.optString("description").ifBlank { null },
    ) to providers
  }

  private fun downloadPlugin(entry: SkyProvider): File {
    val safeName = entry.packageName.replace(Regex("[^A-Za-z0-9._-]"), "_") +
      "_" + entry.repoUrl.hashCode().toUInt().toString(16) + ".sky"
    val file = File(pluginDir, safeName)
    if (file.exists()) file.delete()
    http.newCall(Request.Builder().url(entry.downloadUrl).header("User-Agent", "StreamDek/1.0").build())
      .execute()
      .use { response ->
        require(response.isSuccessful) { "Download failed: ${response.code}" }
        file.outputStream().use { out -> response.body.byteStream().copyTo(out) }
      }
    require(file.length() > 0L) { "The download for ${entry.name} was empty." }
    return file
  }

  private fun text(url: String): String =
    http.newCall(Request.Builder().url(url).header("User-Agent", "StreamDek/1.0").build()).execute().use {
      require(it.isSuccessful) { "Request failed: ${it.code}" }
      it.body.string()
    }

  // ── Persistence ────────────────────────────────────────────────────────────────────────────

  private fun save() {
    state = state.copy(updatedAt = System.currentTimeMillis())
    prefs.edit().putString(storageKey, serialize(state)).apply()
    onStateChanged?.invoke(state)
  }

  private fun serialize(value: SkyPluginState): String {
    val root = JSONObject().put("updatedAt", value.updatedAt)
    root.put(
      "repos",
      JSONArray().apply {
        value.repos.forEach {
          put(
            JSONObject()
              .put("url", it.url)
              .put("name", it.name)
              .put("description", it.description)
              .put("enabled", it.enabled),
          )
        }
      },
    )
    root.put(
      "providers",
      JSONArray().apply {
        value.providers.forEach {
          put(
            JSONObject()
              .put("repoUrl", it.repoUrl)
              .put("packageName", it.packageName)
              .put("name", it.name)
              .put("version", it.version)
              .put("downloadUrl", it.downloadUrl)
              .put("description", it.description)
              .put("categories", JSONArray(it.categories))
              .put("enabled", it.enabled)
              .put("installedFilePath", it.installedFilePath),
          )
        }
      },
    )
    return root.toString()
  }

  private fun load(): SkyPluginState {
    val raw = prefs.getString(storageKey, null) ?: return SkyPluginState()
    return runCatching {
      val root = JSONObject(raw)
      val repos = root.optJSONArray("repos") ?: JSONArray()
      val providers = root.optJSONArray("providers") ?: JSONArray()
      SkyPluginState(
        repos = List(repos.length()) { index ->
          val item = repos.getJSONObject(index)
          SkyRepo(
            url = item.getString("url"),
            name = item.optString("name"),
            description = item.optString("description").ifBlank { null },
            enabled = item.optBoolean("enabled", true),
          )
        },
        providers = List(providers.length()) { index ->
          val item = providers.getJSONObject(index)
          SkyProvider(
            repoUrl = item.optString("repoUrl"),
            packageName = item.optString("packageName"),
            name = item.optString("name"),
            version = item.optInt("version"),
            downloadUrl = item.optString("downloadUrl"),
            description = item.optString("description").ifBlank { null },
            categories = stringArray(item.optJSONArray("categories")),
            enabled = item.optBoolean("enabled", false),
            installedFilePath = item.optString("installedFilePath").ifBlank { null },
          )
        },
        updatedAt = root.optLong("updatedAt", 0L),
      )
    }.getOrDefault(SkyPluginState())
  }
}

// ── Bundle + parsing helpers ────────────────────────────────────────────────────────────────

/** True for a plugin entry that points at a SkyStream bundle rather than a CloudStream `.cs3`. */
internal fun isSkyDownloadUrl(url: String): Boolean =
  url.substringBefore('?').substringBefore('#').trim().endsWith(".sky", ignoreCase = true)

private fun stringArray(values: JSONArray?): List<String> = buildList {
  val source = values ?: return@buildList
  for (index in 0 until source.length()) source.optString(index).takeIf { it.isNotBlank() }?.let(::add)
}

/** Reads `plugin.json` and `plugin.js` out of a `.sky` zip. */
internal fun readBundle(file: File): SkyBundle = ZipFile(file).use { zip ->
  fun entryText(name: String): String? = zip.getEntry(name)?.let { entry ->
    zip.getInputStream(entry).bufferedReader().use { it.readText() }
  }
  val manifestJson = entryText("plugin.json")
    ?: throw IllegalStateException("No plugin.json inside ${file.name} — is this really a .sky plugin?")
  val script = entryText("plugin.js")
    ?: throw IllegalStateException("No plugin.js inside ${file.name}.")
  SkyBundle(manifestJson = manifestJson, script = script)
}

/**
 * The message a plugin sent back when it declined, or null when it succeeded.
 *
 * Kept apart from [parseSkyStreams] so that stays a pure function: these plugins routinely answer
 * "no streams" for perfectly ordinary reasons (no IMDb id, nothing indexed) and the reason is only
 * worth a log line, not an error shown to the viewer.
 */
internal fun skyStreamError(raw: String): String? {
  val root = runCatching { JSONObject(raw) }.getOrNull() ?: return "Unreadable plugin reply"
  if (root.optBoolean("success", false)) return null
  return root.optString("error").takeIf { it.isNotBlank() } ?: "No streams returned"
}

/**
 * Turns a plugin's `getSettings()` reply into the shared field model.
 *
 * SkyStream names the caption `title` where StreamDek's own plugins use `label`, and spells the
 * boolean control `switch`/`bool` rather than `toggle`; both spellings are accepted so one dialog
 * can render either plugin system.
 */
internal fun parseSkySettingsSchema(raw: String): List<PluginSettingField> {
  val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val label = item.optString("title").ifBlank { item.optString("label") }.ifBlank { continue }
      val type = when (item.optString("type").trim().lowercase()) {
        "switch", "bool", "boolean", "toggle" -> "toggle"
        "select", "dropdown", "list" -> "select"
        "header", "section" -> "header"
        else -> "text"
      }
      val options = buildList {
        item.optJSONArray("options")?.let { source ->
          for (optionIndex in 0 until source.length()) {
            when (val option = source.opt(optionIndex)) {
              is JSONObject -> {
                val value = option.optString("value").ifBlank { option.optString("label") }
                if (value.isNotBlank()) add(PluginSettingOption(option.optString("label").ifBlank { value }, value))
              }
              is String -> if (option.isNotBlank()) add(PluginSettingOption(option, option))
            }
          }
        }
      }
      add(
        PluginSettingField(
          type = type,
          key = item.optString("key").ifBlank { null },
          label = label,
          description = item.optString("description").ifBlank { null },
          placeholder = item.optString("placeholder").ifBlank { null },
          defaultValue = if (item.isNull("defaultValue")) null else item.opt("defaultValue"),
          isPassword = item.optBoolean("isPassword", false) ||
            item.optString("key").contains("api_key", ignoreCase = true) ||
            item.optString("key").contains("token", ignoreCase = true),
          options = options,
        ),
      )
    }
  }
}

internal fun normalizeSkyType(value: String): String =
  if (value.trim().lowercase() in setOf("tv", "series", "show")) "series" else "movie"

/** Pulls the info-hash out of a magnet URI, or null for a plain link. */
internal fun skyMagnetInfoHash(url: String): String? {
  if (!url.startsWith("magnet:", ignoreCase = true)) return null
  return Regex("""btih:([A-Fa-f0-9]{32,40})""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
}

/**
 * Turns one `loadStreams` reply into [AddonStream]s.
 *
 * The reply is `{success, data:[…]}`, where each entry is the plugin's own `StreamResult`
 * — `{url, quality, source, headers}`. A magnet is split into an info-hash so it flows through
 * the same debrid resolution as an add-on torrent result; anything else is a direct URL.
 */
internal fun parseSkyStreams(raw: String, provider: SkyProvider): List<AddonStream> {
  val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
  if (!root.optBoolean("success", false)) return emptyList()
  val data = root.optJSONArray("data") ?: return emptyList()
  return buildList {
    for (index in 0 until data.length()) {
      val item = data.optJSONObject(index) ?: continue
      val url = item.optString("url").trim().takeIf { it.isNotEmpty() } ?: continue
      val infoHash = skyMagnetInfoHash(url)
      val quality = item.optString("quality").ifBlank { null }?.takeIf { !it.equals("Unknown", true) }
      val source = item.optString("source").ifBlank { null }
      val headers = buildMap<String, String> {
        item.optJSONObject("headers")?.let { headerJson ->
          headerJson.keys().forEach { key ->
            headerJson.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
          }
        }
      }
      add(
        AddonStream(
          addonId = "sky:${provider.packageName}",
          addonName = provider.name,
          name = source ?: provider.name,
          title = source ?: provider.name,
          description = null,
          url = if (infoHash == null) url else null,
          infoHash = infoHash,
          fileIdx = null,
          filename = null,
          quality = quality,
          size = null,
          cachedBy = emptyList(),
          requestHeaders = headers,
          source = provider.name,
        ),
      )
    }
  }
}

/**
 * Runs a `.sky` bundle's `plugin.js` in QuickJS.
 *
 * Separate from [StreamDekPluginManager]'s runner because the contracts differ in every respect
 * that matters: SkyStream scripts assign onto `globalThis` rather than `module.exports`, take
 * their input as a JSON string, and reply through a callback instead of returning. The sandbox
 * itself — QuickJS, an HTTP bridge, a result/error pair, a timeout — is the same idea.
 */
internal object SkyStreamPluginRuntime {
  suspend fun loadStreams(
    bundle: SkyBundle,
    payload: String,
    preferences: Map<String, String>,
    http: OkHttpClient,
    timeoutMs: Long,
    log: (String) -> Unit,
  ): String = run(bundle, SKY_DRIVER.replace("__SKY_REQUEST__", JSONObject.quote(payload)), preferences, http, timeoutMs, log)

  /** The plugin's own settings schema, as a JSON array. Empty when it declares none. */
  suspend fun getSettings(
    bundle: SkyBundle,
    preferences: Map<String, String>,
    http: OkHttpClient,
    timeoutMs: Long,
    log: (String) -> Unit,
  ): String = run(bundle, SKY_SETTINGS_DRIVER, preferences, http, timeoutMs, log)

  private suspend fun run(
    bundle: SkyBundle,
    driver: String,
    preferences: Map<String, String>,
    http: OkHttpClient,
    timeoutMs: Long,
    log: (String) -> Unit,
  ): String = withTimeout(timeoutMs) {
    val deferred = CompletableDeferred<String>()
    quickJs(Dispatchers.Default) {
      function("__sky_log") { args ->
        log(args.getOrNull(0)?.toString().orEmpty())
        null
      }
      function("__sky_pref") { args ->
        preferences[args.getOrNull(0)?.toString().orEmpty()]
      }
      function("__sky_result") { args ->
        deferred.complete(args.getOrNull(0)?.toString() ?: "{}")
        null
      }
      function("__sky_error") { args ->
        deferred.completeExceptionally(
          IllegalStateException(args.getOrNull(0)?.toString() ?: "SkyStream plugin failed"),
        )
        null
      }
      function("__sky_fetch") { args ->
        val url = args.getOrNull(0)?.toString().orEmpty()
        val method = args.getOrNull(1)?.toString()?.uppercase() ?: "GET"
        val headerJson = runCatching { JSONObject(args.getOrNull(2)?.toString() ?: "{}") }.getOrDefault(JSONObject())
        val body = args.getOrNull(3)?.toString().orEmpty()
        require(url.startsWith("http://") || url.startsWith("https://")) { "Only HTTP(S) is allowed." }
        val request = Request.Builder().url(url)
        headerJson.keys().forEach { key ->
          headerJson.optString(key).takeIf { it.isNotBlank() }?.let { request.header(key, it) }
        }
        if (headerJson.keys().asSequence().none { it.equals("User-Agent", true) }) {
          request.header("User-Agent", "StreamDek/1.0")
        }
        val requestBody = if (method == "GET" || method == "HEAD") {
          null
        } else {
          body.toRequestBody(headerJson.optString("Content-Type").toMediaTypeOrNull())
        }
        runBlocking(Dispatchers.IO) {
          // A transport failure is reported as a response, not thrown. Several of these plugins
          // fire an analytics beacon before scraping, and on any device with DNS filtering that
          // host resolves to 0.0.0.0 — throwing meant one blocked tracker took the whole provider
          // down with it. Handing back a failed response lets a plugin that ignores the result
          // (which is all of them, for a beacon) carry on, and one that checks `ok` see the truth.
          runCatching {
            http.newCall(request.method(method, requestBody).build()).execute().use { response ->
              val responseHeaders = JSONObject()
              response.headers.names().forEach { name ->
                responseHeaders.put(name.lowercase(), response.header(name).orEmpty())
              }
              JSONObject()
                .put("ok", response.isSuccessful)
                .put("status", response.code)
                .put("url", response.request.url.toString())
                .put("headers", responseHeaders)
                .put("body", response.body.string())
                .toString()
            }
          }.getOrElse { failure ->
            JSONObject()
              .put("ok", false)
              .put("status", 0)
              .put("url", url)
              .put("headers", JSONObject())
              .put("body", "")
              .put("error", failure.message.orEmpty())
              .toString()
          }
        }
      }

      // Every evaluate ends in `void 0`. A script's completion value is whatever its last
      // statement evaluated to, and QuickJS marshals that back to Kotlin — a `.sky` bundle ends
      // with `Object.assign(globalThis, PluginModule)`, which returns globalThis, and globalThis
      // refers to itself through window/self/global. Letting that escape fails the whole call
      // with "TypeError: circular reference" before the plugin is ever asked for anything.
      evaluate<Any?>(SKY_HOST_SHIM + "globalThis.manifest=" + bundle.manifestJson + ";void 0;")
      evaluate<Any?>(bundle.script + "\n;void 0;")
      evaluate<Any?>(driver)
      deferred.await()
    }
  }

  /**
   * Drives one plugin through the whole lookup, inside the plugin's own JS context.
   *
   * These are three-stage, title-based scrapers, not id lookups: `search` finds the title on the
   * provider's site, `load` opens that page and pulls out the download links (grouped per episode
   * for a series), and only then does `loadStreams` turn links into playable URLs. Calling
   * `loadStreams` directly — which is what StreamDek did first — hands it something it was never
   * given, and every provider bar Torrentio answered with nothing. StreamFlix was worse than
   * nothing: it concatenates whatever it is given onto its base URLs, so it produced the same
   * handful of dead links for every title.
   *
   * The orchestration lives here rather than in Kotlin so the three stages share one QuickJS
   * session and one parse of the bundle, instead of three round trips.
   *
   * What `loadStreams` expects differs per plugin and there is no declaration to read, so the
   * payload is chosen by shape, most specific first: the links a `load` produced, else an object
   * carrying an IMDb id (Torrentio's form), else the content URL as a plain string (StreamFlix's).
   * Whatever is chosen is passed as a string — every implementation seen either parses it as JSON
   * or treats it as a URL.
   */
  private val SKY_DRIVER = """
    (function(){
      var replied=false;
      function reply(r){
        if (replied) return; replied=true;
        try { __sky_result(JSON.stringify(r||{})); }
        catch(e){ __sky_error('Could not read the plugin reply: '+String(e&&e.message||e)); }
      }
      // Entry points answer through a callback and may also reject; whichever happens first wins.
      function call(fn, arg){
        return new Promise(function(resolve){
          var settled=false;
          function done(v){ if(!settled){ settled=true; resolve(v); } }
          try {
            Promise.resolve(fn(arg, done)).catch(function(e){ done({success:false, error:String(e&&e.message||e)}); });
          } catch(e){ done({success:false, error:String(e&&e.message||e)}); }
        });
      }
      function norm(s){ return String(s==null?'':s).toLowerCase().replace(/[^a-z0-9]+/g,' ').trim(); }

      (async function(){
        var req = JSON.parse(__SKY_REQUEST__);
        if (typeof globalThis.loadStreams !== 'function') throw new Error('Plugin does not provide loadStreams');

        var target = null;
        if (typeof globalThis.search === 'function') {
          var found = await call(globalThis.search, req.title);
          if (found && found.success === false) return reply(found);
          var items = (found && found.data) || [];
          var want = norm(req.title);
          function pick(test){ for (var i=0;i<items.length;i++) if (test(items[i])) return items[i]; return null; }
          target = pick(function(it){ return norm(it.title)===want && req.year && Number(it.year)===Number(req.year); })
                || pick(function(it){ return norm(it.title)===want; })
                || pick(function(it){ return norm(it.title).indexOf(want)>=0 || want.indexOf(norm(it.title))>=0; })
                || items[0] || null;
          if (!target) return reply({success:true, data:[]});
        }

        var detail = null;
        if (target && typeof globalThis.load === 'function') {
          var loaded = await call(globalThis.load, target.url);
          detail = (loaded && loaded.data) || null;
        }

        var source = detail;
        if (req.season && req.episode) {
          var episodes = (detail && detail.episodes) || [];
          var match = null;
          for (var i=0;i<episodes.length;i++) {
            if (Number(episodes[i].season)===Number(req.season) && Number(episodes[i].episode)===Number(req.episode)) { match = episodes[i]; break; }
          }
          // A series with an episode list that does not contain this episode has nothing to offer;
          // handing the show-level payload over would scrape the wrong thing.
          if (!match && episodes.length > 0) return reply({success:true, data:[]});
          source = match || detail;
        }

        var payload;
        if (source && source.links) payload = JSON.stringify(source.links);
        else if (source && source.imdbId) payload = JSON.stringify(source);
        else if (target && target.url) payload = String(target.url);
        else if (source) payload = JSON.stringify(source);
        else payload = JSON.stringify(req);

        reply(await call(globalThis.loadStreams, payload));
      })().catch(function(e){ __sky_error(String(e&&e.message||e)); });
    })();void 0;
  """.trimIndent()

  /**
   * `getSettings()` returns its field list directly rather than through a callback, but it is
   * wrapped in Promise.resolve anyway so a plugin that made it async still works.
   */
  private val SKY_SETTINGS_DRIVER = """
    (function(){
      try {
        var f = globalThis.getSettings;
        if (typeof f !== 'function') { __sky_result('[]'); return; }
        Promise.resolve(f())
          .then(function(r){ __sky_result(JSON.stringify(r||[])); })
          .catch(function(e){ __sky_error(String(e&&e.message||e)); });
      } catch(e){ __sky_error(String(e&&e.message||e)); }
    })();void 0;
  """.trimIndent()

  /**
   * The globals a `.sky` script expects from its host.
   *
   * `http_get`/`http_post` are synchronous here and the scripts `await` them, which is harmless —
   * awaiting a non-promise resolves immediately. `getPreference` is the simple path the bundles
   * try after `_dartAsyncCall`; that first attempt is `typeof`-guarded, so leaving the Flutter
   * bridge undefined costs nothing. `StreamResult` and `MultimediaItem` are plain carriers: the
   * script constructs them and the host reads the fields back off, so behaviour lives in neither.
   */
  private val SKY_HOST_SHIM = """
    globalThis.window=globalThis; globalThis.self=globalThis; globalThis.global=globalThis;
    globalThis.console={log:function(){},warn:function(){},error:function(){
      __sky_log([].slice.call(arguments).map(String).join(' '));
    }};
    function StreamResult(o){ o=o||{}; this.url=o.url; this.quality=o.quality; this.source=o.source;
      this.headers=o.headers||{}; this.title=o.title; this.name=o.name; this.size=o.size; }
    function MultimediaItem(o){ o=o||{}; for (var k in o) if (Object.prototype.hasOwnProperty.call(o,k)) this[k]=o[k]; }
    globalThis.StreamResult=StreamResult; globalThis.MultimediaItem=MultimediaItem;
    globalThis.getPreference=function(k){ var v=__sky_pref(String(k)); return (v===undefined?null:v); };
    globalThis.__sky_request=function(u,m,h,b){ return JSON.parse(__sky_fetch(String(u),m,JSON.stringify(h||{}),String(b||''))); };
    globalThis.http_get=function(u,h){ return globalThis.__sky_request(u,'GET',h,''); };
    globalThis.http_post=function(u,h,b){ return globalThis.__sky_request(u,'POST',h,b); };
    globalThis.setTimeout=function(fn){ if (typeof fn==='function') fn(); return 0; };
    globalThis.clearTimeout=function(){};
    globalThis.setInterval=function(){ return 0; };
    globalThis.clearInterval=function(){};

    // QuickJS ships no URL/URLSearchParams, and scrapers reach for them constantly to join a
    // relative href onto a base or pull a query value out — HDHub4U failed with exactly
    // "URL is not defined". This covers parsing, relative resolution and the query accessors;
    // it is not the full WHATWG algorithm (no IDN, no percent-encoding normalisation).
    (function(){
      if (typeof globalThis.URLSearchParams !== 'function') {
        function SP(init){
          this._p=[];
          if (typeof init === 'string') {
            String(init).replace(/^\?/,'').split('&').forEach(function(kv){
              if (!kv) return;
              var i=kv.indexOf('=');
              var k=i<0?kv:kv.slice(0,i), v=i<0?'':kv.slice(i+1);
              this._p.push([decodeURIComponent(k.replace(/\+/g,' ')), decodeURIComponent(v.replace(/\+/g,' '))]);
            }, this);
          } else if (init && typeof init === 'object') {
            for (var k in init) if (Object.prototype.hasOwnProperty.call(init,k)) this._p.push([k, String(init[k])]);
          }
        }
        SP.prototype.get=function(k){ for (var i=0;i<this._p.length;i++) if (this._p[i][0]===k) return this._p[i][1]; return null; };
        SP.prototype.getAll=function(k){ return this._p.filter(function(p){return p[0]===k}).map(function(p){return p[1]}); };
        SP.prototype.has=function(k){ return this.get(k)!==null; };
        SP.prototype.append=function(k,v){ this._p.push([k,String(v)]); };
        SP.prototype.delete=function(k){ this._p=this._p.filter(function(p){return p[0]!==k}); };
        SP.prototype.set=function(k,v){ this.delete(k); this.append(k,v); };
        SP.prototype.forEach=function(f,t){ var s=this; this._p.slice().forEach(function(p){ f.call(t,p[1],p[0],s) }); };
        SP.prototype.toString=function(){ return this._p.map(function(p){ return encodeURIComponent(p[0])+'='+encodeURIComponent(p[1]) }).join('&'); };
        globalThis.URLSearchParams=SP;
      }
      if (typeof globalThis.URL !== 'function') {
        function resolve(input, base){
          input=String(input==null?'':input);
          if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(input)) return input;
          if (!base) throw new TypeError('Invalid URL: '+input);
          var b=String(base);
          var m=b.match(/^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/([^\/?#]*)([^?#]*)/);
          if (!m) throw new TypeError('Invalid base URL: '+base);
          var origin=m[1]+'//'+m[2];
          if (input.indexOf('//')===0) return m[1]+input;
          if (input.charAt(0)==='/') return origin+input;
          if (input.charAt(0)==='?') return origin+(m[3]||'/')+input;
          if (input.charAt(0)==='#') return b.split('#')[0]+input;
          return origin+(m[3]||'/').replace(/[^\/]*${'$'}/,'')+input;
        }
        function U(input, base){
          var href=resolve(input, base);
          var m=href.match(/^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/([^\/?#]*)([^?#]*)(\?[^#]*)?(#.*)?${'$'}/);
          if (!m) throw new TypeError('Invalid URL: '+input);
          this.protocol=m[1]; this.host=m[2]; this.pathname=m[3]||'/';
          this.search=m[4]||''; this.hash=m[5]||''; this.href=href;
          var hostPort=m[2].split('@').pop();
          var colon=hostPort.lastIndexOf(':');
          this.hostname = colon>0 ? hostPort.slice(0,colon) : hostPort;
          this.port = colon>0 ? hostPort.slice(colon+1) : '';
          this.origin=this.protocol+'//'+this.host;
          this.searchParams=new globalThis.URLSearchParams(this.search);
        }
        U.prototype.toString=function(){ return this.href; };
        globalThis.URL=U;
      }
    })();
  """.trimIndent() + "\n"
}

object SkyStreamPlugins {
  lateinit var manager: SkyStreamPluginManager
    private set
  val isInitialized: Boolean get() = ::manager.isInitialized
  fun initialize(context: Context) {
    if (!::manager.isInitialized) manager = SkyStreamPluginManager(context.applicationContext)
  }
}
