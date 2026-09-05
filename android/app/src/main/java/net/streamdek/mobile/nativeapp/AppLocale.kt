package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import net.streamdek.mobile.R

internal const val APP_SETTINGS_PREFERENCES = "streamdek_native_app_settings"
internal const val APP_LANGUAGE_PREFERENCE = "app_language"

/**
 * The interface language selected on this phone, or [AppLanguage.SystemSelection].
 *
 * Read straight from preferences rather than from [AppUiState], because the callers below run
 * before there is a composition, a view model or a settings store to ask. The stored form is the
 * language tag, so the values written by builds that shipped before System Default existed - "en",
 * "es", "fr", "it", "nl" - are still read back as the explicit choices they were. Only the absence
 * of a value has changed meaning, and only for installations that never touched the setting: they
 * now follow the phone instead of being pinned to English.
 *
 * See `AppLanguage.kt` for why this is stored here and never synced.
 */
internal fun savedAppLanguageSelection(context: Context): String =
    normalizeAppLanguageSelection(
        context.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
            .getString(APP_LANGUAGE_PREFERENCE, null),
    )

/**
 * Wraps [context] so resources resolve in the selected interface language.
 *
 * This covers everything that is not the Compose tree: the window the activity opens with, the
 * notification channel names `StreamDekDownloads` registers, and anything a service builds while no
 * activity exists. The composition itself is handled by [ProvideAppLocale], which can change
 * language without rebuilding the activity; this one is fixed for the life of the process, which is
 * why it must not be the only mechanism.
 */
fun localizedAppContext(context: Context): Context {
    val locale = resolveAppLanguage(savedAppLanguageSelection(context)).locale
    // Only override locale/layoutDirection here. Copying the full current
    // Configuration (as context.resources.configuration would) pins every
    // other field - including uiMode's day/night bits - to whatever it was
    // at process start, which breaks "Follow System" theme tracking.
    val configuration = Configuration()
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return context.createConfigurationContext(configuration)
}

/**
 * The language selector's option values: System Default first, then every supported language.
 *
 * Built from [AppLanguage.entries] rather than written out, so adding a language is the three steps
 * in `AppLanguage.kt` and this row is not one of them.
 */
fun appLanguageSelectionOptions(): List<String> =
    listOf(AppLanguage.SystemSelection) + AppLanguage.entries.map { it.tag }

/**
 * How each option is labelled.
 *
 * Only System Default is translated. The languages are deliberately *not*: each is listed in its own
 * name in every interface language, which is what lets somebody who has put StreamDek into a
 * language they cannot read find their way back - "English" is recognisable in a Polish list, where
 * "Angielski" would not be.
 */
@Composable
fun rememberAppLanguageOptionLabel(): (String) -> String {
    val systemDefault = stringResource(R.string.settings_language_system_default)
    return remember(systemDefault) {
        { option ->
            if (option == AppLanguage.SystemSelection) {
                systemDefault
            } else {
                AppLanguage.fromTag(option)?.nativeName ?: option
            }
        }
    }
}

/**
 * The second line under each option.
 *
 * System Default names the language it currently resolves to, so choosing it is not a guess. The
 * named languages need no gloss beyond their own name, and the separate-preferences note sits under
 * whichever option is not System Default only once, on the row itself.
 */
@Composable
fun rememberAppLanguageOptionDescriptions(): Map<String, String> {
    val resolved = resolveAppLanguage(AppLanguage.SystemSelection)
    val description = stringResource(R.string.settings_language_system_default_description)
    return remember(resolved, description) {
        mapOf(AppLanguage.SystemSelection to "$description (${resolved.nativeName})")
    }
}
