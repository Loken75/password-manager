package com.passwordmanager.android.data

import com.jcraft.jsch.HostKeyRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Verifies the JSch-facing verdict mapping that, combined with
 * StrictHostKeyChecking=yes, makes JSch reject unknown/changed host keys.
 */
class PinningHostKeyRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private fun store() = SshHostKeyStore(File(tempDir.toFile(), "ssh/known_hosts"))

    private fun blob(seed: Byte): ByteArray {
        val type = "ssh-ed25519".toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream()
        out.write(0); out.write(0); out.write(0); out.write(type.size)
        out.write(type)
        out.write(ByteArray(32) { seed })
        return out.toByteArray()
    }

    @Test
    fun `unknown host key maps to NOT_INCLUDED and records the key`() {
        val store = store()
        val repo = PinningHostKeyRepository(store, "host.example", 22)
        val key = blob(1)

        val verdict = repo.check("host.example", key)

        assertEquals(HostKeyRepository.NOT_INCLUDED, verdict)
        assertEquals(HostKeyRepository.NOT_INCLUDED, repo.lastVerdict)
        assertContentEquals(key, repo.lastKey)
    }

    @Test
    fun `pinned matching key maps to OK`() {
        val store = store()
        val key = blob(2)
        store.pin("host.example", 22, key)
        val repo = PinningHostKeyRepository(store, "host.example", 22)

        assertEquals(HostKeyRepository.OK, repo.check("host.example", key))
    }

    @Test
    fun `changed key maps to CHANGED`() {
        val store = store()
        store.pin("host.example", 22, blob(3))
        val repo = PinningHostKeyRepository(store, "host.example", 22)

        assertEquals(HostKeyRepository.CHANGED, repo.check("host.example", blob(9)))
    }

    @Test
    fun `null key maps to NOT_INCLUDED`() {
        val repo = PinningHostKeyRepository(store(), "host.example", 22)
        assertEquals(HostKeyRepository.NOT_INCLUDED, repo.check("host.example", null))
    }
}
