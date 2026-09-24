package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import java.net.URI
import java.net.IDN
import java.util.Locale

/** Downloaded rules have exact entity scopes; parent checks are performed before child exceptions. */
data class ContentSafetyRule(val id: String, val scope: String, val kind: String, val value: String, val status: String, val reason: String) {
  fun matches(entityScope: String, fields: List<String>): Boolean {
    if (scope != "*" && scope != entityScope) return false
    if (kind == "domain") return fields.any { raw -> runCatching {
      val host = URI(raw).host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return@runCatching false
      host == value || host.endsWith(".$value")
    }.getOrDefault(false) }
    val expected = skeleton(value)
    return fields.any { skeleton(it) == expected }
  }
  companion object {
    private val scopes = setOf("*", "repository", "plugin", "provider", "catalogue", "media", "channel", "source")
    private fun skeleton(value: String) = AdultSourceIdentity.normalize(value).replace('0', 'o').replace('1', 'i')
      .replace('3', 'e').replace('4', 'a').replace('5', 's').replace('7', 't').replace(" ", "")
    fun parse(array: JSONArray?): List<ContentSafetyRule> {
      if (array == null) return emptyList()
      require(array.length() <= 200)
      val ids = mutableSetOf<String>()
      return (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        fun string(key: String) = (row.get(key) as? String) ?: error("Invalid policy field")
        val id = string("id"); val scope = string("scope"); val kind = string("kind"); val status = string("status")
        var value = string("value").trim(); val reason = string("reason").trim()
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}")) && ids.add(id) && scope in scopes)
        require(kind in setOf("identity", "domain") && status in setOf("SAFE", "ADULT"))
        require(value.isNotBlank() && value.length <= 512 && reason.isNotBlank() && reason.length <= 240)
        if (kind == "domain") {
          require(value.none { it.isWhitespace() || it in "/:@?#*\\" })
          value = IDN.toASCII(value.trimEnd('.')).lowercase(Locale.ROOT)
          require(value.length <= 253 && value.contains('.') && value.split('.').all { it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) })
        }
        ContentSafetyRule(id, scope, kind, value, status, reason)
      }
    }
  }
}
