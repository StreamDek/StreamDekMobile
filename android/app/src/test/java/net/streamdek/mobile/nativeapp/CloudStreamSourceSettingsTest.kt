package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStreamSourceSettingsTest {
  private fun switch(store: String, key: String, on: Boolean, at: Long) =
    CsSourceValue(store, key, CS_VALUE_BOOLEAN, on, updatedAt = at)

  private fun entry(repo: String, provider: String, vararg values: CsSourceValue) =
    CsSourceSettings(repo, provider, values = values.toList())

  private fun CsSourceSettings.valueOf(key: String): Any? = values.first { it.key == key }.value

  @Test
  fun `the same source name in two plugins stays two sources`() {
    val merged = mergeCsSourceSettings(
      listOf(entry("repo1", "ProviderA", switch("SKTech", "Source A", true, 10))),
      listOf(entry("repo2", "ProviderA", switch("SKTech", "Source A", false, 20))),
    )
    assertEquals(2, merged.size)
    assertEquals(true, merged.first { it.repoUrl == "repo1" }.valueOf("Source A"))
    assertEquals(false, merged.first { it.repoUrl == "repo2" }.valueOf("Source A"))
  }

  @Test
  fun `each switch goes to whichever device changed it last`() {
    val phone = listOf(entry("repo", "P", switch("s", "A", false, 200), switch("s", "B", true, 100)))
    val tv = listOf(entry("repo", "P", switch("s", "A", true, 100), switch("s", "B", false, 300)))
    val merged = mergeCsSourceSettings(phone, tv).single()
    assertEquals(false, merged.valueOf("A"))
    assertEquals(false, merged.valueOf("B"))
  }

  @Test
  fun `an observed default never overrules a choice`() {
    val account = listOf(entry("repo", "P", switch("s", "A", false, 500)))
    val freshDevice = listOf(entry("repo", "P", switch("s", "A", true, 0)))
    assertEquals(false, mergeCsSourceSettings(freshDevice, account).single().valueOf("A"))
    assertEquals(false, mergeCsSourceSettings(account, freshDevice).single().valueOf("A"))
    assertFalse(csSourceSettingsAhead(freshDevice, account))
  }

  @Test
  fun `an extension missing from one device keeps its switches`() {
    val merged = mergeCsSourceSettings(
      listOf(entry("repo", "Installed", switch("s", "A", true, 1))),
      listOf(entry("repo", "Elsewhere", switch("t", "B", false, 2))),
    )
    assertEquals(setOf("Installed", "Elsewhere"), merged.map { it.internalName }.toSet())
  }

  @Test
  fun `local choices the account lacks are reported as ahead`() {
    val local = listOf(entry("repo", "P", switch("s", "A", true, 50)))
    assertTrue(csSourceSettingsAhead(local, emptyList()))
    assertTrue(csSourceSettingsAhead(local, listOf(entry("repo", "P", switch("s", "A", false, 40)))))
    assertFalse(csSourceSettingsAhead(local, listOf(entry("repo", "P", switch("s", "A", false, 60)))))
  }

  @Test
  fun `json round trip keeps enabled and disabled, sets and deletions`() {
    val original = listOf(
      CsSourceSettings(
        "repo", "P", "Provider",
        listOf(
          switch("SKTech", "On", true, 1),
          switch("SKTech", "Off", false, 2),
          CsSourceValue(CS_DATASTORE_PREFS, "list", CS_VALUE_STRING_SET, listOf("a", "b"), updatedAt = 3),
          CsSourceValue(CS_DATASTORE_PREFS, "flag", CS_VALUE_STRING, "true", updatedAt = 4),
          CsSourceValue("SKTech", "Gone", CS_VALUE_BOOLEAN, null, removed = true, updatedAt = 5),
        ),
      ),
    )
    assertEquals(original, parseCsSourceSettings(csSourceSettingsJson(original)))
  }

  @Test
  fun `a visit claims the extension's own store whole`() {
    val existing = CsSourceSettings("repo", "P")
    val before = mapOf("SKTech" to mapOf<String, Any>("A" to true, "B" to true), CS_DATASTORE_PREFS to mapOf<String, Any>("other" to true))
    val after = mapOf("SKTech" to mapOf<String, Any>("A" to false, "B" to true), CS_DATASTORE_PREFS to mapOf<String, Any>("other" to true))
    val recorded = recordCsSourceVisit(existing, before, after, emptySet(), now = 1000)!!
    assertEquals(mapOf("SKTech" to 1000L), recorded.wholeStores)
    val byKey = recorded.values.associateBy { it.key }
    assertEquals(false, byKey.getValue("A").value)
    assertEquals(1000L, byKey.getValue("A").updatedAt)
    // Untouched, but shown on the same screen and saved with it: part of the choice.
    assertEquals(1000L, byKey.getValue("B").updatedAt)
    // The shared DataStore is only claimed key by key, and nothing there changed.
    assertFalse(byKey.containsKey("other"))
  }

  @Test
  fun `a source the extension deleted is recorded as switched off`() {
    val existing = CsSourceSettings("repo", "P", values = listOf(switch("SKTech", "A", true, 0), switch("SKTech", "B", true, 0)))
    val before = mapOf("SKTech" to mapOf<String, Any>("A" to true, "B" to true))
    val after = mapOf("SKTech" to mapOf<String, Any>("B" to true))
    val byKey = recordCsSourceVisit(existing, before, after, setOf("SKTech"), now = 5)!!.values.associateBy { it.key }
    assertTrue(byKey.getValue("A").removed)
    assertEquals(5L, byKey.getValue("A").updatedAt)
  }

  @Test
  fun `a visit that changes nothing records nothing`() {
    val snapshot = mapOf("SKTech" to mapOf<String, Any>("A" to true))
    val settled = CsSourceSettings("repo", "P", values = listOf(switch("SKTech", "A", true, 9)), wholeStores = mapOf("SKTech" to 9))
    assertNull(recordCsSourceVisit(settled, snapshot, snapshot, setOf("SKTech"), 10))
  }

  @Test
  fun `a chosen store is made to match exactly, defaults and all`() {
    // The phone saved SKTech with only TCL TV+ on; the television still has its own VIZIO default.
    val values = listOf(
      switch("SKTech", "TCL TV+", true, 100),
      CsSourceValue("SKTech", "SONY-LIV", CS_VALUE_BOOLEAN, null, removed = true, updatedAt = 100),
      switch("SKTech", "VIZIO", true, 0),
    )
    val plan = planCsStoreWrite("SKTech", values, mapOf("VIZIO" to true, "SONY-LIV" to true, "token" to "abc"), wholeStamp = 100)
    assertEquals(listOf("TCL TV+"), plan.puts.map { it.key })
    assertEquals(setOf("VIZIO", "SONY-LIV"), plan.removes)
  }

  @Test
  fun `a record from before whole stores existed is only gap-filled`() {
    // The phone's first recording kept an untouched TCL TV+ at stamp zero, with no whole-store mark.
    val values = listOf(
      switch("SKTech", "TCL TV+", true, 0),
      CsSourceValue("SKTech", "SONY-LIV", CS_VALUE_BOOLEAN, null, removed = true, updatedAt = 100),
    )
    val plan = planCsStoreWrite("SKTech", values, mapOf("TCL TV+" to true, "SONY-LIV" to true))
    assertEquals(setOf("SONY-LIV"), plan.removes)
    assertTrue(plan.puts.isEmpty())
  }

  @Test
  fun `a whole-store record merges and survives the round trip`() {
    val a = listOf(CsSourceSettings("repo", "P", values = listOf(switch("S", "A", true, 5)), wholeStores = mapOf("S" to 5)))
    val b = listOf(CsSourceSettings("repo", "P", values = listOf(switch("S", "A", false, 9)), wholeStores = mapOf("S" to 9)))
    val merged = mergeCsSourceSettings(a, b)
    assertEquals(mapOf("S" to 9L), merged.single().wholeStores)
    assertEquals(merged, parseCsSourceSettings(csSourceSettingsJson(merged)))
    assertTrue(csSourceSettingsAhead(b, a))
  }

  @Test
  fun `an unchosen store only has its gaps filled`() {
    val values = listOf(switch("SKTech", "A", true, 0), switch("SKTech", "B", false, 0))
    val plan = planCsStoreWrite("SKTech", values, mapOf("A" to false))
    assertEquals(listOf("B"), plan.puts.map { it.key })
    assertTrue(plan.removes.isEmpty())
  }

  @Test
  fun `the shared stores are never cleared wholesale`() {
    val plan = planCsStoreWrite(CS_DATASTORE_PREFS, listOf(switch(CS_DATASTORE_PREFS, "mine", true, 50)), mapOf("someone-else" to true))
    assertTrue(plan.removes.isEmpty())
    assertEquals(listOf("mine"), plan.puts.map { it.key })
  }

  @Test
  fun `only switch-shaped values are carried`() {
    assertEquals(CS_VALUE_BOOLEAN to true, csSwitchValue(true))
    assertEquals(CS_VALUE_STRING to "false", csSwitchValue("false"))
    assertNull(csSwitchValue("session-token"))
    assertNull(csSwitchValue(42))
  }
}
