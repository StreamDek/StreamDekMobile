package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a backup is laid over a device: the backup's copy wins, nothing already on the device is
 * lost, and everything restored is stamped so the account keeps it rather than syncing over it.
 */
class BackupMergeTest {
  private val now = 1_789_740_000_000L

  private fun provider(repo: String, id: String, enabled: Boolean, settings: JSONObject? = null) =
    JSONObject().put("repo", repo).put("id", id).put("name", id).put("enabled", enabled).apply { settings?.let { put("settings", it) } }

  private fun jsDocument(vararg providers: JSONObject, repos: List<String> = providers.map { it.getString("repo") }.distinct(), at: Long = 1L) =
    JSONObject().put("updatedAt", at)
      .put("repos", JSONArray().apply { repos.forEach { put(JSONObject().put("url", it).put("name", it)) } })
      .put("providers", JSONArray().apply { providers.forEach(::put) })

  private fun providers(document: JSONObject) =
    (0 until document.getJSONArray("providers").length()).map { document.getJSONArray("providers").getJSONObject(it) }

  @Test
  fun `a restored plugin document keeps the backup's switches and the device's extra sources`() {
    val backup = jsDocument(provider("r1", "a", enabled = false))
    val device = jsDocument(provider("r1", "a", enabled = true), provider("r2", "b", enabled = true))
    val merged = mergeRestoredPluginDocument(backup, device, now)
    val byId = providers(merged).associateBy { it.getString("id") }
    assertFalse("the backup's switch wins", byId.getValue("a").getBoolean("enabled"))
    assertTrue("a source only the device has is kept", "b" in byId)
    assertEquals(now, merged.getLong("updatedAt"))
  }

  @Test
  fun `a backup without source settings does not sign a working source out`() {
    val cookie = JSONObject().put("cookie", "abc")
    val merged = mergeRestoredPluginDocument(jsDocument(provider("r1", "a", true)), jsDocument(provider("r1", "a", true, cookie)), now)
    assertEquals("abc", providers(merged).single().getJSONObject("settings").getString("cookie"))
  }

  @Test
  fun `every engine's section is stamped so the account takes it`() {
    val backup = jsDocument(provider("r1", "a", true))
      .put("cloudstream", JSONObject().put("updatedAt", 5L).put("repos", JSONArray()).put("providers", JSONArray().put(JSONObject().put("repoUrl", "cs").put("internalName", "x"))))
      .put("skystream", JSONObject().put("updatedAt", 5L).put("repos", JSONArray()).put("providers", JSONArray().put(JSONObject().put("repoUrl", "sky").put("packageName", "y"))))
    val merged = mergeRestoredPluginDocument(backup, JSONObject(), now)
    assertEquals(now, merged.getJSONObject("cloudstream").getLong("updatedAt"))
    assertEquals(now, merged.getJSONObject("skystream").getLong("updatedAt"))
  }

  @Test
  fun `restored CloudStream switches outrank older ones on the device`() {
    fun switches(value: Boolean, at: Long) = JSONArray().put(
      JSONObject().put("repoUrl", "cs").put("internalName", "x").put("wholeStores", JSONObject())
        .put("values", JSONArray().put(JSONObject().put("store", "s").put("key", "k").put("type", CS_VALUE_BOOLEAN).put("value", value).put("removed", false).put("updatedAt", at))),
    )
    val backup = JSONObject().put(CLOUDSTREAM_SOURCE_SETTINGS_KEY, switches(false, 10L))
    // The device changed it later than the backup was made; the restore still brings the backup's back.
    val device = JSONObject().put(CLOUDSTREAM_SOURCE_SETTINGS_KEY, switches(true, 500L))
    val merged = parseCsSourceSettings(mergeRestoredPluginDocument(backup, device, now).getJSONArray(CLOUDSTREAM_SOURCE_SETTINGS_KEY))
    assertEquals(false, merged.single().values.single().value)
  }

  @Test
  fun `an unencrypted backup loses plugin sign-ins but keeps the switches`() {
    val document = jsDocument(provider("r1", "a", true, JSONObject().put("cookie", "abc")), provider("r1", "b", false))
      .put("skystream", JSONObject().put("providers", JSONArray().put(JSONObject().put("repoUrl", "sky").put("packageName", "p").put("name", "Sky P").put("settings", JSONObject().put("key", "k")))))
    val names = stripPluginSecrets(document)
    assertEquals(listOf("a", "Sky P"), names)
    assertFalse(document.toString().contains("abc"))
    assertFalse(providers(document).first().has("settings"))
    assertFalse(providers(document).last().getBoolean("enabled"))
  }

  @Test
  fun `an ordered list is restored in the backup's order with the device's extras after`() {
    val backup = JSONArray()
      .put(JSONObject().put("url", "https://b").put("name", "B from backup").put("position", 1))
      .put(JSONObject().put("url", "https://a").put("name", "A").put("position", 0))
    val device = JSONArray()
      .put(JSONObject().put("url", "https://c").put("name", "C").put("position", 0))
      .put(JSONObject().put("url", "HTTPS://B").put("name", "B on device").put("position", 1))
    val merged = JSONArray(mergeRestoredOrderedList(backup, device.toString(), manifestUrlIdentity("url")))
    val items = (0 until merged.length()).map { merged.getJSONObject(it) }
    assertEquals(listOf("A", "B from backup", "C"), items.map { it.getString("name") })
    assertEquals(listOf(0, 1, 2), items.map { it.getInt("position") })
  }

  @Test
  fun `continue watching keeps the newer position and uploads what came from the backup`() {
    fun entry(id: String, at: Long, position: Double, syncedAt: Long?) = JSONObject()
      .put("mediaId", id).put("mediaType", "movie").put("updatedAt", at).put("positionSeconds", position)
      .put("syncedAt", syncedAt ?: JSONObject.NULL)
    val backup = JSONArray().put(entry("1", 100L, 60.0, syncedAt = 90L)).put(entry("2", 100L, 30.0, syncedAt = 90L))
    val device = JSONArray().put(entry("1", 200L, 120.0, syncedAt = 200L))
    val merged = JSONArray(mergeRestoredResumeEntries(backup, device.toString()))
    val byId = (0 until merged.length()).map { merged.getJSONObject(it) }.associateBy { it.getString("mediaId") }
    assertEquals(120.0, byId.getValue("1").getDouble("positionSeconds"), 0.0)
    assertEquals(200L, byId.getValue("1").getLong("syncedAt"))
    assertTrue("a restored entry is sent to the account, not read as deleted there", byId.getValue("2").isNull("syncedAt"))
  }

  @Test
  fun `watchlists are unioned`() {
    val backup = JSONArray().put(JSONObject().put("id", "1").put("type", "movie")).put(JSONObject().put("id", "1").put("type", "tv"))
    val device = JSONArray().put(JSONObject().put("id", "1").put("type", "movie")).put(JSONObject().put("id", "9").put("type", "tv"))
    val merged = JSONArray(mergeRestoredLibraryList(backup, device.toString(), ::watchlistIdentity))
    val keys = (0 until merged.length()).map { merged.getJSONObject(it).let { item -> watchlistIdentity(item) } }
    assertEquals(listOf("movie:1", "tv:9", "tv:1"), keys)
  }

  @Test
  fun `next up keeps the newer record per episode`() {
    fun record(season: Int, episode: Int, at: Long) = JSONObject().put("id", "1399").put("season", season).put("episode", episode).put("at", at)
    val merged = JSONArray(mergeRestoredNextUp(JSONArray().put(record(1, 1, 50L)).put(record(1, 2, 60L)), JSONArray().put(record(1, 1, 70L)).toString()))
    assertEquals(2, merged.length())
    assertEquals(70L, (0 until merged.length()).map { merged.getJSONObject(it) }.first { it.getInt("episode") == 1 }.getLong("at"))
  }

  @Test
  fun `an add-on installed twice keeps each copy's own switch`() {
    // Two copies of one add-on, one on and one off: the case an undo got wrong when it matched on
    // the URL alone and gave both entries the same copy.
    data class Installed(val id: String, val url: String)
    val installed = listOf(Installed("a1", "https://x/manifest.json"), Installed("a2", "HTTPS://X/manifest.json"))
    val entries = listOf(
      BackupAddonEntry("a2", "https://x/manifest.json", "X", enabled = false, favourite = false, position = 0),
      BackupAddonEntry("a1", "https://x/manifest.json", "X", enabled = true, favourite = false, position = 1),
    )
    val matched = matchAddonEntries(entries, installed, idOf = { it.id }, urlOf = { it.url })
    assertEquals(listOf("a2" to "a2", "a1" to "a1"), matched.map { it.first.id to it.second.id })

    // From another account the ids mean nothing, so the URL decides - still one copy per entry.
    val fromElsewhere = entries.map { it.copy(id = "other-" + it.id) }
    val byUrl = matchAddonEntries(fromElsewhere, installed, idOf = { it.id }, urlOf = { it.url })
    assertEquals(listOf("a1", "a2"), byUrl.map { it.second.id })
  }

  @Test
  fun `restoring nothing leaves the device as it was`() {
    assertEquals("[1]", mergeRestoredOrderedList(JSONArray(), "[1]", manifestUrlIdentity("url")))
    assertEquals("[1]", mergeRestoredResumeEntries(null, "[1]"))
    assertEquals("[1]", mergeRestoredLibraryList(JSONArray(), "[1]", ::watchlistIdentity))
  }
}
