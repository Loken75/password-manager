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
        PasswordEntry entry = new PasswordEntry("Title", "user", "pass".toCharArray(), "", "", "Cat", null);
        vault.addEntry(entry);
        assertEquals(1, vault.getEntries().size());
    }

    @Test
    void removeEntry() {
        Vault vault = new Vault("user");
        PasswordEntry entry = new PasswordEntry("Title", "user", "pass".toCharArray(), "", "", "Cat", null);
        vault.addEntry(entry);
        assertTrue(vault.removeEntry(entry));
        assertEquals(0, vault.getEntries().size());
    }

    @Test
    void removeEntryNotPresent() {
        Vault vault = new Vault("user");
        PasswordEntry entry = new PasswordEntry("Title", "user", "pass".toCharArray(), "", "", "Cat", null);
        assertFalse(vault.removeEntry(entry));
    }

    // ---------------------------------------------------------------
    // Dedup-by-id (R1 defensive layer): add* replaces any existing
    // entry with the same id instead of appending a duplicate.
    // ---------------------------------------------------------------

    @Test
    void addEntry_sameId_replacesInsteadOfDuplicating() {
        Vault vault = new Vault("user");
        PasswordEntry first = new PasswordEntry("Title", "old", "pass".toCharArray(), "", "", "Cat", null);
        vault.addEntry(first);
        PasswordEntry updated = new PasswordEntry("Title", "new", "pass".toCharArray(), "", "", "Cat", null);
        updated.setId(first.getId());

        vault.addEntry(updated);

        assertEquals(1, vault.getEntries().size(), "Same id must not create a duplicate");
        assertEquals("new", vault.getEntries().get(0).getUsername(), "Latest version wins");
    }

    @Test
    void addAppEntry_sameId_replacesInsteadOfDuplicating() {
        Vault vault = new Vault("user");
        AppEntry first = new AppEntry("MyApp", "old", "1234".toCharArray(), "notes");
        vault.addAppEntry(first);
        AppEntry updated = new AppEntry("MyApp", "new", "5678".toCharArray(), "notes");
        updated.setId(first.getId());

        vault.addAppEntry(updated);

        assertEquals(1, vault.getAppEntries().size());
        assertEquals("new", vault.getAppEntries().get(0).getUsername());
    }

    @Test
    void addSshKeyEntry_sameId_replacesInsteadOfDuplicating() {
        Vault vault = new Vault("user");
        SshKeyEntry first = new SshKeyEntry("key", "k1".toCharArray(), "pub", "RSA", "fp1");
        vault.addSshKeyEntry(first);
        SshKeyEntry updated = new SshKeyEntry("key", "k2".toCharArray(), "pub", "ED25519", "fp2");
        updated.setId(first.getId());

        vault.addSshKeyEntry(updated);

        assertEquals(1, vault.getSshKeyEntries().size());
        assertEquals("ED25519", vault.getSshKeyEntries().get(0).getKeyType());
    }

    @Test
    void addEntry_distinctIds_bothKept() {
        Vault vault = new Vault("user");
        vault.addEntry(new PasswordEntry("A", "a", "p".toCharArray(), "", "", "Cat", null));
        vault.addEntry(new PasswordEntry("B", "b", "p".toCharArray(), "", "", "Cat", null));
        assertEquals(2, vault.getEntries().size());
    }

    @Test
    void getEntriesIsUnmodifiable() {
        Vault vault = new Vault("user");
        vault.addEntry(new PasswordEntry("T", "u", "p".toCharArray(), "", "", "C", null));
        List<PasswordEntry> entries = vault.getEntries();
        assertThrows(UnsupportedOperationException.class, () -> entries.add(new PasswordEntry()));
    }

    @Test
    void getEntriesMutableIsMutable() {
        Vault vault = new Vault("user");
        vault.getEntriesMutable().add(new PasswordEntry("T", "u", "p".toCharArray(), "", "", "C", null));
        assertEquals(1, vault.getEntries().size());
    }

    @Test
    void wipeClearsAllData() {
        Vault vault = new Vault("user");
        vault.addEntry(new PasswordEntry("T", "u", "pass".toCharArray(), "", "", "C", null));
        vault.wipe();
        assertNull(vault.getUser());
        assertNull(vault.getCreatedAt());
        assertNull(vault.getUpdatedAt());
        assertEquals(0, vault.getEntries().size());
        assertEquals(0, vault.getCategories().size());
        assertEquals(0, vault.getSettings().size());
    }

    @Test
    void addAppEntry() {
        Vault vault = new Vault("user");
        AppEntry entry = new AppEntry("MyApp", "user1", "1234".toCharArray(), "notes");
        vault.addAppEntry(entry);
        assertEquals(1, vault.getAppEntries().size());
        assertEquals("MyApp", vault.getAppEntries().get(0).getTitle());
    }

    @Test
    void removeAppEntry() {
        Vault vault = new Vault("user");
        AppEntry entry = new AppEntry("MyApp", "user1", "1234".toCharArray(), null);
        vault.addAppEntry(entry);
        assertTrue(vault.removeAppEntry(entry));
        assertEquals(0, vault.getAppEntries().size());
    }

    @Test
    void wipeAlsoWipesAppEntries() {
        Vault vault = new Vault("user");
        vault.addEntry(new PasswordEntry("T", "u", "p".toCharArray(), "", "", "C", null));
        vault.addAppEntry(new AppEntry("App", "u", "1234".toCharArray(), null));
        vault.wipe();
        assertEquals(0, vault.getEntries().size());
        assertEquals(0, vault.getAppEntries().size());
    }

    @Test
    void getAppEntriesNullSafe() {
        // Simulate Gson deserialization where appEntries field was null (old vault)
        Vault vault = new Vault("user");
        vault.setAppEntries(null);
        assertNotNull(vault.getAppEntries());
        assertEquals(0, vault.getAppEntries().size());
    }

    @Test
    void ensureInitialized_fixesNullCollections() {
        Vault vault = new Vault("user");
        vault.setEntries(null);
        vault.setAppEntries(null);
        vault.setSshKeyEntries(null);
        vault.setCategories(null);
        vault.setSettings(null);
        vault.ensureInitialized();
        assertNotNull(vault.getEntries());
        assertNotNull(vault.getAppEntries());
        assertNotNull(vault.getSshKeyEntries());
        assertNotNull(vault.getCategories());
        assertNotNull(vault.getSettings());
    }

    @Test
    void ensureInitialized_preservesExistingData() {
        Vault vault = new Vault("user");
        vault.addEntry(new PasswordEntry("T", "u", "p".toCharArray(), "", "", "C", null));
        vault.ensureInitialized();
        assertEquals(1, vault.getEntries().size());
    }

    @Test
    void getCategoriesIsUnmodifiable() {
        Vault vault = new Vault("user");
        List<String> cats = vault.getCategories();
        assertThrows(UnsupportedOperationException.class, () -> cats.add("NewCat"));
    }

    @Test
    void getSettingsIsUnmodifiable() {
        Vault vault = new Vault("user");
        java.util.Map<String, Object> settings = vault.getSettings();
        assertThrows(UnsupportedOperationException.class, () -> settings.put("key", "val"));
    }

    @Test
    void getCategoriesMutableAllowsMutation() {
        Vault vault = new Vault("user");
        vault.getCategoriesMutable().add("TestCat");
        assertTrue(vault.getCategories().contains("TestCat"));
    }

    @Test
    void addSshKeyEntry() {
        Vault vault = new Vault("user");
        SshKeyEntry entry = new SshKeyEntry("mykey", null, "pub", "ED25519", "fp");
        vault.addSshKeyEntry(entry);
        assertEquals(1, vault.getSshKeyEntries().size());
    }

    @Test
    void removeSshKeyEntry() {
        Vault vault = new Vault("user");
        SshKeyEntry entry = new SshKeyEntry("mykey", null, "pub", "RSA", "fp");
        vault.addSshKeyEntry(entry);
        assertTrue(vault.removeSshKeyEntry(entry));
        assertEquals(0, vault.getSshKeyEntries().size());
    }

    @Test
    void defaultSettings() {
        Vault vault = new Vault("user");
        assertEquals(15, ((Number) vault.getSettings().get("auto_lock_minutes")).intValue());
        assertEquals(30, ((Number) vault.getSettings().get("clipboard_clear_seconds")).intValue());
        assertEquals(180, ((Number) vault.getSettings().get("password_expiry_days")).intValue());
    }
}
