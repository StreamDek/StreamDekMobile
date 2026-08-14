package net.streamdek.mobile.debrid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

private const val BASE = "https://api.real-debrid.com/oauth/v2"

/**
 * Real-Debrid's grant type, sent verbatim. It is a URL by design, not a typo.
 */
private const val DEVICE_GRANT = "http://oauth.net/grant_type/device/1.0"

/**
 * Signing in to Real-Debrid by approving a short code.
 *
 * Real-Debrid publishes a client id for open-source applications, which is what this uses — the
 * flow issues each installation its *own* client id and secret at the end of the exchange, so the
 * published one is only ever a way in, never a shared credential. Nothing here needs a secret
 * registered to StreamDek.
 *
 * Unlike Premiumize, what comes back is not a lasting key: the access token expires, and the
 * credentials issued alongside it are what buy a new one. [refresh] is how a device that has been
 * signed in for a week still plays something, and everything it needs is stored beside the token.
 */
internal object RealDebridDeviceAuth {
  /** Real-Debrid's published id for open-source clients. Not a secret and not StreamDek-specific. */
  const val OPEN_SOURCE_CLIENT_ID = "X245A4XAIBGVM"

  data class Started(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
  )

  /** What a completed sign-in yields. All of it is stored: the token alone cannot be renewed. */
  data class Credentials(
    val accessToken: String,
    val refreshToken: String,
    val clientId: String,
    val clientSecret: String,
  )

  sealed interface Poll {
    data class Authorized(val credentials: Credentials) : Poll
    data object Pending : Poll
    data class Failed(val message: String) : Poll
  }

  /** GET /device/code — the code pair to put on screen. */
  suspend fun start(): Started {
    val json = get("/device/code", mapOf("client_id" to OPEN_SOURCE_CLIENT_ID, "new_credentials" to "yes"))
    val deviceCode = json.optString("device_code")
    val userCode = json.optString("user_code")
    if (deviceCode.isBlank() || userCode.isBlank()) {
      throw IllegalStateException("Real-Debrid did not return a sign-in code. Try again in a moment.")
    }
    return Started(
      deviceCode = deviceCode,
      userCode = userCode,
      verificationUrl = json.optString("verification_url").ifBlank { "https://real-debrid.com/device" },
      intervalSeconds = json.optInt("interval", 5).coerceIn(1, 60),
      expiresInSeconds = json.optInt("expires_in", 1800).coerceIn(30, 3600),
    )
  }

  /**
   * GET /device/credentials, then the token exchange once it answers.
   *
   * Until the viewer approves the code this route simply has nothing to give, which arrives as an
   * error rather than an empty success — so a missing client_id here means "keep waiting", not
   * "something went wrong".
   */
  suspend fun poll(deviceCode: String): Poll {
    val json = runCatching {
      get("/device/credentials", mapOf("client_id" to OPEN_SOURCE_CLIENT_ID, "code" to deviceCode))
    }.getOrElse { return Poll.Pending }

    val clientId = json.optString("client_id")
    val clientSecret = json.optString("client_secret")
    if (clientId.isBlank() || clientSecret.isBlank()) return Poll.Pending

    return runCatching {
      val token = exchange(clientId, clientSecret, deviceCode, DEVICE_GRANT)
      Poll.Authorized(
        Credentials(
          accessToken = token.first,
          refreshToken = token.second,
          clientId = clientId,
          clientSecret = clientSecret,
        ),
      )
    }.getOrElse { Poll.Failed(it.message ?: "Real-Debrid refused the sign-in.") }
  }

  /**
   * Trades the refresh token for a fresh access token.
   *
   * The same endpoint and grant as the first exchange, with the refresh token in place of the
   * device code — Real-Debrid does not use a separate refresh grant.
   */
  suspend fun refresh(clientId: String, clientSecret: String, refreshToken: String): Credentials {
    val token = exchange(clientId, clientSecret, refreshToken, DEVICE_GRANT)
    return Credentials(
      accessToken = token.first,
      // A response that omits a new refresh token leaves the existing one in force.
      refreshToken = token.second.ifBlank { refreshToken },
      clientId = clientId,
      clientSecret = clientSecret,
    )
  }

  // ── Internals ───────────────────────────────────────────────────────────────────────────────

  /** POST /token → access token and refresh token. */
  private suspend fun exchange(
    clientId: String,
    clientSecret: String,
    code: String,
    grantType: String,
  ): Pair<String, String> = withContext(Dispatchers.IO) {
    val request = Request.Builder()
      .url("$BASE/token")
      .post(
        DebridHttp.form(
          "client_id" to clientId,
          "client_secret" to clientSecret,
          "code" to code,
          "grant_type" to grantType,
        ),
      )
      .header("Accept", "application/json")
      .build()
    val body = DebridHttp.client.newCall(request).execute().use { it.body?.string().orEmpty() }
    val json = DebridHttp.parseJsonObject(body)
    val accessToken = json.optString("access_token")
    if (accessToken.isBlank()) {
      throw IllegalStateException(
        json.optString("error_description").ifBlank { json.optString("error") }
          .ifBlank { "Real-Debrid did not return an access token." },
      )
    }
    accessToken to json.optString("refresh_token")
  }

  private suspend fun get(path: String, query: Map<String, String>): JSONObject =
    DebridHttp.json(Request.Builder().url(DebridHttp.url(BASE, path, query)).get().build())
}
