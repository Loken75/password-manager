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
