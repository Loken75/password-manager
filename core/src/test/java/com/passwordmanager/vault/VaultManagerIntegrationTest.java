package com.passwordmanager.vault;

import com.passwordmanager.crypto.VaultSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for VaultManager: create, save, load, change password round-trips.
 */
class VaultManagerIntegrationTest {
    @TempDir
    Path tempDir;

    private VaultManager manager;

    @BeforeEach
    void setUp() {
        manager = new VaultManager(tempDir.toString());
    }

    @Test
    void createAndLoadVault() throws Exception {
        char[] password = "TestP@ssw0rd!123".toCharArray();

        VaultLoadResult created = manager.createVault("alice", password);
        assertNotNull(created.getVault());
        assertNotNull(created.getSession());
        assertEquals("alice", created.getVault().getUser());
        assertTrue(manager.vaultExists("alice"));

        // Load the vault back
        VaultLoadResult loaded = manager.loadVault("alice", password);
        assertEquals("alice", loaded.getVault().getUser());
        assertEquals("2.0", loaded.getVault().getVersion());

        created.getSession().destroy();
        loaded.getSession().destroy();
    }

    @Test
    void loadWithWrongPasswordThrows() throws Exception {
        char[] password = "CorrectP@ss!1234".toCharArray();
        char[] wrong = "Wr0ngP@ssword!12".toCharArray();

        VaultLoadResult created = manager.createVault("bob", password);
        created.getSession().destroy();

        assertThrows(Exception.class, () -> manager.loadVault("bob", wrong));
    }

    @Test
    void saveAndLoadPreservesEntries() throws Exception {
        char[] password = "DataP@ssw0rd!123".toCharArray();

        VaultLoadResult result = manager.createVault("charlie", password);
        Vault vault = result.getVault();
        VaultSession session = result.getSession();

        vault.addEntry(new VaultEntry("Gmail", "user@gmail.com",
                "secret123".toCharArray(), "https://gmail.com", "notes", "Email", null));
        vault.addEntry(new VaultEntry("Bank", "mybank",
                "banking!456".toCharArray(), "https://bank.com", "", "Banking", null));

        manager.saveVault(vault, "charlie", session);

        VaultLoadResult loaded = manager.loadVault("charlie", password);
        assertEquals(2, loaded.getVault().getEntries().size());
        assertEquals("Gmail", loaded.getVault().getEntries().get(0).getTitle());
        assertArrayEquals("secret123".toCharArray(),
                loaded.getVault().getEntries().get(0).getPassword());

        session.destroy();
        loaded.getSession().destroy();
    }

    @Test
    void changePasswordAndReload() throws Exception {
        char[] oldPwd = "OldP@ssw0rd!1234".toCharArray();
        char[] newPwd = "NewP@ssw0rd!5678".toCharArray();

        VaultLoadResult result = manager.createVault("dave", oldPwd);
        Vault vault = result.getVault();
        VaultSession session = result.getSession();

        vault.addEntry(new VaultEntry("TestEntry", "user",
                "entrypass".toCharArray(), "", "", "Cat", null));
        manager.saveVault(vault, "dave", session);

        VaultSession newSession = manager.changeMasterPassword("dave", vault, session, newPwd);

        // Load with new password
        VaultLoadResult loaded = manager.loadVault("dave", newPwd);
        assertEquals(1, loaded.getVault().getEntries().size());
        assertEquals("TestEntry", loaded.getVault().getEntries().get(0).getTitle());

        // Old password should fail
        assertThrows(Exception.class, () -> manager.loadVault("dave", oldPwd));

        newSession.destroy();
        loaded.getSession().destroy();
    }

    @Test
    void reloadVaultWithSession() throws Exception {
        char[] password = "ReloadP@ss!12345".toCharArray();

        VaultLoadResult result = manager.createVault("eve", password);
        Vault vault = result.getVault();
        VaultSession session = result.getSession();

        vault.addEntry(new VaultEntry("Entry1", "u", "p".toCharArray(), "", "", "Cat", null));
        manager.saveVault(vault, "eve", session);

        // Reload using session (no password needed)
        Vault reloaded = manager.reloadVault("eve", session);
        assertEquals(1, reloaded.getEntries().size());
        assertEquals("Entry1", reloaded.getEntries().get(0).getTitle());

        session.destroy();
    }

    @Test
    void listUsersAndDelete() throws Exception {
        char[] password = "ListP@ssw0rd!123".toCharArray();

        manager.createVault("user_a", password).getSession().destroy();
        manager.createVault("user_b", password).getSession().destroy();

        String[] users = manager.listUsers();
        assertEquals(2, users.length);
        assertEquals("user_a", users[0]);
        assertEquals("user_b", users[1]);

        assertTrue(manager.deleteVault("user_a"));
        assertFalse(manager.vaultExists("user_a"));
        assertTrue(manager.vaultExists("user_b"));

        assertFalse(manager.deleteVault("nonexistent"));
    }

    @Test
    void vaultExistsReturnsFalseForMissing() {
        assertFalse(manager.vaultExists("nobody"));
    }

    @Test
    void importCsvThroughManager() throws Exception {
        char[] password = "ImportP@ss!12345".toCharArray();

        VaultLoadResult result = manager.createVault("frank", password);
        Vault vault = result.getVault();

        String csv = "title,username,password,url,notes,category,tags\n"
                + "Site1,user1,pass1,http://s1.com,,Cat,\n";

        int count = manager.importFromCsv(vault, csv);
        assertEquals(1, count);
        assertEquals(1, vault.getEntries().size());

        result.getSession().destroy();
    }

    @Test
    void exportCsvThroughManager() throws Exception {
        char[] password = "ExportP@ss!12345".toCharArray();

        VaultLoadResult result = manager.createVault("grace", password);
        Vault vault = result.getVault();
        vault.addEntry(new VaultEntry("Exported", "user",
                "pwd".toCharArray(), "", "", "Cat", null));

        String csv = new String(manager.exportAsCsv(vault));
        assertTrue(csv.contains("Exported"));
        assertTrue(csv.startsWith("title,"));

        String json = new String(manager.exportAsJson(vault));
        assertTrue(json.contains("Exported"));

        result.getSession().destroy();
    }

    // === Username validation (path traversal prevention) ===

    @Test
    void rejectsNullUsername() {
        assertThrows(IllegalArgumentException.class, () -> manager.getVaultPath(null));
    }

    @Test
    void rejectsEmptyUsername() {
        assertThrows(IllegalArgumentException.class, () -> manager.getVaultPath(""));
    }

    @Test
    void rejectsUsernameWithDots() {
        assertThrows(IllegalArgumentException.class, () -> manager.getVaultPath("../etc"));
    }

    @Test
    void rejectsUsernameWithSlash() {
        assertThrows(IllegalArgumentException.class, () -> manager.getVaultPath("user/admin"));
    }

    @Test
    void rejectsUsernameWithSpecialChars() {
        assertThrows(IllegalArgumentException.class, () -> manager.getVaultPath("user@host"));
        assertThrows(IllegalArgumentException.class, () -> manager.getVaultPath("user name"));
        assertThrows(IllegalArgumentException.class, () -> manager.getVaultPath("user;drop"));
    }

    @Test
    void acceptsValidUsername() {
        String path = manager.getVaultPath("alice_123");
        assertTrue(path.endsWith("vault_alice_123.enc"));
    }

    // === deleteVault also removes backups ===

    @Test
    void deleteVaultRemovesBackups() throws Exception {
        char[] password = "DeleteBak@ss!123".toCharArray();

        VaultLoadResult result = manager.createVault("backupuser", password);
        Vault vault = result.getVault();
        VaultSession session = result.getSession();

        // Save multiple times to generate a .bak file
        vault.addEntry(new VaultEntry("E1", "u", "p".toCharArray(), "", "", "C", null));
        manager.saveVault(vault, "backupuser", session);
        vault.addEntry(new VaultEntry("E2", "u", "p".toCharArray(), "", "", "C", null));
        manager.saveVault(vault, "backupuser", session);

        // Verify backup exists
        File dir = tempDir.toFile();
        File[] backups = dir.listFiles((d, name) ->
            name.startsWith("vault_backupuser") && name.endsWith(".bak"));
        assertNotNull(backups);
        assertTrue(backups.length > 0, "Should have at least one backup file");

        // Delete vault
        assertTrue(manager.deleteVault("backupuser"));

        // Verify vault and backups are gone
        assertFalse(manager.vaultExists("backupuser"));
        File[] remainingBackups = dir.listFiles((d, name) ->
            name.startsWith("vault_backupuser") && name.endsWith(".bak"));
        assertTrue(remainingBackups == null || remainingBackups.length == 0,
            "All backup files should be deleted");

        session.destroy();
    }
}
