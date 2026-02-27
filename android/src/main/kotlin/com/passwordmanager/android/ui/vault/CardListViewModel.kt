package com.passwordmanager.android.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.vault.CardEntry
import com.passwordmanager.vault.SortField
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardListUiState(
    val entries: List<CardEntry> = emptyList(),
    val searchQuery: String = "",
    val sortField: SortField = SortField.TITLE,
    val isSearchActive: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedEntryIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val message: String? = null,
    val refreshToken: Long = 0
)

@HiltViewModel
class CardListViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val configRepo: ConfigRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardListUiState())
    val uiState: StateFlow<CardListUiState> = _uiState.asStateFlow()

    init {
        refreshEntries()
    }

    fun refreshEntries() {
        val cardService = sessionHolder.vaultService?.getCardService() ?: return

        val entries = cardService.search(_uiState.value.searchQuery)

        val sorted = cardService.sorted(entries, _uiState.value.sortField)

        val filtered = if (_uiState.value.favoritesOnly) {
            sorted.filter { it.isFavorite }
        } else {
            sorted
        }

        _uiState.update {
            it.copy(entries = filtered, refreshToken = it.refreshToken + 1)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshEntries()
    }

    fun toggleSearch() {
        _uiState.update { it.copy(isSearchActive = !it.isSearchActive, searchQuery = "") }
        refreshEntries()
    }

    fun setSortField(field: SortField) {
        _uiState.update { it.copy(sortField = field) }
        refreshEntries()
    }

    fun toggleFavoritesFilter() {
        _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
        refreshEntries()
    }

    fun toggleFavorite(entryId: String) {
        val cardService = sessionHolder.vaultService?.getCardService() ?: return
        cardService.toggleFavorite(entryId)
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        refreshEntries()
    }

    fun deleteEntry(entryId: String) {
        val cardService = sessionHolder.vaultService?.getCardService() ?: return
        if (cardService.deleteEntry(entryId)) {
            viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
            refreshEntries()
        }
    }

    fun copyCardNumberForEntry(entryId: String) {
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        val cardNumber = entry.cardNumber ?: return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", String(cardNumber))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
        com.passwordmanager.util.SecureWiper.wipe(cardNumber)
        _uiState.update { it.copy(message = "card_number_copied") }

        val clearDelay = configRepo.getClipboardClearSeconds() * 1000L
        viewModelScope.launch {
            delay(clearDelay)
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // === Multi-select ===

    fun toggleSelection(entryId: String) {
        _uiState.update { state ->
            val newSet = state.selectedEntryIds.toMutableSet()
            if (newSet.contains(entryId)) newSet.remove(entryId) else newSet.add(entryId)
            state.copy(
                selectedEntryIds = newSet,
                isSelectionMode = newSet.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                selectedEntryIds = state.entries.map { it.id }.toSet(),
                isSelectionMode = true
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedEntryIds = emptySet(), isSelectionMode = false) }
    }

    fun bulkSetFavorite(favorite: Boolean) {
        val cardService = sessionHolder.vaultService?.getCardService() ?: return
        val selectedIds = _uiState.value.selectedEntryIds.toList()
        cardService.bulkSetFavorite(selectedIds, favorite)
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    fun bulkToggleFavorite() {
        val cardService = sessionHolder.vaultService?.getCardService() ?: return
        val selectedIds = _uiState.value.selectedEntryIds
        for (id in selectedIds) {
            cardService.toggleFavorite(id)
        }
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    fun bulkDelete() {
        val cardService = sessionHolder.vaultService?.getCardService() ?: return
        val selectedIds = _uiState.value.selectedEntryIds
        for (id in selectedIds) {
            cardService.deleteEntry(id)
        }
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    companion object {
        private const val TAG = "CardListViewModel"
    }
}
