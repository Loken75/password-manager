package com.passwordmanager.android.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SshHostKeyStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private fun storeFile() = File(tempDir.toFile(), "ssh/known_hosts")

    /** Builds a minimal SSH public-key blob: uint32 length + algorithm name + body. */
    private fun blob(keyType: String, body: ByteArray): ByteArray {
        val typeBytes = keyType.toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream()
        out.write((typeBytes.size ushr 24) and 0xFF)
        out.write((typeBytes.size ushr 16) and 0xFF)
        out.write((typeBytes.size ushr 8) and 0xFF)
        out.write(typeBytes.size and 0xFF)
        out.write(typeBytes)
        out.write(body)
        return out.toByteArray()
    }

    // --- fingerprint & key type ---

    @Test
    fun `fingerprint is SHA256 prefixed and deterministic`() {
        val b = blob("ssh-ed25519", ByteArray(32) { it.toByte() })
        val fp1 = SshHostKeyStore.fingerprint(b)
        val fp2 = SshHostKeyStore.fingerprint(b)
        assertEquals(fp1, fp2, "Fingerprint must be deterministic")
        assertTrue(fp1.startsWith("SHA256:"))
        assertEquals(50, fp1.length, "SHA256: + 43 base64 chars (no padding)")
    }

    @Test
    fun `different keys have different fingerprints`() {
        val a = SshHostKeyStore.fingerprint(blob("ssh-ed25519", ByteArray(32) { 1 }))
        val b = SshHostKeyStore.fingerprint(blob("ssh-ed25519", ByteArray(32) { 2 }))
        assertNotEquals(a, b)
    }

    @Test
    fun `parseKeyType reads the algorithm name`() {
        assertEquals("ssh-ed25519", SshHostKeyStore.parseKeyType(blob("ssh-ed25519", ByteArray(8))))
        assertEquals("ssh-rsa", SshHostKeyStore.parseKeyType(blob("ssh-rsa", ByteArray(16))))
    }

    @Test
    fun `parseKeyType returns unknown for garbage`() {
        assertEquals("unknown", SshHostKeyStore.parseKeyType(byteArrayOf(0, 0)))
    }

    // --- status transitions ---

    @Test
    fun `unknown when nothing pinned`() {
        val store = SshHostKeyStore(storeFile())
        val b = blob("ssh-ed25519", ByteArray(32) { 7 })
        val status = store.status("host.example", 22, b)
        assertTrue(status is SshHostKeyStore.Status.Unknown)
        status as SshHostKeyStore.Status.Unknown
        assertEquals("ssh-ed25519", status.keyType)
        assertEquals(SshHostKeyStore.fingerprint(b), status.fingerprint)
    }

    @Test
    fun `match after pinning the same key`() {
        val store = SshHostKeyStore(storeFile())
        val b = blob("ssh-ed25519", ByteArray(32) { 7 })
        store.pin("host.example", 22, b)
        assertTrue(store.status("host.example", 22, b) is SshHostKeyStore.Status.Match)
    }

    @Test
    fun `changed when a different key is presented`() {
        val store = SshHostKeyStore(storeFile())
        store.pin("host.example", 22, blob("ssh-ed25519", ByteArray(32) { 1 }))
        val different = blob("ssh-ed25519", ByteArray(32) { 9 })
        val status = store.status("host.example", 22, different)
        assertTrue(status is SshHostKeyStore.Status.Changed)
        status as SshHostKeyStore.Status.Changed
        assertEquals(SshHostKeyStore.fingerprint(different), status.fingerprint)
    }

    @Test
    fun `pin overwrites the previous key`() {
        val store = SshHostKeyStore(storeFile())
        store.pin("host.example", 22, blob("ssh-ed25519", ByteArray(32) { 1 }))
        val rotated = blob("ssh-ed25519", ByteArray(32) { 2 })
        store.pin("host.example", 22, rotated)
        assertTrue(store.status("host.example", 22, rotated) is SshHostKeyStore.Status.Match)
    }

    // --- persistence & isolation ---

    @Test
    fun `pinned key persists across store instances`() {
        val file = storeFile()
        val b = blob("ssh-ed25519", ByteArray(32) { 5 })
        SshHostKeyStore(file).pin("host.example", 2222, b)

        val reopened = SshHostKeyStore(file)
        assertTrue(reopened.isPinned("host.example", 2222))
        assertTrue(reopened.status("host.example", 2222, b) is SshHostKeyStore.Status.Match)
    }

    @Test
    fun `host and port are independent keys`() {
        val store = SshHostKeyStore(storeFile())
        val b = blob("ssh-ed25519", ByteArray(32) { 3 })
        store.pin("host.example", 22, b)
        assertFalse(store.isPinned("host.example", 2222), "Different port is a different pin")
        assertFalse(store.isPinned("other.example", 22), "Different host is a different pin")
        assertTrue(store.status("host.example", 2222, b) is SshHostKeyStore.Status.Unknown)
    }
}
