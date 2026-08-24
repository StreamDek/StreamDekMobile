package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SeasonAvailabilityTest {
  private val today = LocalDate.of(2026, 8, 24)

  @Test fun `released season is shown and future season hidden`() {
    assertTrue(isSeasonAvailable(SeasonSummary(3, "Season 3", 10, airDate = "2025-10-01"), today))
    assertFalse(isSeasonAvailable(SeasonSummary(4, "Season 4", 10, airDate = "2027-01-01"), today))
  }

  @Test fun `season without episodes is hidden`() {
    assertFalse(isSeasonAvailable(SeasonSummary(4, "Season 4", 0), today))
  }

  @Test fun `unknown date with playable episodes remains visible`() {
    assertTrue(isSeasonAvailable(SeasonSummary(2, "Season 2", 8), today))
  }
}
