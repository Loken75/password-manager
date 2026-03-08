package com.passwordmanager.android.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import android.graphics.Bitmap
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.FaviconRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.config.StorageMode
import com.passwordmanager.sync.EntryMerger
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.SortField
import com.passwordmanager.vault.PasswordEntry
import com.passwordmanager.vault.VaultItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class VaultListUiState(
    val entries: List<PasswordEntry> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val sortField: SortField = SortField.TITLE,
    val isSearchActive: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedEntryIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val selectedStrength: com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength? = null,
    val message: String? = null,
    val refreshToken: Long = 0,
    val favicons: Map<String, Bitmap> = emptyMap(),
    val isSyncEnabled: Boolean = false,
    /** Non-empty when sync found password conflicts that need manual resolution. */
    val passwordConflicts: List<EntryMerger.Conflict<PasswordEntry>> = emptyList()
)

@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val configRepo: ConfigRepository,
    private val faviconRepository: FaviconRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultListUiState())
    val uiState: StateFlow<VaultListUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isSyncEnabled = configRepo.getStorageMode() == StorageMode.REMOTE) }
        refreshEntries()
    }

    fun refreshEntries() {
        val service = sessionHolder.vaultService ?: return
        val vault = sessionHolder.vault ?: return

        val entries = when {
            _uiState.value.searchQuery.isNotBlank() ->
                service.search(_uiState.value.searchQuery)
            _uiState.value.selectedCategory != null ->
                service.getByCategory(_uiState.value.selectedCategory)
            else -> service.search("")
        }

        val sorted = service.sorted(entries, _uiState.value.sortField)

        val filtered = if (_uiState.value.favoritesOnly) {
            sorted.filter { it.isFavorite }
        } else {
            sorted
        }

        val strengthFiltered = if (_uiState.value.selectedStrength != null) {
            filtered.filter { entry ->
                val pw = entry.password
                if (pw != null) {
                    val s = com.passwordmanager.crypto.PasswordStrengthAnalyzer.analyze(pw)
                    com.passwordmanager.util.SecureWiper.wipe(pw)
                    s == _uiState.value.selectedStrength
                } else false
            }
        } else {
            filtered
        }

        _uiState.update {
            it.copy(entries = strengthFiltered, categories = vault.categories, refreshToken = it.refreshToken + 1)
        }
        loadFavicons(strengthFiltered)
    }

    private fun loadFavicons(entries: List<PasswordEntry>) {
        for (entry in entries) {
            val url = entry.url ?: continue
            if (url.isBlank()) continue
            val domain = com.passwordmanager.util.FaviconService.extractDomain(url) ?: continue
            if (_uiState.value.favicons.containsKey(domain)) continue
            viewModelScope.launch(Dispatchers.IO) {
                val bitmap = faviconRepository.getFavicon(url)
                if (bitmap != null) {
                    _uiState.update { it.copy(favicons = it.favicons + (domain to bitmap)) }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshEntries()
    }

    fun toggleSearch() {
        _uiState.update { it.copy(isSearchActive = !it.isSearchActive, searchQuery = "") }
        refreshEntries()
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
        refreshEntries()
    }

    fun setSortField(field: SortField) {
        _uiState.update { it.copy(sortField = field) }
        refreshEntries()
    }

    fun toggleFavorite(entryId: String) {
        val service = sessionHolder.vaultService ?: return
        service.toggleFavorite(entryId)
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        refreshEntries()
    }

    fun toggleFavoritesFilter() {
        _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
        refreshEntries()
    }

    fun selectStrength(strength: com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength?) {
        _uiState.update { it.copy(selectedStrength = strength) }
        refreshEntries()
    }

    fun deleteEntry(entryId: String) {
        val service = sessionHolder.vaultService ?: return
        if (service.deleteEntry(entryId)) {
            viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
            refreshEntries()
        }
    }

    fun copyPasswordForEntry(entryId: String) {
        val service = sessionHolder.vaultService ?: return
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        val password = entry.password ?: return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", String(password))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(message = "password_copied") }

        val clearDelay = configRepo.getClipboardClearSeconds() * 1000L
        viewModelScope.launch {
            delay(clearDelay)
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@launch
                val vault = sessionHolder.vault ?: return@launch
                val count = sessionHolder.getRepository().importFromCsv(vault, content)
                sessionHolder.save()
                refreshEntries()
                _uiState.update { it.copy(message = "import_success:$count") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "CSV import failed", e)
                _uiState.update { it.copy(message = "import_error") }
            }
        }
    }

    fun importJson(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@launch
                val vault = sessionHolder.vault ?: return@launch
                val count = sessionHolder.getRepository().importFromJson(vault, content)
                sessionHolder.save()
                refreshEntries()
                _uiState.update { it.copy(message = "import_success:$count") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "JSON import failed", e)
                _uiState.update { it.copy(message = "import_error") }
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vault = sessionHolder.vault ?: return@launch
                val data = sessionHolder.getRepository().exportAsCsv(vault)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(String(data).toByteArray())
                }
                SecureWiper.wipe(data)
                _uiState.update { it.copy(message = "export_success") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "CSV export failed", e)
                _uiState.update { it.copy(message = "export_error") }
            }
        }
    }

    fun exportJson(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vault = sessionHolder.vault ?: return@launch
                val data = sessionHolder.getRepository().exportAsJson(vault)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(String(data).toByteArray())
                }
                SecureWiper.wipe(data)
                _uiState.update { it.copy(message = "export_success") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "JSON export failed", e)
                _uiState.update { it.copy(message = "export_error") }
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val username = sessionHolder.username ?: return@launch
                // Read the encrypted vault file and write to the SAF uri
                val vaultPath = sessionHolder.getRepository().manager.getVaultPath(username)
                val bytes = java.io.File(vaultPath).readBytes()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                _uiState.update { it.copy(message = "export_success") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Backup export failed", e)
                _uiState.update { it.copy(message = "export_error") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // === Multi-select ===

    fun toggleSelection(entryId: String) {
        _uiState.update { state ->
            val newSet = state.selectedEntryIds.toMutableSet()
            if (newSet.contains(entryId)) newSet.remove(entryId) else newSet.add(entryId)
            state.copy(
                selectedEntryIds = newSet,
                isSelectionMode = newSet.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                selectedEntryIds = state.entries.map { it.id }.toSet(),
                isSelectionMode = true
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedEntryIds = emptySet(), isSelectionMode = false) }
    }

    fun bulkChangeCategory(newCategory: String) {
        val service = sessionHolder.vaultService ?: return
        val selectedIds = _uiState.value.selectedEntryIds
        for (entry in _uiState.value.entries) {
            if (entry.id in selectedIds) {
                entry.category = newCategory
                service.updateEntry(entry)
            }
        }
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    fun bulkSetFavorite(favorite: Boolean) {
        val service = sessionHolder.vaultService ?: return
        val selectedIds = _uiState.value.selectedEntryIds.toList()
        service.bulkSetFavorite(selectedIds, favorite)
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    fun bulkToggleFavorite() {
        val service = sessionHolder.vaultService ?: return
        val selectedIds = _uiState.value.selectedEntryIds
        for (id in selectedIds) {
            service.toggleFavorite(id)
        }
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    fun bulkDelete() {
        val service = sessionHolder.vaultService ?: return
        val selectedIds = _uiState.value.selectedEntryIds
        for (id in selectedIds) {
            service.deleteEntry(id)
        }
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        clearSelection()
        refreshEntries()
    }

    // === SFTP Sync ===

    fun syncNow() {
        if (configRepo.getStorageMode() != StorageMode.REMOTE) {
            _uiState.update { it.copy(message = "sync_error") }
            return
        }
        val username = sessionHolder.username ?: return
        val vaultFilename = "vault_${username}.enc"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val host = configRepo.getSftpHost()
                val port = configRepo.getSftpPort()
                val user = configRepo.getSftpUser()
                val keyPath = configRepo.getSftpKeyPath()
                val remotePath = configRepo.getSftpRemotePath()

                if (host.isBlank() || user.isBlank() || keyPath.isBlank()) {
                    _uiState.update { it.copy(message = "sync_error") }
                    return@launch
                }

                // Save current vault first
                sessionHolder.save()

                val vaultManager = sessionHolder.getRepository().manager
                val localPath = vaultManager.getVaultPath(username)
                val localFile = java.io.File(localPath)
                if (!localFile.exists()) {
                    _uiState.update { it.copy(message = "sync_error") }
                    return@launch
                }

                val jsch = JSch()
                jsch.addIdentity(keyPath)
                val sftpSession = jsch.getSession(user, host, port)
                val knownHostsFile = java.io.File(
                    android.os.Environment.getExternalStorageDirectory(), ".ssh/known_hosts"
                )
                if (knownHostsFile.exists()) {
                    jsch.setKnownHosts(knownHostsFile.absolutePath)
                    sftpSession.setConfig("StrictHostKeyChecking", "yes")
                } else {
                    sftpSession.setConfig("StrictHostKeyChecking", "accept-new")
                }
                sftpSession.connect(15_000)

                try {
                    val channel = sftpSession.openChannel("sftp") as ChannelSftp
                    channel.connect(10_000)

                    try {
                        val remoteFile = "$remotePath/$vaultFilename"

                        // Check if remote file exists
                        val remoteExists = try {
                            channel.lstat(remoteFile)
                            true
                        } catch (_: Exception) {
                            false
                        }

                        if (!remoteExists) {
                            // No remote file -- upload local
                            channel.put(localPath, remoteFile, ChannelSftp.OVERWRITE)
                            _uiState.update { it.copy(message = "sync_success") }
                        } else {
                            // Compare hashes to detect changes
                            val localHash = hashFile(localFile.readBytes())
                            val tempFile = java.io.File.createTempFile("sync_remote", ".enc", context.cacheDir)
                            try {
                                channel.get(remoteFile, tempFile.absolutePath)
                                val remoteHash = hashFile(tempFile.readBytes())

                                if (localHash == remoteHash) {
                                    // Identical -- nothing to do
                                    _uiState.update { it.copy(message = "sync_success") }
                                } else {
                                    // Vaults differ -- decrypt remote and merge
                                    val vaultSession = sessionHolder.session
                                        ?: throw IllegalStateException("No active vault session")
                                    val remoteVault = vaultManager.decryptVaultFile(
                                        tempFile.absolutePath, vaultSession
                                    )

                                    try {
                                        val localVault = sessionHolder.vault
                                            ?: throw IllegalStateException("No local vault")

                                        // Merge all 3 entry types
                                        val pwResult = EntryMerger.merge(
                                            localVault.entries, remoteVault.entries
                                        )
                                        val appResult = EntryMerger.merge(
                                            localVault.appEntries, remoteVault.appEntries
                                        )
                                        val cardResult = EntryMerger.merge(
                                            localVault.cardEntries, remoteVault.cardEntries
                                        )

                                        // Auto-resolve app/card conflicts: keep the most recent version
                                        val mergedApps = autoResolveConflicts(appResult)
                                        val mergedCards = autoResolveConflicts(cardResult)

                                        // Apply non-conflicting merges immediately
                                        localVault.setAppEntries(java.util.ArrayList(mergedApps))
                                        localVault.setCardEntries(java.util.ArrayList(mergedCards))

                                        if (pwResult.hasConflicts()) {
                                            // Apply non-conflicting password entries now;
                                            // conflicts will be resolved by the user
                                            localVault.setEntries(java.util.ArrayList(pwResult.mergedEntries))
                                            sessionHolder.save()

                                            // Store pending conflicts for the UI
                                            pendingPasswordConflicts = pwResult.conflicts
                                            _uiState.update {
                                                it.copy(passwordConflicts = pwResult.conflicts)
                                            }
                                            // Don't upload yet -- wait for conflict resolution
                                        } else {
                                            // No password conflicts -- apply merged passwords, save & upload
                                            localVault.setEntries(java.util.ArrayList(pwResult.mergedEntries))
                                            sessionHolder.save()
                                            refreshEntries()

                                            // Re-read the saved (merged) file and upload
                                            channel.put(localPath, remoteFile, ChannelSftp.OVERWRITE)
                                            _uiState.update { it.copy(message = "sync_merge_auto") }
                                        }
                                    } finally {
                                        remoteVault.wipe()
                                    }
                                }
                            } finally {
                                secureDelete(tempFile)
                            }
                        }
                    } finally {
                        channel.disconnect()
                    }
                } finally {
                    sftpSession.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "SFTP sync failed", e)
                _uiState.update { it.copy(message = "sync_error") }
            }
        }
    }

    // Pending password conflicts awaiting user resolution
    private var pendingPasswordConflicts: List<EntryMerger.Conflict<PasswordEntry>> = emptyList()

    /**
     * Called by the ConflictResolutionScreen when the user has chosen
     * local or remote for each conflicting password entry.
     *
     * @param resolutions map of entryId -> keepLocal (true = local, false = remote)
     */
    fun resolvePasswordConflicts(resolutions: Map<String, Boolean>) {
        val conflicts = pendingPasswordConflicts
        if (conflicts.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localVault = sessionHolder.vault ?: return@launch
                val username = sessionHolder.username ?: return@launch
                val vaultFilename = "vault_${username}.enc"

                // Build the resolved entries and add them to the existing merged list
                val currentEntries = java.util.ArrayList(localVault.entries)
                for (conflict in conflicts) {
                    val keepLocal = resolutions[conflict.localEntry.id] ?: true
                    val chosen = if (keepLocal) conflict.localEntry else conflict.remoteEntry
                    currentEntries.add(chosen)
                }
                localVault.setEntries(currentEntries)
                pendingPasswordConflicts = emptyList()

                // Save locally
                sessionHolder.save()
                refreshEntries()

                // Clear the conflicts from UI
                _uiState.update { it.copy(passwordConflicts = emptyList()) }

                // Upload the merged vault to the server
                uploadToSftp(vaultFilename)

                _uiState.update { it.copy(message = "sync_success") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Conflict resolution failed", e)
                _uiState.update { it.copy(message = "sync_error") }
            }
        }
    }

    /** Dismiss conflict resolution without resolving (keeps local state as-is). */
    fun dismissConflicts() {
        pendingPasswordConflicts = emptyList()
        _uiState.update { it.copy(passwordConflicts = emptyList()) }
    }

    /**
     * Auto-resolves merge conflicts for non-password entry types by keeping
     * the version with the most recent updatedAt timestamp.
     */
    private fun <T : VaultItem> autoResolveConflicts(
        result: EntryMerger.MergeResult<T>
    ): List<T> {
        val merged = java.util.ArrayList(result.mergedEntries)
        for (conflict in result.conflicts) {
            val localTime = conflict.localEntry.updatedAt ?: ""
            val remoteTime = conflict.remoteEntry.updatedAt ?: ""
            // ISO-8601 timestamps are lexicographically comparable
            val winner = if (remoteTime > localTime) conflict.remoteEntry else conflict.localEntry
            merged.add(winner)
        }
        return merged
    }

    /**
     * Opens an SFTP connection and uploads the current local vault file to the server.
     * Used after merge/conflict resolution to push the merged result.
     */
    private fun uploadToSftp(vaultFilename: String) {
        val username = sessionHolder.username ?: return
        val host = configRepo.getSftpHost()
        val port = configRepo.getSftpPort()
        val user = configRepo.getSftpUser()
        val keyPath = configRepo.getSftpKeyPath()
        val remotePath = configRepo.getSftpRemotePath()

        val localPath = sessionHolder.getRepository().manager.getVaultPath(username)

        val jsch = JSch()
        jsch.addIdentity(keyPath)
        val sftpSession = jsch.getSession(user, host, port)
        val knownHostsFile = java.io.File(
            android.os.Environment.getExternalStorageDirectory(), ".ssh/known_hosts"
        )
        if (knownHostsFile.exists()) {
            jsch.setKnownHosts(knownHostsFile.absolutePath)
            sftpSession.setConfig("StrictHostKeyChecking", "yes")
        } else {
            sftpSession.setConfig("StrictHostKeyChecking", "accept-new")
        }
        sftpSession.connect(15_000)
        try {
            val channel = sftpSession.openChannel("sftp") as ChannelSftp
            channel.connect(10_000)
            try {
                val remoteFile = "$remotePath/$vaultFilename"
                channel.put(localPath, remoteFile, ChannelSftp.OVERWRITE)
            } finally {
                channel.disconnect()
            }
        } finally {
            sftpSession.disconnect()
        }
    }

    private fun hashFile(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    // === Import encrypted vault ===

    fun importEncryptedVault(uri: Uri, password: CharArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Read the .enc file from URI to a temp file
                val tempFile = java.io.File.createTempFile("import_vault", ".enc", java.io.File(context.cacheDir.absolutePath))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                val sourceVault = sessionHolder.getRepository().manager.importEncryptedVault(password, tempFile.absolutePath)
                tempFile.delete()
                java.util.Arrays.fill(password, '\u0000')

                val vault = sessionHolder.vault ?: return@launch
                var count = 0
                for (entry in sourceVault.entries) {
                    vault.addEntry(entry)
                    count++
                }
                for (entry in sourceVault.appEntries) {
                    vault.addAppEntry(entry)
                    count++
                }
                for (entry in sourceVault.cardEntries) {
                    vault.addCardEntry(entry)
                    count++
                }
                sessionHolder.save()
                refreshEntries()
                _uiState.update { it.copy(message = "import_success:$count") }
            } catch (e: CancellationException) {
                java.util.Arrays.fill(password, '\u0000')
                throw e
            } catch (e: Exception) {
                java.util.Arrays.fill(password, '\u0000')
                Log.e(TAG, "Encrypted vault import failed", e)
                _uiState.update { it.copy(message = "import_error") }
            }
        }
    }

    companion object {
        private const val TAG = "VaultListViewModel"

        /** Overwrites file content with zeros before deleting to prevent forensic recovery. */
        private fun secureDelete(file: java.io.File) {
            try {
                if (file.exists()) {
                    val length = file.length()
                    if (length > 0) {
                        java.io.RandomAccessFile(file, "rw").use { raf ->
                            val zeros = ByteArray(4096)
                            var remaining = length
                            while (remaining > 0) {
                                val toWrite = minOf(remaining, zeros.size.toLong()).toInt()
                                raf.write(zeros, 0, toWrite)
                                remaining -= toWrite
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Best-effort: proceed to delete even if overwrite fails
            }
            file.delete()
        }
    }
}
