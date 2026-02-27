package com.passwordmanager.android.ui.vault

import androidx.lifecycle.ViewModel
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.vault.AppEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class AppEditUiState(
    val title: String = "",
    val username: String = "",
    val pin: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
    val isNew: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AppEditViewModel @Inject constructor(
    private val sessionHolder: SessionHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppEditUiState())
    val uiState: StateFlow<AppEditUiState> = _uiState.asStateFlow()

    private var existingEntryId: String? = null

    fun loadEntry(entryId: String?) {
        if (entryId != null) {
            val appService = sessionHolder.vaultService?.getAppService() ?: return
            val entry = appService.search("").find { it.id == entryId }
            if (entry != null) {
                existingEntryId = entryId
                val pinChars = entry.pin
                _uiState.value = AppEditUiState(
                    title = entry.title ?: "",
                    username = entry.username ?: "",
                    pin = pinChars?.let { String(it) } ?: "",
                    notes = entry.notes ?: "",
                    favorite = entry.isFavorite,
                    isNew = false
                )
                if (pinChars != null) com.passwordmanager.util.SecureWiper.wipe(pinChars)
                return
            }
        }
        _uiState.value = AppEditUiState()
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value, error = null) }
    fun updateUsername(value: String) = _uiState.update { it.copy(username = value) }
    fun updatePin(value: String) = _uiState.update { it.copy(pin = value) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun toggleFavorite() = _uiState.update { it.copy(favorite = !it.favorite) }

    override fun onCleared() {
        super.onCleared()
        _uiState.update { it.copy(pin = "") }
    }

    fun save(): Boolean {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "title_required") }
            return false
        }

        val appService = sessionHolder.vaultService?.getAppService() ?: return false

        if (existingEntryId != null) {
            val existing = appService.search("").find { it.id == existingEntryId } ?: return false
            existing.title = state.title
            existing.username = state.username
            existing.setPin(state.pin.toCharArray())
            existing.notes = state.notes
            existing.isFavorite = state.favorite
            appService.updateEntry(existing)
        } else {
            val entry = AppEntry(
                state.title,
                state.username,
                state.pin.toCharArray(),
                state.notes
            )
            entry.isFavorite = state.favorite
            appService.addEntry(entry)
        }

        sessionHolder.save()
        return true
    }
}
