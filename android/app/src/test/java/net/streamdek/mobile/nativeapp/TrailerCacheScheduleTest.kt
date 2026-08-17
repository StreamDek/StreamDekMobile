package net.streamdek.mobile.nativeapp

import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the trailer cache clears itself.
 *
 * The state this throws away is what stops trailers playing once it goes stale, and clearing it
 * costs the viewer nothing — so the risk worth testing is the schedule misfiring in the other
 * direction: running at an arbitrary hour, running twice in a day, or never running at all.
 */
class TrailerCacheScheduleTest {

  private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
    set(Calendar.YEAR, 2026)
    set(Calendar.MONTH, Calendar.AUGUST)
    set(Calendar.DAY_OF_MONTH, day)
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
  }.timeInMillis

  private val anchor = TRAILER_CACHE_CLEAR_HOUR_OF_DAY

  @Test
  fun `due once the day's nine o'clock has passed and the last clear was before it`() {
    assertTrue(TrailerCache.isClearDue(lastClearedAt = at(16, 10), intervalHours = 24, anchorHourOfDay = anchor, now = at(17, 9, 30)))
  }

  @Test
  fun `not due between anchors`() {
    // Cleared just after yesterday's nine; at 8am today no further anchor has passed yet.
    assertFalse(TrailerCache.isClearDue(lastClearedAt = at(16, 10), intervalHours = 24, anchorHourOfDay = anchor, now = at(17, 8)))
  }

  @Test
  fun `a missed anchor is caught up rather than skipped`() {
    // The phone was not opened at nine. Opening it at four in the afternoon still clears, instead of
    // waiting a whole further day.
    assertTrue(TrailerCache.isClearDue(lastClearedAt = at(16, 10), intervalHours = 24, anchorHourOfDay = anchor, now = at(17, 16)))
  }

  @Test
  fun `a clear at an odd hour does not push the schedule off nine o'clock`() {
    // Cleared at 10am on the 16th because that is when the app was opened. The next clear is the
    // 17th at nine, not the 17th at ten and the 18th at eleven.
    assertTrue(TrailerCache.isClearDue(lastClearedAt = at(16, 10), intervalHours = 24, anchorHourOfDay = anchor, now = at(17, 9)))
  }

  @Test
  fun `not due twice in the same day`() {
    // Cleared at 9:05 today; still after the anchor at 11pm, and must not run again.
    assertFalse(TrailerCache.isClearDue(lastClearedAt = at(17, 9, 5), intervalHours = 24, anchorHourOfDay = anchor, now = at(17, 23)))
  }

  @Test
  fun `never due when switched off`() {
    assertFalse(TrailerCache.isClearDue(lastClearedAt = at(1, 9), intervalHours = 0, anchorHourOfDay = anchor, now = at(17, 12)))
  }

  @Test
  fun `a two day schedule fires every other day, not daily`() {
    // Asserted as a property rather than against hand-picked dates: the anchor grid has a phase, and
    // a test naming the day it lands on would be testing the phase instead of the schedule.
    assertEquals(10, firesOver(days = 20, intervalHours = 48))
  }

  @Test
  fun `a daily schedule fires once a day`() {
    assertEquals(21, firesOver(days = 21, intervalHours = 24))
  }

  /** How often a schedule fires across [days], sampling hours a viewer might open the app. */
  private fun firesOver(days: Int, intervalHours: Int): Int {
    // Starts after the day's anchor, so the count is of scheduled fires rather than of one
    // scheduled fire plus the catch-up for the anchor that had already passed.
    var lastCleared = at(17, 13)
    var fires = 0
    for (dayOffset in 1..days) {
      for (hour in listOf(0, 8, 9, 13, 21, 23)) {
        val now = at(17 + dayOffset, hour)
        if (now > lastCleared && TrailerCache.isClearDue(lastCleared, intervalHours, anchor, now)) {
          fires++
          lastCleared = now
        }
      }
    }
    return fires
  }

  @Test
  fun `a twelve hour schedule fires twice a day`() {
    // Cleared at the 9am anchor; due again after the 9pm one, not before it.
    val morning = at(17, 9, 2)
    assertFalse(TrailerCache.isClearDue(morning, intervalHours = 12, anchorHourOfDay = anchor, now = at(17, 20)))
    assertTrue(TrailerCache.isClearDue(morning, intervalHours = 12, anchorHourOfDay = anchor, now = at(17, 21, 30)))
  }

  @Test
  fun `a device that has never cleared is not treated as overdue`() {
    assertFalse(TrailerCache.isClearDue(lastClearedAt = 0L, intervalHours = 24, anchorHourOfDay = anchor, now = at(17, 12)))
  }

  @Test
  fun `the default schedule is daily`() {
    assertEquals(24, DEFAULT_TRAILER_CACHE_CLEAR_HOURS)
    assertEquals(9, TRAILER_CACHE_CLEAR_HOUR_OF_DAY)
    assertEquals("Every 24 hours", trailerCacheClearLabel(DEFAULT_TRAILER_CACHE_CLEAR_HOURS))
  }

  @Test
  fun `the offered intervals are twelve, twenty four and forty eight hours`() {
    assertEquals(listOf(12, 24, 48), trailerCacheClearChoices.map { it.first })
    assertTrue(trailerCacheClearChoices.all { (hours, label) -> hours > 0 && label.isNotBlank() })
    assertTrue(trailerCacheClearChoices.any { it.first == DEFAULT_TRAILER_CACHE_CLEAR_HOURS })
    // Each divides or is divided by a day, so anchors keep landing on the chosen hour.
    assertTrue(trailerCacheClearChoices.all { 24 % it.first == 0 || it.first % 24 == 0 })
    assertTrue(trailerCacheClearChoices.all { TimeUnit.HOURS.toMillis(it.first.toLong()) % 3_600_000L == 0L })
  }
}
