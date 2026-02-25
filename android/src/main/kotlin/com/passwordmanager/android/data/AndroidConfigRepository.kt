package com.passwordmanager.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.passwordmanager.config.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android configuration backed by EncryptedSharedPreferences.
 * Replaces desktop's ConfigManager + ConfigEncryptor.
 */
class AndroidConfigRepository(context: Context) {

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
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    // --- Theme ---

    fun getThemeMode(): ThemeMode =
        ThemeMode.fromValue(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.value) ?: ThemeMode.SYSTEM.value)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.value).apply()
        _themeModeFlow.value = mode
    }

    // --- Language ---

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"

    fun setLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    // --- Auto-lock ---

    fun getAutoLockMinutes(): Int = prefs.getInt(KEY_AUTO_LOCK, 15)

    fun setAutoLockMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK, minutes.coerceIn(1, 60)).apply()
    }

    // --- Clipboard clear ---

    fun getClipboardClearSeconds(): Int = prefs.getInt(KEY_CLIPBOARD_CLEAR, 30)

    fun setClipboardClearSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CLIPBOARD_CLEAR, seconds.coerceIn(5, 120)).apply()
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_AUTO_LOCK = "auto_lock_minutes"
        private const val KEY_CLIPBOARD_CLEAR = "clipboard_clear_seconds"
    }
}
