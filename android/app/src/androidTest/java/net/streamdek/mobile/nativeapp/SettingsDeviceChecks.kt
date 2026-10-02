package net.streamdek.mobile.nativeapp

import android.content.Context

/** Two separate instrumentation runs, with force-stop/reboot between them; isolated fixture only. */
internal class SettingsDeviceChecks(private val context: Context) {
  fun run(phase: String): String {
    val raw = context.getSharedPreferences("settings_persistence_device_fixture_v1", Context.MODE_PRIVATE)
    val store = DurableSettingsPreferences(raw, SettingsSyncRegistry.syncedKeys)
    if (phase == "write") {
      check(raw.edit().clear().commit())
      repeat(50) {
        store.edit().putBoolean("pip_enabled", false).putBoolean("double_tap_seek_enabled", false)
          .putInt("double_tap_seek_seconds", 30).putBoolean("media_hub_enabled", it % 2 != 0).apply()
      }
    } else check(phase == "read")
    check(!store.getBoolean("pip_enabled", true))
    check(!store.getBoolean("double_tap_seek_enabled", true))
    check(store.getInt("double_tap_seek_seconds", 0) == 30)
    check(store.getBoolean("media_hub_enabled", false))
    check(store.pending().keys.containsAll(setOf("pip_enabled", "double_tap_seek_enabled", "double_tap_seek_seconds", "media_hub_enabled")))
    return "PASS settings $phase: persisted values and pending uploads survived; fixture only\n"
  }
}
