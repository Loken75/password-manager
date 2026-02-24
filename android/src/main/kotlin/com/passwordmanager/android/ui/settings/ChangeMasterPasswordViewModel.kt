package com.passwordmanager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.crypto.VaultDecryptionException
import com.passwordmanager.util.PasswordValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangeMasterPasswordUiState(
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class ChangeMasterPasswordViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeMasterPasswordUiState())
    val uiState: StateFlow<ChangeMasterPasswordUiState> = _uiState.asStateFlow()

    fun updateOldPassword(value: String) = _uiState.update { it.copy(oldPassword = value, error = null) }
    fun updateNewPassword(value: String) = _uiState.update { it.copy(newPassword = value, error = null) }
    fun updateConfirmPassword(value: String) = _uiState.update { it.copy(confirmPassword = value, error = null) }

    fun changePassword() {
        val state = _uiState.value
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(error = "security_password_mismatch") }
            return
        }
        if (!PasswordValidator.validate(state.newPassword.toCharArray())) {
            _uiState.update { it.copy(error = "security_password_requirements") }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val username = SessionHolder.username ?: throw IllegalStateException()
                val vault = SessionHolder.vault ?: throw IllegalStateException()
                val currentSession = SessionHolder.session ?: throw IllegalStateException()
                val repo = SessionHolder.getRepository()

                // Verify old password by trying to load the vault
                try {
                    repo.loadVault(username, state.oldPassword.toCharArray())
                } catch (e: VaultDecryptionException) {
                    _uiState.update { it.copy(isLoading = false, error = "error_invalid_password") }
                    return@launch
                }

                // Change master password
                val newSession = repo.changeMasterPassword(
                    username, vault, currentSession, state.newPassword.toCharArray()
                )

                // Update session holder with new session
                SessionHolder.unlock(vault, newSession, username)

                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
