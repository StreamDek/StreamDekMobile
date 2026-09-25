package net.streamdek.mobile.nativeapp

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.json.JSONObject

/**
 * Opens the backend's signed content policy.
 *
 * The backend signs the exact payload bytes with ECDSA P-256 (see contentPolicySigning.ts), so the
 * payload is verified before it is parsed and nothing here depends on JSON formatting. A build
 * with trusted keys accepts only signed policies, and never one published before the policy it
 * already holds, so a replayed or intercepted response cannot switch protection off or drop rules.
 * A build without keys (local development) keeps reading the unsigned legacy fields.
 */
internal object ContentPolicyEnvelope {
  class Rejected(message: String) : Exception(message)

  private val isoInstant = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")

  /** Comma-separated base64 SPKI keys, as STREAMDEK_CONTENT_POLICY_PUBLIC_KEYS supplies them. */
  fun trustedKeys(encoded: String): Map<String, PublicKey> = encoded.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    .associate { value ->
      val spki = decodeBase64(value)
      keyId(spki) to KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
    }

  /** First 16 hex characters of SHA-256 over the SPKI bytes, matching the backend's key id. */
  fun keyId(spki: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(spki).joinToString("") { "%02x".format(it) }.take(16)

  /**
   * The policy fields to apply from a /public/content-policy [response].
   *
   * [heldPublishedAt] is the publication time of the policy already in force; anything older is
   * refused. Pass null when restoring from local storage, where there is nothing to compare with.
   */
  fun open(response: JSONObject, trusted: Map<String, PublicKey>, heldPublishedAt: String? = null): JSONObject {
    if (trusted.isEmpty()) return response
    val envelope = response.optJSONObject("signed") ?: throw Rejected("Content policy is not signed")
    if (envelope.optString("algorithm") != "ES256") throw Rejected("Unsupported content policy signature")
    val key = trusted[envelope.optString("keyId")] ?: throw Rejected("Content policy signed by an untrusted key")
    val payload = envelope.opt("payload") as? String ?: throw Rejected("Content policy payload missing")
    val signature = runCatching { decodeBase64(envelope.getString("signature")) }.getOrElse { throw Rejected("Content policy signature malformed") }
    val valid = runCatching {
      Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(payload.toByteArray(Charsets.UTF_8)); verify(signature) }
    }.getOrDefault(false)
    if (!valid) throw Rejected("Content policy signature does not verify")
    val policy = JSONObject(payload)
    if (policy.optInt("format") != 1) throw Rejected("Unsupported content policy format")
    val publishedAt = publishedAt(policy)
    // ISO-8601 UTC timestamps in this fixed form order correctly as strings.
    if (heldPublishedAt != null && (publishedAt == null || publishedAt < heldPublishedAt)) throw Rejected("Content policy is older than the one in force")
    return policy
  }

  fun publishedAt(policy: JSONObject): String? =
    (policy.opt("publishedAt") as? String)?.also { if (!isoInstant.matches(it)) throw Rejected("Invalid publication time") }

  /** Standard base64. Local so verification does not depend on java.util.Base64 (API 26+). */
  fun decodeBase64(value: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val clean = value.trim().trimEnd('=')
    require(clean.isNotEmpty() && clean.length % 4 != 1 && clean.all { it in alphabet }) { "Invalid base64" }
    val out = java.io.ByteArrayOutputStream(clean.length * 3 / 4)
    var buffer = 0; var bits = 0
    for (char in clean) {
      buffer = (buffer shl 6) or alphabet.indexOf(char); bits += 6
      if (bits >= 8) { bits -= 8; out.write((buffer shr bits) and 0xff) }
    }
    return out.toByteArray()
  }
}
