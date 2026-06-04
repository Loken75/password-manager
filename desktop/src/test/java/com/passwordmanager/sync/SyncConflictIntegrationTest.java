package com.passwordmanager.sync;

import com.passwordmanager.config.StorageMode;
import com.passwordmanager.crypto.VaultSession;
import com.passwordmanager.vault.PasswordEntry;
import com.passwordmanager.vault.Vault;
import com.passwordmanager.vault.VaultLoadResult;
import com.passwordmanager.vault.VaultManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R-merge: end-to-end proof that a sync CONFLICT exposes the REAL remote vault
 * for merging (not a re-read of the local file). Uses real crypto + a real
 * embedded SFTP server.
 */
class SyncConflictIntegrationTest {

    @TempDir Path tmp;
    private SftpTestServer server;
    private String oldHome;
    private Path home;
    private Path clientKey;
    private VaultManager vm;
    private VaultSession session;
    private Vault vault;
    private static final String FILE = "vault_alice.enc";

    @BeforeEach
    void setUp() throws Exception {
        Path root = tmp.resolve("remote");
        Files.createDirectories(root.resolve("upload"));
        server = SftpTestServer.start(root);

        home = tmp.resolve("home");
        Files.createDirectories(home.resolve(".ssh"));
        Path vaults = home.resolve(".passwordmanager/vaults");
        Files.createDirectories(vaults);
        oldHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        Files.writeString(home.resolve(".ssh/known_hosts"),
            "[127.0.0.1]:" + server.port() + " " + server.knownHostsEntry() + "\n");
        clientKey = home.resolve("id_rsa");
        SftpTestServer.writeClientKey(clientKey);

        vm = new VaultManager(vaults.toString());
        VaultLoadResult lr = vm.createVault("alice", "TestP@ssw0rd!".toCharArray());
        vault = lr.getVault();
        session = lr.getSession();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) server.close();
        if (oldHome != null) System.setProperty("user.home", oldHome);
    }

    private PasswordEntry entry(String title) {
        return new PasswordEntry(title, "u", "p".toCharArray(), "", "", "Cat", null);
    }

    private Set<String> titles(Vault v) {
        return v.getEntries().stream().map(PasswordEntry::getTitle).collect(Collectors.toSet());
    }

    private SFTPRepository remoteRepo() {
        return new SFTPRepository("127.0.0.1", server.port(), "tester", clientKey.toString(), "/upload");
    }

    @Test
    void conflictExposesRealRemoteVault() throws Exception {
        // Local starts with {A}; push it to the remote.
        vault.addEntry(entry("A"));
        vm.saveVault(vault, "alice", session);

        LocalRepository local = new LocalRepository(home.resolve(".passwordmanager/vaults").toString());
        SFTPRepository remote = remoteRepo();
        SyncService engine = new SyncService(local, remote, StorageMode.REMOTE);

        SyncService.SyncResult first = engine.synchronize(FILE);
        assertTrue(first.isSuccess());
        assertEquals("uploaded", first.getMessage());

        // Remote device adds B -> remote = {A, B}.
        PasswordEntry b = entry("B");
        vault.addEntry(b);
        vm.saveVault(vault, "alice", session);
        remote.connect();
        try {
            remote.uploadFile(vm.getVaultPath("alice"), FILE);
        } finally {
            remote.disconnect();
        }

        // Local device instead has {A, C}.
        vault.removeEntry(b);
        vault.addEntry(entry("C"));
        vm.saveVault(vault, "alice", session);

        // Both sides changed since last sync -> CONFLICT exposing the real remote.
        SyncService.SyncResult conflict = engine.synchronize(FILE);
        assertFalse(conflict.isSuccess());
        assertEquals("CONFLICT", conflict.getMessage());
        assertNotNull(conflict.getRemoteTempPath(), "remote temp path must be exposed for the merge");

        Vault realRemote = vm.decryptVaultFile(conflict.getRemoteTempPath(), session);
        assertTrue(titles(realRemote).contains("B"), "merge sees the remote-only entry B");
        assertFalse(titles(realRemote).contains("C"), "merge must NOT see the local-only entry C");

        // Documents the old R-merge bug: reloadVault reads the LOCAL file ({A, C}),
        // which is exactly what handleConflict used to (wrongly) treat as 'remote'.
        Vault viaReload = vm.reloadVault("alice", session);
        assertTrue(titles(viaReload).contains("C"), "reloadVault returns LOCAL, not remote");
    }
}
