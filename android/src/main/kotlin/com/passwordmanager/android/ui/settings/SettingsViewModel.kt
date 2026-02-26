package com.passwordmanager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.config.StorageMode
import com.passwordmanager.config.ThemeMode
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

data class SettingsUiState(
    val language: String = "en",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoLockMinutes: Int = 15,
    val clipboardClearSeconds: Int = 30,
    val storageMode: StorageMode = StorageMode.LOCAL,
    val sftpHost: String = "",
    val sftpPort: String = "22",
    val sftpUser: String = "",
    val sftpKeyPath: String = "",
    val sftpRemotePath: String = "",
    val connectionTestResult: String? = null
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
            clipboardClearSeconds = configRepo.getClipboardClearSeconds(),
            storageMode = configRepo.getStorageMode(),
            sftpHost = configRepo.getSftpHost(),
            sftpPort = configRepo.getSftpPort().toString(),
            sftpUser = configRepo.getSftpUser(),
            sftpKeyPath = configRepo.getSftpKeyPath(),
            sftpRemotePath = configRepo.getSftpRemotePath()
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
        // Apply locale change via AndroidX AppCompat
        val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(language)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
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

    // --- SFTP sync ---

    fun setStorageMode(mode: StorageMode) {
        configRepo.setStorageMode(mode)
        _uiState.update { it.copy(storageMode = mode) }
    }

    fun setSftpHost(host: String) {
        configRepo.setSftpHost(host)
        _uiState.update { it.copy(sftpHost = host) }
    }

    fun setSftpPort(port: String) {
        val portInt = port.toIntOrNull() ?: return
        configRepo.setSftpPort(portInt)
        _uiState.update { it.copy(sftpPort = port) }
    }

    fun setSftpUser(user: String) {
        configRepo.setSftpUser(user)
        _uiState.update { it.copy(sftpUser = user) }
    }

    fun setSftpKeyPath(path: String) {
        configRepo.setSftpKeyPath(path)
        _uiState.update { it.copy(sftpKeyPath = path) }
    }

    fun setSftpRemotePath(path: String) {
        configRepo.setSftpRemotePath(path)
        _uiState.update { it.copy(sftpRemotePath = path) }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.sftpHost.isBlank() || state.sftpUser.isBlank() || state.sftpKeyPath.isBlank()) {
            _uiState.update { it.copy(connectionTestResult = "fail") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val jsch = JSch()
                jsch.addIdentity(state.sftpKeyPath)
                val session: Session = jsch.getSession(
                    state.sftpUser,
                    state.sftpHost,
                    state.sftpPort.toIntOrNull() ?: 22
                )
                val knownHostsFile = java.io.File(
                    android.os.Environment.getExternalStorageDirectory(), ".ssh/known_hosts"
                )
                if (knownHostsFile.exists()) {
                    jsch.setKnownHosts(knownHostsFile.absolutePath)
                    session.setConfig("StrictHostKeyChecking", "yes")
                } else {
                    session.setConfig("StrictHostKeyChecking", "accept-new")
                }
                session.connect(10_000)
                val ok = session.isConnected
                session.disconnect()
                if (ok) "ok" else "fail"
            } catch (e: Exception) {
                Log.e(TAG, "SFTP connection test failed", e)
                "fail"
            }
            _uiState.update { it.copy(connectionTestResult = result) }
        }
    }

    fun clearConnectionTestResult() {
        _uiState.update { it.copy(connectionTestResult = null) }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
