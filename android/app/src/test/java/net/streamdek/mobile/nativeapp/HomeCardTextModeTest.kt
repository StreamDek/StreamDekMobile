package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardTextModeTest {
  @Test
  fun `existing installs keep full card text`() {
    assertEquals(HomeCardTextMode.ShowFull, HomeCardTextMode.fromKey(null))
    assertEquals(HomeCardTextMode.ShowFull, HomeCardTextMode.fromKey("unknown"))
  }

  @Test
  fun `persisted keys restore every card text option`() {
    assertEquals(HomeCardTextMode.ShowFull, HomeCardTextMode.fromKey("show_full"))
    assertEquals(HomeCardTextMode.ShowYearOnly, HomeCardTextMode.fromKey("show_year_only"))
    assertEquals(HomeCardTextMode.Off, HomeCardTextMode.fromKey("off"))
  }

  @Test
  fun `cloud labels and enum names remain compatible`() {
    assertEquals(HomeCardTextMode.ShowYearOnly, HomeCardTextMode.fromKey("Show Year Only"))
    assertEquals(HomeCardTextMode.ShowYearOnly, HomeCardTextMode.fromKey("ShowYearOnly"))
  }
}
