package net.streamdek.mobile.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing the colour a page is painted with.
 *
 * These guard two failures that are invisible to a compiler and obvious to a viewer: picking the
 * shadow at the edge of a poster instead of the poster's colour, and painting a light-themed page
 * dark, which leaves dark text on a dark ground.
 */
class DominantColorTest {

  private fun pixel(red: Int, green: Int, blue: Int, alpha: Int = 255): Int =
    (alpha shl 24) or (red shl 16) or (green shl 8) or blue

  /**
   * WCAG contrast between a page colour and the text that will sit on it.
   *
   * The property worth testing is legibility, not a raw brightness number: a pale saturated blue
   * and a pale yellow have very different luminance and are both perfectly readable under dark text.
   */
  private fun contrast(background: Color, text: Color): Float {
    val a = background.luminance() + 0.05f
    val b = text.luminance() + 0.05f
    return if (a > b) a / b else b / a
  }

  private val darkText = Color(0xFF101114)
  private val lightText = Color(0xFFF2F3F5)

  private fun hueOf(color: Color): Float =
    DominantColor.rgbToHsv(
      (color.red * 255).toInt(),
      (color.green * 255).toInt(),
      (color.blue * 255).toInt(),
    ).first

  @Test
  fun `a light theme gets a pale page, a dark theme a deep one`() {
    val artworkRed = Triple(200, 40, 40)
    val light = DominantColor.settleForBackground(artworkRed.first, artworkRed.second, artworkRed.third, lightTheme = true)
    val dark = DominantColor.settleForBackground(artworkRed.first, artworkRed.second, artworkRed.third, lightTheme = false)

    // The regression this exists for: a light theme was painted the dark colour, so the theme's
    // dark text sat on a dark page and titles could not be read.
    assertTrue("light page must carry dark text, contrast ${contrast(light, darkText)}", contrast(light, darkText) >= 4.5f)
    assertTrue("dark page must carry light text, contrast ${contrast(dark, lightText)}", contrast(dark, lightText) >= 4.5f)
  }

  @Test
  fun `the artwork's hue survives in both themes`() {
    val blue = Triple(30, 60, 220)
    val light = DominantColor.settleForBackground(blue.first, blue.second, blue.third, lightTheme = true)
    val dark = DominantColor.settleForBackground(blue.first, blue.second, blue.third, lightTheme = false)
    val sourceHue = DominantColor.rgbToHsv(blue.first, blue.second, blue.third).first

    assertEquals(sourceHue, hueOf(light), 2f)
    assertEquals(sourceHue, hueOf(dark), 2f)
  }

  @Test
  fun `every title yields a page of comparable weight`() {
    // Whatever the artwork, the page has to be as readable as any other page — otherwise the
    // reading experience changes from title to title.
    val samples = listOf(
      Triple(255, 255, 0), Triple(10, 10, 90), Triple(120, 200, 120), Triple(240, 120, 20),
    )
    samples.forEach { (r, g, b) ->
      val dark = DominantColor.settleForBackground(r, g, b, lightTheme = false)
      val light = DominantColor.settleForBackground(r, g, b, lightTheme = true)
      assertTrue("dark page fails its text for ($r,$g,$b)", contrast(dark, lightText) >= 4.5f)
      assertTrue("light page fails its text for ($r,$g,$b)", contrast(light, darkText) >= 4.5f)
      // And neither may drift to the other polarity, whatever the artwork.
      assertTrue("dark page too bright for ($r,$g,$b)", dark.luminance() < 0.20f)
      assertTrue("light page too dark for ($r,$g,$b)", light.luminance() > 0.45f)
    }
  }

  @Test
  fun `the colour comes from the artwork, not from its shadows`() {
    // Two thirds near-black, one third a strong teal — the naive "most common colour" answer is the
    // black, and a page painted that would look broken rather than matched.
    val pixels = IntArray(90) { index ->
      if (index < 60) pixel(6, 6, 8) else pixel(20, 160, 170)
    }

    val color = DominantColor.pageColorFrom(pixels, lightTheme = false)

    assertNotNull(color)
    val hue = hueOf(color!!)
    assertTrue("expected a teal hue, got $hue", hue in 160f..200f)
  }

  @Test
  fun `a blown-out sky does not win either`() {
    val pixels = IntArray(90) { index ->
      if (index < 60) pixel(252, 252, 250) else pixel(190, 60, 40)
    }

    val hue = hueOf(DominantColor.pageColorFrom(pixels, lightTheme = false)!!)

    assertTrue("expected a red hue, got $hue", hue < 25f || hue > 340f)
  }

  @Test
  fun `an image with no colour in it produces no page colour`() {
    // All greys: there is nothing to match, and a guess would look worse than the theme background.
    val pixels = IntArray(40) { pixel(128, 128, 128) }

    assertNull(DominantColor.pageColorFrom(pixels, lightTheme = false))
    assertNull(DominantColor.pageColorFrom(IntArray(0), lightTheme = false))
  }

  @Test
  fun `fully transparent pixels are ignored`() {
    val pixels = IntArray(40) { index ->
      if (index < 30) pixel(255, 0, 0, alpha = 0) else pixel(20, 160, 170)
    }

    val hue = hueOf(DominantColor.pageColorFrom(pixels, lightTheme = false)!!)

    assertTrue("transparent red should not have won, got $hue", hue in 160f..200f)
  }

  @Test
  fun `sampling asks for a small copy of the artwork, not the original`() {
    // The colour used to arrive with the hero because it downloaded the same original to look at
    // 48x48 pixels of it.
    assertEquals(
      "https://image.tmdb.org/t/p/w185/abc.jpg",
      DominantColor.samplingUrl("https://image.tmdb.org/t/p/original/abc.jpg"),
    )
    assertEquals(
      "https://image.tmdb.org/t/p/w185/abc.jpg",
      DominantColor.samplingUrl("https://image.tmdb.org/t/p/w1280/abc.jpg"),
    )
    // Any other host is left alone: the full URL is all there is to ask for.
    assertEquals("https://art.example.com/big.jpg", DominantColor.samplingUrl("https://art.example.com/big.jpg"))
  }
}
