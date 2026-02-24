package com.passwordmanager.android.ui.vault

import androidx.lifecycle.ViewModel
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.vault.VaultEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EntryDetailUiState(
    val entry: VaultEntry? = null,
    val passwordVisible: Boolean = false
)

class EntryDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    fun loadEntry(entryId: String) {
        val entry = SessionHolder.vaultService?.search("")
            ?.find { it.id == entryId }
        _uiState.value = EntryDetailUiState(entry = entry)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(passwordVisible = !_uiState.value.passwordVisible)
    }

    fun deleteEntry(entryId: String): Boolean {
        val deleted = SessionHolder.vaultService?.deleteEntry(entryId) ?: false
        if (deleted) SessionHolder.save()
        return deleted
    }
}
