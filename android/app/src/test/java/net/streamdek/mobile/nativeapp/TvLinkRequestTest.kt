package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The links a television's QR code can open the app with.
 *
 * The phone camera hands over the https link; the scanner and older televisions use the custom
 * scheme. Both must land on the same code, and nothing else on the site may be mistaken for one.
 */
class TvLinkRequestTest {

  @Test
  fun `the https link a phone camera opens carries the code`() {
    assertEquals("ABCD-2345", parseTvLinkCode("https://www.streamdek.net/link-tv?code=ABCD-2345"))
    assertEquals("ABCD-2345", parseTvLinkCode("https://streamdek.net/link-tv/?code=abcd-2345"))
    assertEquals("ABCD-2345", parseTvLinkCode("https://www.streamdek.net/link-tv?code=ABCD%2D2345"))
  }

  @Test
  fun `the older custom scheme still works`() {
    assertEquals("ABCD-2345", parseTvLinkCode("streamdek://link-tv?code=ABCD-2345"))
  }

  @Test
  fun `other pages, other hosts and malformed codes are not television links`() {
    assertNull(parseTvLinkCode("https://www.streamdek.net/download/android?code=ABCD-2345"))
    assertNull(parseTvLinkCode("https://evil.example/link-tv?code=ABCD-2345"))
    assertNull(parseTvLinkCode("http://www.streamdek.net/link-tv?code=ABCD-2345"))
    assertNull(parseTvLinkCode("https://www.streamdek.net/link-tv"))
    assertNull(parseTvLinkCode("https://www.streamdek.net/link-tv?code=<script>"))
    assertNull(parseTvLinkCode("streamdek://setup/devices"))
  }

  @Test
  fun `codes are normalised to the backend's alphabet`() {
    assertEquals("ABCD-2345", normalizeTvLinkCode("abcd 2345"))
    // No 0, 1, I or O in the alphabet, so a code containing one is a typo rather than a code.
    assertNull(normalizeTvLinkCode("ABCD-1234"))
    assertNull(normalizeTvLinkCode("ABC-234"))
  }

  @Test
  fun `plugin search matching ignores accents and punctuation`() {
    assertNotNull(PluginCatalogSearch.matchRank("Amélie", "amelie"))
    assertNotNull(PluginCatalogSearch.matchRank("Spider-Man: No Way Home", "spiderman"))
    assertNull(PluginCatalogSearch.matchRank("Sky News", "sky sports"))
  }
}
