package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamCookieJarTest {
  private val jioToken = "__hdnea__=st=1789553284~exp=1789574884~acl=/*~hmac=e4ff8fb80b91d0a79a12d1a1f276315dc9930753fc6d1f802d05b9a622e878ab"

  private fun setCookie(vararg values: String): Map<String, List<String>> =
    mapOf("Content-Type" to listOf("application/vnd.apple.mpegurl"), "Set-Cookie" to values.toList())

  @Test fun leavesRequestsAloneUntilAServerSetsACookie() {
    val jar = StreamCookieJar()
    jar.store("https://cdn.test/live/index.m3u8", mapOf("Content-Type" to listOf("application/vnd.apple.mpegurl")))

    assertNull(jar.cookieHeaderFor("https://cdn.test/live/720p/index.m3u8", null))
    assertNull(jar.cookieHeaderFor("https://cdn.test/live/720p/index.m3u8", "from=playlist"))
  }

  @Test fun sendsJioHlsPartnerCookieToVariantPlaylistsAndSegments() {
    // The real response to b4u_music/HLSPartner/index.m3u8?__hdnea__=...: the token comes back as
    // a cookie, and the relative variant URLs carry no query.
    val jar = StreamCookieJar()
    jar.store(
      "https://jiotvmblive.cdn.jio.com/bpk-tv/b4u_music/HLSPartner/index.m3u8?__hdnea__=st=1",
      setCookie("$jioToken; Domain=jiotvmblive.cdn.jio.com; path=/; Expires=Fri, 01 Jan 2100 00:00:00 GMT"),
    )

    assertEquals(jioToken, jar.cookieHeaderFor("https://jiotvmblive.cdn.jio.com/bpk-tv/b4u_music/HLSPartner/720p/index.m3u8", null))
    assertEquals(jioToken, jar.cookieHeaderFor("https://jiotvmblive.cdn.jio.com/bpk-tv/b4u_music/HLSPartner/720p/seg-1.ts", null))
  }

  @Test fun neverSendsCookiesToOtherHosts() {
    val jar = StreamCookieJar()
    jar.store("https://cdn.test/index.m3u8", setCookie("session=abc; path=/"))
    // A server may not set a cookie for a domain it isn't part of.
    jar.store("https://evil.test/index.m3u8", setCookie("stolen=1; Domain=cdn.test; path=/"))

    assertNull(jar.cookieHeaderFor("https://other.test/index.m3u8", null))
    assertNull(jar.cookieHeaderFor("https://evil.test/index.m3u8", null))
    assertEquals("session=abc", jar.cookieHeaderFor("https://cdn.test/seg.ts", null))
  }

  @Test fun mergesWithPlaylistCookieAndServerValueWins() {
    val jar = StreamCookieJar()
    jar.store("https://cdn.test/index.mpd", setCookie("__hdnea__=fresh; path=/", "extra=1; path=/"))

    assertEquals(
      "__hdnea__=fresh; keep=me; extra=1",
      jar.cookieHeaderFor("https://cdn.test/seg.m4s", "__hdnea__=stale; keep=me"),
    )
  }

  @Test fun keepsSignedValuesUnquotedAndRespectsPathSecureAndExpiry() {
    val jar = StreamCookieJar()
    jar.store(
      "https://cdn.test/live/index.m3u8",
      setCookie(
        "token=exp=1~acl=%2f*~hmac=ab; Max-Age=3600; path=/",
        "scoped=1; path=/live/hd",
        "secureonly=1; Secure; path=/",
        "gone=1; Max-Age=0; path=/",
      ),
    )

    assertEquals("token=exp=1~acl=%2f*~hmac=ab; secureonly=1", jar.cookieHeaderFor("https://cdn.test/live/sd/seg.ts", null))
    assertEquals("token=exp=1~acl=%2f*~hmac=ab", jar.cookieHeaderFor("http://cdn.test/live/sd/seg.ts", null))
    assertEquals(
      "token=exp=1~acl=%2f*~hmac=ab; scoped=1; secureonly=1",
      jar.cookieHeaderFor("https://cdn.test/live/hd/seg.ts", null),
    )
  }

  @Test fun ignoresMalformedInput() {
    val jar = StreamCookieJar()
    jar.store("not a url", setCookie("a=1"))
    jar.store("https://cdn.test/index.m3u8", setCookie(";;;"))

    assertNull(jar.cookieHeaderFor("https://cdn.test/index.m3u8", null))
    assertNull(jar.cookieHeaderFor("::::", "a=1"))
  }
}
