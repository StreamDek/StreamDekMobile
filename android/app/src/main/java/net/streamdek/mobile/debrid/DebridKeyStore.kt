package net.streamdek.mobile.debrid

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where this device keeps the premium-service API keys it calls providers with.
 *
 * The keys arrive from StreamDek's server, which holds the encrypted copy so an account can sync
 * to a TV, and are kept here so the device can reach Real-Debrid, TorBox and the rest itself
 * rather than asking a server to do it on its behalf.
 *
 * Encrypted with a key that lives in the Android Keystore and never leaves it: what is written to
 * preferences is ciphertext, and the key that opens it is not in the file, not in the APK, and not
 * extractable from the device. Plain SharedPreferences would have left a working credential
 * readable to anything that could reach the app's data directory, and rooted devices and backup
 * extractions both can.
 *
 * `androidx.security:security-crypto` does exactly this and was the obvious choice, but it is no
 * longer maintained — its stable release predates the Keystore APIs used here, and taking on an
 * unmaintained security dependency for sixty lines of AES/GCM is the worse trade.
 */
internal object DebridKeyStore {
  private const val PREFERENCES = "streamdek_debrid_keys"
  private const val KEY_ALIAS = "streamdek_debrid_key_v1"
  private const val KEYSTORE = "AndroidKeyStore"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_BITS = 128
  private const val IV_BYTES = 12
  private const val STORED_ACCOUNTS = "accounts"

  /** One provider's credential, in the order the account holder put them in. */
  data class StoredKey(
    val provider: String,
    val apiKey: String,
    val priority: Int,
    val enabled: Boolean,
    /** Who the provider says this key belongs to, for the settings card to show. */
    val username: String? = null,
  )

  /**
   * Replaces everything held for this device.
   *
   * A replace rather than a merge on purpose: a provider the account holder disconnected has to
   * disappear from the device too, and merging would have left it working locally long after it
   * stopped existing anywhere else.
   */
  fun save(context: Context, keys: List<StoredKey>) {
    val serialized = keys.joinToString("\n") { key ->
      listOf(
        key.provider,
        key.apiKey,
        key.priority.toString(),
        if (key.enabled) "1" else "0",
        key.username.orEmpty(),
      ).joinToString("\t")
    }
    val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    if (serialized.isEmpty()) {
      prefs.edit().remove(STORED_ACCOUNTS).apply()
      return
    }
    val encrypted = runCatching { encrypt(serialized) }.getOrNull() ?: return
    prefs.edit().putString(STORED_ACCOUNTS, encrypted).apply()
  }

  /** What this device can currently call providers with, highest priority first. */
  fun load(context: Context): List<StoredKey> {
    val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
      .getString(STORED_ACCOUNTS, null)
      ?.takeIf { it.isNotBlank() }
      ?: return emptyList()
    // A key that will not decrypt is a key that cannot be used. That happens legitimately — the
    // Keystore entry is dropped when the device's lock screen credentials are removed — and the
    // answer is to fetch them again, not to crash the caller.
    val plain = runCatching { decrypt(stored) }.getOrNull() ?: return emptyList()
    return plain.lineSequence().mapNotNull { line ->
      val parts = line.split('\t')
      // Four fields is the original layout, written before the username was carried; those
      // records stay readable rather than being discarded as corrupt.
      if (parts.size < 4) return@mapNotNull null
      val apiKey = parts[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null
      StoredKey(
        provider = parts[0],
        apiKey = apiKey,
        priority = parts[2].toIntOrNull() ?: 0,
        enabled = parts[3] == "1",
        username = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
      )
    }.sortedBy { it.priority }.toList()
  }

  /** Drops every stored credential — on sign-out, or when the account has no providers left. */
  fun clear(context: Context) {
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
  }

  // ── Encryption ──────────────────────────────────────────────────────────────────────────────

  private fun encrypt(plain: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    // The IV is generated per encryption and stored beside the payload; it is not a secret, and
    // reusing one with the same key is what actually breaks GCM.
    val combined = cipher.iv + encrypted
    return Base64.encodeToString(combined, Base64.NO_WRAP)
  }

  private fun decrypt(stored: String): String {
    val combined = Base64.decode(stored, Base64.NO_WRAP)
    if (combined.size <= IV_BYTES) throw IllegalStateException("Stored debrid keys are truncated.")
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(
      Cipher.DECRYPT_MODE,
      secretKey(),
      GCMParameterSpec(GCM_TAG_BITS, combined, 0, IV_BYTES),
    )
    return String(cipher.doFinal(combined, IV_BYTES, combined.size - IV_BYTES), Charsets.UTF_8)
  }

  @Synchronized
  private fun secretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        // Deliberately not requiring user authentication: trailers, playback and cache checks all
        // happen without anyone present to unlock anything, and a key that needs a fingerprint
        // would mean a lock-screen prompt in the middle of pressing play.
        .setUserAuthenticationRequired(false)
        .build(),
    )
    return generator.generateKey()
  }
}
