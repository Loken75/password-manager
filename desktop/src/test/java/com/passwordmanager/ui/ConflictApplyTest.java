package com.passwordmanager.ui;

import com.passwordmanager.sync.EntryMerger;
import com.passwordmanager.vault.PasswordEntry;
import com.passwordmanager.vault.Vault;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the conflict-apply sequence used by {@link MainFrame#handleConflict()}
 * (R1). {@code handleConflict} is Swing/network-bound and cannot be unit-tested
 * directly, so this test reproduces its exact apply steps against a real Vault:
 *
 * <ol>
 *   <li>merge local/remote (mergedEntries excludes conflicts by contract)</li>
 *   <li>applyMerge: clear the list and re-add all mergedEntries</li>
 *   <li>for each conflict, add the resolved entry (chosen, or local on dismiss)</li>
 * </ol>
 *
 * The invariant under test: the final vault never contains duplicate IDs, and the
 * rejected version never survives.
 */
class ConflictApplyTest {

    /** Mirrors MainFrame.applyMerge() for the password list. */
    private static void applyMerge(Vault vault, EntryMerger.MergeResult<PasswordEntry> merge) {
        vault.getEntriesMutable().clear();
        for (PasswordEntry e : merge.getMergedEntries()) {
            vault.addEntry(e);
        }
    }

    private static PasswordEntry pw(String title, String username, String updatedAt) {
        PasswordEntry e = new PasswordEntry(title, username, "p".toCharArray(), "", "", "Cat", null);
        if (updatedAt != null) e.setUpdatedAt(updatedAt);
        return e;
    }

    private static long countById(Vault vault, String id) {
        return vault.getEntries().stream().filter(e -> e.getId().equals(id)).count();
    }

    @Test
    void keepRemote_replacesLocal_noDuplicate() {
        PasswordEntry local = pw("Shared", "localUser", "2024-06-01T00:00:00Z");
        PasswordEntry remote = pw("Shared", "remoteUser", "2024-06-02T00:00:00Z");
        remote.setId(local.getId());

        EntryMerger.MergeResult<PasswordEntry> merge = EntryMerger.merge(
            new ArrayList<>(List.of(local)), new ArrayList<>(List.of(remote)));

        Vault vault = new Vault("user");
        applyMerge(vault, merge);
        // User chose remote for the single conflict.
        vault.addEntry(merge.getConflicts().get(0).getRemoteEntry());

        assertEquals(1, vault.getEntries().size(), "No duplicate id");
        assertEquals(1, countById(vault, local.getId()));
        assertEquals("remoteUser", vault.getEntries().get(0).getUsername(),
            "Chosen remote version must win; rejected local must not survive");
    }

    @Test
    void keepLocal_noDuplicate() {
        PasswordEntry local = pw("Shared", "localUser", "2024-06-01T00:00:00Z");
        PasswordEntry remote = pw("Shared", "remoteUser", "2024-06-02T00:00:00Z");
        remote.setId(local.getId());

        EntryMerger.MergeResult<PasswordEntry> merge = EntryMerger.merge(
            new ArrayList<>(List.of(local)), new ArrayList<>(List.of(remote)));

        Vault vault = new Vault("user");
        applyMerge(vault, merge);
        vault.addEntry(merge.getConflicts().get(0).getLocalEntry());

        assertEquals(1, vault.getEntries().size());
        assertEquals("localUser", vault.getEntries().get(0).getUsername());
    }

    @Test
    void dismiss_keepsLocal_noDuplicate() {
        // Option A: dismissing resolves every conflict in favour of local.
        PasswordEntry local = pw("Shared", "localUser", "2024-06-01T00:00:00Z");
        PasswordEntry remote = pw("Shared", "remoteUser", "2024-06-02T00:00:00Z");
        remote.setId(local.getId());

        EntryMerger.MergeResult<PasswordEntry> merge = EntryMerger.merge(
            new ArrayList<>(List.of(local)), new ArrayList<>(List.of(remote)));

        Vault vault = new Vault("user");
        applyMerge(vault, merge);
        // Dismiss branch: add local for every conflict.
        for (EntryMerger.Conflict<PasswordEntry> c : merge.getConflicts()) {
            vault.addEntry(c.getLocalEntry());
        }

        assertEquals(1, vault.getEntries().size());
        assertEquals("localUser", vault.getEntries().get(0).getUsername());
    }

    @Test
    void nonConflictingEntries_mergedExactlyOnce() {
        PasswordEntry conflictLocal = pw("A", "localA", "2024-06-01T00:00:00Z");
        PasswordEntry localOnly = pw("B", "localB", null);
        PasswordEntry conflictRemote = pw("A", "remoteA", "2024-06-02T00:00:00Z");
        conflictRemote.setId(conflictLocal.getId());
        PasswordEntry remoteOnly = pw("C", "remoteC", null);

        EntryMerger.MergeResult<PasswordEntry> merge = EntryMerger.merge(
            new ArrayList<>(List.of(conflictLocal, localOnly)),
            new ArrayList<>(List.of(conflictRemote, remoteOnly)));

        Vault vault = new Vault("user");
        applyMerge(vault, merge);
        vault.addEntry(merge.getConflicts().get(0).getRemoteEntry());

        assertEquals(3, vault.getEntries().size(), "A (resolved) + B + C, each exactly once");
        assertEquals(1, countById(vault, conflictLocal.getId()));
        assertEquals(1, countById(vault, localOnly.getId()));
        assertEquals(1, countById(vault, remoteOnly.getId()));
    }
}
