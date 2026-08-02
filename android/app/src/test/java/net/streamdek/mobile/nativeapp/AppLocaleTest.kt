package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleTest {
  @Test
  fun supportedLanguagesAreNormalized() {
    assertEquals("en", normalizeAppLanguage("EN"))
    assertEquals("es", normalizeAppLanguage(" es "))
    assertEquals("fr", normalizeAppLanguage("fr"))
    assertEquals("it", normalizeAppLanguage("it"))
    assertEquals("nl", normalizeAppLanguage("nl"))
  }

  @Test
  fun unsupportedOrMissingLanguageFallsBackToEnglish() {
    assertEquals("en", normalizeAppLanguage(null))
    assertEquals("en", normalizeAppLanguage("de"))
  }
}