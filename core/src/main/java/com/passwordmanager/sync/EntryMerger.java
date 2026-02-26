package com.passwordmanager.sync;

import com.passwordmanager.vault.VaultEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry-level vault merge logic for synchronization.
 * Merges local and remote vault entries by ID, with last-write-wins
 * semantics and conflict detection when timestamps differ.
 */
public class EntryMerger {

    private EntryMerger() {}

    /**
     * Merges local and remote entry lists.
     *
     * <ul>
     *   <li>Entries only in local: kept as-is</li>
     *   <li>Entries only in remote: added</li>
     *   <li>Entries in both with same updatedAt: keep local</li>
     *   <li>Entries in both with different updatedAt: marked as conflict</li>
     * </ul>
     *
     * @param local  the local vault entries
     * @param remote the remote vault entries
     * @return the merge result containing merged entries and any conflicts
     */
    public static MergeResult merge(List<VaultEntry> local, List<VaultEntry> remote) {
        List<VaultEntry> mergedEntries = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();

        // Index remote entries by ID
        Map<String, VaultEntry> remoteById = new HashMap<>();
        for (VaultEntry entry : remote) {
            remoteById.put(entry.getId(), entry);
        }

        // Process local entries
        for (VaultEntry localEntry : local) {
            VaultEntry remoteEntry = remoteById.remove(localEntry.getId());
            if (remoteEntry == null) {
                // Only in local: keep
                mergedEntries.add(localEntry);
            } else {
                // In both: compare updatedAt
                String localUpdated = localEntry.getUpdatedAt();
                String remoteUpdated = remoteEntry.getUpdatedAt();
                if (localUpdated != null && localUpdated.equals(remoteUpdated)) {
                    // Same timestamp: keep local
                    mergedEntries.add(localEntry);
                } else {
                    // Different timestamp: conflict
                    conflicts.add(new Conflict(localEntry, remoteEntry));
                }
            }
        }

        // Remaining remote entries (only in remote): add
        for (VaultEntry remoteEntry : remoteById.values()) {
            mergedEntries.add(remoteEntry);
        }

        return new MergeResult(mergedEntries, conflicts);
    }

    /**
     * The result of a merge operation, containing the merged entry list
     * and any conflicts that require manual resolution.
     */
    public static class MergeResult {
        private final List<VaultEntry> mergedEntries;
        private final List<Conflict> conflicts;

        public MergeResult(List<VaultEntry> mergedEntries, List<Conflict> conflicts) {
            this.mergedEntries = mergedEntries;
            this.conflicts = conflicts;
        }

        public List<VaultEntry> getMergedEntries() {
            return mergedEntries;
        }

        public List<Conflict> getConflicts() {
            return conflicts;
        }

        public boolean hasConflicts() {
            return conflicts != null && !conflicts.isEmpty();
        }
    }

    /**
     * Represents a merge conflict where local and remote versions
     * of the same entry have different updatedAt timestamps.
     */
    public static class Conflict {
        private final VaultEntry localEntry;
        private final VaultEntry remoteEntry;

        public Conflict(VaultEntry localEntry, VaultEntry remoteEntry) {
            this.localEntry = localEntry;
            this.remoteEntry = remoteEntry;
        }

        public VaultEntry getLocalEntry() {
            return localEntry;
        }

        public VaultEntry getRemoteEntry() {
            return remoteEntry;
        }
    }
}
