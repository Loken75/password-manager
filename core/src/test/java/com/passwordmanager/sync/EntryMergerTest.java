package com.passwordmanager.sync;

import com.passwordmanager.vault.PasswordEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntryMergerTest {

    @Test
    void entriesOnlyInLocalAreKept() {
        PasswordEntry local = new PasswordEntry("Local Only", "u", "p".toCharArray(), "", "", "Cat", null);
        List<PasswordEntry> localList = new ArrayList<>(List.of(local));
        List<PasswordEntry> remoteList = new ArrayList<>();

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertEquals("Local Only", result.getMergedEntries().get(0).getTitle());
        assertFalse(result.hasConflicts());
    }

    @Test
    void entriesOnlyInRemoteAreAdded() {
        PasswordEntry remote = new PasswordEntry("Remote Only", "u", "p".toCharArray(), "", "", "Cat", null);
        List<PasswordEntry> localList = new ArrayList<>();
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertEquals("Remote Only", result.getMergedEntries().get(0).getTitle());
        assertFalse(result.hasConflicts());
    }

    @Test
    void sameUpdatedAtKeepsLocal() {
        PasswordEntry local = new PasswordEntry("Shared", "localUser", "p".toCharArray(), "", "", "Cat", null);
        local.setUpdatedAt("2024-06-01T00:00:00Z");
        PasswordEntry remote = new PasswordEntry("Shared", "remoteUser", "p".toCharArray(), "", "", "Cat", null);
        remote.setId(local.getId());
        remote.setUpdatedAt("2024-06-01T00:00:00Z");

        List<PasswordEntry> localList = new ArrayList<>(List.of(local));
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertEquals("localUser", result.getMergedEntries().get(0).getUsername());
        assertFalse(result.hasConflicts());
    }

    @Test
    void differentUpdatedAtCreatesConflict() {
        PasswordEntry local = new PasswordEntry("Shared", "localUser", "p".toCharArray(), "", "", "Cat", null);
        local.setUpdatedAt("2024-06-01T00:00:00Z");
        PasswordEntry remote = new PasswordEntry("Shared", "remoteUser", "p".toCharArray(), "", "", "Cat", null);
        remote.setId(local.getId());
        remote.setUpdatedAt("2024-06-02T00:00:00Z");

        List<PasswordEntry> localList = new ArrayList<>(List.of(local));
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertTrue(result.hasConflicts());
        assertEquals(1, result.getConflicts().size());
        assertEquals("localUser", result.getConflicts().get(0).getLocalEntry().getUsername());
        assertEquals("remoteUser", result.getConflicts().get(0).getRemoteEntry().getUsername());
    }

    @Test
    void mergeMultipleEntries() {
        PasswordEntry localOnly = new PasswordEntry("LocalOnly", "u1", "p".toCharArray(), "", "", "Cat", null);
        PasswordEntry shared = new PasswordEntry("Shared", "u2", "p".toCharArray(), "", "", "Cat", null);
        shared.setUpdatedAt("2024-06-01T00:00:00Z");
        PasswordEntry remoteOnly = new PasswordEntry("RemoteOnly", "u3", "p".toCharArray(), "", "", "Cat", null);
        PasswordEntry sharedRemote = new PasswordEntry("Shared", "u2remote", "p".toCharArray(), "", "", "Cat", null);
        sharedRemote.setId(shared.getId());
        sharedRemote.setUpdatedAt("2024-06-01T00:00:00Z");

        List<PasswordEntry> localList = new ArrayList<>(List.of(localOnly, shared));
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(sharedRemote, remoteOnly));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(3, result.getMergedEntries().size());
        assertFalse(result.hasConflicts());
    }

    // ---------------------------------------------------------------
    // Tombstone tests (SYNC-02)
    // ---------------------------------------------------------------

    @Test
    void localTombstone_remoteAbsent_keepsTombstone() {
        PasswordEntry local = new PasswordEntry("Deleted", "u", "p".toCharArray(), "", "", "Cat", null);
        local.markDeleted();

        List<PasswordEntry> localList = new ArrayList<>(List.of(local));
        List<PasswordEntry> remoteList = new ArrayList<>();

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertTrue(result.getMergedEntries().get(0).isDeleted());
        assertFalse(result.hasConflicts());
    }

    @Test
    void remoteTombstone_localAbsent_keepsTombstone() {
        PasswordEntry remote = new PasswordEntry("Deleted", "u", "p".toCharArray(), "", "", "Cat", null);
        remote.markDeleted();

        List<PasswordEntry> localList = new ArrayList<>();
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertTrue(result.getMergedEntries().get(0).isDeleted());
        assertFalse(result.hasConflicts());
    }

    @Test
    void localTombstone_remoteAlive_deletionNewer_keepsTombstone() {
        PasswordEntry local = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat", null);
        local.setUpdatedAt("2024-06-01T00:00:00Z");
        local.setDeleted(true);
        local.setDeletedAt("2024-06-03T00:00:00Z");

        PasswordEntry remote = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat", null);
        remote.setId(local.getId());
        remote.setUpdatedAt("2024-06-02T00:00:00Z");

        List<PasswordEntry> localList = new ArrayList<>(List.of(local));
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertTrue(result.getMergedEntries().get(0).isDeleted(), "Newer deletion should win");
        assertFalse(result.hasConflicts());
    }

    @Test
    void localTombstone_remoteAlive_modificationNewer_keepsRemote() {
        PasswordEntry local = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat", null);
        local.setUpdatedAt("2024-06-01T00:00:00Z");
        local.setDeleted(true);
        local.setDeletedAt("2024-06-02T00:00:00Z");

        PasswordEntry remote = new PasswordEntry("Entry", "u_modified", "p".toCharArray(), "", "", "Cat", null);
        remote.setId(local.getId());
        remote.setUpdatedAt("2024-06-03T00:00:00Z");

        List<PasswordEntry> localList = new ArrayList<>(List.of(local));
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertFalse(result.getMergedEntries().get(0).isDeleted(), "Newer modification should win over deletion");
        assertEquals("u_modified", result.getMergedEntries().get(0).getUsername());
        assertFalse(result.hasConflicts());
    }

    @Test
    void remoteDeleted_localAlive_deletionNewer_keepsTombstone() {
        PasswordEntry local = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat", null);
        local.setUpdatedAt("2024-06-01T00:00:00Z");

        PasswordEntry remote = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat", null);
        remote.setId(local.getId());
        remote.setUpdatedAt("2024-06-01T00:00:00Z");
        remote.setDeleted(true);
        remote.setDeletedAt("2024-06-03T00:00:00Z");

        List<PasswordEntry> localList = new ArrayList<>(List.of(local));
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertTrue(result.getMergedEntries().get(0).isDeleted(), "Newer deletion should win");
        assertFalse(result.hasConflicts());
    }

    @Test
    void bothDeleted_keepsMoreRecent() {
        PasswordEntry local = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat", null);
        local.setDeleted(true);
        local.setDeletedAt("2024-06-01T00:00:00Z");

        PasswordEntry remote = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat", null);
        remote.setId(local.getId());
        remote.setDeleted(true);
        remote.setDeletedAt("2024-06-03T00:00:00Z");

        List<PasswordEntry> localList = new ArrayList<>(List.of(local));
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertTrue(result.getMergedEntries().get(0).isDeleted());
        assertEquals("2024-06-03T00:00:00Z", result.getMergedEntries().get(0).getDeletedAt());
        assertFalse(result.hasConflicts());
    }

    // ---------------------------------------------------------------
    // Conflict contract (R1): conflicting entries are excluded from
    // mergedEntries; the caller adds exactly one resolved entry per conflict.
    // ---------------------------------------------------------------

    @Test
    void conflict_isExcludedFromMergedEntries() {
        PasswordEntry local = new PasswordEntry("Shared", "localUser", "p".toCharArray(), "", "", "Cat", null);
        local.setUpdatedAt("2024-06-01T00:00:00Z");
        PasswordEntry remote = new PasswordEntry("Shared", "remoteUser", "p".toCharArray(), "", "", "Cat", null);
        remote.setId(local.getId());
        remote.setUpdatedAt("2024-06-02T00:00:00Z");

        List<PasswordEntry> localList = new ArrayList<>(List.of(local));
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertTrue(result.hasConflicts());
        assertEquals(1, result.getConflicts().size());
        // The conflicting id must NOT appear in mergedEntries (no placeholder).
        assertTrue(result.getMergedEntries().isEmpty(),
            "Conflicting entry must be excluded from mergedEntries");
        assertFalse(result.getMergedEntries().stream()
            .anyMatch(e -> e.getId().equals(local.getId())));
    }

    @Test
    void conflict_mergedContainsOnlyNonConflicting() {
        // local: A (conflict) + B (local-only); remote: A' (conflict) + C (remote-only)
        PasswordEntry a = new PasswordEntry("A", "localA", "p".toCharArray(), "", "", "Cat", null);
        a.setUpdatedAt("2024-06-01T00:00:00Z");
        PasswordEntry b = new PasswordEntry("B", "localB", "p".toCharArray(), "", "", "Cat", null);
        PasswordEntry aRemote = new PasswordEntry("A", "remoteA", "p".toCharArray(), "", "", "Cat", null);
        aRemote.setId(a.getId());
        aRemote.setUpdatedAt("2024-06-02T00:00:00Z");
        PasswordEntry c = new PasswordEntry("C", "remoteC", "p".toCharArray(), "", "", "Cat", null);

        List<PasswordEntry> localList = new ArrayList<>(List.of(a, b));
        List<PasswordEntry> remoteList = new ArrayList<>(List.of(aRemote, c));

        EntryMerger.MergeResult<PasswordEntry> result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getConflicts().size());
        assertEquals(2, result.getMergedEntries().size(), "Only B and C are non-conflicting");
        assertFalse(result.getMergedEntries().stream().anyMatch(e -> e.getId().equals(a.getId())),
            "Conflicting id A must not be in mergedEntries");
        assertTrue(result.getMergedEntries().stream().anyMatch(e -> e.getId().equals(b.getId())));
        assertTrue(result.getMergedEntries().stream().anyMatch(e -> e.getId().equals(c.getId())));
    }
}
