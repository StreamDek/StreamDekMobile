package net.streamdek.mobile.nativeapp

import android.app.UiModeManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

internal const val APP_APPEARANCE_PREFERENCE = "app_appearance"

/**
 * What the Appearance setting asks for: true for Dark, false for Light, null for Follow System.
 *
 * Read straight from preferences rather than from [AppUiState], because everything here runs before
 * there is a composition, a view model or a settings store to ask.
 */
internal fun savedAppNightMode(context: Context): Boolean? =
  when (
    context.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
      .getString(APP_APPEARANCE_PREFERENCE, null)
  ) {
    "Dark" -> true
    "Light" -> false
    else -> null
  }

/**
 * Tells the platform which night mode this app is in, so the splash screen matches the app.
 *
 * The splash is drawn by the system from the manifest theme before a single line of this app runs,
 * which is why it used to follow the phone rather than the Appearance setting: a profile pinned to
 * Dark on a light phone opened onto a white splash and then a black app.
 *
 * [UiModeManager.setApplicationNightMode] is the hook for exactly this. It records the app's night
 * mode with the system, so the next starting window the system builds for us resolves `values-night`
 * the way the app itself will. It is Android 12 and above; below that the starting window cannot be
 * steered, and [themedAppContext] covers everything from the activity onwards instead.
 *
 * Safe to call repeatedly — setting the mode it is already in is not a configuration change.
 */
fun applyAppNightMode(context: Context) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
  val manager = context.getSystemService(UiModeManager::class.java) ?: return
  val mode = when (savedAppNightMode(context)) {
    true -> UiModeManager.MODE_NIGHT_YES
    false -> UiModeManager.MODE_NIGHT_NO
    null -> UiModeManager.MODE_NIGHT_AUTO
  }
  runCatching { manager.setApplicationNightMode(mode) }
}

/**
 * Wraps [context] so `values-night` resources resolve against the Appearance setting rather than
 * the system's.
 *
 * This is what makes the activity's own window background — the colour held between the splash
 * handing over and Compose painting its first frame — match the app on the platforms
 * [applyAppNightMode] cannot reach.
 *
 * Follow System is returned untouched on purpose. Pinning the night bits there would freeze the app
 * at whatever the phone was doing when the process started, which is the bug the sibling
 * [localizedAppContext] takes care to avoid.
 */
fun themedAppContext(context: Context): Context {
  val dark = savedAppNightMode(context) ?: return context
  val configuration = Configuration()
  // Only the night bits are replaced. A bare Configuration() leaves the ui mode *type* undefined,
  // and overriding that would tell the resource resolver this is no longer a normal-mode device.
  configuration.uiMode =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
      (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
  return context.createConfigurationContext(configuration)
}

private val Configuration.nightModeActive: Boolean
  get() = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/**
 * Whether the *device* is in dark mode, kept current while the app runs.
 *
 * This exists because `isSystemInDarkTheme()` cannot answer the question here. It reads the
 * activity's configuration, and the activity's configuration is not the device's: [themedAppContext]
 * pins the night bits of the base context in `attachBaseContext` to whatever Appearance was saved
 * when the process started, and an override installed there cannot be lifted without recreating the
 * activity.
 *
 * That is what broke Follow System. Somebody on Dark who switched to Follow System kept being told
 * the pinned answer — dark — for the rest of the session, so the app stayed dark on a light phone
 * until it was force-stopped, and the setting looked like it did nothing. Light to Follow System
 * failed the same way in the other direction.
 *
 * The application context is never wrapped like that, so its configuration is the app's real one.
 * And when Appearance is Follow System — the only case this value is used in — [applyAppNightMode]
 * has already cleared the per-application night mode override with the platform, which leaves the
 * app's configuration equal to the device's. Component callbacks keep it current, so turning the
 * phone's dark theme on and off while StreamDek is open moves the app with it.
 */
@Composable
fun deviceInDarkTheme(): Boolean {
  val appContext = LocalContext.current.applicationContext
  var dark by remember(appContext) { mutableStateOf(appContext.resources.configuration.nightModeActive) }
  DisposableEffect(appContext) {
    val callbacks = object : ComponentCallbacks {
      override fun onConfigurationChanged(newConfig: Configuration) {
        dark = newConfig.nightModeActive
      }

      override fun onLowMemory() = Unit
    }
    appContext.registerComponentCallbacks(callbacks)
    // The mode can have moved between the initial read above and this registration.
    dark = appContext.resources.configuration.nightModeActive
    onDispose { appContext.unregisterComponentCallbacks(callbacks) }
  }
  // Belt and braces: any configuration change the composition itself sees is also a moment to
  // re-read, so the value cannot be left stale by a callback that never arrives.
  val activityConfiguration = LocalConfiguration.current
  LaunchedEffect(activityConfiguration) {
    dark = appContext.resources.configuration.nightModeActive
  }
  return dark
}
