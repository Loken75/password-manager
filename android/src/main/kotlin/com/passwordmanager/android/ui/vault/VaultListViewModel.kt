package com.passwordmanager.android.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.config.StorageMode
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.SortField
import com.passwordmanager.vault.VaultEntry
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
    val entries: List<VaultEntry> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val sortField: SortField = SortField.TITLE,
    val isSearchActive: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedEntryIds: Set<String> = emptySet(),
    val message: String? = null
)

@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val configRepo: ConfigRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultListUiState())
    val uiState: StateFlow<VaultListUiState> = _uiState.asStateFlow()

    init {
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

        _uiState.update {
            it.copy(entries = sorted, categories = vault.categories)
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

    // === SFTP Sync ===

    fun syncNow() {
        if (configRepo.getStorageMode() != StorageMode.REMOTE) {
            _uiState.update { it.copy(message = "sync_error") }
            return
        }
        val username = sessionHolder.username ?: return
        val vaultFilename = "${username}.enc"

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

                val localPath = sessionHolder.getRepository().manager.getVaultPath(username)
                val localFile = java.io.File(localPath)
                if (!localFile.exists()) {
                    _uiState.update { it.copy(message = "sync_error") }
                    return@launch
                }

                val jsch = JSch()
                jsch.addIdentity(keyPath)
                val session = jsch.getSession(user, host, port)
                val knownHostsFile = java.io.File(
                    android.os.Environment.getExternalStorageDirectory(), ".ssh/known_hosts"
                )
                if (knownHostsFile.exists()) {
                    jsch.setKnownHosts(knownHostsFile.absolutePath)
                    session.setConfig("StrictHostKeyChecking", "yes")
                } else {
                    session.setConfig("StrictHostKeyChecking", "accept-new")
                }
                session.connect(15_000)

                try {
                    val channel = session.openChannel("sftp") as ChannelSftp
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
                            // No remote file — upload local
                            channel.put(localPath, remoteFile, ChannelSftp.OVERWRITE)
                            _uiState.update { it.copy(message = "sync_success") }
                        } else {
                            // Compare hashes
                            val localHash = hashFile(localFile.readBytes())
                            val tempFile = java.io.File.createTempFile("sync_remote", ".enc", context.cacheDir)
                            try {
                                channel.get(remoteFile, tempFile.absolutePath)
                                val remoteHash = hashFile(tempFile.readBytes())

                                if (localHash == remoteHash) {
                                    _uiState.update { it.copy(message = "sync_success") }
                                } else {
                                    // Local is source of truth — upload
                                    channel.put(localPath, remoteFile, ChannelSftp.OVERWRITE)
                                    _uiState.update { it.copy(message = "sync_success") }
                                }
                            } finally {
                                tempFile.delete()
                            }
                        }
                    } finally {
                        channel.disconnect()
                    }
                } finally {
                    session.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "SFTP sync failed", e)
                _uiState.update { it.copy(message = "sync_error") }
            }
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
                val entries = sessionHolder.getRepository().manager.importEncryptedVault(password, tempFile.absolutePath)
                tempFile.delete()
                java.util.Arrays.fill(password, '\u0000')

                val service = sessionHolder.vaultService ?: return@launch
                var count = 0
                for (entry in entries) {
                    service.addEntry(entry)
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
    }
}
