package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How add-on card presentation metadata is read and applied; see [PosterShape]. */
class CardPresentationTest {
  @Test
  fun stremioPosterShapesAreRead() {
    assertEquals(PosterShape.Poster, PosterShape.parse("poster"))
    assertEquals(PosterShape.Square, PosterShape.parse("square"))
    assertEquals(PosterShape.Landscape, PosterShape.parse("landscape"))
    assertEquals(PosterShape.Landscape, PosterShape.parse("  Landscape "))
  }

  @Test
  fun absentOrMalformedShapesAreUnspecified() {
    assertNull(PosterShape.parse(null))
    assertNull(PosterShape.parse(""))
    assertNull(PosterShape.parse("circle"))
    assertNull(PosterShape.parse(42))
    assertNull(PosterShape.parse(mapOf("shape" to "landscape")))
  }

  @Test
  fun aRowTakesTheShapeMostOfItsItemsDeclare() {
    val shapes = listOf(PosterShape.Landscape, null, PosterShape.Landscape, PosterShape.Poster)
    assertEquals(PosterShape.Landscape, dominantPosterShape(shapes))
  }

  @Test
  fun aRowWithNoDeclarationsHasNoShape() {
    assertNull(dominantPosterShape(listOf(null, null)))
    assertNull(dominantPosterShape(emptyList()))
  }

  @Test
  fun aTieGoesToTheNarrowerShape() {
    assertEquals(PosterShape.Poster, dominantPosterShape(listOf(PosterShape.Landscape, PosterShape.Poster)))
  }

  @Test
  fun aViewersRowSettingBeatsTheAddon() {
    assertEquals(PosterShape.Poster, resolveRowPosterShape(PosterShape.Poster, PosterShape.Landscape, PosterShape.Poster))
  }

  @Test
  fun anAddonsLandscapeOrSquareBeatsTheDefault() {
    assertEquals(PosterShape.Landscape, resolveRowPosterShape(null, PosterShape.Landscape, PosterShape.Poster))
    assertEquals(PosterShape.Square, resolveRowPosterShape(null, PosterShape.Square, PosterShape.Landscape))
  }

  @Test
  fun declaringTheDefaultPosterShapeKeepsTheViewersDefault() {
    assertEquals(PosterShape.Landscape, resolveRowPosterShape(null, PosterShape.Poster, PosterShape.Landscape))
    assertEquals(PosterShape.Poster, resolveRowPosterShape(null, null, PosterShape.Poster))
  }

  @Test
  fun aWideCardPrefersTheAddonsLandscapePoster() {
    assertEquals("wide", cardArtworkUrl("poster", "backdrop", "wide", PosterShape.Landscape, PosterShape.Landscape))
  }

  @Test
  fun aWideCardUsesAPosterTheAddonSaysIsWide() {
    assertEquals("poster", cardArtworkUrl("poster", "backdrop", null, PosterShape.Landscape, PosterShape.Landscape))
  }

  @Test
  fun aWideCardFallsBackToTheBackdropForAPortraitPoster() {
    assertEquals("backdrop", cardArtworkUrl("poster", "backdrop", null, null, PosterShape.Landscape))
    assertEquals("poster", cardArtworkUrl("poster", " ", null, null, PosterShape.Landscape))
  }

  @Test
  fun portraitAndSquareCardsKeepThePoster() {
    assertEquals("poster", cardArtworkUrl("poster", "backdrop", "wide", PosterShape.Landscape, PosterShape.Poster))
    assertEquals("poster", cardArtworkUrl("poster", "backdrop", "wide", PosterShape.Square, PosterShape.Square))
    assertEquals("backdrop", cardArtworkUrl(null, "backdrop", null, null, PosterShape.Poster))
  }

  @Test
  fun artworkThatSuitsTheCardIsCropped() {
    assertEquals(CardArtworkScaling.Crop, chooseCardArtworkScaling(2000f, 3000f, 138f, 204f, hasAlpha = false))
    assertEquals(CardArtworkScaling.Crop, chooseCardArtworkScaling(1920f, 1080f, 224f, 118f, hasAlpha = true))
  }

  @Test
  fun opaqueArtworkOfTheWrongShapeIsFittedOverABackdrop() {
    assertEquals(CardArtworkScaling.FitOverBackdrop, chooseCardArtworkScaling(1920f, 1080f, 138f, 204f, hasAlpha = false))
  }

  @Test
  fun transparentArtworkOfTheWrongShapeIsFittedOnTheCard() {
    assertEquals(CardArtworkScaling.Fit, chooseCardArtworkScaling(800f, 300f, 138f, 204f, hasAlpha = true))
  }

  @Test
  fun unknownSizesCrop() {
    assertEquals(CardArtworkScaling.Crop, chooseCardArtworkScaling(0f, 0f, 138f, 204f, hasAlpha = true))
  }
}
