package com.passwordmanager.android.ui.vault

import androidx.lifecycle.ViewModel
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.vault.VaultEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EntryEditUiState(
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val category: String = "",
    val tags: String = "",
    val categories: List<String> = emptyList(),
    val isNew: Boolean = true,
    val error: String? = null
)

class EntryEditViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EntryEditUiState())
    val uiState: StateFlow<EntryEditUiState> = _uiState.asStateFlow()

    private var existingEntryId: String? = null

    fun loadEntry(entryId: String?) {
        val vault = SessionHolder.vault ?: return
        val categories = vault.categories

        if (entryId != null) {
            val entry = SessionHolder.vaultService?.search("")?.find { it.id == entryId }
            if (entry != null) {
                existingEntryId = entryId
                _uiState.value = EntryEditUiState(
                    title = entry.title ?: "",
                    username = entry.username ?: "",
                    password = entry.password?.let { String(it) } ?: "",
                    url = entry.url ?: "",
                    notes = entry.notes ?: "",
                    category = entry.category ?: "",
                    tags = entry.tags?.joinToString(", ") ?: "",
                    categories = categories,
                    isNew = false
                )
                return
            }
        }
        _uiState.value = EntryEditUiState(
            categories = categories,
            category = categories.firstOrNull() ?: ""
        )
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value, error = null) }
    fun updateUsername(value: String) = _uiState.update { it.copy(username = value) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }
    fun updateUrl(value: String) = _uiState.update { it.copy(url = value) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun updateCategory(value: String) = _uiState.update { it.copy(category = value) }
    fun updateTags(value: String) = _uiState.update { it.copy(tags = value) }

    fun setGeneratedPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun save(): Boolean {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "title_required") }
            return false
        }

        val service = SessionHolder.vaultService ?: return false
        val tags = state.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (existingEntryId != null) {
            // Update existing entry
            val existing = service.search("").find { it.id == existingEntryId } ?: return false
            existing.title = state.title
            existing.username = state.username
            existing.password = state.password.toCharArray()
            existing.url = state.url
            existing.notes = state.notes
            existing.category = state.category
            existing.tags = tags
            service.updateEntry(existing)
        } else {
            // New entry
            val entry = VaultEntry(
                state.title,
                state.username,
                state.password.toCharArray(),
                state.url,
                state.notes,
                state.category,
                tags
            )
            service.addEntry(entry)
        }

        SessionHolder.save()
        return true
    }
}
