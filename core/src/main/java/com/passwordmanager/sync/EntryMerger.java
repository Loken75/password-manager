package com.passwordmanager.sync;

import com.passwordmanager.vault.VaultItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry-level vault merge logic for synchronization.
 * Merges local and remote vault items by ID, with conflict detection
 * and tombstone support for proper deletion propagation.
 * Generic over any VaultItem subtype.
 */
public class EntryMerger {

    private EntryMerger() {}

    /**
     * Merges local and remote entry lists with tombstone support.
     *
     * <ul>
     *   <li>Entries only in local: kept as-is (including tombstones for propagation)</li>
     *   <li>Entries only in remote: added (including tombstones for propagation)</li>
     *   <li>Entries in both with same updatedAt and same deleted state: keep local</li>
     *   <li>Tombstone vs live entry: most recent action wins (deletedAt vs updatedAt)</li>
     *   <li>Both non-null different updatedAt (neither deleted): marked as conflict</li>
     * </ul>
     *
     * @param local  the local vault entries (including tombstones)
     * @param remote the remote vault entries (including tombstones)
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
                // Only in local: keep (tombstone or live)
                mergedEntries.add(localEntry);
            } else {
                // In both: resolve based on deleted state and timestamps
                mergedEntries.add(resolveEntry(localEntry, remoteEntry, conflicts));
            }
        }

        // Remaining remote entries (only in remote): add (tombstone or live)
        for (T remoteEntry : remoteById.values()) {
            mergedEntries.add(remoteEntry);
        }

        return new MergeResult<>(mergedEntries, conflicts);
    }

    /**
     * Resolves which version to keep when an entry exists in both local and remote.
     * Handles tombstone vs live, tombstone vs tombstone, and live vs live cases.
     */
    private static <T extends VaultItem> T resolveEntry(
            T localEntry, T remoteEntry, List<Conflict<T>> conflicts) {

        boolean localDeleted = localEntry.isDeleted();
        boolean remoteDeleted = remoteEntry.isDeleted();

        if (localDeleted && remoteDeleted) {
            // Both deleted: keep the most recent tombstone
            return compareDates(localEntry.getDeletedAt(), remoteEntry.getDeletedAt()) >= 0
                ? localEntry : remoteEntry;
        }

        if (localDeleted && !remoteDeleted) {
            // Local deleted, remote still alive: most recent action wins
            String deleteTime = localEntry.getDeletedAt();
            String updateTime = remoteEntry.getUpdatedAt();
            if (compareDates(deleteTime, updateTime) >= 0) {
                // Deletion is newer: keep tombstone
                return localEntry;
            } else {
                // Remote was modified after deletion: keep remote (user re-edited)
                return remoteEntry;
            }
        }

        if (!localDeleted && remoteDeleted) {
            // Remote deleted, local still alive: most recent action wins
            String updateTime = localEntry.getUpdatedAt();
            String deleteTime = remoteEntry.getDeletedAt();
            if (compareDates(deleteTime, updateTime) >= 0) {
                // Deletion is newer: keep tombstone
                return remoteEntry;
            } else {
                // Local was modified after deletion: keep local
                return localEntry;
            }
        }

        // Both alive: compare updatedAt
        String localUpdated = localEntry.getUpdatedAt();
        String remoteUpdated = remoteEntry.getUpdatedAt();

        if (nullSafeEquals(localUpdated, remoteUpdated)) {
            return localEntry;
        } else if (localUpdated == null) {
            return remoteEntry;
        } else if (remoteUpdated == null) {
            return localEntry;
        } else {
            // Both non-null but different: conflict
            conflicts.add(new Conflict<>(localEntry, remoteEntry));
            return localEntry; // placeholder; caller replaces from conflict resolution
        }
    }

    /**
     * Compares two ISO timestamp strings. Handles nulls (null is "oldest").
     * Returns positive if a > b, negative if a < b, 0 if equal.
     */
    private static int compareDates(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
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
