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

  @Test fun `next episode wins and is removed from alternatives`() {
    val result = EndOfPlaybackCoordinator.decide("series:7:2:6", "series:7", listOf("series:7:2:6", "movie:9", "movie:9"), 2)!!
    assertEquals(UpNextKind.NextEpisode, result.primaryKind)
    assertEquals(listOf("movie:9"), result.alternativeIds)
  }

  @Test fun `recommendation becomes primary when there is no next episode`() {
    val result = EndOfPlaybackCoordinator.decide(null, "movie:1", listOf("movie:1", "movie:2", "movie:3"), 2)!!
    assertEquals(UpNextKind.Recommendation, result.primaryKind)
    assertEquals("movie:2", result.primaryId)
    assertEquals(listOf("movie:3"), result.alternativeIds)
  }

  @Test fun `substantial backwards seek rearms the experience`() {
    assertTrue(EndOfPlaybackCoordinator.shouldResetAfterSeek(2800.0, 2900.0))
    assertFalse(EndOfPlaybackCoordinator.shouldResetAfterSeek(2890.0, 2900.0))
  }
}
