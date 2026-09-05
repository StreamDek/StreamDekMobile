package net.streamdek.mobile.nativeapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * The Compose half of StreamDek's interface-language model.
 *
 * The specification itself - which languages exist, how a selection resolves, what metadata is asked
 * for - is `AppLanguage.kt`, which is deliberately free of Compose so that the data layer can read it
 * without reaching into the UI layer and so that all of it is reachable from a plain JVM test. This
 * file is only the part that has to know about a composition.
 */

/** The interface language of the surrounding composition. */
val LocalAppLanguage: ProvidableCompositionLocal<AppLanguage> =
    staticCompositionLocalOf { AppLanguage.Default }

/**
 * Draws [content] in the selected interface language.
 *
 * # Why this exists rather than a call to recreate()
 *
 * Applying a locale by overriding the activity's base context - which [localizedAppContext] does,
 * and which is all this app used to have - cannot take effect until the activity is built again.
 * Recreating it works, but it throws away the composition: on the phone the settings page it was
 * changed from disappears and rebuilds, and on the television every focus requester in the tree is
 * reconstructed, which drops the remote's focus back to wherever the screen decides to put it.
 * Changing the language is not supposed to feel like the app restarted, and losing your place on a
 * television is exactly what that feels like.
 *
 * Overriding the composition locals instead is what `stringResource` actually reads: it looks up
 * `LocalContext.current.resources`, and takes a dependency on `LocalConfiguration.current` so that
 * a configuration change invalidates it. Providing both means every string in the tree re-resolves
 * on the next recomposition, in place, with the scroll position, the open dialog and the focused
 * control all still where they were.
 *
 * [localizedAppContext] is still needed and still runs, for everything that is not this composition:
 * the window the activity opens with, notification channel names, and anything a service builds.
 *
 * # Why the configuration is copied here but not there
 *
 * [localizedAppContext] builds a bare `Configuration` precisely so it does not pin the day/night
 * bits at process start - see the comment there. Here the opposite is correct: [LocalConfiguration]
 * is live, `remember` is keyed on it, and so copying it preserves every field the platform is
 * currently reporting (night mode, font scale, screen size) while replacing only the locale, and
 * re-copies the moment any of them changes.
 */
@Composable
fun ProvideAppLocale(selection: String?, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    // Keyed on the configuration as well as the selection so that System Default follows a device
    // language changed while StreamDek is running, rather than answering with what the device said
    // at launch.
    val language = remember(selection, baseConfiguration) { resolveAppLanguage(selection) }
    val localizedConfiguration = remember(baseConfiguration, language) {
        Configuration(baseConfiguration).apply {
            val locale = language.locale
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localizedContext = remember(baseContext, localizedConfiguration) {
        LocalizedContextWrapper(
            baseContext,
            baseContext.createConfigurationContext(localizedConfiguration).resources,
        )
    }
    // Taken from the configuration rather than assumed, so that adding an RTL language later is the
    // three steps in the [AppLanguage] documentation and not a layout project.
    val layoutDirection = remember(localizedConfiguration) {
        if (localizedConfiguration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }
    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
        LocalLayoutDirection provides layoutDirection,
        content = content,
    )
}

/**
 * A context that resolves resources in the interface language while still being the activity.
 *
 * The obvious implementation - handing the composition `createConfigurationContext(...)` straight -
 * is wrong in a way that does not appear until something asks the context who owns it.
 * `createConfigurationContext` does not return a wrapper around the activity: it returns a fresh
 * `ContextImpl` parented to the application, with the activity nowhere in its base-context chain.
 *
 * androidx finds the `ActivityResultRegistryOwner`, the `OnBackPressedDispatcherOwner` and their
 * siblings by walking `baseContext` up from `LocalContext`, and that walk stops dead at a
 * `ContextImpl`. Providing one as `LocalContext` therefore threw
 * "No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner" out of
 * `rememberLauncherForActivityResult` on the very first composition, and left every
 * [findActivity] in the app quietly answering null.
 *
 * Wrapping keeps the activity in the chain where those lookups can reach it. Overriding
 * `getResources` is the whole of what the language needs: `stringResource` reads
 * `LocalContext.current.resources`, and everything else about the context stays exactly as it was.
 */
private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources,
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

/**
 * The activity behind a context, however many wrappers deep.
 *
 * [ProvideAppLocale] hands the composition a [LocalizedContextWrapper] rather than the activity
 * itself, so `LocalContext.current as? Activity` - which is what this replaces - silently became
 * null the moment the locale was applied. Walking the base-context chain is correct with or without
 * the wrapper, and is the form that survives the next thing that wraps the context.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Locale-aware formatting for values StreamDek itself renders.
 *
 * Anything with a decimal point, a thousands separator, a percent sign or a date in it goes through
 * here rather than through `String.format`, `"%.1f"` or a hand-built `SimpleDateFormat` with an
 * English pattern - those all resolve against the device's default locale, which is not the
 * interface language, and several of them assume an English separator outright.
 *
 * Durations are not here. A running time is rendered by the player as digits and colons, which is
 * not a locale-varying format, and pushing it through a formatter would only invite a thousands
 * separator into a timecode.
 */
object AppFormats {
    fun number(language: AppLanguage, value: Number, decimals: Int = 0): String =
        NumberFormat.getNumberInstance(language.locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)

    fun percent(language: AppLanguage, fraction: Double, decimals: Int = 0): String =
        NumberFormat.getPercentInstance(language.locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(fraction)

    fun date(language: AppLanguage, millis: Long, style: Int = DateFormat.MEDIUM): String =
        DateFormat.getDateInstance(style, language.locale).format(Date(millis))

    fun time(language: AppLanguage, millis: Long, style: Int = DateFormat.SHORT): String =
        DateFormat.getTimeInstance(style, language.locale).format(Date(millis))
}
