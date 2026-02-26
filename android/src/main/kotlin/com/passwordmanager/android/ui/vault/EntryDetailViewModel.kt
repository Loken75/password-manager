package com.passwordmanager.android.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.vault.VaultEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryDetailUiState(
    val entry: VaultEntry? = null,
    val passwordVisible: Boolean = false
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
        _uiState.value = EntryDetailUiState(entry = entry)
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

    fun deleteEntry(entryId: String): Boolean {
        val deleted = sessionHolder.vaultService?.deleteEntry(entryId) ?: false
        if (deleted) sessionHolder.save()
        return deleted
    }
}
