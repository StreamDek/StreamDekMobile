package net.streamdek.mobile.nativeapp

import android.content.SharedPreferences
import android.content.Context
import android.util.Log
import net.streamdek.mobile.R
import org.json.JSONObject

/** No values, account ids, URLs or credentials are written to diagnostics. */
internal fun settingsDiagnostic(event: String) { Log.i("StreamDekSettings", event) }

/** Small user-owned settings files outside AppSettingsStore use the same durable write boundary. */
internal fun Context.durableSettingsPreferences(name: String): SharedPreferences =
  DurableSettingsPreferences(applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)).also { preferences ->
    preferences.onWriteFailure = {
      android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(applicationContext, R.string.settings_save_failed, android.widget.Toast.LENGTH_LONG).show()
      }
    }
  }

internal fun SharedPreferences.Editor.putSetting(key: String, value: Any?): SharedPreferences.Editor = apply {
  when (value) {
    null -> remove(key)
    is Boolean -> putBoolean(key, value)
    is Int -> putInt(key, value)
    is Long -> putLong(key, value)
    is Float -> putFloat(key, value)
    is String -> putString(key, value)
    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
    else -> error("Unsupported preference type")
  }
}

/**
 * A value and its retry revision reach disk in the SAME transaction, before a setter returns.
 * Downloaded values use [receiving], so a download never creates another upload. An acknowledgement
 * only removes the revision actually sent; an edit during a slow request remains pending.
 */
internal class DurableSettingsPreferences(
  internal val storage: SharedPreferences,
  private val syncedKeys: Set<String> = emptySet(),
  private val diagnostic: (String) -> Unit = ::settingsDiagnostic,
) : SharedPreferences by storage {
  var receiving = false
  var onWriteFailure: (() -> Unit)? = null
  var revision = 0L
    private set

  fun pending(): Map<String, Long> {
    val document = runCatching { JSONObject(storage.getString(PENDING, "{}").orEmpty()) }.getOrElse {
      diagnostic("pending_metadata_invalid")
      // Recover conservatively: keep the stored choices and retry them.
      return storage.all.keys.intersect(syncedKeys).associateWith { 1L }
    }
    return document.keys().asSequence().associateWith { document.optLong(it) }
  }

  fun acknowledge(sent: Map<String, Long>) {
    val remaining = pending().toMutableMap()
    sent.forEach { (key, revision) -> if (remaining[key] == revision) remaining.remove(key) }
    if (!storage.edit().putString(PENDING, JSONObject(remaining as Map<*, *>).toString()).commit()) failed()
  }

  fun markExistingForUpload() {
    val editor = edit()
    storage.all.filterKeys { it in syncedKeys }.forEach { (key, value) -> editor.putSetting(key, value) }
    editor.commit()
  }

  fun migrateOnce() {
    if (storage.getBoolean(MIGRATED, false)) return
    val editor = edit().putBoolean(MIGRATED, true)
    storage.all.filterKeys { it in syncedKeys }.forEach { (key, value) -> editor.putSetting(key, value) }
    editor.commit()
  }

  private fun failed() { diagnostic("disk_write_failed"); onWriteFailure?.invoke() }

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
      override fun apply() { commit() }
      override fun commit(): Boolean = synchronized(storage) {
        val editor = storage.edit()
        if (clear) editor.clear()
        changes.forEach { (key, value) -> editor.putSetting(key, value) }
        val dirty = changes.keys.intersect(syncedKeys)
        if (!receiving && dirty.isNotEmpty()) {
          val next = storage.getLong(SEQUENCE, 0) + 1
          val pending = pending().toMutableMap()
          dirty.forEach { pending[it] = next }
          editor.putLong(SEQUENCE, next).putString(PENDING, JSONObject(pending as Map<*, *>).toString())
        }
        revision++
        editor.commit().also { success ->
          if (!success) failed()
          else if (!receiving && dirty.isNotEmpty()) diagnostic("local_commit keys=${dirty.size}")
        }
      }
    }
  }

  companion object {
    const val PENDING = "__settings_pending_v1"
    const val SEQUENCE = "__settings_sequence_v1"
    const val MIGRATED = "__settings_migrated_v1"
  }
}

/** Account cache for synced device preferences; hardware-only preferences stay in the device file. */
internal class RoutedSettingsPreferences(
  private val device: SharedPreferences,
  private val account: SharedPreferences,
  private val accountKeys: Set<String>,
) : SharedPreferences by device {
  private fun source(key: String) = if (key in accountKeys) account else device
  override fun contains(key: String) = source(key).contains(key)
  override fun getAll(): MutableMap<String, *> = (device.all.filterKeys { it !in accountKeys } + account.all.filterKeys { it in accountKeys }).toMutableMap()
  override fun getString(key: String, defValue: String?) = source(key).getString(key, defValue)
  override fun getStringSet(key: String, defValues: MutableSet<String>?) = source(key).getStringSet(key, defValues)
  override fun getInt(key: String, defValue: Int) = source(key).getInt(key, defValue)
  override fun getLong(key: String, defValue: Long) = source(key).getLong(key, defValue)
  override fun getFloat(key: String, defValue: Float) = source(key).getFloat(key, defValue)
  override fun getBoolean(key: String, defValue: Boolean) = source(key).getBoolean(key, defValue)
  override fun edit(): SharedPreferences.Editor {
    val changes = linkedMapOf<String, Any?>()
    return object : SharedPreferences.Editor {
      override fun putString(key: String, value: String?) = apply { changes[key] = value }
      override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values?.toSet() }
      override fun putInt(key: String, value: Int) = apply { changes[key] = value }
      override fun putLong(key: String, value: Long) = apply { changes[key] = value }
      override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
      override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
      override fun remove(key: String) = apply { changes[key] = null }
      override fun clear() = apply { all.keys.forEach { changes[it] = null } }
      override fun apply() { commit() }
      override fun commit(): Boolean {
        val scoped = account.edit()
        val local = device.edit()
        changes.forEach { (key, value) ->
          if (key in accountKeys) scoped.putSetting(key, value)
          // Keep the startup theme/locale and backup projection current, too.
          local.putSetting(key, value)
        }
        val saved = scoped.commit()
        return local.commit() && saved
      }
    }
  }
}

internal data class PendingSettingsWrite(
  val files: List<Pair<DurableSettingsPreferences, Map<String, Long>>>,
) {
  val keys = files.flatMap { it.second.keys }.toSet()
  fun acknowledge() = files.forEach { (file, revisions) -> file.acknowledge(revisions) }
}
