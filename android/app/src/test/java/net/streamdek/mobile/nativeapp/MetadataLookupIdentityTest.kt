package net.streamdek.mobile.nativeapp

import org.junit.Assert.*
import org.junit.Test

class MetadataLookupIdentityTest {
  @Test fun `opaque provider identities survive without stripped retries`() {
    assertEquals(listOf("cnc:encoded:provider"), MetadataLookupIdentity.detailIds(" cnc:encoded:provider "))
    assertEquals(listOf("tmdb:tv:1399"), MetadataLookupIdentity.detailIds("tmdb:tv:1399"))
    assertTrue(MetadataLookupIdentity.detailIds(" ").isEmpty())
  }
  @Test fun `only movie and tv enter catalogue enrichment`() {
    assertTrue(MetadataLookupIdentity.supportsType("movie"))
    assertTrue(MetadataLookupIdentity.supportsType("tv"))
    assertFalse(MetadataLookupIdentity.supportsType("other"))
  }
  @Test fun `imdb identities are bounded normalized and not extracted from opaque strings`() {
    assertEquals("tt1234567", MetadataLookupIdentity.imdbId("IMDB:TT1234567:1:2"))
    for (id in listOf("tt123", "tt1234567890123", "cnc:tt1234567", "encodedtt1234567data")) {
      assertNull(id, MetadataLookupIdentity.imdbId(id))
    }
  }
  @Test fun `ratings accept only numeric tmdb identities with known namespaces`() {
    assertEquals("1399", MetadataLookupIdentity.tmdbId("tmdb:tv:1399"))
    assertEquals("123", MetadataLookupIdentity.tmdbId("123"))
    for (id in listOf("cs:123", "tt1234567", "1234567890123", "cnc:encoded")) {
      assertNull(id, MetadataLookupIdentity.tmdbId(id))
    }
  }
}
