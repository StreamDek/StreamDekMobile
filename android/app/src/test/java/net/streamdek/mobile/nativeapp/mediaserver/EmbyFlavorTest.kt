package net.streamdek.mobile.nativeapp.mediaserver

import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.JellyfinClientIdentity
import net.streamdek.mobile.nativeapp.mediaserver.jellyfin.MediaBrowserFlavor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Emby as the second member of the Jellyfin family: where the two differ, and where they must not. */
class EmbyFlavorTest {
  private val identity = JellyfinClientIdentity(client = "StreamDek", deviceName = "Phone", deviceId = "dev-1", version = "2.3.0")

  @Test fun `Emby is told who StreamDek is in its own header, with the token apart`() {
    val headers = MediaBrowserFlavor.Emby.requestHeaders(identity, "secret")
    assertTrue(headers.getValue("Authorization").startsWith("Emby Client=\"StreamDek\""))
    assertFalse(headers.getValue("Authorization").contains("secret"))
    assertEquals("secret", headers["X-Emby-Token"])
  }

  @Test fun `Jellyfin keeps its single MediaBrowser header`() {
    val headers = MediaBrowserFlavor.Jellyfin.requestHeaders(identity, "secret")
    assertEquals(setOf("Authorization"), headers.keys)
    assertTrue(headers.getValue("Authorization").startsWith("MediaBrowser "))
    assertTrue(headers.getValue("Authorization").contains("Token=\"secret\""))
  }

  @Test fun `media requests carry Emby's token header and never a token in the URL`() {
    assertEquals("X-Emby-Token" to "secret", MediaBrowserFlavor.Emby.mediaHeader(identity, "secret"))
    assertEquals("Authorization", MediaBrowserFlavor.Jellyfin.mediaHeader(identity, "secret").first)
  }

  @Test fun `a server of the other kind is recognised by the name it gives itself`() {
    assertTrue(MediaBrowserFlavor.Emby.isSibling("Jellyfin Server"))
    assertTrue(MediaBrowserFlavor.Jellyfin.isSibling("Emby Server"))
    assertFalse(MediaBrowserFlavor.Emby.isSibling("Emby Server"))
    assertFalse(MediaBrowserFlavor.Emby.isSibling(null))
    assertFalse(MediaBrowserFlavor.Emby.isSibling("Something else"))
  }

  @Test fun `only Jellyfin offers Quick Connect and only Emby offers Emby Connect`() {
    assertTrue(MediaBrowserFlavor.Jellyfin.quickConnect)
    assertFalse(MediaBrowserFlavor.Emby.quickConnect)
    assertTrue(MediaBrowserFlavor.Emby.embyConnect)
    assertFalse(MediaBrowserFlavor.Emby.currentRoutes)
    assertEquals(MediaBrowserFlavor.Emby, MediaBrowserFlavor.of(EMBY_PROVIDER_ID))
    assertNull(MediaBrowserFlavor.of(PLEX_PROVIDER_ID))
  }

  @Test fun `an Emby backup round-trips in its own section, tokens only when asked`() {
    val server = JellyfinBackupServer("e1", "Den", listOf("http://192.168.1.5:8096"), "u1", "Henry", true, mapOf("films" to true), accessToken = "tok")
    val json = buildMediaServerBackup(MediaServerBackupData(null, emptyList(), listOf(server)), includeTokens = true)!!
    assertTrue(json.has("emby"))
    assertFalse(json.has("jellyfin"))
    val back = parseMediaServerBackup(json)
    assertEquals("tok", back.emby.single().accessToken)
    assertTrue(back.jellyfin.isEmpty())
    assertNull(back.withoutTokens().emby.single().accessToken)
    val plain = buildMediaServerBackup(MediaServerBackupData(null, emptyList(), listOf(server)), includeTokens = false)!!
    assertNull(parseMediaServerBackup(plain).emby.single().accessToken)
  }
}
