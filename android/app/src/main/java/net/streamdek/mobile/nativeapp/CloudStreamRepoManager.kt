package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
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

class CloudStreamRepoManager(private val context: Context) {
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

  @Volatile var state: CsPluginState = load()
    private set
  var onStateChanged: ((CsPluginState) -> Unit)? = null

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
        previous[entry.internalName]?.let { existing -> entry.copy(enabled = existing.enabled, installedFilePath = existing.installedFilePath) } ?: entry
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
      // A .cs3 is compiled against the REAL com.lagradost.cloudstream3 classes, which only
      // exist inside the actual CloudStream app itself — the "cloudstream3" Maven artifact
      // every real CloudStream extension repo compiles against (com.lagradost:cloudstream3:
      // pre-release, e.g. hexated's cloudstream-extensions-hexated build.gradle.kts) is
      // explicitly documented as "Stubs for all Cloudstream classes": compile-time-only
      // stand-ins never meant to ship inside another app. Without StreamDek itself containing
      // real MainAPI/TvType/ExtractorLink implementations — which means either vendoring
      // CloudStream's GPL-3.0 source or a large clean-room reimplementation of its entire
      // provider API surface, a licensing/scope decision rather than a bug fix — a loaded
      // plugin class has nothing real to call into no matter how cleanly it loads. So this
      // intentionally stops here rather than downloading a multi-megabyte .cs3 and surfacing a
      // confusing raw ClassNotFoundException for a source that could never have played anyway.
      throw IllegalStateException("CloudStream sources can't play in StreamDek yet — see the CloudStream Collections note above.")
    }
  }

  private fun downloadPlugin(entry: CsProviderEntry): File {
    val safeName = entry.internalName.replace(Regex("[^A-Za-z0-9._-]"), "_") + "_" + entry.repoUrl.hashCode().toUInt().toString(16) + ".cs3"
    val file = File(pluginDir, safeName)
    val response = http.newCall(Request.Builder().url(entry.downloadUrl).header("User-Agent", "StreamDek/1.0").build()).execute()
    response.use {
      require(it.isSuccessful) { "Download failed: ${it.code}" }
      val body = it.body ?: throw IllegalStateException("Empty download response.")
      file.outputStream().use { out -> body.byteStream().copyTo(out) }
    }
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
    prefs.edit().putString("state", root.toString()).apply()
    onStateChanged?.invoke(state)
  }

  private fun load(): CsPluginState = runCatching {
    val raw = prefs.getString("state", null) ?: return CsPluginState()
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
  fun initialize(context: Context) {
    if (!::manager.isInitialized) manager = CloudStreamRepoManager(context.applicationContext)
  }
}
