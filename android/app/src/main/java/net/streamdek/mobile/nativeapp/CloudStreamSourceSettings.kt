package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * Which sources a CloudStream extension has switched on, as part of the profile.
 *
 * A `.cs3` extension often bundles several sites and lets the viewer pick among them from its own
 * settings screen - SKTech's "SONY-LIV", "LG TV", "TCL TV+"; SportzX's "CricHD", "World Sports". The
 * extension keeps those switches itself, in SharedPreferences it opens by name, and registers only
 * the sources that are on when it loads. StreamDek never sees a schema for them, so they used to
 * live on one device only: a phone set up source by source, and a television beside it still
 * offering everything.
 *
 * This file carries them to the account as values, not as a schema:
 *
 * - **The hierarchy is plugin, provider, source.** A collection's URL, the extension's internal
 *   name inside it, then the preference store and key the extension wrote. Nothing is identified by
 *   a source's name alone, so "Source A" in one extension is never mistaken for "Source A" in
 *   another.
 * - **Every value carries its own stamp**, and a merge keeps the newer of two stamps per value.
 *   Switching one source on a phone and another on a television, each offline, loses neither.
 * - **Only an intentional change gets a real stamp.** A value is stamped when the viewer changes it
 *   in the extension's settings. Values merely *found* on a device - an extension's defaults, or
 *   what was set before this sync existed - are recorded with a stamp of zero: they fill in where
 *   the account has nothing and are never allowed to overrule it, and are only ever written into a
 *   store that does not have that key yet. A stale or default device cannot reset the account.
 * - **Absence is not deletion.** An extension that is not installed here, fails to load or has been
 *   renamed keeps its values in the document untouched; they apply again when it turns up.
 *
 * Only switch-shaped values are carried - booleans, string sets, and `"true"`/`"false"` strings,
 * which is how CloudStream's own DataStore writes a boolean. Extensions also keep cookies, tokens
 * and caches in the same stores, and none of those belong in a synced document.
 */

/** Where the profile plugin document carries these, beside (not inside) its `cloudstream` section. */
internal const val CLOUDSTREAM_SOURCE_SETTINGS_KEY = "cloudstreamSourceSettings"

/** The app's default SharedPreferences, whose file name differs between the phone and TV apps. */
internal const val CS_DEFAULT_PREFS_STORE = "@default"

/** CloudStream's own DataStore (`getKey`/`setKey`), which every extension shares. */
internal const val CS_DATASTORE_PREFS = "rebuild_preference"

/** Stores more than one extension writes to, where only the keys a change touched can be claimed. */
internal val CS_SHARED_PREFS_STORES = setOf(CS_DEFAULT_PREFS_STORE, CS_DATASTORE_PREFS)

internal const val CS_VALUE_BOOLEAN = "boolean"
internal const val CS_VALUE_STRING_SET = "stringSet"
internal const val CS_VALUE_STRING = "string"

/** One source switch: a key in one of an extension's preference stores. */
data class CsSourceValue(
  val store: String,
  val key: String,
  /** [CS_VALUE_BOOLEAN], [CS_VALUE_STRING_SET] or [CS_VALUE_STRING]. */
  val type: String,
  /** A Boolean, a sorted List<String>, or a String, by [type]; null when [removed]. */
  val value: Any?,
  /** The extension deleted the key. Carried so the deletion reaches the other devices too. */
  val removed: Boolean = false,
  /** When the viewer made this change; zero for a value that was only observed. See the file note. */
  val updatedAt: Long = 0L,
) {
  val id: String get() = "$store\u0000$key"
}

/** Everything recorded for one extension (provider) inside one collection (plugin). */
data class CsSourceSettings(
  val repoUrl: String,
  val internalName: String,
  /** The extension's display name when it was last recorded, for the portal. Not an identifier. */
  val name: String? = null,
  val values: List<CsSourceValue> = emptyList(),
  /**
   * Stores recorded *whole* by a visit to the extension's settings, and when: at that moment the
   * store held exactly the values stamped for it and nothing else. Only these stores are made to
   * match exactly on other devices (see [planCsStoreWrite]); without the marker a store is only
   * gap-filled, which is also what keeps records written before the marker existed safe to apply.
   */
  val wholeStores: Map<String, Long> = emptyMap(),
) {
  val identity: String get() = csSourceIdentity(repoUrl, internalName)
  val updatedAt: Long get() = maxOf(values.maxOfOrNull { it.updatedAt } ?: 0L, wholeStores.values.maxOrNull() ?: 0L)
}

internal fun csSourceIdentity(repoUrl: String, internalName: String): String = "$repoUrl\u0000$internalName"

/** The newest stamp anywhere in [settings]; what the plugin-version poll compares. */
internal fun csSourceSettingsLatest(settings: List<CsSourceSettings>): Long = settings.maxOfOrNull { it.updatedAt } ?: 0L

internal fun parseCsSourceSettings(array: JSONArray?): List<CsSourceSettings> {
  if (array == null) return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val repoUrl = item.optString("repoUrl").trim()
      val internalName = item.optString("internalName").trim()
      if (repoUrl.isEmpty() || internalName.isEmpty()) continue
      val values = item.optJSONArray("values") ?: JSONArray()
      add(
        CsSourceSettings(
          repoUrl = repoUrl,
          internalName = internalName,
          name = item.optString("name").takeIf { it.isNotBlank() && it != "null" },
          values = buildList {
            for (valueIndex in 0 until values.length()) parseCsSourceValue(values.optJSONObject(valueIndex))?.let(::add)
          },
          wholeStores = item.optJSONObject("wholeStores")?.let { stores ->
            buildMap { stores.keys().forEach { store -> stores.optLong(store, 0L).takeIf { it > 0L }?.let { put(store, it) } } }
          }.orEmpty(),
        ),
      )
    }
  }
}

private fun parseCsSourceValue(item: JSONObject?): CsSourceValue? {
  item ?: return null
  val store = item.optString("store").trim()
  val key = item.optString("key")
  if (store.isEmpty() || key.isEmpty()) return null
  val removed = item.optBoolean("removed", false)
  val type = item.optString("type")
  val value: Any? = if (removed) null else when (type) {
    CS_VALUE_BOOLEAN -> (item.opt("value") as? Boolean) ?: return null
    CS_VALUE_STRING_SET -> item.optJSONArray("value")?.let { array -> List(array.length()) { array.optString(it) }.sorted() } ?: return null
    CS_VALUE_STRING -> item.opt("value") as? String ?: return null
    else -> return null
  }
  return CsSourceValue(store, key, type, value, removed, item.optLong("updatedAt", 0L).coerceAtLeast(0L))
}

internal fun csSourceSettingsJson(settings: List<CsSourceSettings>): JSONArray = JSONArray().apply {
  settings.filter { it.values.isNotEmpty() || it.wholeStores.isNotEmpty() }.forEach { entry ->
    put(
      JSONObject()
        .put("repoUrl", entry.repoUrl)
        .put("internalName", entry.internalName)
        .put("name", entry.name ?: JSONObject.NULL)
        .put("updatedAt", entry.updatedAt)
        .put("wholeStores", JSONObject().apply { entry.wholeStores.forEach { (store, at) -> put(store, at) } })
        .put(
          "values",
          JSONArray().apply {
            entry.values.forEach { value ->
              put(
                JSONObject()
                  .put("store", value.store)
                  .put("key", value.key)
                  .put("type", value.type)
                  .put(
                    "value",
                    when {
                      value.removed -> JSONObject.NULL
                      value.value is List<*> -> JSONArray(value.value)
                      else -> value.value ?: JSONObject.NULL
                    },
                  )
                  .put("removed", value.removed)
                  .put("updatedAt", value.updatedAt),
              )
            }
          },
        ),
    )
  }
}

/**
 * Two sets of recorded source switches joined, per value.
 *
 * The newer stamp wins each value; on a tie [incoming] does, so two devices holding only observed
 * values (stamp zero) settle on whichever reached the account first instead of trading places on
 * every sync. Values and extensions only one side knows are kept - absence on one device says
 * nothing about what the viewer wants.
 */
internal fun mergeCsSourceSettings(local: List<CsSourceSettings>, incoming: List<CsSourceSettings>): List<CsSourceSettings> {
  if (incoming.isEmpty()) return local
  if (local.isEmpty()) return incoming
  val merged = LinkedHashMap<String, CsSourceSettings>()
  local.forEach { merged[it.identity] = it }
  incoming.forEach { entry ->
    val existing = merged[entry.identity]
    if (existing == null) {
      merged[entry.identity] = entry
      return@forEach
    }
    val values = LinkedHashMap<String, CsSourceValue>()
    existing.values.forEach { values[it.id] = it }
    entry.values.forEach { value ->
      val current = values[value.id]
      if (current == null || value.updatedAt >= current.updatedAt) values[value.id] = value
    }
    val wholeStores = HashMap(existing.wholeStores)
    entry.wholeStores.forEach { (store, at) -> if (at > (wholeStores[store] ?: 0L)) wholeStores[store] = at }
    merged[entry.identity] = existing.copy(name = entry.name ?: existing.name, values = values.values.toList(), wholeStores = wholeStores)
  }
  return merged.values.toList()
}

/** Whether [candidate] holds any value that is newer than, or missing from, [reference]. */
internal fun csSourceSettingsAhead(candidate: List<CsSourceSettings>, reference: List<CsSourceSettings>): Boolean {
  val known = reference.associateBy { it.identity }
  return candidate.any { entry ->
    val theirEntry = known[entry.identity] ?: return@any entry.values.isNotEmpty() || entry.wholeStores.isNotEmpty()
    val other = theirEntry.values.associateBy { it.id }
    entry.values.any { value -> val theirs = other[value.id]; theirs == null || value.updatedAt > theirs.updatedAt } ||
      entry.wholeStores.any { (store, at) -> at > (theirEntry.wholeStores[store] ?: 0L) }
  }
}

/** A preference value as a source switch, or null when it is not switch-shaped. See the file note. */
internal fun csSwitchValue(raw: Any?): Pair<String, Any>? = when (raw) {
  is Boolean -> CS_VALUE_BOOLEAN to raw
  is Set<*> -> CS_VALUE_STRING_SET to raw.map { it.toString() }.sorted()
  is String -> if (raw == "true" || raw == "false") CS_VALUE_STRING to raw else null
  else -> null
}

/**
 * Records one visit to an extension's settings screen.
 *
 * [before] and [after] are the switch-shaped contents of every store, taken either side of the
 * visit. Keys that changed are stamped [now].
 *
 * A store only this extension writes to is claimed *whole*: the screen showed every source and the
 * extension's own Save wrote them all, so what the store holds afterwards is the viewer's complete
 * choice, not just the switches they happened to flip. Every switch in it is stamped [now] (one
 * already recorded with that same value keeps its earlier stamp), and a switch recorded before that
 * the store no longer holds is recorded as removed - many extensions turn a source off by deleting
 * its key, so absence is itself a choice. See [planCsStoreWrite] for how a whole store is applied.
 *
 * @return the updated entry, or null when the visit changed nothing.
 */
internal fun recordCsSourceVisit(
  existing: CsSourceSettings,
  before: Map<String, Map<String, Any>>,
  after: Map<String, Map<String, Any>>,
  ownedStores: Set<String>,
  now: Long,
): CsSourceSettings? {
  val values = LinkedHashMap<String, CsSourceValue>()
  existing.values.forEach { values[it.id] = it }
  val wholeStores = HashMap(existing.wholeStores)
  var changed = false
  val touchedStores = HashSet<String>()
  (before.keys + after.keys).forEach { store ->
    val old = before[store].orEmpty()
    val new = after[store].orEmpty()
    (old.keys + new.keys).forEach { key ->
      if (old[key] == new[key]) return@forEach
      touchedStores += store
      val switch = new[key]
      val value = if (switch == null) {
        CsSourceValue(store, key, old[key]?.let { csSwitchValue(it)?.first } ?: CS_VALUE_BOOLEAN, null, removed = true, updatedAt = now)
      } else {
        val (type, typed) = csSwitchValue(switch) ?: return@forEach
        CsSourceValue(store, key, type, typed, updatedAt = now)
      }
      values[value.id] = value
      changed = true
    }
  }
  (touchedStores + ownedStores).filterNot { it in CS_SHARED_PREFS_STORES }.forEach { store ->
    val held = after[store].orEmpty()
    if (wholeStores[store] == null) changed = true
    wholeStores[store] = now
    held.forEach { (key, raw) ->
      val (type, typed) = csSwitchValue(raw) ?: return@forEach
      val id = "$store\u0000$key"
      val current = values[id]
      if (current != null && current.updatedAt > 0L && !current.removed && current.value == typed) return@forEach
      values[id] = CsSourceValue(store, key, type, typed, updatedAt = now)
      changed = true
    }
    values.values.filter { it.store == store && !it.removed && it.key !in held }.forEach { gone ->
      values[gone.id] = gone.copy(value = null, removed = true, updatedAt = now)
      changed = true
    }
  }
  return if (changed) existing.copy(values = values.values.toList(), wholeStores = wholeStores) else null
}

/** What applying recorded switches to one store comes to: keys to write, and keys to delete. */
internal data class CsStoreWrite(val puts: List<CsSourceValue>, val removes: Set<String>) {
  val isEmpty: Boolean get() = puts.isEmpty() && removes.isEmpty()
}

private fun csSameValue(value: CsSourceValue, current: Any?): Boolean = when (value.type) {
  CS_VALUE_STRING_SET -> (current as? Set<*>)?.map { it.toString() }?.toSet() == (value.value as? List<*>)?.map { it.toString() }?.toSet()
  else -> current == value.value
}

/**
 * How one store on this device should change to reflect [values] recorded for it.
 *
 * Two cases, and the difference is the whole point:
 *
 * - **A store someone has chosen for** - an extension's own store that a visit to its settings
 *   recorded whole ([wholeStamp], see [recordCsSourceVisit]) - is made to match that choice exactly.
 *   Chosen switches are written, removed ones deleted, and any other switch this device holds is
 *   deleted too: it is either a default the extension wrote here or a leftover from before the
 *   choice, and keeping it would switch on a source the viewer switched off elsewhere. Values that
 *   were only observed are ignored. Non-switch values (tokens, caches) are never touched.
 * - **A store nobody has chosen for yet**, or one of the two shared stores, only has gaps filled:
 *   observed values are written where this device has no such key, stamped ones wherever they
 *   differ. Nothing here can undo a choice, because there is none.
 */
internal fun planCsStoreWrite(
  store: String,
  values: List<CsSourceValue>,
  current: Map<String, Any?>,
  /** When the store was last recorded whole, or null if it never has been. */
  wholeStamp: Long? = null,
): CsStoreWrite {
  val chosen = values.filter { it.updatedAt > 0L }
  val puts = mutableListOf<CsSourceValue>()
  val removes = LinkedHashSet<String>()
  if (store !in CS_SHARED_PREFS_STORES && wholeStamp != null && wholeStamp > 0L) {
    chosen.forEach { value ->
      if (value.removed) {
        if (current.containsKey(value.key)) removes += value.key
      } else if (!csSameValue(value, current[value.key])) {
        puts += value
      }
    }
    val mentioned = chosen.mapTo(HashSet()) { it.key }
    current.forEach { (key, raw) -> if (key !in mentioned && csSwitchValue(raw) != null) removes += key }
    return CsStoreWrite(puts, removes)
  }
  values.forEach { value ->
    if (value.updatedAt <= 0L && (value.removed || current.containsKey(value.key))) return@forEach
    if (value.removed) {
      if (current.containsKey(value.key)) removes += value.key
    } else if (!csSameValue(value, current[value.key])) {
      puts += value
    }
  }
  return CsStoreWrite(puts, removes)
}

/**
 * The extension preference stores on this device, and the loads that open them.
 *
 * Ownership is learned rather than declared: while an extension is being loaded, any store it opens
 * through the host StreamDek hands it is noted as that extension's. That is what lets switches set
 * before this sync existed be found at start-up without the viewer opening every settings screen.
 */
internal object CloudStreamSourcePrefs {
  private const val TAG = "CloudStreamSources"

  /** The `.cs3` being loaded right now, if any. Loads are serialised, so there is only ever one. */
  @Volatile var loadingPath: String? = null

  private val owners = ConcurrentHashMap<String, String>()

  /** Called by every host context an extension is given, whenever it opens a preference store. */
  fun noteOpened(context: Context, fileName: String?) {
    val path = loadingPath ?: return
    val store = storeOf(context, fileName ?: return)
    if (store in CS_SHARED_PREFS_STORES || isAppStore(store)) return
    owners[store] = path
  }

  fun storesOwnedBy(path: String?): Set<String> =
    if (path == null) emptySet() else owners.filterValues { it == path }.keys.toSet()

  fun storeOf(context: Context, fileName: String): String =
    if (fileName == "${context.packageName}_preferences") CS_DEFAULT_PREFS_STORE else fileName

  private fun fileOf(context: Context, store: String): String =
    if (store == CS_DEFAULT_PREFS_STORE) "${context.packageName}_preferences" else store

  /** StreamDek's own files, which no extension writes and which must never be synced as one. */
  private fun isAppStore(store: String): Boolean =
    store.startsWith("streamdek", ignoreCase = true) || store.startsWith("WebView") || store.startsWith("androidx") ||
      store.startsWith("com.google") || store.startsWith("_") ||
      // The system WebView's own files on Fire OS, which sit beside the app's.
      store.startsWith("AmazonWebView") || store.startsWith("AwOrigin")

  private fun prefs(context: Context, store: String): SharedPreferences =
    context.applicationContext.getSharedPreferences(fileOf(context, store), Context.MODE_PRIVATE)

  /** Every store an extension may have written: the shared two, and any file StreamDek does not own. */
  private fun candidateStores(context: Context): Set<String> {
    val dir = File(context.applicationInfo.dataDir, "shared_prefs")
    val files = dir.listFiles()?.map { it.name }.orEmpty()
      .filter { it.endsWith(".xml") }
      .map { storeOf(context, it.removeSuffix(".xml")) }
      .filterNot(::isAppStore)
    return files.toSet() + CS_SHARED_PREFS_STORES
  }

  /** The switch-shaped contents of every candidate store, for [recordCsSourceVisit]. */
  fun snapshot(context: Context): Map<String, Map<String, Any>> = runCatching {
    candidateStores(context).associateWith { store -> switches(context, store) }
  }.onFailure { Log.w(TAG, "Could not read extension preferences", it) }.getOrDefault(emptyMap())

  fun switches(context: Context, store: String): Map<String, Any> =
    prefs(context, store).all.mapNotNull { (key, raw) -> csSwitchValue(raw)?.let { key to raw!! } }.toMap()

  /**
   * Writes recorded switches into this device's stores, as [planCsStoreWrite] decides. Committed
   * rather than applied: the extension is about to be reloaded and reads these back straight away.
   *
   * @return whether anything was written.
   */
  fun apply(context: Context, entry: CsSourceSettings): Boolean {
    var wrote = false
    val stores = entry.values.groupBy { it.store } + entry.wholeStores.keys.filterNot { store -> entry.values.any { it.store == store } }.associateWith { emptyList() }
    stores.forEach { (store, storeValues) ->
      if (isAppStore(store)) return@forEach
      val prefs = prefs(context, store)
      val plan = planCsStoreWrite(store, storeValues, prefs.all, entry.wholeStores[store])
      if (plan.isEmpty) return@forEach
      val editor = prefs.edit()
      plan.removes.forEach(editor::remove)
      plan.puts.forEach { value ->
        when (value.type) {
          CS_VALUE_BOOLEAN -> (value.value as? Boolean)?.let { editor.putBoolean(value.key, it) }
          CS_VALUE_STRING -> (value.value as? String)?.let { editor.putString(value.key, it) }
          CS_VALUE_STRING_SET -> (value.value as? List<*>)?.map { it.toString() }?.toSet()?.let { editor.putStringSet(value.key, it) }
        }
      }
      editor.commit()
      wrote = true
    }
    return wrote
  }
}
