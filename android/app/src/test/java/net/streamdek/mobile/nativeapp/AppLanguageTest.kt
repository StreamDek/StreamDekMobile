package net.streamdek.mobile.nativeapp

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The interface-language resolution rules.
 *
 * These are the part of the localisation system most worth pinning down and most easily got wrong:
 * every case here is one where getting it subtly wrong shows up as an app in the wrong language for
 * some viewer somewhere, rather than as a failure anyone would notice in development.
 *
 * `deviceLocales` is passed explicitly throughout so none of this needs a device or Robolectric -
 * the framework stub behind [systemPreferredLocales] would throw.
 */
class AppLanguageTest {

    // --- what is stored -----------------------------------------------------------------------

    @Test
    fun `a fresh installation follows the device`() {
        assertEquals(AppLanguage.SystemSelection, AppLanguage.DefaultSelection)
        assertEquals(AppLanguage.SystemSelection, normalizeAppLanguageSelection(null))
        assertEquals(AppLanguage.SystemSelection, normalizeAppLanguageSelection(""))
        assertEquals(AppLanguage.SystemSelection, normalizeAppLanguageSelection("   "))
    }

    @Test
    fun `an explicit choice written by an older build is still honoured`() {
        // Builds before System Default existed stored bare tags and defaulted to "en". Those are
        // real choices somebody made and must not be reinterpreted as "follow the device".
        for (tag in listOf("en", "es", "fr", "it", "nl")) {
            assertEquals(tag, normalizeAppLanguageSelection(tag))
        }
    }

    @Test
    fun `an unrecognised selection falls back to the rule, not to English`() {
        // A language withdrawn from the build, or a value written by a newer version, must leave the
        // viewer following their device rather than pinned to English by a downgrade.
        assertEquals(AppLanguage.SystemSelection, normalizeAppLanguageSelection("kl"))
        assertEquals(AppLanguage.SystemSelection, normalizeAppLanguageSelection("not-a-language"))
    }

    @Test
    fun `stored selections are case and whitespace insensitive`() {
        assertEquals("de", normalizeAppLanguageSelection("  DE  "))
        assertEquals("pt", normalizeAppLanguageSelection("PT"))
    }

    // --- matching a tag -----------------------------------------------------------------------

    @Test
    fun `a regional tag resolves to the language that has the strings`() {
        assertEquals(AppLanguage.Portuguese, AppLanguage.fromTag("pt-BR"))
        assertEquals(AppLanguage.German, AppLanguage.fromTag("de-AT"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en_GB"))
    }

    @Test
    fun `an unsupported tag matches nothing`() {
        assertNull(AppLanguage.fromTag("ja"))
        assertNull(AppLanguage.fromTag(null))
        assertNull(AppLanguage.fromTag(""))
    }

    // --- System Default -----------------------------------------------------------------------

    @Test
    fun `system default takes the device language when it is supported`() {
        assertEquals(
            AppLanguage.French,
            resolveAppLanguage(AppLanguage.SystemSelection, listOf(Locale.forLanguageTag("fr-CA"))),
        )
    }

    @Test
    fun `system default walks the device preference order`() {
        // A phone set to Catalan first and Spanish second should get Spanish, not English: the
        // second preference is a language this viewer reads, and English is only the last resort.
        val locales = listOf("ca-ES", "es-ES", "en-GB").map(Locale::forLanguageTag)
        assertEquals(AppLanguage.Spanish, resolveAppLanguage(AppLanguage.SystemSelection, locales))
    }

    @Test
    fun `system default falls back to English when nothing is supported`() {
        val locales = listOf("ja-JP", "ko-KR").map(Locale::forLanguageTag)
        assertEquals(AppLanguage.English, resolveAppLanguage(AppLanguage.SystemSelection, locales))
    }

    @Test
    fun `system default falls back to English when the device reports nothing`() {
        assertEquals(AppLanguage.English, resolveAppLanguage(AppLanguage.SystemSelection, emptyList()))
    }

    @Test
    fun `an explicit choice ignores the device entirely`() {
        val locales = listOf("de-DE").map(Locale::forLanguageTag)
        assertEquals(AppLanguage.Polish, resolveAppLanguage("pl", locales))
    }

    // --- metadata ----------------------------------------------------------------------------

    @Test
    fun `metadata takes the region from the device when the language agrees`() {
        assertEquals(
            "pt-BR",
            metadataLanguageTag(AppLanguage.Portuguese, listOf(Locale.forLanguageTag("pt-BR"))),
        )
    }

    @Test
    fun `metadata falls back to the language's own default region`() {
        // The device speaks something else entirely, so its country says nothing about which
        // Portuguese this viewer wants.
        assertEquals(
            "pt-PT",
            metadataLanguageTag(AppLanguage.Portuguese, listOf(Locale.forLanguageTag("en-US"))),
        )
        assertEquals("pt-PT", metadataLanguageTag(AppLanguage.Portuguese, emptyList()))
    }

    @Test
    fun `every language names a well formed metadata locale`() {
        for (language in AppLanguage.entries) {
            val tag = metadataLanguageTag(language, emptyList())
            assertTrue("$language: $tag", Regex("^[a-z]{2}-[A-Z]{2}$").matches(tag))
            assertTrue("$language: $tag", tag.startsWith("${language.tag}-"))
        }
    }

    @Test
    fun `accept-language asks for the region, then the language, then English`() {
        assertEquals(
            "pt-BR, pt;q=0.9, en;q=0.8",
            metadataAcceptLanguage(AppLanguage.Portuguese, listOf(Locale.forLanguageTag("pt-BR"))),
        )
    }

    @Test
    fun `accept-language does not ask English to fall back to itself`() {
        assertEquals("en-US, en;q=0.9", metadataAcceptLanguage(AppLanguage.English, emptyList()))
    }

    @Test
    fun `every language produces an accept-language ending in an English fallback`() {
        for (language in AppLanguage.entries.filter { it != AppLanguage.English }) {
            val header = metadataAcceptLanguage(language, emptyList())
            assertTrue(header, header.endsWith("en;q=0.8"))
            assertTrue(header, header.startsWith(language.defaultMetadataTag))
        }
    }

    // --- the list itself ----------------------------------------------------------------------

    @Test
    fun `English is the fallback and the source language`() {
        assertEquals(AppLanguage.English, AppLanguage.Default)
        assertEquals("en", AppLanguage.entries.first().tag)
    }

    @Test
    fun `every language is distinct and self describing`() {
        val tags = AppLanguage.entries.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
        val nativeNames = AppLanguage.entries.map { it.nativeName }
        assertEquals(nativeNames.size, nativeNames.toSet().size)
        for (language in AppLanguage.entries) {
            assertTrue(language.name, language.nativeName.isNotBlank())
            assertTrue(language.name, language.englishName.isNotBlank())
            // The tag is also a values-<tag> resource qualifier, so it has to stay a bare
            // two-letter language code rather than a regional one.
            assertTrue(language.name, Regex("^[a-z]{2}$").matches(language.tag))
        }
    }

    @Test
    fun `the sentinel cannot collide with a language tag`() {
        assertNull(AppLanguage.fromTag(AppLanguage.SystemSelection))
    }
}
