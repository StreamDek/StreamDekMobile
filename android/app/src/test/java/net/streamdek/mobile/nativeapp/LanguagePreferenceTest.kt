package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning the viewer's language choices into the order a player is asked to satisfy.
 *
 * The failure these guard against is silent: a preference that produces the wrong tag list does not
 * error, it just plays the wrong audio or the wrong subtitles, and looks like the setting was
 * ignored.
 */
class LanguagePreferenceTest {

  @Test
  fun `the preferred language comes before the secondary`() {
    val tags = orderedLanguageTags("vi", "en")

    assertEquals("vi", tags.first())
    assertTrue(tags.indexOf("vi") < tags.indexOf("en"))
    // Both three-letter forms travel with each language so either muxer's spelling matches.
    assertTrue(tags.containsAll(listOf("vi", "vie", "en", "eng")))
  }

  @Test
  fun `a language outside the old hardcoded list is no longer turned into English`() {
    // The regression this replaced: anything not among nine listed languages fell through to
    // English, so choosing Vietnamese or Tamil silently played English audio.
    assertEquals("vi", normalizePreferredAudioLanguage("vi"))
    assertEquals("ta", normalizePreferredAudioLanguage("Tamil"))
    assertEquals("cy", normalizePreferredAudioLanguage("wel"))
    assertFalse(preferredAudioLanguageTags("vi").contains("en"))
  }

  @Test
  fun `original language asks the player for nothing`() {
    assertTrue(preferredAudioLanguageTags(Languages.ORIGINAL).isEmpty())
    // With no primary to satisfy, a secondary is still worth honouring.
    assertEquals(Languages.tags("en"), orderedLanguageTags(Languages.ORIGINAL, "en"))
  }

  @Test
  fun `none as a secondary contributes nothing`() {
    assertEquals(Languages.tags("fr"), orderedLanguageTags("fr", Languages.NONE))
    assertEquals(Languages.tags("fr"), orderedLanguageTags("fr", ""))
    assertEquals(Languages.tags("fr"), orderedLanguageTags("fr", null))
  }

  @Test
  fun `the same language twice is asked for once`() {
    assertEquals(Languages.tags("en"), orderedLanguageTags("en", "eng"))
  }

  @Test
  fun `the subtitle preference list is ordered, deduplicated and free of non-languages`() {
    assertEquals(listOf("vi", "en"), preferredSubtitleLanguages("vi", "en"))
    assertEquals(listOf("vi"), preferredSubtitleLanguages("vi", Languages.NONE))
    assertEquals(listOf("pt"), preferredSubtitleLanguages("pt", "pob"))
    assertTrue(preferredSubtitleLanguages(Languages.NONE, Languages.NONE).isEmpty())
    assertTrue(preferredSubtitleLanguages(Languages.ORIGINAL, "").isEmpty())
  }

  @Test
  fun `the add-on loading modes are the three offered, defaulting to preferred`() {
    assertEquals(
      listOf(ADDON_SUBTITLE_LOADING_PREFERRED, ADDON_SUBTITLE_LOADING_ALL, ADDON_SUBTITLE_LOADING_OFF),
      addonSubtitleLoadingChoices.map { it.first },
    )
    assertEquals("Preferred languages", addonSubtitleLoadingLabel(ADDON_SUBTITLE_LOADING_PREFERRED))
    assertEquals("Off", addonSubtitleLoadingLabel(ADDON_SUBTITLE_LOADING_OFF))
    // An unknown stored value reads as the default rather than as a blank row.
    assertEquals("Preferred languages", addonSubtitleLoadingLabel("nonsense"))
  }

  @Test
  fun `subtitle source preference offers a combined source view`() {
    assertEquals(listOf("All", "BuiltIn", "Addons"), subtitleSourceChoices.map { it.first })
    assertEquals("All sources", subtitleSourceLabel("All"))
    assertEquals("BuiltIn", normalizeSubtitleDefaultSource("built-in"))
    assertEquals("Addons", normalizeSubtitleDefaultSource("add-ons"))
    assertEquals("All", normalizeSubtitleDefaultSource(null))
    assertEquals("All", normalizeSubtitleDefaultSource("unknown"))
    assertTrue(subtitleSourceIncludesBuiltIn("All"))
    assertTrue(subtitleSourceIncludesAddons("All"))
    assertFalse(subtitleSourceIncludesAddons("BuiltIn"))
    assertFalse(subtitleSourceIncludesBuiltIn("Addons"))
  }

  @Test
  fun `built in and addon subtitle results stay in their own source tabs`() {
    val openSubtitles = externalSubtitleOrigin("opensubtitles")
    val addon = externalSubtitleOrigin("addon:flix-streams")

    assertTrue(subtitleOriginVisible("BuiltIn", openSubtitles))
    assertFalse(subtitleOriginVisible("Addons", openSubtitles))
    assertTrue(subtitleOriginVisible("Addons", addon))
    assertFalse(subtitleOriginVisible("BuiltIn", addon))
    assertTrue(subtitleOriginVisible("All", openSubtitles))
    assertTrue(subtitleOriginVisible("All", addon))
  }

  @Test
  fun `strict subtitle language filtering allows only primary and real secondary`() {
    assertTrue(preferredSubtitleLanguageAllowed("English SDH", "en", Languages.NONE, strict = true))
    assertFalse(preferredSubtitleLanguageAllowed("Hindi", "en", Languages.NONE, strict = true))
    assertTrue(preferredSubtitleLanguageAllowed("eng", "English", "Hindi", strict = true))
    assertTrue(preferredSubtitleLanguageAllowed("hin", "English", "Hindi", strict = true))
    assertFalse(preferredSubtitleLanguageAllowed("French", "English", "Hindi", strict = true))
  }

  @Test
  fun `subtitle source setting is a filter rather than only a landing tab`() {
    assertTrue(subtitleSourceAllowsOrigin("BuiltIn", ExternalSubtitleOrigin.BuiltIn))
    assertFalse(subtitleSourceAllowsOrigin("BuiltIn", ExternalSubtitleOrigin.Addon))
    assertTrue(subtitleSourceAllowsOrigin("Addons", ExternalSubtitleOrigin.Addon))
    assertFalse(subtitleSourceAllowsOrigin("Addons", ExternalSubtitleOrigin.BuiltIn))
    assertTrue(subtitleSourceAllowsOrigin("All", ExternalSubtitleOrigin.BuiltIn))
    assertTrue(subtitleSourceAllowsOrigin("All", ExternalSubtitleOrigin.Addon))
  }
}
