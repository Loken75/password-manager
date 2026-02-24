package com.passwordmanager.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LocalRepository: file operations, path traversal validation, backups.
 */
class LocalRepositoryTest {

    @TempDir
    Path tempDir;

    private LocalRepository repo;

    @BeforeEach
    void setUp() {
        repo = new LocalRepository(tempDir.toString());
    }

    // === Path traversal validation ===

    @Test
    void rejectsNullFilename() {
        assertThrows(IllegalArgumentException.class, () -> repo.getFilePath(null));
    }

    @Test
    void rejectsEmptyFilename() {
        assertThrows(IllegalArgumentException.class, () -> repo.getFilePath(""));
    }

    @Test
    void rejectsParentTraversal() {
        assertThrows(IllegalArgumentException.class, () -> repo.getFilePath("../etc/passwd"));
    }

    @Test
    void rejectsForwardSlash() {
        assertThrows(IllegalArgumentException.class, () -> repo.getFilePath("path/to/file"));
    }

    @Test
    void rejectsBackslash() {
        assertThrows(IllegalArgumentException.class, () -> repo.getFilePath("path\\to\\file"));
    }

    @Test
    void rejectsTildePrefix() {
        assertThrows(IllegalArgumentException.class, () -> repo.getFilePath("~/.ssh/id_rsa"));
    }

    @Test
    void rejectsDoubleDotsInMiddle() {
        assertThrows(IllegalArgumentException.class, () -> repo.getFilePath("file..name"));
    }

    @Test
    void acceptsValidFilename() {
        String path = repo.getFilePath("vault_alice.enc");
        assertTrue(path.endsWith("vault_alice.enc"));
    }

    // === File read/write ===

    @Test
    void writeAndReadFile() throws IOException {
        repo.writeFile("test.enc", "{\"data\":\"hello\"}");
        assertTrue(repo.fileExists("test.enc"));

        String content = repo.readFile("test.enc");
        assertEquals("{\"data\":\"hello\"}", content);
    }

    @Test
    void deleteFile() throws IOException {
        repo.writeFile("todelete.enc", "content");
        assertTrue(repo.fileExists("todelete.enc"));

        repo.deleteFile("todelete.enc");
        assertFalse(repo.fileExists("todelete.enc"));
    }

    @Test
    void fileExistsReturnsFalseForMissing() {
        assertFalse(repo.fileExists("nonexistent.enc"));
    }

    @Test
    void getLastModifiedReturnsZeroForMissing() {
        assertEquals(0, repo.getLastModified("missing.enc"));
    }

    @Test
    void getLastModifiedReturnsPositiveForExisting() throws IOException {
        repo.writeFile("existing.enc", "data");
        assertTrue(repo.getLastModified("existing.enc") > 0);
    }

    // === Pending changes ===

    @Test
    void pendingSaveAndRead() throws IOException {
        assertFalse(repo.hasPending("vault.enc"));

        repo.savePending("vault.enc", "{pending data}");
        assertTrue(repo.hasPending("vault.enc"));

        String pending = repo.readPending("vault.enc");
        assertEquals("{pending data}", pending);

        repo.clearPending("vault.enc");
        assertFalse(repo.hasPending("vault.enc"));
    }

    // === Backup ===

    @Test
    void createBackupCopiesFile() throws IOException {
        repo.writeFile("vault_test.enc", "original content");

        repo.createBackup("vault_test.enc");

        // At least one backup file should exist
        java.io.File dir = tempDir.toFile();
        java.io.File[] backups = dir.listFiles((d, name) ->
            name.startsWith("vault_test_backup_") && name.endsWith(".enc"));
        assertNotNull(backups);
        assertTrue(backups.length >= 1);
    }

    @Test
    void createBackupSkipsIfFileDoesNotExist() throws IOException {
        // Should not throw
        repo.createBackup("nonexistent.enc");
    }

    // === Atomic write overwrites ===

    @Test
    void writeFileOverwritesExisting() throws IOException {
        repo.writeFile("overwrite.enc", "first");
        repo.writeFile("overwrite.enc", "second");
        assertEquals("second", repo.readFile("overwrite.enc"));
    }
}
