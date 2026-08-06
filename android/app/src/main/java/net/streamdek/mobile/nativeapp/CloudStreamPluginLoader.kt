package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.io.File
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

  class LoadedCsPlugin(
    val filePath: String,
    val name: String,
    val version: Int,
    val instance: BasePlugin,
    /** The providers this plugin registered while loading. */
    val providers: List<MainAPI>,
  )

  private val loaded = LinkedHashMap<String, LoadedCsPlugin>()

  fun loadedPlugins(): List<LoadedCsPlugin> = synchronized(loaded) { loaded.values.toList() }

  fun isLoaded(filePath: String): Boolean = synchronized(loaded) { loaded.containsKey(filePath) }

  fun providersFor(filePath: String): List<MainAPI> = synchronized(loaded) { loaded[filePath]?.providers.orEmpty() }

  /** Every provider currently registered by a loaded plugin, in load order. */
  fun allProviders(): List<MainAPI> = loadedPlugins().flatMap { it.providers }

  fun load(context: Context, file: File): Result<LoadedCsPlugin> = runCatching {
    val filePath = file.absolutePath
    synchronized(loaded) { loaded[filePath] }?.let { return@runCatching it }

    CloudStreamRuntime.initialize(context)

    // Android 14+ refuses to load code the app itself wrote unless the file is read-only.
    runCatching { if (!file.setReadOnly()) Log.w(TAG, "Failed to set ${file.name} read-only") }

    val loader = PathClassLoader(filePath, context.classLoader)
    val manifestJson = loader.getResourceAsStream("manifest.json")?.use { stream ->
      JSONObject(stream.bufferedReader().readText())
    } ?: throw IllegalStateException("No manifest.json inside ${file.name} — is this really a .cs3 plugin?")

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
    if (instance is Plugin) instance.load(context) else instance.load()
    val registered = APIHolder.allProviders.toList().filter { candidate -> before.none { it === candidate } }

    val record = LoadedCsPlugin(filePath, name, version, instance, registered)
    synchronized(loaded) { loaded[filePath] = record }
    Log.i(TAG, "Loaded $name (v$version) with ${registered.size} provider(s): ${registered.joinToString { it.name }}")
    record
  }.onFailure { Log.e(TAG, "Failed to load CloudStream plugin ${file.name}", it) }

  fun unload(filePath: String) {
    val record = synchronized(loaded) { loaded.remove(filePath) } ?: return
    runCatching { record.instance.beforeUnload() }
      .onFailure { Log.w(TAG, "beforeUnload failed for ${record.name}", it) }
    runCatching {
      APIHolder.allProviders.removeAll { provider -> record.providers.any { it === provider } }
    }.onFailure { Log.w(TAG, "Failed to unregister providers for ${record.name}", it) }
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
}
