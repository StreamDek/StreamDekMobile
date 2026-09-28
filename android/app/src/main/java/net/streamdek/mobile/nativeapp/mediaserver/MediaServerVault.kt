package net.streamdek.mobile.nativeapp.mediaserver

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What this device last knew about a profile's media servers, kept so the Plex destination and
 * rows are there the moment the app opens rather than after a round trip to StreamDek and plex.tv.
 *
 * Holds per-server access tokens, so it is encrypted exactly as the Debrid key store is: AES-GCM
 * under a key that lives in the Android Keystore and never leaves it. A snapshot that will not
 * decrypt (the Keystore entry is dropped with the lock-screen credential) is simply absent, and the
 * next refresh from StreamDek writes a new one.
 *
 * One snapshot per (account, profile): a profile switch must never show the previous profile's
 * servers, even for the second it takes to refresh.
 */
internal class MediaServerVault(private val context: Context, private val gson: Gson = Gson()) {

    /** The persisted form. Nullable throughout because Gson restores field by field. */
    data class Snapshot(
        val linked: Boolean? = null,
        val needsAttention: Boolean? = null,
        val accountName: String? = null,
        val accountThumb: String? = null,
        val servers: List<StoredServer>? = null,
        val savedAtMs: Long? = null,
    ) {
        override fun toString(): String = "Snapshot(linked=$linked, servers=${servers?.size ?: 0})"
    }

    data class StoredServer(
        val id: String? = null,
        val name: String? = null,
        val owned: Boolean? = null,
        val ownerName: String? = null,
        val enabled: Boolean? = null,
        val presence: Boolean? = null,
        val accessToken: String? = null,
        val connections: List<StoredConnection>? = null,
        val libraryChoices: Map<String, Boolean>? = null,
        val libraries: List<StoredLibrary>? = null,
        /** The connection that last answered, tried first next time. */
        val preferredUri: String? = null,
    ) {
        override fun toString(): String = "StoredServer(id=$id, name=$name)"
    }

    data class StoredConnection(
        val uri: String? = null,
        val local: Boolean? = null,
        val relay: Boolean? = null,
    )

    data class StoredLibrary(
        val key: String? = null,
        val title: String? = null,
        val kind: String? = null,
        val itemCount: Int? = null,
    )

    fun load(scope: String): Snapshot? {
        val stored = prefs().getString(keyFor(scope), null)?.takeIf { it.isNotBlank() } ?: return null
        val plain = runCatching { decrypt(stored) }.getOrNull() ?: return null
        return runCatching { gson.fromJson(plain, Snapshot::class.java) }.getOrNull()
    }

    fun save(scope: String, snapshot: Snapshot) {
        val encrypted = runCatching { encrypt(gson.toJson(snapshot)) }.getOrNull() ?: return
        prefs().edit().putString(keyFor(scope), encrypted).apply()
    }

    fun remove(scope: String) {
        prefs().edit().remove(keyFor(scope)).apply()
    }

    /** Everything, for sign-out. */
    fun clear() {
        prefs().edit().clear().apply()
    }

    private fun prefs() = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun keyFor(scope: String) = "snapshot:$scope"

    // ── Encryption, as DebridKeyStore ────────────────────────────────────────────────────────────

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val combined = Base64.decode(stored, Base64.NO_WRAP)
        if (combined.size <= IV_BYTES) throw IllegalStateException("Stored media server snapshot is truncated.")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, combined, 0, IV_BYTES))
        return String(cipher.doFinal(combined, IV_BYTES, combined.size - IV_BYTES), Charsets.UTF_8)
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Playback and artwork happen with nobody present to unlock anything.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "streamdek_media_servers"
        const val KEY_ALIAS = "streamdek_media_server_key_v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
