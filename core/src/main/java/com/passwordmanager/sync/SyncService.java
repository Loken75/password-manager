package com.passwordmanager.sync;

import com.passwordmanager.config.StorageMode;
import com.passwordmanager.util.FileSecurityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates vault synchronization between local and remote storage.
 * Uses three-way hash comparison (local, remote, last-synced) to determine
 * sync direction without relying on unreliable cross-system timestamps.
 */
public class SyncService {
    private static final Logger LOGGER = Logger.getLogger(SyncService.class.getName());
    private final LocalSyncRepository localRepo;
    private final RemoteSyncRepository remoteRepo;
    private final StorageMode storageMode;
    private final Object lock = new Object();
    private long lastSyncTime = 0;
    private volatile String syncStatus = "offline";

    /** Temp suffix used when downloading remote content for hash comparison. */
    private static final String SYNC_TMP_SUFFIX = ".sync_tmp";
    /** Maximum number of retry attempts for transient network errors. */
    private static final int MAX_RETRIES = 1;
    /** Delay between retries in milliseconds. */
    private static final long RETRY_DELAY_MS = 2000;

    /**
     * Constructs the sync engine from repository abstractions. Concrete
     * repositories (local file store, SFTP client) are built per-platform
     * by the caller and injected here, keeping this engine UI- and
     * transport-agnostic so it can live in {@code :core}.
     */
    public SyncService(LocalSyncRepository localRepo, RemoteSyncRepository remoteRepo, StorageMode storageMode) {
        this.localRepo = localRepo;
        this.remoteRepo = remoteRepo;
        this.storageMode = storageMode;
    }

    public LocalSyncRepository getLocalRepo() { return localRepo; }
    public String getSyncStatus() { return syncStatus; }
    public long getLastSyncTime() { return lastSyncTime; }

    /**
     * Synchronizes the vault with the remote server.
     * Uses three-way hash comparison and retries once on transient errors.
     */
    public SyncResult synchronize(String vaultFilename) {
        synchronized (lock) {
            if (storageMode != StorageMode.REMOTE || remoteRepo == null) {
                syncStatus = "local";
                return new SyncResult(true, "local");
            }

            // Apply pending changes before sync
            try {
                if (localRepo.hasPending(vaultFilename)) {
                    String pending = localRepo.readPending(vaultFilename);
                    localRepo.writeFile(vaultFilename, pending);
                    localRepo.clearPending(vaultFilename);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to apply pending changes", e);
            }

            // SYNC-08: Retry once on transient errors
            Exception lastError = null;
            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                try {
                    SyncResult result = doSyncCore(vaultFilename);
                    return result;
                } catch (Exception e) {
                    lastError = e;
                    LOGGER.log(Level.WARNING, "Sync attempt " + (attempt + 1) + " failed", e);
                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } finally {
                    if (remoteRepo != null) remoteRepo.disconnect();
                }
            }

            // All retries exhausted: save pending and return error
            syncStatus = "error";
            try {
                if (localRepo.fileExists(vaultFilename)) {
                    localRepo.savePending(vaultFilename, localRepo.readFile(vaultFilename));
                }
            } catch (IOException ioEx) {
                LOGGER.log(Level.SEVERE, "Failed to save pending changes", ioEx);
            }
            return new SyncResult(false, "error: " + (lastError != null ? lastError.getMessage() : "unknown"));
        }
    }

    /**
     * Core sync logic using three-way hash comparison.
     * Compares local hash, remote hash, and last-synced hash to determine direction.
     */
    private SyncResult doSyncCore(String vaultFilename) throws Exception {
        remoteRepo.connect();
        syncStatus = "syncing";

        boolean localExists = localRepo.fileExists(vaultFilename);
        boolean remoteExists = remoteRepo.remoteFileExists(vaultFilename);

        // Case 1: Remote doesn't exist
        if (!remoteExists) {
            if (localExists) {
                String localPath = localRepo.getFilePath(vaultFilename);
                remoteRepo.uploadFile(localPath, vaultFilename);
                // Save sync hash for future three-way comparison
                String localHash = hashContent(localRepo.readFile(vaultFilename));
                localRepo.saveSyncMeta(vaultFilename, localHash);
            }
            lastSyncTime = System.currentTimeMillis();
            syncStatus = "synced";
            return new SyncResult(true, "uploaded");
        }

        // Case 2: Remote exists — download and compare
        String localHash = localExists ? hashContent(localRepo.readFile(vaultFilename)) : "";
        String lastSyncHash = localRepo.readSyncMeta(vaultFilename);

        String tempFilename = vaultFilename + SYNC_TMP_SUFFIX;
        String tempPath = localRepo.getFilePath(tempFilename);
        boolean keepTemp = false; // on CONFLICT, keep the downloaded remote for the caller's merge
        try {
            remoteRepo.downloadFile(vaultFilename, tempPath);
            // SEC-02: Restrict permissions on temp file containing encrypted vault
            FileSecurityUtils.setOwnerOnlyPermissions(Paths.get(tempPath));

            String remoteHash = hashContent(localRepo.readFile(tempFilename));

            // Same content: already in sync
            if (localHash.equals(remoteHash)) {
                localRepo.saveSyncMeta(vaultFilename, localHash);
                lastSyncTime = System.currentTimeMillis();
                syncStatus = "synced";
                return new SyncResult(true, "synced");
            }

            // SYNC-03: Three-way hash comparison instead of unreliable timestamps
            if (lastSyncHash != null) {
                boolean localChanged = !localHash.equals(lastSyncHash);
                boolean remoteChanged = !remoteHash.equals(lastSyncHash);

                if (localChanged && !remoteChanged) {
                    // Only local changed: upload
                    String localPath = localRepo.getFilePath(vaultFilename);
                    remoteRepo.uploadFile(localPath, vaultFilename);
                    localRepo.saveSyncMeta(vaultFilename, localHash);
                    lastSyncTime = System.currentTimeMillis();
                    syncStatus = "synced";
                    return new SyncResult(true, "uploaded");
                }

                if (!localChanged && remoteChanged) {
                    // Only remote changed: download via repo abstraction
                    localRepo.createBackup(vaultFilename);
                    String remoteContent = localRepo.readFile(tempFilename);
                    localRepo.writeFile(vaultFilename, remoteContent);
                    localRepo.saveSyncMeta(vaultFilename, remoteHash);
                    lastSyncTime = System.currentTimeMillis();
                    syncStatus = "synced";
                    return new SyncResult(true, "downloaded");
                }

                // Both changed: conflict. Preserve the downloaded remote vault so the
                // caller can merge against the REAL remote (not a re-read of local).
                syncStatus = "conflict";
                keepTemp = true;
                return new SyncResult(false, "CONFLICT", tempPath);
            }

            // No sync meta (first sync with existing remote): conflict
            syncStatus = "conflict";
            keepTemp = true;
            return new SyncResult(false, "CONFLICT", tempPath);
        } finally {
            if (!keepTemp) cleanupTempFile(tempPath);
        }
    }

    public SyncResult resolveConflict(String vaultFilename, ConflictStrategy resolution) {
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
                // Update sync hash after resolution
                String hash = hashContent(localRepo.readFile(vaultFilename));
                localRepo.saveSyncMeta(vaultFilename, hash);
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
     */
    public <T extends com.passwordmanager.vault.VaultItem> EntryMerger.MergeResult<T> mergeEntries(
            java.util.List<T> local,
            java.util.List<T> remote) {
        return EntryMerger.merge(local, remote);
    }

    /**
     * Saves the merged vault to disk and then uploads it to the remote server.
     * Enforces correct ordering: persist to disk before upload.
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
                // Update sync hash after successful merge + upload
                String hash = hashContent(localRepo.readFile(vaultFilename));
                localRepo.saveSyncMeta(vaultFilename, hash);
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
     * SYNC-09: Verifies a downloaded file has valid vault structure.
     * Checks non-empty, valid JSON, and contains expected vault fields.
     */
    private void verifyDownload(String filename) throws IOException {
        String content = localRepo.readFile(filename);
        if (content == null || content.isEmpty()) {
            throw new IOException("Downloaded file is empty");
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IOException("Downloaded file is not a valid vault (not JSON object)");
        }
        // Verify presence of essential vault envelope fields
        if (!trimmed.contains("\"version\"") || !trimmed.contains("\"salt\"")) {
            throw new IOException("Downloaded file is missing required vault fields");
        }
    }

    private static void cleanupTempFile(String path) {
        try {
            java.nio.file.Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete temp file: " + path, e);
        }
    }

    public static class SyncResult {
        private final boolean success;
        private final String message;
        /** On a "CONFLICT" result: local path to the downloaded remote .enc to merge against. */
        private final String remoteTempPath;

        public SyncResult(boolean success, String message) {
            this(success, message, null);
        }

        public SyncResult(boolean success, String message, String remoteTempPath) {
            this.success = success;
            this.message = message;
            this.remoteTempPath = remoteTempPath;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getRemoteTempPath() { return remoteTempPath; }
    }
}
