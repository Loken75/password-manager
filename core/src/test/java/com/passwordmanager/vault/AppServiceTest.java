package com.passwordmanager.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppServiceTest {
    private Vault vault;
    private AppService service;

    @BeforeEach
    void setUp() {
        vault = new Vault("testuser");
        service = new AppService(vault);
    }

    @Test
    void addEntry() {
        AppEntry entry = new AppEntry("MyApp", "user1", "1234".toCharArray(), "notes");
        service.addEntry(entry);
        assertEquals(1, vault.getAppEntries().size());
        assertEquals("MyApp", vault.getAppEntries().get(0).getTitle());
    }

    @Test
    void updateEntry() {
        AppEntry entry = new AppEntry("MyApp", "user1", "1234".toCharArray(), null);
        service.addEntry(entry);
        entry.setTitle("Updated");
        assertTrue(service.updateEntry(entry));
        assertEquals("Updated", vault.getAppEntries().get(0).getTitle());
    }

    @Test
    void deleteEntry() {
        AppEntry entry = new AppEntry("MyApp", "user1", "1234".toCharArray(), null);
        service.addEntry(entry);
        assertTrue(service.deleteEntry(entry.getId()));
        assertEquals(0, vault.getAppEntries().size());
    }

    @Test
    void search() {
        service.addEntry(new AppEntry("Banking App", "user1", "1234".toCharArray(), null));
        service.addEntry(new AppEntry("Social App", "admin", "5678".toCharArray(), null));

        List<AppEntry> results = service.search("banking");
        assertEquals(1, results.size());
        assertEquals("Banking App", results.get(0).getTitle());
    }

    @Test
    void searchByUsername() {
        service.addEntry(new AppEntry("App1", "admin", "1234".toCharArray(), null));
        service.addEntry(new AppEntry("App2", "user", "5678".toCharArray(), null));

        List<AppEntry> results = service.search("admin");
        assertEquals(1, results.size());
    }

    @Test
    void sortByTitle() {
        service.addEntry(new AppEntry("Zebra", null, "1".toCharArray(), null));
        service.addEntry(new AppEntry("Alpha", null, "2".toCharArray(), null));

        List<AppEntry> sorted = service.sorted(service.getReadOnlyList(), SortField.TITLE);
        assertEquals("Alpha", sorted.get(0).getTitle());
        assertEquals("Zebra", sorted.get(1).getTitle());
    }

    @Test
    void toggleFavorite() {
        AppEntry entry = new AppEntry("App", null, "1234".toCharArray(), null);
        service.addEntry(entry);
        assertFalse(entry.isFavorite());
        service.toggleFavorite(entry.getId());
        assertTrue(vault.getAppEntries().get(0).isFavorite());
    }
}
