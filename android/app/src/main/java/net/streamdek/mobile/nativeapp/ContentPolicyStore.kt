package net.streamdek.mobile.nativeapp

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONObject

/** App-private and excluded from Android backups; never accepted from profile restore data. */
internal class ContentPolicyStore(context: Context, fileName: String = "content-protection.json") {
  private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
  private data class Policy(val enabled: Boolean, val terms: List<String>, val rules: List<ContentSafetyRule>, val version: String?)
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
    return Policy(json.getBoolean("blockAdult"), values, rules, version)
  }
  private fun apply(policy: Policy) {
    AdultContentFilter.applyPolicy(policy.enabled, policy.terms, policy.rules, policy.version)
  }
  @Synchronized fun restore() {
    runCatching {
      val bytes = file.readFully(); require(bytes.size <= 256 * 1024)
      apply(validate(JSONObject(String(bytes, Charsets.UTF_8))))
    }.onFailure { AdultContentFilter.applyPolicy(null, null) }
  }
  @Synchronized fun accept(json: JSONObject) {
    val policy = validate(json)
    val bytes = json.toString().toByteArray(Charsets.UTF_8); require(bytes.size <= 256 * 1024)
    var stream: java.io.FileOutputStream? = null
    try {
      stream = file.startWrite(); stream.write(bytes); file.finishWrite(stream)
    } catch (failure: Throwable) {
      stream?.let { file.failWrite(it) }; throw failure
    }
    apply(policy)
  }
}
