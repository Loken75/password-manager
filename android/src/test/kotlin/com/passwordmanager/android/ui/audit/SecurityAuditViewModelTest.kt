package com.passwordmanager.android.ui.audit

import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.test.MainDispatcherExtension
import com.passwordmanager.android.test.TestSessionHelper
import com.passwordmanager.vault.Vault
import com.passwordmanager.vault.VaultEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MainDispatcherExtension::class)
class SecurityAuditViewModelTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var vault: Vault

    @BeforeEach
    fun setUp() {
        vault = TestSessionHelper.unlockWithEmptyVault(tempDir)
    }

    @AfterEach
    fun tearDown() {
        SessionHolder.lock()
    }

    @Test
    fun `audit on empty vault has zero issues`() {
        val viewModel = SecurityAuditViewModel(SessionHolder)
        val state = viewModel.uiState.value
        assertEquals(0, state.totalIssues)
        assertTrue(state.weakEntries.isEmpty())
        assertTrue(state.duplicateEntries.isEmpty())
        assertTrue(state.oldEntries.isEmpty())
    }

    @Test
    fun `weak passwords are detected`() {
        val entry = VaultEntry("Weak", "u", "abc".toCharArray(), "", "", "Cat", null)
        SessionHolder.vaultService!!.addEntry(entry)
        val viewModel = SecurityAuditViewModel(SessionHolder)
        assertTrue(viewModel.uiState.value.weakEntries.isNotEmpty())
    }

    @Test
    fun `duplicate passwords are detected`() {
        SessionHolder.vaultService!!.addEntry(
            VaultEntry("Site1", "u", "SameP@ssword123!".toCharArray(), "", "", "Cat", null)
        )
        SessionHolder.vaultService!!.addEntry(
            VaultEntry("Site2", "u", "SameP@ssword123!".toCharArray(), "", "", "Cat", null)
        )
        val viewModel = SecurityAuditViewModel(SessionHolder)
        assertTrue(viewModel.uiState.value.duplicateEntries.isNotEmpty())
    }

    @Test
    fun `old passwords are detected`() {
        val old = VaultEntry("Old", "u", "p@ssW0rd!123".toCharArray(), "", "", "Cat", null)
        // Use a fixed ISO 8601 timestamp well in the past (200+ days)
        old.updatedAt = "2024-01-01T00:00:00Z"
        vault.addEntry(old)

        val viewModel = SecurityAuditViewModel(SessionHolder)
        assertTrue(viewModel.uiState.value.oldEntries.isNotEmpty())
    }

    @Test
    fun `totalIssues sums all categories`() {
        // Add weak
        SessionHolder.vaultService!!.addEntry(
            VaultEntry("Weak", "u", "abc".toCharArray(), "", "", "Cat", null)
        )
        // Add duplicates
        SessionHolder.vaultService!!.addEntry(
            VaultEntry("Dup1", "u", "SameP@ssword123!".toCharArray(), "", "", "Cat", null)
        )
        SessionHolder.vaultService!!.addEntry(
            VaultEntry("Dup2", "u", "SameP@ssword123!".toCharArray(), "", "", "Cat", null)
        )

        val viewModel = SecurityAuditViewModel(SessionHolder)
        val state = viewModel.uiState.value
        assertEquals(state.weakEntries.size + state.duplicateEntries.size + state.oldEntries.size, state.totalIssues)
        assertTrue(state.totalIssues > 0)
    }
}
