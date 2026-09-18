package net.streamdek.mobile.nativeapp

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the backup's list of settings in step with the settings themselves.
 *
 * A setting added to the app and not to [BackupSettingsRegistry] would silently not be backed up,
 * and nobody would find out until a restore came back without it. This reads the settings store's
 * source and fails, naming the key, as soon as that happens.
 */
class BackupSettingsRegistryTest {
  private val source: String by lazy {
    val file = listOf(
      "src/main/java/net/streamdek/mobile/nativeapp/StreamDekNativeApp.kt",
      "app/src/main/java/net/streamdek/mobile/nativeapp/StreamDekNativeApp.kt",
    ).map(::File).first { it.exists() }
    val text = file.readText()
    val start = text.indexOf("private class AppSettingsStore(")
    val end = text.indexOf("private fun parseFusionBadgeUrls(", start)
    check(start >= 0 && end > start) { "AppSettingsStore not found where expected" }
    text.substring(start, end)
  }

  @Test
  fun `every setting the settings store keeps is backed up or deliberately left out`() {
    val keys = Regex("""\.(?:get|put)(?:String|Boolean|Int|Long|Float|StringSet)\("([a-z0-9_]+)"""")
      .findAll(source).map { it.groupValues[1] }.toSet()
    assertTrue("the scan found nothing, so it is not reading the store", keys.size > 50)
    val registered = BackupSettingsRegistry.specs.map { it.key }.toSet() + BackupSettingsRegistry.excludedKeys
    val missing = keys - registered
    assertTrue("add these to BackupSettingsRegistry (or excludedKeys): $missing", missing.isEmpty())
  }

  @Test
  fun `no setting is registered twice in the same place`() {
    val duplicates = BackupSettingsRegistry.specs.groupBy { Triple(it.scope, it.store, it.key) }.filterValues { it.size > 1 }.keys
    assertTrue("registered twice: $duplicates", duplicates.isEmpty())
  }

  @Test
  fun `secrets are marked as secrets`() {
    val secret = BackupSettingsRegistry.specs.filter { it.key.endsWith("_api_key") || it.key.contains("token") || it.key.contains("password") }
    assertTrue("unmarked secrets: $secret", secret.all { it.sensitive })
  }
}
