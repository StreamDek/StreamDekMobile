package net.streamdek.mobile.nativeapp.mediaserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaServerBackupTest {
  private val home = JellyfinBackupServer(
    id = "srv1", name = "Home", addresses = listOf("http://192.168.1.20:8096", "https://jf.example.com"),
    userId = "user1", userName = "henry", enabled = true, libraries = mapOf("films" to true, "home" to false), accessToken = "secret-token",
  )
  private val plex = PlexBackupState(linked = true, accountName = "Henry", servers = listOf(PlexBackupServer("p1", "Living Room", false, mapOf("1" to true))))

  @Test fun `a jellyfin sign-in is only written into a backup that may hold credentials`() {
    val plain = buildMediaServerBackup(MediaServerBackupData(plex, listOf(home)), includeTokens = false)!!
    assertFalse(plain.toString().contains("secret-token"))
    val sealed = buildMediaServerBackup(MediaServerBackupData(plex, listOf(home)), includeTokens = true)!!
    val back = parseMediaServerBackup(sealed)
    assertEquals("secret-token", back.jellyfin.single().accessToken)
    assertEquals(listOf("http://192.168.1.20:8096", "https://jf.example.com"), back.jellyfin.single().addresses)
    assertEquals(mapOf("films" to true, "home" to false), back.jellyfin.single().libraries)
    assertEquals(false, back.plex!!.servers.single().enabled)
    assertNull(back.withoutTokens().jellyfin.single().accessToken)
    assertFalse(home.toString().contains("secret-token"))
  }

  @Test fun `nothing is written for a profile without media servers, and damage is ignored`() {
    assertNull(buildMediaServerBackup(MediaServerBackupData(null, emptyList()), includeTokens = true))
    assertNull(buildMediaServerBackup(MediaServerBackupData(plex.copy(linked = false), emptyList()), includeTokens = true))
    val damaged = org.json.JSONObject("""{"jellyfin":{"servers":[{"id":"x","addresses":["ftp://nope"]},{"name":"no id"}]},"plex":"oops"}""")
    val read = parseMediaServerBackup(damaged)
    assertTrue(read.jellyfin.isEmpty())
    assertNull(read.plex)
  }

  @Test fun `an older backup never overwrites a newer setup, and a removal after it stays`() {
    val made = 1_000L
    fun decide(present: Boolean = true, same: Boolean = false, token: Boolean = false, changed: Long = 0L, removed: Long = 0L, local: Long = 0L) =
      decideJellyfinRestore(made, present, same, token, changed, removed, local)
    assertEquals(MediaServerRestoreDecision.Unchanged, decide(same = true, changed = 5_000))
    assertEquals(MediaServerRestoreDecision.KeepNewer, decide(changed = 2_000))
    assertEquals(MediaServerRestoreDecision.KeepNewer, decide(local = 2_000))
    assertEquals(MediaServerRestoreDecision.ApplyChoices, decide(changed = 500, local = 0))
    assertEquals(MediaServerRestoreDecision.RemovedSince, decide(present = false, token = true, removed = 2_000))
    assertEquals(MediaServerRestoreDecision.AddWithSignIn, decide(present = false, token = true, removed = 500))
    assertEquals(MediaServerRestoreDecision.NeedsSignIn, decide(present = false, token = false))
  }

  @Test fun `plex comes back as choices and the link itself needs reconnecting`() {
    assertEquals(MediaServerRestoreDecision.NeedsReconnect, decidePlexRestore(1_000, linked = false, sameChoices = false, profileChangedAt = 0))
    assertEquals(MediaServerRestoreDecision.KeepNewer, decidePlexRestore(1_000, linked = true, sameChoices = false, profileChangedAt = 2_000))
    assertEquals(MediaServerRestoreDecision.ApplyChoices, decidePlexRestore(1_000, linked = true, sameChoices = false, profileChangedAt = 500))
    assertEquals(MediaServerRestoreDecision.Unchanged, decidePlexRestore(1_000, linked = true, sameChoices = true, profileChangedAt = 2_000))
  }

  @Test fun `timestamps from StreamDek are read, and a missing one is the oldest possible`() {
    assertEquals(1_790_000_000_000L, parseInstant("2026-09-21T14:13:20Z"))
    assertEquals(0L, parseInstant(null))
    assertEquals(0L, parseInstant("yesterday"))
  }

  @Test fun `a refused-token report names the token the way StreamDek does`() {
    // SHA-256("abc") begins ba7816bf; the backend's tokenHint gives the same.
    assertEquals("ba7816bf", tokenHint("abc"))
  }
}
