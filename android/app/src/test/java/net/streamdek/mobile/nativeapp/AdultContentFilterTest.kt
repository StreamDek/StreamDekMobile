package net.streamdek.mobile.nativeapp

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdultContentFilterTest {
  @Before
  fun reset() = AdultContentFilter.applyPolicy(true, emptyList())

  @After
  fun restore() = AdultContentFilter.applyPolicy(true, emptyList())

  @Test
  fun `blocks explicit stream names`() {
    assertTrue(AdultContentFilter.isBlocked("Some.Title.XXX.1080p.WEB-DL"))
    assertTrue(AdultContentFilter.isBlocked("Brazzers - House Party"))
    assertTrue(AdultContentFilter.isBlocked("hardcore_scene_04.mp4"))
    assertTrue(AdultContentFilter.isBlocked("PornHub rip 720p"))
    assertTrue(AdultContentFilter.isBlocked("Naughty America 2024"))
    assertTrue(AdultContentFilter.isBlocked("Channel 18+"))
  }

  /**
   * The cases that decide whether this filter survives contact with real users. Every one of these
   * is hidden by a naive `contains("sex")` or `contains("anal")`, and each wrongly hidden title is
   * a reason for someone to demand the whole thing be switched off.
   */
  @Test
  fun `leaves legitimate titles alone`() {
    assertFalse(AdultContentFilter.isBlocked("Sex Education"))
    assertFalse(AdultContentFilter.isBlocked("Sex and the City"))
    assertFalse(AdultContentFilter.isBlocked("Essex Boys"))
    assertFalse(AdultContentFilter.isBlocked("The Sussex Files"))
    assertFalse(AdultContentFilter.isBlocked("Analysis of a Murder"))
    assertFalse(AdultContentFilter.isBlocked("Scunthorpe United"))
    assertFalse(AdultContentFilter.isBlocked("Deeper"))
    assertFalse(AdultContentFilter.isBlocked("Vixen"))
    assertFalse(AdultContentFilter.isBlocked("Private Practice"))
    assertFalse(AdultContentFilter.isBlocked("Blacked Out"))
    assertFalse(AdultContentFilter.isBlocked("Shame"))
    assertFalse(AdultContentFilter.isBlocked("The Naked Gun"))
    assertFalse(AdultContentFilter.isBlocked("Kink"))
    assertFalse(AdultContentFilter.isBlocked("Stranger Things S04E18"))
  }

  @Test
  fun `honours the TMDB adult flag and adult genres`() {
    assertTrue(AdultContentFilter.isBlockedItem(adultFlag = true, title = "Perfectly Ordinary Title"))
    assertTrue(AdultContentFilter.isBlockedItem(title = "Ordinary", genres = listOf("Adult")))
    assertTrue(AdultContentFilter.isBlockedItem(title = "Ordinary", genres = listOf("XXX")))
    assertFalse(AdultContentFilter.isBlockedItem(title = "Ordinary", genres = listOf("Drama")))
  }

  @Test
  fun `blocks adult category labels`() {
    assertTrue(AdultContentFilter.isBlockedCategory("Adult"))
    assertTrue(AdultContentFilter.isBlockedCategory("adults"))
    assertTrue(AdultContentFilter.isBlockedCategory("XXX Channels"))
    assertFalse(AdultContentFilter.isBlockedCategory("Documentary"))
    assertFalse(AdultContentFilter.isBlockedCategory("Kids"))
  }

  @Test
  fun `applies administrator terms as words and phrases`() {
    AdultContentFilter.applyPolicy(true, listOf("dorcel", "late night heat"))
    assertTrue(AdultContentFilter.isBlocked("Dorcel Club 2021"))
    assertTrue(AdultContentFilter.isBlocked("Late Night Heat"))
    // A multi-word term must not leak into every title sharing one of its words.
    assertFalse(AdultContentFilter.isBlocked("Late Night with the Devil"))
    assertFalse(AdultContentFilter.isBlocked("Heat"))
  }

  @Test
  fun `stops filtering only when the platform says so`() {
    AdultContentFilter.applyPolicy(false, emptyList())
    assertFalse(AdultContentFilter.isBlocked("Some.Title.XXX.1080p"))
    assertFalse(AdultContentFilter.isBlockedItem(adultFlag = true, title = "Anything"))
  }

  /** An unreadable policy must leave the block on rather than quietly disabling it. */
  @Test
  fun `fails closed when the policy is unknown`() {
    AdultContentFilter.applyPolicy(false, emptyList())
    AdultContentFilter.applyPolicy(null, null)
    assertTrue(AdultContentFilter.isBlocked("Some.Title.XXX.1080p"))
  }
}
