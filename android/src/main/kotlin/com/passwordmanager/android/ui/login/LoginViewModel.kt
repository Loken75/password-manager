package com.passwordmanager.android.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.PasswordManagerApp
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.crypto.VaultDecryptionException
import com.passwordmanager.util.PasswordValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val users: List<String> = emptyList(),
    val selectedUser: String? = null,
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRateLimited: Boolean = false,
    val showCreateDialog: Boolean = false,
    val newUsername: String = "",
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val createError: String? = null,
    val createSuccess: Boolean = false
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PasswordManagerApp).vaultRepository

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Track failed attempts per user for rate limiting
    private val failedAttempts = mutableMapOf<String, Int>()

    init {
        loadUsers()
    }

    fun loadUsers() {
        val users = repository.listUsers().toList()
        _uiState.update { it.copy(users = users, selectedUser = users.firstOrNull()) }
    }

    fun selectUser(user: String) {
        _uiState.update { it.copy(selectedUser = user, password = "", error = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        val user = state.selectedUser ?: return
        val password = state.password

        if (password.isBlank()) {
            _uiState.update { it.copy(error = "error_empty_password") }
            return
        }

        val attempts = failedAttempts.getOrDefault(user, 0)
        if (attempts >= 3) {
            _uiState.update { it.copy(isRateLimited = true, error = "error_too_many_attempts") }
            viewModelScope.launch {
                val delayMs = (6_000L * (1 shl (attempts - 3).coerceAtMost(2)))
                    .coerceAtMost(30_000L)
                delay(delayMs)
                _uiState.update { it.copy(isRateLimited = false, error = null) }
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val passwordChars = password.toCharArray()
                val result = repository.loadVault(user, passwordChars)
                passwordChars.fill('\u0000')
                failedAttempts.remove(user)
                SessionHolder.unlock(result.vault, result.session, user)
                _uiState.update { it.copy(isLoading = false) }
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: VaultDecryptionException) {
                failedAttempts[user] = (failedAttempts[user] ?: 0) + 1
                _uiState.update { it.copy(isLoading = false, error = "error_invalid_password") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true, newUsername = "", newPassword = "",
                newPasswordConfirm = "", createError = null, createSuccess = false
            )
        }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun updateNewUsername(value: String) {
        _uiState.update { it.copy(newUsername = value, createError = null) }
    }

    fun updateNewPassword(value: String) {
        _uiState.update { it.copy(newPassword = value, createError = null) }
    }

    fun updateNewPasswordConfirm(value: String) {
        _uiState.update { it.copy(newPasswordConfirm = value, createError = null) }
    }

    fun createUser(localizedCategories: List<String>) {
        val state = _uiState.value
        val username = state.newUsername.trim()
        val password = state.newPassword
        val confirm = state.newPasswordConfirm

        if (username.isBlank()) {
            _uiState.update { it.copy(createError = "error_empty_username") }
            return
        }
        if (!username.matches(Regex("[a-zA-Z0-9_]+"))) {
            _uiState.update { it.copy(createError = "error_invalid_username") }
            return
        }
        if (repository.vaultExists(username)) {
            _uiState.update { it.copy(createError = "error_user_exists") }
            return
        }
        if (password != confirm) {
            _uiState.update { it.copy(createError = "security_password_mismatch") }
            return
        }
        if (!PasswordValidator.validate(password.toCharArray())) {
            _uiState.update { it.copy(createError = "security_password_requirements") }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val passwordChars = password.toCharArray()
                repository.createVault(username, passwordChars, localizedCategories)
                passwordChars.fill('\u0000')
                loadUsers()
                _uiState.update {
                    it.copy(
                        isLoading = false, showCreateDialog = false,
                        createSuccess = true, selectedUser = username
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, createError = e.message) }
            }
        }
    }

    fun clearCreateSuccess() {
        _uiState.update { it.copy(createSuccess = false) }
    }
}
