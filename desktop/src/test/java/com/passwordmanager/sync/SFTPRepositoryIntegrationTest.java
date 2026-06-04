package com.passwordmanager.sync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real end-to-end test of the production {@link SFTPRepository} against an
 * embedded SFTP server, exercising the strict host-key path. Validates the
 * actual JSch client (not a mock).
 */
class SFTPRepositoryIntegrationTest {

    @TempDir Path tmp;
    private SftpTestServer server;
    private String oldHome;
    private Path home;
    private Path clientKey;
    private static final String REMOTE = "/upload";

    @BeforeEach
    void setUp() throws Exception {
        Path root = tmp.resolve("remote");
        Files.createDirectories(root.resolve("upload"));
        server = SftpTestServer.start(root);

        home = tmp.resolve("home");
        Files.createDirectories(home.resolve(".ssh"));
        Files.createDirectories(home.resolve(".passwordmanager"));
        oldHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());

        // Seed a trusted known_hosts so StrictHostKeyChecking=yes accepts the server.
        Files.writeString(home.resolve(".ssh/known_hosts"),
            "[127.0.0.1]:" + server.port() + " " + server.knownHostsEntry() + "\n");

        clientKey = home.resolve("id_rsa");
        SftpTestServer.writeClientKey(clientKey);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) server.close();
        if (oldHome != null) System.setProperty("user.home", oldHome);
    }

    private SFTPRepository repo() {
        return new SFTPRepository("127.0.0.1", server.port(), "tester", clientKey.toString(), REMOTE);
    }

    @Test
    void uploadExistsDownloadRoundTrip() throws Exception {
        Path local = home.resolve(".passwordmanager/vault.enc");
        String content = "{\"version\":\"2.0\",\"salt\":\"x\"}";
        Files.writeString(local, content);

        SFTPRepository repo = repo();
        repo.connect();
        try {
            assertFalse(repo.remoteFileExists("vault.enc"), "not uploaded yet");
            repo.uploadFile(local.toString(), "vault.enc");
            assertTrue(repo.remoteFileExists("vault.enc"), "exists after upload");

            Path dl = home.resolve(".passwordmanager/dl.enc");
            repo.downloadFile("vault.enc", dl.toString());
            assertEquals(content, Files.readString(dl), "round-trips byte-for-byte");
        } finally {
            repo.disconnect();
        }
    }

    @Test
    void testConnectionSucceedsWithTrustedHost() {
        assertTrue(repo().testConnection());
    }

    @Test
    void connectFailsWhenHostKeyNotTrusted() throws Exception {
        // Empty the known_hosts: with StrictHostKeyChecking=yes the unknown host is rejected.
        Files.writeString(home.resolve(".ssh/known_hosts"), "");
        SFTPRepository repo = repo();
        assertThrows(Exception.class, repo::connect, "unknown host key must be rejected");
    }
}
