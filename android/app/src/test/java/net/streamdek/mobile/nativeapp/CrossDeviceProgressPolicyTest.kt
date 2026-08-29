package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDeviceProgressPolicyTest {
  @Test
  fun `television progress requires a local source choice on mobile`() {
    assertTrue(progressCameFromAnotherPlatform("androidtv", "mobile"))
    assertTrue(progressCameFromAnotherPlatform("firetv", "mobile"))
  }

  @Test
  fun `mobile aliases remain local to mobile`() {
    assertFalse(progressCameFromAnotherPlatform("mobile", "mobile"))
    assertFalse(progressCameFromAnotherPlatform("android", "mobile"))
  }
}
