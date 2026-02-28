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
import kotlin.test.assertTrue

@ExtendWith(MainDispatcherExtension::class)
class EntryEditViewModelTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var viewModel: EntryEditViewModel
    private lateinit var vault: Vault

    @BeforeEach
    fun setUp() {
        vault = TestSessionHelper.unlockWithEmptyVault(tempDir)
        viewModel = EntryEditViewModel(SessionHolder)
    }

    @AfterEach
    fun tearDown() {
        SessionHolder.lock()
    }

    @Test
    fun `loadEntry null id creates new entry form`() {
        viewModel.loadEntry(null)
        val state = viewModel.uiState.value
        assertTrue(state.isNew)
        assertTrue(state.categories.isNotEmpty())
    }

    @Test
    fun `loadEntry existing entry populates form`() {
        val entry = PasswordEntry("Gmail", "user", "pass123".toCharArray(), "https://gmail.com", "notes", "Email", listOf("tag1"))
        SessionHolder.vaultService!!.addEntry(entry)

        viewModel.loadEntry(entry.id)
        val state = viewModel.uiState.value
        assertFalse(state.isNew)
        assertEquals("Gmail", state.title)
        assertEquals("user", state.username)
        assertEquals("pass123", state.password)
        assertEquals("https://gmail.com", state.url)
        assertEquals("notes", state.notes)
        assertEquals("Email", state.category)
        assertEquals("tag1", state.tags)
    }

    @Test
    fun `updateTitle clears error`() {
        viewModel.loadEntry(null)
        // Trigger error first
        viewModel.save()
        assertEquals("title_required", viewModel.uiState.value.error)
        viewModel.updateTitle("New Title")
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals("New Title", viewModel.uiState.value.title)
    }

    @Test
    fun `update fields`() {
        viewModel.loadEntry(null)
        viewModel.updateUsername("user1")
        assertEquals("user1", viewModel.uiState.value.username)
        viewModel.updatePassword("pass1")
        assertEquals("pass1", viewModel.uiState.value.password)
        viewModel.updateUrl("http://test.com")
        assertEquals("http://test.com", viewModel.uiState.value.url)
        viewModel.updateNotes("some notes")
        assertEquals("some notes", viewModel.uiState.value.notes)
        viewModel.updateCategory("Work")
        assertEquals("Work", viewModel.uiState.value.category)
        viewModel.updateTags("t1, t2")
        assertEquals("t1, t2", viewModel.uiState.value.tags)
    }

    @Test
    fun `save new entry returns true`() {
        viewModel.loadEntry(null)
        viewModel.updateTitle("New Entry")
        viewModel.updatePassword("mypassword")
        assertTrue(viewModel.save())
        assertEquals(1, vault.getEntries().size)
    }

    @Test
    fun `save existing entry returns true`() {
        val entry = PasswordEntry("Original", "u", "p".toCharArray(), "", "", "Email", null)
        SessionHolder.vaultService!!.addEntry(entry)
        viewModel.loadEntry(entry.id)
        viewModel.updateTitle("Updated Title")
        assertTrue(viewModel.save())
    }

    @Test
    fun `save with empty title fails`() {
        viewModel.loadEntry(null)
        viewModel.updateTitle("   ")
        assertFalse(viewModel.save())
        assertEquals("title_required", viewModel.uiState.value.error)
    }

    @Test
    fun `setGeneratedPassword updates state`() {
        viewModel.loadEntry(null)
        viewModel.setGeneratedPassword("generated123!")
        assertEquals("generated123!", viewModel.uiState.value.password)
    }

    @Test
    fun `toggleFavorite toggles state`() {
        viewModel.loadEntry(null)
        assertFalse(viewModel.uiState.value.favorite)
        viewModel.toggleFavorite()
        assertTrue(viewModel.uiState.value.favorite)
        viewModel.toggleFavorite()
        assertFalse(viewModel.uiState.value.favorite)
    }
}
