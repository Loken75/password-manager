package com.passwordmanager.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
        VaultEntry entry = new VaultEntry("Gmail", "user@gmail.com",
            "pass123".toCharArray(), "https://gmail.com", "notes", "Email", null);
        service.addEntry(entry);
        assertEquals(1, vault.getEntries().size());
        assertEquals("Gmail", vault.getEntries().get(0).getTitle());
    }

    @Test
    void updateEntry() {
        VaultEntry entry = new VaultEntry("Gmail", "user@gmail.com",
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
        VaultEntry entry = new VaultEntry("Test", "user",
            "pass".toCharArray(), "url", "", "Autre", null);
        service.addEntry(entry);
        assertEquals(1, vault.getEntries().size());

        boolean deleted = service.deleteEntry(entry.getId());
        assertTrue(deleted);
        assertEquals(0, vault.getEntries().size());
    }

    @Test
    void deleteNonExistent() {
        assertFalse(service.deleteEntry("non-existent-id"));
    }

    @Test
    void search() {
        service.addEntry(new VaultEntry("Gmail", "user@gmail.com",
            "pass1".toCharArray(), "https://gmail.com", "", "Email", null));
        service.addEntry(new VaultEntry("Facebook", "user@fb.com",
            "pass2".toCharArray(), "https://facebook.com", "", "Social", null));
        service.addEntry(new VaultEntry("Bank", "mybank",
            "pass3".toCharArray(), "https://bank.com", "compte bancaire", "Bancaire", null));

        List<VaultEntry> results = service.search("gmail");
        assertEquals(1, results.size());
        assertEquals("Gmail", results.get(0).getTitle());

        results = service.search("user");
        assertEquals(2, results.size());

        results = service.search("bancaire");
        assertEquals(1, results.size());
    }

    @Test
    void searchEmpty() {
        service.addEntry(new VaultEntry("Test", "user",
            "pass".toCharArray(), "url", "", "Cat", null));
        List<VaultEntry> results = service.search("");
        assertEquals(1, results.size());

        results = service.search(null);
        assertEquals(1, results.size());
    }

    @Test
    void getByCategory() {
        service.addEntry(new VaultEntry("Gmail", "u", "p".toCharArray(), "", "", "Email", null));
        service.addEntry(new VaultEntry("Yahoo", "u", "p".toCharArray(), "", "", "Email", null));
        service.addEntry(new VaultEntry("Bank", "u", "p".toCharArray(), "", "", "Bancaire", null));

        List<VaultEntry> emails = service.getByCategory("Email");
        assertEquals(2, emails.size());

        List<VaultEntry> banking = service.getByCategory("Bancaire");
        assertEquals(1, banking.size());
    }

    @Test
    void sortByTitle() {
        service.addEntry(new VaultEntry("Zebra", "u", "p".toCharArray(), "", "", "Cat", null));
        service.addEntry(new VaultEntry("Alpha", "u", "p".toCharArray(), "", "", "Cat", null));
        service.addEntry(new VaultEntry("Middle", "u", "p".toCharArray(), "", "", "Cat", null));

        List<VaultEntry> sorted = service.sorted(vault.getEntries(), SortField.TITLE);
        assertEquals("Alpha", sorted.get(0).getTitle());
        assertEquals("Middle", sorted.get(1).getTitle());
        assertEquals("Zebra", sorted.get(2).getTitle());
    }

    @Test
    void sortByCategory() {
        service.addEntry(new VaultEntry("Z", "u", "p".toCharArray(), "", "", "Work", null));
        service.addEntry(new VaultEntry("A", "u", "p".toCharArray(), "", "", "Banking", null));

        List<VaultEntry> sorted = service.sorted(vault.getEntries(), SortField.CATEGORY);
        assertEquals("Banking", sorted.get(0).getCategory());
        assertEquals("Work", sorted.get(1).getCategory());
    }

    @Test
    void findDuplicatePasswords() {
        service.addEntry(new VaultEntry("Site1", "u", "samepass".toCharArray(), "", "", "Cat", null));
        service.addEntry(new VaultEntry("Site2", "u", "samepass".toCharArray(), "", "", "Cat", null));
        service.addEntry(new VaultEntry("Site3", "u", "unique".toCharArray(), "", "", "Cat", null));

        Map<String, List<VaultEntry>> dups = service.findDuplicatePasswords();
        assertEquals(1, dups.size());
        List<VaultEntry> dupGroup = dups.values().iterator().next();
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
        VaultEntry entry = new VaultEntry("Tagged", "u", "p".toCharArray(), "", "", "Cat",
            Arrays.asList("important", "work"));
        service.addEntry(entry);

        List<VaultEntry> results = service.search("important");
        assertEquals(1, results.size());
    }

    @Test
    void deleteEntryWipesPassword() {
        VaultEntry entry = new VaultEntry("Wipe", "u", "secret123".toCharArray(), "", "", "Cat", null);
        String id = entry.getId();
        service.addEntry(entry);

        service.deleteEntry(id);
        // After deletion, the entry's password should be wiped
        assertNull(entry.getPassword());
    }

    @Test
    void findOldPasswordsReturnsOldEntries() {
        VaultEntry old = new VaultEntry("Old", "u", "p".toCharArray(), "", "", "Cat", null);
        // Use a fixed timestamp well in the past (>180 days)
        old.setUpdatedAt("2024-01-01T00:00:00Z");
        vault.addEntry(old);

        VaultEntry recent = new VaultEntry("Recent", "u", "p".toCharArray(), "", "", "Cat", null);
        // updatedAt is set to now by constructor
        vault.addEntry(recent);

        List<VaultEntry> results = service.findOldPasswords(180);
        assertEquals(1, results.size());
        assertEquals("Old", results.get(0).getTitle());
    }

    @Test
    void findOldPasswordsEmptyForRecentEntries() {
        VaultEntry entry = new VaultEntry("New", "u", "p".toCharArray(), "", "", "Cat", null);
        service.addEntry(entry);
        List<VaultEntry> results = service.findOldPasswords(180);
        assertTrue(results.isEmpty());
    }

    @Test
    void findOldPasswordsSkipsInvalidDates() {
        VaultEntry entry = new VaultEntry("Bad", "u", "p".toCharArray(), "", "", "Cat", null);
        entry.setUpdatedAt("not-a-date");
        vault.addEntry(entry);
        List<VaultEntry> results = service.findOldPasswords(180);
        assertTrue(results.isEmpty());
    }

    @Test
    void sortByDateDescending() {
        VaultEntry e1 = new VaultEntry("First", "u", "p".toCharArray(), "", "", "Cat", null);
        e1.setUpdatedAt("2024-01-01T00:00:00Z");
        vault.addEntry(e1);

        VaultEntry e2 = new VaultEntry("Second", "u", "p".toCharArray(), "", "", "Cat", null);
        e2.setUpdatedAt("2024-06-01T00:00:00Z");
        vault.addEntry(e2);

        List<VaultEntry> sorted = service.sorted(vault.getEntries(), SortField.DATE);
        assertEquals("Second", sorted.get(0).getTitle());
        assertEquals("First", sorted.get(1).getTitle());
    }

    @Test
    void addEntrySetsTimestamp() {
        VaultEntry entry = new VaultEntry("TS", "u", "p".toCharArray(), "", "", "Cat", null);
        // Set timestamp to a known past value so we can verify addEntry updates it
        entry.setUpdatedAt("2020-01-01T00:00:00Z");
        service.addEntry(entry);
        assertNotNull(entry.getUpdatedAt());
        assertNotEquals("2020-01-01T00:00:00Z", entry.getUpdatedAt(),
                "addEntry should update the timestamp");
    }

    @Test
    void updateNonExistentEntryReturnsFalse() {
        VaultEntry phantom = new VaultEntry("Ghost", "u", "p".toCharArray(), "", "", "Cat", null);
        assertFalse(service.updateEntry(phantom));
    }

    @Test
    void findDuplicatePasswordsSkipsNullPasswords() {
        VaultEntry e1 = new VaultEntry("NoPass", "u", null, "", "", "Cat", null);
        vault.addEntry(e1);
        VaultEntry e2 = new VaultEntry("AlsoNoPass", "u", null, "", "", "Cat", null);
        vault.addEntry(e2);
        Map<String, List<VaultEntry>> dups = service.findDuplicatePasswords();
        assertTrue(dups.isEmpty());
    }

    @Test
    void findDuplicatePasswordsSkipsEmptyPasswords() {
        VaultEntry e1 = new VaultEntry("Empty", "u", new char[0], "", "", "Cat", null);
        vault.addEntry(e1);
        VaultEntry e2 = new VaultEntry("AlsoEmpty", "u", new char[0], "", "", "Cat", null);
        vault.addEntry(e2);
        Map<String, List<VaultEntry>> dups = service.findDuplicatePasswords();
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
        service.addEntry(new VaultEntry("Google", "u", "p".toCharArray(),
            "https://google.com", "", "Cat", null));
        List<VaultEntry> results = service.search("google.com");
        assertEquals(1, results.size());
        assertEquals("Google", results.get(0).getTitle());
    }

    @Test
    void searchByEmail() {
        VaultEntry entry = new VaultEntry("Gmail", "johndoe", "p".toCharArray(),
            "https://gmail.com", "", "Email", null);
        entry.setEmail("john@example.com");
        service.addEntry(entry);

        List<VaultEntry> results = service.search("john@example");
        assertEquals(1, results.size());
        assertEquals("Gmail", results.get(0).getTitle());
    }

    @Test
    void searchByPseudo() {
        VaultEntry entry = new VaultEntry("Discord", "user123", "p".toCharArray(),
            "", "", "Social", null);
        entry.setPseudo("CoolNick");
        service.addEntry(entry);

        List<VaultEntry> results = service.search("coolnick");
        assertEquals(1, results.size());
        assertEquals("Discord", results.get(0).getTitle());
    }

    @Test
    void sortByUsername() {
        VaultEntry e1 = new VaultEntry("A", "zorro", "p".toCharArray(), "", "", "Cat", null);
        VaultEntry e2 = new VaultEntry("B", "alpha", "p".toCharArray(), "", "", "Cat", null);
        service.addEntry(e1);
        service.addEntry(e2);

        List<VaultEntry> sorted = service.sorted(vault.getEntries(), SortField.USERNAME);
        assertEquals("alpha", sorted.get(0).getUsername());
        assertEquals("zorro", sorted.get(1).getUsername());
    }

    @Test
    void sortByEmail() {
        VaultEntry e1 = new VaultEntry("A", "u", "p".toCharArray(), "", "", "Cat", null);
        e1.setEmail("z@test.com");
        VaultEntry e2 = new VaultEntry("B", "u", "p".toCharArray(), "", "", "Cat", null);
        e2.setEmail("a@test.com");
        service.addEntry(e1);
        service.addEntry(e2);

        List<VaultEntry> sorted = service.sorted(vault.getEntries(), SortField.EMAIL);
        assertEquals("a@test.com", sorted.get(0).getEmail());
        assertEquals("z@test.com", sorted.get(1).getEmail());
    }

    @Test
    void sortByPseudo() {
        VaultEntry e1 = new VaultEntry("A", "u", "p".toCharArray(), "", "", "Cat", null);
        e1.setPseudo("Zeta");
        VaultEntry e2 = new VaultEntry("B", "u", "p".toCharArray(), "", "", "Cat", null);
        e2.setPseudo("Alpha");
        service.addEntry(e1);
        service.addEntry(e2);

        List<VaultEntry> sorted = service.sorted(vault.getEntries(), SortField.PSEUDO);
        assertEquals("Alpha", sorted.get(0).getPseudo());
        assertEquals("Zeta", sorted.get(1).getPseudo());
    }

    @Test
    void sortByUrl() {
        VaultEntry e1 = new VaultEntry("A", "u", "p".toCharArray(), "https://z.com", "", "Cat", null);
        VaultEntry e2 = new VaultEntry("B", "u", "p".toCharArray(), "https://a.com", "", "Cat", null);
        service.addEntry(e1);
        service.addEntry(e2);

        List<VaultEntry> sorted = service.sorted(vault.getEntries(), SortField.URL);
        assertEquals("https://a.com", sorted.get(0).getUrl());
        assertEquals("https://z.com", sorted.get(1).getUrl());
    }

    @Test
    void updateEntrySetsTimestamp() {
        VaultEntry entry = new VaultEntry("TS", "u", "p".toCharArray(), "", "", "Cat", null);
        service.addEntry(entry);
        // Set to a known past value so we can detect the update
        entry.setUpdatedAt("2020-01-01T00:00:00Z");
        entry.setTitle("Updated");
        service.updateEntry(entry);
        assertNotNull(entry.getUpdatedAt());
        assertNotEquals("2020-01-01T00:00:00Z", entry.getUpdatedAt(),
                "updateEntry should update the timestamp");
    }
}
