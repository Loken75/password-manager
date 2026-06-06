package com.passwordmanager.android.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.PasswordEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryDetailUiState(
    val entry: PasswordEntry? = null,
    val passwordVisible: Boolean = false,
    // Bumped on every (re)load so StateFlow always emits a distinct value even when the
    // underlying entry object is mutated in place (e.g. toggling favorite), forcing recomposition.
    val refreshToken: Long = 0
)

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val sessionHolder: SessionHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    fun loadEntry(entryId: String) {
        val entry = sessionHolder.vaultService?.search("")
            ?.find { it.id == entryId }
        _uiState.value = _uiState.value.copy(
            entry = entry,
            refreshToken = _uiState.value.refreshToken + 1
        )
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(passwordVisible = !_uiState.value.passwordVisible)
    }

    fun toggleFavorite(entryId: String) {
        val vaultService = sessionHolder.vaultService ?: return
        vaultService.toggleFavorite(entryId)
        viewModelScope.launch(Dispatchers.IO) {
            sessionHolder.save()
        }
        loadEntry(entryId)
    }

    fun duplicateEntry(entryId: String, prefix: String = "Copy of"): String? {
        val vaultService = sessionHolder.vaultService ?: return null
        val entry = vaultService.search("").find { it.id == entryId } ?: return null
        val pwdCopy = entry.password
        try {
            val dup = PasswordEntry(
                "$prefix ${entry.title}",
                entry.username,
                entry.email,
                pwdCopy,
                entry.url,
                entry.notes,
                entry.category,
                if (entry.tags != null) ArrayList(entry.tags) else null
            )
            vaultService.addEntry(dup)
            viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
            return dup.id
        } finally {
            SecureWiper.wipe(pwdCopy)
        }
    }

    fun deleteEntry(entryId: String): Boolean {
        val deleted = sessionHolder.vaultService?.deleteEntry(entryId) ?: false
        if (deleted) sessionHolder.save()
        return deleted
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value = EntryDetailUiState()
    }
}
