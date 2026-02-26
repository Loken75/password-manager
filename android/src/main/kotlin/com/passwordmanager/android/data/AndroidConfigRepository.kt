package com.passwordmanager.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.passwordmanager.config.StorageMode
import com.passwordmanager.config.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android configuration backed by EncryptedSharedPreferences.
 * Replaces desktop's ConfigManager + ConfigEncryptor.
 */
class AndroidConfigRepository(context: Context) : ConfigRepository {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "password_manager_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    override val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    // --- Theme ---

    override fun getThemeMode(): ThemeMode =
        ThemeMode.fromValue(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.value) ?: ThemeMode.SYSTEM.value)

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.value).apply()
        _themeModeFlow.value = mode
    }

    // --- Language ---

    override fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"

    override fun setLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    // --- Auto-lock ---

    override fun getAutoLockMinutes(): Int = prefs.getInt(KEY_AUTO_LOCK, 15)

    override fun setAutoLockMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK, minutes.coerceIn(1, 60)).apply()
    }

    // --- Clipboard clear ---

    override fun getClipboardClearSeconds(): Int = prefs.getInt(KEY_CLIPBOARD_CLEAR, 30)

    override fun setClipboardClearSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CLIPBOARD_CLEAR, seconds.coerceIn(5, 120)).apply()
    }

    // --- SFTP sync ---

    override fun getStorageMode(): StorageMode =
        StorageMode.fromValue(prefs.getString(KEY_STORAGE_MODE, StorageMode.LOCAL.value) ?: StorageMode.LOCAL.value)

    override fun setStorageMode(mode: StorageMode) {
        prefs.edit().putString(KEY_STORAGE_MODE, mode.value).apply()
    }

    override fun getSftpHost(): String = prefs.getString(KEY_SFTP_HOST, "") ?: ""

    override fun setSftpHost(host: String) {
        prefs.edit().putString(KEY_SFTP_HOST, host).apply()
    }

    override fun getSftpPort(): Int = prefs.getInt(KEY_SFTP_PORT, 22)

    override fun setSftpPort(port: Int) {
        prefs.edit().putInt(KEY_SFTP_PORT, port.coerceIn(1, 65535)).apply()
    }

    override fun getSftpUser(): String = prefs.getString(KEY_SFTP_USER, "") ?: ""

    override fun setSftpUser(user: String) {
        prefs.edit().putString(KEY_SFTP_USER, user).apply()
    }

    override fun getSftpKeyPath(): String = prefs.getString(KEY_SFTP_KEY_PATH, "") ?: ""

    override fun setSftpKeyPath(path: String) {
        prefs.edit().putString(KEY_SFTP_KEY_PATH, path).apply()
    }

    override fun getSftpRemotePath(): String = prefs.getString(KEY_SFTP_REMOTE_PATH, "") ?: ""

    override fun setSftpRemotePath(path: String) {
        prefs.edit().putString(KEY_SFTP_REMOTE_PATH, path).apply()
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_AUTO_LOCK = "auto_lock_minutes"
        private const val KEY_CLIPBOARD_CLEAR = "clipboard_clear_seconds"
        private const val KEY_STORAGE_MODE = "storage_mode"
        private const val KEY_SFTP_HOST = "sftp_host"
        private const val KEY_SFTP_PORT = "sftp_port"
        private const val KEY_SFTP_USER = "sftp_user"
        private const val KEY_SFTP_KEY_PATH = "sftp_key_path"
        private const val KEY_SFTP_REMOTE_PATH = "sftp_remote_path"
    }
}
