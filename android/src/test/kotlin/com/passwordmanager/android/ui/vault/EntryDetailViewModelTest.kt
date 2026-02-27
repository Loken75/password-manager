package com.passwordmanager.android.ui.vault

import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.test.MainDispatcherExtension
import com.passwordmanager.android.test.TestSessionHelper
import com.passwordmanager.vault.Vault
import com.passwordmanager.vault.PasswordEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MainDispatcherExtension::class)
class EntryDetailViewModelTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var viewModel: EntryDetailViewModel
    private lateinit var vault: Vault

    @BeforeEach
    fun setUp() {
        vault = TestSessionHelper.unlockWithEmptyVault(tempDir)
        viewModel = EntryDetailViewModel(SessionHolder)
    }

    @AfterEach
    fun tearDown() {
        SessionHolder.lock()
    }

    @Test
    fun `loadEntry existing entry`() {
        val entry = PasswordEntry("Gmail", "user@gmail.com", "pass".toCharArray(), "", "", "Email", null)
        SessionHolder.vaultService!!.addEntry(entry)

        viewModel.loadEntry(entry.id)
        val state = viewModel.uiState.value
        assertNotNull(state.entry)
        assertEquals("Gmail", state.entry!!.title)
    }

    @Test
    fun `loadEntry unknown id sets null`() {
        viewModel.loadEntry("non-existent-id")
        assertNull(viewModel.uiState.value.entry)
    }

    @Test
    fun `togglePasswordVisibility flips state`() {
        assertFalse(viewModel.uiState.value.passwordVisible)
        viewModel.togglePasswordVisibility()
        assertTrue(viewModel.uiState.value.passwordVisible)
        viewModel.togglePasswordVisibility()
        assertFalse(viewModel.uiState.value.passwordVisible)
    }

    @Test
    fun `deleteEntry returns true for existing`() {
        val entry = PasswordEntry("ToDelete", "u", "p".toCharArray(), "", "", "Cat", null)
        SessionHolder.vaultService!!.addEntry(entry)
        assertTrue(viewModel.deleteEntry(entry.id))
    }

    @Test
    fun `deleteEntry returns false for non-existent`() {
        assertFalse(viewModel.deleteEntry("non-existent-id"))
    }
}
