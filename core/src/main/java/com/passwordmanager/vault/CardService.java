package com.passwordmanager.vault;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for CRUD operations on bank card entries.
 */
public class CardService extends BaseVaultService<CardEntry> {

    public CardService(Vault vault) {
        super(vault);
    }

    @Override
    protected List<CardEntry> getMutableList() {
        return vault.getCardEntriesMutable();
    }

    @Override
    public List<CardEntry> getReadOnlyList() {
        return vault.getCardEntries();
    }

    @Override
    protected boolean matchesBase(CardEntry e, String q) {
        return containsIC(e.getTitle(), q) || containsIC(e.getCardholderName(), q)
            || containsIC(e.getCardType(), q) || containsIC(e.getNotes(), q);
    }

    public synchronized List<CardEntry> sorted(List<CardEntry> entries, SortField sortBy) {
        List<CardEntry> sorted = new ArrayList<>(entries);
        Comparator<CardEntry> comp;
        switch (sortBy) {
            case CARDHOLDER_NAME:
                comp = (a, b) -> safe(a.getCardholderName()).compareToIgnoreCase(safe(b.getCardholderName()));
                break;
            case CARD_TYPE:
                comp = (a, b) -> safe(a.getCardType()).compareToIgnoreCase(safe(b.getCardType()));
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
            Comparator<CardEntry> withTitle = (a, b) -> {
                int favCmp = comp.compare(a, b);
                return favCmp != 0 ? favCmp : safe(a.getTitle()).compareToIgnoreCase(safe(b.getTitle()));
            };
            sorted.sort(withTitle);
        } else {
            Comparator<CardEntry> withFavorites = (a, b) -> {
                int favCmp = Boolean.compare(b.isFavorite(), a.isFavorite());
                return favCmp != 0 ? favCmp : comp.compare(a, b);
            };
            sorted.sort(withFavorites);
        }
        return sorted;
    }
}
