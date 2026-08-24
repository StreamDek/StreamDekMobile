package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recognising a language however it was written down.
 *
 * The point of this table is that the same language reaches the app as a two-letter code from a
 * container, a three-letter one from a different muxer, a locale tag from a stream manifest and a
 * plain English word from a subtitle add-on — and a preference that only matches one of those forms
 * silently fails to apply.
 */
class LanguagesTest {

  @Test
  fun `the list covers every ISO language, not a hand-picked few`() {
    // The hardcoded lists this replaced held nine and thirteen entries.
    assertTrue("expected the full ISO 639-1 set, got ${Languages.all.size}", Languages.all.size > 150)
    assertTrue(Languages.all.any { it.code == "vi" })
    assertTrue(Languages.all.any { it.code == "ta" })
    assertTrue(Languages.all.any { it.code == "cy" })
    assertTrue(Languages.all.all { it.label.isNotBlank() })
  }

  @Test
  fun `the list is sorted by name and free of duplicates`() {
    val labels = Languages.all.map { it.label.lowercase() }
    assertEquals(labels.sorted(), labels)
    assertEquals(Languages.all.size, Languages.all.map { it.code }.toSet().size)
  }

  @Test
  fun `two and three letter codes resolve to the same language`() {
    assertEquals("en", Languages.normalize("en"))
    assertEquals("en", Languages.normalize("eng"))
    assertEquals("fr", Languages.normalize("fra"))
    // The bibliographic form, which the JVM does not answer with but muxers still write.
    assertEquals("fr", Languages.normalize("fre"))
    assertEquals("de", Languages.normalize("ger"))
    assertEquals("zh", Languages.normalize("chi"))
    assertEquals("cs", Languages.normalize("cze"))
  }

  @Test
  fun `names resolve, in English and in the language itself`() {
    assertEquals("vi", Languages.normalize("Vietnamese"))
    assertEquals("de", Languages.normalize("Deutsch"))
    assertEquals("es", Languages.normalize("español"))
    assertEquals("ja", Languages.normalize("JAPANESE"))
  }

  @Test
  fun `region and variant suffixes are dropped`() {
    assertEquals("en", Languages.normalize("en-US"))
    assertEquals("pt", Languages.normalize("pt_BR"))
    assertEquals("zh", Languages.normalize("zh-Hans-CN"))
    assertEquals("pt", Languages.normalize("pob"))
  }

  @Test
  fun `the notes subtitle listings carry are ignored`() {
    assertEquals("en", Languages.normalize("English (SDH)"))
    assertEquals("en", Languages.normalize("English CC"))
    assertEquals("en", Languages.normalize("English SDH"))
    assertEquals("es", Languages.normalize("Spanish [forced]"))
    assertEquals("en", Languages.normalize("  English  "))
  }

  @Test
  fun `nothing is not something`() {
    assertEquals("", Languages.normalize(null))
    assertEquals("", Languages.normalize(""))
    assertEquals("", Languages.normalize("und"))
    assertEquals("", Languages.normalize("Klingon"))
  }

  @Test
  fun `the two non-languages survive normalisation`() {
    assertEquals(Languages.ORIGINAL, Languages.normalize("original"))
    assertEquals(Languages.NONE, Languages.normalize("none"))
    assertEquals("Original language", Languages.label(Languages.ORIGINAL))
    assertEquals("None", Languages.label(Languages.NONE))
  }

  @Test
  fun `tags cover both three letter forms so either muxer's spelling matches`() {
    assertEquals(listOf("en", "eng"), Languages.tags("en"))
    assertTrue(Languages.tags("fr").containsAll(listOf("fr", "fra", "fre")))
    assertTrue(Languages.tags("de").containsAll(listOf("de", "deu", "ger")))
    // A language with no separate bibliographic form contributes two tags, not a duplicate pair.
    assertEquals(Languages.tags("vi").distinct(), Languages.tags("vi"))
  }

  @Test
  fun `the non-languages match no track`() {
    assertTrue(Languages.tags(Languages.ORIGINAL).isEmpty())
    assertTrue(Languages.tags(Languages.NONE).isEmpty())
    assertTrue(Languages.tags(null).isEmpty())
  }

  @Test
  fun `matching is by language, not by spelling`() {
    assertTrue(Languages.matches("eng", "English"))
    assertTrue(Languages.matches("pt", "pob"))
    assertTrue(Languages.matches("fre", "fra"))
    assertFalse(Languages.matches("en", "es"))
    // Unknown never matches unknown; otherwise every untagged track would look like a hit.
    assertFalse(Languages.matches("und", "und"))
    assertFalse(Languages.matches(null, null))
  }

  @Test
  fun `the pickers offer the extra choices only where they make sense`() {
    assertEquals(Languages.ORIGINAL, Languages.audioOptions().first())
    assertFalse(Languages.audioOptions().contains(Languages.NONE))
    assertEquals(Languages.NONE, Languages.subtitleOptions(includeNone = true).first())
    assertFalse(Languages.subtitleOptions(includeNone = false).contains(Languages.NONE))
  }
}
