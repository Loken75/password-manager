package com.passwordmanager.android.ui.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.SortField
import com.passwordmanager.vault.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultListUiState(
    val entries: List<VaultEntry> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val sortField: SortField = SortField.TITLE,
    val isSearchActive: Boolean = false,
    val message: String? = null
)

class VaultListViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VaultListUiState())
    val uiState: StateFlow<VaultListUiState> = _uiState.asStateFlow()

    init {
        refreshEntries()
    }

    fun refreshEntries() {
        val service = SessionHolder.vaultService ?: return
        val vault = SessionHolder.vault ?: return

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
                val context = getApplication<Application>()
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@launch
                val vault = SessionHolder.vault ?: return@launch
                val count = SessionHolder.getRepository().importFromCsv(vault, content)
                SessionHolder.save()
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
                val context = getApplication<Application>()
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@launch
                val vault = SessionHolder.vault ?: return@launch
                val count = SessionHolder.getRepository().importFromJson(vault, content)
                SessionHolder.save()
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
                val vault = SessionHolder.vault ?: return@launch
                val data = SessionHolder.getRepository().exportAsCsv(vault)
                val context = getApplication<Application>()
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
                val vault = SessionHolder.vault ?: return@launch
                val data = SessionHolder.getRepository().exportAsJson(vault)
                val context = getApplication<Application>()
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
                val username = SessionHolder.username ?: return@launch
                val session = SessionHolder.session ?: return@launch
                val context = getApplication<Application>()
                // Read the encrypted vault file and write to the SAF uri
                val vaultPath = SessionHolder.getRepository().manager.getVaultPath(username)
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
