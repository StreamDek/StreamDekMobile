package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
  @Test fun comparesReleaseVersionsNumerically() {
    assertTrue(compareSemanticVersions("2.0.2", "2.0.1") > 0)
    assertTrue(compareSemanticVersions("v2.10.0", "2.9.9") > 0)
    assertEquals(0, compareSemanticVersions("2.0", "2.0.0"))
  }
}