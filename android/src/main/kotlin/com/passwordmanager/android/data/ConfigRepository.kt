package com.passwordmanager.android.data

import com.passwordmanager.config.StorageMode
import com.passwordmanager.config.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface ConfigRepository {
    val themeModeFlow: StateFlow<ThemeMode>
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun getLanguage(): String
    fun setLanguage(language: String)
    fun getAutoLockMinutes(): Int
    fun setAutoLockMinutes(minutes: Int)
    fun getClipboardClearSeconds(): Int
    fun setClipboardClearSeconds(seconds: Int)
    fun isFaviconsEnabled(): Boolean
    fun setFaviconsEnabled(enabled: Boolean)

    // SFTP sync
    fun getStorageMode(): StorageMode
    fun setStorageMode(mode: StorageMode)
    fun getSftpHost(): String
    fun setSftpHost(host: String)
    fun getSftpPort(): Int
    fun setSftpPort(port: Int)
    fun getSftpUser(): String
    fun setSftpUser(user: String)
    fun getSftpKeyPath(): String
    fun setSftpKeyPath(path: String)
    fun getSftpRemotePath(): String
    fun setSftpRemotePath(path: String)
    fun getSftpKeyId(): String
    fun setSftpKeyId(keyId: String)

    // Vault working folder (workspace). Null = default (internal storage).
    fun getVaultWorkspace(): String?
    fun setVaultWorkspace(spec: String?)

    // Biometric unlock
    fun isBiometricEnabled(username: String): Boolean
    fun setBiometricEnabled(username: String, enabled: Boolean)
    fun getBiometricEncryptedPassword(username: String): ByteArray?
    fun setBiometricEncryptedPassword(username: String, data: ByteArray?)
    fun getBiometricIv(username: String): ByteArray?
    fun setBiometricIv(username: String, iv: ByteArray?)
    fun clearBiometricData(username: String)
}
