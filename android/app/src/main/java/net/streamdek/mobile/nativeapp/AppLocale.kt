package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

internal const val APP_SETTINGS_PREFERENCES = "streamdek_native_app_settings"
internal const val APP_LANGUAGE_PREFERENCE = "app_language"

internal val supportedAppLanguages = linkedMapOf(
  "en" to "English",
  "es" to "Español",
  "fr" to "Français",
  "it" to "Italiano",
  "nl" to "Nederlands",
)

internal fun normalizeAppLanguage(value: String?): String =
  value?.trim()?.lowercase()?.takeIf(supportedAppLanguages::containsKey) ?: "en"

fun localizedAppContext(context: Context): Context {
  val language = normalizeAppLanguage(
    context.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
      .getString(APP_LANGUAGE_PREFERENCE, "en"),
  )
  val locale = Locale.forLanguageTag(language)
  // Only override locale/layoutDirection here. Copying the full current
  // Configuration (as context.resources.configuration would) pins every
  // other field - including uiMode's day/night bits - to whatever it was
  // at process start, which breaks "Follow System" theme tracking.
  val configuration = Configuration()
  configuration.setLocale(locale)
  configuration.setLayoutDirection(locale)
  return context.createConfigurationContext(configuration)
}