package net.streamdek.mobile.nativeapp

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Screens and caches reload on [AdultContentFilter.changes]; it must move exactly when enforcement does. */
class ContentPolicyChangesTest {
  @After fun reset() = AdultContentFilter.applyPolicy(true, emptyList(), emptyList())

  @Test fun `the revision moves only when what is enforced changes`() {
    AdultContentFilter.applyPolicy(true, emptyList(), emptyList())
    val start = AdultContentFilter.changes.value
    AdultContentFilter.applyPolicy(true, emptyList(), emptyList(), "a".repeat(64))
    assertEquals("same policy under a new revision string", start, AdultContentFilter.changes.value)
    AdultContentFilter.applyPolicy(true, listOf("fixture"), emptyList())
    assertEquals("term added", start + 1, AdultContentFilter.changes.value)
    val rule = ContentSafetyRule("r", "media", "identity", "Ordinary Film", "ADULT", "fixture")
    AdultContentFilter.applyPolicy(true, listOf("fixture"), listOf(rule))
    assertEquals("rule added", start + 2, AdultContentFilter.changes.value)
    AdultContentFilter.applyPolicy(false, listOf("fixture"), listOf(rule))
    assertEquals("switched off", start + 3, AdultContentFilter.changes.value)
    // A failed refresh passes nulls: blocking comes back on and terms and rules are kept.
    AdultContentFilter.applyPolicy(null, null)
    assertEquals("failed refresh switches blocking back on", start + 4, AdultContentFilter.changes.value)
    assertTrue(AdultContentFilter.isBlockedEntity("media", "Ordinary Film"))
  }
}
