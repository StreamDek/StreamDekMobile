package net.streamdek.mobile.nativeapp

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `.streamdek` file itself: that what goes in comes back out, that a damaged or doctored file
 * is refused rather than half-restored, that secrets never sit in a file in the clear, and that an
 * old backup can still be read after the format moves on.
 */
class BackupFormatTest {
  private val iterations = 10_000

  private fun payload() = JSONObject()
    .put("device", JSONObject().put("platform", BACKUP_PLATFORM_MOBILE).put("settings", JSONArray().put(encodeBackupSetting("theme_preset", "Ocean"))))
    .put(
      "profiles",
      JSONArray().put(
        JSONObject().put("name", "Henry").put("addons", JSONArray().put(JSONObject().put("manifestUrl", "https://example.com/secret-token-123/manifest.json"))),
      ),
    )

  private fun summary(encrypted: Boolean) = BackupSummary(
    createdAt = 1_789_740_000_000L,
    appVersion = "2.1.20",
    platform = BACKUP_PLATFORM_MOBILE,
    schemaVersion = BACKUP_SCHEMA_VERSION,
    encrypted = encrypted,
    categories = setOf(BackupCategory.Addons, BackupCategory.Appearance),
    profiles = 1, addons = 1, pluginRepositories = 0, pluginSources = 0, playlists = 0, settings = 1, libraryItems = 0, credentials = 0,
  )

  private fun write(passphrase: String?) =
    writeBackupFile(payload(), summary(passphrase != null), "StreamDek Mobile", passphrase?.toCharArray(), iterations)

  @Test
  fun `an unencrypted backup reads back to the same payload and summary`() {
    val file = readBackupFile(write(null)).getOrThrow()
    assertFalse(file.encrypted)
    assertEquals(1, file.summary.profiles)
    assertEquals("2.1.20", file.summary.appVersion)
    assertEquals(setOf(BackupCategory.Addons, BackupCategory.Appearance), file.summary.categories)
    assertEquals(canonicalJson(payload()), canonicalJson(file.open().getOrThrow()))
  }

  @Test
  fun `an encrypted backup opens only with its passphrase`() {
    val file = readBackupFile(write("correct horse")).getOrThrow()
    assertTrue(file.encrypted)
    // The summary is readable before the passphrase is asked for.
    assertEquals(1, file.summary.addons)
    assertEquals(BackupReadError.PassphraseRequired, (file.open(null).exceptionOrNull() as BackupReadException).error)
    assertEquals(BackupReadError.WrongPassphrase, (file.open("wrong horse".toCharArray()).exceptionOrNull() as BackupReadException).error)
    assertEquals(canonicalJson(payload()), canonicalJson(file.open("correct horse".toCharArray()).getOrThrow()))
  }

  @Test
  fun `an encrypted backup does not contain its secrets in the clear`() {
    val text = String(write("correct horse"), Charsets.UTF_8)
    assertFalse(text.contains("secret-token-123"))
    assertFalse(text.contains("Ocean"))
  }

  @Test
  fun `a payload edited after the backup was made is refused`() {
    val document = JSONObject(String(write(null), Charsets.UTF_8))
    document.getJSONObject("payload").getJSONObject("device").put("platform", "somewhere-else")
    val file = readBackupFile(document.toString().toByteArray()).getOrThrow()
    assertEquals(BackupReadError.Damaged, (file.open().exceptionOrNull() as BackupReadException).error)
  }

  @Test
  fun `an encrypted backup whose summary was edited does not open`() {
    val document = JSONObject(String(write("correct horse"), Charsets.UTF_8))
    document.getJSONObject("summary").put("addons", 99)
    val file = readBackupFile(document.toString().toByteArray()).getOrThrow()
    assertTrue(file.open("correct horse".toCharArray()).isFailure)
  }

  @Test
  fun `key order and reformatting do not change the checksum`() {
    val document = JSONObject(String(write(null), Charsets.UTF_8))
    // Written back out compactly by a different writer, which is free to reorder keys.
    val rewritten = JSONObject(document.toString()).toString().toByteArray()
    assertTrue(readBackupFile(rewritten).getOrThrow().open().isSuccess)
    assertEquals(canonicalJson(JSONObject().put("b", 1).put("a", 2)), canonicalJson(JSONObject().put("a", 2).put("b", 1)))
  }

  @Test
  fun `files that are not backups say so`() {
    assertEquals(BackupReadError.NotABackup, (readBackupFile("hello".toByteArray()).exceptionOrNull() as BackupReadException).error)
    assertEquals(BackupReadError.NotABackup, (readBackupFile("""{"format":"other"}""".toByteArray()).exceptionOrNull() as BackupReadException).error)
    assertEquals(BackupReadError.TooLarge, (readBackupFile(ByteArray(BACKUP_MAX_BYTES + 1)).exceptionOrNull() as BackupReadException).error)
  }

  @Test
  fun `a backup from a newer StreamDek is recognised as such`() {
    val document = JSONObject(String(write(null), Charsets.UTF_8)).put("schemaVersion", BACKUP_SCHEMA_VERSION + 1)
    assertEquals(BackupReadError.NewerVersion, (readBackupFile(document.toString().toByteArray()).exceptionOrNull() as BackupReadException).error)
  }

  @Test
  fun `an old payload is migrated one version at a time`() {
    val migrations = mapOf<Int, (JSONObject) -> JSONObject>(
      1 to { it.put("steps", "1") },
      2 to { it.put("steps", it.getString("steps") + ",2") },
    )
    val migrated = migrateBackupPayload(JSONObject(), fromVersion = 1, migrations = migrations, toVersion = 3)
    assertEquals("1,2", migrated?.getString("steps"))
    assertNull("a missing step is not skipped", migrateBackupPayload(JSONObject(), 1, mapOf(2 to { it }), 3))
    assertNotNull(migrateBackupPayload(JSONObject(), BACKUP_SCHEMA_VERSION))
  }

  @Test
  fun `the fallback PBKDF2 matches the platform and the RFC test vector`() {
    val expected = "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc"
    val derived = BackupCrypto.pbkdf2HmacSha256("passwd".toCharArray(), "salt".toByteArray(), 1, 32)
    assertEquals(expected, derived.joinToString("") { "%02x".format(it) })
    val salt = "streamdek-salt".toByteArray()
    val platform = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      .generateSecret(PBEKeySpec("pässwörd".toCharArray(), salt, 2_000, 256)).encoded
    assertArrayEquals(platform, BackupCrypto.pbkdf2HmacSha256("pässwörd".toCharArray(), salt, 2_000, 32))
  }

  @Test
  fun `a doctored iteration count is refused rather than obeyed`() {
    val document = JSONObject(String(write("correct horse"), Charsets.UTF_8))
    document.getJSONObject("encryption").put("iterations", 2_000_000_000)
    val file = readBackupFile(document.toString().toByteArray()).getOrThrow()
    assertTrue(file.open("correct horse".toCharArray()).isFailure)
  }

  @Test
  fun `settings keep their type through a backup`() {
    listOf(true, 42, 7_000_000_000L, 1.1f, "Ocean", setOf("b", "a")).forEach { value ->
      val encoded = JSONObject(encodeBackupSetting("key", value).toString())
      assertEquals(value, decodeBackupSettingValue(encoded))
    }
    assertNull(decodeBackupSettingValue(JSONObject().put("key", "k").put("type", "future-type").put("value", 1)))
  }

  @Test
  fun `links that carry logins or configuration are treated as private`() {
    assertFalse(urlCarriesPrivateConfig("https://v3-cinemeta.strem.io/manifest.json"))
    assertFalse(urlCarriesPrivateConfig("https://opensubtitles-v3.strem.io/manifest.json"))
    assertFalse(urlCarriesPrivateConfig("https://raw.githubusercontent.com/iptv-org/iptv/master/streams/uk.m3u"))
    assertTrue(urlCarriesPrivateConfig("https://torrentio.strem.fun/realdebrid=ABCDEF123/manifest.json"))
    assertTrue(urlCarriesPrivateConfig("https://aiostreams.example/eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9/manifest.json"))
    assertTrue(urlCarriesPrivateConfig("http://panel.example:8080/get.php?username=me&password=pw&type=m3u_plus"))
    assertTrue(urlCarriesPrivateConfig("http://me:pw@panel.example/playlist.m3u"))
    assertTrue(urlCarriesPrivateConfig("stremio://addon.example/providers=a|b/manifest.json"))
  }
}
