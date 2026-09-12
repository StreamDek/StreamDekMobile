package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassContrastPolicyTest {
  @Test
  fun darkThemeOnlyNeedsHelpOverBrightArtwork() {
    assertEquals(0f, GlassContrastPolicy.artworkNeed(luminance = 0.05f, detail = 0f, lightTheme = false))
    assertTrue(GlassContrastPolicy.artworkNeed(luminance = 0.8f, detail = 0f, lightTheme = false) > 0.9f)
  }

  @Test
  fun lightThemeOnlyNeedsHelpOverDarkArtwork() {
    assertEquals(0f, GlassContrastPolicy.artworkNeed(luminance = 0.9f, detail = 0f, lightTheme = true))
    assertTrue(GlassContrastPolicy.artworkNeed(luminance = 0.02f, detail = 0f, lightTheme = true) > 0.9f)
  }

  @Test
  fun busyTextureAddsProtection() {
    val flat = GlassContrastPolicy.artworkNeed(luminance = 0.3f, detail = 0f, lightTheme = false)
    val busy = GlassContrastPolicy.artworkNeed(luminance = 0.3f, detail = 1f, lightTheme = false)
    assertTrue(busy > flat)
  }

  @Test
  fun scrolledContentUnderChromeGetsAFixedLift() {
    val top = GlassContrastPolicy.protection(GlassContrastZone.TopChrome, backdrop = null, scrolledUnder = true, lightTheme = false, highContrast = false)
    val bottom = GlassContrastPolicy.protection(GlassContrastZone.BottomChrome, backdrop = null, scrolledUnder = true, lightTheme = false, highContrast = false)
    assertEquals(GlassContrastPolicy.SCROLLED_TOP, top)
    assertEquals(GlassContrastPolicy.SCROLLED_BOTTOM, bottom)
    assertEquals(0f, GlassContrastPolicy.protection(GlassContrastZone.None, null, scrolledUnder = true, lightTheme = false, highContrast = false))
  }

  @Test
  fun heroArtworkDrivesProtectionAtTheTop() {
    val bright = ArtworkBands(topLuminance = 0.9f, bottomLuminance = 0.1f, detail = 0f)
    assertTrue(GlassContrastPolicy.protection(GlassContrastZone.TopChrome, bright, scrolledUnder = false, lightTheme = false, highContrast = false) > 0.9f)
    assertEquals(0f, GlassContrastPolicy.protection(GlassContrastZone.BottomChrome, bright, scrolledUnder = false, lightTheme = false, highContrast = false))
  }

  @Test
  fun highContrastTextSetsAFloor() {
    assertEquals(
      GlassContrastPolicy.HIGH_CONTRAST_FLOOR,
      GlassContrastPolicy.protection(GlassContrastZone.BottomChrome, null, scrolledUnder = false, lightTheme = false, highContrast = true),
    )
  }

  @Test
  fun bandsSeparateABrightTopFromADarkBottom() {
    val size = 6
    val white = 0xFFFFFFFF.toInt()
    val black = 0xFF000000.toInt()
    val pixels = IntArray(size * size) { index -> if (index / size < size / 2) white else black }
    val bands = ArtworkBandsSampler.bandsFrom(pixels, size, size)
    assertNotNull(bands)
    assertEquals(1f, bands!!.topLuminance, 0.001f)
    assertEquals(0f, bands.bottomLuminance, 0.001f)
    assertEquals(0f, bands.detail, 0.001f) // no horizontal edges at all
  }

  @Test
  fun checkerboardIsMaximallyDetailed() {
    val size = 4
    val pixels = IntArray(size * size) { index -> if ((index / size + index % size) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
    assertEquals(1f, ArtworkBandsSampler.bandsFrom(pixels, size, size)!!.detail, 0.001f)
  }
}
