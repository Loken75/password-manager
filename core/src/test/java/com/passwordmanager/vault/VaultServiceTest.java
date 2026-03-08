package com.passwordmanager.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for VaultService CRUD, search, and sort operations.
 */
class VaultServiceTest {
    private Vault vault;
    private VaultService service;

    @BeforeEach
    void setUp() {
        vault = new Vault("testuser");
        service = new VaultService(vault);
    }

    @Test
    void addEntry() {
        PasswordEntry entry = new PasswordEntry("Gmail", "user@gmail.com",
            "pass123".toCharArray(), "https://gmail.com", "notes", "Email", null);
        service.addEntry(entry);
        assertEquals(1, vault.getEntries().size());
        assertEquals("Gmail", vault.getEntries().get(0).getTitle());
    }

    @Test
    void updateEntry() {
        PasswordEntry entry = new PasswordEntry("Gmail", "user@gmail.com",
            "pass123".toCharArray(), "https://gmail.com", "", "Email", null);
        service.addEntry(entry);

        entry.setTitle("Gmail Updated");
        entry.setPassword("newPass456!".toCharArray());
        boolean updated = service.updateEntry(entry);
        assertTrue(updated);
        assertEquals("Gmail Updated", vault.getEntries().get(0).getTitle());
    }

    @Test
    void deleteEntry() {
        PasswordEntry entry = new PasswordEntry("Test", "user",
            "pass".toCharArray(), "url", "", "Autre", null);
        service.addEntry(entry);
        assertEquals(1, vault.getEntries().size());

        boolean deleted = service.deleteEntry(entry.getId());
        assertTrue(deleted);
        // Soft-delete: entry is still in mutable list as tombstone, but hidden from active list
        assertEquals(1, vault.getEntriesMutable().size());
        assertTrue(vault.getEntriesMutable().get(0).isDeleted());
        assertEquals(0, service.getPasswordService().getActiveList().size());
    }

    @Test
    void deleteNonExistent() {
        assertFalse(service.deleteEntry("non-existent-id"));
    }

    @Test
    void search() {
        service.addEntry(new PasswordEntry("Gmail", "user@gmail.com",
            "pass1".toCharArray(), "https://gmail.com", "", "Email", null));
        service.addEntry(new PasswordEntry("Facebook", "user@fb.com",
            "pass2".toCharArray(), "https://facebook.com", "", "Social", null));
        service.addEntry(new PasswordEntry("Bank", "mybank",
            "pass3".toCharArray(), "https://bank.com", "compte bancaire", "Bancaire", null));

        List<PasswordEntry> results = service.search("gmail");
        assertEquals(1, results.size());
        assertEquals("Gmail", results.get(0).getTitle());

        results = service.search("user");
        assertEquals(2, results.size());

        results = service.search("bancaire");
        assertEquals(1, results.size());
    }

    @Test
    void searchEmpty() {
        service.addEntry(new PasswordEntry("Test", "user",
            "pass".toCharArray(), "url", "", "Cat", null));
        List<PasswordEntry> results = service.search("");
        assertEquals(1, results.size());

        results = service.search(null);
        assertEquals(1, results.size());
    }

    @Test
    void getByCategory() {
        service.addEntry(new PasswordEntry("Gmail", "u", "p".toCharArray(), "", "", "Email", null));
        service.addEntry(new PasswordEntry("Yahoo", "u", "p".toCharArray(), "", "", "Email", null));
        service.addEntry(new PasswordEntry("Bank", "u", "p".toCharArray(), "", "", "Bancaire", null));

        List<PasswordEntry> emails = service.getByCategory("Email");
        assertEquals(2, emails.size());

        List<PasswordEntry> banking = service.getByCategory("Bancaire");
        assertEquals(1, banking.size());
    }

    @Test
    void sortByTitle() {
        service.addEntry(new PasswordEntry("Zebra", "u", "p".toCharArray(), "", "", "Cat", null));
        service.addEntry(new PasswordEntry("Alpha", "u", "p".toCharArray(), "", "", "Cat", null));
        service.addEntry(new PasswordEntry("Middle", "u", "p".toCharArray(), "", "", "Cat", null));

        List<PasswordEntry> sorted = service.sorted(vault.getEntries(), SortField.TITLE);
        assertEquals("Alpha", sorted.get(0).getTitle());
        assertEquals("Middle", sorted.get(1).getTitle());
        assertEquals("Zebra", sorted.get(2).getTitle());
    }

    @Test
    void sortByCategory() {
        service.addEntry(new PasswordEntry("Z", "u", "p".toCharArray(), "", "", "Work", null));
        service.addEntry(new PasswordEntry("A", "u", "p".toCharArray(), "", "", "Banking", null));

        List<PasswordEntry> sorted = service.sorted(vault.getEntries(), SortField.CATEGORY);
        assertEquals("Banking", sorted.get(0).getCategory());
        assertEquals("Work", sorted.get(1).getCategory());
    }

    @Test
    void findDuplicatePasswords() {
        service.addEntry(new PasswordEntry("Site1", "u", "samepass".toCharArray(), "", "", "Cat", null));
        service.addEntry(new PasswordEntry("Site2", "u", "samepass".toCharArray(), "", "", "Cat", null));
        service.addEntry(new PasswordEntry("Site3", "u", "unique".toCharArray(), "", "", "Cat", null));

        Map<String, List<PasswordEntry>> dups = service.findDuplicatePasswords();
        assertEquals(1, dups.size());
        List<PasswordEntry> dupGroup = dups.values().iterator().next();
        assertEquals(2, dupGroup.size());
    }

    @Test
    void addCategory() {
        int initial = vault.getCategories().size();
        service.addCategory("NewCat");
        assertEquals(initial + 1, vault.getCategories().size());

        // Adding same again should not duplicate
        service.addCategory("NewCat");
        assertEquals(initial + 1, vault.getCategories().size());
    }

    @Test
    void searchByTags() {
        PasswordEntry entry = new PasswordEntry("Tagged", "u", "p".toCharArray(), "", "", "Cat",
            Arrays.asList("important", "work"));
        service.addEntry(entry);

        List<PasswordEntry> results = service.search("important");
        assertEquals(1, results.size());
    }

    @Test
    void deleteEntryWipesPassword() {
        PasswordEntry entry = new PasswordEntry("Wipe", "u", "secret123".toCharArray(), "", "", "Cat", null);
        String id = entry.getId();
        service.addEntry(entry);

        service.deleteEntry(id);
        // After deletion, the entry's password should be wiped
        assertNull(entry.getPassword());
    }

    @Test
    void findOldPasswordsReturnsOldEntries() {
        PasswordEntry old = new PasswordEntry("Old", "u", "p".toCharArray(), "", "", "Cat", null);
        // Use a fixed timestamp well in the past (>180 days)
        old.setUpdatedAt("2024-01-01T00:00:00Z");
        vault.addEntry(old);

        PasswordEntry recent = new PasswordEntry("Recent", "u", "p".toCharArray(), "", "", "Cat", null);
        // updatedAt is set to now by constructor
        vault.addEntry(recent);

        List<PasswordEntry> results = service.findOldPasswords(180);
        assertEquals(1, results.size());
        assertEquals("Old", results.get(0).getTitle());
    }

    @Test
    void findOldPasswordsEmptyForRecentEntries() {
        PasswordEntry entry = new PasswordEntry("New", "u", "p".toCharArray(), "", "", "Cat", null);
        service.addEntry(entry);
        List<PasswordEntry> results = service.findOldPasswords(180);
        assertTrue(results.isEmpty());
    }

    @Test
    void findOldPasswordsSkipsInvalidDates() {
        PasswordEntry entry = new PasswordEntry("Bad", "u", "p".toCharArray(), "", "", "Cat", null);
        entry.setUpdatedAt("not-a-date");
        vault.addEntry(entry);
        List<PasswordEntry> results = service.findOldPasswords(180);
        assertTrue(results.isEmpty());
    }

    @Test
    void sortByDateDescending() {
        PasswordEntry e1 = new PasswordEntry("First", "u", "p".toCharArray(), "", "", "Cat", null);
        e1.setUpdatedAt("2024-01-01T00:00:00Z");
        vault.addEntry(e1);

        PasswordEntry e2 = new PasswordEntry("Second", "u", "p".toCharArray(), "", "", "Cat", null);
        e2.setUpdatedAt("2024-06-01T00:00:00Z");
        vault.addEntry(e2);

        List<PasswordEntry> sorted = service.sorted(vault.getEntries(), SortField.DATE);
        assertEquals("Second", sorted.get(0).getTitle());
        assertEquals("First", sorted.get(1).getTitle());
    }

    @Test
    void addEntrySetsTimestamp() {
        PasswordEntry entry = new PasswordEntry("TS", "u", "p".toCharArray(), "", "", "Cat", null);
        // Set timestamp to a known past value so we can verify addEntry updates it
        entry.setUpdatedAt("2020-01-01T00:00:00Z");
        service.addEntry(entry);
        assertNotNull(entry.getUpdatedAt());
        assertNotEquals("2020-01-01T00:00:00Z", entry.getUpdatedAt(),
                "addEntry should update the timestamp");
    }

    @Test
    void updateNonExistentEntryReturnsFalse() {
        PasswordEntry phantom = new PasswordEntry("Ghost", "u", "p".toCharArray(), "", "", "Cat", null);
        assertFalse(service.updateEntry(phantom));
    }

    @Test
    void findDuplicatePasswordsSkipsNullPasswords() {
        PasswordEntry e1 = new PasswordEntry("NoPass", "u", null, "", "", "Cat", null);
        vault.addEntry(e1);
        PasswordEntry e2 = new PasswordEntry("AlsoNoPass", "u", null, "", "", "Cat", null);
        vault.addEntry(e2);
        Map<String, List<PasswordEntry>> dups = service.findDuplicatePasswords();
        assertTrue(dups.isEmpty());
    }

    @Test
    void findDuplicatePasswordsSkipsEmptyPasswords() {
        PasswordEntry e1 = new PasswordEntry("Empty", "u", new char[0], "", "", "Cat", null);
        vault.addEntry(e1);
        PasswordEntry e2 = new PasswordEntry("AlsoEmpty", "u", new char[0], "", "", "Cat", null);
        vault.addEntry(e2);
        Map<String, List<PasswordEntry>> dups = service.findDuplicatePasswords();
        assertTrue(dups.isEmpty());
    }

    @Test
    void removeCategory() {
        service.addCategory("ToRemove");
        assertTrue(vault.getCategories().contains("ToRemove"));
        assertTrue(service.removeCategory("ToRemove"));
        assertFalse(vault.getCategories().contains("ToRemove"));
    }

    @Test
    void removeCategoryNonExistent() {
        assertFalse(service.removeCategory("NonExistent"));
    }

    @Test
    void searchByUrl() {
        service.addEntry(new PasswordEntry("Google", "u", "p".toCharArray(),
            "https://google.com", "", "Cat", null));
        List<PasswordEntry> results = service.search("google.com");
        assertEquals(1, results.size());
        assertEquals("Google", results.get(0).getTitle());
    }

    @Test
    void searchByEmail() {
        PasswordEntry entry = new PasswordEntry("Gmail", "johndoe", "p".toCharArray(),
            "https://gmail.com", "", "Email", null);
        entry.setEmail("john@example.com");
        service.addEntry(entry);

        List<PasswordEntry> results = service.search("john@example");
        assertEquals(1, results.size());
        assertEquals("Gmail", results.get(0).getTitle());
    }

    @Test
    void sortByUsername() {
        PasswordEntry e1 = new PasswordEntry("A", "zorro", "p".toCharArray(), "", "", "Cat", null);
        PasswordEntry e2 = new PasswordEntry("B", "alpha", "p".toCharArray(), "", "", "Cat", null);
        service.addEntry(e1);
        service.addEntry(e2);

        List<PasswordEntry> sorted = service.sorted(vault.getEntries(), SortField.USERNAME);
        assertEquals("alpha", sorted.get(0).getUsername());
        assertEquals("zorro", sorted.get(1).getUsername());
    }

    @Test
    void sortByEmail() {
        PasswordEntry e1 = new PasswordEntry("A", "u", "p".toCharArray(), "", "", "Cat", null);
        e1.setEmail("z@test.com");
        PasswordEntry e2 = new PasswordEntry("B", "u", "p".toCharArray(), "", "", "Cat", null);
        e2.setEmail("a@test.com");
        service.addEntry(e1);
        service.addEntry(e2);

        List<PasswordEntry> sorted = service.sorted(vault.getEntries(), SortField.EMAIL);
        assertEquals("a@test.com", sorted.get(0).getEmail());
        assertEquals("z@test.com", sorted.get(1).getEmail());
    }

    @Test
    void sortByUrl() {
        PasswordEntry e1 = new PasswordEntry("A", "u", "p".toCharArray(), "https://z.com", "", "Cat", null);
        PasswordEntry e2 = new PasswordEntry("B", "u", "p".toCharArray(), "https://a.com", "", "Cat", null);
        service.addEntry(e1);
        service.addEntry(e2);

        List<PasswordEntry> sorted = service.sorted(vault.getEntries(), SortField.URL);
        assertEquals("https://a.com", sorted.get(0).getUrl());
        assertEquals("https://z.com", sorted.get(1).getUrl());
    }

    @Test
    void updateEntrySetsTimestamp() {
        PasswordEntry entry = new PasswordEntry("TS", "u", "p".toCharArray(), "", "", "Cat", null);
        service.addEntry(entry);
        // Set to a known past value so we can detect the update
        entry.setUpdatedAt("2020-01-01T00:00:00Z");
        entry.setTitle("Updated");
        service.updateEntry(entry);
        assertNotNull(entry.getUpdatedAt());
        assertNotEquals("2020-01-01T00:00:00Z", entry.getUpdatedAt(),
                "updateEntry should update the timestamp");
    }

    @Test
    void toggleFavorite() {
        PasswordEntry entry = new PasswordEntry("Fav", "u", "p".toCharArray(), "", "", "Cat", null);
        service.addEntry(entry);
        assertFalse(entry.isFavorite());

        service.toggleFavorite(entry.getId());
        assertTrue(vault.getEntries().get(0).isFavorite());

        service.toggleFavorite(entry.getId());
        assertFalse(vault.getEntries().get(0).isFavorite());
    }

    @Test
    void bulkSetFavorite() {
        PasswordEntry e1 = new PasswordEntry("A", "u", "p".toCharArray(), "", "", "Cat", null);
        PasswordEntry e2 = new PasswordEntry("B", "u", "p".toCharArray(), "", "", "Cat", null);
        service.addEntry(e1);
        service.addEntry(e2);

        int count = service.bulkSetFavorite(List.of(e1.getId(), e2.getId()), true);
        assertEquals(2, count);
        assertTrue(vault.getEntries().get(0).isFavorite());
        assertTrue(vault.getEntries().get(1).isFavorite());
    }

    @Test
    void sortedPutsFavoritesFirst() {
        PasswordEntry normal = new PasswordEntry("Alpha", "u", "p".toCharArray(), "", "", "Cat", null);
        PasswordEntry fav = new PasswordEntry("Zebra", "u", "p".toCharArray(), "", "", "Cat", null);
        fav.setFavorite(true);
        service.addEntry(normal);
        service.addEntry(fav);

        List<PasswordEntry> sorted = service.sorted(vault.getEntries(), SortField.TITLE);
        assertEquals("Zebra", sorted.get(0).getTitle(), "Favorite should come first despite alphabetical order");
        assertEquals("Alpha", sorted.get(1).getTitle());
    }

    @Test
    void filterByEntryFilter() {
        PasswordEntry email = new PasswordEntry("Gmail", "u", "p".toCharArray(), "", "", "Email", null);
        PasswordEntry banking = new PasswordEntry("Bank", "u", "p".toCharArray(), "", "", "Banking", null);
        service.addEntry(email);
        service.addEntry(banking);

        EntryFilter filter = new EntryFilter.Builder().category("Email").build();
        List<PasswordEntry> filtered = service.filter(vault.getEntries(), filter);
        assertEquals(1, filtered.size());
        assertEquals("Gmail", filtered.get(0).getTitle());
    }
}
