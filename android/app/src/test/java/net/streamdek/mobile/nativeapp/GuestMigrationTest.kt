package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merges behind "bring your StreamDek setup with you".
 *
 * The rule the whole migration is built on is that nothing a viewer has is allowed to disappear
 * because they signed in: lists are unioned on a stable identity, positions are resolved by which
 * is newer, and a profile that has already answered for itself is never overruled. These are the
 * decisions each of those merges makes, tested where they are decided rather than through the
 * fourteen preference files they happen to be applied to.
 */
class GuestMigrationTest {

  private data class Entry(val key: String, val at: Long)

  // ── Watchlists, favourites, add-ons: union, destination wins ──────────────────────────────────

  @Test
  fun `a union keeps the destination's copy and appends what it has never seen`() {
    val merged = mergePreservingTarget(
      target = listOf("tv:1399" to "account", "movie:27205" to "account"),
      source = listOf("tv:1399" to "guest", "movie:157336" to "guest"),
      identity = { it.first },
    )
    assertEquals(listOf("tv:1399", "movie:27205", "movie:157336"), merged.map { it.first })
    assertEquals("account", merged.first { it.first == "tv:1399" }.second)
  }

  @Test
  fun `running the union twice adds nothing the first run did not`() {
    val target = listOf("a", "b")
    val source = listOf("b", "c")
    val once = mergePreservingTarget(target, source) { it }
    assertEquals(once, mergePreservingTarget(once, source) { it })
  }

  // ── Playback positions: newest wins, per title ────────────────────────────────────────────────

  @Test
  fun `a position resolves to whichever side touched it last, in either direction`() {
    val merged = mergeNewestWins(
      target = listOf(Entry("tv:1399:s1:e1", at = 200), Entry("movie:27205", at = 500)),
      source = listOf(Entry("tv:1399:s1:e1", at = 900), Entry("movie:603", at = 100)),
      identity = { it.key },
      updatedAt = { it.at },
    )
    assertEquals(900L, merged.first { it.key == "tv:1399:s1:e1" }.at)
    // The account's newer film position is not dragged backwards by the guest's older one.
    assertEquals(500L, merged.first { it.key == "movie:27205" }.at)
    assertEquals(3, merged.size)
  }

  @Test
  fun `a losing position is dropped rather than kept as a second row for the same episode`() {
    val merged = mergeNewestWins(
      target = listOf(Entry("tv:1399:s1:e1", at = 10)),
      source = listOf(Entry("tv:1399:s1:e1", at = 20)),
      identity = { it.key },
      updatedAt = { it.at },
    )
    assertEquals(1, merged.size)
  }

  // ── Watched sets ─────────────────────────────────────────────────────────────────────────────

  @Test
  fun `watched episodes union, because neither side saying watched can cancel the other`() {
    val merged = unionJsonStringArrays("""["e1","e2"]""", """["e2","e3"]""")
    assertEquals(listOf("e1", "e2", "e3"), JSONArray(merged).let { array -> List(array.length()) { array.optString(it) } })
  }

  @Test
  fun `nothing to merge leaves the destination exactly as it was`() {
    assertEquals("""["e1"]""", unionJsonStringArrays("""["e1"]""", null))
    assertNull(unionJsonStringArrays(null, null))
    // Unparseable incoming data is ignored rather than allowed to destroy what is there.
    assertEquals("""["e1"]""", unionJsonStringArrays("""["e1"]""", "not json"))
  }

  // ── Records with identity ────────────────────────────────────────────────────────────────────

  @Test
  fun `records merge on identity and objects with none are not guessed at`() {
    val target = JSONArray().put(JSONObject().put("url", "https://a").put("enabled", true)).toString()
    val source = JSONArray()
      .put(JSONObject().put("url", "https://a").put("enabled", false))
      .put(JSONObject().put("url", "https://b"))
      .put(JSONObject().put("name", "nameless"))
      .toString()
    val merged = JSONArray(mergeJsonObjectArrays(target, source, identity = { it.optString("url").takeIf(String::isNotBlank) }))
    assertEquals(2, merged.length())
    // The destination's record of a shared source wins: its enabled flag is the one being synced.
    assertEquals(true, merged.getJSONObject(0).optBoolean("enabled"))
    assertEquals("https://b", merged.getJSONObject(1).optString("url"))
  }

  // ── Plugin documents ─────────────────────────────────────────────────────────────────────────

  private fun pluginDocument(repoUrl: String, providerId: String, token: String?, updatedAt: Long): String =
    JSONObject()
      .put("enabled", true)
      .put("updatedAt", updatedAt)
      .put("repos", JSONArray().put(JSONObject().put("url", repoUrl).put("name", "Collection")))
      .put(
        "providers",
        JSONArray().put(
          JSONObject()
            .put("id", providerId)
            .put("repo", repoUrl)
            .put("enabled", true)
            .put("settings", JSONObject().apply { token?.let { put("token", it) } }),
        ),
      )
      .toString()

  @Test
  fun `a guest's collections arrive whole, with the settings that make them work`() {
    val merged = JSONObject(
      mergePluginStateDocuments(
        targetRaw = pluginDocument("https://repo-a", "alpha", token = "account-token", updatedAt = 50),
        sourceRaw = pluginDocument("https://repo-b", "beta", token = "guest-token", updatedAt = 10),
        now = 1_000,
      )!!,
    )
    assertEquals(2, merged.getJSONArray("repos").length())
    val providers = merged.getJSONArray("providers")
    assertEquals(2, providers.length())
    val beta = (0 until providers.length()).map { providers.getJSONObject(it) }.first { it.optString("id") == "beta" }
    assertEquals("guest-token", beta.getJSONObject("settings").optString("token"))
  }

  @Test
  fun `the profile's own copy of a shared source keeps its token`() {
    val merged = JSONObject(
      mergePluginStateDocuments(
        targetRaw = pluginDocument("https://repo-a", "alpha", token = "account-token", updatedAt = 50),
        sourceRaw = pluginDocument("https://repo-a", "alpha", token = "stale-guest-token", updatedAt = 900),
        now = 1_000,
      )!!,
    )
    assertEquals(1, merged.getJSONArray("providers").length())
    assertEquals(
      "account-token",
      merged.getJSONArray("providers").getJSONObject(0).getJSONObject("settings").optString("token"),
    )
  }

  /**
   * The merged document has to look newer than both sides it came from, or the reconciliation that
   * decides whether to push it or take the account's could hand back the very copy it was merged
   * with - losing the guest's sources a second after they arrived.
   */
  @Test
  fun `a merged document is stamped as newly written`() {
    val merged = JSONObject(
      mergePluginStateDocuments(
        targetRaw = pluginDocument("https://repo-a", "alpha", null, updatedAt = 50),
        sourceRaw = pluginDocument("https://repo-b", "beta", null, updatedAt = 900),
        now = 1_000,
      )!!,
    )
    assertTrue(merged.getLong("updatedAt") >= 1_000)
  }

  @Test
  fun `a CloudStream or SkyStream document keeps its own shape`() {
    // Those documents have no top-level `enabled`, and name their sources repoUrl + internalName.
    fun document(repoUrl: String, internalName: String, updatedAt: Long) = JSONObject()
      .put("updatedAt", updatedAt)
      .put("repos", JSONArray().put(JSONObject().put("url", repoUrl)))
      .put("providers", JSONArray().put(JSONObject().put("repoUrl", repoUrl).put("internalName", internalName)))
      .toString()

    val merged = JSONObject(
      mergePluginStateDocuments(
        targetRaw = document("https://cs-a", "Alpha", 10),
        sourceRaw = document("https://cs-b", "Beta", 20),
        now = 100,
      )!!,
    )
    assertEquals(false, merged.has("enabled"))
    assertEquals(2, merged.getJSONArray("providers").length())
  }

  @Test
  fun `nothing to bring over leaves the destination document untouched`() {
    val target = pluginDocument("https://repo-a", "alpha", null, updatedAt = 5)
    assertEquals(target, mergePluginStateDocuments(target, null))
    assertEquals(target, mergePluginStateDocuments(target, "{}"))
    assertEquals(target, mergePluginStateDocuments(target, "not json"))
  }

  @Test
  fun `a document says how many sources it holds, and bad input says none`() {
    assertEquals(1, countProviders(pluginDocument("https://repo-a", "alpha", null, updatedAt = 1)))
    assertEquals(0, countProviders("{}"))
    assertEquals(0, countProviders("not json"))
    assertEquals(0, countProviders(null))
  }

  // ── Naming a profile after the address that made the account ─────────────────────────────────

  @Test
  fun `an address becomes a name`() {
    assertEquals("Henry Okwuenu", defaultProfileNameFromEmail("henry.okwuenu@example.com"))
    assertEquals("Henry Okwuenu", defaultProfileNameFromEmail("henry_okwuenu@example.com"))
    assertEquals("Henry Okwuenu", defaultProfileNameFromEmail("henry-okwuenu@example.com"))
    // A mailbox disambiguator names a mailbox, not a person.
    assertEquals("Henry", defaultProfileNameFromEmail("henry+streamdek@example.com"))
    assertEquals("Henry", defaultProfileNameFromEmail("  HENRY@example.com  "))
  }

  @Test
  fun `an address with no name in it is left for the caller to name`() {
    assertNull(defaultProfileNameFromEmail(""))
    assertNull(defaultProfileNameFromEmail(null))
    assertNull(defaultProfileNameFromEmail("@example.com"))
  }

  @Test
  fun `a profile name cannot exceed what the profile screen accepts`() {
    val name = defaultProfileNameFromEmail("a".repeat(80) + "@example.com")
    assertEquals(32, name?.length)
  }

  // ── What the prompt says it found ────────────────────────────────────────────────────────────

  @Test
  fun `an identity with nothing on it is not worth asking about`() {
    assertTrue(GuestDataSummary().isEmpty)
    assertTrue(!GuestDataSummary(watchlistItems = 1).isEmpty)
    assertTrue(!GuestDataSummary(hasPreferences = true).isEmpty)
  }

  @Test
  fun `a migration is named by the pairing it moves between, not by the device`() {
    assertEquals("guest:abc>user-1:profile-2", guestMigrationToken("guest:abc", "user-1:profile-2"))
    assertTrue(guestMigrationToken("guest", "user-1") != guestMigrationToken("guest", "user-2"))
  }

  @Test
  fun `a migrated layout follows its add-ons to the ids they have under the profile`() {
    val rows = JSONArray()
      .put(JSONObject().put("id", "trending_movies").put("enabled", true))
      .put(JSONObject().put("id", "addon:guest-a:movie:top:0").put("enabled", false))
      .put(JSONObject().put("id", "addon:other:series:top:1").put("enabled", true))
      .toString()
    val remapped = JSONArray(remapHomeRowAddonIds(rows, mapOf("guest-a" to "profile-a")))
    assertEquals("trending_movies", remapped.getJSONObject(0).getString("id"))
    assertEquals("addon:profile-a:movie:top:0", remapped.getJSONObject(1).getString("id"))
    // The switch travels with the row, which is the point of carrying the layout at all.
    assertEquals(false, remapped.getJSONObject(1).getBoolean("enabled"))
    assertEquals("addon:other:series:top:1", remapped.getJSONObject(2).getString("id"))
  }

  @Test
  fun `a remap that lands two rows on one catalogue keeps the higher one`() {
    val rows = JSONArray()
      .put(JSONObject().put("id", "addon:guest-a:movie:top:0").put("enabled", false))
      .put(JSONObject().put("id", "addon:profile-a:movie:top:0").put("enabled", true))
      .toString()
    val remapped = JSONArray(remapHomeRowAddonIds(rows, mapOf("guest-a" to "profile-a")))
    assertEquals(1, remapped.length())
    assertEquals(false, remapped.getJSONObject(0).getBoolean("enabled"))
  }

  @Test
  fun `the source order is remapped with the rows and nothing else in it moves`() {
    val order = JSONArray().put("streamdek").put("guest-a").put("guest-b").toString()
    val remapped = JSONArray(remapHomeRowSourceOrder(order, mapOf("guest-a" to "profile-a")))
    assertEquals(listOf("streamdek", "profile-a", "guest-b"), List(remapped.length()) { remapped.getString(it) })
    assertNull(remapHomeRowSourceOrder(null, mapOf("a" to "b")))
  }
}
