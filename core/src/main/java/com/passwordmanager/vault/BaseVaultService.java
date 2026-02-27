package com.passwordmanager.vault;

import com.passwordmanager.util.DateUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Generic base service for CRUD operations on vault items.
 * Subclasses provide the specific list accessor and search overrides.
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

    public synchronized void addEntry(T entry) {
        entry.setUpdatedAt(DateUtils.getCurrentTimestamp());
        getMutableList().add(entry);
        vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
    }

    public synchronized boolean updateEntry(T updated) {
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

    public synchronized boolean deleteEntry(String entryId) {
        Iterator<T> it = getMutableList().iterator();
        while (it.hasNext()) {
            T entry = it.next();
            if (entry.getId().equals(entryId)) {
                entry.wipe();
                it.remove();
                vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
                return true;
            }
        }
        return false;
    }

    /**
     * Searches entries by title and notes. Subclasses override to add type-specific fields.
     */
    public synchronized List<T> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(getReadOnlyList());
        }
        String q = query.toLowerCase();
        List<T> results = new ArrayList<>();
        for (T e : getReadOnlyList()) {
            if (matchesBase(e, q)) results.add(e);
        }
        return results;
    }

    protected boolean matchesBase(T e, String q) {
        return containsIC(e.getTitle(), q) || containsIC(e.getNotes(), q);
    }

    protected static boolean containsIC(String str, String q) {
        return str != null && str.toLowerCase().contains(q);
    }

    public synchronized boolean toggleFavorite(String entryId) {
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

    public synchronized int bulkSetFavorite(List<String> entryIds, boolean favorite) {
        int count = 0;
        for (T entry : getMutableList()) {
            if (entryIds.contains(entry.getId())) {
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

    public synchronized int bulkDelete(List<String> entryIds) {
        int count = 0;
        for (String id : entryIds) {
            if (deleteEntry(id)) count++;
        }
        return count;
    }

    /**
     * Sorts with favorites-first as default behavior.
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
