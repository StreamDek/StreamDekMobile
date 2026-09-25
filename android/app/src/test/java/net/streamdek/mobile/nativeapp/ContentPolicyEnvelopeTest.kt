package net.streamdek.mobile.nativeapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Signing vectors produced by the backend's real signer, verified by the app's verifier. */
class ContentPolicyEnvelopeTest {
  private val signing = JSONObject(javaClass.classLoader!!.getResource("content-safety/fixtures.json")!!.readText()).getJSONObject("signing")
  private val trusted = ContentPolicyEnvelope.trustedKeys((0 until signing.getJSONArray("trustedPublicKeys").length())
    .joinToString(",") { signing.getJSONArray("trustedPublicKeys").getString(it) })

  @Test fun `key ids match the backend`() {
    assertEquals(setOf(signing.getString("trustedKeyId")), trusted.keys)
  }

  @Test fun `every signing vector is accepted or refused as the contract says`() {
    val cases = signing.getJSONArray("cases")
    for (index in 0 until cases.length()) {
      val case = cases.getJSONObject(index)
      val held = case.opt("held") as? String
      val open = { ContentPolicyEnvelope.open(case.getJSONObject("response"), trusted, held) }
      if (case.getString("expect") == "accept") {
        val policy = open()
        // The signed payload is what applies; unsigned legacy fields beside it are ignored.
        assertEquals(case.getString("name"), true, policy.getBoolean("blockAdult"))
        assertEquals(case.getString("name"), 1, policy.getJSONArray("rules").length())
      } else {
        assertThrows(case.getString("name"), ContentPolicyEnvelope.Rejected::class.java) { open() }
      }
    }
  }

  @Test fun `builds without trusted keys keep reading the legacy fields`() {
    val response = JSONObject(mapOf("blockAdult" to false, "terms" to emptyList<String>()))
    assertEquals(false, ContentPolicyEnvelope.open(response, emptyMap()).getBoolean("blockAdult"))
  }

  @Test fun `base64 decoding matches the standard alphabet and rejects junk`() {
    assertTrue(ContentPolicyEnvelope.decodeBase64("AAECAwQ=").contentEquals(byteArrayOf(0, 1, 2, 3, 4)))
    assertTrue(ContentPolicyEnvelope.decodeBase64("+/8=").contentEquals(byteArrayOf(-5, -1)))
    assertThrows(IllegalArgumentException::class.java) { ContentPolicyEnvelope.decodeBase64("***") }
    assertThrows(IllegalArgumentException::class.java) { ContentPolicyEnvelope.decodeBase64("A") }
  }
}
