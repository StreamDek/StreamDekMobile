package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Installs and manages CloudStream-style provider collections — repos shaped like
 * https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/CNC.json
 * (`{name, pluginLists: [urls to a plugins.json]}`), where each `pluginLists` entry is a
 * compiled `.cs3` provider (a real CloudStream extension, e.g. one of CNCVerse's providers).
 *
 * A manifest may also be an *aggregate* ("mega") repo that lists other repo manifests under
 * `repos` instead of listing plugins itself — sky-universe's mega_repo.json is one. Those are
 * followed transparently, so installing the aggregate URL installs everything the collections
 * beneath it publish, as one entry in the user's list.
 *
 * This is deliberately a separate system from [StreamDekPluginManager] (StreamDek's own JS
 * scraper collections): a `.cs3` file is compiled Kotlin/JVM bytecode written against the
 * `com.lagradost.cloudstream3` provider API, not a JS `getStreams()` script, so it needs
 * [CloudStreamPluginLoader] (a PathClassLoader-based loader) rather than the QuickJS sandbox.
 * See that file for the runtime-dependency caveats — this manager only handles fetching repo
 * metadata and downloading/enabling individual `.cs3` files; it never has to know anything
 * about the cloudstream3 API surface itself.
 *
 * Providers default to *disabled* on install: a single repo can list 40+ extensions, and
 * downloading/loading all of them just because the repo URL was added would be slow and,
 * for the loading part, a real risk before the plugin API dependency has been verified to
 * work — see CloudStreamPluginLoader's header comment.
 */
/**
 * How far a chain of aggregate repos is followed. A mega repo pointing at repos that point at
 * further repos is unusual but legal; the cap stops a malformed or malicious manifest turning an
 * install into an unbounded crawl. Cycles are already excluded by the visited set.
 */
private const val MAX_REPO_NESTING = 3

/** URLs from a manifest array that holds either plain strings or `{ "url": … }` objects. */
private fun manifestUrlList(values: JSONArray?): List<String> = buildList {
  val source = values ?: return@buildList
  for (index in 0 until source.length()) {
    val value = when (val entry = source.opt(index)) {
      is String -> entry
      is JSONObject -> entry.optString("url")
      else -> null
    }
    value?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
  }
}

/**
 * Every `plugins.json` URL reachable from a repo manifest, in declaration order.
 *
 * A plain collection lists them directly under `pluginLists`. An aggregate lists other repo
 * manifests under `repos` and nothing else, so those have to be fetched and descended into before
 * there is anything to install — without this, adding one returned "Repo manifest has no
 * pluginLists" even though every collection beneath it was perfectly valid.
 *
 * [fetchManifest] returns null for a child that could not be read, which is skipped rather than
 * failing the install. Manifests are free to carry both keys; both are honoured.
 */
internal fun collectRepoPluginListUrls(
  rootUrl: String,
  rootManifest: JSONObject,
  maxDepth: Int = MAX_REPO_NESTING,
  fetchManifest: (String) -> JSONObject?,
): List<String> {
  val listUrls = LinkedHashSet<String>()
  val visited = mutableSetOf(rootUrl)

  fun walk(manifest: JSONObject, depth: Int) {
    listUrls += manifestUrlList(manifest.optJSONArray("pluginLists"))
    if (depth >= maxDepth) return
    for (childUrl in manifestUrlList(manifest.optJSONArray("repos"))) {
      if (!visited.add(childUrl)) continue
      val childManifest = fetchManifest(childUrl) ?: continue
      walk(childManifest, depth + 1)
    }
  }

  walk(rootManifest, 0)
  return listUrls.toList()
}

data class CsRepo(val url: String, val name: String, val description: String?, val iconUrl: String?, val enabled: Boolean = true, val favourite: Boolean = false)
data class CsProviderEntry(
  val repoUrl: String,
  val internalName: String,
  val name: String,
  val version: Int,
  val downloadUrl: String,
  val tvTypes: List<String>,
  val language: String?,
  val description: String?,
  val enabled: Boolean = false,
  val installedFilePath: String? = null,
)
data class CsPluginState(
  val repos: List<CsRepo> = emptyList(),
  val providers: List<CsProviderEntry> = emptyList(),
  val updatedAt: Long = 0L,
  /**
   * Which sources each extension has switched on, as recorded for the account; see
   * CloudStreamSourceSettings.kt. Kept beside the collections but synced apart from them, and never
   * part of [updatedAt]: a switch flipped inside one extension is not a reason for this device's
   * copy of the collections to win over the account's.
   */
  val sourceSettings: List<CsSourceSettings> = emptyList(),
)

class CloudStreamRepoManager(private val context: Context) {
  private companion object {
    const val TAG = "CloudStreamRepos"
    const val LEGACY_STORAGE_KEY = "state"
  }
  private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("streamdek_cs_plugins", Context.MODE_PRIVATE)
  private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

  // Downloaded .cs3 files MUST live under the app-specific *external* files directory, not
  // context.filesDir. Android 14+ enforces W^X-style restrictions on dynamically loaded code
  // written by the app itself in ordinary private storage — PathClassLoader can fail to find
  // any classes at all in a file placed under filesDir (it silently behaves as if the zip has
  // no classes, which is what a "didn't find class ... on path" error actually means here; the
  // file itself is fine). Real CloudStream's own PluginManager works around this the same way —
  // see its loadAllLocalPlugins, which copies plugin files into getExternalFilesDir(null)/plugins
  // specifically because of this. Not every device honours chmod on external storage, though, so
  // a file here can stay writable after setReadOnly(); the loader falls back to a private copy then.
  private val pluginDir: File by lazy {
    val base = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
    File(base, "cs3_plugins").apply { mkdirs() }
  }

  // Which profile's collections are in play. Kept separate per profile the same way the JS plugin
  // collections, add-ons and M3U playlists are: one household member's sources should not appear
  // (or start scraping) under another's profile.
  private var storageKey = LEGACY_STORAGE_KEY

  @Volatile var state: CsPluginState = load()
    private set
  var onStateChanged: ((CsPluginState) -> Unit)? = null

  /**
   * Switches to [ownerKey]'s collections. Everything the previous profile had loaded is unloaded
   * first — a `.cs3` stays live in the process until it is explicitly dropped, so without this the
   * outgoing profile's providers would keep answering stream requests for the incoming one.
   * Callers follow this with [loadEnabledProviders] to bring the new profile's sources up.
   */
  fun selectProfileStorage(ownerKey: String) {
    val nextKey = "state:$ownerKey"
    if (nextKey == storageKey) return
    state.providers.mapNotNull { it.installedFilePath }.distinct().forEach(CloudStreamPluginLoader::unload)
    storageKey = nextKey
    // Collections added before this was profile-scoped live under the old unscoped key. Hand them
    // to the first profile that asks so an upgrade does not look like the collections were lost.
    if (!prefs.contains(nextKey)) {
      prefs.getString(LEGACY_STORAGE_KEY, null)?.let { legacy ->
        prefs.edit().putString(nextKey, legacy).remove(LEGACY_STORAGE_KEY).apply()
      }
    }
    state = load()
    // The stores extensions keep their switches in belong to the device, not to a profile, so the
    // incoming profile's choices are written back into them before any of its extensions load.
    applySourceSettings(state.sourceSettings)
    onStateChanged?.invoke(state)
  }

  /** How many sources one owner's collections hold, without switching to that owner. */
  fun providerCountFor(ownerKey: String): Int = countProviders(prefs.getString("state:${ownerKey.ifBlank { "guest" }}", null))

  /**
   * Joins one owner's collections into another's, without switching the selected profile.
   *
   * By collection URL and by the internal name inside it, destination first. The downloaded `.cs3`
   * files are shared - a provider record carries the path it was installed to, and both owners can
   * point at the same file - so a migrated collection needs no second download.
   */
  fun mergeProfileStorageInto(fromOwnerKey: String, toOwnerKey: String): Boolean {
    val fromKey = "state:${fromOwnerKey.ifBlank { "guest" }}"
    val toKey = "state:${toOwnerKey.ifBlank { "guest" }}"
    if (fromKey == toKey) return false
    val incoming = prefs.getString(fromKey, null)?.takeIf { it.isNotBlank() && it != "{}" } ?: return false
    val merged = mergePluginStateDocuments(prefs.getString(toKey, null), incoming) ?: return false
    val committed = prefs.edit().putString(toKey, merged).commit()
    if (committed && toKey == storageKey) {
      state = load()
      onStateChanged?.invoke(state)
    }
    return committed
  }

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
      val previous = state.providers.filter { it.repoUrl == url }.associateBy { it.internalName }
      val (repo, freshProviders) = fetchRepo(url)
      // Preserve which providers were enabled/downloaded — refreshing shouldn't silently
      // re-disable something the user already turned on.
      val merged = freshProviders.map { entry ->
        val existing = previous[entry.internalName] ?: return@map entry
        // A version bump means the downloaded .cs3 is stale. Unload and forget it so the next
        // enable/startup pass fetches the new build instead of re-loading the old classes.
        if (existing.version != entry.version) {
          existing.installedFilePath?.let { path ->
            CloudStreamPluginLoader.unload(path)
            runCatching { File(path).delete() }
          }
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
    }
  }

  fun removeRepo(url: String) {
    state.providers.filter { it.repoUrl == url }.forEach { it.installedFilePath?.let(CloudStreamPluginLoader::unload) }
    state = state.copy(repos = state.repos.filterNot { it.url == url }, providers = state.providers.filterNot { it.repoUrl == url })
    save()
  }

  fun enableRepo(url: String, enabled: Boolean) {
    state = state.copy(repos = state.repos.map { if (it.url == url) it.copy(enabled = enabled) else it })
    if (!enabled) {
      state.providers.filter { it.repoUrl == url && it.installedFilePath != null }.forEach { it.installedFilePath?.let(CloudStreamPluginLoader::unload) }
    }
    save()
  }

  fun toggleRepoFavourite(url: String) {
    state = state.copy(repos = state.repos.map { if (it.url == url) it.copy(favourite = !it.favourite) else it })
    save()
  }

  /** Downloads (if needed) and loads a single provider, or unloads it, on-device. */
  suspend fun setProviderEnabled(repoUrl: String, internalName: String, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val entry = state.providers.firstOrNull { it.repoUrl == repoUrl && it.internalName == internalName }
        ?: throw IllegalStateException("This source is no longer listed in its collection.")
      if (!enabled) {
        entry.installedFilePath?.let(CloudStreamPluginLoader::unload)
        state = state.copy(providers = state.providers.map { if (it === entry) it.copy(enabled = false) else it })
        save()
        return@runCatching
      }
      val file = entry.installedFilePath?.let(::File)?.takeIf { it.exists() && it.length() > 0L } ?: downloadPlugin(entry)
      CloudStreamPluginLoader.load(context.applicationContext, file)
        .onFailure { failure ->
          // A half-written or stale download is the usual cause; drop it so the next attempt refetches.
          runCatching { file.delete() }
          throw IllegalStateException(loadFailureMessage(entry.name, failure), failure)
        }
      state = state.copy(
        providers = state.providers.map {
          if (it.repoUrl == repoUrl && it.internalName == internalName) it.copy(enabled = true, installedFilePath = file.absolutePath) else it
        },
      )
      save()
    }.also { notifyProvidersChanged() }
  }

  /**
   * Told whenever the set of loaded providers may have changed — sources switched on or off, or the
   * enabled ones brought up. The Home layout listens, since each provider can offer Home rows.
   */
  @Volatile var onProvidersChanged: (() -> Unit)? = null

  private fun notifyProvidersChanged() {
    runCatching { onProvidersChanged?.invoke() }.onFailure { Log.w(TAG, "Provider change listener failed", it) }
  }

  /**
   * Loads everything the user has already enabled. Called once at startup: `.cs3` providers only
   * exist in the process while their classes are loaded, so without this an app restart would
   * silently leave every enabled source unable to answer.
   */
  suspend fun loadEnabledProviders(): Unit = withContext(Dispatchers.IO) {
    val enabledRepos = state.repos.filter { it.enabled }.mapTo(mutableSetOf()) { it.url }
    val wanted = state.providers.filter { it.enabled && it.repoUrl in enabledRepos }
    val installedPaths = mutableMapOf<String, String>()
    wanted.forEach { entry ->
      val file = entry.installedFilePath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
        ?: runCatching { downloadPlugin(entry) }
          .onFailure { Log.w(TAG, "Could not fetch ${entry.name}", it) }
          .getOrNull()
        ?: return@forEach
      CloudStreamPluginLoader.load(context.applicationContext, file)
        .onSuccess { installedPaths[entry.repoUrl + "|" + entry.internalName] = file.absolutePath }
        .onFailure { Log.w(TAG, "Could not load ${entry.name}", it) }
    }
    // A source whose file had to be re-fetched here has no stored path yet, and activeProviders()
    // resolves loaded providers *by* that path — without writing it back the plugin would be
    // loaded in memory but invisible to every stream request.
    if (installedPaths.isNotEmpty()) {
      state = state.copy(
        providers = state.providers.map { entry ->
          installedPaths[entry.repoUrl + "|" + entry.internalName]
            ?.takeIf { it != entry.installedFilePath }
            ?.let { entry.copy(installedFilePath = it) }
            ?: entry
        },
      )
      save()
    }
    Log.i(TAG, "CloudStream sources ready: ${activeProviders().size} provider(s) from ${wanted.size} enabled source(s)")
    notifyProvidersChanged()
    recordObservedSourceSettings()
  }

  /** The providers usable right now — loaded, and belonging to an enabled source in an enabled repo. */
  fun activeProviders(): List<com.lagradost.cloudstream3.MainAPI> {
    val enabledRepos = state.repos.filter { it.enabled }.associateBy { it.url }
    return state.providers
      .filter { it.enabled && it.repoUrl in enabledRepos }
      .sortedWith(compareByDescending<CsProviderEntry> { enabledRepos[it.repoUrl]?.favourite == true }.thenBy { it.name.lowercase() })
      .mapNotNull { it.installedFilePath }
      .flatMap(CloudStreamPluginLoader::providersFor)
  }

  private fun loadFailureMessage(name: String, failure: Throwable): String {
    val reason = failure.message.orEmpty()
    return when {
      reason.contains("ClassNotFoundException", true) || reason.contains("NoClassDefFoundError", true) ->
        "$name needs a part of the CloudStream API that StreamDek does not ship. It cannot run here."
      reason.contains("manifest.json", true) -> "$name is not a valid CloudStream extension file."
      reason.isBlank() -> "$name could not be loaded."
      else -> "$name could not be loaded: ${reason.take(180)}"
    }
  }

  private fun downloadPlugin(entry: CsProviderEntry): File {
    val safeName = entry.internalName.replace(Regex("[^A-Za-z0-9._-]"), "_") + "_" + entry.repoUrl.hashCode().toUInt().toString(16) + ".cs3"
    val file = File(pluginDir, safeName)
    // The loader marks installed plugins read-only (Android 14+ refuses to load writable code),
    // so an existing copy has to be made writable again before it can be replaced.
    if (file.exists()) {
      file.setWritable(true)
      file.delete()
    }
    val response = http.newCall(Request.Builder().url(entry.downloadUrl).header("User-Agent", "StreamDek/1.0").build()).execute()
    response.use {
      require(it.isSuccessful) { "Download failed: ${it.code}" }
      val body = it.body ?: throw IllegalStateException("Empty download response.")
      file.outputStream().use { out -> body.byteStream().copyTo(out) }
    }
    require(file.length() > 0L) { "The download for ${entry.name} was empty." }
    return file
  }

  private fun fetchRepo(url: String): Pair<CsRepo, List<CsProviderEntry>> {
    val manifest = JSONObject(text(url))
    val name = manifest.optString("name").ifBlank { "CloudStream collection" }
    val pluginListUrls = collectRepoPluginListUrls(url, manifest) { childUrl ->
      // One unreachable collection inside an aggregate must not fail the whole install, which
      // matches how an unreadable plugins.json is already tolerated below.
      runCatching { JSONObject(text(childUrl)) }
        .onFailure { Log.w(TAG, "Skipping unreachable repo $childUrl inside $url", it) }
        .getOrNull()
    }
    require(pluginListUrls.isNotEmpty()) { "Repo manifest has no pluginLists." }
    // Providers are identified by (repoUrl, internalName) everywhere else — state lookups,
    // enable/disable, and the on-disk .cs3 filename. Everything an aggregate pulls in shares one
    // repoUrl, so two collections shipping the same internalName would otherwise collide on all
    // three. First listed wins, which respects the order the aggregate declared.
    val seen = mutableSetOf<String>()
    val providers = buildList {
      for (listUrl in pluginListUrls) {
        val entries = runCatching { JSONArray(text(listUrl)) }.getOrDefault(JSONArray())
        for (j in 0 until entries.length()) {
          val item = entries.optJSONObject(j) ?: continue
          val internalName = item.optString("internalName").ifBlank { item.optString("name") }
          val downloadUrl = item.optString("url")
          if (internalName.isBlank() || downloadUrl.isBlank()) continue
          // SkyStream bundles advertise themselves through an identical manifest and are handled
          // by SkyStreamPluginManager. Skipping them here is what lets one aggregate carrying both
          // formats install into both engines, each taking only the entries it can actually run —
          // and stops a pure-SkyStream repo listing dozens of sources that fail on being switched
          // on, which is how these first showed up.
          if (isSkyDownloadUrl(downloadUrl)) continue
          if (!seen.add(internalName)) continue
          val tvTypes = item.optJSONArray("tvTypes")
          add(
            CsProviderEntry(
              repoUrl = url,
              internalName = internalName,
              name = item.optString("name").ifBlank { internalName },
              version = item.optInt("version", 0),
              downloadUrl = downloadUrl,
              tvTypes = buildList { tvTypes?.let { arr -> for (k in 0 until arr.length()) arr.optString(k).takeIf { it.isNotBlank() }?.let(::add) } },
              language = item.optString("language").ifBlank { null },
              description = item.optString("description").ifBlank { null },
              enabled = false,
            ),
          )
        }
      }
    }
    require(providers.isNotEmpty()) { "No providers found in that collection." }
    return CsRepo(url = url, name = name, description = manifest.optString("description").ifBlank { null }, iconUrl = manifest.optString("iconUrl").ifBlank { null }) to providers
  }

  private fun text(url: String): String = try {
    http.newCall(Request.Builder().url(url).header("User-Agent", "StreamDek/1.0").build()).execute().use {
      require(it.isSuccessful) { "Request failed: ${it.code}" }
      it.body?.string() ?: throw IllegalStateException("Empty response.")
    }
  } catch (e: java.io.IOException) {
    val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
    if (isLocalNetworkHost(host)) {
      throw IllegalStateException("Could not reach $host from this phone. Use your computer's LAN IP instead of localhost, or run `adb reverse tcp:<port> tcp:<port>` first.", e)
    }
    throw e
  }

  private fun save() {
    state = state.copy(updatedAt = System.currentTimeMillis())
    persist()
  }

  /** Writes the state as it stands, without restamping it. */
  private fun persist() {
    prefs.edit().putString(storageKey, localDocument()).apply()
    onStateChanged?.invoke(state)
  }

  /** The on-disk form: the account's copy plus this device's file paths and its source switches. */
  private fun localDocument(): String =
    JSONObject(snapshotJsonWithPaths()).put("sourceSettings", csSourceSettingsJson(state.sourceSettings)).toString()

  // ── Source switches inside extensions (see CloudStreamSourceSettings.kt) ──────────────────

  /**
   * Told when this device has a source switch the account may not have yet - one the viewer just
   * changed, or one found on an extension's first load - so the caller can push the document.
   */
  @Volatile var onSourceSettingsChanged: (() -> Unit)? = null

  /** What happened when the account's switches were taken. */
  data class SourceSettingsMerge(
    /** Something was written into an extension's store, and that extension was unloaded to re-read it. */
    val reloadNeeded: Boolean,
    /** This device holds switches newer than the account's, which it should push. */
    val localAhead: Boolean,
  )

  /** This profile's switches as the account stores them, under `cloudstreamSourceSettings`. */
  fun sourceSettingsJson(): JSONArray = csSourceSettingsJson(state.sourceSettings)

  fun hasSourceSettings(): Boolean = state.sourceSettings.any { it.values.isNotEmpty() }

  /**
   * Takes the account's source switches, value by value, and writes whatever changed into the
   * extensions' own stores.
   *
   * Never a replacement: see [mergeCsSourceSettings]. Anything recorded here that the account does
   * not have survives the merge and is reported through [SourceSettingsMerge.localAhead], so local
   * choices made before the account was reachable are pushed rather than lost. An extension whose
   * switches changed is unloaded, because it decides which sources to register when it loads; the
   * caller follows a [SourceSettingsMerge.reloadNeeded] with [loadEnabledProviders].
   */
  @Synchronized
  fun mergeCloudSourceSettings(array: JSONArray?): SourceSettingsMerge {
    val incoming = parseCsSourceSettings(array)
    val local = state.sourceSettings
    val localAhead = csSourceSettingsAhead(local, incoming)
    val merged = mergeCsSourceSettings(local, incoming)
    if (merged == local) return SourceSettingsMerge(reloadNeeded = false, localAhead = localAhead)
    val before = local.associateBy { it.identity }
    state = state.copy(sourceSettings = merged)
    persist()
    val reload = applySourceSettings(merged.filter { before[it.identity] != it })
    return SourceSettingsMerge(reloadNeeded = reload, localAhead = localAhead)
  }

  /**
   * Records one visit to an extension's settings screen; see [recordCsSourceVisit].
   *
   * [before] was taken as the screen opened and is compared with the stores as they are now, so
   * only what the viewer actually changed there is stamped as a decision.
   */
  @Synchronized
  fun recordSourceVisit(installedFilePath: String, before: Map<String, Map<String, Any>>) {
    val provider = state.providers.firstOrNull { it.installedFilePath == installedFilePath } ?: return
    val after = CloudStreamSourcePrefs.snapshot(context)
    val identity = csSourceIdentity(provider.repoUrl, provider.internalName)
    val existing = state.sourceSettings.firstOrNull { it.identity == identity }
      ?: CsSourceSettings(provider.repoUrl, provider.internalName)
    val updated = recordCsSourceVisit(
      existing = existing.copy(name = provider.name),
      before = before,
      after = after,
      ownedStores = CloudStreamSourcePrefs.storesOwnedBy(installedFilePath),
      now = System.currentTimeMillis(),
    ) ?: return
    state = state.copy(sourceSettings = state.sourceSettings.filterNot { it.identity == identity } + updated)
    persist()
    Log.i(TAG, "Recorded source switches for ${provider.name}: ${updated.values.size} value(s)")
    notifySourceSettingsChanged()
  }

  /**
   * Records, as observed rather than chosen, the switches every loaded extension already has in the
   * stores it owns. Only keys not recorded yet, and only at stamp zero, so this can seed the account
   * from a device that was set up before this sync existed without ever overruling a real choice.
   */
  @Synchronized
  private fun recordObservedSourceSettings() {
    var settings = state.sourceSettings
    var changed = false
    state.providers.filter { it.enabled && it.installedFilePath != null }.forEach { provider ->
      val stores = CloudStreamSourcePrefs.storesOwnedBy(provider.installedFilePath)
      if (stores.isEmpty()) return@forEach
      val identity = csSourceIdentity(provider.repoUrl, provider.internalName)
      val existing = settings.firstOrNull { it.identity == identity } ?: CsSourceSettings(provider.repoUrl, provider.internalName, provider.name)
      val known = existing.values.mapTo(HashSet()) { it.id }
      // A store someone has chosen for is settled; what this device happens to hold is not news.
      val chosen = existing.wholeStores.keys
      val observed = stores.filterNot { it in chosen }.flatMap { store ->
        CloudStreamSourcePrefs.switches(context, store).mapNotNull { (key, raw) ->
          val (type, typed) = csSwitchValue(raw) ?: return@mapNotNull null
          CsSourceValue(store, key, type, typed, updatedAt = 0L).takeIf { it.id !in known }
        }
      }
      if (observed.isEmpty()) return@forEach
      settings = settings.filterNot { it.identity == identity } + existing.copy(values = existing.values + observed)
      changed = true
    }
    if (!changed) return
    state = state.copy(sourceSettings = settings)
    persist()
    notifySourceSettingsChanged()
  }

  /** Writes [entries] into the extensions' stores and unloads any extension whose switches changed. */
  private fun applySourceSettings(entries: List<CsSourceSettings>): Boolean {
    var wrote = false
    entries.forEach { entry ->
      val changed = runCatching { CloudStreamSourcePrefs.apply(context, entry) }
        .onFailure { Log.w(TAG, "Could not apply source switches for ${entry.name ?: entry.internalName}", it) }
        .getOrDefault(false)
      if (!changed) return@forEach
      wrote = true
      state.providers.firstOrNull { it.repoUrl == entry.repoUrl && it.internalName == entry.internalName }
        ?.installedFilePath?.let(CloudStreamPluginLoader::unload)
    }
    return wrote
  }

  private fun notifySourceSettingsChanged() {
    runCatching { onSourceSettingsChanged?.invoke() }.onFailure { Log.w(TAG, "Source settings listener failed", it) }
  }

  /**
   * This profile's collections as the account stores them, for the other clients to read.
   *
   * `installedFilePath` is deliberately left out. Where this device put its copy of a `.cs3` is
   * this device's business -- another phone has a different path and a television has no copy at
   * all -- and syncing it would hand every client a pointer to a file it does not have.
   */
  fun snapshotJson(): String {
    val root = JSONObject().put("updatedAt", state.updatedAt)
    root.put("repos", JSONArray().apply {
      state.repos.forEach { put(JSONObject().put("url", it.url).put("name", it.name).put("description", it.description).put("iconUrl", it.iconUrl).put("enabled", it.enabled).put("favourite", it.favourite)) }
    })
    root.put("providers", JSONArray().apply {
      state.providers.forEach {
        put(
          JSONObject()
            .put("repoUrl", it.repoUrl)
            .put("internalName", it.internalName)
            .put("name", it.name)
            .put("version", it.version)
            .put("downloadUrl", it.downloadUrl)
            .put("tvTypes", JSONArray(it.tvTypes))
            .put("language", it.language)
            .put("description", it.description)
            .put("enabled", it.enabled),
        )
      }
    })
    return root.toString()
  }

  fun snapshotUpdatedAt(raw: String): Long = runCatching { JSONObject(raw).optLong("updatedAt", 0L) }.getOrDefault(0L)

  /**
   * Takes the account's copy of the collections, keeping what only this device can know.
   *
   * A provider already downloaded here keeps its `installedFilePath`, matched on the same
   * (repoUrl, internalName) pair everything else uses -- otherwise taking an update from the web
   * portal would orphan every `.cs3` already on disk and every enabled source would have to be
   * fetched again. Anything the incoming copy no longer lists is dropped, including its file:
   * that is what makes a removal made elsewhere actually free the space here.
   *
   * Returns true when something changed, so the caller can decide whether to reload providers.
   */
  fun restoreCloudState(raw: String?): Boolean {
    val root = runCatching { JSONObject(raw.orEmpty().ifBlank { "{}" }) }.getOrNull() ?: return false
    if (!root.has("repos") && !root.has("providers")) return false
    val incoming = parseState(root)
    val localPaths = state.providers.associate { (it.repoUrl to it.internalName) to it.installedFilePath }
    val merged = incoming.copy(
      providers = incoming.providers.map { provider ->
        provider.copy(installedFilePath = localPaths[provider.repoUrl to provider.internalName])
      },
      // Not part of the section: they arrive separately, through mergeCloudSourceSettings.
      sourceSettings = state.sourceSettings,
    )
    if (merged.repos == state.repos && merged.providers == state.providers) return false

    // A source that has gone, or been switched off elsewhere, must stop answering here too --
    // a .cs3 stays live in the process until it is explicitly dropped.
    // A collection switched off elsewhere takes its sources with it, whatever their own switches say.
    val enabledRepos = merged.repos.filter { it.enabled }.mapTo(HashSet()) { it.url }
    val keep = merged.providers.filter { it.enabled && it.repoUrl in enabledRepos }.mapNotNull { it.installedFilePath }.toSet()
    state.providers.mapNotNull { it.installedFilePath }.distinct()
      .filterNot { it in keep }
      .forEach(CloudStreamPluginLoader::unload)

    state = merged
    prefs.edit().putString(storageKey, localDocument()).apply()
    return true
  }

  /** The on-disk form, which unlike [snapshotJson] does keep this device's file paths. */
  private fun snapshotJsonWithPaths(): String {
    val root = JSONObject(snapshotJson())
    val providers = root.optJSONArray("providers") ?: JSONArray()
    for (index in 0 until providers.length()) {
      val item = providers.optJSONObject(index) ?: continue
      val match = state.providers.firstOrNull {
        it.repoUrl == item.optString("repoUrl") && it.internalName == item.optString("internalName")
      }
      item.put("installedFilePath", match?.installedFilePath)
    }
    return root.toString()
  }

  private fun parseState(root: JSONObject): CsPluginState {
    val repos = root.optJSONArray("repos") ?: JSONArray()
    val providers = root.optJSONArray("providers") ?: JSONArray()
    return CsPluginState(
      repos = List(repos.length()) {
        repos.getJSONObject(it).run { CsRepo(getString("url"), getString("name"), optString("description").ifBlank { null }, optString("iconUrl").ifBlank { null }, optBoolean("enabled", true), optBoolean("favourite", false)) }
      },
      providers = List(providers.length()) { index ->
        providers.getJSONObject(index).run {
          val tvTypes = optJSONArray("tvTypes") ?: JSONArray()
          CsProviderEntry(
            repoUrl = getString("repoUrl"),
            internalName = getString("internalName"),
            name = getString("name"),
            version = optInt("version", 0),
            downloadUrl = getString("downloadUrl"),
            tvTypes = List(tvTypes.length()) { i -> tvTypes.getString(i) },
            language = optString("language").ifBlank { null },
            description = optString("description").ifBlank { null },
            enabled = optBoolean("enabled", false),
            installedFilePath = optString("installedFilePath").ifBlank { null },
          )
        }
      },
      updatedAt = root.optLong("updatedAt", 0L),
    )
  }

  private fun load(): CsPluginState = runCatching {
    val raw = prefs.getString(storageKey, null) ?: return CsPluginState()
    val root = JSONObject(raw)
    val repos = root.optJSONArray("repos") ?: JSONArray()
    val providers = root.optJSONArray("providers") ?: JSONArray()
    CsPluginState(
      repos = List(repos.length()) {
        repos.getJSONObject(it).run { CsRepo(getString("url"), getString("name"), optString("description").ifBlank { null }, optString("iconUrl").ifBlank { null }, optBoolean("enabled", true), optBoolean("favourite", false)) }
      },
      providers = List(providers.length()) { index ->
        providers.getJSONObject(index).run {
          val tvTypes = optJSONArray("tvTypes") ?: JSONArray()
          CsProviderEntry(
            repoUrl = getString("repoUrl"),
            internalName = getString("internalName"),
            name = getString("name"),
            version = optInt("version", 0),
            downloadUrl = getString("downloadUrl"),
            tvTypes = List(tvTypes.length()) { i -> tvTypes.getString(i) },
            language = optString("language").ifBlank { null },
            description = optString("description").ifBlank { null },
            enabled = optBoolean("enabled", false),
            installedFilePath = optString("installedFilePath").ifBlank { null },
          )
        }
      },
      updatedAt = root.optLong("updatedAt", 0L),
      sourceSettings = parseCsSourceSettings(root.optJSONArray("sourceSettings")),
    )
  }.getOrDefault(CsPluginState())
}

object CloudStreamPlugins {
  lateinit var manager: CloudStreamRepoManager
    private set
  val isInitialized: Boolean get() = ::manager.isInitialized
  fun initialize(context: Context) {
    if (!::manager.isInitialized) manager = CloudStreamRepoManager(context.applicationContext)
  }
}
