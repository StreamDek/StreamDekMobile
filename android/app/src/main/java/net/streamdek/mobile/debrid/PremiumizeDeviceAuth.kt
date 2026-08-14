package net.streamdek.mobile.debrid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val PREMIUMIZE_TOKEN_ENDPOINT = "https://www.premiumize.me/token"

/** Premiumize's documented browser-pairing flow for clients that cannot keep a secret. */
internal object PremiumizeDeviceAuth {
  data class Started(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
  )

  sealed interface Poll {
    data class Authorized(val accessToken: String) : Poll
    data object Pending : Poll
    data object SlowDown : Poll
    data class Failed(val message: String) : Poll
  }

  suspend fun start(clientId: String): Started = withContext(Dispatchers.IO) {
    val json = post(
      "response_type" to "device_code",
      "client_id" to clientId,
    )
    val deviceCode = json.optString("device_code")
    val userCode = json.optString("user_code")
    if (deviceCode.isBlank() || userCode.isBlank()) {
      throw IllegalStateException(errorMessage(json, "Premiumize did not return a sign-in code."))
    }
    Started(
      deviceCode = deviceCode,
      userCode = userCode,
      verificationUrl = json.optString("verification_uri").ifBlank { "https://www.premiumize.me/device" },
      intervalSeconds = json.optInt("interval", 5).coerceIn(1, 60),
      expiresInSeconds = json.optInt("expires_in", 600).coerceIn(30, 3600),
    )
  }

  suspend fun poll(clientId: String, deviceCode: String): Poll = withContext(Dispatchers.IO) {
    val json = post(
      "grant_type" to "device_code",
      "code" to deviceCode,
      "client_id" to clientId,
    )
    val accessToken = json.optString("access_token")
    if (accessToken.isNotBlank()) return@withContext Poll.Authorized(accessToken)

    when (json.optString("error")) {
      "authorization_pending" -> Poll.Pending
      "slow_down" -> Poll.SlowDown
      "access_denied" -> Poll.Failed("Premiumize sign-in was declined.")
      "invalid_grant" -> Poll.Failed("That Premiumize code expired or is no longer valid.")
      else -> Poll.Failed(errorMessage(json, "Premiumize sign-in failed."))
    }
  }

  private fun post(vararg fields: Pair<String, String>) =
    Request.Builder()
      .url(PREMIUMIZE_TOKEN_ENDPOINT)
      .post(DebridHttp.form(*fields))
      .header("Accept", "application/json")
      .build()
      .let { request ->
        DebridHttp.client.newCall(request).execute().use { response ->
          DebridHttp.parseJsonObject(response.body?.string().orEmpty())
        }
      }

  private fun errorMessage(json: org.json.JSONObject, fallback: String): String =
    sequenceOf("error_description", "message", "error")
      .mapNotNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }
      .firstOrNull() ?: fallback
}
