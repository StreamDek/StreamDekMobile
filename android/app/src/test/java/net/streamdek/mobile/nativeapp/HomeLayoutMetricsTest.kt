package net.streamdek.mobile.nativeapp

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Relaxed mode is the home screen as it shipped, so the tests that matter most here are the
 * ones asserting it is untouched: a metric that quietly drifts is a redesign of the default
 * experience, which is the one thing this setting must never do.
 */
class HomeLayoutMetricsTest {
  private val relaxed = HomeLayoutMetrics.forDensity(HomeDensity.Relaxed)
  private val compact = HomeLayoutMetrics.forDensity(HomeDensity.Compact)

  @Test fun relaxedKeepsTheMeasurementsTheHomeScreenAlreadyHad() {
    assertEquals(1f, relaxed.cardScale, 0f)
    assertEquals(1f, relaxed.textScale, 0f)
    assertEquals(26.dp, relaxed.rowGap)
    assertEquals(12.dp, relaxed.heroToRowGap)
    assertEquals(14.dp, relaxed.rowHeaderGap)
    assertEquals(6.dp, relaxed.rowHeaderTitleGap)
    assertEquals(16.dp, relaxed.rowSideInset)
    assertEquals(14.dp, relaxed.cardGap)
    assertEquals(22.5.sp, relaxed.rowTitleSize)
    assertEquals(20.sp, relaxed.addonRowTitleSize)
    assertEquals(8.dp, relaxed.cardMetaGap)
    assertEquals(56.dp, relaxed.cardMetaHeight)
    assertTrue(relaxed.showRowAccentBar)
  }

  /** A scale of exactly one has to be a no-op, not a value that happens to round back. */
  @Test fun relaxedScalesNothing() {
    assertEquals(204.dp, relaxed.card(204.dp))
    assertEquals(12.sp, relaxed.text(12.sp))
    val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    assertSame(style, relaxed.text(style))
  }

  /**
   * The gap the home screen puts between two rows is the list's own spacing plus the spacer it adds
   * below the spotlight. Both readings have to come out at [HomeLayoutMetrics.rowGap], or a page
   * with a spotlight would be spaced differently from one without.
   */
  @Test fun theSpacerBelowTheSpotlightMakesUpTheFullRowGap() {
    listOf(relaxed, compact).forEach { metrics ->
      val spacer = metrics.rowGap - metrics.heroToRowGap
      assertEquals(metrics.rowGap, metrics.heroToRowGap + spacer)
      assertTrue("hero gap must not exceed the row gap", metrics.heroToRowGap <= metrics.rowGap)
    }
  }

  @Test fun compactCardsAreAFifthSmallerInBothDimensions() {
    assertEquals(0.8f, compact.cardScale, 0f)
    // Width and height take the same multiplier, so a 138x204 poster keeps its 2:3 proportions.
    val relaxedRatio = 204f / 138f
    val compactRatio = compact.card(204.dp).value / compact.card(138.dp).value
    assertEquals(relaxedRatio, compactRatio, 0.0001f)
  }

  @Test fun compactDropsTheBarUnderEachRowHeader() {
    assertFalse(compact.showRowAccentBar)
  }

  @Test fun compactTightensEverySpacingItTouches() {
    assertTrue(compact.rowGap < relaxed.rowGap)
    assertTrue(compact.heroToRowGap < relaxed.heroToRowGap)
    assertTrue(compact.rowHeaderGap < relaxed.rowHeaderGap)
    assertTrue(compact.cardGap < relaxed.cardGap)
    assertTrue(compact.cardMetaGap < relaxed.cardMetaGap)
    assertTrue(compact.cardMetaHeight < relaxed.cardMetaHeight)
    // The row inset is deliberately left alone: Home has to line up with the rest of the app.
    assertEquals(relaxed.rowSideInset, compact.rowSideInset)
  }

  /**
   * Type comes down far less than artwork does. Fitting more titles on the screen is no use if the
   * titles stop being readable, and a caption shrunk the full 20% is what that looks like.
   */
  @Test fun compactTypeShrinksGentlyAndTheCaptionKeepsRoomForIt() {
    assertTrue("type must not shrink as hard as artwork", compact.textScale > compact.cardScale)
    assertTrue(compact.textScale < 1f)
    val twoTitleLines = compact.text(16.sp).value * 2
    val subtitleLine = compact.text(11.sp).value * 1.4f
    val gap = compact.card(4.dp).value
    assertTrue(
      "the caption block must be tall enough for two title lines and a subtitle",
      twoTitleLines + subtitleLine + gap <= compact.cardMetaHeight.value,
    )
  }

  /**
   * A fixed-height card lays its second line out against whatever the first left over. If the type
   * shrank more gently than the box, that remainder would be smaller at Compact than at Relaxed and
   * the same title would clip worse at the smaller size.
   */
  @Test fun typeInFixedHeightCardsFollowsTheBoxExactly() {
    val relaxedTicket = relaxed.cardText(TextStyle(fontSize = 16.sp, lineHeight = 24.sp))
    assertEquals(16.sp, relaxedTicket.fontSize)
    assertEquals(24.sp, relaxedTicket.lineHeight)

    val ticket = compact.cardText(TextStyle(fontSize = 16.sp, lineHeight = 24.sp))
    assertEquals(16.sp * compact.cardScale, ticket.fontSize)
    assertEquals(24.sp * compact.cardScale, ticket.lineHeight)

    // The room left for the line below a two-line title, as a share of the card, is unchanged.
    val relaxedRemainder = (88f - 12f * 2 - 24f * 2 - 6f) / 88f
    val compactRemainder =
      (compact.card(88.dp).value - compact.card(12.dp).value * 2 - ticket.lineHeight.value * 2 - compact.card(6.dp).value) /
        compact.card(88.dp).value
    assertEquals(relaxedRemainder, compactRemainder, 0.001f)
  }

  @Test fun textScalingLeavesUnspecifiedSizesAlone() {
    val bare = TextStyle(fontSize = TextUnit.Unspecified, lineHeight = TextUnit.Unspecified)
    val scaled = compact.text(bare)
    assertEquals(TextUnit.Unspecified, scaled.fontSize)
    assertEquals(TextUnit.Unspecified, scaled.lineHeight)
  }

  @Test fun theDefaultIsTheExistingHomeScreen() {
    assertEquals(HomeDensity.Relaxed, HomeDensity.Default)
    assertEquals(HomeDensity.Relaxed, HomeDensity.fromKey(null))
    assertEquals(HomeDensity.Relaxed, HomeDensity.fromKey(""))
    assertEquals(HomeDensity.Relaxed, HomeDensity.fromKey("nonsense"))
  }

  @Test fun storedKeysAndEnumNamesBothRoundTrip() {
    HomeDensity.entries.forEach { density ->
      assertEquals(density, HomeDensity.fromKey(density.key))
      assertEquals(density, HomeDensity.fromKey(density.name))
      assertEquals(density, HomeDensity.fromKey(" ${density.name.uppercase()} "))
    }
  }
}
