package com.passwordmanager.vault;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for CRUD operations on application PIN entries.
 */
public class AppService extends BaseVaultService<AppEntry> {

    public AppService(Vault vault) {
        super(vault);
    }

    @Override
    protected List<AppEntry> getMutableList() {
        return vault.getAppEntriesMutable();
    }

    @Override
    public List<AppEntry> getReadOnlyList() {
        return vault.getAppEntries();
    }

    @Override
    protected boolean matchesBase(AppEntry e, String q) {
        return containsIC(e.getTitle(), q) || containsIC(e.getUsername(), q)
            || containsIC(e.getNotes(), q);
    }

    public synchronized List<AppEntry> sorted(List<AppEntry> entries, SortField sortBy) {
        return sorted(entries, sortBy, false);
    }

    /**
     * Sorts entries with favorites ALWAYS grouped first (two blocks: favorites, then the rest),
     * each block ordered by {@code sortBy}. {@code descending} reverses only the in-block field
     * order — it never moves favorites below non-favorites.
     */
    public synchronized List<AppEntry> sorted(List<AppEntry> entries, SortField sortBy, boolean descending) {
        List<AppEntry> sorted = new ArrayList<>(entries);
        Comparator<AppEntry> field;
        switch (sortBy) {
            case USERNAME:
                field = (a, b) -> safe(a.getUsername()).compareToIgnoreCase(safe(b.getUsername()));
                break;
            case DATE:
                field = (a, b) -> safe(b.getUpdatedAt()).compareTo(safe(a.getUpdatedAt()));
                break;
            case CREATED:
                field = (a, b) -> safe(b.getCreatedAt()).compareTo(safe(a.getCreatedAt()));
                break;
            case FAVORITE:
            default:
                // Favorites are already floated first below; secondary order is by title.
                field = (a, b) -> safe(a.getTitle()).compareToIgnoreCase(safe(b.getTitle()));
        }
        Comparator<AppEntry> directed = descending ? field.reversed() : field;
        // Favorites are always grouped first, regardless of sort direction.
        Comparator<AppEntry> withFavorites = (a, b) -> {
            int favCmp = Boolean.compare(b.isFavorite(), a.isFavorite());
            return favCmp != 0 ? favCmp : directed.compare(a, b);
        };
        sorted.sort(withFavorites);
        return sorted;
    }
}
