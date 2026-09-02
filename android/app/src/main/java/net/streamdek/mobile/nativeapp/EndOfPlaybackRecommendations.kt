package net.streamdek.mobile.nativeapp

enum class RecommendationTiming(val key: String) {
  Early("early"), Standard("standard"), Late("late");
  companion object {
    fun fromKey(value: String?): RecommendationTiming = entries.firstOrNull { it.key.equals(value, true) } ?: Standard
  }
}

enum class MeaningfulEndSignal { CreditsMetadata, StructuralMetadata, RemainingTime, PercentageFallback }

data class MeaningfulContentEnd(val triggerPositionSec: Double, val boundaryPositionSec: Double, val signal: MeaningfulEndSignal)

object AdaptiveEndOfPlaybackTrigger {
  fun estimate(
    durationSec: Double,
    timing: RecommendationTiming,
    creditsStartSec: Double? = null,
    structuralOutroStartSec: Double? = null,
  ): MeaningfulContentEnd? {
    if (!durationSec.isFinite() || durationSec < 180.0) return null
    validBoundary(creditsStartSec, durationSec)?.let { return structural(it, durationSec, timing, MeaningfulEndSignal.CreditsMetadata) }
    validBoundary(structuralOutroStartSec, durationSec)?.let { return structural(it, durationSec, timing, MeaningfulEndSignal.StructuralMetadata) }
    val desired = when (timing) { RecommendationTiming.Early -> 300.0; RecommendationTiming.Standard -> 180.0; RecommendationTiming.Late -> 90.0 }
    val remaining = desired.coerceAtMost(durationSec * 0.12).coerceAtLeast(45.0)
    val trigger = durationSec - remaining
    if (trigger.isFinite() && trigger >= 0.0) return MeaningfulContentEnd(trigger, durationSec, MeaningfulEndSignal.RemainingTime)
    val percent = when (timing) { RecommendationTiming.Early -> .92; RecommendationTiming.Standard -> .94; RecommendationTiming.Late -> .96 }
    return MeaningfulContentEnd(durationSec * percent, durationSec, MeaningfulEndSignal.PercentageFallback)
  }

  fun isReached(positionSec: Double, estimate: MeaningfulContentEnd?) = estimate != null && positionSec.isFinite() && positionSec >= estimate.triggerPositionSec

  private fun validBoundary(value: Double?, durationSec: Double) = value?.takeIf { it.isFinite() && it >= durationSec * .2 && it <= durationSec - 5.0 }
  private fun structural(boundary: Double, durationSec: Double, timing: RecommendationTiming, signal: MeaningfulEndSignal): MeaningfulContentEnd {
    val offset = when (timing) { RecommendationTiming.Early -> -30.0; RecommendationTiming.Standard -> 0.0; RecommendationTiming.Late -> 30.0 }
    return MeaningfulContentEnd((boundary + offset).coerceIn(0.0, durationSec - 5.0), boundary, signal)
  }
}
