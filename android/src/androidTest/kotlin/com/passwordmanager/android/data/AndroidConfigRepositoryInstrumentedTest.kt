package com.passwordmanager.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises [AndroidConfigRepository] against the real EncryptedSharedPreferences /
 * Android Keystore stack on-device, which JVM unit tests cannot run. Verifies that
 * sensitive config (biometric blobs, SFTP host) persists across instances and is
 * actually encrypted at rest.
 */
@RunWith(AndroidJUnit4::class)
class AndroidConfigRepositoryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefsName = "password_manager_prefs"

    @Before
    fun clearPrefs() {
        context.deleteSharedPreferences(prefsName)
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences(prefsName)
    }

    @Test
    fun biometricPassword_persistsAcrossInstances() {
        val blob = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        AndroidConfigRepository(context).setBiometricEncryptedPassword("alice", blob)

        // A new instance forces a read from disk rather than an in-memory cache hit.
        val reopened = AndroidConfigRepository(context)
        assertArrayEquals(blob, reopened.getBiometricEncryptedPassword("alice"))
    }

    @Test
    fun clearBiometricData_removesBlobAndFlag() {
        val repo = AndroidConfigRepository(context)
        repo.setBiometricEncryptedPassword("bob", byteArrayOf(9, 9, 9))
        repo.setBiometricEnabled("bob", true)
        repo.clearBiometricData("bob")

        val reopened = AndroidConfigRepository(context)
        assertNull(reopened.getBiometricEncryptedPassword("bob"))
        assertFalse(reopened.isBiometricEnabled("bob"))
    }

    @Test
    fun sftpHost_isEncryptedAtRest_butReadsBackCorrectly() {
        val marker = "secret-host-UNIQUE-MARKER-12345.example.com"
        AndroidConfigRepository(context).setSftpHost(marker)

        // SharedPreferences.apply() flushes to disk on a background thread, so the
        // file may not exist yet immediately after the write. Poll until it is
        // materialized (or time out) before inspecting raw bytes, to avoid a race.
        val prefsFile = File(context.filesDir.parentFile, "shared_prefs/$prefsName.xml")
        val deadline = System.currentTimeMillis() + 5_000
        while ((!prefsFile.exists() || prefsFile.length() == 0L) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue(
            "prefs file should be flushed to disk within the timeout",
            prefsFile.exists() && prefsFile.length() > 0L
        )
        assertFalse(
            "plaintext host must not appear on disk (values are AES-256-GCM encrypted)",
            prefsFile.readText().contains(marker)
        )
        assertEquals(marker, AndroidConfigRepository(context).getSftpHost())
    }
}
