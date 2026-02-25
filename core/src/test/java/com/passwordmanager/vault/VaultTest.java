package com.passwordmanager.vault;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VaultTest {

    @Test
    void defaultConstructorSetsCategories() {
        Vault vault = new Vault("alice");
        assertEquals("alice", vault.getUser());
        assertEquals("2.0", vault.getVersion());
        assertNotNull(vault.getCategories());
        assertEquals(Vault.DEFAULT_CATEGORIES, vault.getCategories());
        assertNotNull(vault.getSettings());
        assertNotNull(vault.getCreatedAt());
        assertNotNull(vault.getUpdatedAt());
    }

    @Test
    void constructorWithCustomCategories() {
        List<String> cats = Arrays.asList("Cat1", "Cat2");
        Vault vault = new Vault("bob", cats);
        assertEquals(2, vault.getCategories().size());
        assertEquals("Cat1", vault.getCategories().get(0));
    }

    @Test
    void addEntry() {
        Vault vault = new Vault("user");
        VaultEntry entry = new VaultEntry("Title", "user", "pass".toCharArray(), "", "", "Cat", null);
        vault.addEntry(entry);
        assertEquals(1, vault.getEntries().size());
    }

    @Test
    void removeEntry() {
        Vault vault = new Vault("user");
        VaultEntry entry = new VaultEntry("Title", "user", "pass".toCharArray(), "", "", "Cat", null);
        vault.addEntry(entry);
        assertTrue(vault.removeEntry(entry));
        assertEquals(0, vault.getEntries().size());
    }

    @Test
    void removeEntryNotPresent() {
        Vault vault = new Vault("user");
        VaultEntry entry = new VaultEntry("Title", "user", "pass".toCharArray(), "", "", "Cat", null);
        assertFalse(vault.removeEntry(entry));
    }

    @Test
    void getEntriesIsUnmodifiable() {
        Vault vault = new Vault("user");
        vault.addEntry(new VaultEntry("T", "u", "p".toCharArray(), "", "", "C", null));
        List<VaultEntry> entries = vault.getEntries();
        assertThrows(UnsupportedOperationException.class, () -> entries.add(new VaultEntry()));
    }

    @Test
    void getEntriesMutableIsMutable() {
        Vault vault = new Vault("user");
        vault.getEntriesMutable().add(new VaultEntry("T", "u", "p".toCharArray(), "", "", "C", null));
        assertEquals(1, vault.getEntries().size());
    }

    @Test
    void wipeClearsAllData() {
        Vault vault = new Vault("user");
        vault.addEntry(new VaultEntry("T", "u", "pass".toCharArray(), "", "", "C", null));
        vault.wipe();
        assertNull(vault.getUser());
        assertNull(vault.getCreatedAt());
        assertNull(vault.getUpdatedAt());
        assertEquals(0, vault.getEntries().size());
        assertEquals(0, vault.getCategories().size());
        assertEquals(0, vault.getSettings().size());
    }

    @Test
    void defaultSettings() {
        Vault vault = new Vault("user");
        assertEquals(15, ((Number) vault.getSettings().get("auto_lock_minutes")).intValue());
        assertEquals(30, ((Number) vault.getSettings().get("clipboard_clear_seconds")).intValue());
        assertEquals(180, ((Number) vault.getSettings().get("password_expiry_days")).intValue());
    }
}
