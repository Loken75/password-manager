package com.passwordmanager.android.ui.settings

import com.passwordmanager.android.data.AndroidVaultRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.data.SshHostKeyStore
import java.io.File
import com.passwordmanager.android.test.FakeBiometricHelper
import com.passwordmanager.android.test.FakeConfigRepository
import com.passwordmanager.android.test.FakeWorkspaceManager
import com.passwordmanager.android.test.MainDispatcherExtension
import com.passwordmanager.android.test.TestSessionHelper
import com.passwordmanager.config.ThemeMode
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
class SettingsViewModelTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var configRepo: FakeConfigRepository
    private lateinit var biometricHelper: FakeBiometricHelper
    private lateinit var hostKeyStore: SshHostKeyStore
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        configRepo = FakeConfigRepository()
        TestSessionHelper.unlockWithEmptyVault(tempDir)
        biometricHelper = FakeBiometricHelper()
        hostKeyStore = SshHostKeyStore(File(tempDir.toFile(), "ssh_known_hosts"))
        viewModel = SettingsViewModel(configRepo, biometricHelper, SessionHolder, hostKeyStore, FakeWorkspaceManager(tempDir.toString()))
    }

    @AfterEach
    fun tearDown() {
        SessionHolder.lock()
    }

    @Test
    fun `init loads config values`() {
        val state = viewModel.uiState.value
        assertEquals("en", state.language)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(15, state.autoLockMinutes)
        assertEquals(30, state.clipboardClearSeconds)
    }

    @Test
    fun `setTheme updates state and repo`() {
        viewModel.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        assertEquals(ThemeMode.DARK, configRepo.getThemeMode())
    }

    @Test
    fun `setLanguage updates state and repo`() {
        viewModel.setLanguage("fr")
        assertEquals("fr", viewModel.uiState.value.language)
        assertEquals("fr", configRepo.getLanguage())
    }

    @Test
    fun `setAutoLockMinutes clamps and updates`() {
        viewModel.setAutoLockMinutes(30)
        assertEquals(30, viewModel.uiState.value.autoLockMinutes)
        assertEquals(30, configRepo.getAutoLockMinutes())

        // Clamp to min
        viewModel.setAutoLockMinutes(0)
        assertEquals(1, viewModel.uiState.value.autoLockMinutes)

        // Clamp to max
        viewModel.setAutoLockMinutes(100)
        assertEquals(60, viewModel.uiState.value.autoLockMinutes)
    }

    @Test
    fun `setClipboardClearSeconds clamps and updates`() {
        viewModel.setClipboardClearSeconds(60)
        assertEquals(60, viewModel.uiState.value.clipboardClearSeconds)
        assertEquals(60, configRepo.getClipboardClearSeconds())

        // Clamp to min
        viewModel.setClipboardClearSeconds(1)
        assertEquals(5, viewModel.uiState.value.clipboardClearSeconds)

        // Clamp to max
        viewModel.setClipboardClearSeconds(200)
        assertEquals(120, viewModel.uiState.value.clipboardClearSeconds)
    }

    // --- Biometric toggle ---

    @Test
    fun `biometric unavailable hides toggle`() {
        biometricHelper.available = false
        viewModel = SettingsViewModel(configRepo, biometricHelper, SessionHolder, hostKeyStore, FakeWorkspaceManager(tempDir.toString()))
        assertFalse(viewModel.uiState.value.biometricAvailable)
    }

    @Test
    fun `biometric available shows toggle`() {
        biometricHelper.available = true
        viewModel = SettingsViewModel(configRepo, biometricHelper, SessionHolder, hostKeyStore, FakeWorkspaceManager(tempDir.toString()))
        assertTrue(viewModel.uiState.value.biometricAvailable)
    }

    @Test
    fun `biometric disabled by default`() {
        assertFalse(viewModel.uiState.value.biometricEnabled)
    }

    @Test
    fun `biometric enabled reflects config state`() {
        configRepo.setBiometricEnabled("testuser", true)
        viewModel = SettingsViewModel(configRepo, biometricHelper, SessionHolder, hostKeyStore, FakeWorkspaceManager(tempDir.toString()))
        assertTrue(viewModel.uiState.value.biometricEnabled)
    }

    @Test
    fun `disableBiometric clears config and deletes key`() {
        configRepo.setBiometricEnabled("testuser", true)
        configRepo.setBiometricEncryptedPassword("testuser", byteArrayOf(1, 2, 3))
        configRepo.setBiometricIv("testuser", byteArrayOf(4, 5, 6))

        viewModel = SettingsViewModel(configRepo, biometricHelper, SessionHolder, hostKeyStore, FakeWorkspaceManager(tempDir.toString()))
        viewModel.disableBiometric()

        assertFalse(viewModel.uiState.value.biometricEnabled)
        assertFalse(configRepo.isBiometricEnabled("testuser"))
        assertNull(configRepo.getBiometricEncryptedPassword("testuser"))
        assertNull(configRepo.getBiometricIv("testuser"))
        assertTrue(biometricHelper.deletedKeys.contains("testuser"))
    }

    @Test
    fun `showBiometricPasswordPrompt opens dialog`() {
        viewModel.showBiometricPasswordPrompt()
        assertTrue(viewModel.uiState.value.showBiometricPasswordDialog)
        assertEquals("", viewModel.uiState.value.biometricPasswordInput)
        assertNull(viewModel.uiState.value.biometricPasswordError)
    }

    @Test
    fun `dismissBiometricPasswordPrompt closes dialog`() {
        viewModel.showBiometricPasswordPrompt()
        viewModel.dismissBiometricPasswordPrompt()
        assertFalse(viewModel.uiState.value.showBiometricPasswordDialog)
    }

    @Test
    fun `updateBiometricPasswordInput updates state`() {
        viewModel.showBiometricPasswordPrompt()
        viewModel.updateBiometricPasswordInput("mypassword")
        assertEquals("mypassword", viewModel.uiState.value.biometricPasswordInput)
    }
}
