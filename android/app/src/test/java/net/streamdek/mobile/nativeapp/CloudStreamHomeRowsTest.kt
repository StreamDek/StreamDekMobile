package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

/** Home rows offered by CloudStream providers' main pages; see [cloudStreamHomeCatalogCandidates]. */
class CloudStreamHomeRowsTest {
  private fun liveSubsource(label: String) = object : MainAPI() {
    override var name = label
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)
  }

  @Test
  fun everyRegisteredSubsourceGetsItsOwnRowEvenWithTheDefaultBlankMainPage() {
    // PlayZTV/SKTech register multiple MainAPI instances, each using the default blank request.
    val providers = listOf("Live Events", "JioTV", "Sony", "Zee Live").map(::liveSubsource)
    val rows = cloudStreamHomeCatalogCandidates(providers)
    assertEquals(providers.map { it.name }, rows.map { it.title })
    assertEquals(4, rows.map { it.id }.distinct().size)
    assertTrue(rows.all { !it.enabled })
    rows.forEachIndexed { index, row ->
      assertTrue(resolveCloudStreamHomeRow(row.id, providers)?.provider === providers[index])
    }
  }

  @Test
  fun newlyEnabledSubsourcesPreserveExistingHomeRowChoices() {
    val events = liveSubsource("Live Events")
    val saved = cloudStreamHomeCatalogCandidates(listOf(events)).map { it.copy(enabled = true) }
    val candidates = cloudStreamHomeCatalogCandidates(listOf(events, liveSubsource("JioTV")))
    val merged = mergeHomeCatalogRows(saved, emptyList(), fallbackCatalogDefinitions, candidates)
    assertTrue(merged.single { it.id == saved.single().id }.enabled)
    assertFalse(merged.single { it.title == "JioTV" }.enabled)
  }

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
