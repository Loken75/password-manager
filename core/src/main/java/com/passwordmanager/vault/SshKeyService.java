package com.passwordmanager.vault;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for CRUD operations on SSH key entries.
 */
public class SshKeyService extends BaseVaultService<SshKeyEntry> {

    public SshKeyService(Vault vault) {
        super(vault);
    }

    @Override
    protected List<SshKeyEntry> getMutableList() {
        return vault.getSshKeyEntriesMutable();
    }

    @Override
    public List<SshKeyEntry> getReadOnlyList() {
        return vault.getSshKeyEntries();
    }

    @Override
    protected boolean matchesBase(SshKeyEntry e, String q) {
        return containsIC(e.getTitle(), q) || containsIC(e.getKeyType(), q)
            || containsIC(e.getFingerprint(), q) || containsIC(e.getNotes(), q);
    }

    /** Pure function of the supplied list; needs no synchronization. */
    public List<SshKeyEntry> sorted(List<SshKeyEntry> entries, SortField sortBy) {
        List<SshKeyEntry> sorted = new ArrayList<>(entries);
        Comparator<SshKeyEntry> comp;
        switch (sortBy) {
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
            Comparator<SshKeyEntry> withTitle = (a, b) -> {
                int favCmp = comp.compare(a, b);
                return favCmp != 0 ? favCmp : safe(a.getTitle()).compareToIgnoreCase(safe(b.getTitle()));
            };
            sorted.sort(withTitle);
        } else {
            Comparator<SshKeyEntry> withFavorites = (a, b) -> {
                int favCmp = Boolean.compare(b.isFavorite(), a.isFavorite());
                return favCmp != 0 ? favCmp : comp.compare(a, b);
            };
            sorted.sort(withFavorites);
        }
        return sorted;
    }
}
