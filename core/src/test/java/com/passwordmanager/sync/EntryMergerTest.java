package com.passwordmanager.sync;

import com.passwordmanager.vault.VaultEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntryMergerTest {

    @Test
    void entriesOnlyInLocalAreKept() {
        VaultEntry local = new VaultEntry("Local Only", "u", "p".toCharArray(), "", "", "Cat", null);
        List<VaultEntry> localList = new ArrayList<>(List.of(local));
        List<VaultEntry> remoteList = new ArrayList<>();

        EntryMerger.MergeResult result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertEquals("Local Only", result.getMergedEntries().get(0).getTitle());
        assertFalse(result.hasConflicts());
    }

    @Test
    void entriesOnlyInRemoteAreAdded() {
        VaultEntry remote = new VaultEntry("Remote Only", "u", "p".toCharArray(), "", "", "Cat", null);
        List<VaultEntry> localList = new ArrayList<>();
        List<VaultEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertEquals("Remote Only", result.getMergedEntries().get(0).getTitle());
        assertFalse(result.hasConflicts());
    }

    @Test
    void sameUpdatedAtKeepsLocal() {
        VaultEntry local = new VaultEntry("Shared", "localUser", "p".toCharArray(), "", "", "Cat", null);
        local.setUpdatedAt("2024-06-01T00:00:00Z");
        VaultEntry remote = new VaultEntry("Shared", "remoteUser", "p".toCharArray(), "", "", "Cat", null);
        remote.setId(local.getId());
        remote.setUpdatedAt("2024-06-01T00:00:00Z");

        List<VaultEntry> localList = new ArrayList<>(List.of(local));
        List<VaultEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult result = EntryMerger.merge(localList, remoteList);
        assertEquals(1, result.getMergedEntries().size());
        assertEquals("localUser", result.getMergedEntries().get(0).getUsername());
        assertFalse(result.hasConflicts());
    }

    @Test
    void differentUpdatedAtCreatesConflict() {
        VaultEntry local = new VaultEntry("Shared", "localUser", "p".toCharArray(), "", "", "Cat", null);
        local.setUpdatedAt("2024-06-01T00:00:00Z");
        VaultEntry remote = new VaultEntry("Shared", "remoteUser", "p".toCharArray(), "", "", "Cat", null);
        remote.setId(local.getId());
        remote.setUpdatedAt("2024-06-02T00:00:00Z");

        List<VaultEntry> localList = new ArrayList<>(List.of(local));
        List<VaultEntry> remoteList = new ArrayList<>(List.of(remote));

        EntryMerger.MergeResult result = EntryMerger.merge(localList, remoteList);
        assertTrue(result.hasConflicts());
        assertEquals(1, result.getConflicts().size());
        assertEquals("localUser", result.getConflicts().get(0).getLocalEntry().getUsername());
        assertEquals("remoteUser", result.getConflicts().get(0).getRemoteEntry().getUsername());
    }

    @Test
    void mergeMultipleEntries() {
        VaultEntry localOnly = new VaultEntry("LocalOnly", "u1", "p".toCharArray(), "", "", "Cat", null);
        VaultEntry shared = new VaultEntry("Shared", "u2", "p".toCharArray(), "", "", "Cat", null);
        shared.setUpdatedAt("2024-06-01T00:00:00Z");
        VaultEntry remoteOnly = new VaultEntry("RemoteOnly", "u3", "p".toCharArray(), "", "", "Cat", null);
        VaultEntry sharedRemote = new VaultEntry("Shared", "u2remote", "p".toCharArray(), "", "", "Cat", null);
        sharedRemote.setId(shared.getId());
        sharedRemote.setUpdatedAt("2024-06-01T00:00:00Z");

        List<VaultEntry> localList = new ArrayList<>(List.of(localOnly, shared));
        List<VaultEntry> remoteList = new ArrayList<>(List.of(sharedRemote, remoteOnly));

        EntryMerger.MergeResult result = EntryMerger.merge(localList, remoteList);
        assertEquals(3, result.getMergedEntries().size());
        assertFalse(result.hasConflicts());
    }
}
