package com.passwordmanager.android.ui.vault

import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.test.MainDispatcherExtension
import com.passwordmanager.android.test.TestSessionHelper
import com.passwordmanager.sync.EntryMerger
import com.passwordmanager.vault.PasswordEntry
import com.passwordmanager.vault.Vault
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * Reproduces the conflict-apply steps performed by [VaultListViewModel] (R1).
 * The real method runs inside an SFTP coroutine and cannot be unit-tested
 * directly, so this test replays its sequence against a real persisted vault:
 *
 * 1. merge step: setEntries(mergedEntries + LOCAL version of each conflict), save
 * 2. resolvePasswordConflicts: for each conflict, vault.addEntry(chosen) -- dedup replaces
 * 3. dismissConflicts: nothing to do (local versions already persisted)
 *
 * Invariant: the final vault never contains duplicate IDs, the rejected version
 * never survives, and dismiss keeps local (Option A).
 */
@ExtendWith(MainDispatcherExtension::class)
class ConflictApplyTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var vault: Vault

    @BeforeEach
    fun setUp() {
        vault = TestSessionHelper.unlockWithEmptyVault(tempDir)
    }

    @AfterEach
    fun tearDown() {
        SessionHolder.lock()
    }

    private fun pw(title: String, username: String, updatedAt: String?): PasswordEntry {
        val e = PasswordEntry(title, username, "p".toCharArray(), "", "", "Email", null)
        if (updatedAt != null) e.updatedAt = updatedAt
        return e
    }

    /** Mirrors the merge step of VaultListViewModel.syncNow for password conflicts. */
    private fun applyMergeStep(merge: EntryMerger.MergeResult<PasswordEntry>) {
        val withLocalConflicts = ArrayList(merge.mergedEntries)
        for (conflict in merge.conflicts) {
            withLocalConflicts.add(conflict.localEntry)
        }
        vault.setEntries(withLocalConflicts)
        SessionHolder.save()
    }

    private fun countById(id: String): Int = vault.entries.count { it.id == id }

    @Test
    fun `keepRemote replaces local without duplicate`() {
        val local = pw("Shared", "localUser", "2024-06-01T00:00:00Z")
        val remote = pw("Shared", "remoteUser", "2024-06-02T00:00:00Z")
        remote.id = local.id

        val merge = EntryMerger.merge(arrayListOf(local), arrayListOf(remote))
        applyMergeStep(merge)

        // resolvePasswordConflicts: keepLocal = false -> add remote (replaces by id)
        for (conflict in merge.conflicts) {
            vault.addEntry(conflict.remoteEntry)
        }

        assertEquals(1, vault.entries.size, "No duplicate id")
        assertEquals(1, countById(local.id))
        assertEquals("remoteUser", vault.entries[0].username, "Rejected local must not survive")
    }

    @Test
    fun `keepLocal without duplicate`() {
        val local = pw("Shared", "localUser", "2024-06-01T00:00:00Z")
        val remote = pw("Shared", "remoteUser", "2024-06-02T00:00:00Z")
        remote.id = local.id

        val merge = EntryMerger.merge(arrayListOf(local), arrayListOf(remote))
        applyMergeStep(merge)

        for (conflict in merge.conflicts) {
            vault.addEntry(conflict.localEntry)
        }

        assertEquals(1, vault.entries.size)
        assertEquals("localUser", vault.entries[0].username)
    }

    @Test
    fun `dismiss keeps local version persisted`() {
        // Option A: after the merge step the LOCAL version is already persisted,
        // and dismissConflicts does nothing further.
        val local = pw("Shared", "localUser", "2024-06-01T00:00:00Z")
        val remote = pw("Shared", "remoteUser", "2024-06-02T00:00:00Z")
        remote.id = local.id

        val merge = EntryMerger.merge(arrayListOf(local), arrayListOf(remote))
        applyMergeStep(merge)
        // dismissConflicts(): no-op on the vault.

        assertEquals(1, vault.entries.size)
        assertEquals(1, countById(local.id))
        assertEquals("localUser", vault.entries[0].username)
    }

    @Test
    fun `non-conflicting entries appear exactly once`() {
        val conflictLocal = pw("A", "localA", "2024-06-01T00:00:00Z")
        val localOnly = pw("B", "localB", null)
        val conflictRemote = pw("A", "remoteA", "2024-06-02T00:00:00Z")
        conflictRemote.id = conflictLocal.id
        val remoteOnly = pw("C", "remoteC", null)

        val merge = EntryMerger.merge(
            arrayListOf(conflictLocal, localOnly),
            arrayListOf(conflictRemote, remoteOnly)
        )
        applyMergeStep(merge)
        for (conflict in merge.conflicts) {
            vault.addEntry(conflict.remoteEntry)
        }

        assertEquals(3, vault.entries.size, "A (resolved) + B + C, each once")
        assertEquals(1, countById(conflictLocal.id))
        assertEquals(1, countById(localOnly.id))
        assertEquals(1, countById(remoteOnly.id))
    }
}
