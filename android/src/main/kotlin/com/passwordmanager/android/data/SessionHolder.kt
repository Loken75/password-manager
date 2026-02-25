package com.passwordmanager.android.data

import com.passwordmanager.crypto.VaultSession
import com.passwordmanager.vault.Vault
import com.passwordmanager.vault.VaultService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton holding the current unlocked vault session.
 * Acts as the source of truth for authentication state.
 */
object SessionHolder {

    private lateinit var repository: AndroidVaultRepository

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlockedFlow: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    var vault: Vault? = null
        private set

    var session: VaultSession? = null
        private set

    var vaultService: VaultService? = null
        private set

    var username: String? = null
        private set

    fun init(repository: AndroidVaultRepository) {
        this.repository = repository
    }

    fun getRepository(): AndroidVaultRepository = repository

    fun unlock(vault: Vault, session: VaultSession, username: String) {
        this.vault = vault
        this.session = session
        this.vaultService = VaultService(vault)
        this.username = username
        _isUnlocked.value = true
    }

    fun lock() {
        vault?.wipe()
        session?.destroy()
        vault = null
        session = null
        vaultService = null
        username = null
        _isUnlocked.value = false
    }

    fun isUnlocked(): Boolean = _isUnlocked.value

    fun save() {
        val v = vault ?: return
        val s = session ?: return
        val u = username ?: return
        repository.saveVault(v, u, s)
    }
}
