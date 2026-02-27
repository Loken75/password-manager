package com.passwordmanager.android.test

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.passwordmanager.android.data.BiometricHelper
import javax.crypto.Cipher

/**
 * Test double for BiometricHelper for JVM unit tests.
 *
 * All methods that touch Android framework APIs (KeyStore, BiometricManager, BiometricPrompt)
 * are overridden to avoid RuntimeException("Stub!") from android.jar stubs.
 * Passes null context since all methods are overridden and the lazy keyStore is never accessed.
 */
class FakeBiometricHelper : BiometricHelper(null) {

    var available: Boolean = false
    val generatedKeys = mutableSetOf<String>()
    val deletedKeys = mutableListOf<String>()

    override fun canAuthenticate(): Boolean = available

    override fun generateKey(username: String) {
        generatedKeys.add(username)
    }

    override fun deleteKey(username: String) {
        deletedKeys.add(username)
        generatedKeys.remove(username)
    }

    override fun getEncryptCipher(username: String): Cipher? = null

    override fun getDecryptCipher(username: String, iv: ByteArray): Cipher? = null

    override fun showBiometricPrompt(
        activity: FragmentActivity,
        cryptoObject: BiometricPrompt.CryptoObject,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onError: (Int, String) -> Unit
    ) {
        // No-op — biometric prompt UI requires a real device
    }
}
