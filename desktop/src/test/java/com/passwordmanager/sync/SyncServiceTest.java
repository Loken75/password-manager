package com.passwordmanager.sync;

import com.passwordmanager.config.StorageMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SyncService} using in-memory test doubles.
 * No mocking framework required: {@link InMemoryLocalRepo} and
 * {@link InMemoryRemoteRepo} provide full control over local/remote state.
 */
class SyncServiceTest {

    private static final String VAULT = "vault_test.enc";

    private InMemoryLocalRepo localRepo;
    private InMemoryRemoteRepo remoteRepo;
    private SyncService service;

    @BeforeEach
    void setUp() {
        localRepo = new InMemoryLocalRepo();
        remoteRepo = new InMemoryRemoteRepo(localRepo);
        service = new SyncService(localRepo, remoteRepo, StorageMode.REMOTE);
    }

    // ---------------------------------------------------------------
    // synchronize() - LOCAL mode
    // ---------------------------------------------------------------

    @Test
    void synchronize_localMode_returnsImmediately() {
        SyncService localService = new SyncService(localRepo, remoteRepo, StorageMode.LOCAL);
        SyncService.SyncResult result = localService.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals("local", result.getMessage());
        assertEquals("local", localService.getSyncStatus());
        assertFalse(remoteRepo.connectCalled, "Should not contact remote in LOCAL mode");
    }

    @Test
    void synchronize_localMode_nullRemote_returnsLocal() {
        SyncService localService = new SyncService(localRepo, null, StorageMode.LOCAL);
        SyncService.SyncResult result = localService.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals("local", result.getMessage());
    }

    // ---------------------------------------------------------------
    // synchronize() - remote does not exist, local does (upload)
    // ---------------------------------------------------------------

    @Test
    void synchronize_remoteAbsent_localExists_uploads() throws IOException {
        localRepo.writeFile(VAULT, "{\"entries\":[]}");

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals("uploaded", result.getMessage());
        assertTrue(remoteRepo.files.containsKey(VAULT), "File should have been uploaded");
        assertEquals("synced", service.getSyncStatus());
        assertTrue(service.getLastSyncTime() > 0);
    }

    @Test
    void synchronize_remoteAbsent_localAbsent_noUpload() {
        SyncService.SyncResult result = service.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals("uploaded", result.getMessage());
        assertFalse(remoteRepo.files.containsKey(VAULT), "Nothing to upload when local is absent");
    }

    // ---------------------------------------------------------------
    // synchronize() - hashes match (no conflict)
    // ---------------------------------------------------------------

    @Test
    void synchronize_hashesMatch_returnsSynced() throws IOException {
        String content = "{\"entries\":[{\"id\":1}]}";
        localRepo.writeFile(VAULT, content);
        remoteRepo.putRemoteFile(VAULT, content, localRepo.getLastModified(VAULT));

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals("synced", result.getMessage());
        assertEquals("synced", service.getSyncStatus());
    }

    // ---------------------------------------------------------------
    // synchronize() - local is newer (uploads)
    // ---------------------------------------------------------------

    @Test
    void synchronize_localNewer_uploads() throws IOException {
        String localContent = "{\"entries\":[{\"id\":1},{\"id\":2}]}";
        String remoteContent = "{\"entries\":[{\"id\":1}]}";

        localRepo.writeFile(VAULT, localContent);
        // Remote is older
        remoteRepo.putRemoteFile(VAULT, remoteContent, localRepo.getLastModified(VAULT) - 5000);

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals("uploaded", result.getMessage());
        // Verify the upload happened (remote should have been given the local path)
        assertTrue(remoteRepo.uploadCalled);
    }

    // ---------------------------------------------------------------
    // synchronize() - remote is newer and different (conflict)
    // ---------------------------------------------------------------

    @Test
    void synchronize_remoteNewer_differentContent_conflict() throws IOException {
        String localContent = "{\"entries\":[{\"id\":1}]}";
        String remoteContent = "{\"entries\":[{\"id\":1},{\"id\":99}]}";

        localRepo.writeFile(VAULT, localContent);
        // Remote is newer
        remoteRepo.putRemoteFile(VAULT, remoteContent, localRepo.getLastModified(VAULT) + 5000);

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertFalse(result.isSuccess());
        assertEquals("CONFLICT", result.getMessage());
        assertEquals("conflict", service.getSyncStatus());
    }

    // ---------------------------------------------------------------
    // synchronize() - pending changes are applied first
    // ---------------------------------------------------------------

    @Test
    void synchronize_withPending_appliesPendingFirst() throws IOException {
        String pendingContent = "{\"entries\":[{\"id\":42}]}";
        localRepo.savePending(VAULT, pendingContent);
        // Remote does not exist, so after applying pending the file should be uploaded
        SyncService.SyncResult result = service.synchronize(VAULT);

        assertTrue(result.isSuccess());
        // Pending should have been written to the main file
        assertEquals(pendingContent, localRepo.readFile(VAULT));
        // Pending should be cleared
        assertFalse(localRepo.hasPending(VAULT));
        // File should have been uploaded
        assertTrue(remoteRepo.files.containsKey(VAULT));
    }

    @Test
    void synchronize_withPending_matchesRemote_synced() throws IOException {
        String content = "{\"entries\":[{\"id\":42}]}";
        localRepo.savePending(VAULT, content);
        // Remote has the same content
        remoteRepo.putRemoteFile(VAULT, content, System.currentTimeMillis());

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals("synced", result.getMessage());
        assertFalse(localRepo.hasPending(VAULT));
    }

    // ---------------------------------------------------------------
    // synchronize() - connection failure (saves pending, returns error)
    // ---------------------------------------------------------------

    @Test
    void synchronize_connectionFails_savesPending() throws IOException {
        String content = "{\"entries\":[{\"id\":1}]}";
        localRepo.writeFile(VAULT, content);
        remoteRepo.failOnConnect = true;

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().startsWith("error:"));
        assertEquals("error", service.getSyncStatus());
        // Pending should have been saved
        assertTrue(localRepo.hasPending(VAULT));
        assertEquals(content, localRepo.readPending(VAULT));
    }

    @Test
    void synchronize_connectionFails_noLocalFile_noPendingSaved() {
        remoteRepo.failOnConnect = true;

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().startsWith("error:"));
        assertFalse(localRepo.hasPending(VAULT), "No pending when there is no local file");
    }

    // ---------------------------------------------------------------
    // resolveConflict() - KEEP_LOCAL
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_keepLocal_uploadsToRemote() throws IOException {
        String localContent = "{\"entries\":[{\"local\":true}]}";
        localRepo.writeFile(VAULT, localContent);

        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictResolver.KEEP_LOCAL);

        assertTrue(result.isSuccess());
        assertEquals("resolved", result.getMessage());
        assertTrue(remoteRepo.uploadCalled);
        assertEquals("synced", service.getSyncStatus());
    }

    // ---------------------------------------------------------------
    // resolveConflict() - KEEP_REMOTE
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_keepRemote_downloadsFromRemote() throws IOException {
        String localContent = "{\"entries\":[{\"local\":true}]}";
        String remoteContent = "{\"entries\":[{\"remote\":true}]}";
        localRepo.writeFile(VAULT, localContent);
        remoteRepo.putRemoteFile(VAULT, remoteContent, System.currentTimeMillis());

        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictResolver.KEEP_REMOTE);

        assertTrue(result.isSuccess());
        assertEquals("resolved", result.getMessage());
        // Local should now contain remote content (download overwrites local path)
        assertEquals(remoteContent, localRepo.readFile(VAULT));
    }

    // ---------------------------------------------------------------
    // resolveConflict() - KEEP_BOTH
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_keepBoth_backsUpThenDownloads() throws IOException {
        String localContent = "{\"entries\":[{\"local\":true}]}";
        String remoteContent = "{\"entries\":[{\"remote\":true}]}";
        localRepo.writeFile(VAULT, localContent);
        remoteRepo.putRemoteFile(VAULT, remoteContent, System.currentTimeMillis());

        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictResolver.KEEP_BOTH);

        assertTrue(result.isSuccess());
        assertEquals("resolved", result.getMessage());
        // Backup should have been created
        assertTrue(localRepo.backupCreated, "Backup should have been created before overwriting");
        // Local should now contain remote content
        assertEquals(remoteContent, localRepo.readFile(VAULT));
    }

    // ---------------------------------------------------------------
    // resolveConflict() - no remote configured
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_noRemote_returnsError() {
        SyncService localOnly = new SyncService(localRepo, null, StorageMode.REMOTE);
        SyncService.SyncResult result = localOnly.resolveConflict(VAULT, ConflictResolver.KEEP_LOCAL);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("no remote configured"));
    }

    // ---------------------------------------------------------------
    // resolveConflict() - connection failure
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_connectionFails_returnsError() {
        remoteRepo.failOnConnect = true;
        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictResolver.KEEP_LOCAL);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().startsWith("error:"));
        assertEquals("error", service.getSyncStatus());
    }

    // ---------------------------------------------------------------
    // testConnection()
    // ---------------------------------------------------------------

    @Test
    void testConnection_noRemote_returnsFalse() {
        SyncService localOnly = new SyncService(localRepo, null, StorageMode.LOCAL);
        assertFalse(localOnly.testConnection());
    }

    @Test
    void testConnection_success() {
        assertTrue(service.testConnection());
    }

    @Test
    void testConnection_failure() {
        remoteRepo.failOnConnect = true;
        assertFalse(service.testConnection());
    }

    // ---------------------------------------------------------------
    // hashContent() - deterministic
    // ---------------------------------------------------------------

    @Test
    void hashContent_sameInput_sameOutput() throws IOException {
        String hash1 = SyncService.hashContent("{\"data\":1}");
        String hash2 = SyncService.hashContent("{\"data\":1}");
        assertEquals(hash1, hash2);
    }

    @Test
    void hashContent_differentInput_differentOutput() throws IOException {
        String hash1 = SyncService.hashContent("{\"data\":1}");
        String hash2 = SyncService.hashContent("{\"data\":2}");
        assertNotEquals(hash1, hash2);
    }

    // ---------------------------------------------------------------
    // synchronize() - disconnect is always called
    // ---------------------------------------------------------------

    @Test
    void synchronize_alwaysDisconnects() throws IOException {
        localRepo.writeFile(VAULT, "{\"entries\":[]}");
        service.synchronize(VAULT);
        assertTrue(remoteRepo.disconnectCalled, "disconnect() should be called after sync");
    }

    @Test
    void synchronize_disconnectsEvenOnError() {
        remoteRepo.failOnConnect = true;
        service.synchronize(VAULT);
        assertTrue(remoteRepo.disconnectCalled, "disconnect() should be called even on error");
    }

    // ===================================================================
    // In-memory test doubles
    // ===================================================================

    /**
     * In-memory implementation of {@link LocalSyncRepository}.
     * Stores file contents and metadata in HashMaps.
     */
    static class InMemoryLocalRepo implements LocalSyncRepository {
        final Map<String, String> files = new LinkedHashMap<>();
        final Map<String, Long> timestamps = new LinkedHashMap<>();
        final Map<String, String> pending = new LinkedHashMap<>();
        boolean backupCreated = false;

        @Override
        public String getFilePath(String filename) {
            // Return a synthetic path that is predictable in tests
            return "/test-vault/" + filename;
        }

        @Override
        public boolean fileExists(String filename) {
            return files.containsKey(filename);
        }

        @Override
        public String readFile(String filename) throws IOException {
            if (!files.containsKey(filename)) {
                throw new IOException("File not found: " + filename);
            }
            return files.get(filename);
        }

        @Override
        public void writeFile(String filename, String content) {
            files.put(filename, content);
            timestamps.put(filename, System.currentTimeMillis());
        }

        @Override
        public long getLastModified(String filename) {
            return timestamps.getOrDefault(filename, 0L);
        }

        @Override
        public boolean hasPending(String filename) {
            return pending.containsKey(filename);
        }

        @Override
        public String readPending(String filename) throws IOException {
            if (!pending.containsKey(filename)) {
                throw new IOException("No pending data for: " + filename);
            }
            return pending.get(filename);
        }

        @Override
        public void savePending(String filename, String content) {
            pending.put(filename, content);
        }

        @Override
        public void clearPending(String filename) {
            pending.remove(filename);
        }

        @Override
        public void createBackup(String filename) {
            backupCreated = true;
            if (files.containsKey(filename)) {
                files.put(filename + ".backup", files.get(filename));
            }
        }

        /** Sets a specific timestamp for a file (for testing time comparisons). */
        void setLastModified(String filename, long time) {
            timestamps.put(filename, time);
        }
    }

    /**
     * In-memory implementation of {@link RemoteSyncRepository}.
     * Holds a reference to the local repo so that {@link #downloadFile}
     * can "write" content where the local repo can read it (mirroring
     * how SFTP writes to the local filesystem in production).
     */
    static class InMemoryRemoteRepo implements RemoteSyncRepository {
        final Map<String, String> files = new LinkedHashMap<>();
        final Map<String, Long> timestamps = new LinkedHashMap<>();
        private final InMemoryLocalRepo localRepo;

        boolean failOnConnect = false;
        boolean connectCalled = false;
        boolean disconnectCalled = false;
        boolean uploadCalled = false;

        InMemoryRemoteRepo(InMemoryLocalRepo localRepo) {
            this.localRepo = localRepo;
        }

        /** Pre-populates a remote file for test setup. */
        void putRemoteFile(String filename, String content, long lastModified) {
            files.put(filename, content);
            timestamps.put(filename, lastModified);
        }

        @Override
        public void connect() throws Exception {
            connectCalled = true;
            if (failOnConnect) {
                throw new IOException("Simulated connection failure");
            }
        }

        @Override
        public void disconnect() {
            disconnectCalled = true;
        }

        @Override
        public boolean remoteFileExists(String remoteFilename) {
            return files.containsKey(remoteFilename);
        }

        @Override
        public long getRemoteLastModified(String remoteFilename) {
            return timestamps.getOrDefault(remoteFilename, 0L);
        }

        @Override
        public void uploadFile(String localPath, String remoteFilename) {
            uploadCalled = true;
            // In a real scenario, this reads from localPath on disk.
            // Here we record that the upload was requested.
            // Find the content from the local repo by matching the path.
            for (Map.Entry<String, String> entry : localRepo.files.entrySet()) {
                if (localRepo.getFilePath(entry.getKey()).equals(localPath)) {
                    files.put(remoteFilename, entry.getValue());
                    timestamps.put(remoteFilename, System.currentTimeMillis());
                    return;
                }
            }
            files.put(remoteFilename, "<uploaded from " + localPath + ">");
            timestamps.put(remoteFilename, System.currentTimeMillis());
        }

        @Override
        public void downloadFile(String remoteFilename, String localPath) throws Exception {
            if (!files.containsKey(remoteFilename)) {
                throw new IOException("Remote file not found: " + remoteFilename);
            }
            String content = files.get(remoteFilename);
            // Write to local repo at the filename that maps to localPath.
            // The localPath is "/test-vault/<filename>", so extract the filename.
            String filename = localPath.replace("/test-vault/", "");
            localRepo.writeFile(filename, content);
        }

        @Override
        public boolean testConnection() {
            if (failOnConnect) return false;
            return true;
        }
    }
}
