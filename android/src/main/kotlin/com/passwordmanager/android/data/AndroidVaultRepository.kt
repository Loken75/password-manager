package com.passwordmanager.android.data

import com.passwordmanager.crypto.VaultSession
import com.passwordmanager.vault.Vault
import com.passwordmanager.vault.VaultLoadResult
import com.passwordmanager.vault.VaultManager
import com.passwordmanager.vault.store.FileVaultStore
import com.passwordmanager.vault.store.VaultStore

/**
 * Wraps [VaultManager] for Android. The backing [VaultStore] is swappable at runtime so the
 * user can change the vault working folder; [useStore] must only be called while no session
 * is active (the caller locks first).
 */
class AndroidVaultRepository(initialStore: VaultStore) {

    @Volatile
    private var manager: VaultManager = VaultManager(initialStore)

    /** Convenience for a plain filesystem directory (used by tests and the default workspace). */
    constructor(vaultDirectory: String) : this(FileVaultStore(vaultDirectory))

    /** Re-points the repository at a new workspace store. Caller must ensure no session is active. */
    fun useStore(store: VaultStore) {
        manager = VaultManager(store)
    }

    fun listUsers(): Array<String> = manager.listUsers()

    /** Bare vault filename for a user (e.g. {@code "vault_alice.enc"}). */
    fun vaultFilename(username: String): String = manager.vaultFilename(username)

    /** Raw encrypted bytes of the user's vault file — SAF-safe (no filesystem path assumption). */
    fun readVaultBytes(username: String): ByteArray = manager.readVaultBytes(username)

    /** Decrypts a vault from an arbitrary (already-materialized) file, e.g. a synced temp copy. */
    fun decryptVaultFile(encFilePath: String, session: VaultSession): Vault =
        manager.decryptVaultFile(encFilePath, session)

    /** Adopts the password-derived envelope from an external vault file into the session. */
    fun adoptEnvelopeFromFile(session: VaultSession, encFilePath: String) {
        manager.adoptEnvelopeFromFile(session, encFilePath)
    }

    /** Imports entries from an encrypted vault file using its own master password. */
    fun importEncryptedVault(sourcePassword: CharArray, encFilePath: String): Vault =
        manager.importEncryptedVault(sourcePassword, encFilePath)

    fun vaultExists(username: String): Boolean = manager.vaultExists(username)

    fun createVault(
        username: String,
        masterPassword: CharArray,
        defaultCategories: List<String>? = null
    ): VaultLoadResult = manager.createVault(username, masterPassword, defaultCategories)

    fun loadVault(username: String, masterPassword: CharArray): VaultLoadResult =
        manager.loadVault(username, masterPassword)

    fun saveVault(vault: Vault, username: String, session: VaultSession) {
        manager.saveVault(vault, username, session)
    }

    fun changeMasterPassword(
        username: String,
        vault: Vault,
        currentSession: VaultSession,
        newPassword: CharArray
    ): VaultSession = manager.changeMasterPassword(username, vault, currentSession, newPassword)

    fun exportAsJson(vault: Vault): CharArray = manager.exportAsJson(vault)

    fun exportAsCsv(vault: Vault): CharArray = manager.exportAsCsv(vault)

    fun importFromCsv(vault: Vault, csvContent: String): Int =
        manager.importFromCsv(vault, csvContent)

    fun importFromJson(vault: Vault, jsonContent: String): Int =
        manager.importFromJson(vault, jsonContent)

    fun exportBackup(username: String, session: VaultSession, exportPath: String) {
        manager.exportBackup(username, session, exportPath)
    }
}
