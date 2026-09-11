package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** The crop-or-fit decision behind [AdaptiveArtwork]; see [chooseArtworkFit]. */
class AdaptiveArtworkTest {
  // The Classic detail hero: height = width x 1.18.
  private val heroWidth = 1000f
  private val heroHeight = 1180f

  @Test
  fun aBackdropInTheTallHeroStaysCropped() {
    assertEquals(ArtworkFit.Crop, chooseArtworkFit(1920f, 1080f, heroWidth, heroHeight, ArtworkCropTolerance.BACKDROP))
  }

  @Test
  fun aWideCardInThePosterSlotIsFittedOverBlur() {
    assertEquals(ArtworkFit.FitOverBlur, chooseArtworkFit(1920f, 1080f, heroWidth, heroHeight, ArtworkCropTolerance.POSTER))
  }

  @Test
  fun portraitAndSquarePostersStayCropped() {
    assertEquals(ArtworkFit.Crop, chooseArtworkFit(2000f, 3000f, heroWidth, heroHeight, ArtworkCropTolerance.POSTER))
    assertEquals(ArtworkFit.Crop, chooseArtworkFit(1000f, 1000f, heroWidth, heroHeight, ArtworkCropTolerance.POSTER))
  }

  @Test
  fun anUltraWideBackdropIsFittedEvenThoughBackdropsMayCrop() {
    assertEquals(ArtworkFit.FitOverBlur, chooseArtworkFit(3600f, 1000f, heroWidth, heroHeight, ArtworkCropTolerance.BACKDROP))
  }

  @Test
  fun aPortraitPosterAcrossAWideTvFrameIsFittedOverBlur() {
    assertEquals(ArtworkFit.FitOverBlur, chooseArtworkFit(2000f, 3000f, 1920f, 1080f, ArtworkCropTolerance.POSTER))
  }

  @Test
  fun anUnknownSizeCropsAsBefore() {
    assertEquals(ArtworkFit.Crop, chooseArtworkFit(0f, 0f, heroWidth, heroHeight, ArtworkCropTolerance.POSTER))
  }
}
