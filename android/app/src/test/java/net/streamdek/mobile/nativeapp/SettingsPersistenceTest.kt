package net.streamdek.mobile.nativeapp

import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** apply() deliberately does NOT reach disk: recreating a process exposes accidental async writes. */
internal class SettingsTestPreferences(private val disk: MutableMap<String, Any?> = linkedMapOf()) : SharedPreferences {
  private val memory = disk.toMutableMap()
  var failWrites = false
  fun restart() = SettingsTestPreferences(disk)
  override fun getAll(): MutableMap<String, *> = memory.toMutableMap()
  override fun contains(key: String) = memory.containsKey(key)
  override fun getString(key: String, defValue: String?) = memory[key] as? String ?: defValue
  override fun getStringSet(key: String, defValues: MutableSet<String>?) = (memory[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
  override fun getInt(key: String, defValue: Int) = memory[key] as? Int ?: defValue
  override fun getLong(key: String, defValue: Long) = memory[key] as? Long ?: defValue
  override fun getFloat(key: String, defValue: Float) = memory[key] as? Float ?: defValue
  override fun getBoolean(key: String, defValue: Boolean) = memory[key] as? Boolean ?: defValue
  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
  override fun edit(): SharedPreferences.Editor {
    val changes = linkedMapOf<String, Any?>()
    var clear = false
    return object : SharedPreferences.Editor {
      override fun putString(key: String, value: String?) = apply { changes[key] = value }
      override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values?.toSet() }
      override fun putInt(key: String, value: Int) = apply { changes[key] = value }
      override fun putLong(key: String, value: Long) = apply { changes[key] = value }
      override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
      override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
      override fun remove(key: String) = apply { changes[key] = null }
      override fun clear() = apply { clear = true }
      override fun apply() {
        if (clear) memory.clear()
        changes.forEach { (key, value) -> if (value == null) memory.remove(key) else memory[key] = value }
      }
      override fun commit(): Boolean {
        apply()
        if (failWrites) return false
        disk.clear(); disk.putAll(memory)
        return true
      }
    }
  }
}

class SettingsPersistenceTest {
  private fun durable(file: SettingsTestPreferences) = DurableSettingsPreferences(file, SettingsSyncRegistry.syncedKeys) {}

  @Test fun `rapid edits survive immediate process death with matching retry record`() {
    val disk = SettingsTestPreferences()
    val settings = durable(disk)
    repeat(50) { settings.edit().putBoolean("pip_enabled", it % 2 == 0).apply() }
    val restarted = durable(disk.restart())
    assertFalse(restarted.getBoolean("pip_enabled", true))
    assertEquals(50L, restarted.pending()["pip_enabled"])
  }

  @Test fun `old acknowledgement cannot clear a newer edit even when value changes back`() {
    val settings = durable(SettingsTestPreferences())
    settings.edit().putBoolean("media_hub_enabled", true).apply()
    val sent = settings.pending()
    settings.edit().putBoolean("media_hub_enabled", false).apply()
    settings.edit().putBoolean("media_hub_enabled", true).apply()
    settings.acknowledge(sent)
    assertEquals(3L, settings.pending()["media_hub_enabled"])
    settings.acknowledge(settings.pending())
    assertTrue(settings.pending().isEmpty())
  }

  @Test fun `offline startup retains local false and masks stale cloud true`() {
    val disk = SettingsTestPreferences()
    durable(disk).edit().putBoolean("double_tap_seek_enabled", false).apply()
    val restarted = durable(disk.restart())
    val cloud = CloudPlaybackPreferences(doubleTapSeekEnabled = true, mediaHubEnabled = true)
    val accepted = SettingsSyncRegistry.select(cloud, SettingsSyncRegistry.syncedKeys - SettingsSyncRegistry.protectedKeys(restarted.pending().keys))
    assertNull(accepted.doubleTapSeekEnabled)
    assertEquals(true, accepted.mediaHubEnabled)
    assertFalse(restarted.getBoolean("double_tap_seek_enabled", true))
  }

  @Test fun `cloud restoration persists without creating upload loop`() {
    val disk = SettingsTestPreferences()
    val settings = durable(disk)
    settings.receiving = true
    settings.edit().putBoolean("pip_enabled", false).putInt("double_tap_seek_seconds", 15).apply()
    val restored = durable(disk.restart())
    assertFalse(restored.getBoolean("pip_enabled", true))
    assertEquals(15, restored.getInt("double_tap_seek_seconds", 10))
    assertTrue(restored.pending().isEmpty())
  }

  @Test fun `upgrade journals existing values once and retains explicit false`() {
    val disk = SettingsTestPreferences()
    disk.edit().putBoolean("pip_enabled", false).commit()
    val settings = durable(disk)
    settings.migrateOnce()
    val revisions = settings.pending()
    assertTrue(revisions.containsKey("pip_enabled"))
    settings.acknowledge(revisions)
    durable(disk.restart()).migrateOnce()
    assertTrue(durable(disk.restart()).pending().isEmpty())
    assertFalse(disk.restart().getBoolean("pip_enabled", true))
  }

  @Test fun `failed disk save is reported without printing a setting value`() {
    val disk = SettingsTestPreferences().apply { failWrites = true }
    val events = mutableListOf<String>()
    val settings = DurableSettingsPreferences(disk, setOf("pip_enabled"), events::add)
    var failed = false
    settings.onWriteFailure = { failed = true }
    assertFalse(settings.edit().putBoolean("pip_enabled", false).commit())
    assertTrue(failed)
    assertEquals(listOf("disk_write_failed"), events)
  }

  @Test fun `composite navigation controls protect every participating key`() {
    val protected = SettingsSyncRegistry.protectedKeys(setOf("navigation_collapse_trigger"))
    val cloud = CloudPlaybackPreferences(navigationBehaviour = "Expanded", collapsibleNavigationEnabled = false)
    val accepted = SettingsSyncRegistry.select(cloud, SettingsSyncRegistry.syncedKeys - protected)
    assertNull(accepted.navigationBehaviour)
    assertNull(accepted.collapsibleNavigationEnabled)
  }

  @Test fun `account switch isolates pending values but keeps hardware preferences`() {
    val device = SettingsTestPreferences()
    val first = durable(SettingsTestPreferences())
    val second = durable(SettingsTestPreferences())
    val keys = setOf("pip_enabled")
    RoutedSettingsPreferences(device, first, keys).edit().putBoolean("pip_enabled", false).putBoolean("tunneled_playback", true).apply()
    val other = RoutedSettingsPreferences(device, second, keys)
    assertTrue(other.getBoolean("pip_enabled", true))
    assertTrue(other.getBoolean("tunneled_playback", false))
    other.edit().putBoolean("pip_enabled", true).apply()
    assertFalse(RoutedSettingsPreferences(device, first, keys).getBoolean("pip_enabled", true))
    assertEquals(setOf("pip_enabled"), first.pending().keys)
  }

  @Test fun `malformed retry metadata conservatively protects saved settings`() {
    val disk = SettingsTestPreferences()
    disk.edit().putBoolean("pip_enabled", false).putString(DurableSettingsPreferences.PENDING, "broken").commit()
    assertEquals(setOf("pip_enabled"), durable(disk).pending().keys)
  }
}

class CloudSettingsCodecTest {
  @Test fun `false zero and empty arrays round trip while missing remains absent`() {
    val value = CloudPlaybackPreferences(pictureInPictureEnabled = false, mediaHubEnabled = false,
      doubleTapSeekEnabled = false, maxFileSizeGb = 0, enabledRatingProviders = emptyList(), favoriteSourceKeys = emptyList())
    val restored = parseCloudSettings(cloudPreferencesPayload(value))
    assertEquals(false, restored.pictureInPictureEnabled)
    assertEquals(false, restored.mediaHubEnabled)
    assertEquals(false, restored.doubleTapSeekEnabled)
    assertEquals(0, restored.maxFileSizeGb)
    assertEquals(emptyList<String>(), restored.enabledRatingProviders)
    assertEquals(emptyList<String>(), restored.favoriteSourceKeys)
    assertNull(restored.skipIntroEnabled)
    assertNull(restored.homeDensity)
  }

  @Test fun `sparse edit does not write other defaults or legacy skip toggle`() {
    val payload = removeEmptySettingsSections(cloudPreferencesPayload(CloudPlaybackPreferences(pictureInPictureEnabled = false)))
    assertEquals(setOf("playback"), payload.keys().asSequence().toSet())
    assertEquals(setOf("pictureInPictureEnabled"), payload.getJSONObject("playback").keys().asSequence().toSet())
  }

  @Test fun `profile section merge preserves other clients settings`() {
    val old = JSONObject("""{"playback":{"externalPlayerEnabled":true,"autoLoadSubtitles":true},"liveFavouriteChannels":["channel"]}""")
    val patch = cloudProfilePreferencesPayload(CloudPlaybackPreferences(autoLoadSubtitles = false))
    val merged = mergeSettingsPatch(old, removeEmptySettingsSections(patch))
    assertTrue(merged.getJSONObject("playback").getBoolean("externalPlayerEnabled"))
    assertFalse(merged.getJSONObject("playback").getBoolean("autoLoadSubtitles"))
    assertEquals("channel", merged.getJSONArray("liveFavouriteChannels").getString(0))
  }

  @Test fun `every cloud field has a local ownership declaration and can round trip`() {
    val fields = CloudPlaybackPreferences::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
    assertEquals(fields.map { it.name }.toSet(), SettingsSyncRegistry.keysByField.keys)
    fields.filter { SettingsSyncRegistry.keysByField.getValue(it.name).isNotEmpty() }.forEach { field ->
      val value = CloudPlaybackPreferences()
      field.isAccessible = true
      val sample: Any = when (field.type) {
        java.lang.Boolean::class.java -> false
        java.lang.Integer::class.java -> 5
        java.lang.Float::class.java -> 1.5f
        String::class.java -> if (field.name == "homeCatalogRowsJson") "[]" else "sample"
        else -> listOf("sample")
      }
      field.set(value, sample)
      val decoded = parseCloudSettings(cloudPreferencesPayload(value))
      assertEquals(field.name, sample, field.get(decoded))
    }
  }
}
