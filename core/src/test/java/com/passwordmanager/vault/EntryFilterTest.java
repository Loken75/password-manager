package com.passwordmanager.vault;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EntryFilterTest {

    @Test
    void filterByCategory() {
        EntryFilter filter = new EntryFilter.Builder().category("Email").build();
        VaultEntry match = new VaultEntry("Gmail", "u", "p".toCharArray(), "", "", "Email", null);
        VaultEntry noMatch = new VaultEntry("Bank", "u", "p".toCharArray(), "", "", "Banking", null);
        assertTrue(filter.matches(match));
        assertFalse(filter.matches(noMatch));
    }

    @Test
    void filterByFavorites() {
        EntryFilter filter = new EntryFilter.Builder().favoritesOnly(true).build();
        VaultEntry fav = new VaultEntry("Fav", "u", "p".toCharArray(), "", "", "Cat", null);
        fav.setFavorite(true);
        VaultEntry notFav = new VaultEntry("Normal", "u", "p".toCharArray(), "", "", "Cat", null);
        assertTrue(filter.matches(fav));
        assertFalse(filter.matches(notFav));
    }

    @Test
    void filterBySearchQuery() {
        EntryFilter filter = new EntryFilter.Builder().searchQuery("gmail").build();
        VaultEntry match = new VaultEntry("Gmail", "u", "p".toCharArray(), "https://gmail.com", "", "Email", null);
        VaultEntry noMatch = new VaultEntry("Facebook", "u", "p".toCharArray(), "", "", "Social", null);
        assertTrue(filter.matches(match));
        assertFalse(filter.matches(noMatch));
    }

    @Test
    void filterCombinesAllCriteria() {
        EntryFilter filter = new EntryFilter.Builder()
            .category("Email")
            .favoritesOnly(true)
            .searchQuery("gmail")
            .build();

        VaultEntry matchAll = new VaultEntry("Gmail", "u", "p".toCharArray(), "", "", "Email", null);
        matchAll.setFavorite(true);

        VaultEntry wrongCategory = new VaultEntry("Gmail Social", "u", "p".toCharArray(), "", "", "Social", null);
        wrongCategory.setFavorite(true);

        VaultEntry notFavorite = new VaultEntry("Gmail", "u", "p".toCharArray(), "", "", "Email", null);

        assertTrue(filter.matches(matchAll));
        assertFalse(filter.matches(wrongCategory));
        assertFalse(filter.matches(notFavorite));
    }

    @Test
    void emptyFilterMatchesAll() {
        EntryFilter filter = new EntryFilter.Builder().build();
        VaultEntry entry = new VaultEntry("Any", "u", "p".toCharArray(), "", "", "Cat", null);
        assertTrue(filter.matches(entry));
    }

    @Test
    void filterByTags() {
        EntryFilter filter = new EntryFilter.Builder().tags(Arrays.asList("work")).build();
        VaultEntry match = new VaultEntry("Tagged", "u", "p".toCharArray(), "", "", "Cat",
            Arrays.asList("work", "important"));
        VaultEntry noMatch = new VaultEntry("NoTag", "u", "p".toCharArray(), "", "", "Cat",
            Arrays.asList("personal"));
        assertTrue(filter.matches(match));
        assertFalse(filter.matches(noMatch));
    }
}
