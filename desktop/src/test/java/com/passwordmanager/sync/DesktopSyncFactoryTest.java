package com.passwordmanager.sync;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.StorageMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the desktop factory wires the core {@link SyncService} from AppConfig
 * without opening any network connection (E.1a).
 */
class DesktopSyncFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void localMode_buildsLocalOnlyEngine() {
        AppConfig config = new AppConfig();
        config.setLocalVaultDirectory(tempDir.toString());
        config.setStorageMode(StorageMode.LOCAL);

        SyncService engine = DesktopSyncFactory.create(config, null);
        assertNotNull(engine);

        // LOCAL mode short-circuits with no remote and no network call.
        SyncService.SyncResult result = engine.synchronize("vault_x.enc");
        assertTrue(result.isSuccess());
        assertEquals("local", result.getMessage());
    }

    @Test
    void remoteMode_buildsEngineWithoutConnecting() {
        AppConfig config = new AppConfig();
        config.setLocalVaultDirectory(tempDir.toString());
        config.setSftpHost("example.com");
        config.setSftpUser("user");
        config.setSftpKeyPath("/tmp/key");
        config.setSftpRemotePath("/vault");
        config.setStorageMode(StorageMode.REMOTE);

        // Building must not attempt a connection; status stays offline until sync runs.
        SyncService engine = DesktopSyncFactory.create(config, null);
        assertNotNull(engine);
        assertEquals("offline", engine.getSyncStatus());
    }
}
