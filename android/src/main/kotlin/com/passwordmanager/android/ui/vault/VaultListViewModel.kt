package com.passwordmanager.android.ui.vault

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.SortField
import com.passwordmanager.vault.VaultEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultListUiState(
    val entries: List<VaultEntry> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val sortField: SortField = SortField.TITLE,
    val isSearchActive: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultListUiState())
    val uiState: StateFlow<VaultListUiState> = _uiState.asStateFlow()

    init {
        refreshEntries()
    }

    fun refreshEntries() {
        val service = sessionHolder.vaultService ?: return
        val vault = sessionHolder.vault ?: return

        val entries = when {
            _uiState.value.searchQuery.isNotBlank() ->
                service.search(_uiState.value.searchQuery)
            _uiState.value.selectedCategory != null ->
                service.getByCategory(_uiState.value.selectedCategory)
            else -> service.search("")
        }

        val sorted = service.sorted(entries, _uiState.value.sortField)

        _uiState.update {
            it.copy(entries = sorted, categories = vault.categories)
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

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
        refreshEntries()
    }

    fun setSortField(field: SortField) {
        _uiState.update { it.copy(sortField = field) }
        refreshEntries()
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@launch
                val vault = sessionHolder.vault ?: return@launch
                val count = sessionHolder.getRepository().importFromCsv(vault, content)
                sessionHolder.save()
                refreshEntries()
                _uiState.update { it.copy(message = "import_success:$count") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "import_error") }
            }
        }
    }

    fun importJson(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@launch
                val vault = sessionHolder.vault ?: return@launch
                val count = sessionHolder.getRepository().importFromJson(vault, content)
                sessionHolder.save()
                refreshEntries()
                _uiState.update { it.copy(message = "import_success:$count") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "import_error") }
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vault = sessionHolder.vault ?: return@launch
                val data = sessionHolder.getRepository().exportAsCsv(vault)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(String(data).toByteArray())
                }
                SecureWiper.wipe(data)
                _uiState.update { it.copy(message = "export_success") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "export_error") }
            }
        }
    }

    fun exportJson(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vault = sessionHolder.vault ?: return@launch
                val data = sessionHolder.getRepository().exportAsJson(vault)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(String(data).toByteArray())
                }
                SecureWiper.wipe(data)
                _uiState.update { it.copy(message = "export_success") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "export_error") }
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val username = sessionHolder.username ?: return@launch
                // Read the encrypted vault file and write to the SAF uri
                val vaultPath = sessionHolder.getRepository().manager.getVaultPath(username)
                val bytes = java.io.File(vaultPath).readBytes()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                _uiState.update { it.copy(message = "export_success") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "export_error") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
