package com.passwordmanager.android.data

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
}
