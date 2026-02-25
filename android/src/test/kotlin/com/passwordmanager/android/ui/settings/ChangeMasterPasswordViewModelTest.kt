package com.passwordmanager.android.ui.settings

import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.test.MainDispatcherExtension
import com.passwordmanager.android.test.TestSessionHelper
import com.passwordmanager.vault.Vault
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MainDispatcherExtension::class)
class ChangeMasterPasswordViewModelTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var vault: Vault
    private lateinit var viewModel: ChangeMasterPasswordViewModel

    @BeforeEach
    fun setUp() {
        vault = TestSessionHelper.unlockWithEmptyVault(tempDir)
        viewModel = ChangeMasterPasswordViewModel(SessionHolder)
    }

    @AfterEach
    fun tearDown() {
        SessionHolder.lock()
    }

    @Test
    fun `initial state is empty`() {
        val state = viewModel.uiState.value
        assertEquals("", state.oldPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.success)
    }

    @Test
    fun `updateOldPassword updates state and clears error`() {
        viewModel.updateOldPassword("old123")
        assertEquals("old123", viewModel.uiState.value.oldPassword)
    }

    @Test
    fun `updateNewPassword updates state and clears error`() {
        viewModel.updateNewPassword("new123")
        assertEquals("new123", viewModel.uiState.value.newPassword)
    }

    @Test
    fun `updateConfirmPassword updates state and clears error`() {
        viewModel.updateConfirmPassword("confirm123")
        assertEquals("confirm123", viewModel.uiState.value.confirmPassword)
    }

    @Test
    fun `changePassword rejects mismatched passwords`() {
        viewModel.updateOldPassword("OldP@ssw0rd!123")
        viewModel.updateNewPassword("NewP@ssw0rd!123")
        viewModel.updateConfirmPassword("Different!123")
        viewModel.changePassword()
        assertEquals("security_password_mismatch", viewModel.uiState.value.error)
    }

    @Test
    fun `changePassword rejects weak new password`() {
        viewModel.updateOldPassword("OldP@ssw0rd!123")
        viewModel.updateNewPassword("weak")
        viewModel.updateConfirmPassword("weak")
        viewModel.changePassword()
        assertEquals("security_password_requirements", viewModel.uiState.value.error)
    }

    @Test
    fun `onCleared clears passwords from state`() {
        viewModel.updateOldPassword("secret1")
        viewModel.updateNewPassword("secret2")
        viewModel.updateConfirmPassword("secret3")

        val method = viewModel.javaClass.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)

        val state = viewModel.uiState.value
        assertEquals("", state.oldPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
    }
}
