package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppVersionPolicyTest {
  @Test fun semanticOrderingDoesNotUseStringsOrDecimals() {
    assertEquals(-1, compareAppVersions("2.1.7", "2.1.8"))
    assertEquals(0, compareAppVersions("2.1.8", "2.1.8"))
    assertEquals(1, compareAppVersions("2.1.15", "2.1.8"))
    assertEquals(-1, compareAppVersions("2.1.9", "2.1.10"))
    assertEquals(-1, compareAppVersions("2.1.15", "2.1.16"))
    assertEquals(-1, compareAppVersions("2.1.16-rc.1", "2.1.16"))
    assertEquals(1, compareAppVersions("2.1.16-rc.10", "2.1.16-rc.2"))
    assertEquals(-1, compareAppVersions("0.3.3", "0.3.3a"))
    assertEquals(-1, compareAppVersions("0.3.3a", "0.3.4"))
    assertNull(compareAppVersions("not-a-version", "2.1.8"))
  }
}
