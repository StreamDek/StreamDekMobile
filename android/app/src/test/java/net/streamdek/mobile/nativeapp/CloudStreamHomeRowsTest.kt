package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home rows offered by CloudStream providers' main pages; see [cloudStreamHomeCatalogCandidates]. */
class CloudStreamHomeRowsTest {
  private fun csRow(provider: String, name: String, index: Int, enabled: Boolean = false) = HomeCatalogRow(
    id = "addon:${cloudStreamRowSourceId(provider)}:series:$name:$index",
    title = name,
    subtitleRes = null,
    builtin = false,
    enabled = enabled,
  )

  @Test
  fun sourceIdIsAColonFreeSlugOfTheProviderName() {
    assertEquals("cloudstream.sktech-live-events", cloudStreamRowSourceId("⚡SKTech Live Events"))
    assertEquals("cloudstream.prime-video", cloudStreamRowSourceId("Prime Video"))
    assertEquals("cloudstream.a-b", cloudStreamRowSourceId("A:B"))
  }

  @Test
  fun tellsCloudStreamRowsApartFromAddOnAndBuiltInRows() {
    assertTrue(isCloudStreamHomeRowId(csRow("Netflix", "trending", 0).id))
    assertFalse(isCloudStreamHomeRowId("addon:com.example.addon:movie:top:0"))
    assertFalse(isCloudStreamHomeRowId("trending_movies"))
  }

  @Test
  fun newCloudStreamRowsArriveSwitchedOff() {
    val rows = mergeHomeCatalogRows(emptyList(), emptyList(), fallbackCatalogDefinitions, listOf(csRow("Netflix", "trending", 0)))
    assertFalse(rows.single { isCloudStreamHomeRowId(it.id) }.enabled)
  }

  @Test
  fun switchedOnRowSurvivesAStartWhereTheProviderHasNotLoadedYet() {
    val saved = listOf(csRow("Netflix", "trending", 0, enabled = true))
    val rows = mergeHomeCatalogRows(saved, emptyList(), fallbackCatalogDefinitions)
    assertTrue(rows.single { isCloudStreamHomeRowId(it.id) }.enabled)
  }

  @Test
  fun rowIsDroppedWhenItsLoadedProviderNoLongerOffersIt() {
    val saved = listOf(csRow("Netflix", "trending", 0, enabled = true))
    val rows = mergeHomeCatalogRows(saved, emptyList(), fallbackCatalogDefinitions, listOf(csRow("Netflix", "latest", 1)))
    assertTrue(rows.none { it.id == saved.single().id })
    assertTrue(rows.any { it.id == csRow("Netflix", "latest", 1).id })
  }

  @Test
  fun rowThatMovesInTheProvidersListKeepsTheViewersSwitch() {
    val saved = listOf(csRow("Netflix", "trending", 0, enabled = true))
    val rows = mergeHomeCatalogRows(saved, emptyList(), fallbackCatalogDefinitions, listOf(csRow("Netflix", "trending", 3)))
    val row = rows.single { isCloudStreamHomeRowId(it.id) }
    assertTrue(row.enabled)
    assertTrue(row.id.endsWith(":3"))
  }
}
