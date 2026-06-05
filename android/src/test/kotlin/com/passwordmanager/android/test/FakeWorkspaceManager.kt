package com.passwordmanager.android.test

import com.passwordmanager.android.data.WorkspaceManager
import com.passwordmanager.vault.store.FileVaultStore
import com.passwordmanager.vault.store.VaultStore

/**
 * Test [WorkspaceManager] backed by one or two fixed directories (no Android Context).
 * Pass [externalDir] to exercise the two-workspace switch/migration paths.
 */
class FakeWorkspaceManager(
    private val internalDir: String,
    private val externalDir: String? = null
) : WorkspaceManager {
    private var spec = WorkspaceManager.SPEC_INTERNAL

    override fun currentSpec(): String = spec
    override fun currentStore(): VaultStore = storeFor(spec)
    override fun setWorkspace(spec: String) { this.spec = spec }

    override fun availableSpecs(): List<String> =
        if (externalDir != null) listOf(WorkspaceManager.SPEC_INTERNAL, WorkspaceManager.SPEC_EXTERNAL)
        else listOf(WorkspaceManager.SPEC_INTERNAL)

    override fun storeFor(spec: String): VaultStore =
        if (spec == WorkspaceManager.SPEC_EXTERNAL && externalDir != null) FileVaultStore(externalDir)
        else FileVaultStore(internalDir)

    override fun biometricAccount(username: String): String =
        if (spec == WorkspaceManager.SPEC_INTERNAL) username else "${spec}_$username"
}
