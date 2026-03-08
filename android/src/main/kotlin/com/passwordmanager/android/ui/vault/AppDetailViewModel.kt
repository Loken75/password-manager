package com.passwordmanager.android.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.AppEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppDetailUiState(
    val entry: AppEntry? = null,
    val pinVisible: Boolean = false
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val sessionHolder: SessionHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    fun loadEntry(entryId: String) {
        val appService = sessionHolder.vaultService?.getAppService() ?: return
        val entry = appService.search("").find { it.id == entryId }
        _uiState.value = AppDetailUiState(entry = entry)
    }

    fun togglePinVisibility() {
        _uiState.value = _uiState.value.copy(pinVisible = !_uiState.value.pinVisible)
    }

    fun toggleFavorite(entryId: String) {
        val appService = sessionHolder.vaultService?.getAppService() ?: return
        appService.toggleFavorite(entryId)
        viewModelScope.launch(Dispatchers.IO) {
            sessionHolder.save()
        }
        loadEntry(entryId)
    }

    fun duplicateEntry(entryId: String, prefix: String = "Copy of"): String? {
        val appService = sessionHolder.vaultService?.getAppService() ?: return null
        val entry = appService.search("").find { it.id == entryId } ?: return null
        val pinCopy = entry.pin
        try {
            val dup = AppEntry("$prefix ${entry.title}", entry.username, pinCopy, entry.notes)
            appService.addEntry(dup)
            viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
            return dup.id
        } finally {
            SecureWiper.wipe(pinCopy)
        }
    }

    fun deleteEntry(entryId: String): Boolean {
        val appService = sessionHolder.vaultService?.getAppService() ?: return false
        val deleted = appService.deleteEntry(entryId)
        if (deleted) sessionHolder.save()
        return deleted
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value = AppDetailUiState()
    }
}
