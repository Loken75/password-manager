package com.passwordmanager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.SshKeyEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class SshKeyManagementUiState(
    val keys: List<SshKeyMeta> = emptyList(),
    val showPublicKey: String? = null,
    val error: String? = null
)

/** Lightweight view of an SshKeyEntry without sensitive data. */
data class SshKeyMeta(
    val id: String,
    val name: String,
    val keyType: String,
    val fingerprint: String,
    val createdAt: String
)

@HiltViewModel
class SshKeyManagementViewModel @Inject constructor(
    private val sessionHolder: SessionHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(SshKeyManagementUiState())
    val uiState: StateFlow<SshKeyManagementUiState> = _uiState.asStateFlow()

    fun load() {
        val vault = sessionHolder.vault ?: return
        val metas = vault.sshKeyEntries.map { entry ->
            SshKeyMeta(
                id = entry.id,
                name = entry.title ?: "",
                keyType = entry.keyType ?: "ED25519",
                fingerprint = entry.fingerprint ?: "",
                createdAt = entry.createdAt ?: ""
            )
        }
        _uiState.update { it.copy(keys = metas, error = null) }
    }

    fun generateKey(name: String, type: String) {
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "ssh_key_name_required") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsch = JSch()
                val keyType = if (type == "RSA") KeyPair.RSA else KeyPair.ED25519
                val keySize = if (type == "RSA") 4096 else 0
                val kpair = KeyPair.genKeyPair(jsch, keyType, keySize)

                val privOut = ByteArrayOutputStream()
                val pubOut = ByteArrayOutputStream()
                kpair.writePrivateKey(privOut)
                kpair.writePublicKey(pubOut, name)
                val fingerprint = kpair.getFingerPrint()
                kpair.dispose()

                val privChars = privOut.toByteArray().let { bytes ->
                    val chars = String(bytes, Charsets.UTF_8).toCharArray()
                    bytes.fill(0)
                    chars
                }
                // Best-effort wipe of ByteArrayOutputStream internal buffer
                val privSize = privOut.size()
                privOut.reset()
                privOut.write(ByteArray(privSize))
                privOut.reset()

                val pubString = pubOut.toString(Charsets.UTF_8)

                val entry = SshKeyEntry(name, privChars, pubString, type, fingerprint)
                SecureWiper.wipe(privChars)

                val vault = sessionHolder.vault ?: return@launch
                vault.addSshKeyEntry(entry)
                sessionHolder.save()
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "ssh_key_generate_error") }
            }
        }
    }

    /**
     * Import from raw bytes (e.g. file picker). Validates with JSch, stores in vault.
     */
    fun importKeyFromBytes(name: String, pemBytes: ByteArray) {
        if (name.isBlank()) {
            pemBytes.fill(0)
            _uiState.update { it.copy(error = "ssh_key_name_required") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsch = JSch()
                val kpair = KeyPair.load(jsch, pemBytes, null)

                val pubOut = ByteArrayOutputStream()
                kpair.writePublicKey(pubOut, name)
                val fingerprint = kpair.getFingerPrint()
                val type = when (kpair.keyType) {
                    KeyPair.RSA -> "RSA"
                    KeyPair.ED25519 -> "ED25519"
                    KeyPair.ECDSA -> "ECDSA"
                    else -> "UNKNOWN"
                }
                kpair.dispose()

                val privChars = String(pemBytes, Charsets.UTF_8).toCharArray()
                pemBytes.fill(0)

                val entry = SshKeyEntry(name, privChars, pubOut.toString(Charsets.UTF_8), type, fingerprint)
                SecureWiper.wipe(privChars)

                val vault = sessionHolder.vault ?: return@launch
                vault.addSshKeyEntry(entry)
                sessionHolder.save()
                load()
            } catch (e: Exception) {
                pemBytes.fill(0)
                _uiState.update { it.copy(error = "ssh_key_invalid") }
            }
        }
    }

    /**
     * Import from pasted text content. Validates with JSch, stores in vault.
     */
    fun importKeyFromContent(name: String, content: String) {
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "ssh_key_name_required") }
            return
        }
        if (content.isBlank()) return
        val pemBytes = content.toByteArray(Charsets.UTF_8)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsch = JSch()
                val kpair = KeyPair.load(jsch, pemBytes, null)

                val pubOut = ByteArrayOutputStream()
                kpair.writePublicKey(pubOut, name)
                val fingerprint = kpair.getFingerPrint()
                val type = when (kpair.keyType) {
                    KeyPair.RSA -> "RSA"
                    KeyPair.ED25519 -> "ED25519"
                    KeyPair.ECDSA -> "ECDSA"
                    else -> "UNKNOWN"
                }
                kpair.dispose()

                val privChars = String(pemBytes, Charsets.UTF_8).toCharArray()
                pemBytes.fill(0)

                val entry = SshKeyEntry(name, privChars, pubOut.toString(Charsets.UTF_8), type, fingerprint)
                SecureWiper.wipe(privChars)

                val vault = sessionHolder.vault ?: return@launch
                vault.addSshKeyEntry(entry)
                sessionHolder.save()
                load()
            } catch (e: Exception) {
                pemBytes.fill(0)
                _uiState.update { it.copy(error = "ssh_key_import_content_error") }
            }
        }
    }

    fun deleteKey(keyId: String) {
        val vault = sessionHolder.vault ?: return
        val entry = vault.sshKeyEntriesMutable.find { it.id == keyId } ?: return
        entry.wipe()
        vault.sshKeyEntriesMutable.remove(entry)
        viewModelScope.launch(Dispatchers.IO) { sessionHolder.save() }
        load()
    }

    fun viewPublicKey(keyId: String) {
        val vault = sessionHolder.vault ?: return
        val entry = vault.sshKeyEntries.find { it.id == keyId } ?: return
        _uiState.update { it.copy(showPublicKey = entry.publicKey) }
    }

    fun dismissPublicKey() {
        _uiState.update { it.copy(showPublicKey = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
