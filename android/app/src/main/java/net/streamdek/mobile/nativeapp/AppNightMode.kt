package net.streamdek.mobile.nativeapp

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build

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
