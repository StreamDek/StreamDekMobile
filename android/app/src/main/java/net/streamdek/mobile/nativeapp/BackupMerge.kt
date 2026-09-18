package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import org.json.JSONObject

/**
 * How a backup is laid over what is already on the device.
 *
 * One rule throughout: **a restore adds and corrects, it never deletes.** Where the backup and the
 * device both have something - the same add-on, the same plugin source - the backup's copy wins,
 * because bringing that copy back is what the viewer asked for. Anything the device has that the
 * backup does not is kept, after the backup's own items. On a fresh install, which is the case
 * this exists for, that is exactly the setup that was backed up; on a device that has been used
 * since, nothing set up in the meantime is lost.
 *
 * Library data (progress, watched history) is different in kind: neither side is "the setup", so
 * lists are unioned and positions resolved newest-first, the same way the guest migration does it.
 *
 * Pure JSON in, JSON out, so every rule is tested without a device.
 */

/**
 * A plugin document restored over the device's own copy.
 *
 * Each of the three engines is merged separately - StreamDek's JS collections at the top level,
 * CloudStream and SkyStream in their sections - with the backup's collections and sources first
 * and winning collisions. Every part is stamped [now]: the account arbitrates between devices on
 * these stamps, and a restored document that kept the backup's old stamps would be replaced by the
 * account's copy on the next sync, undoing the restore a few seconds after it finished.
 *
 * CloudStream source switches merge value by value like they do with the account, with the
 * backup's values restamped so they outrank anything older on the device.
 */
internal fun mergeRestoredPluginDocument(backup: JSONObject, current: JSONObject?, now: Long): JSONObject {
  val device = current ?: JSONObject()
  val sectionKeys = setOf("cloudstream", "skystream", CLOUDSTREAM_SOURCE_SETTINGS_KEY)
  fun jsPart(document: JSONObject) = JSONObject(document.toString()).apply { sectionKeys.forEach(::remove) }

  val result = mergeRestoredEngineDocument(jsPart(backup), jsPart(device), now)
  listOf("cloudstream", "skystream").forEach { section ->
    val fromBackup = backup.optJSONObject(section)
    val onDevice = device.optJSONObject(section)
    when {
      fromBackup != null -> result.put(section, mergeRestoredEngineDocument(fromBackup, onDevice, now))
      onDevice != null -> result.put(section, onDevice)
    }
  }
  val restoredSwitches = parseCsSourceSettings(backup.optJSONArray(CLOUDSTREAM_SOURCE_SETTINGS_KEY)).map { entry ->
    entry.copy(values = entry.values.map { it.copy(updatedAt = now) }, wholeStores = entry.wholeStores.mapValues { now })
  }
  val deviceSwitches = parseCsSourceSettings(device.optJSONArray(CLOUDSTREAM_SOURCE_SETTINGS_KEY))
  if (restoredSwitches.isNotEmpty() || deviceSwitches.isNotEmpty()) {
    result.put(CLOUDSTREAM_SOURCE_SETTINGS_KEY, csSourceSettingsJson(mergeCsSourceSettings(deviceSwitches, restoredSwitches)))
  }
  return result
}

/**
 * One engine's document restored over the device's.
 *
 * A source the backup carries without its settings - an unencrypted backup leaves them out, since
 * they are cookies and keys - keeps the settings it has on the device, so restoring an unencrypted
 * backup never signs a working source out.
 */
private fun mergeRestoredEngineDocument(backup: JSONObject, device: JSONObject?, now: Long): JSONObject {
  val restored = JSONObject(backup.toString())
  val deviceProviders = device?.optJSONArray("providers")?.let { array ->
    (0 until array.length()).mapNotNull { array.optJSONObject(it) }.associateBy { pluginProviderIdentity(it) }
  }.orEmpty()
  restored.optJSONArray("providers")?.let { providers ->
    for (index in 0 until providers.length()) {
      val provider = providers.optJSONObject(index) ?: continue
      if (provider.has("settings")) continue
      deviceProviders[pluginProviderIdentity(provider)]?.optJSONObject("settings")?.let { provider.put("settings", it) }
    }
  }
  val merged = mergePluginStateDocuments(restored.toString(), device?.toString(), now)
    ?.let { runCatching { JSONObject(it) }.getOrNull() }
    ?: restored
  return merged.put("updatedAt", now)
}

/**
 * Takes the secrets out of a plugin document for an unencrypted backup.
 *
 * A source's settings are where plugins keep what they need to sign in - a cookie, a key - so they
 * are removed whole; the switches and the order stay. CloudStream extensions keep text values in
 * their switches store too, and those go for the same reason; on/off switches and choices stay.
 *
 * @return the names of the sources that lost something, for the backup to list as left out.
 */
internal fun stripPluginSecrets(document: JSONObject): List<String> {
  val names = linkedSetOf<String>()
  fun stripProviders(engine: JSONObject?) {
    val providers = engine?.optJSONArray("providers") ?: return
    for (index in 0 until providers.length()) {
      val provider = providers.optJSONObject(index) ?: continue
      val settings = provider.optJSONObject("settings")
      if (settings != null && settings.length() > 0) names += provider.optString("name").ifBlank { pluginProviderIdentity(provider).orEmpty() }
      provider.remove("settings")
    }
  }
  stripProviders(document)
  stripProviders(document.optJSONObject("skystream"))
  document.optJSONArray(CLOUDSTREAM_SOURCE_SETTINGS_KEY)?.let { array ->
    val kept = parseCsSourceSettings(array).map { entry ->
      val (text, rest) = entry.values.partition { it.type == CS_VALUE_STRING && !it.removed }
      if (text.isNotEmpty()) names += entry.name ?: entry.internalName
      entry.copy(values = rest)
    }
    document.put(CLOUDSTREAM_SOURCE_SETTINGS_KEY, csSourceSettingsJson(kept))
  }
  return names.filter { it.isNotBlank() }
}

/** Collections and sources across all three engines of a plugin document. */
internal fun countPluginDocument(document: JSONObject?): Pair<Int, Int> {
  if (document == null) return 0 to 0
  val engines = listOfNotNull(document, document.optJSONObject("cloudstream"), document.optJSONObject("skystream"))
  return engines.sumOf { it.optJSONArray("repos")?.length() ?: 0 } to engines.sumOf { it.optJSONArray("providers")?.length() ?: 0 }
}

// ── Lists ───────────────────────────────────────────────────────────────────────────────────

/**
 * An ordered list restored over the device's: the backup's items in the backup's order, then
 * anything only the device has. Items are matched on [identity] and renumbered by `position` when
 * they carry one, so the result is one continuous order.
 */
internal fun mergeRestoredOrderedList(backup: JSONArray?, deviceRaw: String?, identity: (JSONObject) -> String?): String? {
  if (backup == null || backup.length() == 0) return deviceRaw
  val sortedBackup = JSONArray().apply {
    (0 until backup.length()).mapNotNull { backup.optJSONObject(it) }
      .sortedBy { it.optInt("position", Int.MAX_VALUE) }
      .forEach(::put)
  }
  val merged = mergeJsonObjectArrays(targetRaw = sortedBackup.toString(), sourceRaw = deviceRaw, identity = identity)
    ?: return deviceRaw
  val array = JSONArray(merged)
  for (index in 0 until array.length()) {
    val item = array.optJSONObject(index) ?: continue
    if (item.has("position")) item.put("position", index)
  }
  return array.toString()
}

/** Watchlist and favourites: a union, with the device's order kept and the backup's titles after. */
internal fun mergeRestoredLibraryList(backup: JSONArray?, deviceRaw: String?, identity: (JSONObject) -> String?): String? {
  if (backup == null || backup.length() == 0) return deviceRaw
  if (deviceRaw.isNullOrBlank()) return backup.toString()
  return mergeJsonObjectArrays(targetRaw = deviceRaw, sourceRaw = backup.toString(), identity = identity) ?: deviceRaw
}

internal fun watchlistIdentity(item: JSONObject): String? {
  val id = item.optString("id").trim().takeIf { it.isNotEmpty() } ?: return null
  return item.optString("type").trim() + ":" + id
}

internal fun manifestUrlIdentity(field: String): (JSONObject) -> String? = { item ->
  item.optString(field).trim().lowercase().takeIf { it.isNotEmpty() }
}

/**
 * Continue Watching positions restored over the device's: newest wins per film or episode.
 *
 * An entry that wins from the backup has its `syncedAt` cleared. That field means "the account
 * has this", and a device that reads it on a row the account does not have concludes the row was
 * removed elsewhere and deletes it - which, for a backup restored onto a new account or after the
 * account's copy was cleared, would throw away the very progress that was just restored. Cleared,
 * the entry is uploaded instead.
 */
internal fun mergeRestoredResumeEntries(backup: JSONArray?, deviceRaw: String?): String? {
  if (backup == null || backup.length() == 0) return deviceRaw
  fun keyOf(item: JSONObject) = listOf(
    item.optString("mediaType"),
    item.optString("mediaId"),
    item.optInt("seasonNumber", -1),
    item.optInt("episodeNumber", -1),
  ).joinToString(":")
  val merged = LinkedHashMap<String, JSONObject>()
  val device = runCatching { JSONArray(deviceRaw.orEmpty().ifBlank { "[]" }) }.getOrDefault(JSONArray())
  for (index in 0 until device.length()) device.optJSONObject(index)?.let { merged[keyOf(it)] = it }
  for (index in 0 until backup.length()) {
    val entry = backup.optJSONObject(index) ?: continue
    if (entry.optString("mediaId").isBlank()) continue
    val key = keyOf(entry)
    val existing = merged[key]
    if (existing == null || entry.optLong("updatedAt") > existing.optLong("updatedAt")) {
      merged[key] = JSONObject(entry.toString()).put("syncedAt", JSONObject.NULL)
    }
  }
  return JSONArray(merged.values.sortedByDescending { it.optLong("updatedAt") }).toString()
}

/** Next Up history: per series episode, the newer record wins. */
internal fun mergeRestoredNextUp(backup: JSONArray?, deviceRaw: String?): String? {
  if (backup == null || backup.length() == 0) return deviceRaw
  fun keyOf(item: JSONObject) = listOf(item.optString("id"), item.optInt("season"), item.optInt("episode")).joinToString(":")
  val merged = LinkedHashMap<String, JSONObject>()
  val device = runCatching { JSONArray(deviceRaw.orEmpty().ifBlank { "[]" }) }.getOrDefault(JSONArray())
  (0 until device.length()).mapNotNull { device.optJSONObject(it) }.forEach { merged[keyOf(it)] = it }
  (0 until backup.length()).mapNotNull { backup.optJSONObject(it) }.forEach { entry ->
    val existing = merged[keyOf(entry)]
    if (existing == null || entry.optLong("at") > existing.optLong("at")) merged[keyOf(entry)] = entry
  }
  return JSONArray(merged.values.sortedByDescending { it.optLong("at") }.take(400)).toString()
}
