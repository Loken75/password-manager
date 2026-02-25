package com.passwordmanager.android.ui.settings

import androidx.lifecycle.ViewModel
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.config.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val language: String = "en",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoLockMinutes: Int = 15,
    val clipboardClearSeconds: Int = 30
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepo: ConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            language = configRepo.getLanguage(),
            themeMode = configRepo.getThemeMode(),
            autoLockMinutes = configRepo.getAutoLockMinutes(),
            clipboardClearSeconds = configRepo.getClipboardClearSeconds()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        configRepo.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setLanguage(language: String) {
        configRepo.setLanguage(language)
        _uiState.update { it.copy(language = language) }
    }

    fun setAutoLockMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 60)
        configRepo.setAutoLockMinutes(clamped)
        _uiState.update { it.copy(autoLockMinutes = clamped) }
    }

    fun setClipboardClearSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        configRepo.setClipboardClearSeconds(clamped)
        _uiState.update { it.copy(clipboardClearSeconds = clamped) }
    }
}
