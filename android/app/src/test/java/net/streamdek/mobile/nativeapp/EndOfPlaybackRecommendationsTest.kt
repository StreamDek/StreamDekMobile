package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndOfPlaybackRecommendationsTest {
  @Test fun `credits metadata wins over IntroDB structure`() {
    val result = AdaptiveEndOfPlaybackTrigger.estimate(3600.0, RecommendationTiming.Standard, 3300.0, 3200.0)!!
    assertEquals(MeaningfulEndSignal.CreditsMetadata, result.signal)
    assertEquals(3300.0, result.triggerPositionSec, .01)
  }

  @Test fun `IntroDB outro is used when valid`() {
    val result = AdaptiveEndOfPlaybackTrigger.estimate(3120.0, RecommendationTiming.Standard, structuralOutroStartSec = 2900.0)!!
    assertEquals(MeaningfulEndSignal.StructuralMetadata, result.signal)
    assertTrue(AdaptiveEndOfPlaybackTrigger.isReached(2900.0, result))
  }

  @Test fun `stale structure falls back and short trailers are ineligible`() {
    val result = AdaptiveEndOfPlaybackTrigger.estimate(7200.0, RecommendationTiming.Late, structuralOutroStartSec = 90.0)!!
    assertEquals(MeaningfulEndSignal.RemainingTime, result.signal)
    assertFalse(AdaptiveEndOfPlaybackTrigger.isReached(7000.0, result))
    assertEquals(null, AdaptiveEndOfPlaybackTrigger.estimate(120.0, RecommendationTiming.Standard))
  }
}
