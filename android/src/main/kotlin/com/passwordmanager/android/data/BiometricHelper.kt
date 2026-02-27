package com.passwordmanager.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

open class BiometricHelper(private val context: Context?) {

    private val keyStore by lazy { KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) } }

    open fun canAuthenticate(): Boolean {
        val ctx = context ?: return false
        val biometricManager = BiometricManager.from(ctx)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    open fun generateKey(username: String) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias(username),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    open fun deleteKey(username: String) {
        val alias = keyAlias(username)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    open fun getEncryptCipher(username: String): Cipher? {
        return try {
            val key = keyStore.getKey(keyAlias(username), null) as SecretKey
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteKey(username)
            null
        }
    }

    open fun getDecryptCipher(username: String, iv: ByteArray): Cipher? {
        return try {
            val key = keyStore.getKey(keyAlias(username), null) as SecretKey
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteKey(username)
            null
        }
    }

    open fun showBiometricPrompt(
        activity: FragmentActivity,
        cryptoObject: BiometricPrompt.CryptoObject,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onError: (Int, String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess(result.cryptoObject)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                // Individual attempt failed, prompt stays open — no action needed
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(promptInfo, cryptoObject)
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        private fun keyAlias(username: String) = "biometric_key_$username"

        fun encryptPassword(cipher: Cipher, password: CharArray): Pair<ByteArray, ByteArray> {
            val bytes = String(password).toByteArray(Charsets.UTF_8)
            try {
                val encrypted = cipher.doFinal(bytes)
                val iv = cipher.iv
                return Pair(encrypted, iv)
            } finally {
                bytes.fill(0)
            }
        }

        fun decryptPassword(cipher: Cipher, encryptedData: ByteArray): CharArray {
            val bytes = cipher.doFinal(encryptedData)
            try {
                return String(bytes, Charsets.UTF_8).toCharArray()
            } finally {
                bytes.fill(0)
            }
        }
    }
}
