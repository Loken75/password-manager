package com.passwordmanager.android.ui.settings

import androidx.lifecycle.ViewModel
import com.passwordmanager.android.data.AndroidConfigRepository
import com.passwordmanager.config.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val language: String = "en",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoLockMinutes: Int = 15,
    val clipboardClearSeconds: Int = 30
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var configRepo: AndroidConfigRepository? = null

    fun init(configRepo: AndroidConfigRepository) {
        this.configRepo = configRepo
        _uiState.value = SettingsUiState(
            language = configRepo.getLanguage(),
            themeMode = configRepo.getThemeMode(),
            autoLockMinutes = configRepo.getAutoLockMinutes(),
            clipboardClearSeconds = configRepo.getClipboardClearSeconds()
        )
    }

    fun setTheme(mode: ThemeMode) {
        configRepo?.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setLanguage(language: String) {
        configRepo?.setLanguage(language)
        _uiState.update { it.copy(language = language) }
    }

    fun setAutoLockMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 60)
        configRepo?.setAutoLockMinutes(clamped)
        _uiState.update { it.copy(autoLockMinutes = clamped) }
    }

    fun setClipboardClearSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        configRepo?.setClipboardClearSeconds(clamped)
        _uiState.update { it.copy(clipboardClearSeconds = clamped) }
    }
}
