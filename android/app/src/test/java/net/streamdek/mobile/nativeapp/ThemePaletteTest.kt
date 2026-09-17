package net.streamdek.mobile.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {
  private val appearances = listOf(true, false)

  private fun assertContrast(label: String, a: Color, b: Color, minimum: Float) {
    val ratio = contrastRatio(a, b)
    assertTrue("$label: %.2f < $minimum".format(ratio), ratio >= minimum)
  }

  @Test fun `the collection is curated and White is gone`() {
    assertEquals(
      listOf("Amber", "Ocean", "Crimson", "Violet", "Emerald", "Aurora", "Rose", "Monochrome"),
      AppThemePreset.entries.map { it.name },
    )
    assertEquals(AppThemePreset.Monochrome, AppThemePreset.fromName("White"))
    assertEquals(AppThemePreset.Aurora, AppThemePreset.fromName("Aurora"))
    assertNull(AppThemePreset.fromName("Sunset"))
    assertNull(AppThemePreset.fromName(null))
  }

  @Test fun `text and controls stay readable in every theme and appearance`() {
    AppThemePreset.entries.forEach { theme ->
      appearances.forEach { dark ->
        val c = streamDekThemeColors(theme, dark)
        val tag = "$theme ${if (dark) "dark" else "light"}"
        val grounds = if (dark) listOf(StreamDekNeutrals.darkBackground, StreamDekNeutrals.darkSurface)
          else listOf(StreamDekNeutrals.lightBackground, StreamDekNeutrals.lightSurface)
        val surface = grounds.last()
        grounds.forEach { ground ->
          assertContrast("$tag accent text on ground", c.accent, ground, 4.5f)
          assertContrast("$tag progress on ground", c.progress, ground, 3f)
          assertContrast("$tag focus ring on ground", c.focusRing, ground, 3f)
          assertContrast("$tag secondary on ground", c.secondary, ground, 3f)
        }
        assertContrast("$tag text on accent", c.onAccent, c.accent, 4.5f)
        assertContrast("$tag text on secondary", c.onSecondary, c.secondary, 4.5f)
        assertContrast("$tag toggle thumb on track", c.toggleThumb, c.toggleTrack, 4.5f)
        assertContrast("$tag selected chip label", c.onChipSelected, c.chipSelected.compositeOver(surface), 4.5f)
        assertContrast("$tag selected card text", c.onSelectedContainer, c.selectedContainer.compositeOver(surface), 4.5f)
        // The navigation's selected pill is neutral; only its icon is themed.
        val navPill = (if (dark) StreamDekNeutrals.darkOnSurface.copy(alpha = 0.15f) else StreamDekNeutrals.lightOnSurface.copy(alpha = 0.11f)).compositeOver(surface)
        assertContrast("$tag selected nav icon", c.onSelectedContainer, navPill, 4.5f)
      }
    }
  }

  @Test fun `a theme is a system of related tones, not one colour`() {
    AppThemePreset.entries.forEach { theme ->
      appearances.forEach { dark ->
        val tones = themeTones(theme, dark)
        assertNotEquals("$theme highlight", tones.accent, tones.highlight)
        assertNotEquals("$theme deep", tones.accent, tones.deep)
      }
      // The same identity, retuned per appearance rather than reused as-is.
      assertNotEquals("$theme retuned for light", themeTones(theme, true).accent, themeTones(theme, false).accent)
    }
  }

  @Test fun `no two coloured themes share a hue`() {
    fun hue(color: Color): Float {
      val max = maxOf(color.red, color.green, color.blue)
      val min = minOf(color.red, color.green, color.blue)
      val delta = max - min
      if (delta == 0f) return 0f
      val h = when (max) {
        color.red -> ((color.green - color.blue) / delta).mod(6f)
        color.green -> (color.blue - color.red) / delta + 2f
        else -> (color.red - color.green) / delta + 4f
      }
      return h * 60f
    }
    val coloured = AppThemePreset.entries.filter { it != AppThemePreset.Monochrome }
    coloured.forEach { a ->
      coloured.filter { it.ordinal > a.ordinal }.forEach { b ->
        val ha = hue(themeTones(a, true).accent)
        val hb = hue(themeTones(b, true).accent)
        val distance = minOf(kotlin.math.abs(ha - hb), 360f - kotlin.math.abs(ha - hb))
        assertTrue("$a and $b are only %.0f° apart".format(distance), distance >= 20f)
      }
    }
  }

  @Test fun `themes never recolour the page itself`() {
    appearances.forEach { dark ->
      val schemes = AppThemePreset.entries.map { appColorScheme(it, dark) }
      assertEquals(1, schemes.map { it.background }.distinct().size)
      assertEquals(1, schemes.map { it.surface }.distinct().size)
      assertEquals(1, schemes.map { it.onSurface }.distinct().size)
    }
  }

  @Test fun `monochrome adapts to the appearance instead of being white`() {
    val dark = streamDekThemeColors(AppThemePreset.Monochrome, darkMode = true)
    val light = streamDekThemeColors(AppThemePreset.Monochrome, darkMode = false)
    assertTrue(dark.accent.red > 0.9f && dark.onAccent == StreamDekNeutrals.inkDark)
    assertTrue(light.accent.red < 0.15f && light.onAccent == StreamDekNeutrals.inkLight)
  }
}
