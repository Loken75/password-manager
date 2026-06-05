package com.passwordmanager.vault;

import com.passwordmanager.vault.store.FileVaultStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VaultStoreMigratorTest {

    @TempDir Path fromDir;
    @TempDir Path toDir;

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test
    void movesVaultFilesAndSidecarsButNotUnrelatedFiles() throws IOException {
        FileVaultStore from = new FileVaultStore(fromDir.toString());
        FileVaultStore to = new FileVaultStore(toDir.toString());
        from.writeAtomic("vault_alice.enc", bytes("a"));
        from.writeAtomic("vault_alice.enc.bak", bytes("a-bak"));
        from.writeAtomic("vault_bob.enc.sync_meta", bytes("hash"));
        from.writeAtomic("notes.txt", bytes("unrelated"));

        VaultStoreMigrator.Result r = VaultStoreMigrator.migrate(from, to);

        assertEquals(3, r.moved);
        assertEquals(0, r.skipped);
        assertEquals(0, r.failed);
        assertFalse(r.hasFailures());
        assertTrue(to.exists("vault_alice.enc"));
        assertTrue(to.exists("vault_alice.enc.bak"));
        assertTrue(to.exists("vault_bob.enc.sync_meta"));
        // Unrelated file is left in place; moved files are removed from the source.
        assertTrue(from.exists("notes.txt"));
        assertFalse(from.exists("vault_alice.enc"));
        assertArrayEquals(bytes("a"), to.read("vault_alice.enc"));
    }

    @Test
    void neverOverwritesExistingDestinationFile() throws IOException {
        FileVaultStore from = new FileVaultStore(fromDir.toString());
        FileVaultStore to = new FileVaultStore(toDir.toString());
        from.writeAtomic("vault_alice.enc", bytes("source"));
        to.writeAtomic("vault_alice.enc", bytes("destination"));

        VaultStoreMigrator.Result r = VaultStoreMigrator.migrate(from, to);

        assertEquals(0, r.moved);
        assertEquals(1, r.skipped);
        // Destination is untouched and the source copy is preserved (not deleted).
        assertArrayEquals(bytes("destination"), to.read("vault_alice.enc"));
        assertTrue(from.exists("vault_alice.enc"));
    }

    @Test
    void emptySourceIsANoOp() throws IOException {
        VaultStoreMigrator.Result r = VaultStoreMigrator.migrate(
            new FileVaultStore(fromDir.toString()), new FileVaultStore(toDir.toString()));
        assertEquals(0, r.moved);
        assertEquals(0, r.skipped);
        assertEquals(0, r.failed);
    }
}
