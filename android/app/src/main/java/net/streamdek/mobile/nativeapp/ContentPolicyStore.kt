package net.streamdek.mobile.nativeapp

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.PublicKey
import org.json.JSONObject

/**
 * App-private and excluded from Android backups; never accepted from profile restore data.
 *
 * Stores the whole signed response, so a restored policy is verified again rather than trusted.
 * See [ContentPolicyEnvelope] for what a build with trusted keys accepts.
 */
internal class ContentPolicyStore(
  context: Context,
  fileName: String = "content-protection.json",
  private val trustedKeys: Map<String, PublicKey> = ContentPolicyEnvelope.trustedKeys(net.streamdek.mobile.BuildConfig.CONTENT_POLICY_PUBLIC_KEYS),
) {
  private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
  @Volatile private var heldPublishedAt: String? = null
  private data class Policy(val enabled: Boolean, val terms: List<String>, val rules: List<ContentSafetyRule>, val version: String?, val publishedAt: String?)
  private fun validate(json: JSONObject): Policy {
    require(json.opt("blockAdult") is Boolean)
    val terms = json.optJSONArray("terms")
    require(!json.has("terms") || terms != null)
    require((terms?.length() ?: 0) <= 500)
    val values = (0 until (terms?.length() ?: 0)).map { index ->
      val term = terms!!.get(index) as? String ?: error("Invalid policy term")
      require(term.length <= 120); term
    }
    require(!json.has("rules") || json.optJSONArray("rules") != null)
    val rules = ContentSafetyRule.parse(json.optJSONArray("rules"))
    val version = if (json.has("version")) json.getString("version").also { require(it.matches(Regex("[a-f0-9]{64}"))) } else null
    return Policy(json.getBoolean("blockAdult"), values, rules, version, ContentPolicyEnvelope.publishedAt(json))
  }
  private fun apply(policy: Policy) {
    AdultContentFilter.applyPolicy(policy.enabled, policy.terms, policy.rules, policy.version)
  }
  @Synchronized fun restore() {
    runCatching {
      val bytes = file.readFully(); require(bytes.size <= 256 * 1024)
      val stored = JSONObject(String(bytes, Charsets.UTF_8))
      // Files written before signing hold only the legacy fields; they came over the same channel
      // the app already trusted, so they still restore until the first signed refresh replaces them.
      val policy = validate(if (stored.has("signed")) ContentPolicyEnvelope.open(stored, trustedKeys) else stored)
      heldPublishedAt = policy.publishedAt
      apply(policy)
    }.onFailure { AdultContentFilter.applyPolicy(null, null) }
  }
  @Synchronized fun accept(json: JSONObject) {
    val policy = validate(ContentPolicyEnvelope.open(json, trustedKeys, heldPublishedAt))
    val bytes = json.toString().toByteArray(Charsets.UTF_8); require(bytes.size <= 256 * 1024)
    var stream: java.io.FileOutputStream? = null
    try {
      stream = file.startWrite(); stream.write(bytes); file.finishWrite(stream)
    } catch (failure: Throwable) {
      stream?.let { file.failWrite(it) }; throw failure
    }
    heldPublishedAt = policy.publishedAt ?: heldPublishedAt
    apply(policy)
  }
}
