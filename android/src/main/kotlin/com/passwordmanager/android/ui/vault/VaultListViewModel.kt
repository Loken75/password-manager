package com.passwordmanager.android.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.passwordmanager.android.data.AndroidSftpRepository
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.FaviconCache
import com.passwordmanager.android.data.FaviconRepository
import com.passwordmanager.android.data.HostKeyChangedException
import com.passwordmanager.android.data.HostKeyPrompt
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.data.SshHostKeyStore
import com.passwordmanager.android.data.UnknownHostKeyException
import com.passwordmanager.config.StorageMode
import com.passwordmanager.sync.EntryMerger
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.SortField
import com.passwordmanager.vault.PasswordEntry
import com.passwordmanager.vault.VaultItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val createdSince: java.time.LocalDate? = null,
    val modifiedSince: java.time.LocalDate? = null,
    val createdOn: java.time.LocalDate? = null,
    val modifiedOn: java.time.LocalDate? = null,
    val message: String? = null,
    val refreshToken: Long = 0,
    val favicons: Map<String, Bitmap> = emptyMap(),
    val isSyncEnabled: Boolean = false,
    /** Non-empty when sync found password conflicts that need manual resolution. */
    val passwordConflicts: List<EntryMerger.Conflict<PasswordEntry>> = emptyList(),
    /** Non-null when a host key needs user confirmation (first use or changed). */
    val hostKeyPrompt: HostKeyPrompt? = null
)

@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val configRepo: ConfigRepository,
    private val faviconRepository: FaviconRepository,
    private val faviconCache: FaviconCache,
    private val hostKeyStore: SshHostKeyStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultListUiState())
    val uiState: StateFlow<VaultListUiState> = _uiState.asStateFlow()
    private var syncJob: Job? = null

    init {
        _uiState.update { it.copy(isSyncEnabled = configRepo.getStorageMode() == StorageMode.REMOTE) }
        // Reflect favicons warmed elsewhere (e.g. on create/edit) immediately, for
        // domains relevant to the currently displayed entries.
        viewModelScope.launch {
            faviconCache.favicons.collect { shared ->
                _uiState.update { state ->
                    val relevant = shared.filterKeys { domain ->
                        state.entries.any {
                            com.passwordmanager.util.FaviconService.extractDomain(it.url ?: "") == domain
                        }
                    }
                    if (relevant.isEmpty()) state else state.copy(favicons = state.favicons + relevant)
                }
            }
        }
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

        val dateFiltered = applyDateFilters(strengthFiltered)

        _uiState.update {
            it.copy(entries = dateFiltered, categories = vault.categories, refreshToken = it.refreshToken + 1)
        }
        loadFavicons(dateFiltered)
    }

    private fun applyDateFilters(list: List<PasswordEntry>): List<PasswordEntry> {
        val s = _uiState.value
        if (s.createdSince == null && s.modifiedSince == null && s.createdOn == null && s.modifiedOn == null) return list
        return list.filter { e ->
            val c = dateOf(e.createdAt)
            val m = dateOf(e.updatedAt)
            when {
                s.createdSince != null && (c == null || c.isBefore(s.createdSince)) -> false
                s.modifiedSince != null && (m == null || m.isBefore(s.modifiedSince)) -> false
                s.createdOn != null && c != s.createdOn -> false
                s.modifiedOn != null && m != s.modifiedOn -> false
                else -> true
            }
        }
    }

    private fun dateOf(iso: String?): java.time.LocalDate? {
        if (iso == null || iso.length < 10) return null
        return try { java.time.LocalDate.parse(iso.substring(0, 10)) } catch (e: Exception) { null }
    }

    fun setCreatedSince(d: java.time.LocalDate?) { _uiState.update { it.copy(createdSince = d) }; refreshEntries() }
    fun setModifiedSince(d: java.time.LocalDate?) { _uiState.update { it.copy(modifiedSince = d) }; refreshEntries() }
    fun setCreatedOn(d: java.time.LocalDate?) { _uiState.update { it.copy(createdOn = d) }; refreshEntries() }
    fun setModifiedOn(d: java.time.LocalDate?) { _uiState.update { it.copy(modifiedOn = d) }; refreshEntries() }
    fun clearDateFilters() {
        _uiState.update { it.copy(createdSince = null, modifiedSince = null, createdOn = null, modifiedOn = null) }
        refreshEntries()
    }

    private fun loadFavicons(entries: List<PasswordEntry>) {
        if (!configRepo.isFaviconsEnabled()) return
        val neededDomains = entries.mapNotNull { entry ->
            val url = entry.url ?: return@mapNotNull null
            if (url.isBlank()) return@mapNotNull null
            com.passwordmanager.util.FaviconService.extractDomain(url)
        }.toSet()

        // Evict stale favicons not needed by current entries
        val currentFavicons = _uiState.value.favicons
        if (currentFavicons.size > MAX_FAVICONS) {
            val toKeep = currentFavicons.filterKeys { it in neededDomains }
            _uiState.update { it.copy(favicons = toKeep) }
        }

        for (entry in entries) {
            val url = entry.url ?: continue
            if (url.isBlank()) continue
            val domain = com.passwordmanager.util.FaviconService.extractDomain(url) ?: continue
            if (_uiState.value.favicons.containsKey(domain)) continue
            if (_uiState.value.favicons.size >= MAX_FAVICONS) break
            // Prefer the shared in-memory cache (e.g. just warmed on create/edit).
            val cached = faviconCache.get(domain)
            if (cached != null) {
                _uiState.update { state ->
                    if (state.favicons.size < MAX_FAVICONS) state.copy(favicons = state.favicons + (domain to cached)) else state
                }
                continue
            }
            viewModelScope.launch(Dispatchers.IO) {
                // Cache-only on display: never hit the network when listing entries,
                // so unlocking the vault discloses no domains. The network fetch runs
                // only on create/edit (EntryEditViewModel.save).
                val bitmap = faviconRepository.getCachedFavicon(url)
                if (bitmap != null) {
                    faviconCache.put(domain, bitmap)
                    _uiState.update { state ->
                        if (state.favicons.size < MAX_FAVICONS) {
                            state.copy(favicons = state.favicons + (domain to bitmap))
                        } else state
                    }
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

    /** Reset every filter (category, favorites, strength, dates) while keeping search and sort. */
    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                selectedCategory = null,
                favoritesOnly = false,
                selectedStrength = null,
                createdSince = null,
                modifiedSince = null,
                createdOn = null,
                modifiedOn = null
            )
        }
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
                // Read the encrypted vault bytes (store-backed, SAF-safe) and write to the SAF uri
                val bytes = sessionHolder.getRepository().readVaultBytes(username)
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
        val state = _uiState.value
        val selectedIds = state.selectedEntryIds
        for (entry in state.entries) {
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
        val vaultFilename = sessionHolder.getRepository().vaultFilename(username)

        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            // Local vault staged to a temp file for JSch's path-based put() (SAF-safe).
            var localStaged: java.io.File? = null
            try {
                // Save current vault first
                sessionHolder.save()

                val repository = sessionHolder.getRepository()
                val localFile = try {
                    stageVaultToTemp(username)
                } catch (e: Exception) {
                    _uiState.update { it.copy(message = "sync_error") }
                    return@launch
                }
                localStaged = localFile
                val localPath = localFile.absolutePath

                val repo = AndroidSftpRepository.fromConfig(
                    configRepo, sessionHolder.vault, hostKeyStore
                ) ?: run {
                    _uiState.update { it.copy(message = "sync_error") }
                    return@launch
                }

                try {
                    // Throws UnknownHostKeyException / HostKeyChangedException on an
                    // untrusted key (handled below to prompt the user).
                    repo.connect()

                    if (!repo.remoteFileExists(vaultFilename)) {
                        // No remote file -- upload local
                        repo.uploadFile(localPath, vaultFilename)
                        _uiState.update { it.copy(message = "sync_success") }
                    } else {
                        // Compare hashes to detect changes
                        val localHash = hashFile(localFile.readBytes())
                        val tempFile = java.io.File.createTempFile("sync_remote", ".enc", context.cacheDir)
                        try {
                            repo.downloadFile(vaultFilename, tempFile.absolutePath)
                            val remoteHash = hashFile(tempFile.readBytes())

                            if (localHash == remoteHash) {
                                // Identical -- nothing to do
                                _uiState.update { it.copy(message = "sync_success") }
                            } else {
                                // Vaults differ -- decrypt remote and merge
                                val vaultSession = sessionHolder.session
                                    ?: throw IllegalStateException("No active vault session")
                                val remoteVault = repository.decryptVaultFile(
                                    tempFile.absolutePath, vaultSession
                                )
                                // R4: adopt the remote envelope so a master-password change
                                // made on another device is preserved when we save+upload
                                // (not reverted by this device's stale envelope).
                                repository.adoptEnvelopeFromFile(vaultSession, tempFile.absolutePath)

                                try {
                                    val localVault = sessionHolder.vault
                                        ?: throw IllegalStateException("No local vault")

                                    // Merge all 4 entry types
                                    val pwResult = EntryMerger.merge(
                                        localVault.entries, remoteVault.entries
                                    )
                                    val appResult = EntryMerger.merge(
                                        localVault.appEntries, remoteVault.appEntries
                                    )
                                    val sshKeyResult = EntryMerger.merge(
                                        localVault.sshKeyEntries, remoteVault.sshKeyEntries
                                    )

                                    // Auto-resolve app/ssh conflicts: keep the most recent version
                                    val mergedApps = autoResolveConflicts(appResult)
                                    val mergedSshKeys = autoResolveConflicts(sshKeyResult)

                                    // Apply non-conflicting merges immediately
                                    localVault.setAppEntries(java.util.ArrayList(mergedApps))
                                    localVault.setSshKeyEntries(java.util.ArrayList(mergedSshKeys))

                                    if (pwResult.hasConflicts()) {
                                        // Persist non-conflicting merges plus the LOCAL version of
                                        // each conflict, so local data survives until resolution and a
                                        // dismissal keeps local state as-is (Option A). Resolution
                                        // replaces these by id via Vault.addEntry.
                                        val withLocalConflicts =
                                            java.util.ArrayList(pwResult.mergedEntries)
                                        for (conflict in pwResult.conflicts) {
                                            withLocalConflicts.add(conflict.localEntry)
                                        }
                                        localVault.setEntries(withLocalConflicts)
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

                                        // Re-stage the saved (merged) vault and upload
                                        val mergedStaged = stageVaultToTemp(username)
                                        try {
                                            repo.uploadFile(mergedStaged.absolutePath, vaultFilename)
                                        } finally {
                                            secureDelete(mergedStaged)
                                        }
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
                    repo.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostKeyException) {
                stashPendingHostKey(e.host, e.port, e.blob)
                _uiState.update {
                    it.copy(hostKeyPrompt = HostKeyPrompt(e.host, e.port, e.fingerprint, e.keyType, changed = false))
                }
            } catch (e: HostKeyChangedException) {
                stashPendingHostKey(e.host, e.port, e.blob)
                _uiState.update {
                    it.copy(hostKeyPrompt = HostKeyPrompt(e.host, e.port, e.fingerprint, e.keyType, changed = true))
                }
            } catch (e: Exception) {
                Log.e(TAG, "SFTP sync failed", e)
                _uiState.update { it.copy(message = "sync_error") }
            } finally {
                localStaged?.let { secureDelete(it) }
            }
        }
    }

    /** Materializes the user's local vault into a temp file for path-based SFTP/JSch use (SAF-safe). */
    private fun stageVaultToTemp(username: String): java.io.File {
        val bytes = sessionHolder.getRepository().readVaultBytes(username)
        val temp = java.io.File.createTempFile("vault_stage", ".enc", context.cacheDir)
        temp.writeBytes(bytes)
        return temp
    }

    // Host key awaiting user confirmation (kept out of UI state -- raw bytes).
    @Volatile
    private var pendingHostKeyBlob: ByteArray? = null
    @Volatile
    private var pendingHostKeyHost: String? = null
    @Volatile
    private var pendingHostKeyPort: Int = 22

    private fun stashPendingHostKey(host: String, port: Int, blob: ByteArray) {
        pendingHostKeyHost = host
        pendingHostKeyPort = port
        pendingHostKeyBlob = blob
    }

    /** User confirmed the presented host key: pin it and resume the sync. */
    fun confirmHostKey() {
        val blob = pendingHostKeyBlob
        val host = pendingHostKeyHost
        if (blob == null || host == null) {
            dismissHostKeyPrompt()
            return
        }
        val port = pendingHostKeyPort
        clearPendingHostKey()
        _uiState.update { it.copy(hostKeyPrompt = null) }
        viewModelScope.launch(Dispatchers.IO) {
            hostKeyStore.pin(host, port, blob)
            syncNow()
        }
    }

    /** User declined the host key: abort without pinning. */
    fun dismissHostKeyPrompt() {
        clearPendingHostKey()
        _uiState.update { it.copy(hostKeyPrompt = null) }
    }

    private fun clearPendingHostKey() {
        pendingHostKeyBlob = null
        pendingHostKeyHost = null
        pendingHostKeyPort = 22
    }

    // Pending password conflicts awaiting user resolution
    @Volatile
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
                val vaultFilename = sessionHolder.getRepository().vaultFilename(username)

                // Replace each conflicting entry by id with the chosen version.
                // Vault.addEntry replaces any existing entry with the same id, so the
                // local version persisted during merge is overwritten (no duplicates).
                for (conflict in conflicts) {
                    val keepLocal = resolutions[conflict.localEntry.id] ?: true
                    val chosen = if (keepLocal) conflict.localEntry else conflict.remoteEntry
                    localVault.addEntry(chosen)
                }
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

    /**
     * Dismiss conflict resolution without choosing (Option A: keep local).
     * The local version of each conflict was already persisted during the merge
     * step, so there is nothing to re-apply -- we simply clear the pending state.
     */
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
        val localStaged = stageVaultToTemp(username)
        val repo = AndroidSftpRepository.fromConfig(configRepo, sessionHolder.vault, hostKeyStore)
        if (repo == null) {
            secureDelete(localStaged)
            return
        }
        try {
            repo.connect()
            repo.uploadFile(localStaged.absolutePath, vaultFilename)
        } finally {
            repo.disconnect()
            secureDelete(localStaged)
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
                val sourceVault = sessionHolder.getRepository().importEncryptedVault(password, tempFile.absolutePath)
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
                for (entry in sourceVault.sshKeyEntries) {
                    vault.addSshKeyEntry(entry)
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

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        syncJob = null
        // Drop references to decrypted entries/favicons retained in UI state once the
        // screen is gone. The entry objects themselves are owned and wiped by the
        // vault/session, not here; this only releases this ViewModel's references.
        _uiState.value = VaultListUiState()
    }

    companion object {
        private const val TAG = "VaultListViewModel"
        private const val MAX_FAVICONS = 50

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
