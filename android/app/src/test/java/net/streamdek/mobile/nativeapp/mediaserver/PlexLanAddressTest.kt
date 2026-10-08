package net.streamdek.mobile.nativeapp.mediaserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlexLanAddressTest {
    @Test
    fun `a plex direct address gives its plain local address`() {
        assertEquals("http://192.168.1.20:32400", plexDirectLanAddress("https://192-168-1-20.a1b2c3d4e5f6.plex.direct:32400"))
        assertEquals("http://10.0.0.5:32400", plexDirectLanAddress("https://10-0-0-5.0123abcd.plex.direct"))
        assertEquals("http://172.16.4.9:8443", plexDirectLanAddress("https://172-16-4-9.ffee.plex.direct:8443/"))
    }

    @Test
    fun `anything else gives nothing`() {
        assertNull(plexDirectLanAddress("http://192.168.1.20:32400"))
        assertNull(plexDirectLanAddress("https://plex.example.com:32400"))
        assertNull(plexDirectLanAddress("https://300-1-1-1.abcd.plex.direct:32400"))
        assertNull(plexDirectLanAddress("https://2001-db8--1.abcd.plex.direct:32400"))
    }

    @Test
    fun `fallbacks follow local connections only and are not repeated`() {
        val local = MediaServerEndpoint("s", "https://192-168-1-20.abc.plex.direct:32400", true, false, "t")
        val remote = MediaServerEndpoint("s", "https://81-2-3-4.abc.plex.direct:32400", false, false, "t")
        val plain = MediaServerEndpoint("s", "http://192.168.1.20:32400", true, false, "t")
        assertEquals(
            listOf(local.uri, remote.uri, "http://192.168.1.20:32400"),
            withPlexLanFallbacks(listOf(local, remote)).map { it.uri },
        )
        assertEquals(listOf(local.uri, plain.uri), withPlexLanFallbacks(listOf(local, plain)).map { it.uri })
    }
}
