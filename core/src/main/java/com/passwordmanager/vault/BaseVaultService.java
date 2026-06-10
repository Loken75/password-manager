package com.passwordmanager.vault;

import com.passwordmanager.util.DateUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Generic base service for CRUD operations on vault items.
 * Subclasses provide the specific list accessor and search overrides.
 *
 * <p><b>Thread-safety:</b> all methods that read or mutate the vault's shared
 * collections synchronize on the {@link Vault} instance (the single monitor that
 * also guards {@link Vault}'s own {@code addEntry}/{@code removeEntry} mutators and
 * the save-time serialization in {@code VaultManager}). Do not introduce a second
 * monitor for vault state.
 */
public abstract class BaseVaultService<T extends VaultItem> {
    protected final Vault vault;

    protected BaseVaultService(Vault vault) {
        this.vault = vault;
    }

    public Vault getVault() { return vault; }

    /** Returns the mutable list backing this service. */
    protected abstract List<T> getMutableList();

    /** Returns the read-only list for this service. */
    public abstract List<T> getReadOnlyList();

    public void addEntry(T entry) {
        synchronized (vault) {
            entry.setUpdatedAt(DateUtils.getCurrentTimestamp());
            getMutableList().add(entry);
            vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
        }
    }

    public boolean updateEntry(T updated) {
        synchronized (vault) {
            List<T> list = getMutableList();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(updated.getId())) {
                    T old = list.get(i);
                    updated.setUpdatedAt(DateUtils.getCurrentTimestamp());
                    list.set(i, updated);
                    if (old != updated) {
                        old.wipe();
                    }
                    vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Soft-deletes an entry by marking it as a tombstone.
     * The entry remains in the list for sync propagation but is hidden from UI.
     * Tombstones are purged after sync merge via {@link #purgeTombstones(int)}.
     */
    public boolean deleteEntry(String entryId) {
        if (entryId == null) return false;
        synchronized (vault) {
            for (T entry : getMutableList()) {
                if (entryId.equals(entry.getId())) {
                    entry.wipe();
                    entry.markDeleted();
                    vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Permanently removes tombstones older than the specified number of days.
     * Should be called after a successful sync merge.
     */
    public int purgeTombstones(int maxAgeDays) {
        synchronized (vault) {
            long threshold = System.currentTimeMillis() - ((long) maxAgeDays * 24 * 60 * 60 * 1000);
            int count = 0;
            Iterator<T> it = getMutableList().iterator();
            while (it.hasNext()) {
                T entry = it.next();
                if (entry.isDeleted() && entry.getDeletedAt() != null) {
                    try {
                        java.util.Date d = DateUtils.parseTimestamp(entry.getDeletedAt());
                        if (d.getTime() < threshold) {
                            it.remove();
                            count++;
                        }
                    } catch (Exception ignored) {
                        // Unparseable date: remove stale tombstone
                        it.remove();
                        count++;
                    }
                }
            }
            return count;
        }
    }

    /**
     * Returns active (non-deleted) entries for UI display and operations.
     * Subclasses' getReadOnlyList() may include tombstones; this filters them.
     */
    public List<T> getActiveList() {
        synchronized (vault) {
            List<T> active = new ArrayList<>();
            for (T e : getReadOnlyList()) {
                if (!e.isDeleted()) active.add(e);
            }
            return active;
        }
    }

    /**
     * Searches entries by title and notes. Subclasses override to add type-specific fields.
     * Excludes soft-deleted entries.
     */
    public List<T> search(String query) {
        synchronized (vault) {
            if (query == null || query.trim().isEmpty()) {
                return getActiveList();
            }
            String q = query.toLowerCase();
            List<T> results = new ArrayList<>();
            for (T e : getReadOnlyList()) {
                if (!e.isDeleted() && matchesBase(e, q)) results.add(e);
            }
            return results;
        }
    }

    protected boolean matchesBase(T e, String q) {
        return containsIC(e.getTitle(), q) || containsIC(e.getNotes(), q);
    }

    protected static boolean containsIC(String str, String q) {
        return str != null && str.toLowerCase().contains(q);
    }

    public boolean toggleFavorite(String entryId) {
        synchronized (vault) {
            for (T entry : getMutableList()) {
                if (entry.getId().equals(entryId)) {
                    entry.setFavorite(!entry.isFavorite());
                    entry.setUpdatedAt(DateUtils.getCurrentTimestamp());
                    vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
                    return true;
                }
            }
            return false;
        }
    }

    public int bulkSetFavorite(List<String> entryIds, boolean favorite) {
        synchronized (vault) {
            java.util.Set<String> idSet = new java.util.HashSet<>(entryIds);
            int count = 0;
            for (T entry : getMutableList()) {
                if (idSet.contains(entry.getId())) {
                    entry.setFavorite(favorite);
                    entry.setUpdatedAt(DateUtils.getCurrentTimestamp());
                    count++;
                }
            }
            if (count > 0) {
                vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
            }
            return count;
        }
    }

    public int bulkDelete(List<String> entryIds) {
        synchronized (vault) {
            int count = 0;
            for (String id : entryIds) {
                if (deleteEntry(id)) count++;
            }
            return count;
        }
    }

    /**
     * Sorts with favorites-first as default behavior. Operates only on the supplied
     * list (no shared vault state), so it needs no synchronization.
     */
    public List<T> sorted(List<T> entries, Comparator<T> comp) {
        List<T> sorted = new ArrayList<>(entries);
        Comparator<T> withFavorites = (a, b) -> {
            int favCmp = Boolean.compare(b.isFavorite(), a.isFavorite());
            return favCmp != 0 ? favCmp : comp.compare(a, b);
        };
        sorted.sort(withFavorites);
        return sorted;
    }

    protected static String safe(String s) { return s == null ? "" : s; }
}
