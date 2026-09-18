package net.streamdek.mobile.nativeapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.plugins.Plugin
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.streamdek.mobile.R

/** The colours StreamDek is painting with right now, for windows outside its main composition. */
object PluginSettingsTheme {
  @Volatile var colorScheme: ColorScheme? = null
}

/**
 * Hosts the settings UI supplied by an installed CloudStream plugin, as a StreamDek popup.
 *
 * Plugins show their settings as a dialog or bottom sheet on the AppCompatActivity CloudStream hands
 * them, and StreamDek's own activity is not one, so this activity is that host. Its window is
 * transparent (Theme.StreamDek.PluginSettings) and the plugin's own dialog is kept invisible: its
 * contents are read by [PluginSettingsMirror] and drawn as a StreamDek dialog over the screen the
 * viewer came from. A screen that cannot be read that way is shown as the plugin drew it.
 *
 * Several plugins (CNCVerse's PlayZTV and SKTech among them) register their sub-sources from these
 * settings when they load, write them only when Save is pressed, and then offer to restart the app.
 * StreamDek presses Save itself on close, never shows that prompt, and reloads the plugin instead.
 */
class CloudStreamSettingsActivity : AppCompatActivity() {
  private sealed interface Screen {
    data object Loading : Screen
    data class Mirrored(val parts: List<PluginSettingsMirror.Part>) : Screen
    /** The plugin's own dialog is on screen; this window only waits for it to close. */
    data object PluginDrawn : Screen
  }

  private var pluginPath: String? = null
  private var screen by mutableStateOf<Screen>(Screen.Loading)
  private var mirroredFragment: DialogFragment? = null
  private var changed = false
  private var closing = false
  private var suppressPluginDialogs = false
  /** The extensions' switches as they were when this screen opened; see [onCreate]. */
  private var switchesBefore: Map<String, Map<String, Any>>? = null

  override fun attachBaseContext(newBase: Context) {
    // The app's language and appearance, as MainActivity has them.
    super.attachBaseContext(themedAppContext(localizedAppContext(newBase)))
  }

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
    // Taken before the extension is loaded here, so everything the visit changes - and only that -
    // is recorded as the viewer's choice when the screen closes. See CloudStreamSourceSettings.kt.
    switchesBefore = CloudStreamSourcePrefs.snapshot(this)

    setContent { PluginSettingsScreen(sourceName = entry.name) }

    supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
      override fun onFragmentViewCreated(fm: FragmentManager, fragment: Fragment, view: View, savedInstanceState: Bundle?) {
        val dialogFragment = fragment as? DialogFragment ?: return
        if (mirroredFragment != null) return
        val parts = PluginSettingsMirror.read(view)
        if (parts != null && parts.any { it !is PluginSettingsMirror.Part.Text }) {
          mirroredFragment = dialogFragment
          hidePluginDialog(dialogFragment)
          screen = Screen.Mirrored(parts)
        } else {
          interceptCommitButtons(view)
          screen = Screen.PluginDrawn
        }
      }

      override fun onFragmentDetached(fm: FragmentManager, fragment: Fragment) {
        if (screen != Screen.Loading && fm.fragments.none { it is DialogFragment }) finish()
      }
    }, false)

    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        // Reconstruct with the real activity: some plugins capture load()'s host in their callback.
        CloudStreamPluginLoader.unload(path)
        CloudStreamPluginLoader.load(this@CloudStreamSettingsActivity, File(path))
      }
      val settings = result.getOrNull()?.let { (it.instance as? Plugin)?.openSettings }
      val failure = when {
        result.isFailure -> R.string.cloudstream_settings_failed
        settings == null -> R.string.plugin_no_settings
        else -> runCatching {
          settings(this@CloudStreamSettingsActivity)
          // Brings the plugin's fragment, and so its view, up now rather than on a later frame.
          supportFragmentManager.executePendingTransactions()
        }
          .onFailure { Log.w(TAG, "Plugin settings failed to open for ${entry.name}", it) }
          .fold(onSuccess = { null }, onFailure = { R.string.cloudstream_settings_failed })
      }
      if (failure != null) {
        Toast.makeText(this@CloudStreamSettingsActivity, failure, Toast.LENGTH_LONG).show()
        finish()
      } else if (screen == Screen.Loading) {
        // A plain dialog rather than a fragment: nothing to read, so it stays as the plugin drew it.
        screen = Screen.PluginDrawn
      }
    }
  }

  /**
   * While set, a plugin cannot open a window from this activity: Dialog's constructor finds no
   * window manager and throws, which the caller catches. Only held around presses StreamDek makes on
   * the plugin's behalf, so its "restart the app to apply" prompt never appears.
   */
  override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences {
    CloudStreamSourcePrefs.noteOpened(this, name)
    return super.getSharedPreferences(name, mode)
  }

  override fun getSystemService(name: String): Any? =
    if (suppressPluginDialogs && name == Context.WINDOW_SERVICE) null else super.getSystemService(name)

  private fun withoutPluginDialogs(block: () -> Unit) {
    suppressPluginDialogs = true
    try {
      runCatching(block).onFailure { Log.i(TAG, "Plugin action finished early: ${it.javaClass.simpleName}") }
    } finally {
      suppressPluginDialogs = false
    }
  }

  private fun hidePluginDialog(fragment: DialogFragment) {
    val window = fragment.dialog?.window ?: return
    window.setWindowAnimations(0)
    window.setDimAmount(0f)
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    window.attributes = window.attributes.apply { alpha = 0f }
  }

  /**
   * For a plugin screen shown as drawn: its Save-style buttons keep their behaviour, minus the restart
   * prompt. Reaches the plugin's listener through View's hidden listener record; where that is not
   * allowed the button is left alone and the prompt appears as before.
   */
  private fun interceptCommitButtons(view: View) {
    if (view is android.view.ViewGroup) for (index in 0 until view.childCount) interceptCommitButtons(view.getChildAt(index))
    val label = (view as? android.widget.TextView)?.text?.toString() ?: view.contentDescription?.toString()
    if (!view.hasOnClickListeners() || !PluginSettingsMirror.isCommitLabel(label)) return
    val original = runCatching {
      val info = View::class.java.getDeclaredMethod("getListenerInfo").apply { isAccessible = true }.invoke(view)
      info?.javaClass?.getDeclaredField("mOnClickListener")?.apply { isAccessible = true }?.get(info) as? View.OnClickListener
    }.getOrNull() ?: return
    view.setOnClickListener { clicked ->
      withoutPluginDialogs { original.onClick(clicked) }
      Toast.makeText(this, R.string.plugin_settings_apply_on_close, Toast.LENGTH_SHORT).show()
    }
  }

  private fun refreshMirror() {
    val view = mirroredFragment?.view ?: return
    val parts = PluginSettingsMirror.read(view) ?: return
    val current = screen
    if (current is Screen.Mirrored && current.parts != parts) screen = Screen.Mirrored(parts)
  }

  /** Writes what the viewer chose through the plugin's own Save, then closes. */
  private fun closeAndApply() {
    if (closing) return
    closing = true
    val current = screen
    if (changed && current is Screen.Mirrored) {
      current.parts.filterIsInstance<PluginSettingsMirror.Part.Action>().filter { it.commits && it.enabled }
        .forEach { action -> withoutPluginDialogs { action.view.performClick() } }
    }
    supportFragmentManager.fragments.filterIsInstance<DialogFragment>().forEach { runCatching { it.dismissAllowingStateLoss() } }
    finish()
  }

  override fun onDestroy() {
    val path = pluginPath
    super.onDestroy()
    if (path != null && isFinishing) {
      // Registration is decided at load time. Re-read saved switches and notify the Home layout.
      val before = switchesBefore
      reloadScope.launch {
        // Recorded before the reload, and from the stores rather than the screen: the extension's
        // own Save is what wrote them, and what it wrote is what the other devices should get.
        if (before != null && CloudStreamPlugins.isInitialized) {
          runCatching { CloudStreamPlugins.manager.recordSourceVisit(path, before) }
            .onFailure { Log.w(TAG, "Could not record source switches", it) }
        }
        CloudStreamPluginLoader.unload(path)
        if (CloudStreamPlugins.isInitialized) CloudStreamPlugins.manager.loadEnabledProviders()
      }
    }
  }

  @Composable
  private fun PluginSettingsScreen(sourceName: String) {
    MaterialTheme(
      colorScheme = PluginSettingsTheme.colorScheme ?: darkColorScheme(),
      shapes = Shapes(
        extraSmall = StreamDekRadius.controlShape,
        small = StreamDekRadius.thumbShape,
        medium = StreamDekRadius.cardShape,
        large = StreamDekRadius.panelShape,
        extraLarge = StreamDekRadius.sheetShape,
      ),
    ) {
      when (val current = screen) {
        Screen.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
          Surface(shape = StreamDekRadius.panelShape, color = MaterialTheme.colorScheme.surface) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp).size(36.dp), strokeWidth = 3.dp)
          }
        }
        is Screen.Mirrored -> MirroredSettingsDialog(sourceName, current.parts)
        // Nothing of this window shows, so a tap that reaches it means the plugin's dialog is gone -
        // including a plain dialog, which leaves no fragment behind to watch.
        Screen.PluginDrawn -> Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { finish() })
      }
    }
  }

  @Composable
  private fun MirroredSettingsDialog(sourceName: String, parts: List<PluginSettingsMirror.Part>) {
    // Plugins can change their screen on their own, after a fetch finishes or a choice unlocks more.
    LaunchedEffect(Unit) {
      while (true) {
        delay(MIRROR_REFRESH_MS)
        refreshMirror()
      }
    }
    val title = parts.firstOrNull { it is PluginSettingsMirror.Part.Text && it.emphasis == PluginSettingsMirror.Emphasis.Title } as? PluginSettingsMirror.Part.Text
    val shown = PluginSettingsMirror.alphabetised(parts.filter { it !== title && !(it is PluginSettingsMirror.Part.Action && it.commits) })
    val toggleCount = shown.count { it is PluginSettingsMirror.Part.Toggle }
    var query by remember { mutableStateOf("") }
    val visible = if (query.isBlank()) shown else shown.filter { it !is PluginSettingsMirror.Part.Toggle || it.label.contains(query, ignoreCase = true) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.84f).dp

    Dialog(onDismissRequest = ::closeAndApply, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight),
        shape = StreamDekRadius.panelShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
      ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.plugin_source_settings), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
            Text(title?.text ?: sourceName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
          }
          // A long list of switches, such as a provider's channel groups, is easier to search than scroll.
          if (toggleCount > SEARCH_THRESHOLD) {
            OutlinedTextField(
              value = query,
              onValueChange = { query = it },
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text(stringResource(R.string.hint_search_this_list)) },
              leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
              singleLine = true,
              shape = StreamDekRadius.controlShape,
            )
          }
          LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            itemsIndexed(visible, key = { _, part -> part.key }) { index, part ->
              if (index > 0 && part is PluginSettingsMirror.Part.Toggle && visible[index - 1] is PluginSettingsMirror.Part.Toggle) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)))
              }
              MirroredPart(part)
            }
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = ::closeAndApply, shape = StreamDekRadius.controlShape) { Text(stringResource(R.string.action_done)) }
          }
        }
      }
    }
  }

  @Composable
  private fun MirroredPart(part: PluginSettingsMirror.Part) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    when (part) {
      is PluginSettingsMirror.Part.Text -> Text(
        part.text,
        modifier = Modifier.fillMaxWidth().padding(top = if (part.emphasis == PluginSettingsMirror.Emphasis.Heading) 10.dp else 2.dp, bottom = 6.dp),
        style = when (part.emphasis) {
          PluginSettingsMirror.Emphasis.Title, PluginSettingsMirror.Emphasis.Heading -> MaterialTheme.typography.titleMedium
          PluginSettingsMirror.Emphasis.Body -> MaterialTheme.typography.bodyMedium
          PluginSettingsMirror.Emphasis.Caption -> MaterialTheme.typography.bodySmall
        },
        fontWeight = if (part.emphasis == PluginSettingsMirror.Emphasis.Heading || part.emphasis == PluginSettingsMirror.Emphasis.Title) FontWeight.Bold else null,
        color = if (part.emphasis == PluginSettingsMirror.Emphasis.Heading || part.emphasis == PluginSettingsMirror.Emphasis.Title) onSurface else onSurface.copy(alpha = 0.68f),
      )
      is PluginSettingsMirror.Part.Toggle -> Row(
        modifier = Modifier
          .fillMaxWidth()
          .toggleable(value = part.checked, enabled = part.enabled, role = Role.Switch) {
            part.view.performClick()
            changed = true
            refreshMirror()
          }
          .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(part.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = onSurface)
          part.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = onSurface.copy(alpha = 0.62f)) }
        }
        Switch(
          checked = part.checked,
          onCheckedChange = null,
          enabled = part.enabled,
          colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
          ),
        )
      }
      is PluginSettingsMirror.Part.Input -> {
        OutlinedTextField(
          value = part.value,
          onValueChange = { value ->
            part.view.setText(value)
            changed = true
            refreshMirror()
          },
          label = part.label.takeIf { it.isNotBlank() }?.let { label -> { Text(label) } },
          visualTransformation = if (part.password) PasswordVisualTransformation() else VisualTransformation.None,
          singleLine = true,
          shape = StreamDekRadius.controlShape,
          modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
      }
      is PluginSettingsMirror.Part.Action -> FilledTonalButton(
        onClick = {
          part.view.performClick()
          changed = true
          refreshMirror()
        },
        enabled = part.enabled,
        shape = StreamDekRadius.controlShape,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
      ) { Text(part.label) }
    }
  }

  companion object {
    const val EXTRA_PLUGIN_PATH = "plugin_path"
    private const val TAG = "CloudStreamSettings"
    private const val MIRROR_REFRESH_MS = 500L
    private const val SEARCH_THRESHOLD = 8
    private val reloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }
}
