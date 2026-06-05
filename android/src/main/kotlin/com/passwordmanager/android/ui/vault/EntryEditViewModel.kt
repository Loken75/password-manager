package com.passwordmanager.android.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.FaviconCache
import com.passwordmanager.android.data.FaviconRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.util.FaviconService
import com.passwordmanager.vault.PasswordEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryEditUiState(
    val title: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val category: String = "",
    val tags: String = "",
    val categories: List<String> = emptyList(),
    val favorite: Boolean = false,
    val isNew: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class EntryEditViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val faviconRepository: FaviconRepository,
    private val faviconCache: FaviconCache,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryEditUiState())
    val uiState: StateFlow<EntryEditUiState> = _uiState.asStateFlow()

    private var existingEntryId: String? = null
    private var isLoaded = false

    fun loadEntry(entryId: String?) {
        if (isLoaded) return
        isLoaded = true

        val vault = sessionHolder.vault ?: return
        val categories = vault.categories.sorted()

        if (entryId != null) {
            val entry = sessionHolder.vaultService?.search("")?.find { it.id == entryId }
            if (entry != null) {
                existingEntryId = entryId
                _uiState.value = EntryEditUiState(
                    title = entry.title ?: "",
                    username = entry.username ?: "",
                    email = entry.email ?: "",
                    password = entry.password?.let { String(it) } ?: "",
                    url = entry.url ?: "",
                    notes = entry.notes ?: "",
                    category = entry.category ?: "",
                    tags = entry.tags?.joinToString(", ") ?: "",
                    categories = categories,
                    favorite = entry.isFavorite,
                    isNew = false
                )
                return
            }
        }
        _uiState.value = EntryEditUiState(
            categories = categories,
            category = categories.find { it.equals("Other", ignoreCase = true) || it.equals("Autre", ignoreCase = true) } ?: categories.lastOrNull() ?: ""
        )
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value, error = null) }
    fun updateUsername(value: String) = _uiState.update { it.copy(username = value) }
    fun updateEmail(value: String) = _uiState.update { it.copy(email = value) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }
    fun updateUrl(value: String) = _uiState.update { it.copy(url = value) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun updateCategory(value: String) = _uiState.update { it.copy(category = value) }
    fun updateTags(value: String) = _uiState.update { it.copy(tags = value) }
    fun toggleFavorite() = _uiState.update { it.copy(favorite = !it.favorite) }

    fun setGeneratedPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.update { it.copy(password = "") }
    }

    fun save(): Boolean {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "title_required") }
            return false
        }

        val service = sessionHolder.vaultService ?: return false
        val tags = state.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (existingEntryId != null) {
            // Update existing entry
            val existing = service.search("").find { it.id == existingEntryId } ?: return false
            existing.title = state.title
            existing.username = state.username
            existing.email = state.email
            existing.password = state.password.toCharArray()
            existing.url = state.url
            existing.notes = state.notes
            existing.category = state.category
            existing.tags = tags
            existing.isFavorite = state.favorite
            service.updateEntry(existing)
        } else {
            // New entry
            val entry = PasswordEntry(
                state.title,
                state.username,
                state.email,
                state.password.toCharArray(),
                state.url,
                state.notes,
                state.category,
                tags
            )
            entry.isFavorite = state.favorite
            service.addEntry(entry)
        }

        sessionHolder.save()
        warmFavicon(state.url)
        return true
    }

    /**
     * Fetches the favicon for a saved URL over the network, warming the on-disk
     * cache so the list can show it later without any request at display time.
     * Fire-and-forget; failures are ignored. No-op when no URL is set.
     */
    private fun warmFavicon(url: String) {
        if (url.isBlank() || !configRepository.isFaviconsEnabled()) return
        val domain = FaviconService.extractDomain(url) ?: return
        viewModelScope.launch {
            runCatching {
                faviconRepository.getFavicon(url)?.let { faviconCache.put(domain, it) }
            }
        }
    }
}
