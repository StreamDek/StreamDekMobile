package net.streamdek.mobile.nativeapp

import org.json.JSONObject
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

// Android Keystore implementations on Fire OS and older Android TV releases use
// SHA-1 for OAEP's MGF1 digest even when the OAEP message digest is SHA-256.
// Name that interoperability choice explicitly so receivers never have to guess.
private const val HANDOFF_ALGORITHM = "RSA-OAEP-256-MGF1-SHA1+A256GCM"
private val HANDOFF_AAD = "streamdek-handoff-v1".toByteArray(Charsets.UTF_8)

internal fun encryptPlaybackHandoff(payloadJson: String, tvPublicKeyBase64: String): JSONObject {
  val decoder = Base64.getUrlDecoder()
  val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoder.decode(tvPublicKeyBase64)))
  val keyGenerator = KeyGenerator.getInstance("AES").apply { init(256) }
  val aesKey = keyGenerator.generateKey()
  val iv = ByteArray(12).also(SecureRandom()::nextBytes)
  val aes = Cipher.getInstance("AES/GCM/NoPadding")
  aes.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
  aes.updateAAD(HANDOFF_AAD)
  val ciphertext = aes.doFinal(payloadJson.toByteArray(Charsets.UTF_8))
  val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
  rsa.init(
    Cipher.ENCRYPT_MODE,
    publicKey,
    OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT),
  )
  val encryptedKey = rsa.doFinal(aesKey.encoded)
  val encoder = Base64.getUrlEncoder().withoutPadding()
  return JSONObject()
    .put("version", 1)
    .put("algorithm", HANDOFF_ALGORITHM)
    .put("encryptedKey", encoder.encodeToString(encryptedKey))
    .put("iv", encoder.encodeToString(iv))
    .put("ciphertext", encoder.encodeToString(ciphertext))
}