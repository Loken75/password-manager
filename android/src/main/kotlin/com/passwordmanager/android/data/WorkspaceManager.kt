package com.passwordmanager.android.data

import android.content.Context
import android.net.Uri
import com.passwordmanager.vault.store.FileVaultStore
import com.passwordmanager.vault.store.VaultStore

/**
 * Resolves and persists the active vault working folder ("workspace").
 *
 * The persisted value is an opaque spec string so the contract can grow without breaking
 * callers: Phase 2 ships two File-backed locations (internal/external); Phase 3 will add a
 * SAF tree (e.g. {@code "saf:<treeUri>"}) handled entirely inside the implementation.
 */
interface WorkspaceManager {
    /** Opaque identifier of the current workspace (defaults to [SPEC_INTERNAL]). */
    fun currentSpec(): String

    /** Builds a [VaultStore] for the current workspace. */
    fun currentStore(): VaultStore

    /** Persists the selected workspace. */
    fun setWorkspace(spec: String)

    /** Specs the user can pick on this device. */
    fun availableSpecs(): List<String>

    /** Builds a [VaultStore] for an arbitrary spec. */
    fun storeFor(spec: String): VaultStore

    /**
     * Namespaces a biometric account by the current workspace so that the same username in two
     * different workspaces does not share one enrollment / Keystore key. The default workspace
     * keeps the bare username for backward compatibility with existing enrollments.
     */
    fun biometricAccount(username: String): String

    companion object {
        /** App-private internal storage (default): invisible to other apps, wiped on uninstall. */
        const val SPEC_INTERNAL = "internal"

        /** App-external storage: reachable over a USB cable; still app-scoped. */
        const val SPEC_EXTERNAL = "external"

        /** Prefix for a user-picked SAF document tree: {@code "saf:<treeUri>"}. */
        const val SAF_PREFIX = "saf:"

        /** Builds the spec string for a picked SAF tree URI. */
        fun safSpec(treeUri: String): String = SAF_PREFIX + treeUri
    }
}

/**
 * [WorkspaceManager] backed by app-scoped [FileVaultStore]s, persisting the choice in
 * [ConfigRepository] (EncryptedSharedPreferences).
 */
class AndroidWorkspaceManager(
    private val context: Context,
    private val configRepo: ConfigRepository
) : WorkspaceManager {

    override fun currentSpec(): String {
        val spec = configRepo.getVaultWorkspace() ?: WorkspaceManager.SPEC_INTERNAL
        // Keep spec, store and UI consistent: if a persisted location is no longer available
        // (e.g. external storage ejected), report internal rather than a phantom "external".
        return if (spec in availableSpecs()) spec else WorkspaceManager.SPEC_INTERNAL
    }

    override fun currentStore(): VaultStore = storeFor(currentSpec())

    override fun setWorkspace(spec: String) = configRepo.setVaultWorkspace(spec)

    override fun availableSpecs(): List<String> {
        val specs = mutableListOf(WorkspaceManager.SPEC_INTERNAL)
        if (context.getExternalFilesDir(null) != null) specs.add(WorkspaceManager.SPEC_EXTERNAL)
        // Surface the currently-selected SAF folder as long as its permission is still held.
        val persisted = configRepo.getVaultWorkspace()
        if (persisted != null && persisted.startsWith(WorkspaceManager.SAF_PREFIX) && isSafValid(persisted)) {
            specs.add(persisted)
        }
        return specs
    }

    override fun biometricAccount(username: String): String {
        val spec = currentSpec()
        return when {
            spec == WorkspaceManager.SPEC_INTERNAL -> username
            spec.startsWith(WorkspaceManager.SAF_PREFIX) ->
                // The raw tree URI is unsafe as a Keystore alias; use a stable short tag.
                "saf${Integer.toHexString(spec.hashCode())}_$username"
            else -> "${spec}_$username"
        }
    }

    override fun storeFor(spec: String): VaultStore = when {
        spec.startsWith(WorkspaceManager.SAF_PREFIX) ->
            SafVaultStore(context, Uri.parse(spec.removePrefix(WorkspaceManager.SAF_PREFIX)))
        spec == WorkspaceManager.SPEC_EXTERNAL ->
            // Fall back to internal if external storage is unavailable (e.g. ejected).
            context.getExternalFilesDir(null)?.let { FileVaultStore(it.absolutePath + VAULTS_SUBDIR) }
                ?: FileVaultStore(internalVaultsDir())
        else -> FileVaultStore(internalVaultsDir())
    }

    /** True if we still hold a persisted read+write grant for the SAF spec's tree URI. */
    private fun isSafValid(spec: String): Boolean {
        val uri = Uri.parse(spec.removePrefix(WorkspaceManager.SAF_PREFIX))
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
    }

    private fun internalVaultsDir(): String = context.filesDir.absolutePath + VAULTS_SUBDIR

    private companion object {
        const val VAULTS_SUBDIR = "/vaults"
    }
}
