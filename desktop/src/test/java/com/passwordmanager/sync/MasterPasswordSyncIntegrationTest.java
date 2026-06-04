package com.passwordmanager.sync;

import com.passwordmanager.config.StorageMode;
import com.passwordmanager.crypto.VaultDecryptionException;
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
 * R4: a master-password change made on one device must not be silently reverted
 * when another device (with a stale session) syncs. Two devices share one vault
 * (same DEK) over a real embedded SFTP server.
 */
class MasterPasswordSyncIntegrationTest {

    @TempDir Path tmp;
    private SftpTestServer server;
    private String oldHome;
    private Path home;
    private Path clientKey;
    private static final String FILE = "vault_alice.enc";
    private final char[] P0 = "P0assw0rd!1".toCharArray();
    private final char[] P1 = "P1assw0rd!1".toCharArray();

    @BeforeEach
    void setUp() throws Exception {
        Path root = tmp.resolve("remote");
        Files.createDirectories(root.resolve("upload"));
        server = SftpTestServer.start(root);
        home = tmp.resolve("home");
        Files.createDirectories(home.resolve(".ssh"));
        oldHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
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

    private Path dir(String name) throws Exception {
        Path d = home.resolve(".passwordmanager/" + name);
        Files.createDirectories(d);
        return d;
    }

    private SyncService engine(Path vaultDir) {
        return new SyncService(
            new LocalRepository(vaultDir.toString()),
            new SFTPRepository("127.0.0.1", server.port(), "tester", clientKey.toString(), "/upload"),
            StorageMode.REMOTE);
    }

    private Set<String> titlesAfterUnlock(Path dir, char[] pw) throws Exception {
        VaultManager vm = new VaultManager(dir.toString());
        VaultLoadResult lr = vm.loadVault("alice", pw);
        return lr.getVault().getEntries().stream()
            .map(PasswordEntry::getTitle).collect(Collectors.toSet());
    }

    /**
     * Drives two devices to a conflict after a password change on A, returning the
     * download dir holding the final remote vault. If {@code adopt} is true, B adopts
     * the remote envelope before saving (the R4 fix).
     */
    private Path runConflict(boolean adopt) throws Exception {
        Path dirA = dir("A");
        Path dirB = dir("B");
        Path dirC = dir("C");

        VaultManager vmA = new VaultManager(dirA.toString());
        VaultLoadResult lrA = vmA.createVault("alice", P0.clone());
        Files.copy(dirA.resolve(FILE), dirB.resolve(FILE));
        VaultManager vmB = new VaultManager(dirB.toString());
        VaultLoadResult lrB = vmB.loadVault("alice", P0.clone());

        SyncService engineA = engine(dirA);
        SyncService engineB = engine(dirB);
        assertEquals("uploaded", engineA.synchronize(FILE).getMessage());
        assertEquals("synced", engineB.synchronize(FILE).getMessage());

        // A changes the master password to P1 and uploads.
        vmA.changeMasterPassword("alice", lrA.getVault(), lrA.getSession(), P1.clone());
        assertEquals("uploaded", engineA.synchronize(FILE).getMessage());

        // B edits data (unaware of the password change), then syncs -> CONFLICT.
        lrB.getVault().addEntry(new PasswordEntry("Bnew", "u", "p".toCharArray(), "", "", "Cat", null));
        vmB.saveVault(lrB.getVault(), "alice", lrB.getSession());
        SyncService.SyncResult conflict = engineB.synchronize(FILE);
        assertEquals("CONFLICT", conflict.getMessage());

        // B resolves: (optionally) adopt the remote envelope, then save + upload.
        VaultSession sessionB = lrB.getSession();
        if (adopt) {
            vmB.adoptEnvelopeFromFile(sessionB, conflict.getRemoteTempPath());
        }
        SyncService.SyncResult merged = engineB.syncAfterMerge(FILE,
            () -> vmB.saveVault(lrB.getVault(), "alice", sessionB));
        assertTrue(merged.isSuccess());

        // Download the final remote vault for inspection.
        SFTPRepository check = new SFTPRepository("127.0.0.1", server.port(), "tester", clientKey.toString(), "/upload");
        check.connect();
        try {
            check.downloadFile(FILE, dirC.resolve(FILE).toString());
        } finally {
            check.disconnect();
        }
        return dirC;
    }

    @Test
    void passwordChangePreservedWithEnvelopeAdoption() throws Exception {
        Path dirC = runConflict(true);
        // The new password unlocks the remote vault, and B's data change survived.
        Set<String> titles = titlesAfterUnlock(dirC, P1.clone());
        assertTrue(titles.contains("Bnew"), "B's edit propagated");
        // The old password no longer unlocks it -- the change was preserved.
        assertThrows(VaultDecryptionException.class,
            () -> new VaultManager(dirC.toString()).loadVault("alice", P0.clone()));
    }

    @Test
    void withoutAdoption_passwordChangeIsReverted() throws Exception {
        // Documents the R4 bug: a stale session re-uploads its old envelope.
        Path dirC = runConflict(false);
        // Old password still works (the change was reverted) and new password fails.
        assertTrue(titlesAfterUnlock(dirC, P0.clone()).contains("Bnew"));
        assertThrows(VaultDecryptionException.class,
            () -> new VaultManager(dirC.toString()).loadVault("alice", P1.clone()));
    }
}
