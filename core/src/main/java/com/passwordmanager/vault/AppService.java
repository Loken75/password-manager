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
        List<AppEntry> sorted = new ArrayList<>(entries);
        Comparator<AppEntry> comp;
        switch (sortBy) {
            case USERNAME:
                comp = (a, b) -> safe(a.getUsername()).compareToIgnoreCase(safe(b.getUsername()));
                break;
            case DATE:
                comp = (a, b) -> safe(b.getUpdatedAt()).compareTo(safe(a.getUpdatedAt()));
                break;
            case FAVORITE:
                comp = (a, b) -> Boolean.compare(b.isFavorite(), a.isFavorite());
                break;
            default:
                comp = (a, b) -> safe(a.getTitle()).compareToIgnoreCase(safe(b.getTitle()));
        }
        if (sortBy == SortField.FAVORITE) {
            Comparator<AppEntry> withTitle = (a, b) -> {
                int favCmp = comp.compare(a, b);
                return favCmp != 0 ? favCmp : safe(a.getTitle()).compareToIgnoreCase(safe(b.getTitle()));
            };
            sorted.sort(withTitle);
        } else {
            Comparator<AppEntry> withFavorites = (a, b) -> {
                int favCmp = Boolean.compare(b.isFavorite(), a.isFavorite());
                return favCmp != 0 ? favCmp : comp.compare(a, b);
            };
            sorted.sort(withFavorites);
        }
        return sorted;
    }
}
