package com.passwordmanager.android.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength
import com.passwordmanager.security.HibpChecker
import com.passwordmanager.vault.PasswordEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SecurityAuditUiState(
    val weakEntries: List<PasswordEntry> = emptyList(),
    val duplicateEntries: List<PasswordEntry> = emptyList(),
    val oldEntries: List<PasswordEntry> = emptyList(),
    val passwordExpiryDays: Int = 180,
    val breachedEntries: List<PasswordEntry> = emptyList(),
    val isCheckingBreaches: Boolean = false,
    val breachError: Boolean = false,
    val totalIssues: Int = 0
)

@HiltViewModel
class SecurityAuditViewModel @Inject constructor(
    private val sessionHolder: SessionHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityAuditUiState())
    val uiState: StateFlow<SecurityAuditUiState> = _uiState.asStateFlow()

    init {
        runAudit()
    }

    fun runAudit() {
        val service = sessionHolder.vaultService ?: return
        val vault = sessionHolder.vault ?: return

        val expiryDays = (vault.settings?.get("password_expiry_days") as? Number)?.toInt() ?: 180

        // Find weak passwords
        val weak = service.search("").filter { entry ->
            entry.password?.let { PasswordStrengthAnalyzer.analyze(it) }
                .let { it == Strength.WEAK || it == Strength.MEDIUM }
        }

        // Find duplicate passwords
        val duplicateMap = service.findDuplicatePasswords()
        val duplicates = duplicateMap.values.flatten()

        // Find old passwords
        val old = service.findOldPasswords(expiryDays)

        val breached = _uiState.value.breachedEntries
        val totalIssues = weak.size + duplicates.size + old.size + breached.size

        _uiState.value = _uiState.value.copy(
            weakEntries = weak,
            duplicateEntries = duplicates,
            oldEntries = old,
            passwordExpiryDays = expiryDays,
            totalIssues = totalIssues
        )
    }

    fun checkBreaches() {
        val service = sessionHolder.vaultService ?: return

        _uiState.value = _uiState.value.copy(
            isCheckingBreaches = true,
            breachError = false
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allEntries = service.search("")
                val breached = mutableListOf<PasswordEntry>()

                for (entry in allEntries) {
                    val password = entry.password ?: continue
                    val count = HibpChecker.checkPassword(password)
                    if (count > 0) {
                        breached.add(entry)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    breachedEntries = breached,
                    isCheckingBreaches = false,
                    breachError = false,
                    totalIssues = _uiState.value.weakEntries.size +
                        _uiState.value.duplicateEntries.size +
                        _uiState.value.oldEntries.size +
                        breached.size
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCheckingBreaches = false,
                    breachError = true
                )
            }
        }
    }
}
