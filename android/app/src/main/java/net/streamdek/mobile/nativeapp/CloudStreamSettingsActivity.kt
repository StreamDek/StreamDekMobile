package net.streamdek.mobile.nativeapp

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.plugins.Plugin
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.streamdek.mobile.R

/** A real window for the settings UI supplied by an installed CloudStream plugin. */
class CloudStreamSettingsActivity : AppCompatActivity() {
  private var pluginPath: String? = null
  private var opened = false

  override fun onCreate(savedInstanceState: Bundle?) {
    // Plugin fragments often have constructor arguments and live in a separate class loader.
    // Reopen through the plugin callback instead of letting FragmentManager restore them blind.
    super.onCreate(null)
    val path = intent.getStringExtra(EXTRA_PLUGIN_PATH)
    // Only installed, enabled sources can be opened; this activity is not exported.
    val entry = if (CloudStreamPlugins.isInitialized) CloudStreamPlugins.manager.state.providers
      .firstOrNull { it.installedFilePath == path && it.enabled } else null
    if (entry == null || path == null) { finish(); return }
    pluginPath = path
    val status = TextView(this).apply { setText(R.string.plugin_reading_settings) }
    setContentView(LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      val inset = (24 * resources.displayMetrics.density).toInt()
      setPadding(inset, inset, inset, inset)
      addView(status)
      addView(Button(this@CloudStreamSettingsActivity).apply {
        setText(R.string.action_close)
        setOnClickListener { finish() }
      })
    })
    supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
      override fun onFragmentDetached(fm: FragmentManager, fragment: Fragment) {
        if (opened && fm.fragments.isEmpty()) finish()
      }
    }, false)
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        // Reconstruct with the real activity: some plugins capture load()'s host in their callback.
        CloudStreamPluginLoader.unload(path)
        CloudStreamPluginLoader.load(this@CloudStreamSettingsActivity, File(path))
      }
      result.fold(onSuccess = { loaded ->
        val settings = (loaded.instance as? Plugin)?.openSettings
        if (settings == null) {
          status.setText(R.string.plugin_no_settings)
        } else {
          runCatching {
            settings(this@CloudStreamSettingsActivity)
            opened = true
            status.setText(R.string.cloudstream_subsources_help)
          }.onFailure { status.setText(R.string.cloudstream_settings_failed) }
        }
      }, onFailure = { status.setText(R.string.cloudstream_settings_failed) })
    }
  }

  override fun onDestroy() {
    val path = pluginPath
    super.onDestroy()
    if (path != null && isFinishing) {
      // Registration is decided at load time. Re-read saved switches and notify the Home layout.
      reloadScope.launch {
        CloudStreamPluginLoader.unload(path)
        if (CloudStreamPlugins.isInitialized) CloudStreamPlugins.manager.loadEnabledProviders()
      }
    }
  }

  companion object {
    const val EXTRA_PLUGIN_PATH = "plugin_path"
    private val reloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }
}
