package com.passwordmanager.vault;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.passwordmanager.crypto.KeyDerivation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test of the v1.0 -> v2.0 auto-migration in {@link VaultManager#loadVault}.
 *
 * <p>A real legacy (v1.0) envelope -- {@code {salt, iv, encrypted_data}}, where the body
 * is encrypted directly with a key derived from the master password, no DEK/KEK -- is
 * written to disk, then loaded. The test asserts the migration (a) preserves the data,
 * (b) rewrites the file in v2.0 (DEK/KEK envelope) format, and (c) the result reloads
 * cleanly through the v2.0 path. Closes the audit gap: migration was previously only
 * unit-tested at the {@code decryptLegacy} level, never end-to-end through VaultManager,
 * so a regression here would silently lose existing users' data on upgrade.
 */
class VaultManagerMigrationTest {

    /** Iteration count used by the pre-2.0 (v1.0) key derivation. */
    private static final int LEGACY_ITERATIONS = 100_000;

    @TempDir
    Path tempDir;

    private VaultManager manager;
    private final char[] password = "MigrateP@ss!123".toCharArray();

    @BeforeEach
    void setUp() {
        manager = new VaultManager(tempDir.toString());
    }

    /** Writes a v1.0 legacy envelope: {@code {salt, iv, encrypted_data}} with no version field. */
    private void writeLegacyVault(String username, Vault vault) throws Exception {
        char[] bodyChars = VaultJsonCodec.encode(vault);
        byte[] body = new String(bodyChars).getBytes(StandardCharsets.UTF_8);

        byte[] salt = KeyDerivation.generateSalt();
        SecretKey legacyKey = KeyDerivation.deriveKey(password.clone(), salt, LEGACY_ITERATIONS);
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey, new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(body);

        JsonObject env = new JsonObject();
        env.addProperty("salt", Base64.getEncoder().encodeToString(salt));
        env.addProperty("iv", Base64.getEncoder().encodeToString(iv));
        env.addProperty("encrypted_data", Base64.getEncoder().encodeToString(ciphertext));
        Files.write(tempDir.resolve("vault_" + username + ".enc"),
            env.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void legacyV1VaultMigratesToV2AndPreservesData() throws Exception {
        Vault legacy = new Vault("alice");
        legacy.addEntry(new PasswordEntry("Gmail", "user@gmail.com",
            "secret123".toCharArray(), "https://gmail.com", "notes", "Email", null));
        legacy.addEntry(new PasswordEntry("Bank", "mybank",
            "banking!456".toCharArray(), "https://bank.com", "", "Banking", null));
        writeLegacyVault("alice", legacy);

        // Load -> triggers auto-migration (legacy decrypt -> re-encrypt as v2.0 -> save).
        VaultLoadResult loaded = manager.loadVault("alice", password.clone());
        try {
            // (a) data preserved through the legacy decrypt
            assertEquals(2, loaded.getVault().getEntries().size());
            assertEquals("Gmail", loaded.getVault().getEntries().get(0).getTitle());
            assertArrayEquals("secret123".toCharArray(),
                loaded.getVault().getEntries().get(0).getPassword());
            assertArrayEquals("banking!456".toCharArray(),
                loaded.getVault().getEntries().get(1).getPassword());
        } finally {
            loaded.getSession().destroy();
        }

        // (b) file rewritten in v2.0 (DEK/KEK) format
        String onDisk = Files.readString(tempDir.resolve("vault_alice.enc"));
        JsonObject env = JsonParser.parseString(onDisk).getAsJsonObject();
        assertEquals("2.0", env.get("version").getAsString(), "envelope must be migrated to 2.0");
        assertTrue(env.has("encrypted_dek"), "v2.0 envelope must carry the encrypted DEK");
        assertTrue(env.has("kdf_iterations"), "v2.0 envelope must carry kdf_iterations");
        assertFalse(env.has("iv"), "the legacy 'iv' field must be gone after migration");

        // (c) the migrated file reloads cleanly through the v2.0 path, data intact
        VaultLoadResult reloaded = manager.loadVault("alice", password.clone());
        try {
            assertEquals(2, reloaded.getVault().getEntries().size());
            assertArrayEquals("banking!456".toCharArray(),
                reloaded.getVault().getEntries().get(1).getPassword());
        } finally {
            reloaded.getSession().destroy();
        }
    }

    @Test
    void migratedVaultIsOpenableWithCorrectPasswordOnly() throws Exception {
        Vault legacy = new Vault("bob");
        legacy.addEntry(new PasswordEntry("Site", "bob", "pw!2345678".toCharArray(),
            "https://site.com", "", "Other", null));
        writeLegacyVault("bob", legacy);

        manager.loadVault("bob", password.clone()).getSession().destroy(); // migrate

        // After migration, a wrong password must fail (the v2.0 envelope is intact).
        char[] wrong = "Wr0ngP@ss!9999".toCharArray();
        assertThrows(Exception.class, () -> manager.loadVault("bob", wrong));
    }
}
