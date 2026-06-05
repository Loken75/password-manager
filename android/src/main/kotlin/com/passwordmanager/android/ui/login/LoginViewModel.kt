package com.passwordmanager.android.ui.login

import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.AndroidVaultRepository
import com.passwordmanager.android.data.BiometricHelper
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.data.WorkspaceManager
import com.passwordmanager.android.di.IoDispatcher
import com.passwordmanager.crypto.VaultDecryptionException
import com.passwordmanager.util.PasswordValidator
import com.passwordmanager.vault.VaultStoreMigrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
    val biometricError: String? = null,
    val workspaceSpec: String = WorkspaceManager.SPEC_INTERNAL,
    val workspaceOptions: List<String> = listOf(WorkspaceManager.SPEC_INTERNAL),
    /** Target spec awaiting the user's migrate/keep decision (null = no pending switch). */
    val pendingWorkspaceSwitch: String? = null,
    val pendingWorkspaceVaultCount: Int = 0
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AndroidVaultRepository,
    private val sessionHolder: SessionHolder,
    private val biometricHelper: BiometricHelper,
    private val configRepo: ConfigRepository,
    private val workspaceManager: WorkspaceManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Track failed attempts per user for rate limiting
    private val failedAttempts = mutableMapOf<String, Int>()

    // Temporary storage for biometric enrollment flow
    private var pendingPasswordForEnroll: CharArray? = null
    private var pendingOnSuccess: (() -> Unit)? = null

    init {
        // Workspace + user resolution can touch a SAF tree (ContentResolver), so keep it off main.
        viewModelScope.launch(ioDispatcher) {
            _uiState.update {
                it.copy(
                    biometricAvailable = biometricHelper.canAuthenticate(),
                    workspaceSpec = workspaceManager.currentSpec(),
                    workspaceOptions = workspaceManager.availableSpecs()
                )
            }
            loadUsers()
        }
    }

    /**
     * Requests a working-folder switch. If the current folder holds vaults, defers to a
     * migrate/keep prompt; otherwise switches immediately. Runs off the main thread because
     * a SAF-backed store lists/copies via ContentResolver.
     */
    fun switchWorkspace(spec: String) {
        if (spec == _uiState.value.workspaceSpec) return
        viewModelScope.launch(ioDispatcher) {
            val existing = repository.listUsers().size
            if (existing > 0) {
                _uiState.update { it.copy(pendingWorkspaceSwitch = spec, pendingWorkspaceVaultCount = existing) }
            } else {
                performWorkspaceSwitch(spec, migrate = false)
            }
        }
    }

    /** Confirms a pending switch, optionally migrating the existing vaults to the new folder. */
    fun confirmWorkspaceSwitch(migrate: Boolean) {
        val spec = _uiState.value.pendingWorkspaceSwitch ?: return
        viewModelScope.launch(ioDispatcher) { performWorkspaceSwitch(spec, migrate) }
    }

    fun cancelWorkspaceSwitch() {
        _uiState.update { it.copy(pendingWorkspaceSwitch = null, pendingWorkspaceVaultCount = 0) }
    }

    /**
     * Performs the switch: locks any session (defensive), optionally migrates vault files,
     * re-points the repository, resets stale per-user state, and reloads users.
     */
    private fun performWorkspaceSwitch(spec: String, migrate: Boolean) {
        val oldSpec = _uiState.value.workspaceSpec
        sessionHolder.lock()
        if (migrate) {
            try {
                val result = VaultStoreMigrator.migrate(
                    workspaceManager.storeFor(oldSpec), workspaceManager.storeFor(spec)
                )
                if (result.hasFailures()) {
                    Log.w("LoginViewModel", "Workspace migration: ${result.failed} file(s) could not be moved")
                }
            } catch (e: Exception) {
                Log.w("LoginViewModel", "Workspace migration failed", e)
            }
        }
        workspaceManager.setWorkspace(spec)
        repository.useStore(workspaceManager.storeFor(spec))
        failedAttempts.clear()
        _uiState.update {
            it.copy(
                workspaceSpec = spec, password = "", error = null, biometricError = null,
                isRateLimited = false, pendingWorkspaceSwitch = null, pendingWorkspaceVaultCount = 0
            )
        }
        loadUsers()
    }

    /** Biometric enrollment is namespaced per workspace so same-named users don't collide. */
    private fun bioAccount(username: String): String = workspaceManager.biometricAccount(username)

    fun loadUsers() {
        val users = repository.listUsers().toList()
        val selectedUser = users.firstOrNull()
        val biometricEnabled = selectedUser?.let { configRepo.isBiometricEnabled(bioAccount(it)) } ?: false
        _uiState.update {
            it.copy(
                users = users,
                selectedUser = selectedUser,
                biometricEnabledForUser = biometricEnabled
            )
        }
    }

    fun selectUser(user: String) {
        val biometricEnabled = configRepo.isBiometricEnabled(bioAccount(user))
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
                if (state.biometricAvailable && !configRepo.isBiometricEnabled(bioAccount(user))) {
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
        val account = bioAccount(user)
        val password = pendingPasswordForEnroll ?: run { finishEnrollment(); return }

        biometricHelper.generateKey(account)
        val cipher = biometricHelper.getEncryptCipher(account)
        if (cipher == null) {
            biometricHelper.deleteKey(account)
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
                configRepo.setBiometricEncryptedPassword(account, encrypted)
                configRepo.setBiometricIv(account, iv)
                configRepo.setBiometricEnabled(account, true)
                finishEnrollment()
            },
            onError = { _, _ ->
                biometricHelper.deleteKey(account)
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
                biometricEnabledForUser = it.selectedUser?.let { u -> configRepo.isBiometricEnabled(bioAccount(u)) } ?: false
            )
        }
        callback?.let { viewModelScope.launch(Dispatchers.Main) { it() } }
    }

    fun loginWithBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
        val user = _uiState.value.selectedUser ?: return
        val account = bioAccount(user)
        val encryptedPassword = configRepo.getBiometricEncryptedPassword(account)
        val iv = configRepo.getBiometricIv(account)
        if (encryptedPassword == null || iv == null) {
            configRepo.clearBiometricData(account)
            _uiState.update {
                it.copy(biometricEnabledForUser = false, biometricError = "biometric_key_invalidated")
            }
            return
        }

        val cipher = biometricHelper.getDecryptCipher(account, iv)
        if (cipher == null) {
            configRepo.clearBiometricData(account)
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
                        configRepo.clearBiometricData(account)
                        biometricHelper.deleteKey(account)
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
