package com.passwordmanager.vault.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileVaultStoreTest {

    @TempDir
    Path tempDir;

    private FileVaultStore store() {
        return new FileVaultStore(tempDir.toString());
    }

    @Test
    void createsBaseDirectoryWhenMissing() {
        Path sub = tempDir.resolve("nested/vaults");
        new FileVaultStore(sub.toString());
        assertTrue(Files.isDirectory(sub));
    }

    @Test
    void writeReadRoundTrip() throws IOException {
        FileVaultStore s = store();
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        s.writeAtomic("vault_alice.enc", data);
        assertTrue(s.exists("vault_alice.enc"));
        assertArrayEquals(data, s.read("vault_alice.enc"));
        assertEquals(data.length, s.size("vault_alice.enc"));
    }

    @Test
    void writeAtomicOverwritesAndLeavesNoTempFile() throws IOException {
        FileVaultStore s = store();
        s.writeAtomic("vault_a.enc", "v1".getBytes(StandardCharsets.UTF_8));
        s.writeAtomic("vault_a.enc", "v2".getBytes(StandardCharsets.UTF_8));
        assertEquals("v2", new String(s.read("vault_a.enc"), StandardCharsets.UTF_8));
        assertFalse(s.exists("vault_a.enc.tmp"));
    }

    @Test
    void copyDuplicatesContent() throws IOException {
        FileVaultStore s = store();
        s.writeAtomic("vault_a.enc", "x".getBytes(StandardCharsets.UTF_8));
        s.copy("vault_a.enc", "vault_a.enc.bak");
        assertTrue(s.exists("vault_a.enc.bak"));
        assertArrayEquals(s.read("vault_a.enc"), s.read("vault_a.enc.bak"));
    }

    @Test
    void listReturnsBareFilenames() throws IOException {
        FileVaultStore s = store();
        s.writeAtomic("vault_a.enc", "a".getBytes(StandardCharsets.UTF_8));
        s.writeAtomic("vault_b.enc", "b".getBytes(StandardCharsets.UTF_8));
        List<String> names = s.list();
        assertTrue(names.contains("vault_a.enc"));
        assertTrue(names.contains("vault_b.enc"));
    }

    @Test
    void deleteRemovesFile() throws IOException {
        FileVaultStore s = store();
        s.writeAtomic("vault_a.enc", "a".getBytes(StandardCharsets.UTF_8));
        s.delete("vault_a.enc");
        assertFalse(s.exists("vault_a.enc"));
        // delete of a missing file is a no-op
        assertDoesNotThrow(() -> s.delete("vault_a.enc"));
    }

    @Test
    void pathOfReturnsRealPathUnderBase() {
        FileVaultStore s = store();
        String p = s.pathOf("vault_a.enc");
        assertTrue(p.endsWith("vault_a.enc"));
        assertTrue(p.startsWith(tempDir.toString()));
    }

    @Test
    void describeReturnsBaseDirectory() {
        assertEquals(tempDir.toString(), store().describe());
    }

    @Test
    void rejectsPathTraversalNames() {
        FileVaultStore s = store();
        assertThrows(IllegalArgumentException.class, () -> s.pathOf("../escape"));
        assertThrows(IllegalArgumentException.class, () -> s.pathOf("a/b"));
        assertThrows(IllegalArgumentException.class, () -> s.exists("~/secret"));
        assertThrows(IllegalArgumentException.class, () -> s.pathOf(""));
    }

    @Test
    void lastModifiedZeroForMissing() {
        assertEquals(0, store().lastModified("nope.enc"));
    }

    @Test
    void readAndSizeOnMissingFileThrowIOException() {
        FileVaultStore s = store();
        // loadVault/reloadVault rely on these surfacing "vault not found" as IOException.
        assertThrows(IOException.class, () -> s.read("vault_missing.enc"));
        assertThrows(IOException.class, () -> s.size("vault_missing.enc"));
    }

    @Test
    void writeAtomicAppliesOwnerOnlyPermissions() throws IOException {
        Path file = Paths.get(tempDir.toString(), "vault_a.enc");
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"),
            "POSIX permissions not supported on this filesystem");
        store().writeAtomic("vault_a.enc", "x".getBytes(StandardCharsets.UTF_8));
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms,
            "vault file must be owner read/write only (600)");
    }

    @Test
    void copyAppliesOwnerOnlyPermissions() throws IOException {
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"),
            "POSIX permissions not supported on this filesystem");
        FileVaultStore s = store();
        s.writeAtomic("vault_a.enc", "x".getBytes(StandardCharsets.UTF_8));
        s.copy("vault_a.enc", "vault_a.enc.bak");
        Set<PosixFilePermission> perms =
            Files.getPosixFilePermissions(Paths.get(tempDir.toString(), "vault_a.enc.bak"));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms,
            "backup file must be owner read/write only (600)");
    }
}
