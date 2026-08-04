package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailerResolverTest {
  @Test
  fun trailerResolutionSupports4kAndClampsOutOfRangeValues() {
    assertEquals(360, normalizeTrailerMaxHeight(144))
    assertEquals(2160, normalizeTrailerMaxHeight(2160))
    assertEquals(2160, normalizeTrailerMaxHeight(4320))
  }
}
