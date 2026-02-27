package com.passwordmanager.sync;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.StorageMode;
import com.passwordmanager.sync.EntryMerger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates vault synchronization between local and remote storage.
 */
public class SyncService {
    private static final Logger LOGGER = Logger.getLogger(SyncService.class.getName());
    private final LocalSyncRepository localRepo;
    private RemoteSyncRepository remoteRepo;
    private StorageMode storageMode;
    private AppConfig config;
    private final Object lock = new Object();
    private long lastSyncTime = 0;
    private volatile String syncStatus = "offline";

    /** Temp suffix used when downloading remote content for hash comparison. */
    private static final String SYNC_TMP_SUFFIX = ".sync_tmp";

    /**
     * Production constructor: builds concrete repositories from AppConfig.
     * Existing code using {@code new SyncService(appConfig)} continues to work.
     */
    public SyncService(AppConfig config) {
        this.config = config;
        this.storageMode = config.getStorageMode();
        this.localRepo = new LocalRepository(config.getLocalVaultDirectory());
        buildSftpRepo();
    }

    /**
     * Testable constructor: accepts repository abstractions directly.
     *
     * @param localRepo   local file operations
     * @param remoteRepo  remote file operations (may be null for LOCAL mode)
     * @param storageMode determines whether sync is active
     */
    public SyncService(LocalSyncRepository localRepo, RemoteSyncRepository remoteRepo, StorageMode storageMode) {
        this.localRepo = localRepo;
        this.remoteRepo = remoteRepo;
        this.storageMode = storageMode;
        this.config = null;
    }

    private void buildSftpRepo() {
        if (config != null && config.getStorageMode() == StorageMode.REMOTE) {
            this.remoteRepo = new SFTPRepository(
                config.getSftpHost(), config.getSftpPort(),
                config.getSftpUser(), config.getSftpKeyPath(),
                config.getSftpRemotePath()
            );
        } else {
            this.remoteRepo = null;
        }
    }

    public void refreshConfig(AppConfig config) {
        synchronized (lock) {
            this.config = config;
            this.storageMode = config.getStorageMode();
            buildSftpRepo();
        }
    }

    public LocalSyncRepository getLocalRepo() { return localRepo; }
    public String getSyncStatus() { return syncStatus; }
    public long getLastSyncTime() { return lastSyncTime; }

    public SyncResult synchronize(String vaultFilename) {
        synchronized (lock) {
            if (storageMode != StorageMode.REMOTE || remoteRepo == null) {
                syncStatus = "local";
                return new SyncResult(true, "local");
            }

            try {
                remoteRepo.connect();
                syncStatus = "syncing";

                if (localRepo.hasPending(vaultFilename)) {
                    String pending = localRepo.readPending(vaultFilename);
                    localRepo.writeFile(vaultFilename, pending);
                    localRepo.clearPending(vaultFilename);
                }

                boolean localExists = localRepo.fileExists(vaultFilename);
                long localTime = localRepo.getLastModified(vaultFilename);
                boolean remoteExists = remoteRepo.remoteFileExists(vaultFilename);

                if (!remoteExists) {
                    if (localExists) {
                        String localPath = localRepo.getFilePath(vaultFilename);
                        remoteRepo.uploadFile(localPath, vaultFilename);
                    }
                    lastSyncTime = System.currentTimeMillis();
                    syncStatus = "synced";
                    return new SyncResult(true, "uploaded");
                }

                long remoteTime = remoteRepo.getRemoteLastModified(vaultFilename);
                String localHash = hashContent(localRepo.readFile(vaultFilename));

                // Download remote to a temp file, then compare hashes.
                String tempFilename = vaultFilename + SYNC_TMP_SUFFIX;
                String tempPath = localRepo.getFilePath(tempFilename);
                try {
                    remoteRepo.downloadFile(vaultFilename, tempPath);
                    String remoteHash = hashContent(localRepo.readFile(tempFilename));

                    if (localHash.equals(remoteHash)) {
                        lastSyncTime = System.currentTimeMillis();
                        syncStatus = "synced";
                        return new SyncResult(true, "synced");
                    }

                    if (localTime >= remoteTime) {
                        String localPath = localRepo.getFilePath(vaultFilename);
                        remoteRepo.uploadFile(localPath, vaultFilename);
                        lastSyncTime = System.currentTimeMillis();
                        syncStatus = "synced";
                        return new SyncResult(true, "uploaded");
                    } else {
                        syncStatus = "conflict";
                        return new SyncResult(false, "CONFLICT");
                    }
                } finally {
                    try {
                        if (localRepo.fileExists(tempFilename)) {
                            // Best-effort cleanup of temp file via writeFile + deleteFile
                            // In production, Files.deleteIfExists handles this;
                            // through the interface we rely on the implementation.
                            cleanupTempFile(tempPath);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to clean up temp file", e);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Sync failed", e);
                syncStatus = "error";
                try {
                    if (localRepo.fileExists(vaultFilename)) {
                        localRepo.savePending(vaultFilename, localRepo.readFile(vaultFilename));
                    }
                } catch (IOException ioEx) {
                    LOGGER.log(Level.SEVERE, "Failed to save pending changes", ioEx);
                }
                return new SyncResult(false, "error: " + e.getMessage());
            } finally {
                if (remoteRepo != null) remoteRepo.disconnect();
            }
        }
    }

    public SyncResult resolveConflict(String vaultFilename, ConflictResolver resolution) {
        synchronized (lock) {
            if (remoteRepo == null) {
                return new SyncResult(false, "error: no remote configured");
            }
            try {
                remoteRepo.connect();
                String localPath = localRepo.getFilePath(vaultFilename);
                switch (resolution) {
                    case KEEP_LOCAL:
                        remoteRepo.uploadFile(localPath, vaultFilename);
                        break;
                    case KEEP_REMOTE:
                        remoteRepo.downloadFile(vaultFilename, localPath);
                        verifyDownload(vaultFilename);
                        break;
                    case KEEP_BOTH:
                        localRepo.createBackup(vaultFilename);
                        remoteRepo.downloadFile(vaultFilename, localPath);
                        verifyDownload(vaultFilename);
                        break;
                }
                lastSyncTime = System.currentTimeMillis();
                syncStatus = "synced";
                return new SyncResult(true, "resolved");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Conflict resolution failed", e);
                syncStatus = "error";
                return new SyncResult(false, "error: " + e.getMessage());
            } finally {
                if (remoteRepo != null) remoteRepo.disconnect();
            }
        }
    }

    /**
     * Returns the merge result when local and remote vaults differ.
     * The caller must handle decryption/re-encryption externally.
     * Package-private so the UI layer (MainFrame) can call it after decrypting both vaults.
     */
    public <T extends com.passwordmanager.vault.VaultItem> EntryMerger.MergeResult<T> mergeEntries(
            java.util.List<T> local,
            java.util.List<T> remote) {
        return EntryMerger.merge(local, remote);
    }

    /**
     * Saves the merged vault to disk and then uploads it to the remote server.
     * <p>
     * This method enforces the correct ordering after a merge: the vault must be
     * persisted to disk <em>before</em> the file is uploaded. If the save action
     * fails, the upload is skipped so that a stale pre-merge file is never pushed
     * to the remote.
     *
     * @param vaultFilename the vault file name (e.g. "vault_user.enc")
     * @param saveAction    callback that persists the merged vault to disk;
     *                      must throw on failure so the upload is skipped
     * @return sync result indicating success or the reason for failure
     */
    public SyncResult syncAfterMerge(String vaultFilename, VaultSaveAction saveAction) {
        synchronized (lock) {
            // 1. Save the merged vault to disk first
            try {
                saveAction.save();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save merged vault before upload", e);
                syncStatus = "error";
                return new SyncResult(false, "error: save failed - " + e.getMessage());
            }

            // 2. Upload the freshly-saved file to the remote server
            if (remoteRepo == null) {
                return new SyncResult(false, "error: no remote configured");
            }
            try {
                remoteRepo.connect();
                String localPath = localRepo.getFilePath(vaultFilename);
                remoteRepo.uploadFile(localPath, vaultFilename);
                lastSyncTime = System.currentTimeMillis();
                syncStatus = "synced";
                return new SyncResult(true, "merged");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Upload after merge failed", e);
                syncStatus = "error";
                return new SyncResult(false, "error: " + e.getMessage());
            } finally {
                if (remoteRepo != null) remoteRepo.disconnect();
            }
        }
    }

    /**
     * Functional interface for the vault save operation passed to
     * {@link #syncAfterMerge(String, VaultSaveAction)}.
     * Declared as a checked-exception interface so callers can propagate
     * {@link IOException} or encryption errors without wrapping.
     */
    @FunctionalInterface
    public interface VaultSaveAction {
        void save() throws Exception;
    }

    public boolean testConnection() {
        synchronized (lock) {
            if (remoteRepo == null) return false;
            return remoteRepo.testConnection();
        }
    }

    /**
     * Computes SHA-256 hash of content for integrity comparison.
     * Package-private for test access.
     */
    static String hashContent(String content) throws IOException {
        try {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies a downloaded file is valid (non-empty, valid JSON structure).
     * Reads through the local repository abstraction.
     */
    private void verifyDownload(String filename) throws IOException {
        String content = localRepo.readFile(filename);
        if (content == null || content.isEmpty()) {
            throw new IOException("Downloaded file is empty");
        }
        if (!content.trim().startsWith("{")) {
            throw new IOException("Downloaded file is not a valid vault");
        }
    }

    /**
     * Cleans up a temporary file. Uses java.nio for deletion (works in production);
     * in tests, the temp file lives in the in-memory store and is cleaned up naturally.
     */
    private static void cleanupTempFile(String path) {
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete temp file: " + path, e);
        }
    }

    public static class SyncResult {
        private final boolean success;
        private final String message;

        public SyncResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
