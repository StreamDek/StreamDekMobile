package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The app half of the shared content-safety contract. The fixture file is generated in the backend
 * repository (scripts/content-safety-fixtures.js) and copied here unchanged; the backend runs the
 * same cases, so a difference between the two implementations fails one side or the other.
 */
class ContentSafetyContractTest {
  private val fixtures = JSONObject(javaClass.classLoader!!.getResource("content-safety/fixtures.json")!!.readText())

  private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
  private fun JSONArray.strings() = (0 until length()).map { getString(it) }

  @After fun resetPolicy() = AdultContentFilter.applyPolicy(true, emptyList(), emptyList())

  @Test fun normalisation() {
    fixtures.getJSONArray("normalization").objects().forEach { case ->
      assertEquals(case.getString("input"), case.getString("normalized"), AdultSourceIdentity.normalize(case.getString("input")))
    }
  }

  @Test fun `built-in identities and repositories`() {
    fixtures.getJSONArray("identity").objects().forEach { case ->
      assertEquals(case.getString("input"), case.getBoolean("adult"), AdultSourceIdentity.matches(case.getString("input")))
    }
  }

  @Test fun `rule validation`() {
    val validation = fixtures.getJSONObject("ruleValidation")
    validation.getJSONArray("valid").objects().forEach { case ->
      val parsed = ContentSafetyRule.parse(case.getJSONArray("rules")).associateBy { it.id }
      val expected = case.getJSONObject("expected")
      assertEquals(case.getString("name"), expected.keys().asSequence().toSet(), parsed.keys)
      expected.keys().asSequence().toSet().forEach { id ->
        val want = expected.getJSONObject(id); val got = parsed.getValue(id)
        assertEquals(case.getString("name"), listOf("scope", "kind", "value", "status", "reason").map { want.getString(it) },
          listOf(got.scope, got.kind, got.value, got.status, got.reason))
      }
    }
    validation.getJSONArray("invalid").objects().forEach { case ->
      assertThrows(case.getString("name"), Exception::class.java) { ContentSafetyRule.parse(case.getJSONArray("rules")) }
    }
    val limit = validation.getInt("maxRules")
    fun rules(count: Int) = JSONArray((0 until count).map { JSONObject(mapOf("id" to "r$it", "scope" to "provider", "kind" to "identity", "value" to "a", "status" to "ADULT", "reason" to "r")) })
    assertEquals(limit, ContentSafetyRule.parse(rules(limit)).size)
    assertThrows(Exception::class.java) { ContentSafetyRule.parse(rules(limit + 1)) }
  }

  @Test fun `rule matching`() {
    fixtures.getJSONArray("ruleMatching").objects().forEach { case ->
      val rule = ContentSafetyRule.parse(JSONArray().put(case.getJSONObject("rule"))).single()
      assertEquals(case.getString("name"), case.getBoolean("matches"), rule.matches(case.getString("scope"), case.getJSONArray("fields").strings()))
    }
  }

  @Test fun `text signals, including joined-up names`() {
    fixtures.getJSONArray("textSignals").objects().forEach { case ->
      assertEquals(case.getString("input"), case.getBoolean("adult"), AdultContentFilter.isBlocked(case.getString("input")))
    }
  }

  @Test fun `CloudStream plugins`() {
    fixtures.getJSONObject("cloudStream").getJSONArray("plugins").objects().forEach { case ->
      AdultContentFilter.applyPolicy(true, emptyList(), ContentSafetyRule.parse(case.optJSONArray("rules") ?: JSONArray()))
      val blocked = isBlockedCsPlugin(case.getString("repoUrl"), case.getString("internalName"), case.getString("pluginName"),
        case.getString("downloadUrl"), case.getString("description"), case.getJSONArray("tvTypes").strings())
      assertEquals(case.getString("name"), case.getBoolean("blocked"), blocked)
    }
  }

  @Test fun `CloudStream repositories`() {
    fixtures.getJSONObject("cloudStream").getJSONArray("repositories").objects().forEach { case ->
      val types = case.getJSONArray("pluginTvTypes").let { outer -> (0 until outer.length()).map { outer.getJSONArray(it).strings() } }
      assertEquals(case.getString("name"), case.getBoolean("blocked"),
        isAdultOnlyCsRepo(case.getString("url"), case.getString("repoName"), case.getString("description"), types))
    }
  }

  /** Enforcement checks each level from the top, so a parent block cannot be undone by a child exception. */
  @Test fun `decisions across the hierarchy`() {
    fixtures.getJSONArray("decisions").objects().forEach { case ->
      AdultContentFilter.applyPolicy(case.optBoolean("blockAdult", true), emptyList(), ContentSafetyRule.parse(case.getJSONArray("rules")))
      val blocked = case.getJSONArray("levels").objects().any { level ->
        AdultContentFilter.isBlockedEntity(level.getString("scope"), *level.getJSONArray("fields").strings().toTypedArray())
      }
      assertEquals(case.getString("name"), case.getBoolean("blocked"), blocked)
    }
  }
}
