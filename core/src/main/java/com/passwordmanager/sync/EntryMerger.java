package com.passwordmanager.sync;

import com.passwordmanager.vault.VaultItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry-level vault merge logic for synchronization.
 * Merges local and remote vault items by ID, with last-write-wins
 * semantics and conflict detection when timestamps differ.
 * Generic over any VaultItem subtype.
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
    public static <T extends VaultItem> MergeResult<T> merge(List<T> local, List<T> remote) {
        List<T> mergedEntries = new ArrayList<>();
        List<Conflict<T>> conflicts = new ArrayList<>();

        // Index remote entries by ID
        Map<String, T> remoteById = new HashMap<>();
        for (T entry : remote) {
            remoteById.put(entry.getId(), entry);
        }

        // Process local entries
        for (T localEntry : local) {
            T remoteEntry = remoteById.remove(localEntry.getId());
            if (remoteEntry == null) {
                // Only in local: keep
                mergedEntries.add(localEntry);
            } else {
                // In both: compare updatedAt
                String localUpdated = localEntry.getUpdatedAt();
                String remoteUpdated = remoteEntry.getUpdatedAt();
                if (nullSafeEquals(localUpdated, remoteUpdated)) {
                    // Same timestamp (including both null): keep local
                    mergedEntries.add(localEntry);
                } else if (localUpdated == null) {
                    // Local has no date, remote does: take remote (no conflict)
                    mergedEntries.add(remoteEntry);
                } else if (remoteUpdated == null) {
                    // Remote has no date, local does: keep local (no conflict)
                    mergedEntries.add(localEntry);
                } else {
                    // Both non-null but different: conflict
                    conflicts.add(new Conflict<>(localEntry, remoteEntry));
                }
            }
        }

        // Remaining remote entries (only in remote): add
        for (T remoteEntry : remoteById.values()) {
            mergedEntries.add(remoteEntry);
        }

        return new MergeResult<>(mergedEntries, conflicts);
    }

    private static boolean nullSafeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * The result of a merge operation, containing the merged entry list
     * and any conflicts that require manual resolution.
     */
    public static class MergeResult<T extends VaultItem> {
        private final List<T> mergedEntries;
        private final List<Conflict<T>> conflicts;

        public MergeResult(List<T> mergedEntries, List<Conflict<T>> conflicts) {
            this.mergedEntries = mergedEntries;
            this.conflicts = conflicts;
        }

        public List<T> getMergedEntries() {
            return mergedEntries;
        }

        public List<Conflict<T>> getConflicts() {
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
    public static class Conflict<T extends VaultItem> {
        private final T localEntry;
        private final T remoteEntry;

        public Conflict(T localEntry, T remoteEntry) {
            this.localEntry = localEntry;
            this.remoteEntry = remoteEntry;
        }

        public T getLocalEntry() {
            return localEntry;
        }

        public T getRemoteEntry() {
            return remoteEntry;
        }
    }
}
