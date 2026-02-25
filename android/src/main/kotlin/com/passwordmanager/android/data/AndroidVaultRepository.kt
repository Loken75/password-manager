package com.passwordmanager.android.data

import com.passwordmanager.crypto.VaultSession
import com.passwordmanager.vault.Vault
import com.passwordmanager.vault.VaultLoadResult
import com.passwordmanager.vault.VaultManager

/**
 * Wraps [VaultManager] for Android, providing the vault directory from context.filesDir.
 */
class AndroidVaultRepository(vaultDirectory: String) {

    val manager = VaultManager(vaultDirectory)

    fun listUsers(): Array<String> = manager.listUsers()

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
