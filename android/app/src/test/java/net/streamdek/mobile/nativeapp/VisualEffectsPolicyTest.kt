package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualEffectsPolicyTest {
  private fun resolve(
    mode: VisualEffectsMode,
    blurSupported: Boolean = true,
    constrained: Boolean = false,
    batterySaver: Boolean = false,
    performanceLimited: Boolean = false,
  ) = VisualEffectsPolicy.resolve(mode, blurSupported, constrained, batterySaver, performanceLimited)

  @Test
  fun automaticUsesBlurOnACapableDevice() {
    val effects = resolve(VisualEffectsMode.Automatic)
    assertTrue(effects.liveBlur)
    assertNull(effects.reducedReason)
  }

  @Test
  fun automaticStepsDownForEachConstraint() {
    assertEquals(ReducedEffectsReason.BatterySaver, resolve(VisualEffectsMode.Automatic, batterySaver = true).reducedReason)
    assertEquals(ReducedEffectsReason.Device, resolve(VisualEffectsMode.Automatic, constrained = true).reducedReason)
    assertEquals(ReducedEffectsReason.Performance, resolve(VisualEffectsMode.Automatic, performanceLimited = true).reducedReason)
  }

  @Test
  fun fullOverridesTheHeuristicsButNotTheOperatingSystem() {
    assertTrue(resolve(VisualEffectsMode.Full, constrained = true, batterySaver = true, performanceLimited = true).liveBlur)
    val unsupported = resolve(VisualEffectsMode.Full, blurSupported = false)
    assertFalse(unsupported.liveBlur)
    assertEquals(ReducedEffectsReason.Unsupported, unsupported.reducedReason)
  }

  @Test
  fun reducedIsAlwaysReducedAndSaysItWasChosen() {
    val effects = resolve(VisualEffectsMode.Reduced)
    assertFalse(effects.liveBlur)
    assertEquals(ReducedEffectsReason.Chosen, effects.reducedReason)
  }

  @Test
  fun deviceHeuristicIsConservative() {
    val gib = 1024L * 1024 * 1024
    assertTrue(VisualEffectsPolicy.deviceConstrained(lowRamDevice = true, totalMemoryBytes = 8 * gib, cpuCores = 8))
    assertTrue(VisualEffectsPolicy.deviceConstrained(lowRamDevice = false, totalMemoryBytes = 3 * gib, cpuCores = 8))
    assertTrue(VisualEffectsPolicy.deviceConstrained(lowRamDevice = false, totalMemoryBytes = 6 * gib, cpuCores = 4))
    // A "4GB" phone reports a little under 4GiB and is not constrained.
    assertFalse(VisualEffectsPolicy.deviceConstrained(lowRamDevice = false, totalMemoryBytes = 3_700L * 1024 * 1024, cpuCores = 8))
    // Unknown memory is not evidence of anything.
    assertFalse(VisualEffectsPolicy.deviceConstrained(lowRamDevice = false, totalMemoryBytes = 0, cpuCores = 8))
  }

  @Test
  fun jankWindowNeedsAQuarterOfFramesSlow() {
    assertFalse(VisualEffectsPolicy.windowIsJanky(frames = 120, slowFrames = 29))
    assertTrue(VisualEffectsPolicy.windowIsJanky(frames = 120, slowFrames = 30))
    assertFalse(VisualEffectsPolicy.windowIsJanky(frames = 0, slowFrames = 0))
  }

  @Test
  fun modeKeysAreStable() {
    assertEquals(VisualEffectsMode.Automatic, VisualEffectsMode.fromKey(null))
    assertEquals(VisualEffectsMode.Full, VisualEffectsMode.fromKey("full"))
    assertEquals(VisualEffectsMode.Reduced, VisualEffectsMode.fromKey("Reduced"))
    assertEquals(VisualEffectsMode.Automatic, VisualEffectsMode.fromKey("something-else"))
  }

  @Test
  fun navigationBehaviourKeepsTheSyncedBooleanMeaning() {
    assertEquals(NavigationBehaviour.AlwaysExpanded, NavigationBehaviour.from(collapsible = false, collapseOnScroll = true))
    assertEquals(NavigationBehaviour.CollapseAfterDelay, NavigationBehaviour.from(collapsible = true, collapseOnScroll = false))
    assertEquals(NavigationBehaviour.CollapseWhileScrolling, NavigationBehaviour.from(collapsible = true, collapseOnScroll = true))
    assertFalse(NavigationBehaviour.AlwaysExpanded.collapses)
    assertTrue(NavigationBehaviour.CollapseAfterDelay.collapses)
    assertTrue(NavigationBehaviour.CollapseWhileScrolling.collapses)
  }
}
