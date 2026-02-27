package com.passwordmanager.android.ui.login

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.AndroidVaultRepository
import com.passwordmanager.android.data.BiometricHelper
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.crypto.VaultDecryptionException
import com.passwordmanager.util.PasswordValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val createSuccess: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnabledForUser: Boolean = false,
    val showBiometricEnrollDialog: Boolean = false,
    val biometricError: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AndroidVaultRepository,
    private val sessionHolder: SessionHolder,
    private val biometricHelper: BiometricHelper,
    private val configRepo: ConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Track failed attempts per user for rate limiting
    private val failedAttempts = mutableMapOf<String, Int>()

    // Temporary storage for biometric enrollment flow
    private var pendingPasswordForEnroll: CharArray? = null
    private var pendingOnSuccess: (() -> Unit)? = null

    init {
        _uiState.update { it.copy(biometricAvailable = biometricHelper.canAuthenticate()) }
        loadUsers()
    }

    fun loadUsers() {
        val users = repository.listUsers().toList()
        val selectedUser = users.firstOrNull()
        val biometricEnabled = selectedUser?.let { configRepo.isBiometricEnabled(it) } ?: false
        _uiState.update {
            it.copy(
                users = users,
                selectedUser = selectedUser,
                biometricEnabledForUser = biometricEnabled
            )
        }
    }

    fun selectUser(user: String) {
        val biometricEnabled = configRepo.isBiometricEnabled(user)
        _uiState.update {
            it.copy(
                selectedUser = user,
                password = "",
                error = null,
                biometricError = null,
                biometricEnabledForUser = biometricEnabled
            )
        }
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

        _uiState.update { it.copy(isLoading = true, error = null, biometricError = null) }

        viewModelScope.launch(Dispatchers.IO) {
            val passwordChars = password.toCharArray()
            try {
                val result = repository.loadVault(user, passwordChars)
                failedAttempts.remove(user)
                sessionHolder.unlock(result.vault, result.session, user)
                _uiState.update { it.copy(isLoading = false, password = "") }

                // Propose biometric enrollment if available and not already enabled
                if (state.biometricAvailable && !configRepo.isBiometricEnabled(user)) {
                    pendingPasswordForEnroll = password.toCharArray()
                    pendingOnSuccess = onSuccess
                    _uiState.update { it.copy(showBiometricEnrollDialog = true) }
                } else {
                    launch(Dispatchers.Main) { onSuccess() }
                }
            } catch (e: VaultDecryptionException) {
                failedAttempts[user] = (failedAttempts[user] ?: 0) + 1
                _uiState.update { it.copy(isLoading = false, password = "", error = "error_invalid_password") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, password = "", error = e.message) }
            } finally {
                passwordChars.fill('\u0000')
            }
        }
    }

    fun enrollBiometric(activity: FragmentActivity) {
        val user = _uiState.value.selectedUser ?: return
        val password = pendingPasswordForEnroll ?: run { finishEnrollment(); return }

        biometricHelper.generateKey(user)
        val cipher = biometricHelper.getEncryptCipher(user)
        if (cipher == null) {
            biometricHelper.deleteKey(user)
            finishEnrollment()
            return
        }

        biometricHelper.showBiometricPrompt(
            activity = activity,
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            title = activity.getString(com.passwordmanager.android.R.string.biometric_enroll_title),
            subtitle = activity.getString(com.passwordmanager.android.R.string.biometric_enroll_subtitle),
            negativeText = activity.getString(com.passwordmanager.android.R.string.biometric_use_password),
            onSuccess = { crypto ->
                val authenticatedCipher = crypto?.cipher ?: return@showBiometricPrompt
                val (encrypted, iv) = BiometricHelper.encryptPassword(authenticatedCipher, password)
                configRepo.setBiometricEncryptedPassword(user, encrypted)
                configRepo.setBiometricIv(user, iv)
                configRepo.setBiometricEnabled(user, true)
                finishEnrollment()
            },
            onError = { _, _ ->
                biometricHelper.deleteKey(user)
                finishEnrollment()
            }
        )
    }

    fun dismissBiometricEnrollment() {
        finishEnrollment()
    }

    private fun finishEnrollment() {
        pendingPasswordForEnroll?.fill('\u0000')
        pendingPasswordForEnroll = null
        val callback = pendingOnSuccess
        pendingOnSuccess = null
        _uiState.update {
            it.copy(
                showBiometricEnrollDialog = false,
                biometricEnabledForUser = it.selectedUser?.let { u -> configRepo.isBiometricEnabled(u) } ?: false
            )
        }
        callback?.let { viewModelScope.launch(Dispatchers.Main) { it() } }
    }

    fun loginWithBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
        val user = _uiState.value.selectedUser ?: return
        val encryptedPassword = configRepo.getBiometricEncryptedPassword(user)
        val iv = configRepo.getBiometricIv(user)
        if (encryptedPassword == null || iv == null) {
            configRepo.clearBiometricData(user)
            _uiState.update {
                it.copy(biometricEnabledForUser = false, biometricError = "biometric_key_invalidated")
            }
            return
        }

        val cipher = biometricHelper.getDecryptCipher(user, iv)
        if (cipher == null) {
            configRepo.clearBiometricData(user)
            _uiState.update {
                it.copy(biometricEnabledForUser = false, biometricError = "biometric_key_invalidated")
            }
            return
        }

        biometricHelper.showBiometricPrompt(
            activity = activity,
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            title = activity.getString(com.passwordmanager.android.R.string.biometric_login_title),
            subtitle = activity.getString(com.passwordmanager.android.R.string.biometric_login_subtitle),
            negativeText = activity.getString(com.passwordmanager.android.R.string.biometric_use_password),
            onSuccess = { crypto ->
                val authenticatedCipher = crypto?.cipher ?: return@showBiometricPrompt
                val passwordChars = BiometricHelper.decryptPassword(authenticatedCipher, encryptedPassword)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val result = repository.loadVault(user, passwordChars)
                        sessionHolder.unlock(result.vault, result.session, user)
                        launch(Dispatchers.Main) { onSuccess() }
                    } catch (_: VaultDecryptionException) {
                        // Master password was changed — biometric data is stale
                        configRepo.clearBiometricData(user)
                        biometricHelper.deleteKey(user)
                        _uiState.update {
                            it.copy(
                                biometricEnabledForUser = false,
                                biometricError = "biometric_credential_expired"
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    } finally {
                        passwordChars.fill('\u0000')
                    }
                }
            },
            onError = { errorCode, _ ->
                if (errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                    errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                ) {
                    _uiState.update { it.copy(biometricError = "biometric_lockout") }
                }
                // Other errors: user cancelled or used negative button — no action needed
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        pendingPasswordForEnroll?.fill('\u0000')
        pendingPasswordForEnroll = null
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
            val passwordChars = password.toCharArray()
            try {
                repository.createVault(username, passwordChars, localizedCategories)
                loadUsers()
                _uiState.update {
                    it.copy(
                        isLoading = false, showCreateDialog = false,
                        createSuccess = true, selectedUser = username,
                        newPassword = "", newPasswordConfirm = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, createError = e.message) }
            } finally {
                passwordChars.fill('\u0000')
            }
        }
    }

    fun clearCreateSuccess() {
        _uiState.update { it.copy(createSuccess = false) }
    }
}
