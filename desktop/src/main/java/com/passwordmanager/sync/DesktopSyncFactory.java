package com.passwordmanager.sync;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.StorageMode;

/**
 * Builds a {@link SyncService} for the desktop client by constructing the
 * concrete local file store and SFTP client from {@link AppConfig}.
 *
 * The sync engine itself lives in {@code :core} and is transport-agnostic;
 * this factory keeps the desktop-specific wiring (JSch-based {@link SFTPRepository},
 * vault-key vs key-file selection) out of the engine.
 */
public final class DesktopSyncFactory {

    private DesktopSyncFactory() {}

    /**
     * @param config        the application configuration
     * @param vaultKeyBytes SSH private key bytes when using vault-key auth, or {@code null}
     * @return a configured {@link SyncService} (remote repository is {@code null} in LOCAL mode)
     */
    public static SyncService create(AppConfig config, byte[] vaultKeyBytes) {
        LocalRepository local = new LocalRepository(config.getLocalVaultDirectory());
        RemoteSyncRepository remote = null;
        if (config.getStorageMode() == StorageMode.REMOTE) {
            if (config.isUsingVaultKey() && vaultKeyBytes != null) {
                remote = new SFTPRepository(
                    config.getSftpHost(), config.getSftpPort(),
                    config.getSftpUser(), vaultKeyBytes,
                    config.getSftpRemotePath());
            } else {
                remote = new SFTPRepository(
                    config.getSftpHost(), config.getSftpPort(),
                    config.getSftpUser(), config.getSftpKeyPath(),
                    config.getSftpRemotePath());
            }
        }
        return new SyncService(local, remote, config.getStorageMode());
    }
}
