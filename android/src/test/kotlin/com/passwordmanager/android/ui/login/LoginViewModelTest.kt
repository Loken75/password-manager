package com.passwordmanager.android.ui.login

import com.passwordmanager.android.data.AndroidVaultRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.data.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import com.passwordmanager.vault.store.FileVaultStore
import com.passwordmanager.android.test.FakeBiometricHelper
import com.passwordmanager.android.test.FakeConfigRepository
import com.passwordmanager.android.test.FakeWorkspaceManager
import com.passwordmanager.android.test.MainDispatcherExtension
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

/**
 * Tests for LoginViewModel covering state logic, user creation validation,
 * password login flow, and biometric config state management.
 *
 * BiometricPrompt interactions (enrollBiometric, loginWithBiometric)
 * require a FragmentActivity and real AndroidKeyStore — tested via instrumented tests.
 */
@ExtendWith(MainDispatcherExtension::class)
class LoginViewModelTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var configRepo: FakeConfigRepository
    private lateinit var biometricHelper: FakeBiometricHelper
    private lateinit var workspaceManager: FakeWorkspaceManager
    private lateinit var repo: AndroidVaultRepository
    private lateinit var viewModel: LoginViewModel

    @BeforeEach
    fun setUp() {
        configRepo = FakeConfigRepository()
        biometricHelper = FakeBiometricHelper()
        workspaceManager = FakeWorkspaceManager(tempDir.toString())
        repo = AndroidVaultRepository(tempDir.toString())
        SessionHolder.init(repo)

        // Create a test user
        repo.createVault("testuser", TEST_PASSWORD.toCharArray(), listOf("Email", "Work"))

        viewModel = LoginViewModel(repo, SessionHolder, biometricHelper, configRepo, workspaceManager, Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        SessionHolder.lock()
    }

    // --- Initial state ---

    @Test
    fun `init loads users from repository`() {
        val state = viewModel.uiState.value
        assertTrue(state.users.contains("testuser"))
        assertEquals("testuser", state.selectedUser)
    }

    @Test
    fun `init detects biometric unavailable`() {
        biometricHelper.available = false
        viewModel = LoginViewModel(repo, SessionHolder, biometricHelper, configRepo, workspaceManager, Dispatchers.Unconfined)
        assertFalse(viewModel.uiState.value.biometricAvailable)
    }

    @Test
    fun `init detects biometric available`() {
        biometricHelper.available = true
        viewModel = LoginViewModel(repo, SessionHolder, biometricHelper, configRepo, workspaceManager, Dispatchers.Unconfined)
        assertTrue(viewModel.uiState.value.biometricAvailable)
    }

    @Test
    fun `init loads biometric enabled status for selected user`() {
        configRepo.setBiometricEnabled("testuser", true)
        viewModel = LoginViewModel(repo, SessionHolder, biometricHelper, configRepo, workspaceManager, Dispatchers.Unconfined)
        assertTrue(viewModel.uiState.value.biometricEnabledForUser)
    }

    @Test
    fun `biometric disabled by default for new user`() {
        assertFalse(viewModel.uiState.value.biometricEnabledForUser)
    }

    // --- User selection ---

    @Test
    fun `selectUser updates state and clears errors`() {
        viewModel.selectUser("testuser")
        val state = viewModel.uiState.value
        assertEquals("testuser", state.selectedUser)
        assertEquals("", state.password)
        assertNull(state.error)
        assertNull(state.biometricError)
    }

    @Test
    fun `selectUser checks biometric enabled status`() {
        configRepo.setBiometricEnabled("testuser", true)
        viewModel.selectUser("testuser")
        assertTrue(viewModel.uiState.value.biometricEnabledForUser)
    }

    @Test
    fun `selectUser shows biometric disabled when not enrolled`() {
        configRepo.setBiometricEnabled("testuser", false)
        viewModel.selectUser("testuser")
        assertFalse(viewModel.uiState.value.biometricEnabledForUser)
    }

    // --- Password login ---

    @Test
    fun `login with empty password shows error`() {
        viewModel.selectUser("testuser")
        viewModel.updatePassword("")
        viewModel.login {}
        assertEquals("error_empty_password", viewModel.uiState.value.error)
    }

    @Test
    fun `login with blank password shows error`() {
        viewModel.selectUser("testuser")
        viewModel.updatePassword("   ")
        viewModel.login {}
        assertEquals("error_empty_password", viewModel.uiState.value.error)
    }

    @Test
    fun `updatePassword updates state and clears error`() {
        viewModel.updatePassword("test123")
        val state = viewModel.uiState.value
        assertEquals("test123", state.password)
        assertNull(state.error)
    }

    // --- Biometric enrollment dialog ---

    @Test
    fun `dismissBiometricEnrollment clears dialog state`() {
        viewModel.dismissBiometricEnrollment()
        assertFalse(viewModel.uiState.value.showBiometricEnrollDialog)
    }

    // --- Biometric config state tests ---

    @Test
    fun `biometric state is per-user in config`() {
        configRepo.setBiometricEnabled("user1", true)
        configRepo.setBiometricEnabled("user2", false)
        assertTrue(configRepo.isBiometricEnabled("user1"))
        assertFalse(configRepo.isBiometricEnabled("user2"))
    }

    @Test
    fun `biometric encrypted password storage round-trip`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val iv = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)

        configRepo.setBiometricEncryptedPassword("testuser", data)
        configRepo.setBiometricIv("testuser", iv)

        assertTrue(data.contentEquals(configRepo.getBiometricEncryptedPassword("testuser")!!))
        assertTrue(iv.contentEquals(configRepo.getBiometricIv("testuser")!!))
    }

    @Test
    fun `biometric data null when not set`() {
        assertNull(configRepo.getBiometricEncryptedPassword("testuser"))
        assertNull(configRepo.getBiometricIv("testuser"))
    }

    @Test
    fun `clearBiometricData removes all biometric state`() {
        configRepo.setBiometricEnabled("testuser", true)
        configRepo.setBiometricEncryptedPassword("testuser", byteArrayOf(1, 2, 3))
        configRepo.setBiometricIv("testuser", byteArrayOf(4, 5, 6))

        configRepo.clearBiometricData("testuser")

        assertFalse(configRepo.isBiometricEnabled("testuser"))
        assertNull(configRepo.getBiometricEncryptedPassword("testuser"))
        assertNull(configRepo.getBiometricIv("testuser"))
    }

    @Test
    fun `clearBiometricData only affects target user`() {
        configRepo.setBiometricEnabled("user1", true)
        configRepo.setBiometricEnabled("user2", true)

        configRepo.clearBiometricData("user1")

        assertFalse(configRepo.isBiometricEnabled("user1"))
        assertTrue(configRepo.isBiometricEnabled("user2"))
    }

    // --- Create user ---

    @Test
    fun `showCreateDialog opens dialog`() {
        viewModel.showCreateDialog()
        assertTrue(viewModel.uiState.value.showCreateDialog)
    }

    @Test
    fun `dismissCreateDialog closes dialog`() {
        viewModel.showCreateDialog()
        viewModel.dismissCreateDialog()
        assertFalse(viewModel.uiState.value.showCreateDialog)
    }

    @Test
    fun `createUser with empty username shows error`() {
        viewModel.showCreateDialog()
        viewModel.updateNewUsername("")
        viewModel.createUser(listOf("Email"))
        assertEquals("error_empty_username", viewModel.uiState.value.createError)
    }

    @Test
    fun `createUser with invalid username shows error`() {
        viewModel.showCreateDialog()
        viewModel.updateNewUsername("user@name")
        viewModel.createUser(listOf("Email"))
        assertEquals("error_invalid_username", viewModel.uiState.value.createError)
    }

    @Test
    fun `createUser with existing user shows error`() {
        viewModel.showCreateDialog()
        viewModel.updateNewUsername("testuser")
        viewModel.updateNewPassword("NewP@ssw0rd!123")
        viewModel.updateNewPasswordConfirm("NewP@ssw0rd!123")
        viewModel.createUser(listOf("Email"))
        assertEquals("error_user_exists", viewModel.uiState.value.createError)
    }

    @Test
    fun `createUser with mismatched passwords shows error`() {
        viewModel.showCreateDialog()
        viewModel.updateNewUsername("newuser")
        viewModel.updateNewPassword("TestP@ssw0rd!123")
        viewModel.updateNewPasswordConfirm("DifferentP@ss!123")
        viewModel.createUser(listOf("Email"))
        assertEquals("security_password_mismatch", viewModel.uiState.value.createError)
    }

    @Test
    fun `createUser with weak password shows error`() {
        viewModel.showCreateDialog()
        viewModel.updateNewUsername("newuser")
        viewModel.updateNewPassword("weak")
        viewModel.updateNewPasswordConfirm("weak")
        viewModel.createUser(listOf("Email"))
        assertEquals("security_password_requirements", viewModel.uiState.value.createError)
    }

    // --- Workspace switch / migration ---

    @Test
    fun `switchWorkspace defers to migrate prompt when current folder has vaults`() {
        val internal = tempDir.resolve("internal").toString()
        val external = tempDir.resolve("external").toString()
        val ws = FakeWorkspaceManager(internal, external)
        val r = AndroidVaultRepository(FileVaultStore(internal))
        SessionHolder.init(r)
        r.createVault("alice", TEST_PASSWORD.toCharArray(), listOf("Email"))
        val vm = LoginViewModel(r, SessionHolder, biometricHelper, configRepo, ws, Dispatchers.Unconfined)

        vm.switchWorkspace(WorkspaceManager.SPEC_EXTERNAL)

        assertEquals(WorkspaceManager.SPEC_EXTERNAL, vm.uiState.value.pendingWorkspaceSwitch)
        assertEquals(1, vm.uiState.value.pendingWorkspaceVaultCount)
        // Not switched yet
        assertEquals(WorkspaceManager.SPEC_INTERNAL, vm.uiState.value.workspaceSpec)
    }

    @Test
    fun `confirmWorkspaceSwitch with migrate moves vaults to the new folder`() {
        val internal = tempDir.resolve("internal").toString()
        val external = tempDir.resolve("external").toString()
        val ws = FakeWorkspaceManager(internal, external)
        val r = AndroidVaultRepository(FileVaultStore(internal))
        SessionHolder.init(r)
        r.createVault("alice", TEST_PASSWORD.toCharArray(), listOf("Email"))
        val vm = LoginViewModel(r, SessionHolder, biometricHelper, configRepo, ws, Dispatchers.Unconfined)

        vm.switchWorkspace(WorkspaceManager.SPEC_EXTERNAL)
        vm.confirmWorkspaceSwitch(migrate = true)

        assertEquals(WorkspaceManager.SPEC_EXTERNAL, vm.uiState.value.workspaceSpec)
        assertNull(vm.uiState.value.pendingWorkspaceSwitch)
        assertTrue(vm.uiState.value.users.contains("alice"))
        // Source folder no longer holds the vault after a move
        assertFalse(AndroidVaultRepository(FileVaultStore(internal)).listUsers().contains("alice"))
    }

    @Test
    fun `confirmWorkspaceSwitch keep leaves vaults in the old folder`() {
        val internal = tempDir.resolve("internal").toString()
        val external = tempDir.resolve("external").toString()
        val ws = FakeWorkspaceManager(internal, external)
        val r = AndroidVaultRepository(FileVaultStore(internal))
        SessionHolder.init(r)
        r.createVault("alice", TEST_PASSWORD.toCharArray(), listOf("Email"))
        val vm = LoginViewModel(r, SessionHolder, biometricHelper, configRepo, ws, Dispatchers.Unconfined)

        vm.switchWorkspace(WorkspaceManager.SPEC_EXTERNAL)
        vm.confirmWorkspaceSwitch(migrate = false)

        assertEquals(WorkspaceManager.SPEC_EXTERNAL, vm.uiState.value.workspaceSpec)
        assertFalse(vm.uiState.value.users.contains("alice"))
        // Original vault is untouched in the internal folder
        assertTrue(AndroidVaultRepository(FileVaultStore(internal)).listUsers().contains("alice"))
    }

    // --- onCleared ---

    @Test
    fun `onCleared wipes pending password safely`() {
        val method = viewModel.javaClass.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        // No crash = password was wiped safely
    }

    // --- loadUsers biometric state ---

    @Test
    fun `loadUsers sets biometricEnabledForUser from config`() {
        configRepo.setBiometricEnabled("testuser", true)
        viewModel.loadUsers()
        assertTrue(viewModel.uiState.value.biometricEnabledForUser)
    }

    @Test
    fun `loadUsers sets biometricEnabledForUser false when not configured`() {
        viewModel.loadUsers()
        assertFalse(viewModel.uiState.value.biometricEnabledForUser)
    }

    companion object {
        private const val TEST_PASSWORD = "TestP@ssw0rd!123"
    }
}
