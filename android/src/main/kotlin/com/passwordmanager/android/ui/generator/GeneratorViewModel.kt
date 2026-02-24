package com.passwordmanager.android.ui.generator

import androidx.lifecycle.ViewModel
import com.passwordmanager.crypto.PasswordGenerator
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.util.SecureWiper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GeneratorUiState(
    val password: CharArray = charArrayOf(),
    val length: Int = 16,
    val useUppercase: Boolean = true,
    val useLowercase: Boolean = true,
    val useDigits: Boolean = true,
    val useSpecial: Boolean = true,
    val excludeAmbiguous: Boolean = false,
    val strength: PasswordStrengthAnalyzer.Strength = PasswordStrengthAnalyzer.Strength.WEAK,
    val score: Int = 0
) {
    // Equals/hashCode that handles char[] correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratorUiState) return false
        return password.contentEquals(other.password) &&
                length == other.length &&
                useUppercase == other.useUppercase &&
                useLowercase == other.useLowercase &&
                useDigits == other.useDigits &&
                useSpecial == other.useSpecial &&
                excludeAmbiguous == other.excludeAmbiguous
    }

    override fun hashCode(): Int = password.contentHashCode() + length
}

class GeneratorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GeneratorUiState())
    val uiState: StateFlow<GeneratorUiState> = _uiState.asStateFlow()

    init {
        generate()
    }

    fun generate() {
        val state = _uiState.value
        val oldPassword = state.password
        val newPassword = PasswordGenerator.generate(
            state.length, state.useUppercase, state.useLowercase,
            state.useDigits, state.useSpecial, state.excludeAmbiguous
        )
        _uiState.update {
            it.copy(
                password = newPassword,
                strength = PasswordStrengthAnalyzer.analyze(newPassword),
                score = PasswordStrengthAnalyzer.getScore(newPassword)
            )
        }
        SecureWiper.wipe(oldPassword)
    }

    fun setLength(length: Int) {
        _uiState.update { it.copy(length = length.coerceIn(8, 128)) }
        generate()
    }

    fun toggleUppercase() {
        _uiState.update { it.copy(useUppercase = !it.useUppercase) }
        generate()
    }

    fun toggleLowercase() {
        _uiState.update { it.copy(useLowercase = !it.useLowercase) }
        generate()
    }

    fun toggleDigits() {
        _uiState.update { it.copy(useDigits = !it.useDigits) }
        generate()
    }

    fun toggleSpecial() {
        _uiState.update { it.copy(useSpecial = !it.useSpecial) }
        generate()
    }

    fun toggleExcludeAmbiguous() {
        _uiState.update { it.copy(excludeAmbiguous = !it.excludeAmbiguous) }
        generate()
    }

    override fun onCleared() {
        SecureWiper.wipe(_uiState.value.password)
        super.onCleared()
    }
}
