package com.passwordmanager.android.ui.settings

import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.test.FakeBiometricHelper
import com.passwordmanager.android.test.FakeConfigRepository
import com.passwordmanager.android.test.FakeWorkspaceManager
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
    private lateinit var configRepo: FakeConfigRepository
    private lateinit var biometricHelper: FakeBiometricHelper
    private lateinit var viewModel: ChangeMasterPasswordViewModel

    @BeforeEach
    fun setUp() {
        configRepo = FakeConfigRepository()
        biometricHelper = FakeBiometricHelper()
        vault = TestSessionHelper.unlockWithEmptyVault(tempDir)
        viewModel = ChangeMasterPasswordViewModel(SessionHolder, biometricHelper, configRepo, FakeWorkspaceManager(tempDir.toString()))
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

    @Test
    fun `changePassword invalidates biometric data`() {
        // Simulate biometric enrollment
        configRepo.setBiometricEnabled("testuser", true)
        configRepo.setBiometricEncryptedPassword("testuser", byteArrayOf(1, 2, 3))
        configRepo.setBiometricIv("testuser", byteArrayOf(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))

        viewModel.updateOldPassword("TestP@ssw0rd!123")
        viewModel.updateNewPassword("NewStr0ng!P@ss456")
        viewModel.updateConfirmPassword("NewStr0ng!P@ss456")

        viewModel.changePassword()

        // Wait for coroutine to complete
        Thread.sleep(2000)

        // Biometric data should be cleared proactively
        assertFalse(configRepo.isBiometricEnabled("testuser"))
        assertNull(configRepo.getBiometricEncryptedPassword("testuser"))
        assertNull(configRepo.getBiometricIv("testuser"))
        assertTrue(biometricHelper.deletedKeys.contains("testuser"))
    }

    @Test
    fun `changePassword does not touch biometric if not enabled`() {
        viewModel.updateOldPassword("TestP@ssw0rd!123")
        viewModel.updateNewPassword("NewStr0ng!P@ss456")
        viewModel.updateConfirmPassword("NewStr0ng!P@ss456")

        viewModel.changePassword()

        // Wait for coroutine
        Thread.sleep(2000)

        assertFalse(configRepo.isBiometricEnabled("testuser"))
        assertTrue(biometricHelper.deletedKeys.isEmpty())
    }
}
