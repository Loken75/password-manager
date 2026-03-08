package com.passwordmanager.vault;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EntryFilterTest {

    @Test
    void filterByCategory() {
        EntryFilter filter = new EntryFilter.Builder().category("Email").build();
        PasswordEntry match = new PasswordEntry("Gmail", "u", "p".toCharArray(), "", "", "Email", null);
        PasswordEntry noMatch = new PasswordEntry("Bank", "u", "p".toCharArray(), "", "", "Banking", null);
        assertTrue(filter.matches(match));
        assertFalse(filter.matches(noMatch));
    }

    @Test
    void filterByFavorites() {
        EntryFilter filter = new EntryFilter.Builder().favoritesOnly(true).build();
        PasswordEntry fav = new PasswordEntry("Fav", "u", "p".toCharArray(), "", "", "Cat", null);
        fav.setFavorite(true);
        PasswordEntry notFav = new PasswordEntry("Normal", "u", "p".toCharArray(), "", "", "Cat", null);
        assertTrue(filter.matches(fav));
        assertFalse(filter.matches(notFav));
    }

    @Test
    void filterBySearchQuery() {
        EntryFilter filter = new EntryFilter.Builder().searchQuery("gmail").build();
        PasswordEntry match = new PasswordEntry("Gmail", "u", "p".toCharArray(), "https://gmail.com", "", "Email", null);
        PasswordEntry noMatch = new PasswordEntry("Facebook", "u", "p".toCharArray(), "", "", "Social", null);
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

        PasswordEntry matchAll = new PasswordEntry("Gmail", "u", "p".toCharArray(), "", "", "Email", null);
        matchAll.setFavorite(true);

        PasswordEntry wrongCategory = new PasswordEntry("Gmail Social", "u", "p".toCharArray(), "", "", "Social", null);
        wrongCategory.setFavorite(true);

        PasswordEntry notFavorite = new PasswordEntry("Gmail", "u", "p".toCharArray(), "", "", "Email", null);

        assertTrue(filter.matches(matchAll));
        assertFalse(filter.matches(wrongCategory));
        assertFalse(filter.matches(notFavorite));
    }

    @Test
    void emptyFilterMatchesAll() {
        EntryFilter filter = new EntryFilter.Builder().build();
        PasswordEntry entry = new PasswordEntry("Any", "u", "p".toCharArray(), "", "", "Cat", null);
        assertTrue(filter.matches(entry));
    }

    @Test
    void searchQueryMatchesTags() {
        EntryFilter filter = new EntryFilter.Builder().searchQuery("important").build();
        PasswordEntry match = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat",
            Arrays.asList("important", "work"));
        PasswordEntry noMatch = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat",
            Arrays.asList("personal"));
        PasswordEntry noTags = new PasswordEntry("Entry", "u", "p".toCharArray(), "", "", "Cat", null);
        assertTrue(filter.matches(match));
        assertFalse(filter.matches(noMatch));
        assertFalse(filter.matches(noTags));
    }

    @Test
    void filterByTags() {
        EntryFilter filter = new EntryFilter.Builder().tags(Arrays.asList("work")).build();
        PasswordEntry match = new PasswordEntry("Tagged", "u", "p".toCharArray(), "", "", "Cat",
            Arrays.asList("work", "important"));
        PasswordEntry noMatch = new PasswordEntry("NoTag", "u", "p".toCharArray(), "", "", "Cat",
            Arrays.asList("personal"));
        assertTrue(filter.matches(match));
        assertFalse(filter.matches(noMatch));
    }
}
