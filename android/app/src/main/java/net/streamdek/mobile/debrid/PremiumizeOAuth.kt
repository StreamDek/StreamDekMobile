package net.streamdek.mobile.debrid

import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val AUTHORIZE_ENDPOINT = "https://www.premiumize.me/authorize"
private const val TOKEN_ENDPOINT = "https://www.premiumize.me/token"

/**
 * Signing in to Premiumize with Authorization Code + PKCE.
 *
 * Premiumize's recommended flow for anything that can open a browser, which on a phone is the
 * better experience than reading a code off the screen: the viewer taps once, approves in a
 * browser tab, and is handed back. The television uses Premiumize's device-code flow
 * instead, which is what Premiumize recommends for input-constrained devices.
 *
 * No client secret is involved, by design rather than by omission. PKCE replaces the static secret
 * with a fresh proof generated per attempt: the verifier is held in memory here and only its
 * SHA-256 hash travels on the first redirect, so intercepting that redirect yields nothing usable
 * without the verifier that never left the device. This matters because a phone is a public OAuth
 * client — a secret compiled into one can be read straight back out of the package.
 */
internal object PremiumizeOAuth {
  /**
   * Where Premiumize sends the viewer back to.
   *
   * Must match the redirect registered for the client id, and be claimed by an intent filter in
   * the manifest, or the browser has nowhere to hand control back to.
   */
  const val REDIRECT_URI = "streamdek://premiumize/callback"

  /**
   * One attempt's secrets.
   *
   * [verifier] never leaves the device; [challenge] is the only part that travels. [state] is
   * carried through the redirect and compared on the way back, so a response the app did not ask
   * for cannot be palmed off on it.
   */
  data class Challenge(val verifier: String, val challenge: String, val state: String)

  /** Whether this build can offer the flow at all. Blank id means the option stays hidden. */
  fun isConfigured(clientId: String): Boolean = clientId.isNotBlank()

  /** A fresh verifier, its S256 challenge, and a state value. */
  fun newChallenge(random: SecureRandom = SecureRandom()): Challenge {
    val verifier = randomUrlSafe(64, random)
    return Challenge(
      verifier = verifier,
      challenge = challengeFor(verifier),
      state = randomUrlSafe(24, random),
    )
  }

  /** The S256 challenge for a verifier: base64url of its SHA-256, unpadded. */
  fun challengeFor(verifier: String): String =
    base64UrlNoPadding(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

  /** The URL to open in a browser tab. */
  fun authorizeUrl(clientId: String, challenge: Challenge): String =
    DebridHttp.url(
      AUTHORIZE_ENDPOINT,
      "",
      mapOf(
        "response_type" to "code",
        "client_id" to clientId,
        "redirect_uri" to REDIRECT_URI,
        "state" to challenge.state,
        "code_challenge" to challenge.challenge,
        "code_challenge_method" to "S256",
      ),
    ).toString()

  /**
   * Reads the authorization code out of the redirect the browser came back with.
   *
   * Returns null when the state does not match what was sent — a redirect the app did not
   * initiate, which is exactly what state exists to catch — or when Premiumize reported an error
   * instead of a code.
   */
  fun codeFromRedirect(redirect: String, expectedState: String): String? {
    val url = runCatching { java.net.URI(redirect) }.getOrNull() ?: return null
    val params = (url.query ?: url.fragment).orEmpty().split('&').mapNotNull { pair ->
      val separator = pair.indexOf('=')
      if (separator <= 0) return@mapNotNull null
      decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
    }.toMap()
    if (params["state"] != expectedState) return null
    return params["code"]?.takeIf { it.isNotBlank() }
  }

  /**
   * Exchanges the code for a token.
   * POST /token  grant_type=authorization_code&code=…&client_id=…&redirect_uri=…&code_verifier=…
   *
   * The verifier is sent here and only here, which is the whole point: whoever intercepted the
   * redirect does not have it, so the code alone buys them nothing.
   */
  suspend fun exchange(clientId: String, code: String, verifier: String): String =
    withContext(Dispatchers.IO) {
      val request = Request.Builder()
        .url(TOKEN_ENDPOINT)
        .post(
          DebridHttp.form(
            "grant_type" to "authorization_code",
            "code" to code,
            "client_id" to clientId,
            "redirect_uri" to REDIRECT_URI,
            "code_verifier" to verifier,
          ),
        )
        .header("Accept", "application/json")
        .build()
      val body = DebridHttp.client.newCall(request).execute().use { it.body?.string().orEmpty() }
      val json = DebridHttp.parseJsonObject(body)
      json.optString("access_token").takeIf { it.isNotBlank() }
        ?: throw IllegalStateException(
          sequenceOf("error_description", "message", "error")
            .mapNotNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }
            .firstOrNull() ?: "Premiumize did not return an access token.",
        )
    }

  // ── Internals ───────────────────────────────────────────────────────────────────────────────

  /** The unreserved set RFC 7636 allows in a verifier. */
  private const val URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

  private fun randomUrlSafe(length: Int, random: SecureRandom): String =
    buildString(length) { repeat(length) { append(URL_SAFE[random.nextInt(URL_SAFE.length)]) } }

  /**
   * Base64url without padding, written out rather than taken from a library.
   *
   * `java.util.Base64` needs API 26 and this app supports 24, and `android.util.Base64` is a stub
   * outside a device, which would leave the one piece of this worth testing untestable.
   */
  private fun base64UrlNoPadding(bytes: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val out = StringBuilder((bytes.size + 2) / 3 * 4)
    var index = 0
    while (index + 2 < bytes.size) {
      val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
        ((bytes[index + 1].toInt() and 0xFF) shl 8) or
        (bytes[index + 2].toInt() and 0xFF)
      out.append(alphabet[(chunk shr 18) and 0x3F])
      out.append(alphabet[(chunk shr 12) and 0x3F])
      out.append(alphabet[(chunk shr 6) and 0x3F])
      out.append(alphabet[chunk and 0x3F])
      index += 3
    }
    when (bytes.size - index) {
      1 -> {
        val chunk = (bytes[index].toInt() and 0xFF) shl 16
        out.append(alphabet[(chunk shr 18) and 0x3F])
        out.append(alphabet[(chunk shr 12) and 0x3F])
      }
      2 -> {
        val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or ((bytes[index + 1].toInt() and 0xFF) shl 8)
        out.append(alphabet[(chunk shr 18) and 0x3F])
        out.append(alphabet[(chunk shr 12) and 0x3F])
        out.append(alphabet[(chunk shr 6) and 0x3F])
      }
    }
    return out.toString()
  }

  private fun decode(value: String): String =
    runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
