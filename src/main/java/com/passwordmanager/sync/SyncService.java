package com.passwordmanager.sync;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.StorageMode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates vault synchronization between local and remote storage.
 */
public class SyncService {
    private static final Logger LOGGER = Logger.getLogger(SyncService.class.getName());
    private final LocalRepository localRepo;
    private SFTPRepository sftpRepo;
    private AppConfig config;
    private final Object lock = new Object();
    private long lastSyncTime = 0;
    private volatile String syncStatus = "offline";

    public SyncService(AppConfig config) {
        this.config = config;
        this.localRepo = new LocalRepository(config.getLocalVaultDirectory());
        buildSftpRepo();
    }

    private void buildSftpRepo() {
        if (config.getStorageMode() == StorageMode.REMOTE) {
            this.sftpRepo = new SFTPRepository(
                config.getSftpHost(), config.getSftpPort(),
                config.getSftpUser(), config.getSftpKeyPath(),
                config.getSftpRemotePath()
            );
        } else {
            this.sftpRepo = null;
        }
    }

    public void refreshConfig(AppConfig config) {
        synchronized (lock) {
            this.config = config;
            buildSftpRepo();
        }
    }

    public LocalRepository getLocalRepo() { return localRepo; }
    public String getSyncStatus() { return syncStatus; }
    public long getLastSyncTime() { return lastSyncTime; }

    public SyncResult synchronize(String vaultFilename) {
        synchronized (lock) {
            if (config.getStorageMode() != StorageMode.REMOTE || sftpRepo == null) {
                syncStatus = "local";
                return new SyncResult(true, "local");
            }

            try {
                sftpRepo.connect();
                syncStatus = "syncing";

                if (localRepo.hasPending(vaultFilename)) {
                    String pending = localRepo.readPending(vaultFilename);
                    localRepo.writeFile(vaultFilename, pending);
                    localRepo.clearPending(vaultFilename);
                }

                String localPath = localRepo.getFilePath(vaultFilename);
                long localTime = localRepo.getLastModified(vaultFilename);
                boolean remoteExists = sftpRepo.remoteFileExists(vaultFilename);

                if (!remoteExists) {
                    if (new File(localPath).exists()) {
                        sftpRepo.uploadFile(localPath, vaultFilename);
                    }
                    lastSyncTime = System.currentTimeMillis();
                    syncStatus = "synced";
                    return new SyncResult(true, "uploaded");
                }

                long remoteTime = sftpRepo.getRemoteLastModified(vaultFilename);
                String localHash = hashFile(localPath);

                // Always download remote to temp first, then compare hashes.
                // This avoids TOCTOU: the hash is computed on the actual downloaded content.
                String tempPath = localPath + ".sync_tmp";
                try {
                    sftpRepo.downloadFile(vaultFilename, tempPath);
                    String remoteHash = hashFile(tempPath);

                    if (localHash.equals(remoteHash)) {
                        // Same content — no conflict regardless of timestamps
                        lastSyncTime = System.currentTimeMillis();
                        syncStatus = "synced";
                        return new SyncResult(true, "synced");
                    }

                    if (localTime >= remoteTime) {
                        // Local is newer — upload
                        sftpRepo.uploadFile(localPath, vaultFilename);
                        lastSyncTime = System.currentTimeMillis();
                        syncStatus = "synced";
                        return new SyncResult(true, "uploaded");
                    } else {
                        // Remote is newer and content differs — conflict
                        syncStatus = "conflict";
                        return new SyncResult(false, "CONFLICT");
                    }
                } finally {
                    try { Files.deleteIfExists(Paths.get(tempPath)); } catch (IOException ignored) {}
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
                if (sftpRepo != null) sftpRepo.disconnect();
            }
        }
    }

    public SyncResult resolveConflict(String vaultFilename, ConflictResolver resolution) {
        synchronized (lock) {
            if (sftpRepo == null) {
                return new SyncResult(false, "error: no remote configured");
            }
            try {
                sftpRepo.connect();
                String localPath = localRepo.getFilePath(vaultFilename);
                switch (resolution) {
                    case KEEP_LOCAL:
                        sftpRepo.uploadFile(localPath, vaultFilename);
                        break;
                    case KEEP_REMOTE:
                        sftpRepo.downloadFile(vaultFilename, localPath);
                        verifyDownload(localPath);
                        break;
                    case KEEP_BOTH:
                        localRepo.createBackup(vaultFilename);
                        sftpRepo.downloadFile(vaultFilename, localPath);
                        verifyDownload(localPath);
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
                if (sftpRepo != null) sftpRepo.disconnect();
            }
        }
    }

    public boolean testConnection() {
        synchronized (lock) {
            if (sftpRepo == null) return false;
            return sftpRepo.testConnection();
        }
    }

    /**
     * Computes SHA-256 hash of a file for integrity verification.
     */
    private static String hashFile(String path) {
        try {
            byte[] data = Files.readAllBytes(Paths.get(path));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            return "";
        }
    }

    /**
     * Verifies a downloaded file is valid (non-empty, valid JSON structure).
     */
    private static void verifyDownload(String localPath) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(localPath));
        if (data.length == 0) {
            throw new IOException("Downloaded file is empty");
        }
        String content = new String(data, StandardCharsets.UTF_8);
        if (!content.trim().startsWith("{")) {
            throw new IOException("Downloaded file is not a valid vault");
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
