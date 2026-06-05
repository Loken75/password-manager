package com.passwordmanager.android.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Validates that the real Android Keystore performs AES-256-GCM encrypt/decrypt
 * round-trips on-device — the hardware-backed crypto path that JVM unit tests
 * (and Robolectric shadows) cannot exercise.
 *
 * Uses a dedicated test key WITHOUT user-authentication so it runs unattended.
 * The biometric (user-auth-required) key path is covered by manual QA, not here,
 * because it requires satisfying a real fingerprint prompt.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCryptoInstrumentedTest {

    private val keyAlias = "pm_instrumented_test_key"

    @After
    fun cleanup() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(keyAlias)) ks.deleteEntry(keyAlias)
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    @Test
    fun keystore_aesGcm_roundTrips_acrossFreshHandle() {
        generateKey()
        val plaintext = "correct horse battery staple".toByteArray()

        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, loadKey())
        val iv = encryptCipher.iv
        val ciphertext = encryptCipher.doFinal(plaintext)
        assertFalse("ciphertext must differ from plaintext", ciphertext.contentEquals(plaintext))

        // A fresh handle to the same alias must decrypt: proves the key persisted
        // in the Keystore rather than living only in this process's memory.
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, loadKey(), GCMParameterSpec(128, iv))
        assertArrayEquals(plaintext, decryptCipher.doFinal(ciphertext))
    }

    @Test
    fun keystore_wrongIv_failsAuthentication() {
        generateKey()
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, loadKey())
        val ciphertext = encryptCipher.doFinal("secret".toByteArray())

        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, loadKey(), GCMParameterSpec(128, ByteArray(12)))
        assertThrows(AEADBadTagException::class.java) { decryptCipher.doFinal(ciphertext) }
    }

    private fun loadKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }
}
