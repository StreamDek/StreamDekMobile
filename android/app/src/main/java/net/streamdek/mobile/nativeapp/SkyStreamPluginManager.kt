package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
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
 *   plugin.js     a bundled script that assigns getHome/search/load/loadStreams onto globalThis
 *
 * so it runs in QuickJS ([SkyStreamRuntime]), not a ClassLoader.
 *
 * Each switched-on source is offered to the rest of the app as a CloudStream provider
 * ([SkyStreamMainApi], through [mainApis]), so it takes part everywhere a `.cs3` does: Home rows,
 * the Fuse page, detail pages, live channels and stream lookups.
 *
 * Providers default to *disabled* on install, matching the CloudStream manager: one aggregate can
 * list dozens, and downloading every bundle because a URL was pasted would be slow and unwanted.
 */
data class SkyRepo(
  val url: String,
  val name: String,
  val description: String?,
  val enabled: Boolean = true,
  val favourite: Boolean = false,
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
  /** Mirror addresses the collection lists for the site, which the user can switch between. */
  val domains: List<SkyDomain> = emptyList(),
  val authors: List<String> = emptyList(),
  val languages: List<String> = emptyList(),
)

data class SkyDomain(val name: String, val url: String)

/**
 * What a SkyStream source can be configured with, beyond the fields its script declares: the site
 * address (with any mirrors its collection lists) and, for a plugin that splits into several
 * sources, which of them are switched on. SkyStream's own settings screen offers the same three.
 */
data class SkySettingsSchema(
  val fields: List<PluginSettingField>,
  /** The script's own field for its address, when it declares one; the address control replaces it. */
  val addressField: PluginSettingField?,
  val defaultAddress: String,
  val address: String?,
  val domains: List<SkyDomain>,
  /** (id, name, on) of each sub-provider. */
  val subProviders: List<Triple<String, String, Boolean>>,
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
  companion object {
    private const val TAG = "SkyStreamPlugins"
    /**
     * Setting keys the host owns rather than the script. Stored among the script's own values,
     * under the same names SkyStream uses, but never shown as fields of their own.
     */
    const val ADDRESS_KEY = "_base_url"
    const val SUB_PROVIDER_ENABLED_PREFIX = "_provider_enabled_"
    fun isHostSettingKey(key: String) = key == ADDRESS_KEY || key.startsWith(SUB_PROVIDER_ENABLED_PREFIX)
    private const val LEGACY_STORAGE_KEY = "state"
    const val HOME_TIMEOUT_MS = 60_000L
    const val SEARCH_TIMEOUT_MS = 30_000L
    const val LOAD_TIMEOUT_MS = 30_000L
    const val STREAM_TIMEOUT_MS = 45_000L
    private const val SMALL_CALL_TIMEOUT_MS = 20_000L
    /** How long one getHome() reply serves every row cut from it. */
    private const val HOME_CACHE_MS = 10 * 60_000L
    /** QuickJS runtimes alive at once, across every source. */
    private const val MAX_CONCURRENT_CALLS = 6
  }

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("streamdek_sky_plugins", Context.MODE_PRIVATE)
  private val http = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  // Unlike a .cs3 this is never handed to a ClassLoader — the zip is opened and its plugin.js read
  // as text — so ordinary private storage is fine and avoids relying on external storage existing.
  private val pluginDir: File by lazy { File(context.applicationContext.filesDir, "sky_plugins").apply { mkdirs() } }
  private val playlistDir: File by lazy { File(context.applicationContext.cacheDir, "sky_hls").apply { mkdirs() } }

  /** Work that outlives the call that started it: a Home row timing out must not waste the fetch. */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val callGate = Semaphore(MAX_CONCURRENT_CALLS)
  private val bundles = ConcurrentHashMap<String, SkyBundle>()
  private val homeCache = ConcurrentHashMap<String, Pair<Long, Map<String, List<JSONObject>>>>()
  private val homeInFlight = ConcurrentHashMap<String, Deferred<Map<String, List<JSONObject>>>>()
  private val probeLock = Mutex()
  @Volatile private var adapters: Pair<String, List<MainAPI>>? = null

  private var storageKey = LEGACY_STORAGE_KEY

  @Volatile var state: SkyPluginState = load()
    private set
  var onStateChanged: ((SkyPluginState) -> Unit)? = null

  /**
   * The sources [mainApis] answers with changed, or one of them learnt its Home rows. The app
   * re-offers Home rows and refetches the switched-on ones, as it does for CloudStream.
   */
  @Volatile var onProvidersChanged: (() -> Unit)? = null

  private fun notifyProvidersChanged() {
    runCatching { onProvidersChanged?.invoke() }.onFailure { Log.w(TAG, "Provider change listener failed", it) }
  }

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
    // No onStateChanged here: that pushes this device's copy to the account, and on a profile
    // switch the account's copy has not been read yet — pushing first would overwrite changes made
    // on another device with whatever this one last had.
    notifyProvidersChanged()
    restoreEnabledBundles()
  }

  /** How many sources one owner's collections hold, without switching to that owner. */
  fun providerCountFor(ownerKey: String): Int = countProviders(prefs.getString("state:${ownerKey.ifBlank { "guest" }}", null))

  /**
   * Joins one owner's collections into another's, without switching the selected profile.
   *
   * The same merge the JS collections use: by collection URL and by the package name inside it,
   * with the destination's copy of anything shared kept. A `.sky` already on disk is referenced by
   * path from both sides, so nothing is downloaded again.
   */
  fun mergeProfileStorageInto(fromOwnerKey: String, toOwnerKey: String): Boolean {
    val fromKey = "state:${fromOwnerKey.ifBlank { "guest" }}"
    val toKey = "state:${toOwnerKey.ifBlank { "guest" }}"
    if (fromKey == toKey) return false
    val incoming = prefs.getString(fromKey, null)?.takeIf { it.isNotBlank() && it != "{}" } ?: return false
    val merged = mergePluginStateDocuments(prefs.getString(toKey, null), incoming) ?: return false
    val committed = prefs.edit().putString(toKey, merged).commit()
    // The running profile is the destination when a migration happens right after signing in, so
    // its sources have to come up now rather than at the next profile switch.
    if (committed && toKey == storageKey) {
      state = load()
      onStateChanged?.invoke(state)
      notifyProvidersChanged()
      restoreEnabledBundles()
    }
    return committed
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
          existing.installedFilePath?.let { path -> bundles.remove(path); runCatching { File(path).delete() } }
          entry.copy(enabled = existing.enabled, installedFilePath = null)
        } else {
          entry.copy(enabled = existing.enabled, installedFilePath = existing.installedFilePath)
        }
      }
      val existingRepo = state.repos.firstOrNull { it.url == url }
      state = state.copy(
        repos = state.repos.map { if (it.url == url) repo.copy(enabled = existingRepo?.enabled ?: true, favourite = existingRepo?.favourite ?: false) else it },
        providers = state.providers.filterNot { it.repoUrl == url } + merged,
      )
      save()
      // An updated plugin that stays switched on is fetched again and asked what it offers now.
      restoreEnabledBundles()
    }
  }

  fun removeRepo(url: String) {
    state.providers.filter { it.repoUrl == url }.forEach { entry ->
      entry.installedFilePath?.let { path -> bundles.remove(path); runCatching { File(path).delete() } }
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
    if (enabled) probeMissing()
  }

  fun toggleRepoFavourite(url: String) {
    state = state.copy(repos = state.repos.map { if (it.url == url) it.copy(favourite = !it.favourite) else it })
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
        bundles[file.absolutePath] = readBundle(file)
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
        // Its Home rows are named by what getHome() answers, so ask now rather than leave the
        // source with nothing to offer until something else happens to call it.
        probeMissing()
      }
    }

  /**
   * Fetches any switched-on source whose bundle is missing from disk — cleared app data, or a
   * version bump in [refreshRepo] — then probes what they offer.
   */
  private fun restoreEnabledBundles() {
    scope.launch {
      val missing = activeProviders().filter { entry -> entry.installedFilePath?.let(::File)?.let { it.exists() && it.length() > 0L } != true }
      if (missing.isNotEmpty()) {
        val paths = missing.mapNotNull { entry ->
          runCatching { downloadPlugin(entry) }
            .onFailure { Log.w(TAG, "Could not fetch ${entry.name}", it) }
            .getOrNull()
            ?.let { file -> (entry.repoUrl to entry.packageName) to file.absolutePath }
        }.toMap()
        if (paths.isNotEmpty()) {
          state = state.copy(providers = state.providers.map { entry -> paths[entry.repoUrl to entry.packageName]?.let { entry.copy(installedFilePath = it) } ?: entry })
          // Where this device keeps a file is not something to sync, nor a change to stamp.
          persistLocal()
          notifyProvidersChanged()
        }
      }
      probeMissing()
    }
  }

  /** Providers that are switched on, inside collections that are switched on. */
  fun activeProviders(): List<SkyProvider> {
    val enabledRepos = state.repos.filter { it.enabled }.associateBy { it.url }
    return state.providers.filter { it.enabled && it.repoUrl in enabledRepos }
      .sortedWith(compareByDescending<SkyProvider> { enabledRepos[it.repoUrl]?.favourite == true }.thenBy { it.name.lowercase() })
  }

  // ── Sources as CloudStream providers ─────────────────────────────────────────────────────

  /**
   * Every switched-on source as a CloudStream provider, sub-providers included.
   *
   * @param reservedNames names already taken by loaded CloudStream providers. A provider's name is
   * its identity throughout the CloudStream integration — row ids, cached pages, stream origins —
   * so a source that shares one (two collections both carry "Castle TV") is told apart by its
   * collection rather than left to shadow the other.
   */
  fun mainApis(reservedNames: Set<String> = emptySet()): List<MainAPI> {
    val sources = activeSources()
    val signature = sources.joinToString("\n") { it.key + "@" + it.version + "@" + it.filePath + "@" + it.baseUrl } +
      "\n#" + reservedNames.sorted().joinToString("|")
    adapters?.takeIf { it.first == signature }?.let { lastProviders = it.second; return it.second }
    val repoNames = state.repos.associate { it.url to it.name }
    val baseNames = sources.map { it.displayName }
    val taken = reservedNames.toMutableSet()
    val built = sources.mapIndexed { index, source ->
      var name = baseNames[index]
      if (name in taken || baseNames.count { it == name } > 1) {
        // By collection where that tells them apart; by package where one collection (a mega
        // repo) carries two plugins of the same name.
        val sameCollection = sources.indices.count { baseNames[it] == name && sources[it].repoUrl == source.repoUrl } > 1
        name = "$name · ${if (sameCollection) source.packageName else repoNames[source.repoUrl]?.takeIf { it.isNotBlank() } ?: source.packageName}"
      }
      while (name in taken) name += " (SkyStream)"
      taken += name
      SkyStreamMainApi(source, name, this)
    }
    adapters = signature to built
    lastProviders = built
    return built
  }

  /**
   * What [mainApis] answered most recently, without working it out again — for lookups made once
   * per row on screen, such as which collection a stream came from.
   */
  @Volatile var lastProviders: List<MainAPI> = emptyList()
    private set

  /** Collection names by provider name, for [lastProviders]: "SkyStream · <collection>". */
  fun collectionNamesByProvider(): Map<String, String> {
    val repoNames = state.repos.associate { it.url to it.name }
    return lastProviders.filterIsInstance<SkyStreamMainApi>().associate { provider ->
      provider.name to (repoNames[provider.source.repoUrl]?.takeIf { it.isNotBlank() } ?: provider.source.repoUrl)
    }
  }

  /** Sources of switched-on providers: each provider itself, or the sub-providers it listed. */
  internal fun activeSources(): List<SkySource> = activeProviders().flatMap { provider ->
    val path = provider.installedFilePath ?: return@flatMap emptyList()
    val manifest = bundleOrNull(path)?.manifestJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@flatMap emptyList()
    val settings = providerPreferences(provider.packageName)
    val base = SkySource(
      repoUrl = provider.repoUrl,
      packageName = provider.packageName,
      pluginName = provider.name,
      subId = null,
      subName = null,
      baseUrl = settings[ADDRESS_KEY]?.takeIf { it.isNotBlank() } ?: manifest.optString("baseUrl"),
      categories = provider.categories.ifEmpty { stringArray(manifest.optJSONArray("categories")) },
      language = stringArray(manifest.optJSONArray("languages")).firstOrNull() ?: "en",
      filePath = path,
      version = provider.version,
    )
    val subs = subProviders(provider)
    if (subs.isEmpty()) listOf(base) else subs
      // Switched off in the source's settings; on unless said otherwise, as in SkyStream.
      .filter { (id, _, _) -> settings[SUB_PROVIDER_ENABLED_PREFIX + id] != "false" }
      .map { (id, name, baseUrl) ->
        base.copy(subId = id, subName = name, baseUrl = baseUrl ?: base.baseUrl, providerId = if (baseUrl == null) id else null)
      }
  }

  /** The rows a source's getHome() answered with last time, by name, in its order. */
  internal fun homeSections(source: SkySource): List<String> =
    prefs.getString("sections:${source.key}", null)?.let { raw -> runCatching { stringArray(JSONArray(raw)) }.getOrNull() }.orEmpty()

  /**
   * A source's getHome() reply, row by row. One call answers every row, so the reply is shared by
   * all of them for a while — and fetched once however many rows ask at the same moment.
   */
  internal suspend fun home(source: SkySource): Map<String, List<JSONObject>> {
    homeCache[source.key]?.takeIf { System.currentTimeMillis() - it.first < HOME_CACHE_MS }?.let { return it.second }
    val pending = homeInFlight.computeIfAbsent(source.key) {
      scope.async {
        try {
          fetchHome(source)
        } finally {
          homeInFlight.remove(source.key)
        }
      }
    }
    return pending.await()
  }

  private suspend fun fetchHome(source: SkySource): Map<String, List<JSONObject>> {
    val raw = call(source, "getHome", emptyList(), HOME_TIMEOUT_MS)
    val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
    val rows = LinkedHashMap<String, List<JSONObject>>()
    json.keys().forEach { name ->
      val list = json.optJSONArray(name) ?: return@forEach
      val items = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
      if (name.isNotBlank() && items.isNotEmpty()) rows[name] = items
    }
    homeCache[source.key] = System.currentTimeMillis() to rows
    val names = rows.keys.toList()
    if (names != homeSections(source) || !prefs.contains("sections:${source.key}")) {
      prefs.edit().putString("sections:${source.key}", JSONArray(names).toString()).apply()
      notifyProvidersChanged()
    }
    return rows
  }

  private fun subProvidersKey(provider: SkyProvider) = "subs:${provider.repoUrl}|${provider.packageName}@${provider.version}"

  /** What `getProviders()` answered for this plugin version: (id, name, baseUrl) of each. */
  private fun subProviders(provider: SkyProvider): List<Triple<String, String, String?>> {
    val raw = prefs.getString(subProvidersKey(provider), null) ?: return emptyList()
    return runCatching {
      val list = JSONArray(raw)
      (0 until list.length()).mapNotNull { index ->
        val item = list.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id").ifBlank { return@mapNotNull null }
        Triple(id, item.optString("name").ifBlank { id }, item.optString("baseUrl").ifBlank { null })
      }
    }.getOrDefault(emptyList())
  }

  /**
   * Asks each switched-on source that has not been asked yet what it is made of: the
   * sub-providers it splits into (SkyStream's `getProviders()`, fixed per plugin version) and the
   * rows its `getHome()` offers. Both are remembered, so this is a one-off per source.
   */
  fun probeMissing() {
    scope.launch {
      probeLock.withLock {
        activeProviders().forEach { provider ->
          if (prefs.contains(subProvidersKey(provider))) return@forEach
          val path = provider.installedFilePath ?: return@forEach
          val base = SkySource(provider.repoUrl, provider.packageName, provider.name, null, null, "", provider.categories, "en", path, provider.version)
          val subs = runCatching { call(base, "getProviders", emptyList(), SMALL_CALL_TIMEOUT_MS) }
            .onFailure { Log.d(TAG, "${provider.name} lists no sub-providers: ${it.message?.lineSequence()?.firstOrNull()}") }
            .getOrNull()
            ?.let { raw -> runCatching { JSONArray(raw) }.getOrNull() }
            ?: JSONArray()
          prefs.edit().putString(subProvidersKey(provider), subs.toString()).apply()
          if (subs.length() > 0) notifyProvidersChanged()
        }
        // Each source's settings, learnt once per version, so the web portal can offer them.
        var learnt = false
        activeProviders().filter { it.installedFilePath != null && !prefs.contains(schemaKey(it)) }.forEach { provider ->
          settingsSchema(provider).onSuccess { learnt = true }
        }
        // Sent without a new stamp: nothing the user chose has changed, so no other device needs
        // to take anything, but the account copy should carry what the portal needs to draw.
        if (learnt) onStateChanged?.invoke(state)
        activeSources().filter { !prefs.contains("sections:${it.key}") }.forEach { source ->
          runCatching { home(source) }.onFailure {
            Log.w(TAG, "${source.displayName} home could not be read: ${it.message}")
            // Remembered as empty so a source whose site is down is not asked again on every
            // launch; a Home row asking for it later still retries and fills the list in.
            prefs.edit().putString("sections:${source.key}", "[]").apply()
          }
        }
      }
    }
  }

  /** Runs one entry point of a source's plugin. The reply is JSON text, or null for nothing. */
  internal suspend fun call(source: SkySource, function: String, args: List<Any?>, timeoutMs: Long): String? {
    val bundle = withContext(Dispatchers.IO) { bundleFor(source) }
    val manifest = runCatching { JSONObject(bundle.manifestJson) }.getOrDefault(JSONObject())
    // The address the user picked, or a sub-provider's own; and a sub-provider without one is told
    // which it is by id — the way SkyStream sets its manifest up.
    if (source.baseUrl.isNotBlank()) manifest.put("baseUrl", source.baseUrl)
    source.providerId?.let { manifest.put("providerId", it) }
    return callGate.withPermit {
      SkyStreamRuntime.invoke(
        bundle = bundle,
        manifestJson = manifest.toString(),
        function = function,
        args = args,
        storage = storageFor(source.packageName),
        http = http,
        timeoutMs = timeoutMs,
        log = { message -> Log.d(TAG, "[${source.displayName}] $message") },
      )
    }
  }

  /** A `magic_m3u8:` playlist, written where the player can open it as a file. */
  internal fun writeLocalPlaylist(content: String): File {
    val digest = MessageDigest.getInstance("SHA-1").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
    val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
    playlistDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { runCatching { it.delete() } }
    return File(playlistDir, "sky_$digest.m3u8").apply { if (!exists()) writeText(content) }
  }

  private fun bundleFor(source: SkySource): SkyBundle {
    bundleOrNull(source.filePath)?.let { return it }
    val provider = state.providers.firstOrNull { it.repoUrl == source.repoUrl && it.packageName == source.packageName }
      ?: throw IllegalStateException("${source.displayName} is no longer installed.")
    val file = downloadPlugin(provider)
    return readBundle(file).also { bundles[source.filePath] = it; bundles[file.absolutePath] = it }
  }

  private fun bundleOrNull(path: String): SkyBundle? = bundles[path] ?: File(path)
    .takeIf { it.exists() && it.length() > 0L }
    ?.let { file -> runCatching { readBundle(file) }.onFailure { Log.w(TAG, "Unreadable bundle $path", it) }.getOrNull() }
    ?.also { bundles[path] = it }

  private fun storageFor(packageName: String) = object : SkyPluginStorage {
    override fun get(key: String): String? = providerPreferences(packageName)[key]
    override fun set(key: String, value: String?) = setProviderPreference(packageName, key, value)
  }

  /**
   * Everything a source can be configured with, as SkyStream's settings screen offers it.
   *
   * Fields come from two places, merged by key with the script winning: `settings` in plugin.json,
   * and what `getSettings()` answers at runtime — Torrentio's debrid picker is only known by asking.
   * The script's address field, if it declares one, is taken out and offered as the address control
   * instead, alongside the mirrors the collection lists.
   */
  suspend fun settingsSchema(provider: SkyProvider): Result<SkySettingsSchema> =
    withContext(Dispatchers.IO) {
      runCatching {
        val path = provider.installedFilePath?.takeIf { File(it).let { file -> file.exists() && file.length() > 0L } }
          ?: downloadPlugin(provider).absolutePath
        val bundle = bundleOrNull(path) ?: readBundle(File(path)).also { bundles[path] = it }
        val manifest = runCatching { JSONObject(bundle.manifestJson) }.getOrDefault(JSONObject())
        val source = SkySource(provider.repoUrl, provider.packageName, provider.name, null, null, "", provider.categories, "en", path, provider.version)
        // A plugin without getSettings() is one with nothing more to configure, not a failure.
        val raw = runCatching { call(source, "getSettings", emptyList(), SMALL_CALL_TIMEOUT_MS) }
          .getOrElse { failure -> if (failure is SkyPluginException) null else throw failure }
        val declared = LinkedHashMap<String, PluginSettingField>()
        val unkeyed = mutableListOf<PluginSettingField>()
        (parseSkySettingsSchema(manifest.optJSONArray("settings")?.toString() ?: "[]") + parseSkySettingsSchema(raw ?: "[]"))
          .forEach { field -> field.key?.let { declared[it] = field } ?: unkeyed.add(field) }
        val all = unkeyed + declared.values
        val addressField = all.firstOrNull { it.type == SKY_ADDRESS_FIELD_TYPE }
        val settings = providerPreferences(provider.packageName)
        SkySettingsSchema(
          fields = all.filter { it.type != SKY_ADDRESS_FIELD_TYPE },
          addressField = addressField,
          defaultAddress = manifest.optString("baseUrl"),
          address = settings[ADDRESS_KEY]?.takeIf { it.isNotBlank() },
          domains = (provider.domains + skyDomains(manifest.optJSONArray("domains"))).distinctBy { it.url },
          subProviders = subProviders(provider).map { (id, name, _) -> Triple(id, name, settings[SUB_PROVIDER_ENABLED_PREFIX + id] != "false") },
        ).also { schema -> prefs.edit().putString(schemaKey(provider), publishedSchema(schema).toString()).apply() }
      }
    }

  private fun schemaKey(provider: SkyProvider) = "schema:${provider.repoUrl}|${provider.packageName}@${provider.version}"

  /**
   * A source's settings as the web portal draws them. The portal has no JavaScript engine to ask a
   * plugin for its fields, so the device that did publishes what it learnt in the synced document,
   * as JS plugin collections already do with their `settingsSchema`.
   */
  private fun publishedSchema(schema: SkySettingsSchema): JSONObject = JSONObject()
    .put("fields", JSONArray().apply { schema.fields.forEach { put(settingFieldJson(it)) } })
    .put("address", schema.addressField?.let { JSONObject().put("label", it.label).put("description", it.description) }
      ?: if (schema.domains.isNotEmpty()) JSONObject() else JSONObject.NULL)
    .put("defaultAddress", schema.defaultAddress)
    .put("domains", JSONArray().apply { schema.domains.forEach { put(JSONObject().put("name", it.name).put("url", it.url)) } })

  /** Stored values for a plugin's own settings, as the dialog reads them — host keys left out. */
  fun providerSettings(packageName: String): Map<String, Any> =
    providerPreferences(packageName).filterKeys { !isHostSettingKey(it) }

  /** Saves the script's own values. Host keys, and anything the dialog was not shown, are kept. */
  fun saveProviderSettings(packageName: String, values: Map<String, Any?>) {
    val json = JSONObject()
    providerPreferences(packageName).filterKeys(::isHostSettingKey).forEach { (key, value) -> json.put(key, value) }
    values.forEach { (key, value) -> if (value != null && !isHostSettingKey(key)) json.put(key, value.toString()) }
    prefs.edit().putString("settings:$storageKey:$packageName", json.toString()).apply()
    settingsChanged(packageName)
  }

  /** The site address (null for the plugin's own) and which sub-providers are switched on. */
  fun saveSourceOptions(packageName: String, address: String?, subProvidersOn: Map<String, Boolean>) {
    val current = providerPreferences(packageName).toMutableMap()
    val clean = address?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    if (clean == null) current.remove(ADDRESS_KEY) else current[ADDRESS_KEY] = clean
    subProvidersOn.forEach { (id, on) -> current[SUB_PROVIDER_ENABLED_PREFIX + id] = if (on) "true" else "false" }
    prefs.edit().putString("settings:$storageKey:$packageName", JSONObject(current as Map<*, *>).toString()).apply()
    settingsChanged(packageName)
  }

  /**
   * A setting changed by the user: rows cut before it are stale (a debrid key, a language and an
   * address all change what a source lists), the set of sources may have changed, and the change
   * is stamped so it syncs to the profile's other devices.
   */
  private fun settingsChanged(packageName: String) {
    homeCache.keys.removeIf { it.split('|').getOrNull(1) == packageName || it.contains("|$packageName") }
    adapters = null
    save()
  }

  /** Values previously stored for this plugin — its own `getSettings()` fields and host keys. */
  private fun providerPreferences(packageName: String): Map<String, String> {
    val raw = prefs.getString("settings:$storageKey:$packageName", null) ?: return emptyMap()
    return runCatching {
      val json = JSONObject(raw)
      buildMap { json.keys().forEach { key -> put(key, json.optString(key)) } }
    }.getOrDefault(emptyMap())
  }

  /** A write from the plugin's own script (`setPreference`): stored, but neither stamped nor synced. */
  fun setProviderPreference(packageName: String, key: String, value: String?) {
    val current = providerPreferences(packageName).toMutableMap()
    if (value.isNullOrBlank()) current.remove(key) else current[key] = value
    prefs.edit().putString("settings:$storageKey:$packageName", JSONObject(current as Map<*, *>).toString()).apply()
  }

  // ── Account sync ─────────────────────────────────────────────────────────────────────────────

  /**
   * This profile's collections as the `skystream` section of its plugin document: what is
   * installed and switched on, and each source's settings. Nothing device-specific — where a
   * bundle sits on disk is this device's business.
   */
  fun snapshotJson(): String {
    val root = JSONObject(serialize(state))
    val providers = root.optJSONArray("providers") ?: JSONArray()
    for (index in 0 until providers.length()) {
      val item = providers.optJSONObject(index) ?: continue
      item.remove("installedFilePath")
      val settings = providerPreferences(item.optString("packageName"))
      if (settings.isNotEmpty()) item.put("settings", JSONObject(settings as Map<*, *>))
      val provider = state.providers.firstOrNull { it.repoUrl == item.optString("repoUrl") && it.packageName == item.optString("packageName") } ?: continue
      prefs.getString(schemaKey(provider), null)?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }?.let { schema ->
        item.put("settingsSchema", schema.optJSONArray("fields") ?: JSONArray())
        if (!schema.isNull("address")) item.put("addressSetting", schema.opt("address"))
        item.put("defaultAddress", schema.optString("defaultAddress"))
      }
      val subs = subProviders(provider)
      if (subs.isNotEmpty()) item.put("subProviders", JSONArray().apply { subs.forEach { (id, name, _) -> put(JSONObject().put("id", id).put("name", name)) } })
    }
    return root.toString()
  }

  fun hasCollections(): Boolean = state.repos.isNotEmpty() || state.providers.isNotEmpty()

  /**
   * Takes the account's copy of the collections, keeping what only this device knows.
   *
   * A bundle already downloaded here stays, matched on (collection, package); anything the account
   * no longer lists is dropped with its file, which is what makes a removal made elsewhere free the
   * space here. Settings arriving with a source replace this device's. Sources switched on
   * elsewhere are downloaded and asked what they offer. True when something changed.
   */
  fun restoreCloudState(raw: String?): Boolean {
    val root = runCatching { JSONObject(raw.orEmpty().ifBlank { "{}" }) }.getOrNull() ?: return false
    if (!root.has("repos") && !root.has("providers")) return false
    val incoming = runCatching { parseState(root) }.getOrNull() ?: return false
    val localPaths = state.providers.associate { (it.repoUrl to it.packageName) to it.installedFilePath }
    val merged = incoming.copy(providers = incoming.providers.map { it.copy(installedFilePath = localPaths[it.repoUrl to it.packageName]) })
    val editor = prefs.edit()
    var settingsChanged = false
    val providers = root.optJSONArray("providers") ?: JSONArray()
    for (index in 0 until providers.length()) {
      val item = providers.optJSONObject(index) ?: continue
      val settings = item.optJSONObject("settings") ?: continue
      val key = "settings:$storageKey:${item.optString("packageName")}"
      if (prefs.getString(key, null) != settings.toString()) {
        editor.putString(key, settings.toString())
        settingsChanged = true
      }
    }
    editor.apply()
    if (merged.repos == state.repos && merged.providers == state.providers && !settingsChanged) return false
    val kept = merged.providers.mapNotNull { it.installedFilePath }.toSet()
    state.providers.mapNotNull { it.installedFilePath }.filterNot { it in kept }.forEach { path ->
      bundles.remove(path)
      runCatching { File(path).delete() }
    }
    homeCache.clear()
    adapters = null
    state = merged
    persistLocal()
    notifyProvidersChanged()
    restoreEnabledBundles()
    return true
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
              domains = skyDomains(item.optJSONArray("domains")),
              authors = stringArray(item.optJSONArray("authors")),
              languages = stringArray(item.optJSONArray("languages")),
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
    bundles.remove(file.absolutePath)
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

  /** A change the user made: stamped, stored, synced to the profile's other devices. */
  private fun save() {
    state = state.copy(updatedAt = System.currentTimeMillis())
    persistLocal()
    onStateChanged?.invoke(state)
    notifyProvidersChanged()
  }

  /** Stores the state as it is, without stamping or syncing it. */
  private fun persistLocal() {
    prefs.edit().putString(storageKey, serialize(state)).apply()
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
              .put("enabled", it.enabled)
              .put("favourite", it.favourite),
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
              .put("installedFilePath", it.installedFilePath)
              .put("domains", JSONArray().apply { it.domains.forEach { domain -> put(JSONObject().put("name", domain.name).put("url", domain.url)) } })
              .put("authors", JSONArray(it.authors))
              .put("languages", JSONArray(it.languages)),
          )
        }
      },
    )
    return root.toString()
  }

  private fun load(): SkyPluginState {
    val raw = prefs.getString(storageKey, null) ?: return SkyPluginState()
    return runCatching { parseState(JSONObject(raw)) }.getOrDefault(SkyPluginState())
  }

  private fun parseState(root: JSONObject): SkyPluginState {
    return run {
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
            favourite = item.optBoolean("favourite", false),
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
            domains = skyDomains(item.optJSONArray("domains")),
            authors = stringArray(item.optJSONArray("authors")),
            languages = stringArray(item.optJSONArray("languages")),
          )
        },
        updatedAt = root.optLong("updatedAt", 0L),
      )
    }
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

/** A collection's mirror list: plain URLs or `{name, url}` objects, as both turn up. */
internal fun skyDomains(values: JSONArray?): List<SkyDomain> = buildList {
  val source = values ?: return@buildList
  for (index in 0 until source.length()) {
    when (val item = source.opt(index)) {
      is String -> item.trim().takeIf { it.startsWith("http") }?.let { add(SkyDomain(it.substringAfter("//").trimEnd('/'), it.trimEnd('/'))) }
      is JSONObject -> item.optString("url").trim().takeIf { it.startsWith("http") }?.let { url ->
        add(SkyDomain(item.optString("name").ifBlank { url.substringAfter("//") }, url.trimEnd('/')))
      }
    }
  }
}

/** One settings field in the shape the web portal reads, shared with the JS plugin collections. */
internal fun settingFieldJson(field: PluginSettingField): JSONObject = JSONObject()
  .put("type", field.type)
  .put("key", field.key)
  .put("label", field.label)
  .put("description", field.description)
  .put("placeholder", field.placeholder)
  .put("defaultValue", field.defaultValue?.toString())
  .put("options", JSONArray().apply {
    field.options.forEach { put(JSONObject().put("label", it.label).put("value", it.value).put("defaultOn", it.defaultOn)) }
  })

/** The field type an address setting is parsed into, so the dialog can offer its own control. */
internal const val SKY_ADDRESS_FIELD_TYPE = "address"

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
 * Turns a plugin's `getSettings()` reply into the shared field model.
 *
 * SkyStream names the caption `title` where StreamDek's own plugins use `label`, and spells the
 * boolean control `switch`/`bool` rather than `toggle`; both spellings are accepted so one dialog
 * can render either plugin system.
 */
internal fun parseSkySettingsSchema(raw: String): List<PluginSettingField> {
  // SkyStream accepts the list itself or `{settings: [...]}`.
  val array = runCatching { JSONArray(raw) }.getOrNull()
    ?: runCatching { JSONObject(raw).optJSONArray("settings") }.getOrNull()
    ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val key = item.optString("key").ifBlank { null }
      val label = item.optString("title").ifBlank { item.optString("label") }.ifBlank { item.optString("name") }.ifBlank { key.orEmpty() }.ifBlank { continue }
      val isAddress = item.optBoolean("isBaseUrl", false) || item.optBoolean("baseUrl", false) || key == "base_url" || key == "baseUrl"
      val type = when {
        isAddress -> SKY_ADDRESS_FIELD_TYPE
        else -> when (item.optString("type").trim().lowercase()) {
          "switch", "bool", "boolean", "toggle" -> "toggle"
          "select", "dropdown", "list" -> "select"
          "toggle_group", "togglegroup", "multi_toggle", "multitoggle", "switch_group" -> "toggleGroup"
          "header", "section" -> "header"
          else -> "text"
        }
      }
      val options = buildList {
        item.optJSONArray("options")?.let { source ->
          for (optionIndex in 0 until source.length()) {
            when (val option = source.opt(optionIndex)) {
              is JSONObject -> {
                val value = option.optString("value").ifBlank { option.optString("key") }.ifBlank { option.optString("label") }
                val on = option.opt("defaultValue") ?: option.opt("default") ?: option.opt("enabled")
                if (value.isNotBlank()) add(PluginSettingOption(option.optString("label").ifBlank { option.optString("title") }.ifBlank { value }, value, settingIsOn(on)))
              }
              is String -> if (option.isNotBlank()) add(PluginSettingOption(option, option))
            }
          }
        }
      }
      val default = sequenceOf("defaultValue", "default", "value").firstOrNull { item.has(it) && !item.isNull(it) }?.let(item::opt)
      add(
        PluginSettingField(
          type = type,
          key = key,
          label = label,
          description = item.optString("description").ifBlank { null },
          placeholder = item.optString("placeholder").ifBlank { null },
          // Toggle groups are stored as a JSON object of option → on, like SkyStream does.
          defaultValue = when (default) {
            is JSONObject, is JSONArray -> default.toString()
            else -> default
          },
          isPassword = item.optBoolean("isPassword", false) ||
            item.optString("key").contains("api_key", ignoreCase = true) ||
            item.optString("key").contains("token", ignoreCase = true),
          options = options,
        ),
      )
    }
  }
}

object SkyStreamPlugins {
  lateinit var manager: SkyStreamPluginManager
    private set
  val isInitialized: Boolean get() = ::manager.isInitialized
  fun initialize(context: Context) {
    if (!::manager.isInitialized) manager = SkyStreamPluginManager(context.applicationContext)
  }
}
