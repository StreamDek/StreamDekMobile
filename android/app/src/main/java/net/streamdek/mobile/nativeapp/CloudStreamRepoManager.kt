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
data class CsRepo(val url: String, val name: String, val description: String?, val iconUrl: String?, val enabled: Boolean = true)
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
data class CsPluginState(val repos: List<CsRepo> = emptyList(), val providers: List<CsProviderEntry> = emptyList(), val updatedAt: Long = 0L)

/** A known title used to check that a CloudStream source still scrapes. */
data class CsTestMedia(
  val label: String,
  val title: String,
  val year: Int?,
  val type: String,
  val season: Int? = null,
  val episode: Int? = null,
) {
  fun toRequest(): CloudStreamProviderBridge.StreamRequest =
    CloudStreamProviderBridge.StreamRequest(title = title, year = year, type = type, season = season, episode = episode)
}

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
  // specifically because of this.
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
    onStateChanged?.invoke(state)
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
        repos = state.repos.map { if (it.url == url) repo.copy(enabled = existingRepo?.enabled ?: true) else it },
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
    }
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
  }

  /**
   * A well-known title to probe a source with, chosen from the types the source advertises.
   * Returns null for sources StreamDek cannot meaningfully test by title — live/IPTV scrapers
   * answer with channels rather than titles, so searching one for a film proves nothing.
   */
  fun testMediaForProvider(repoUrl: String, internalName: String): CsTestMedia? {
    val entry = state.providers.firstOrNull { it.repoUrl == repoUrl && it.internalName == internalName } ?: return null
    val types = entry.tvTypes.map { it.lowercase() }
    val anime = types.any { "anime" in it } || entry.name.contains("anime", ignoreCase = true)
    val hasSeries = types.any { it in setOf("tvseries", "anime", "ova", "asiandrama", "cartoon") }
    val hasMovie = types.any { it in setOf("movie", "animemovie", "documentary") }
    return when {
      anime && hasSeries -> CsTestMedia("Attack on Titan S1 E1", "Attack on Titan", 2013, "tv", 1, 1)
      anime -> CsTestMedia("Spirited Away (2001)", "Spirited Away", 2001, "movie")
      hasMovie -> CsTestMedia("The Matrix (1999)", "The Matrix", 1999, "movie")
      hasSeries -> CsTestMedia("Breaking Bad S1 E1", "Breaking Bad", 2008, "tv", 1, 1)
      // Sources that declare nothing usable are still worth a films probe; live-only ones are not.
      types.isEmpty() -> CsTestMedia("The Matrix (1999)", "The Matrix", 1999, "movie")
      else -> null
    }
  }

  /** Runs [testMediaForProvider] through this one source and returns a few sample results. */
  suspend fun testProvider(repoUrl: String, internalName: String): Result<List<AddonStream>> = withContext(Dispatchers.IO) {
    runCatching {
      val entry = state.providers.firstOrNull { it.repoUrl == repoUrl && it.internalName == internalName }
        ?: throw IllegalStateException("This source is no longer listed in its collection.")
      val media = testMediaForProvider(repoUrl, internalName)
        ?: throw IllegalStateException("${entry.name} serves live channels, which cannot be checked with a test title.")
      val file = entry.installedFilePath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
        ?: throw IllegalStateException("Turn ${entry.name} on before testing it.")
      // Normally already loaded; loading here keeps the button working right after an enable.
      CloudStreamPluginLoader.load(context.applicationContext, file).getOrThrow()
      val providers = CloudStreamPluginLoader.providersFor(file.absolutePath)
      require(providers.isNotEmpty()) { "${entry.name} did not register any provider to test." }
      CloudStreamProviderBridge.streams(providers, media.toRequest()).take(5)
    }
  }

  /** The providers usable right now — loaded, and belonging to an enabled source in an enabled repo. */
  fun activeProviders(): List<com.lagradost.cloudstream3.MainAPI> {
    val enabledRepos = state.repos.filter { it.enabled }.mapTo(mutableSetOf()) { it.url }
    return state.providers
      .filter { it.enabled && it.repoUrl in enabledRepos }
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
    val pluginListUrls = manifest.optJSONArray("pluginLists") ?: throw IllegalArgumentException("Repo manifest has no pluginLists.")
    val providers = buildList {
      for (i in 0 until pluginListUrls.length()) {
        val listUrl = pluginListUrls.optString(i).takeIf { it.isNotBlank() } ?: continue
        val entries = runCatching { JSONArray(text(listUrl)) }.getOrDefault(JSONArray())
        for (j in 0 until entries.length()) {
          val item = entries.optJSONObject(j) ?: continue
          val internalName = item.optString("internalName").ifBlank { item.optString("name") }
          val downloadUrl = item.optString("url")
          if (internalName.isBlank() || downloadUrl.isBlank()) continue
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
    val root = JSONObject().put("updatedAt", state.updatedAt)
    root.put("repos", JSONArray().apply {
      state.repos.forEach { put(JSONObject().put("url", it.url).put("name", it.name).put("description", it.description).put("iconUrl", it.iconUrl).put("enabled", it.enabled)) }
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
            .put("enabled", it.enabled)
            .put("installedFilePath", it.installedFilePath),
        )
      }
    })
    prefs.edit().putString(storageKey, root.toString()).apply()
    onStateChanged?.invoke(state)
  }

  private fun load(): CsPluginState = runCatching {
    val raw = prefs.getString(storageKey, null) ?: return CsPluginState()
    val root = JSONObject(raw)
    val repos = root.optJSONArray("repos") ?: JSONArray()
    val providers = root.optJSONArray("providers") ?: JSONArray()
    CsPluginState(
      repos = List(repos.length()) {
        repos.getJSONObject(it).run { CsRepo(getString("url"), getString("name"), optString("description").ifBlank { null }, optString("iconUrl").ifBlank { null }, optBoolean("enabled", true)) }
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
