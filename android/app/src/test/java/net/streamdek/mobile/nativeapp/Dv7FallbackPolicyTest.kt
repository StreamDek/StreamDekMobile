package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Dv7FallbackPolicyTest {
  @Test fun supportedNativePlaybackIsKept() {
    assertFalse(shouldUseDv7Fallback(true, true, true, false, false))
  }
  @Test fun unsupportedAndFailedNativePlaybackFallBack() {
    assertTrue(shouldUseDv7Fallback(true, true, false, false, false))
    assertTrue(shouldUseDv7Fallback(true, true, true, true, false))
  }
  @Test fun disabledUnknownOtherProfilesAndDrmKeepNormalBehavior() {
    assertFalse(shouldUseDv7Fallback(false, true, false, true, false))
    assertFalse(shouldUseDv7Fallback(true, false, false, true, false))
    assertFalse(shouldUseDv7Fallback(true, true, false, true, true))
  }
}
