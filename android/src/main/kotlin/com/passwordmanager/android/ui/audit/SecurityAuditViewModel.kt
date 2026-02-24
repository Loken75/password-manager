package com.passwordmanager.android.ui.audit

import androidx.lifecycle.ViewModel
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength
import com.passwordmanager.vault.VaultEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SecurityAuditUiState(
    val weakEntries: List<VaultEntry> = emptyList(),
    val duplicateEntries: List<VaultEntry> = emptyList(),
    val oldEntries: List<VaultEntry> = emptyList(),
    val passwordExpiryDays: Int = 180,
    val totalIssues: Int = 0
)

class SecurityAuditViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityAuditUiState())
    val uiState: StateFlow<SecurityAuditUiState> = _uiState.asStateFlow()

    init {
        runAudit()
    }

    fun runAudit() {
        val service = SessionHolder.vaultService ?: return
        val vault = SessionHolder.vault ?: return

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

        val totalIssues = weak.size + duplicates.size + old.size

        _uiState.value = SecurityAuditUiState(
            weakEntries = weak,
            duplicateEntries = duplicates,
            oldEntries = old,
            passwordExpiryDays = expiryDays,
            totalIssues = totalIssues
        )
    }
}
