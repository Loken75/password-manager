package com.passwordmanager.android.test

import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.config.StorageMode
import com.passwordmanager.config.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeConfigRepository : ConfigRepository {
    private val _themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private var language = "en"
    private var autoLockMinutes = 15
    private var clipboardClearSeconds = 30
    private var storageMode = StorageMode.LOCAL
    private var sftpHost = ""
    private var sftpPort = 22
    private var sftpUser = ""
    private var sftpKeyPath = ""
    private var sftpRemotePath = ""

    override fun getThemeMode(): ThemeMode = _themeModeFlow.value
    override fun setThemeMode(mode: ThemeMode) { _themeModeFlow.value = mode }
    override fun getLanguage(): String = language
    override fun setLanguage(language: String) { this.language = language }
    override fun getAutoLockMinutes(): Int = autoLockMinutes
    override fun setAutoLockMinutes(minutes: Int) { this.autoLockMinutes = minutes.coerceIn(1, 60) }
    override fun getClipboardClearSeconds(): Int = clipboardClearSeconds
    override fun setClipboardClearSeconds(seconds: Int) { this.clipboardClearSeconds = seconds.coerceIn(5, 120) }

    override fun getStorageMode(): StorageMode = storageMode
    override fun setStorageMode(mode: StorageMode) { this.storageMode = mode }
    override fun getSftpHost(): String = sftpHost
    override fun setSftpHost(host: String) { this.sftpHost = host }
    override fun getSftpPort(): Int = sftpPort
    override fun setSftpPort(port: Int) { this.sftpPort = port.coerceIn(1, 65535) }
    override fun getSftpUser(): String = sftpUser
    override fun setSftpUser(user: String) { this.sftpUser = user }
    override fun getSftpKeyPath(): String = sftpKeyPath
    override fun setSftpKeyPath(path: String) { this.sftpKeyPath = path }
    override fun getSftpRemotePath(): String = sftpRemotePath
    override fun setSftpRemotePath(path: String) { this.sftpRemotePath = path }

    // Biometric
    private val biometricEnabled = mutableMapOf<String, Boolean>()
    private val biometricPasswords = mutableMapOf<String, ByteArray>()
    private val biometricIvs = mutableMapOf<String, ByteArray>()

    override fun isBiometricEnabled(username: String): Boolean = biometricEnabled[username] ?: false
    override fun setBiometricEnabled(username: String, enabled: Boolean) { biometricEnabled[username] = enabled }
    override fun getBiometricEncryptedPassword(username: String): ByteArray? = biometricPasswords[username]
    override fun setBiometricEncryptedPassword(username: String, data: ByteArray?) {
        if (data != null) biometricPasswords[username] = data else biometricPasswords.remove(username)
    }
    override fun getBiometricIv(username: String): ByteArray? = biometricIvs[username]
    override fun setBiometricIv(username: String, iv: ByteArray?) {
        if (iv != null) biometricIvs[username] = iv else biometricIvs.remove(username)
    }
    override fun clearBiometricData(username: String) {
        biometricEnabled.remove(username)
        biometricPasswords.remove(username)
        biometricIvs.remove(username)
    }
}
