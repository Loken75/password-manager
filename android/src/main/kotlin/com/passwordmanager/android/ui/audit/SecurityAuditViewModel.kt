package com.passwordmanager.android.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength
import com.passwordmanager.security.HibpChecker
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.PasswordEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SecurityAuditUiState(
    // À risque
    val weakEntries: List<PasswordEntry> = emptyList(),
    val duplicateEntries: List<PasswordEntry> = emptyList(),
    val oldEntries: List<PasswordEntry> = emptyList(),
    val breachedEntries: List<PasswordEntry> = emptyList(),
    val passwordExpiryDays: Int = 180,
    // Points forts
    val strongEntries: List<PasswordEntry> = emptyList(),
    val uniquePercent: Int = 100,
    // Vue d'ensemble
    val total: Int = 0,
    val score20: Int = 0,
    val score: Int = 0,
    val totalIssues: Int = 0,
    // Composition
    val categoriesCount: Int = 0,
    val favoritesCount: Int = 0,
    // Complétude
    val noUrlCount: Int = 0,
    val noEmailCount: Int = 0,
    // Activité
    val addedLast30: Int = 0,
    val modifiedLast30: Int = 0,
    val oldestAgeDays: Long? = null,
    // HIBP
    val isCheckingBreaches: Boolean = false,
    val breachError: Boolean = false,
    val breachesChecked: Boolean = false
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

        val all = service.search("")

        // Single strength pass: classify weak/strong and accumulate the security score.
        // Each password clone is wiped right after analysis.
        val weak = mutableListOf<PasswordEntry>()
        val strong = mutableListOf<PasswordEntry>()
        var points = 0
        for (e in all) {
            val pw = e.password ?: continue
            try {
                when (PasswordStrengthAnalyzer.analyze(pw)) {
                    Strength.WEAK -> { weak.add(e); points += 25 }
                    Strength.MEDIUM -> { weak.add(e); points += 55 }
                    Strength.STRONG -> { strong.add(e); points += 85 }
                    Strength.VERY_STRONG -> { strong.add(e); points += 100 }
                }
            } finally {
                SecureWiper.wipe(pw)
            }
        }

        val duplicates = service.findDuplicatePasswords().values.flatten()
        val old = service.findOldPasswords(expiryDays)

        val total = all.size
        val score = if (total == 0) 100 else Math.round(points / total.toFloat())
        val score20 = Math.round(score / 5.0).toInt()
        val uniquePercent = if (total == 0) 100 else Math.round((total - duplicates.size) * 100f / total)

        val categoriesCount = all.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct().size
        val favoritesCount = all.count { it.isFavorite }
        val noUrlCount = all.count { it.url.isNullOrBlank() }
        val noEmailCount = all.count { it.email.isNullOrBlank() }

        val today = LocalDate.now()
        val cutoff = today.minusDays(30)
        val addedLast30 = all.count { parseDate(it.createdAt)?.isAfter(cutoff) == true }
        val modifiedLast30 = all.count { parseDate(it.updatedAt)?.isAfter(cutoff) == true }
        val oldest = all.mapNotNull { parseDate(it.updatedAt) }.minOrNull()
        val oldestAgeDays = oldest?.let { ChronoUnit.DAYS.between(it, today) }

        val breached = _uiState.value.breachedEntries
        val totalIssues = weak.size + duplicates.size + old.size + breached.size

        _uiState.value = _uiState.value.copy(
            weakEntries = weak,
            strongEntries = strong,
            duplicateEntries = duplicates,
            oldEntries = old,
            passwordExpiryDays = expiryDays,
            total = total,
            score = score,
            score20 = score20,
            uniquePercent = uniquePercent,
            categoriesCount = categoriesCount,
            favoritesCount = favoritesCount,
            noUrlCount = noUrlCount,
            noEmailCount = noEmailCount,
            addedLast30 = addedLast30,
            modifiedLast30 = modifiedLast30,
            oldestAgeDays = oldestAgeDays,
            totalIssues = totalIssues
        )
    }

    private fun parseDate(iso: String?): LocalDate? {
        if (iso == null || iso.length < 10) return null
        return try { LocalDate.parse(iso.substring(0, 10)) } catch (e: Exception) { null }
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
                    breachesChecked = true,
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
