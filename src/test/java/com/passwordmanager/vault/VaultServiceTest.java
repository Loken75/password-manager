package com.passwordmanager.vault;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for VaultService CRUD and search operations.
 */
public class VaultServiceTest {
    private Vault vault;
    private VaultService service;

    @Before
    public void setUp() {
        vault = new Vault("testuser");
        service = new VaultService(vault);
    }

    @Test
    public void testAddEntry() {
        VaultEntry entry = new VaultEntry("Gmail", "user@gmail.com", "pass123", "https://gmail.com", "notes", "Email", null);
        service.addEntry(entry);
        assertEquals(1, vault.getEntries().size());
        assertEquals("Gmail", vault.getEntries().get(0).getTitle());
    }

    @Test
    public void testUpdateEntry() {
        VaultEntry entry = new VaultEntry("Gmail", "user@gmail.com", "pass123", "https://gmail.com", "", "Email", null);
        service.addEntry(entry);

        entry.setTitle("Gmail Updated");
        entry.setPassword("newPass456!");
        boolean updated = service.updateEntry(entry);
        assertTrue(updated);
        assertEquals("Gmail Updated", vault.getEntries().get(0).getTitle());
    }

    @Test
    public void testDeleteEntry() {
        VaultEntry entry = new VaultEntry("Test", "user", "pass", "url", "", "Autre", null);
        service.addEntry(entry);
        assertEquals(1, vault.getEntries().size());

        boolean deleted = service.deleteEntry(entry.getId());
        assertTrue(deleted);
        assertEquals(0, vault.getEntries().size());
    }

    @Test
    public void testDeleteNonExistent() {
        assertFalse(service.deleteEntry("non-existent-id"));
    }

    @Test
    public void testSearch() {
        service.addEntry(new VaultEntry("Gmail", "user@gmail.com", "pass1", "https://gmail.com", "", "Email", null));
        service.addEntry(new VaultEntry("Facebook", "user@fb.com", "pass2", "https://facebook.com", "", "Social", null));
        service.addEntry(new VaultEntry("Bank", "mybank", "pass3", "https://bank.com", "compte bancaire", "Bancaire", null));

        List<VaultEntry> results = service.search("gmail");
        assertEquals(1, results.size());
        assertEquals("Gmail", results.get(0).getTitle());

        results = service.search("user");
        assertEquals(2, results.size());

        results = service.search("bancaire");
        assertEquals(1, results.size());
    }

    @Test
    public void testSearchEmpty() {
        service.addEntry(new VaultEntry("Test", "user", "pass", "url", "", "Cat", null));
        List<VaultEntry> results = service.search("");
        assertEquals(1, results.size());

        results = service.search(null);
        assertEquals(1, results.size());
    }

    @Test
    public void testGetByCategory() {
        service.addEntry(new VaultEntry("Gmail", "u", "p", "", "", "Email", null));
        service.addEntry(new VaultEntry("Yahoo", "u", "p", "", "", "Email", null));
        service.addEntry(new VaultEntry("Bank", "u", "p", "", "", "Bancaire", null));

        List<VaultEntry> emails = service.getByCategory("Email");
        assertEquals(2, emails.size());

        List<VaultEntry> banking = service.getByCategory("Bancaire");
        assertEquals(1, banking.size());
    }

    @Test
    public void testSort() {
        service.addEntry(new VaultEntry("Zebra", "u", "p", "", "", "Cat", null));
        service.addEntry(new VaultEntry("Alpha", "u", "p", "", "", "Cat", null));
        service.addEntry(new VaultEntry("Middle", "u", "p", "", "", "Cat", null));

        List<VaultEntry> sorted = service.sorted(vault.getEntries(), "title");
        assertEquals("Alpha", sorted.get(0).getTitle());
        assertEquals("Middle", sorted.get(1).getTitle());
        assertEquals("Zebra", sorted.get(2).getTitle());
    }

    @Test
    public void testFindDuplicatePasswords() {
        service.addEntry(new VaultEntry("Site1", "u", "samepass", "", "", "Cat", null));
        service.addEntry(new VaultEntry("Site2", "u", "samepass", "", "", "Cat", null));
        service.addEntry(new VaultEntry("Site3", "u", "unique", "", "", "Cat", null));

        Map<String, List<VaultEntry>> dups = service.findDuplicatePasswords();
        assertEquals(1, dups.size());
        assertTrue(dups.containsKey("samepass"));
        assertEquals(2, dups.get("samepass").size());
    }

    @Test
    public void testAddCategory() {
        int initial = vault.getCategories().size();
        service.addCategory("NewCat");
        assertEquals(initial + 1, vault.getCategories().size());

        // Adding same again should not duplicate
        service.addCategory("NewCat");
        assertEquals(initial + 1, vault.getCategories().size());
    }

    @Test
    public void testSearchByTags() {
        VaultEntry entry = new VaultEntry("Tagged", "u", "p", "", "", "Cat", Arrays.asList("important", "work"));
        service.addEntry(entry);

        List<VaultEntry> results = service.search("important");
        assertEquals(1, results.size());
    }
}
