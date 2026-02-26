package com.passwordmanager.vault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VaultImporter CSV and JSON import.
 */
class VaultImporterTest {
    private Vault vault;
    private VaultImporter importer;

    @BeforeEach
    void setUp() {
        Gson gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(char[].class, new VaultManager.CharArrayAdapter())
                .create();
        vault = new Vault("testuser");
        importer = new VaultImporter(gson);
    }

    @Test
    void importCsvWithHeaders() {
        String csv = "title,username,password,url,notes,category,tags\n"
                + "Gmail,user@gmail.com,pass123,https://gmail.com,my mail,Email,important;work\n"
                + "Bank,admin,secret,https://bank.com,,Bancaire,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(2, count);
        assertEquals(2, vault.getEntries().size());

        VaultEntry gmail = vault.getEntries().get(vault.getEntries().size() - 2);
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

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
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

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("My, Site", entry.getTitle());
        assertEquals("notes with \"quotes\"", entry.getNotes());
    }

    @Test
    void importCsvEmptyPasswordSetsNull() {
        String csv = "title,username,password\n"
                + "NoPass,user,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertNull(entry.getPassword());
    }

    @Test
    void importFromJsonValid() {
        String json = "{\"entries\":[{\"title\":\"Test\",\"username\":\"u\","
                + "\"password\":\"p\",\"url\":\"http://test.com\",\"notes\":\"\","
                + "\"category\":\"Cat\",\"tags\":[\"t1\"]}]}";

        int count = importer.importFromJson(vault, json);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
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

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertNotEquals("old-id", entry.getId());
    }

    @Test
    void importCsvWithBomHeader() {
        // UTF-8 BOM (U+FEFF) prepended to header
        String csv = "\uFEFFtitle,username,password\n"
                + "BomTest,user,pass\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("BomTest", entry.getTitle());
    }

    @Test
    void setDefaultImportCategoryNull() {
        importer.setDefaultImportCategory(null);
        String csv = "title,username,password\nTest,user,pass\n";
        importer.importFromCsv(vault, csv);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Other", entry.getCategory());
    }

    @Test
    void setDefaultImportCategoryEmpty() {
        importer.setDefaultImportCategory("");
        String csv = "title,username,password\nTest,user,pass\n";
        importer.importFromCsv(vault, csv);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Other", entry.getCategory());
    }

    @Test
    void setDefaultImportCategoryCustom() {
        importer.setDefaultImportCategory("Importé");
        String csv = "title,username,password\nTest,user,pass\n";
        importer.importFromCsv(vault, csv);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
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
        String csv = "title,username,email,pseudo,password,url,notes,category,tags\n"
                + "Gmail,johndoe,john@gmail.com,JohnD,pass123,https://gmail.com,my mail,Email,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("Gmail", entry.getTitle());
        assertEquals("johndoe", entry.getUsername());
        assertEquals("john@gmail.com", entry.getEmail());
        assertEquals("JohnD", entry.getPseudo());
        assertArrayEquals("pass123".toCharArray(), entry.getPassword());
    }

    @Test
    void importCsvWithEmailAliases() {
        String csv = "titre,identifiant,mail,surnom,mot de passe,url,notes,categorie,tags\n"
                + "Site,user1,user@mail.com,nick1,pass,http://site.com,,Work,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("user@mail.com", entry.getEmail());
        assertEquals("nick1", entry.getPseudo());
    }

    @Test
    void importFromJsonWithEmailAndPseudo() {
        String json = "{\"entries\":[{\"title\":\"Test\",\"username\":\"u\","
                + "\"email\":\"test@test.com\",\"pseudo\":\"TestNick\","
                + "\"password\":\"p\",\"url\":\"http://test.com\",\"notes\":\"\","
                + "\"category\":\"Cat\",\"tags\":[]}]}";

        int count = importer.importFromJson(vault, json);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("test@test.com", entry.getEmail());
        assertEquals("TestNick", entry.getPseudo());
    }

    @Test
    void importCsvSanitizesControlCharacters() {
        String csv = "title,username,password\n"
                + "Clean\u0007Title,user\u0001name,pass\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("CleanTitle", entry.getTitle());
        assertEquals("username", entry.getUsername());
    }
}
