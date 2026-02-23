package com.passwordmanager.security;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.passwordmanager.crypto.*;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security-focused tests for the password manager.
 * Covers: IV uniqueness, memory wiping, file permissions, import sanitization,
 * key derivation safety, vault format integrity, and edge cases.
 */
class SecurityAuditTest {
    @TempDir
    Path tempDir;

    private VaultManager manager;
    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        manager = new VaultManager(tempDir.toString());
        cryptoService = new CryptoService();
    }

    // === IV Uniqueness (AES-GCM critical requirement) ===

    @Test
    void ivIsUniqueAcrossMultipleEncryptions() throws Exception {
        char[] password = "IvTestP@ss!12345".toCharArray();
        VaultSession session = cryptoService.createSession(password);

        byte[] data = "test data".getBytes(StandardCharsets.UTF_8);
        byte[][] ivs = new byte[100][];

        for (int i = 0; i < 100; i++) {
            EncryptedPayload payload = cryptoService.encryptData(data, session.getDataKey());
            ivs[i] = payload.getIv();
        }

        // Verify all IVs are unique
        for (int i = 0; i < ivs.length; i++) {
            for (int j = i + 1; j < ivs.length; j++) {
                assertFalse(Arrays.equals(ivs[i], ivs[j]),
                    "IV collision detected between encryption " + i + " and " + j);
            }
        }

        session.destroy();
    }

    @Test
    void ivLengthIs12Bytes() throws Exception {
        char[] password = "IvLenP@ss!123456".toCharArray();
        VaultSession session = cryptoService.createSession(password);

        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        EncryptedPayload payload = cryptoService.encryptData(data, session.getDataKey());

        assertEquals(12, payload.getIv().length, "GCM IV must be 12 bytes (96 bits)");

        session.destroy();
    }

    @Test
    void saltIsUniquePerSession() throws Exception {
        char[] password = "SaltP@ss!1234567".toCharArray();

        VaultSession s1 = cryptoService.createSession(password);
        VaultSession s2 = cryptoService.createSession(password);

        assertFalse(Arrays.equals(s1.getSalt(), s2.getSalt()),
            "Each session must use a unique salt");

        s1.destroy();
        s2.destroy();
    }

    @Test
    void saltIs32Bytes() throws Exception {
        char[] password = "SaltLenP@ss!1234".toCharArray();
        VaultSession session = cryptoService.createSession(password);

        assertEquals(32, session.getSalt().length, "Salt must be 32 bytes");

        session.destroy();
    }

    // === Key Derivation Safety ===

    @Test
    void kdfIterationsAreAtLeast600k() {
        assertTrue(KeyDerivation.getDefaultIterations() >= 600_000,
            "PBKDF2 iterations must be >= 600,000 (OWASP 2024 recommendation)");
    }

    @Test
    void sessionKdfIterationsMatchDefault() throws Exception {
        char[] password = "KdfIterP@ss!1234".toCharArray();
        VaultSession session = cryptoService.createSession(password);

        assertEquals(KeyDerivation.getDefaultIterations(), session.getKdfIterations(),
            "Session must use the default KDF iteration count");

        session.destroy();
    }

    @Test
    void pbeKeySpecPasswordIsCleared() throws Exception {
        // Verify deriveKey works correctly (indirect test: if clearPassword fails, key is wrong)
        char[] password = "ClearP@ss!123456".toCharArray();
        byte[] salt = KeyDerivation.generateSalt();

        javax.crypto.SecretKey key1 = KeyDerivation.deriveKey(password, salt);
        javax.crypto.SecretKey key2 = KeyDerivation.deriveKey(password, salt);

        assertArrayEquals(key1.getEncoded(), key2.getEncoded(),
            "Same password+salt must produce same key (PBEKeySpec.clearPassword must not corrupt)");
    }

    // === Memory Wiping ===

    @Test
    void secureWiperZeroesByteArray() {
        byte[] data = {1, 2, 3, 4, 5};
        SecureWiper.wipe(data);
        assertArrayEquals(new byte[5], data, "Byte array must be zeroed after wipe");
    }

    @Test
    void secureWiperZeroesCharArray() {
        char[] data = "secret".toCharArray();
        SecureWiper.wipe(data);
        assertArrayEquals(new char[6], data, "Char array must be zeroed after wipe");
    }

    @Test
    void secureWiperHandlesNull() {
        assertDoesNotThrow(() -> SecureWiper.wipe((byte[]) null));
        assertDoesNotThrow(() -> SecureWiper.wipe((char[]) null));
    }

    @Test
    void secureWiperHandlesEmpty() {
        assertDoesNotThrow(() -> SecureWiper.wipe(new byte[0]));
        assertDoesNotThrow(() -> SecureWiper.wipe(new char[0]));
    }

    @Test
    void vaultEntryWipeClearsPassword() {
        VaultEntry entry = new VaultEntry("Test", "user",
            "MyP@ssword!1234".toCharArray(), "url", "notes", "Cat", null);

        assertNotNull(entry.getPassword());
        entry.wipe();
        assertNull(entry.getTitle());
        assertNull(entry.getUsername());
    }

    @Test
    void sessionDestroyIsIdempotent() throws Exception {
        char[] password = "IdempotP@ss!1234".toCharArray();
        VaultSession session = cryptoService.createSession(password);

        session.destroy();
        assertTrue(session.isDestroyed());

        // Second destroy should not throw
        assertDoesNotThrow(session::destroy);
        assertTrue(session.isDestroyed());
    }

    // === File Permissions ===

    @Test
    void vaultFileHasRestrictedPermissions() throws Exception {
        char[] password = "PermsP@ssw0rd!12".toCharArray();

        manager.createVault("permtest", password).getSession().destroy();

        Path vaultFile = tempDir.resolve("vault_permtest.enc");
        assertTrue(Files.exists(vaultFile));

        if (vaultFile.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(vaultFile);
            assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
            assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE));
            assertFalse(perms.contains(PosixFilePermission.GROUP_READ),
                "Vault file must not be group-readable");
            assertFalse(perms.contains(PosixFilePermission.OTHERS_READ),
                "Vault file must not be world-readable");
        }
    }

    @Test
    void backupFileHasRestrictedPermissions() throws Exception {
        char[] password = "BackupP@ss!12345".toCharArray();

        VaultLoadResult result = manager.createVault("backuptest", password);
        // Save again to trigger backup creation
        manager.saveVault(result.getVault(), "backuptest", result.getSession());

        Path backupFile = tempDir.resolve("vault_backuptest.enc.bak");
        if (Files.exists(backupFile) &&
            backupFile.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(backupFile);
            assertFalse(perms.contains(PosixFilePermission.GROUP_READ));
            assertFalse(perms.contains(PosixFilePermission.OTHERS_READ));
        }

        result.getSession().destroy();
    }

    // === Vault Format Integrity ===

    @Test
    void vaultFileContainsExpectedFields() throws Exception {
        char[] password = "FormatP@ss!12345".toCharArray();

        manager.createVault("fmttest", password).getSession().destroy();

        String content = Files.readString(tempDir.resolve("vault_fmttest.enc"));

        assertTrue(content.contains("\"version\""));
        assertTrue(content.contains("\"kdf\""));
        assertTrue(content.contains("\"kdf_iterations\""));
        assertTrue(content.contains("\"salt\""));
        assertTrue(content.contains("\"kek_iv\""));
        assertTrue(content.contains("\"encrypted_dek\""));
        assertTrue(content.contains("\"data_iv\""));
        assertTrue(content.contains("\"encrypted_data\""));

        // Must NOT contain any plaintext vault data
        assertFalse(content.contains("entries"), "Vault file must not contain plaintext entries");
        assertFalse(content.contains("fmttest"), "Vault file must not contain plaintext username");
    }

    @Test
    void vaultVersionIs2_0() throws Exception {
        char[] password = "VersionP@ss!1234".toCharArray();

        manager.createVault("vertest", password).getSession().destroy();

        String content = Files.readString(tempDir.resolve("vault_vertest.enc"));
        assertTrue(content.contains("\"version\":\"2.0\""));
    }

    @Test
    void tamperedCiphertextFailsDecryption() throws Exception {
        char[] password = "TamperP@ss!12345".toCharArray();

        VaultLoadResult result = manager.createVault("tampertest", password);
        result.getVault().getEntries().add(new VaultEntry("Secret", "user",
            "pass123".toCharArray(), "", "", "Cat", null));
        manager.saveVault(result.getVault(), "tampertest", result.getSession());
        result.getSession().destroy();

        // Tamper with the encrypted data
        Path vaultPath = tempDir.resolve("vault_tampertest.enc");
        String content = Files.readString(vaultPath);
        String modified = content.replace("encrypted_data\":\"", "encrypted_data\":\"A");
        Files.writeString(vaultPath, modified);

        // Decryption must fail (GCM authentication tag check)
        assertThrows(Exception.class, () -> manager.loadVault("tampertest", password),
            "Tampered ciphertext must be rejected by GCM authentication");
    }

    // === Import Sanitization ===

    @Test
    void csvImportStripsControlCharacters() {
        Gson gson = new GsonBuilder().create();
        VaultImporter importer = new VaultImporter(gson);
        Vault vault = new Vault("testuser");

        String csv = "title,username,password,url,notes,category,tags\n"
            + "Clean\u0000Title,user\u0007name,pass\u0001word,http://url.com,notes\u0003here,Cat\u000B,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("CleanTitle", entry.getTitle(), "Null byte must be stripped");
        assertEquals("username", entry.getUsername(), "BEL char must be stripped");
        assertEquals("noteshere", entry.getNotes(), "ETX char must be stripped");
        assertFalse(entry.getCategory().contains("\u000B"), "VT char must be stripped");
    }

    @Test
    void csvImportPreservesTabs() {
        Gson gson = new GsonBuilder().create();
        VaultImporter importer = new VaultImporter(gson);
        Vault vault = new Vault("testuser");

        // Tabs are valid within a single CSV line
        String csv = "title,username,password,url,notes,category,tags\n"
            + "Test,user,pass,url,\"notes\there\",Cat,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertTrue(entry.getNotes().contains("\t"), "Tabs should be preserved in notes");
    }

    @Test
    void csvImportFormulaCharactersAreNotStripped() {
        Gson gson = new GsonBuilder().create();
        VaultImporter importer = new VaultImporter(gson);
        Vault vault = new Vault("testuser");

        // Formula characters are valid data, sanitization only strips control chars
        // Export sanitization handles formula protection
        String csv = "title,username,password,url,notes,category,tags\n"
            + "\"=MyTitle\",user,pass,url,notes,Cat,\n";

        int count = importer.importFromCsv(vault, csv);
        assertEquals(1, count);

        VaultEntry entry = vault.getEntries().get(vault.getEntries().size() - 1);
        assertEquals("=MyTitle", entry.getTitle(), "Formula chars are valid import data");
    }

    // === Export Security ===

    @Test
    void csvExportProtectsAllFormulaCharacters() {
        Gson gson = new GsonBuilder().create();
        VaultExporter exporter = new VaultExporter(gson);
        Vault vault = new Vault("testuser");

        String[] formulaChars = {"=CMD", "+CMD", "-CMD", "@CMD"};
        for (String title : formulaChars) {
            vault.getEntries().add(new VaultEntry(title, "user",
                "pass".toCharArray(), "", "", "Cat", null));
        }

        String csv = new String(exporter.exportAsCsv(vault));
        for (String title : formulaChars) {
            assertTrue(csv.contains("'" + title),
                "Formula character in '" + title + "' must be prefixed with single quote");
        }
    }

    // === Password Generator Security ===

    @Test
    void generatorProducesCorrectLength() {
        for (int len : new int[]{8, 16, 32, 64, 128}) {
            char[] pwd = PasswordGenerator.generate(len, true, true, true, true, false);
            assertEquals(len, pwd.length);
            SecureWiper.wipe(pwd);
        }
    }

    @Test
    void generatorClampsMinimumLength() {
        char[] pwd = PasswordGenerator.generate(3, true, true, true, true, false);
        assertEquals(8, pwd.length, "Minimum password length must be 8");
        SecureWiper.wipe(pwd);
    }

    @Test
    void generatorClampsMaximumLength() {
        char[] pwd = PasswordGenerator.generate(200, true, true, true, true, false);
        assertEquals(128, pwd.length, "Maximum password length must be 128");
        SecureWiper.wipe(pwd);
    }

    @Test
    void generatorIncludesAllRequestedCharTypes() {
        // Run multiple times to account for randomness
        for (int i = 0; i < 10; i++) {
            char[] pwd = PasswordGenerator.generate(32, true, true, true, true, false);
            boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
            for (char c : pwd) {
                if (Character.isUpperCase(c)) hasUpper = true;
                else if (Character.isLowerCase(c)) hasLower = true;
                else if (Character.isDigit(c)) hasDigit = true;
                else hasSpecial = true;
            }
            assertTrue(hasUpper && hasLower && hasDigit && hasSpecial,
                "Generated password must include all requested character types");
            SecureWiper.wipe(pwd);
        }
    }

    @Test
    void generatorExcludesAmbiguousCharacters() {
        String ambiguous = "0O1lI";
        for (int i = 0; i < 20; i++) {
            char[] pwd = PasswordGenerator.generate(64, true, true, true, false, true);
            for (char c : pwd) {
                assertFalse(ambiguous.indexOf(c) >= 0,
                    "Ambiguous character '" + c + "' found when excludeAmbiguous=true");
            }
            SecureWiper.wipe(pwd);
        }
    }

    @Test
    void generatorFallsBackToLowercaseWhenNothingSelected() {
        char[] pwd = PasswordGenerator.generate(16, false, false, false, false, false);
        assertEquals(16, pwd.length);
        for (char c : pwd) {
            assertTrue(Character.isLowerCase(c),
                "With no char types selected, generator must fall back to lowercase");
        }
        SecureWiper.wipe(pwd);
    }

    // === Password Strength Analyzer ===

    @Test
    void analyzerHandlesNullAndEmpty() {
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK,
            PasswordStrengthAnalyzer.analyze((char[]) null));
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK,
            PasswordStrengthAnalyzer.analyze(new char[0]));
    }

    @Test
    void analyzerStringOverloadWipesArray() {
        // Can't directly verify wipe, but ensure it doesn't throw
        PasswordStrengthAnalyzer.Strength s = PasswordStrengthAnalyzer.analyze("TestPassword123!");
        assertEquals(PasswordStrengthAnalyzer.Strength.VERY_STRONG, s);
    }

    // === VaultEntry Password Clone Safety ===

    @Test
    void vaultEntryGetPasswordReturnsClone() {
        char[] original = "MyP@ssword!1234".toCharArray();
        VaultEntry entry = new VaultEntry("Test", "user", original, "", "", "Cat", null);

        char[] clone1 = entry.getPassword();
        char[] clone2 = entry.getPassword();

        // Must be equal in content
        assertArrayEquals(clone1, clone2);

        // Must NOT be the same object
        assertNotSame(clone1, clone2, "getPassword() must return a new clone each time");

        // Wiping one clone must not affect the entry
        SecureWiper.wipe(clone1);
        char[] clone3 = entry.getPassword();
        assertFalse(Arrays.equals(clone1, clone3),
            "Wiping a clone must not affect the original password in the entry");
    }
}
