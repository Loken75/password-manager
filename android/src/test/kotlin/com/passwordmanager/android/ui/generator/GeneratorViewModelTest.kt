package com.passwordmanager.android.ui.generator

import com.passwordmanager.android.test.MainDispatcherExtension
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@ExtendWith(MainDispatcherExtension::class)
class GeneratorViewModelTest {

    private lateinit var viewModel: GeneratorViewModel

    @BeforeEach
    fun setUp() {
        viewModel = GeneratorViewModel()
    }

    @Test
    fun `initial state has generated password`() {
        val state = viewModel.uiState.value
        assertTrue(state.password.isNotEmpty())
        assertEquals(16, state.length)
        assertTrue(state.useUppercase)
        assertTrue(state.useLowercase)
        assertTrue(state.useDigits)
        assertTrue(state.useSpecial)
    }

    @Test
    fun `generate produces new password`() {
        val first = String(viewModel.uiState.value.password)
        viewModel.generate()
        val second = String(viewModel.uiState.value.password)
        assertTrue(second.isNotEmpty())
        // Two random 16-char passwords are extremely unlikely to be identical
        assertNotEquals(first, second)
    }

    @Test
    fun `setLength clamps to minimum 8`() {
        viewModel.setLength(3)
        assertEquals(8, viewModel.uiState.value.length)
    }

    @Test
    fun `setLength clamps to maximum 128`() {
        viewModel.setLength(200)
        assertEquals(128, viewModel.uiState.value.length)
    }

    @Test
    fun `setLength valid value`() {
        viewModel.setLength(32)
        assertEquals(32, viewModel.uiState.value.length)
    }

    @Test
    fun `toggleUppercase flips state`() {
        assertTrue(viewModel.uiState.value.useUppercase)
        viewModel.toggleUppercase()
        assertEquals(false, viewModel.uiState.value.useUppercase)
        viewModel.toggleUppercase()
        assertEquals(true, viewModel.uiState.value.useUppercase)
    }

    @Test
    fun `toggleLowercase flips state`() {
        assertTrue(viewModel.uiState.value.useLowercase)
        viewModel.toggleLowercase()
        assertEquals(false, viewModel.uiState.value.useLowercase)
    }

    @Test
    fun `toggleDigits flips state`() {
        assertTrue(viewModel.uiState.value.useDigits)
        viewModel.toggleDigits()
        assertEquals(false, viewModel.uiState.value.useDigits)
    }

    @Test
    fun `toggleSpecial flips state`() {
        assertTrue(viewModel.uiState.value.useSpecial)
        viewModel.toggleSpecial()
        assertEquals(false, viewModel.uiState.value.useSpecial)
    }

    @Test
    fun `strength is computed`() {
        viewModel.setLength(32)
        val state = viewModel.uiState.value
        assertNotEquals(PasswordStrengthAnalyzer.Strength.WEAK, state.strength)
    }

    @Test
    fun `onCleared wipes password`() {
        // Access onCleared via reflection since it's protected
        val method = viewModel.javaClass.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        // After clearing, the password in state should be wiped (all zeros)
        val password = viewModel.uiState.value.password
        assertTrue(password.all { it == '\u0000' })
    }
}
