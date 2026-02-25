package com.passwordmanager.android.test

import com.passwordmanager.android.data.AndroidVaultRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.vault.Vault
import com.passwordmanager.vault.VaultManager
import java.nio.file.Path

/**
 * Initializes SessionHolder with a real vault for local JVM tests.
 * Creates a temporary vault on disk, loads it, and unlocks SessionHolder.
 */
object TestSessionHelper {

    fun unlockWithEmptyVault(tempDir: Path): Vault {
        val repo = AndroidVaultRepository(tempDir.toString())
        SessionHolder.init(repo)
        val password = "TestP@ssw0rd!123".toCharArray()
        val created = repo.createVault("testuser", password, listOf("Email", "Work", "Other"))
        val loaded = repo.loadVault("testuser", password)
        SessionHolder.unlock(loaded.vault, loaded.session, "testuser")
        return loaded.vault
    }
}
