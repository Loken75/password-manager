package com.passwordmanager.android.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.vault.CardEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardDetailUiState(
    val entry: CardEntry? = null,
    val cardNumberVisible: Boolean = false,
    val cvvVisible: Boolean = false,
    val pinVisible: Boolean = false
)

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val sessionHolder: SessionHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    fun loadEntry(entryId: String) {
        val cardService = sessionHolder.vaultService?.getCardService() ?: return
        val entry = cardService.search("").find { it.id == entryId }
        _uiState.value = CardDetailUiState(entry = entry)
    }

    fun toggleCardNumberVisibility() {
        _uiState.value = _uiState.value.copy(cardNumberVisible = !_uiState.value.cardNumberVisible)
    }

    fun toggleCvvVisibility() {
        _uiState.value = _uiState.value.copy(cvvVisible = !_uiState.value.cvvVisible)
    }

    fun togglePinVisibility() {
        _uiState.value = _uiState.value.copy(pinVisible = !_uiState.value.pinVisible)
    }

    fun toggleFavorite(entryId: String) {
        val cardService = sessionHolder.vaultService?.getCardService() ?: return
        cardService.toggleFavorite(entryId)
        viewModelScope.launch(Dispatchers.IO) {
            sessionHolder.save()
        }
        loadEntry(entryId)
    }

    fun deleteEntry(entryId: String): Boolean {
        val cardService = sessionHolder.vaultService?.getCardService() ?: return false
        val deleted = cardService.deleteEntry(entryId)
        if (deleted) sessionHolder.save()
        return deleted
    }
}
