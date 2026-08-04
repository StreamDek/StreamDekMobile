package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class HandoffEncryptionTest {
  @Test
  fun `handoff envelope can only be opened with target private key`() {
    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val decoder = Base64.getUrlDecoder()
    val original = """{"mediaId":"603","url":"https://private.example/movie.m3u8"}"""

    val envelope = encryptPlaybackHandoff(original, encoder.encodeToString(keyPair.public.encoded))
    assertEquals(1, envelope.getInt("version"))
    assertEquals("RSA-OAEP-256-MGF1-SHA1+A256GCM", envelope.getString("algorithm"))

    val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    rsa.init(
      Cipher.DECRYPT_MODE,
      keyPair.private,
      OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT),
    )
    val aesKey = rsa.doFinal(decoder.decode(envelope.getString("encryptedKey")))
    val aes = Cipher.getInstance("AES/GCM/NoPadding")
    aes.init(
      Cipher.DECRYPT_MODE,
      SecretKeySpec(aesKey, "AES"),
      GCMParameterSpec(128, decoder.decode(envelope.getString("iv"))),
    )
    aes.updateAAD("streamdek-handoff-v1".toByteArray(Charsets.UTF_8))
    val decrypted = aes.doFinal(decoder.decode(envelope.getString("ciphertext"))).toString(Charsets.UTF_8)

    assertEquals(original, decrypted)
    check(!envelope.toString().contains("private.example"))
  }
}