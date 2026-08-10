package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class AddonMetaRequestTypeTest {
  @Test
  fun `series catalog type is preserved for metadata route`() {
    assertEquals("series", addonMetaRequestType(type = "tv", sourceCatalogType = "series"))
  }

  @Test
  fun `tv card without source type uses canonical Stremio series route`() {
    assertEquals("series", addonMetaRequestType(type = "tv", sourceCatalogType = null))
  }

  @Test
  fun `movie catalog type remains movie`() {
    assertEquals("movie", addonMetaRequestType(type = "movie", sourceCatalogType = "movie"))
  }
}
