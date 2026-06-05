package com.passwordmanager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.BiometricHelper
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.data.WorkspaceManager
import com.passwordmanager.crypto.VaultDecryptionException
import com.passwordmanager.util.PasswordValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangeMasterPasswordUiState(
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class ChangeMasterPasswordViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val biometricHelper: BiometricHelper,
    private val configRepo: ConfigRepository,
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeMasterPasswordUiState())
    val uiState: StateFlow<ChangeMasterPasswordUiState> = _uiState.asStateFlow()

    fun updateOldPassword(value: String) = _uiState.update { it.copy(oldPassword = value, error = null) }
    fun updateNewPassword(value: String) = _uiState.update { it.copy(newPassword = value, error = null) }
    fun updateConfirmPassword(value: String) = _uiState.update { it.copy(confirmPassword = value, error = null) }

    override fun onCleared() {
        super.onCleared()
        _uiState.update { it.copy(oldPassword = "", newPassword = "", confirmPassword = "") }
    }

    fun changePassword() {
        val state = _uiState.value
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(error = "security_password_mismatch") }
            return
        }
        val validateChars = state.newPassword.toCharArray()
        try {
            if (!PasswordValidator.validate(validateChars)) {
                _uiState.update { it.copy(error = "security_password_requirements") }
                return
            }
        } finally {
            validateChars.fill('\u0000')
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val oldPasswordChars = state.oldPassword.toCharArray()
            val newPasswordChars = state.newPassword.toCharArray()
            try {
                val username = sessionHolder.username ?: throw IllegalStateException()
                val vault = sessionHolder.vault ?: throw IllegalStateException()
                val currentSession = sessionHolder.session ?: throw IllegalStateException()
                val repo = sessionHolder.getRepository()

                // Verify old password by trying to load the vault
                try {
                    val check = repo.loadVault(username, oldPasswordChars)
                    check.vault.wipe()
                    check.session.destroy()
                } catch (e: VaultDecryptionException) {
                    _uiState.update { it.copy(isLoading = false, error = "error_invalid_password") }
                    return@launch
                }

                // Change master password
                val newSession = repo.changeMasterPassword(
                    username, vault, currentSession, newPasswordChars
                )

                // Update session holder with new session
                sessionHolder.unlock(vault, newSession, username)

                // Invalidate biometric data — the stored encrypted password is now stale
                val account = workspaceManager.biometricAccount(username)
                if (configRepo.isBiometricEnabled(account)) {
                    configRepo.clearBiometricData(account)
                    biometricHelper.deleteKey(account)
                }

                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            } finally {
                oldPasswordChars.fill('\u0000')
                newPasswordChars.fill('\u0000')
            }
        }
    }
}
