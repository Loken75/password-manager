package com.passwordmanager.android.data

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo

/** UI model describing a host key awaiting user confirmation. */
data class HostKeyPrompt(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val keyType: String,
    /** true = a previously pinned key changed (warn strongly); false = first use. */
    val changed: Boolean
)

/** Thrown when the host presents a key with no existing pin (first use). */
class UnknownHostKeyException(
    val host: String, val port: Int, val fingerprint: String, val keyType: String, val blob: ByteArray
) : Exception("Unknown host key for $host:$port")

/** Thrown when the pinned key differs from the presented key (possible MITM). */
class HostKeyChangedException(
    val host: String, val port: Int, val fingerprint: String, val keyType: String, val blob: ByteArray
) : Exception("Host key changed for $host:$port")

/**
 * Connects a JSch session with strict host-key pinning backed by [SshHostKeyStore].
 *
 * - Pinned & matching  -> connects and returns the session.
 * - No pin (first use) -> throws [UnknownHostKeyException]; the caller asks the user to confirm.
 * - Pinned but changed -> throws [HostKeyChangedException]; the caller warns the user.
 *
 * The host key is never trusted implicitly: the pin is written only by an explicit
 * call to [SshHostKeyStore.pin] after the user confirms.
 */
object SftpHostKeyVerifier {

    fun connect(
        jsch: JSch,
        host: String,
        port: Int,
        user: String,
        store: SshHostKeyStore,
        timeoutMs: Int
    ): Session {
        val repo = PinningHostKeyRepository(store, host, port)
        jsch.hostKeyRepository = repo
        val session = jsch.getSession(user, host, port)
        // Strict: an unknown or changed key makes connect() fail; we surface the
        // recorded key to the caller for an explicit user decision.
        session.setConfig("StrictHostKeyChecking", "yes")
        try {
            session.connect(timeoutMs)
            return session
        } catch (e: Exception) {
            val blob = repo.lastKey
            if (blob != null) {
                when (repo.lastVerdict) {
                    HostKeyRepository.NOT_INCLUDED -> throw UnknownHostKeyException(
                        host, port, SshHostKeyStore.fingerprint(blob), SshHostKeyStore.parseKeyType(blob), blob)
                    HostKeyRepository.CHANGED -> throw HostKeyChangedException(
                        host, port, SshHostKeyStore.fingerprint(blob), SshHostKeyStore.parseKeyType(blob), blob)
                }
            }
            throw e
        }
    }
}

/**
 * A [HostKeyRepository] that delegates the trust decision to [SshHostKeyStore]
 * and records the key presented by the server so the caller can display its
 * fingerprint when the key is unknown or changed.
 *
 * Internal (not private) so the verdict mapping can be unit-tested without a server.
 */
internal class PinningHostKeyRepository(
    private val store: SshHostKeyStore,
    private val host: String,
    private val port: Int
) : HostKeyRepository {
    @Volatile var lastKey: ByteArray? = null
    @Volatile var lastVerdict: Int = HostKeyRepository.OK

    override fun check(hostArg: String?, key: ByteArray?): Int {
        lastKey = key
        val verdict = if (key == null) {
            HostKeyRepository.NOT_INCLUDED
        } else when (store.status(host, port, key)) {
            is SshHostKeyStore.Status.Match -> HostKeyRepository.OK
            is SshHostKeyStore.Status.Unknown -> HostKeyRepository.NOT_INCLUDED
            is SshHostKeyStore.Status.Changed -> HostKeyRepository.CHANGED
        }
        lastVerdict = verdict
        return verdict
    }

    // Pinning is performed explicitly via SshHostKeyStore.pin after user confirmation,
    // so JSch's own add/remove are intentionally no-ops.
    override fun add(hostkey: HostKey?, ui: UserInfo?) {}
    override fun remove(host: String?, type: String?) {}
    override fun remove(host: String?, type: String?, key: ByteArray?) {}
    override fun getKnownHostsRepositoryID(): String = "SshHostKeyStore"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
