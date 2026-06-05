package com.passwordmanager.android.ui.settings

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.BiometricHelper
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.HostKeyChangedException
import com.passwordmanager.android.data.HostKeyPrompt
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.data.SftpHostKeyVerifier
import com.passwordmanager.android.data.SshHostKeyStore
import com.passwordmanager.android.data.UnknownHostKeyException
import com.passwordmanager.android.data.WorkspaceManager
import com.passwordmanager.config.StorageMode
import com.passwordmanager.config.ThemeMode
import com.jcraft.jsch.JSch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

data class SshKeyOption(val id: String, val name: String)

data class SettingsUiState(
    val language: String = "en",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoLockMinutes: Int = 15,
    val clipboardClearSeconds: Int = 30,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val showBiometricPasswordDialog: Boolean = false,
    val biometricPasswordInput: String = "",
    val biometricPasswordError: String? = null,
    val biometricPasswordLoading: Boolean = false,
    val storageMode: StorageMode = StorageMode.LOCAL,
    val sftpHost: String = "",
    val sftpPort: String = "22",
    val sftpUser: String = "",
    val sftpKeyPath: String = "",
    val sftpRemotePath: String = "",
    val sshKeys: List<SshKeyOption> = emptyList(),
    val selectedSshKeyId: String = "",
    val connectionTestResult: String? = null,
    /** Non-null when a host key needs user confirmation (first use or changed). */
    val hostKeyPrompt: HostKeyPrompt? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepo: ConfigRepository,
    private val biometricHelper: BiometricHelper,
    private val sessionHolder: SessionHolder,
    private val hostKeyStore: SshHostKeyStore,
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    /** Biometric enrollment is namespaced per workspace so same-named users don't collide. */
    private fun bioAccount(username: String): String = workspaceManager.biometricAccount(username)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            language = configRepo.getLanguage(),
            themeMode = configRepo.getThemeMode(),
            autoLockMinutes = configRepo.getAutoLockMinutes(),
            clipboardClearSeconds = configRepo.getClipboardClearSeconds(),
            biometricAvailable = biometricHelper.canAuthenticate(),
            biometricEnabled = sessionHolder.username?.let { configRepo.isBiometricEnabled(bioAccount(it)) } ?: false,
            storageMode = configRepo.getStorageMode(),
            sftpHost = configRepo.getSftpHost(),
            sftpPort = configRepo.getSftpPort().toString(),
            sftpUser = configRepo.getSftpUser(),
            sftpKeyPath = configRepo.getSftpKeyPath(),
            sftpRemotePath = configRepo.getSftpRemotePath(),
            selectedSshKeyId = configRepo.getSftpKeyId()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init { refreshSshKeys() }

    fun refreshSshKeys() {
        val vault = sessionHolder.vault ?: return
        val keys = vault.sshKeyEntries.map { SshKeyOption(it.id, it.title ?: "") }
        _uiState.update { it.copy(sshKeys = keys) }
    }

    fun selectSshKey(keyId: String) {
        configRepo.setSftpKeyId(keyId)
        _uiState.update { it.copy(selectedSshKeyId = keyId) }
    }

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

    // --- Biometric ---

    fun showBiometricPasswordPrompt() {
        _uiState.update { it.copy(showBiometricPasswordDialog = true, biometricPasswordInput = "", biometricPasswordError = null) }
    }

    fun dismissBiometricPasswordPrompt() {
        _uiState.update { it.copy(showBiometricPasswordDialog = false, biometricPasswordInput = "", biometricPasswordError = null) }
    }

    fun updateBiometricPasswordInput(value: String) {
        _uiState.update { it.copy(biometricPasswordInput = value, biometricPasswordError = null) }
    }

    fun confirmBiometricPassword(activity: FragmentActivity) {
        val username = sessionHolder.username ?: return
        val password = _uiState.value.biometricPasswordInput

        if (password.isBlank()) return

        _uiState.update { it.copy(biometricPasswordLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val passwordChars = password.toCharArray()
            try {
                // Verify the password by loading the vault
                val repo = sessionHolder.getRepository()
                val result = repo.loadVault(username, passwordChars)
                result.vault.wipe()
                result.session.destroy()

                // Password is correct — proceed with biometric enrollment
                launch(Dispatchers.Main) {
                    _uiState.update { it.copy(showBiometricPasswordDialog = false, biometricPasswordLoading = false) }
                    enrollBiometric(activity, password.toCharArray())
                }
            } catch (_: com.passwordmanager.crypto.VaultDecryptionException) {
                _uiState.update { it.copy(biometricPasswordLoading = false, biometricPasswordError = "error_invalid_password") }
            } catch (e: Exception) {
                _uiState.update { it.copy(biometricPasswordLoading = false, biometricPasswordError = e.message) }
            } finally {
                passwordChars.fill('\u0000')
            }
        }
    }

    private fun enrollBiometric(activity: FragmentActivity, password: CharArray) {
        val username = sessionHolder.username ?: run { password.fill('\u0000'); return }

        val account = bioAccount(username)
        biometricHelper.generateKey(account)
        val cipher = biometricHelper.getEncryptCipher(account)
        if (cipher == null) {
            biometricHelper.deleteKey(account)
            password.fill('\u0000')
            return
        }

        biometricHelper.showBiometricPrompt(
            activity = activity,
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            title = activity.getString(com.passwordmanager.android.R.string.biometric_enroll_title),
            subtitle = activity.getString(com.passwordmanager.android.R.string.biometric_enroll_subtitle),
            negativeText = activity.getString(com.passwordmanager.android.R.string.common_cancel),
            onSuccess = { crypto ->
                val authenticatedCipher = crypto?.cipher ?: run { password.fill('\u0000'); return@showBiometricPrompt }
                try {
                    val (encrypted, iv) = BiometricHelper.encryptPassword(authenticatedCipher, password)
                    configRepo.setBiometricEncryptedPassword(account, encrypted)
                    configRepo.setBiometricIv(account, iv)
                    configRepo.setBiometricEnabled(account, true)
                    _uiState.update { it.copy(biometricEnabled = true) }
                } finally {
                    password.fill('\u0000')
                }
            },
            onError = { _, _ ->
                biometricHelper.deleteKey(account)
                password.fill('\u0000')
            }
        )
    }

    fun disableBiometric() {
        val username = sessionHolder.username ?: return
        val account = bioAccount(username)
        configRepo.clearBiometricData(account)
        biometricHelper.deleteKey(account)
        _uiState.update { it.copy(biometricEnabled = false) }
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
        if (state.sftpHost.isBlank() || state.sftpUser.isBlank()) {
            _uiState.update { it.copy(connectionTestResult = "fail") }
            return
        }

        // Load private key bytes from vault
        val vault = sessionHolder.vault
        val keyEntry = vault?.sshKeyEntries?.find { it.id == state.selectedSshKeyId }
        val keyPath = state.sftpKeyPath

        if (keyEntry == null && keyPath.isBlank()) {
            _uiState.update { it.copy(connectionTestResult = "fail") }
            return
        }

        val port = state.sftpPort.toIntOrNull() ?: 22
        viewModelScope.launch(Dispatchers.IO) {
            var keyBytes: ByteArray? = null
            try {
                val jsch = JSch()
                if (keyEntry != null) {
                    val privChars = keyEntry.privateKey
                    if (privChars != null) {
                        keyBytes = String(privChars).toByteArray(Charsets.UTF_8)
                        com.passwordmanager.util.SecureWiper.wipe(privChars)
                        jsch.addIdentity("vault_key", keyBytes, null, null)
                    }
                } else {
                    jsch.addIdentity(keyPath)
                }
                val session = SftpHostKeyVerifier.connect(
                    jsch, state.sftpHost, port, state.sftpUser, hostKeyStore, 10_000
                )
                val ok = session.isConnected
                session.disconnect()
                jsch.removeAllIdentity()
                _uiState.update { it.copy(connectionTestResult = if (ok) "ok" else "fail") }
            } catch (e: UnknownHostKeyException) {
                stashPendingHostKey(e.host, e.port, e.blob)
                _uiState.update {
                    it.copy(hostKeyPrompt = HostKeyPrompt(e.host, e.port, e.fingerprint, e.keyType, changed = false))
                }
            } catch (e: HostKeyChangedException) {
                stashPendingHostKey(e.host, e.port, e.blob)
                _uiState.update {
                    it.copy(hostKeyPrompt = HostKeyPrompt(e.host, e.port, e.fingerprint, e.keyType, changed = true))
                }
            } catch (e: Exception) {
                Log.e(TAG, "SFTP connection test failed", e)
                _uiState.update { it.copy(connectionTestResult = "fail") }
            } finally {
                keyBytes?.fill(0)
            }
        }
    }

    fun clearConnectionTestResult() {
        _uiState.update { it.copy(connectionTestResult = null) }
    }

    // Host key awaiting user confirmation (raw bytes kept out of UI state).
    @Volatile
    private var pendingHostKeyBlob: ByteArray? = null
    @Volatile
    private var pendingHostKeyHost: String? = null
    @Volatile
    private var pendingHostKeyPort: Int = 22

    private fun stashPendingHostKey(host: String, port: Int, blob: ByteArray) {
        pendingHostKeyHost = host
        pendingHostKeyPort = port
        pendingHostKeyBlob = blob
    }

    /** User confirmed the presented host key: pin it and retry the connection test. */
    fun confirmHostKey() {
        val blob = pendingHostKeyBlob
        val host = pendingHostKeyHost
        if (blob == null || host == null) {
            dismissHostKeyPrompt()
            return
        }
        val port = pendingHostKeyPort
        clearPendingHostKey()
        _uiState.update { it.copy(hostKeyPrompt = null) }
        viewModelScope.launch(Dispatchers.IO) {
            hostKeyStore.pin(host, port, blob)
            testConnection()
        }
    }

    /** User declined the host key: abort without pinning. */
    fun dismissHostKeyPrompt() {
        clearPendingHostKey()
        _uiState.update { it.copy(hostKeyPrompt = null) }
    }

    private fun clearPendingHostKey() {
        pendingHostKeyBlob = null
        pendingHostKeyHost = null
        pendingHostKeyPort = 22
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.update { it.copy(biometricPasswordInput = "") }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
