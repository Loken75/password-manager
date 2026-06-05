package com.passwordmanager.android.data

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.passwordmanager.sync.RemoteSyncRepository
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.Vault

/**
 * Android SFTP implementation of the shared [RemoteSyncRepository] (JSch + pinned
 * host-key verification via [SftpHostKeyVerifier]). Centralises the connection and
 * transfer logic that was previously duplicated inline in VaultListViewModel and
 * SettingsViewModel, so host-key handling lives in one place.
 *
 * Not thread-safe: one repository instance drives a single connect/transfer/disconnect
 * cycle. [connect] may throw [UnknownHostKeyException]/[HostKeyChangedException], which
 * callers translate into a host-key confirmation prompt.
 *
 * The caller MUST call [disconnect] (e.g. in a finally block) to release the connection
 * and wipe the in-memory private key, even if [connect] threw.
 */
class AndroidSftpRepository(
    private val host: String,
    private val port: Int,
    private val user: String,
    private var keyBytes: ByteArray?,
    private val keyPath: String?,
    private val remotePath: String,
    private val hostKeyStore: SshHostKeyStore,
    private val connectTimeoutMs: Int = 15_000,
    private val channelTimeoutMs: Int = 10_000
) : RemoteSyncRepository {

    private var jsch: JSch? = null
    private var session: Session? = null
    private var channel: ChannelSftp? = null

    override fun connect() {
        val j = JSch()
        val bytes = keyBytes
        when {
            bytes != null -> j.addIdentity("vault_key", bytes, null, null)
            !keyPath.isNullOrBlank() -> j.addIdentity(keyPath)
            else -> throw IllegalStateException("No SSH key configured")
        }
        // Throws UnknownHostKeyException / HostKeyChangedException on an untrusted key.
        val s = SftpHostKeyVerifier.connect(j, host, port, user, hostKeyStore, connectTimeoutMs)
        val ch = s.openChannel("sftp") as ChannelSftp
        ch.connect(channelTimeoutMs)
        jsch = j
        session = s
        channel = ch
    }

    override fun disconnect() {
        try { channel?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        try { jsch?.removeAllIdentity() } catch (_: Exception) {}
        channel = null
        session = null
        jsch = null
        keyBytes?.let { SecureWiper.wipe(it) }
        keyBytes = null
    }

    private fun remote(remoteFilename: String): String = "$remotePath/$remoteFilename"

    override fun remoteFileExists(remoteFilename: String): Boolean =
        try {
            channel?.lstat(remote(remoteFilename))
            true
        } catch (_: Exception) {
            false
        }

    override fun getRemoteLastModified(remoteFilename: String): Long =
        try {
            (channel?.lstat(remote(remoteFilename))?.mTime?.toLong() ?: 0L) * 1000L
        } catch (_: Exception) {
            0L
        }

    override fun uploadFile(localPath: String, remoteFilename: String) {
        val ch = channel ?: throw IllegalStateException("Not connected")
        ch.put(localPath, remote(remoteFilename), ChannelSftp.OVERWRITE)
    }

    override fun downloadFile(remoteFilename: String, localPath: String) {
        val ch = channel ?: throw IllegalStateException("Not connected")
        ch.get(remote(remoteFilename), localPath)
    }

    /**
     * Connects and immediately disconnects, returning whether the session came up.
     * Host-key exceptions propagate (so the caller can prompt to pin the key).
     */
    override fun testConnection(): Boolean =
        try {
            connect()
            val ok = session?.isConnected == true
            disconnect()
            ok
        } catch (e: Exception) {
            disconnect()
            throw e
        }

    companion object {
        /**
         * Builds a repository from the current config, resolving the SSH key from the
         * vault (preferred) or a file path. Returns null when host/user or key are missing.
         * The vault key's char[] is wiped after conversion.
         */
        fun fromConfig(
            configRepo: ConfigRepository,
            vault: Vault?,
            hostKeyStore: SshHostKeyStore,
            connectTimeoutMs: Int = 15_000
        ): AndroidSftpRepository? {
            val host = configRepo.getSftpHost()
            val user = configRepo.getSftpUser()
            if (host.isBlank() || user.isBlank()) return null

            val keyPath = configRepo.getSftpKeyPath()
            val keyEntry = vault?.sshKeyEntries?.find { it.id == configRepo.getSftpKeyId() }
            var keyBytes: ByteArray? = null
            if (keyEntry != null) {
                val priv = keyEntry.privateKey
                if (priv != null) {
                    keyBytes = String(priv).toByteArray(Charsets.UTF_8)
                    SecureWiper.wipe(priv)
                }
            }
            if (keyBytes == null && keyPath.isBlank()) return null

            return AndroidSftpRepository(
                host, configRepo.getSftpPort(), user, keyBytes, keyPath,
                configRepo.getSftpRemotePath(), hostKeyStore, connectTimeoutMs
            )
        }
    }
}
