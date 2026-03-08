package com.passwordmanager.vault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VaultExporter CSV and JSON export.
 */
class VaultExporterTest {
    private Vault vault;
    private VaultExporter exporter;

    @BeforeEach
    void setUp() {
        Gson gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(char[].class, new VaultManager.CharArrayAdapter())
                .create();
        vault = new Vault("testuser");
        exporter = new VaultExporter(gson);
    }

    @Test
    void exportCsvHeader() {
        String csv = new String(exporter.exportAsCsv(vault));
        assertTrue(csv.startsWith("type,title,username,email,password,url,notes,category,tags,favorite,pin\n"));
    }

    @Test
    void exportCsvWithEntries() {
        vault.addEntry(new PasswordEntry("Gmail", "user@gmail.com",
                "pass123".toCharArray(), "https://gmail.com", "notes", "Email",
                Arrays.asList("tag1", "tag2")));

        String csv = new String(exporter.exportAsCsv(vault));
        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[1].contains("Gmail"));
        assertTrue(lines[1].contains("user@gmail.com"));
        assertTrue(lines[1].contains("pass123"));
        assertTrue(lines[1].contains("tag1;tag2"));
    }

    @Test
    void exportCsvEscapesCommas() {
        vault.addEntry(new PasswordEntry("My, Site", "user",
                "pass".toCharArray(), "", "", "Cat", null));

        String csv = new String(exporter.exportAsCsv(vault));
        assertTrue(csv.contains("\"My, Site\""));
    }

    @Test
    void exportCsvFormulaInjectionProtection() {
        vault.addEntry(new PasswordEntry("=FORMULA", "user",
                "pass".toCharArray(), "", "", "Cat", null));

        String csv = new String(exporter.exportAsCsv(vault));
        // Formula-triggering characters should be prefixed with single quote
        assertTrue(csv.contains("\"'=FORMULA\""));
    }

    @Test
    void exportCsvNullPassword() {
        vault.addEntry(new PasswordEntry("NoPass", "user",
                null, "", "", "Cat", null));

        String csv = new String(exporter.exportAsCsv(vault));
        // Should not throw, empty password field
        assertNotNull(csv);
        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
    }

    @Test
    void exportJsonValid() {
        vault.addEntry(new PasswordEntry("Test", "user",
                "pass".toCharArray(), "http://test.com", "", "Cat", null));

        String json = new String(exporter.exportAsJson(vault));
        assertNotNull(json);
        assertTrue(json.contains("\"Test\""));
        assertTrue(json.contains("\"user\""));
    }

    @Test
    void exportCsvWithEmail() {
        PasswordEntry entry = new PasswordEntry("Gmail", "johndoe",
                "pass123".toCharArray(), "https://gmail.com", "notes", "Email", null);
        entry.setEmail("john@gmail.com");
        vault.addEntry(entry);

        String csv = new String(exporter.exportAsCsv(vault));
        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[1].contains("john@gmail.com"));
    }

    @Test
    void exportJsonEmptyVault() {
        String json = new String(exporter.exportAsJson(vault));
        assertNotNull(json);
        assertTrue(json.contains("\"entries\""));
    }

    @Test
    void exportCsvRoundTripWithImporter() {
        VaultImporter importer = new VaultImporter(new GsonBuilder().create());

        vault.addEntry(new PasswordEntry("Site1", "user1",
                "pass1".toCharArray(), "http://site1.com", "note1", "Email", null));
        vault.addEntry(new PasswordEntry("Site2", "user2",
                "pass2".toCharArray(), "http://site2.com", "", "Work",
                Arrays.asList("important")));

        String csv = new String(exporter.exportAsCsv(vault));

        Vault importVault = new Vault("importuser");
        int count = importer.importFromCsv(importVault, csv);
        assertEquals(2, count);

        // Find entries by position (they're appended after default entries)
        int offset = importVault.getEntries().size() - 2;
        assertEquals("Site1", importVault.getEntries().get(offset).getTitle());
        assertEquals("Site2", importVault.getEntries().get(offset + 1).getTitle());
    }
}
