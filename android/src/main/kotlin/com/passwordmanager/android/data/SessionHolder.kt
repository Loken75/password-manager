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
 *
 * Thread safety: all mutable fields are @Volatile and mutations are synchronized
 * to prevent races between IO coroutines (login) and Main thread (UI reads, auto-lock).
 */
object SessionHolder {

    private lateinit var repository: AndroidVaultRepository

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlockedFlow: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    @Volatile
    var vault: Vault? = null
        private set

    @Volatile
    var session: VaultSession? = null
        private set

    @Volatile
    var vaultService: VaultService? = null
        private set

    @Volatile
    var username: String? = null
        private set

    fun init(repository: AndroidVaultRepository) {
        this.repository = repository
    }

    fun getRepository(): AndroidVaultRepository = repository

    @Synchronized
    fun unlock(vault: Vault, session: VaultSession, username: String) {
        this.vault = vault
        this.session = session
        this.vaultService = VaultService(vault)
        this.username = username
        _isUnlocked.value = true
    }

    @Synchronized
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

    @Synchronized
    fun save() {
        val v = vault ?: return
        val s = session ?: return
        val u = username ?: return
        repository.saveVault(v, u, s)
    }
}
