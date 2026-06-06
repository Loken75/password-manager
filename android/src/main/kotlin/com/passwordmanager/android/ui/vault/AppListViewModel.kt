package com.passwordmanager.android.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.vault.AppEntry
import com.passwordmanager.vault.AppService
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

data class AppListUiState(
    val entries: List<AppEntry> = emptyList(),
    val searchQuery: String = "",
    val sortField: SortField = SortField.TITLE,
    val isSearchActive: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedEntryIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val createdSince: java.time.LocalDate? = null,
    val modifiedSince: java.time.LocalDate? = null,
    val createdOn: java.time.LocalDate? = null,
    val modifiedOn: java.time.LocalDate? = null,
    val message: String? = null,
    val refreshToken: Long = 0
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val configRepo: ConfigRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        refreshEntries()
    }

    private fun getAppService(): AppService? =
        sessionHolder.vaultService?.getAppService()

    fun refreshEntries() {
        val appService = getAppService() ?: return

        val entries = appService.search(_uiState.value.searchQuery)

        val sorted = appService.sorted(entries, _uiState.value.sortField)

        val filtered = if (_uiState.value.favoritesOnly) {
            sorted.filter { it.isFavorite }
        } else {
            sorted
        }

        val dateFiltered = applyDateFilters(filtered)

        _uiState.update {
            it.copy(entries = dateFiltered, refreshToken = it.refreshToken + 1)
        }
    }

    private fun applyDateFilters(list: List<AppEntry>): List<AppEntry> {
        val s = _uiState.value
        if (s.createdSince == null && s.modifiedSince == null && s.createdOn == null && s.modifiedOn == null) return list
        return list.filter { e ->
            val c = dateOf(e.createdAt)
            val m = dateOf(e.updatedAt)
            when {
                s.createdSince != null && (c == null || c.isBefore(s.createdSince)) -> false
                s.modifiedSince != null && (m == null || m.isBefore(s.modifiedSince)) -> false
                s.createdOn != null && c != s.createdOn -> false
                s.modifiedOn != null && m != s.modifiedOn -> false
                else -> true
            }
        }
    }

    private fun dateOf(iso: String?): java.time.LocalDate? {
        if (iso == null || iso.length < 10) return null
        return try { java.time.LocalDate.parse(iso.substring(0, 10)) } catch (e: Exception) { null }
    }

    fun setCreatedSince(d: java.time.LocalDate?) { _uiState.update { it.copy(createdSince = d) }; refreshEntries() }
    fun setModifiedSince(d: java.time.LocalDate?) { _uiState.update { it.copy(modifiedSince = d) }; refreshEntries() }
    fun setCreatedOn(d: java.time.LocalDate?) { _uiState.update { it.copy(createdOn = d) }; refreshEntries() }
    fun setModifiedOn(d: java.time.LocalDate?) { _uiState.update { it.copy(modifiedOn = d) }; refreshEntries() }

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
        val appService = getAppService() ?: return
        appService.toggleFavorite(entryId)
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        refreshEntries()
    }

    fun deleteEntry(entryId: String) {
        val appService = getAppService() ?: return
        if (appService.deleteEntry(entryId)) {
            viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
            refreshEntries()
        }
    }

    fun copyPinForEntry(entryId: String) {
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        val pin = entry.pin ?: return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", String(pin))
        com.passwordmanager.util.SecureWiper.wipe(pin)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(message = "pin_copied") }

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
        val appService = getAppService() ?: return
        val selectedIds = _uiState.value.selectedEntryIds.toList()
        appService.bulkSetFavorite(selectedIds, favorite)
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    fun bulkToggleFavorite() {
        val appService = getAppService() ?: return
        val selectedIds = _uiState.value.selectedEntryIds
        for (id in selectedIds) {
            appService.toggleFavorite(id)
        }
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    fun bulkDelete() {
        val appService = getAppService() ?: return
        val selectedIds = _uiState.value.selectedEntryIds
        for (id in selectedIds) {
            appService.deleteEntry(id)
        }
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    companion object {
        private const val TAG = "AppListViewModel"
    }
}
