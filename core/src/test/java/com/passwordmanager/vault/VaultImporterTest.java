package com.passwordmanager.vault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VaultImporter CSV and JSON import.
 */
class VaultImporterTest {
    private Vault vault;
    private VaultImporter importer;
    private VaultExporter exporter;

    @BeforeEach
    void setUp() {
        Gson gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(char[].class, new VaultManager.CharArrayAdapter())
                .create();
        vault = new Vault("testuser");
        importer = new VaultImporter(gson);
        exporter = new VaultExporter(gson);
    }

    @Test
    void importCsvWithHeaders() {
        String csv = "title,username,password,url,notes,category,tags\n"
                + "Gmail,user@gmail.com,pass123,https://gmail.com,my mail,Email,important;work\n"
                + "Bank,admin,secret,https://bank.com,,Bancaire,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(2, count);
        assertEquals(2, vault.getEntries().size());

        PasswordEntry gmail = vault.getEntries().get(vault.getEntries().size() - 2);
        assertEquals("Gmail", gmail.getTitle());
        assertEquals("user@gmail.com", gmail.getUsername());
        assertArrayEquals("pass123".toCharArray(), gmail.getPassword());
        assertEquals("https://gmail.com", gmail.getUrl());
        assertEquals("my mail", gmail.getNotes());
        assertEquals("Email", gmail.getCategory());
        assertEquals(2, gmail.getTags().size());
    }

    @Test
    void importCsvSemicolonSeparator() {
        String csv = "titre;identifiant;mot de passe;url;notes;categorie;tags\n"
                + "Site1;user1;pass1;http://site1.com;notes1;Work;\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Site1", entry.getTitle());
        assertEquals("user1", entry.getUsername());
        assertArrayEquals("pass1".toCharArray(), entry.getPassword());
    }

    @Test
    void importCsvNoHeaders() {
        String csv = "col1,col2,col3,col4,col5,col6,col7\n"
                + "MyTitle,myuser,mypass,http://url.com,some notes,Cat,tag1\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);
    }

    @Test
    void importCsvEmptyContent() {
        assertEquals(0, importer.importFromCsv(vault, ""));
        assertEquals(0, importer.importFromCsv(vault, "title,username,password\n"));
    }

    @Test
    void importCsvSkipsEmptyLines() {
        String csv = "title,username,password\n"
                + "\n"
                + "Test,user,pass\n"
                + "\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);
    }

    @Test
    void importCsvQuotedFields() {
        String csv = "title,username,password,url,notes,category,tags\n"
                + "\"My, Site\",user,pass,url,\"notes with \"\"quotes\"\"\",Cat,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("My, Site", entry.getTitle());
        assertEquals("notes with \"quotes\"", entry.getNotes());
    }

    @Test
    void importCsvEmptyPasswordSetsNull() {
        String csv = "title,username,password\n"
                + "NoPass,user,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertNull(entry.getPassword());
    }

    @Test
    void importFromJsonValid() {
        String json = "{\"entries\":[{\"title\":\"Test\",\"username\":\"u\","
                + "\"password\":\"p\",\"url\":\"http://test.com\",\"notes\":\"\","
                + "\"category\":\"Cat\",\"tags\":[\"t1\"]}]}";

        int count = importer.importFromJson(vault, json);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Test", entry.getTitle());
    }

    @Test
    void importFromJsonEmpty() {
        assertEquals(0, importer.importFromJson(vault, "{\"entries\":[]}"));
        assertEquals(0, importer.importFromJson(vault, "{}"));
    }

    @Test
    void importFromJsonAssignsNewIds() {
        String json = "{\"entries\":[{\"id\":\"old-id\",\"title\":\"Test\","
                + "\"username\":\"u\",\"password\":\"p\",\"category\":\"Cat\"}]}";

        importer.importFromJson(vault, json);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertNotEquals("old-id", entry.getId());
    }

    @Test
    void importCsvWithBomHeader() {
        // UTF-8 BOM (U+FEFF) prepended to header
        String csv = "\uFEFFtitle,username,password\n"
                + "BomTest,user,pass\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("BomTest", entry.getTitle());
    }

    @Test
    void setDefaultImportCategoryNull() {
        importer.setDefaultImportCategory(null);
        String csv = "title,username,password\nTest,user,pass\n";
        importer.importFromCsv(vault, csv);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Other", entry.getCategory());
    }

    @Test
    void setDefaultImportCategoryEmpty() {
        importer.setDefaultImportCategory("");
        String csv = "title,username,password\nTest,user,pass\n";
        importer.importFromCsv(vault, csv);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Other", entry.getCategory());
    }

    @Test
    void setDefaultImportCategoryCustom() {
        importer.setDefaultImportCategory("Importé");
        String csv = "title,username,password\nTest,user,pass\n";
        importer.importFromCsv(vault, csv);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Importé", entry.getCategory());
    }

    @Test
    void importFromJsonMalformedReturnsZero() {
        assertEquals(0, importer.importFromJson(vault, "not valid json {{{"));
    }

    @Test
    void importFromJsonNullEntriesReturnsZero() {
        assertEquals(0, importer.importFromJson(vault, "{\"owner\":\"test\"}"));
    }

    @Test
    void importCsvWithEmailAndPseudo() {
        // Pseudo column is accepted for backward compat but ignored when username is present
        String csv = "title,username,email,pseudo,password,url,notes,category,tags\n"
                + "Gmail,johndoe,john@gmail.com,JohnD,pass123,https://gmail.com,my mail,Email,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Gmail", entry.getTitle());
        assertEquals("johndoe", entry.getUsername());
        assertEquals("john@gmail.com", entry.getEmail());
        assertArrayEquals("pass123".toCharArray(), entry.getPassword());
    }

    @Test
    void importCsvWithEmailAliases() {
        // "surnom" (pseudo alias) is ignored when "identifiant" (username alias) is present
        String csv = "titre,identifiant,mail,surnom,mot de passe,url,notes,categorie,tags\n"
                + "Site,user1,user@mail.com,nick1,pass,http://site.com,,Work,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("user1", entry.getUsername());
        assertEquals("user@mail.com", entry.getEmail());
    }

    @Test
    void importFromJsonWithEmailIgnoresPseudo() {
        // JSON with pseudo field: Gson ignores unknown fields, pseudo is silently dropped
        String json = "{\"entries\":[{\"title\":\"Test\",\"username\":\"u\","
                + "\"email\":\"test@test.com\",\"pseudo\":\"TestNick\","
                + "\"password\":\"p\",\"url\":\"http://test.com\",\"notes\":\"\","
                + "\"category\":\"Cat\",\"tags\":[]}]}";

        int count = importer.importFromJson(vault, json);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("test@test.com", entry.getEmail());
    }

    @Test
    void importCsvPseudoMapsToUsernameWhenNoUsernameColumn() {
        // When CSV has pseudo column but no username column, pseudo maps to username
        String csv = "title,pseudo,password,url\n"
                + "Discord,CoolNick,pass123,https://discord.com\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("CoolNick", entry.getUsername());
    }

    @Test
    void importCsvSanitizesControlCharacters() {
        String csv = "title,username,password\n"
                + "Clean\u0007Title,user\u0001name,pass\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("CleanTitle", entry.getTitle());
        assertEquals("username", entry.getUsername());
    }

    @Test
    void importCsvQuotedFieldWithNewline() {
        // RFC 4180: newlines inside quoted fields should be preserved as field content
        String csv = "title,username,password,url,notes,category,tags\n"
                + "Gmail,user,pass,url,\"line1\nline2\",Cat,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Gmail", entry.getTitle());
        assertEquals("line1\nline2", entry.getNotes());
    }

    @Test
    void importCsvQuotedFieldWithCRLF() {
        // RFC 4180: CRLF inside quoted fields should be preserved
        String csv = "title,username,password,url,notes,category,tags\n"
                + "Site,user,pass,url,\"line1\r\nline2\",Cat,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Site", entry.getTitle());
        assertEquals("line1\r\nline2", entry.getNotes());
    }

    @Test
    void importCsvMultipleEntriesWithQuotedNewlines() {
        // Multiple records where some fields contain newlines
        String csv = "title,username,password,url,notes,category,tags\n"
                + "Site1,user1,pass1,url1,\"note with\nnewline\",Cat1,\n"
                + "Site2,user2,pass2,url2,simple note,Cat2,\n"
                + "Site3,user3,pass3,url3,\"multi\nline\nnotes\",Cat3,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(3, count);

        PasswordEntry e1 = vault.getEntries().get(vault.getEntries().size() - 3);
        assertEquals("Site1", e1.getTitle());
        assertEquals("note with\nnewline", e1.getNotes());

        PasswordEntry e2 = vault.getEntries().get(vault.getEntries().size() - 2);
        assertEquals("Site2", e2.getTitle());
        assertEquals("simple note", e2.getNotes());

        PasswordEntry e3 = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Site3", e3.getTitle());
        assertEquals("multi\nline\nnotes", e3.getNotes());
    }

    @Test
    void importCsvQuotedFieldWithCommaAndNewline() {
        // Field containing both comma and newline inside quotes
        String csv = "title,username,password,url,notes,category,tags\n"
                + "Site,user,pass,url,\"notes with, comma\nand newline\",Cat,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("notes with, comma\nand newline", entry.getNotes());
    }

    @Test
    void importCsvQuotedFieldWithEscapedQuotesAndNewline() {
        // Field containing escaped quotes and newlines
        String csv = "title,username,password,url,notes,category,tags\n"
                + "Site,user,pass,url,\"said \"\"hello\"\"\nthen left\",Cat,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("said \"hello\"\nthen left", entry.getNotes());
    }

    @Test
    void importCsvNullContentReturnsZero() {
        assertEquals(0, importer.importFromCsv(vault, null));
    }

    @Test
    void importCsvContentWithoutTrailingNewline() {
        // CSV content that does not end with a newline
        String csv = "title,username,password\n"
                + "Test,user,pass";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        PasswordEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Test", entry.getTitle());
    }

    @Test
    void importCsvExporterRoundTripColumnCount() {
        // Verify that export produces correct column count for all entry types
        Gson gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(char[].class, new VaultManager.CharArrayAdapter())
                .create();
        VaultExporter exporter = new VaultExporter(gson);

        Vault exportVault = new Vault("testuser");
        exportVault.addEntry(new PasswordEntry("MySite", "user1",
                "pass1".toCharArray(), "http://site.com", "some notes", "Work", null));

        AppEntry app = new AppEntry();
        app.setTitle("MyApp");
        app.setUsername("appuser");
        app.setPin("1234".toCharArray());
        app.setNotes("app notes");
        exportVault.addAppEntry(app);

        String csv = new String(exporter.exportAsCsv(exportVault));
        String[] lines = csv.split("\n");

        // Header: 11 columns = 10 commas
        assertEquals(11, countFields(lines[0]));

        // Each data row must also have 11 fields
        for (int i = 1; i < lines.length; i++) {
            assertEquals(11, countFields(lines[i]),
                    "Row " + i + " has wrong column count: " + lines[i]);
        }

        // Round-trip: re-import and verify
        Vault importVault = new Vault("importuser");
        int count = importer.importFromCsv(importVault, csv);
        assertEquals(2, count);
        assertEquals(1, importVault.getEntries().size());
        assertEquals(1, importVault.getAppEntries().size());

        assertEquals("MySite", importVault.getEntries().get(0).getTitle());
        assertEquals("MyApp", importVault.getAppEntries().get(0).getTitle());
    }

    // ---- Round-trip tests covering both entry types ----

    @Test
    void csvRoundTripBothEntryTypes() {
        Vault exportVault = new Vault("testuser");

        // PasswordEntry with all fields
        PasswordEntry pwd = new PasswordEntry("Gmail", "johndoe", "john@gmail.com",
                "S3cret!".toCharArray(), "https://gmail.com", "Personal email", "Email",
                Arrays.asList("google", "personal"));
        pwd.setFavorite(true);
        exportVault.addEntry(pwd);

        // AppEntry with username and pin
        AppEntry app = new AppEntry("Slack", "slackuser", "7890".toCharArray(), "Work chat app");
        app.setFavorite(false);
        exportVault.addAppEntry(app);

        // Export to CSV
        String csv = new String(exporter.exportAsCsv(exportVault));

        // Re-import into a fresh vault
        Vault importVault = new Vault("importuser");
        int count = importer.importFromCsv(importVault, csv);

        assertEquals(2, count);
        assertEquals(1, importVault.getEntries().size());
        assertEquals(1, importVault.getAppEntries().size());

        // Verify PasswordEntry fields
        PasswordEntry iPwd = importVault.getEntries().get(0);
        assertEquals("Gmail", iPwd.getTitle());
        assertEquals("johndoe", iPwd.getUsername());
        assertEquals("john@gmail.com", iPwd.getEmail());
        assertArrayEquals("S3cret!".toCharArray(), iPwd.getPassword());
        assertEquals("https://gmail.com", iPwd.getUrl());
        assertEquals("Personal email", iPwd.getNotes());
        assertEquals("Email", iPwd.getCategory());
        assertEquals(Arrays.asList("google", "personal"), iPwd.getTags());
        assertTrue(iPwd.isFavorite());

        // Verify AppEntry fields
        AppEntry iApp = importVault.getAppEntries().get(0);
        assertEquals("Slack", iApp.getTitle());
        assertEquals("slackuser", iApp.getUsername());
        assertArrayEquals("7890".toCharArray(), iApp.getPin());
        assertEquals("Work chat app", iApp.getNotes());
        assertFalse(iApp.isFavorite());
    }

    @Test
    void jsonRoundTripBothEntryTypes() {
        Vault exportVault = new Vault("testuser");

        // PasswordEntry with all fields
        PasswordEntry pwd = new PasswordEntry("GitHub", "devuser", "dev@github.com",
                "gh-t0ken!".toCharArray(), "https://github.com", "Dev account", "Work",
                Arrays.asList("dev", "vcs"));
        pwd.setFavorite(false);
        exportVault.addEntry(pwd);

        // AppEntry with username and pin
        AppEntry app = new AppEntry("Authy", "authuser", "1234".toCharArray(), "2FA app");
        app.setFavorite(true);
        exportVault.addAppEntry(app);

        // Export to JSON
        String json = new String(exporter.exportAsJson(exportVault));

        // Re-import into a fresh vault
        Vault importVault = new Vault("importuser");
        int count = importer.importFromJson(importVault, json);

        assertEquals(2, count);
        assertEquals(1, importVault.getEntries().size());
        assertEquals(1, importVault.getAppEntries().size());

        // Verify PasswordEntry fields
        PasswordEntry iPwd = importVault.getEntries().get(0);
        assertEquals("GitHub", iPwd.getTitle());
        assertEquals("devuser", iPwd.getUsername());
        assertEquals("dev@github.com", iPwd.getEmail());
        assertArrayEquals("gh-t0ken!".toCharArray(), iPwd.getPassword());
        assertEquals("https://github.com", iPwd.getUrl());
        assertEquals("Dev account", iPwd.getNotes());
        assertEquals("Work", iPwd.getCategory());
        assertEquals(Arrays.asList("dev", "vcs"), iPwd.getTags());
        assertFalse(iPwd.isFavorite());

        // Verify AppEntry fields
        AppEntry iApp = importVault.getAppEntries().get(0);
        assertEquals("Authy", iApp.getTitle());
        assertEquals("authuser", iApp.getUsername());
        assertArrayEquals("1234".toCharArray(), iApp.getPin());
        assertEquals("2FA app", iApp.getNotes());
        assertTrue(iApp.isFavorite());
    }

    @Test
    void csvImportWithoutTypeColumnDefaultsToPasswordEntry() {
        // CSV without the "type" column -- retrocompat: all entries should import as PasswordEntry
        String csv = "title,username,email,password,url,notes,category,tags\n"
                + "Gmail,user1,user1@gmail.com,pass1,https://gmail.com,notes1,Email,tag1;tag2\n"
                + "Bank,admin,,secret,https://bank.com,,Banking,\n"
                + "Forum,poster,poster@forum.com,f0rum,,My forum,Social networks,fun\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(3, count);

        // All entries must be PasswordEntry (no AppEntry)
        assertEquals(3, vault.getEntries().size());
        assertEquals(0, vault.getAppEntries().size());

        // Verify first entry
        PasswordEntry e1 = vault.getEntries().get(0);
        assertEquals("Gmail", e1.getTitle());
        assertEquals("user1", e1.getUsername());
        assertEquals("user1@gmail.com", e1.getEmail());
        assertArrayEquals("pass1".toCharArray(), e1.getPassword());
        assertEquals("https://gmail.com", e1.getUrl());
        assertEquals("notes1", e1.getNotes());
        assertEquals("Email", e1.getCategory());
        assertEquals(Arrays.asList("tag1", "tag2"), e1.getTags());

        // Second entry with empty email and category
        PasswordEntry e2 = vault.getEntries().get(1);
        assertEquals("Bank", e2.getTitle());
        assertEquals("admin", e2.getUsername());
        assertEquals("Banking", e2.getCategory());

        // Third entry
        PasswordEntry e3 = vault.getEntries().get(2);
        assertEquals("Forum", e3.getTitle());
        assertEquals("poster@forum.com", e3.getEmail());
    }

    @Test
    void csvRoundTripSpecialCharactersInNotes() {
        Vault exportVault = new Vault("testuser");

        // Notes with commas, double quotes, and newlines
        PasswordEntry entry = new PasswordEntry("Tricky Site", "user",
                "pass".toCharArray(), "https://tricky.com",
                "Line 1, with comma\nLine 2 with \"quotes\"\nLine 3 end",
                "Other", null);
        exportVault.addEntry(entry);

        // Export to CSV
        String csv = new String(exporter.exportAsCsv(exportVault));

        // Re-import into a fresh vault
        Vault importVault = new Vault("importuser");
        int count = importer.importFromCsv(importVault, csv);

        assertEquals(1, count);

        PasswordEntry imported = importVault.getEntries().get(0);
        assertEquals("Tricky Site", imported.getTitle());
        assertEquals("user", imported.getUsername());
        assertArrayEquals("pass".toCharArray(), imported.getPassword());
        assertEquals("https://tricky.com", imported.getUrl());
        // Notes must survive the round-trip with commas, quotes, and newlines intact
        assertEquals("Line 1, with comma\nLine 2 with \"quotes\"\nLine 3 end",
                imported.getNotes());
        assertEquals("Other", imported.getCategory());
    }

    /** Counts CSV fields by splitting on commas (does not handle quoted commas, use for simple values). */
    private int countFields(String line) {
        // Use the same parsing logic: count separators + 1
        int count = 1;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                }
            } else {
                if (c == '"') inQuotes = true;
                else if (c == ',') count++;
            }
        }
        return count;
    }
}
