package net.streamdek.mobile.nativeapp

import android.content.res.Resources
import java.util.Locale

/**
 * StreamDek's canonical interface-language model.
 *
 * # One specification, two installations
 *
 * This file is the specification, and - like `AnimationSpeed.kt` beside it - it is deliberately
 * duplicated verbatim, modulo the package line, in the other app. The two apps share no code, so
 * the alternative to duplicating it is the two of them drifting into different ideas of which
 * languages exist, what they are called, and what happens when a device asks for one StreamDek does
 * not have. Tags, native names, ordering, resolution and fallback behaviour must stay identical in
 * both copies; only the storage and the settings row differ.
 *
 * The *selection* is emphatically not shared. It is a property of the installation, not of the
 * account or the viewing profile: a household can reasonably have a television in one language and
 * a phone in another, and somebody who changes the phone has not asked to change the television. So
 * it is written to device-local storage on each side and never travels through SyncDek, the profile
 * document, cloud preferences or the backend - the same rule [AnimationSpeed] follows.
 *
 * # What this is not
 *
 * This is the language of StreamDek's *interface*: its menus, buttons, dialogs, errors and player
 * controls. It is a separate preference from Preferred Audio Language and Preferred Subtitle
 * Language, which describe what should come out of the speakers and appear over the picture, and
 * which live on the profile because they follow the person rather than the device. Someone can
 * reasonably read a Polish interface while listening to English audio. Do not collapse the three.
 *
 * It is also not a translator for anything a metadata provider or a stream source hands us. Film and
 * series titles, cast names, add-on and provider names, file names and URLs stay exactly as they
 * arrive. Where TMDB can supply genuinely localised metadata for the selected language we ask it
 * for that - see [metadataLanguageTag] - but that is a request for a different document, not a
 * translation of this one.
 *
 * # Adding a language
 *
 * Three steps, and no UI file is touched:
 *
 *  1. add an entry here, in both copies of this file;
 *  2. add `values-<tag>/strings.xml` in both apps;
 *  3. add the tag to `res/xml/locales_config.xml` in both apps.
 *
 * `scripts/check-translations.mjs` then validates the result, and the settings row picks the new
 * entry up from [AppLanguage.entries] without being edited.
 */
enum class AppLanguage(
    /** The BCP 47 tag. Also the `values-<tag>` resource qualifier, and the persisted form. */
    val tag: String,
    /**
     * The language's name in itself.
     *
     * Shown in the selector in preference to the English name, so that somebody who has put
     * StreamDek into a language they cannot read can still find their way back out of it: "Polski"
     * is recognisable to a Polish speaker in a list of otherwise unreadable words, where "Polish"
     * rendered in a Polish interface would not be.
     */
    val nativeName: String,
    /** For logs, support and the developer-facing tooling. Never shown to a viewer. */
    val englishName: String,
    /**
     * The locale asked of a metadata provider when the device offers no better region.
     *
     * A bare language is not enough for TMDB, which keys artwork and certifications by region, so
     * each language names the region its speakers are most likely to want. [metadataLanguageTag]
     * overrides this with the device's own country when the two agree - a Brazilian phone in
     * Portuguese asks for `pt-BR` rather than the `pt-PT` named here.
     */
    val defaultMetadataTag: String,
) {
    // Declaration order is the order of the settings selector. English first as the source language
    // and the fallback; the rest in the order the languages were specified.
    English("en", "English", "English", "en-US"),
    French("fr", "Français", "French", "fr-FR"),
    German("de", "Deutsch", "German", "de-DE"),
    Spanish("es", "Español", "Spanish", "es-ES"),
    Italian("it", "Italiano", "Italian", "it-IT"),
    Portuguese("pt", "Português", "Portuguese", "pt-PT"),
    Dutch("nl", "Nederlands", "Dutch", "nl-NL"),
    Polish("pl", "Polski", "Polish", "pl-PL");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        /**
         * The fallback, and the language every string is authored in.
         *
         * `values/strings.xml` - with no qualifier - holds English, so a key missing from a
         * translation resolves to the English text rather than to a crash or a blank label. That is
         * the platform's own behaviour and this model relies on it: see the fallback discussion in
         * [resolveAppLanguage].
         */
        val Default = English

        /**
         * The stored value meaning "whatever the device is set to".
         *
         * Deliberately not a member of the enum. System Default is a rule for *choosing* a language
         * rather than a language, and making it an entry would mean every consumer of an
         * [AppLanguage] having to handle a value that has no strings, no locale and no name of its
         * own.
         */
        const val SystemSelection = "system"

        /** The selection stored for a fresh installation. */
        const val DefaultSelection = SystemSelection

        fun fromTag(tag: String?): AppLanguage? {
            val normalized = tag?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            // Matched on the language subtag alone, so a stored "pt-BR" - or a device asking for
            // "de-AT" - still finds its way to the entry that has the strings.
            val language = normalized.substringBefore('-').substringBefore('_')
            return entries.firstOrNull { it.tag == language }
        }
    }
}

/**
 * Normalises whatever is in storage to either [AppLanguage.SystemSelection] or a supported tag.
 *
 * Anything unrecognised - a language that has since been withdrawn, a corrupted preference, a value
 * written by a newer build - becomes System Default rather than English. Falling back to the *rule*
 * rather than to a fixed language is what stops a downgrade from silently pinning a German phone to
 * an English interface.
 */
fun normalizeAppLanguageSelection(value: String?): String {
    val normalized = value?.trim()?.lowercase().orEmpty()
    if (normalized.isEmpty() || normalized == AppLanguage.SystemSelection) return AppLanguage.SystemSelection
    return AppLanguage.fromTag(normalized)?.tag ?: AppLanguage.SystemSelection
}

/**
 * The device's preferred languages, most-preferred first.
 *
 * Read from [Resources.getSystem], not from the app's own resources: the app's configuration is the
 * one this file has just overridden, so asking it what the device prefers would return StreamDek's
 * own answer and pin System Default to whatever was selected first.
 */
fun systemPreferredLocales(): List<Locale> {
    val locales = Resources.getSystem().configuration.locales
    return (0 until locales.size()).mapNotNull { index -> runCatching { locales[index] }.getOrNull() }
}

/**
 * Turns a stored selection into the language the interface is actually drawn in.
 *
 * For an explicit choice this is just the lookup. For System Default it walks the device's ordered
 * preference list and takes the first language StreamDek has, so a phone set to Catalan then
 * Spanish gets Spanish rather than English; only when none of them is supported does it land on
 * [AppLanguage.Default].
 *
 * [deviceLocales] is a parameter rather than a call so that the resolution rules - which are the
 * part of this file most worth getting right and most easily got wrong - can be exercised by an
 * ordinary JVM unit test, without a device and without the framework stubs throwing.
 */
fun resolveAppLanguage(
    selection: String?,
    deviceLocales: List<Locale> = systemPreferredLocales(),
): AppLanguage {
    val normalized = normalizeAppLanguageSelection(selection)
    if (normalized != AppLanguage.SystemSelection) {
        return AppLanguage.fromTag(normalized) ?: AppLanguage.Default
    }
    return deviceLocales.firstNotNullOfOrNull { AppLanguage.fromTag(it.toLanguageTag()) }
        ?: AppLanguage.Default
}

/**
 * The locale to ask a metadata provider for, given the interface language.
 *
 * Refined with the device's own country when the device speaks the same language, so a Brazilian
 * phone in Portuguese asks TMDB for `pt-BR` and a Portuguese one for `pt-PT`, without either of
 * them needing a separate setting. Falls back to [AppLanguage.defaultMetadataTag].
 *
 * This is a *request*, never a requirement. Callers must keep their existing English and
 * provider-default fallbacks: a synopsis TMDB has not translated must leave the English synopsis on
 * screen, not an empty panel.
 */
fun metadataLanguageTag(
    language: AppLanguage,
    deviceLocales: List<Locale> = systemPreferredLocales(),
): String {
    val country = deviceLocales
        .firstOrNull { it.language.equals(language.tag, ignoreCase = true) }
        ?.country
        ?.takeIf { it.length == 2 }
    return if (country != null) "${language.tag}-${country.uppercase(Locale.ROOT)}" else language.defaultMetadataTag
}

/**
 * The `Accept-Language` value sent with metadata requests.
 *
 * StreamDek does not talk to TMDB directly - the backend does, and forwards what it is asked for -
 * so the interface language reaches metadata as ordinary HTTP content negotiation rather than as a
 * bespoke parameter. That choice is deliberate on two counts: a backend that does not yet act on it
 * ignores it and nothing changes, and the fallback chain requirement 6 asks for is exactly what the
 * header already expresses.
 *
 * The chain is: the regional locale, then the bare language, then English - so `pt-BR, pt;q=0.9,
 * en;q=0.8` asks for Brazilian Portuguese, will take any Portuguese, and would rather have English
 * than nothing. Anything the provider still cannot supply falls to the provider's own default,
 * which is the last link and is the server's to apply.
 *
 * This governs *metadata*, never interface text: a synopsis or a certification label TMDB has
 * translated, not a menu. And a field the provider has not translated must leave the English text
 * on screen rather than blanking the panel - the header asks, it does not require.
 */
fun metadataAcceptLanguage(
    language: AppLanguage,
    deviceLocales: List<Locale> = systemPreferredLocales(),
): String {
    val regional = metadataLanguageTag(language, deviceLocales)
    return buildList {
        add(regional)
        add("${language.tag};q=0.9")
        if (language != AppLanguage.English) add("en;q=0.8")
    }.joinToString(", ")
}
