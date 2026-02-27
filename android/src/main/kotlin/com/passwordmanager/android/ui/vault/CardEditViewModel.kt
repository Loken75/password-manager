package com.passwordmanager.android.ui.vault

import androidx.lifecycle.ViewModel
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.CardEntry
import com.passwordmanager.vault.CardType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class CardEditUiState(
    val title: String = "",
    val cardholderName: String = "",
    val cardNumber: String = "",
    val expiryDate: String = "",
    val cvv: String = "",
    val cardPin: String = "",
    val cardType: String = CardType.VISA,
    val notes: String = "",
    val favorite: Boolean = false,
    val isNew: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CardEditViewModel @Inject constructor(
    private val sessionHolder: SessionHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardEditUiState())
    val uiState: StateFlow<CardEditUiState> = _uiState.asStateFlow()

    private var existingEntryId: String? = null

    fun loadEntry(entryId: String?) {
        if (entryId != null) {
            val cardService = sessionHolder.vaultService?.getCardService() ?: return
            val entry = cardService.search("").find { it.id == entryId }
            if (entry != null) {
                existingEntryId = entryId
                val cardNumberChars = entry.cardNumber
                val cvvChars = entry.cvv
                val cardPinChars = entry.cardPin
                _uiState.value = CardEditUiState(
                    title = entry.title ?: "",
                    cardholderName = entry.cardholderName ?: "",
                    cardNumber = cardNumberChars?.let { String(it) } ?: "",
                    expiryDate = entry.expiryDate ?: "",
                    cvv = cvvChars?.let { String(it) } ?: "",
                    cardPin = cardPinChars?.let { String(it) } ?: "",
                    cardType = CardType.normalize(entry.cardType ?: ""),
                    notes = entry.notes ?: "",
                    favorite = entry.isFavorite,
                    isNew = false
                )
                if (cardNumberChars != null) SecureWiper.wipe(cardNumberChars)
                if (cvvChars != null) SecureWiper.wipe(cvvChars)
                if (cardPinChars != null) SecureWiper.wipe(cardPinChars)
                return
            }
        }
        _uiState.value = CardEditUiState()
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value, error = null) }
    fun updateCardholderName(value: String) = _uiState.update { it.copy(cardholderName = value) }
    fun updateCardNumber(value: String) = _uiState.update { it.copy(cardNumber = value) }
    fun updateExpiryDate(value: String) = _uiState.update { it.copy(expiryDate = value) }
    fun updateCvv(value: String) = _uiState.update { it.copy(cvv = value) }
    fun updateCardPin(value: String) = _uiState.update { it.copy(cardPin = value) }
    fun updateCardType(value: String) = _uiState.update { it.copy(cardType = value) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun toggleFavorite() = _uiState.update { it.copy(favorite = !it.favorite) }

    override fun onCleared() {
        super.onCleared()
        _uiState.update { it.copy(cardNumber = "", cvv = "", cardPin = "") }
    }

    fun save(): Boolean {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "title_required") }
            return false
        }

        val cardService = sessionHolder.vaultService?.getCardService() ?: return false

        if (existingEntryId != null) {
            val existing = cardService.search("").find { it.id == existingEntryId } ?: return false
            existing.title = state.title
            existing.cardholderName = state.cardholderName
            existing.cardNumber = state.cardNumber.toCharArray()
            existing.expiryDate = state.expiryDate
            existing.cvv = state.cvv.toCharArray()
            existing.cardPin = state.cardPin.toCharArray()
            existing.cardType = state.cardType
            existing.notes = state.notes
            existing.isFavorite = state.favorite
            cardService.updateEntry(existing)
        } else {
            val entry = CardEntry(
                state.title,
                state.cardholderName,
                state.cardNumber.toCharArray(),
                state.expiryDate,
                state.cvv.toCharArray(),
                state.cardPin.toCharArray(),
                state.cardType,
                state.notes
            )
            entry.isFavorite = state.favorite
            cardService.addEntry(entry)
        }

        sessionHolder.save()
        return true
    }
}
