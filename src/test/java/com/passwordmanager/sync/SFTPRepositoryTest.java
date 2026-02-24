package com.passwordmanager.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SFTPRepository filename validation.
 * Connection-dependent operations are not tested (require a real SFTP server).
 */
class SFTPRepositoryTest {

    private final SFTPRepository repo = new SFTPRepository(
        "localhost", 22, "user", "/tmp/key", "/remote/path");

    // === Filename validation on upload ===

    @Test
    void uploadRejectsNullFilename() {
        assertThrows(IllegalArgumentException.class,
            () -> repo.uploadFile("/tmp/local", null));
    }

    @Test
    void uploadRejectsEmptyFilename() {
        assertThrows(IllegalArgumentException.class,
            () -> repo.uploadFile("/tmp/local", ""));
    }

    @Test
    void uploadRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
            () -> repo.uploadFile("/tmp/local", "../etc/passwd"));
    }

    @Test
    void uploadRejectsForwardSlash() {
        assertThrows(IllegalArgumentException.class,
            () -> repo.uploadFile("/tmp/local", "path/to/file"));
    }

    @Test
    void uploadRejectsBackslash() {
        assertThrows(IllegalArgumentException.class,
            () -> repo.uploadFile("/tmp/local", "path\\to\\file"));
    }

    @Test
    void uploadRejectsTilde() {
        assertThrows(IllegalArgumentException.class,
            () -> repo.uploadFile("/tmp/local", "~/.ssh/key"));
    }

    // === Filename validation on download ===

    @Test
    void downloadRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
            () -> repo.downloadFile("../../etc/shadow", "/tmp/out"));
    }

    // === Filename validation on exists check ===

    @Test
    void remoteFileExistsRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
            () -> repo.remoteFileExists("../secret"));
    }

    // === Filename validation on getRemoteLastModified ===

    @Test
    void getRemoteLastModifiedRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
            () -> repo.getRemoteLastModified("../secret"));
    }

    // === Valid filenames should not throw on validation (will fail on connection) ===

    @Test
    void remoteFileExistsAcceptsValidFilename() {
        // Will return false because not connected, but should not throw IllegalArgumentException
        // It will throw NullPointerException because sftpChannel is null (not connected)
        // We test that the validation passes, not the SFTP operation
        assertThrows(NullPointerException.class,
            () -> repo.remoteFileExists("vault_alice.enc"));
    }
}
