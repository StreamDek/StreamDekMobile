package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.io.File

/**
 * Loads compiled CloudStream provider plugins (`.cs3` files) on-device.
 *
 * FIRST VERSION — READ BEFORE BUILDING.
 *
 * This is ported from CloudStream's own open-source loader
 * (recloudstream/cloudstream, `app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt`),
 * trimmed of the pieces that only make sense inside the real CloudStream app itself
 * (its notification channel, its own `R` string/drawable resources, its settings-persistence
 * keys). The `.cs3` loading mechanism below — PathClassLoader + manifest.json + reflection
 * instantiation — is a faithful port and should behave the same way CloudStream's own loader
 * does, PROVIDED the `com.lagradost.cloudstream3` core classes a plugin's compiled bytecode
 * references (`Plugin`, `BasePlugin`, `MainAPI`, `TvType`, `ExtractorLink`, ...) are actually
 * present on StreamDek's own runtime classpath. That means StreamDek's `build.gradle.kts` needs
 * a real runtime dependency on the cloudstream3 core library — see the comment added there.
 * The exact Maven coordinates and API-compatibility version could not be verified in the
 * environment this was written in (no Android SDK, no Maven network access), so:
 *
 *   1. Confirm the dependency resolves and the versions/apiVersion your plugin repo expects
 *      line up with recloudstream/cloudstream's current plugin-development docs.
 *   2. Expect the first local build to surface dependency version conflicts against
 *      StreamDek's existing OkHttp/Jsoup/coroutines versions — resolve those the normal
 *      Gradle way (forced versions / exclusions) once you see the actual errors.
 *   3. `CloudStreamStreamBridge` (a separate file) turns a loaded plugin's search/loadLinks
 *      results into StreamDek's own stream model using reflection rather than hard-coded
 *      field names, specifically because `ExtractorLink`'s constructor has changed shape
 *      across CloudStream versions — that's the part most likely to need a follow-up pass
 *      once this is building against a real, pinned cloudstream3 version.
 */
object CloudStreamPluginLoader {
  private const val TAG = "CloudStreamPluginLoader"

  class LoadedCsPlugin(
    val filePath: String,
    val name: String,
    val version: Int,
    val instance: Any,
  )

  // Maps plugin file path -> loaded plugin, so the same .cs3 is never instantiated twice.
  private val loaded = LinkedHashMap<String, LoadedCsPlugin>()

  fun loadedPlugins(): List<LoadedCsPlugin> = loaded.values.toList()

  fun isLoaded(filePath: String): Boolean = loaded.containsKey(filePath)

  /**
   * Loads a single `.cs3` file. Returns the loaded plugin on success, or a failure describing
   * what went wrong (missing manifest, class not found, incompatible cloudstream3 version, a
   * `ClassNotFoundException` for `com.lagradost.cloudstream3.*` meaning the runtime dependency
   * above is missing or the wrong version, etc).
   */
  fun load(context: Context, file: File): Result<LoadedCsPlugin> = runCatching {
    val filePath = file.absolutePath
    loaded[filePath]?.let { return@runCatching it }

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

    val pluginClass = loader.loadClass(pluginClassName)
    val instance = pluginClass.getDeclaredConstructor().newInstance()

    // BasePlugin.filename — set reflectively so this file doesn't need a compile-time
    // reference to the cloudstream3 types (keeps this loader buildable even before the
    // dependency below is wired up, so install/list/remove UI can be reviewed independently).
    runCatching {
      val filenameField = instance.javaClass.methods.firstOrNull { it.name == "setFilename" && it.parameterCount == 1 }
      filenameField?.invoke(instance, filePath)
    }

    if (requiresResources) {
      @Suppress("DEPRECATION")
      runCatching {
        val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
        val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
        addAssetPath.invoke(assets, filePath)
        // Deprecated constructor, but it's what CloudStream's own PluginManager uses for this
        // exact purpose (loading a plugin's bundled resources) — there's no non-deprecated
        // replacement that fits this use case.
        val resources = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
        val setResources = instance.javaClass.methods.firstOrNull { it.name == "setResources" && it.parameterCount == 1 }
        setResources?.invoke(instance, resources)
      }.onFailure { Log.w(TAG, "Failed to attach plugin resources for $name", it) }
    }

    // Plugin.load(context) vs BasePlugin.load() — call whichever overload exists.
    val loadWithContext = instance.javaClass.methods.firstOrNull { it.name == "load" && it.parameterCount == 1 }
    val loadNoArgs = instance.javaClass.methods.firstOrNull { it.name == "load" && it.parameterCount == 0 }
    when {
      loadWithContext != null -> loadWithContext.invoke(instance, context)
      loadNoArgs != null -> loadNoArgs.invoke(instance)
      else -> throw IllegalStateException("$pluginClassName has no load() entry point — is the cloudstream3 dependency the right version?")
    }

    val record = LoadedCsPlugin(filePath, name, version, instance)
    loaded[filePath] = record
    record
  }.onFailure { Log.e(TAG, "Failed to load CloudStream plugin ${file.name}", it) }

  fun unload(filePath: String) {
    val record = loaded.remove(filePath) ?: return
    runCatching {
      val beforeUnload = record.instance.javaClass.methods.firstOrNull { it.name == "beforeUnload" && it.parameterCount == 0 }
      beforeUnload?.invoke(record.instance)
    }.onFailure { Log.w(TAG, "beforeUnload failed for ${record.name}", it) }
  }
}
