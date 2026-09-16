package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.InMemoryDexClassLoader
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile
import java.lang.ref.WeakReference

/**
 * Loads compiled CloudStream provider plugins (`.cs3` files) on-device.
 *
 * A `.cs3` is a zip holding `manifest.json` plus a `classes.dex` compiled against the real
 * `com.lagradost.cloudstream3` API. StreamDek ships that API — see the
 * `libs/cloudstream-provider-runtime.jar` note in app/build.gradle — so a plugin's classes
 * resolve their superclasses (Plugin/BasePlugin/MainAPI/ExtractorApi) against the same classes
 * this file references, and the loaded provider ends up in [APIHolder.allProviders] exactly the
 * way it would inside CloudStream itself.
 *
 * Loading is deliberately keyed on the plugin's file path so the same `.cs3` is never
 * instantiated twice, and unloading removes whatever the plugin registered.
 */
object CloudStreamPluginLoader {
  private const val TAG = "CloudStreamPluginLoader"
  private val DEX_ENTRY = Regex("classes\\d*\\.dex")

  class LoadedCsPlugin(
    val filePath: String,
    val name: String,
    val version: Int,
    val instance: BasePlugin,
    /** The providers this plugin registered while loading. */
    val providers: List<MainAPI>,
  )

  private val loaded = LinkedHashMap<String, LoadedCsPlugin>()

  /** Moves on every load and unload, so answers derived from the loaded set know when to refresh. */
  @Volatile var generation = 0
    private set

  fun loadedPlugins(): List<LoadedCsPlugin> = synchronized(loaded) { loaded.values.toList() }

  fun isLoaded(filePath: String): Boolean = synchronized(loaded) { loaded.containsKey(filePath) }

  fun providersFor(filePath: String): List<MainAPI> = synchronized(loaded) { loaded[filePath]?.providers.orEmpty() }

  /** Every provider currently registered by a loaded plugin, in load order. */
  fun allProviders(): List<MainAPI> = loadedPlugins().flatMap { it.providers }

  /**
   * The plugin file each provider was registered by, keyed by provider name. A provider's name need
   * not match its plugin's, and one plugin can register several, so the file is the dependable way
   * back to the collection a stream result came from.
   */
  fun providerFiles(): Map<String, String> =
    loadedPlugins().flatMap { plugin -> plugin.providers.map { provider -> provider.name to plugin.filePath } }.toMap()

  @Synchronized
  fun load(context: Context, file: File): Result<LoadedCsPlugin> = runCatching {
    val filePath = file.absolutePath
    synchronized(loaded) { loaded[filePath] }?.let { return@runCatching it }

    CloudStreamRuntime.initialize(context)
    // Plugins can read CommonActivity.activity in their constructor, before load() runs.
    // In particular, optional providers may be selected through its SharedPreferences.
    val pluginHost = if (context is AppCompatActivity) context else CloudStreamRuntime.pluginHost(context)
    (pluginHost as? android.app.Activity)?.let {
      com.lagradost.cloudstream3.CommonActivity.setActivityInstance(it)
    }

    val manifestJson = readManifest(file)
      ?: throw IllegalStateException("No manifest.json inside ${file.name} — is this really a .cs3 plugin?")
    val loader = pluginClassLoader(context, file)

    val name = manifestJson.optString("name").ifBlank { file.nameWithoutExtension }
    val version = manifestJson.optInt("version", Int.MIN_VALUE)
    val pluginClassName = manifestJson.optString("pluginClassName").ifBlank {
      throw IllegalStateException("manifest.json in ${file.name} has no pluginClassName")
    }
    val requiresResources = manifestJson.optBoolean("requiresResources", false)

    val instance = loader.loadClass(pluginClassName).getDeclaredConstructor().newInstance() as? BasePlugin
      ?: throw IllegalStateException("$pluginClassName is not a CloudStream plugin.")
    instance.filename = filePath

    if (requiresResources && instance is Plugin) {
      @Suppress("DEPRECATION")
      runCatching {
        val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
        AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, filePath)
        // Deprecated constructor, but it is what CloudStream's own PluginManager uses for this
        // exact purpose (loading a plugin's bundled resources) — there is no replacement that fits.
        instance.resources = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
      }.onFailure { Log.w(TAG, "Failed to attach plugin resources for $name", it) }
    }

    // registerMainAPI() appends to the shared APIHolder list (and stamps each provider with the
    // plugin's filename), so diffing that list around load() is how we find out which providers
    // belong to this particular plugin.
    val before = APIHolder.allProviders.toList()
    // An AppCompatActivity, as CloudStream itself hands over; see CloudStreamRuntime.pluginHost.
    try {
      if (instance is Plugin) instance.load(pluginHost) else instance.load()
    } catch (failure: Throwable) {
      // A plugin may register its default provider before failing to load optional providers.
      // Do not leave those registrations behind and duplicate them on the next attempt.
      APIHolder.allProviders.removeAll { candidate -> before.none { it === candidate } }
      throw failure
    }
    val registered = APIHolder.allProviders.toList().filter { candidate -> before.none { it === candidate } }

    val record = LoadedCsPlugin(filePath, name, version, instance, registered)
    synchronized(loaded) { loaded[filePath] = record }
    generation += 1
    Log.i(TAG, "Loaded $name (v$version) with ${registered.size} provider(s): ${registered.joinToString { it.name }}")
    record
  }.onFailure { Log.e(TAG, "Failed to load CloudStream plugin ${file.name}", it) }

  @Synchronized
  fun unload(filePath: String) {
    val record = synchronized(loaded) { loaded.remove(filePath) } ?: return
    generation += 1
    runCatching { record.instance.beforeUnload() }
      .onFailure { Log.w(TAG, "beforeUnload failed for ${record.name}", it) }
    runCatching {
      APIHolder.allProviders.removeAll { provider -> record.providers.any { it === provider } }
    }.onFailure { Log.w(TAG, "Failed to unregister providers for ${record.name}", it) }
  }

  private fun readManifest(file: File): JSONObject? = runCatching {
    ZipFile(file).use { zip ->
      zip.getEntry("manifest.json")?.let { entry ->
        zip.getInputStream(entry).use { JSONObject(it.bufferedReader().readText()) }
      }
    }
  }.getOrNull()

  /**
   * A class loader for [file] that ART will accept. Android 14+ refuses to open a dex file the app
   * can still write to — it tests `access(path, W_OK)` — and `setReadOnly()` alone does not always
   * get there: on some devices (a OnePlus Nord N30 SE on Android 15, for one) app-specific external
   * storage ignores chmod, so the plugin stays writable and loading throws
   * "Writable dex file ... is not allowed". Internal storage does honour it, and is where CloudStream
   * keeps the plugins it downloads, so a private copy is loaded instead. If even that stays
   * writable, the dex is loaded from memory, which the check does not apply to.
   */
  private fun pluginClassLoader(context: Context, file: File): ClassLoader {
    val readOnly = makeReadOnly(file)
    if (readOnly || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return PathClassLoader(file.absolutePath, context.classLoader)
    }
    Log.w(TAG, "${file.name} is still writable after setReadOnly(); loading a private copy")
    val copy = privateCopy(context, file)
    if (copy != null && makeReadOnly(copy)) return PathClassLoader(copy.absolutePath, context.classLoader)
    Log.w(TAG, "No read-only copy of ${file.name} could be made; loading its dex from memory")
    return inMemoryClassLoader(context, file)
  }

  /** True once [file] can no longer be written, by the same `access(W_OK)` test ART applies. */
  private fun makeReadOnly(file: File): Boolean {
    runCatching { file.setReadOnly() }.onFailure { Log.w(TAG, "setReadOnly failed for ${file.name}", it) }
    return !file.canWrite()
  }

  /** [file] mirrored into internal storage, refreshed whenever the original changes. */
  private fun privateCopy(context: Context, file: File): File? = runCatching {
    val copy = File(File(context.filesDir, "cs3_plugins").apply { mkdirs() }, file.name)
    if (copy.absolutePath == file.absolutePath) return@runCatching null
    if (!copy.exists() || copy.length() != file.length() || copy.lastModified() != file.lastModified()) {
      // A previous copy was made read-only, so it has to be made writable before it is replaced.
      copy.setWritable(true)
      copy.delete()
      file.copyTo(copy)
      copy.setLastModified(file.lastModified())
    }
    copy
  }.onFailure { Log.w(TAG, "Could not copy ${file.name} into internal storage", it) }.getOrNull()

  private fun inMemoryClassLoader(context: Context, file: File): ClassLoader {
    val dexes = ZipFile(file).use { zip ->
      zip.entries().asSequence()
        .filter { DEX_ENTRY.matches(it.name) }
        // classes.dex first, then classes2.dex, classes3.dex, ... as the runtime would order them.
        .sortedBy { it.name.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 1 }
        .map { entry -> ByteBuffer.wrap(zip.getInputStream(entry).use { it.readBytes() }) }
        .toList()
    }
    require(dexes.isNotEmpty()) { "${file.name} has no classes.dex" }
    return InMemoryDexClassLoader(dexes.toTypedArray(), context.classLoader)
  }
}

/**
 * One-time process-wide setup the CloudStream runtime expects the host app to have done before
 * any provider code runs. Inside CloudStream this happens in its Application.onCreate; here it is
 * driven from plugin loading (and from StreamDek's own Application) instead.
 *
 * The only piece that genuinely has to be injected is the application Context: providers reach it
 * through `CloudStreamApp.context` for `getKey`/`setKey`-backed settings. `MainAPI.settingsForProvider`
 * already defaults to a usable value in the runtime's own static initialiser.
 */
object CloudStreamRuntime {
  private const val TAG = "CloudStreamRuntime"

  @Volatile private var initialized = false

  fun initialize(context: Context) {
    if (initialized) return
    synchronized(this) {
      if (initialized) return
      runCatching {
        // _context is private with only a synthetic accessor, so reflection is the honest way in.
        val field = com.lagradost.cloudstream3.CloudStreamApp::class.java.getDeclaredField("_context")
        field.isAccessible = true
        field.set(null, WeakReference(context.applicationContext))
      }.onFailure { Log.w(TAG, "Could not attach an application context to the CloudStream runtime", it) }
      initialized = true
    }
  }

  @Volatile private var host: Context? = null

  /**
   * The Context a plugin's `load()` is handed.
   *
   * Inside CloudStream that is its main activity, an AppCompatActivity, and many extensions cast it
   * to one on the spot to keep for their settings screen — CNCVerse's SKTechProvider, CNC Verse and
   * CNC Verse Mobile among them. Handed the application instead, that cast threw ("MainApplication
   * cannot be cast to AppCompatActivity") partway through loading, and the source never turned on.
   *
   * StreamDek's own activity is not an AppCompatActivity, and making it one would change how the app
   * applies its language and night mode. So plugins get a stand-in: a real AppCompatActivity that is
   * never started or shown, whose Context is the application. Anything a plugin does with it as a
   * Context behaves exactly as before. Opening plugin settings instead reloads that plugin with
   * CloudStreamSettingsActivity, a real host window. Background loading keeps this stand-in so it
   * cannot hold the main activity alive or unexpectedly show a window.
   *
   * An Activity must be constructed on the main thread (its lifecycle insists), while plugins load on
   * an IO thread; so it is built there once and shared. Should that ever fail, plugins get the
   * application, which is what they had before.
   */
  fun pluginHost(context: Context): Context {
    host?.let { return it }
    synchronized(this) {
      host?.let { return it }
      val created = onMainThread { CloudStreamPluginHost(context.applicationContext) }
      host = created
      return created ?: context.applicationContext
    }
  }

  private fun <T : Any> onMainThread(block: () -> T): T? {
    val main = Looper.getMainLooper()
    if (Looper.myLooper() == main) return runCatching(block).onFailure { Log.w(TAG, "Could not create the plugin host", it) }.getOrNull()
    var result: T? = null
    val done = CountDownLatch(1)
    Handler(main).post {
      result = runCatching(block).onFailure { Log.w(TAG, "Could not create the plugin host", it) }.getOrNull()
      done.countDown()
    }
    return if (done.await(5, TimeUnit.SECONDS)) result else null
  }
}

/** The stand-in activity described at [CloudStreamRuntime.pluginHost]. Never started, never shown. */
private class CloudStreamPluginHost(base: Context) : AppCompatActivity() {
  init {
    attachBaseContext(base)
  }
}
