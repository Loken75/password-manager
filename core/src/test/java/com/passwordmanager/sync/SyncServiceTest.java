package com.passwordmanager.sync;

import com.passwordmanager.config.StorageMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SyncService} using in-memory test doubles.
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
        // Sync meta should be saved
        assertNotNull(localRepo.readSyncMeta(VAULT));
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
    // synchronize() - three-way hash: only local changed (uploads)
    // ---------------------------------------------------------------

    @Test
    void synchronize_onlyLocalChanged_uploads() throws IOException {
        String originalContent = "{\"entries\":[{\"id\":1}]}";
        localRepo.writeFile(VAULT, originalContent);
        remoteRepo.putRemoteFile(VAULT, originalContent, System.currentTimeMillis());
        // Establish sync baseline
        String baseHash = SyncService.hashContent(originalContent);
        localRepo.saveSyncMeta(VAULT, baseHash);

        // Now modify local only
        String localContent = "{\"entries\":[{\"id\":1},{\"id\":2}]}";
        localRepo.writeFile(VAULT, localContent);

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals("uploaded", result.getMessage());
        assertTrue(remoteRepo.uploadCalled);
    }

    // ---------------------------------------------------------------
    // synchronize() - three-way hash: only remote changed (downloads)
    // ---------------------------------------------------------------

    @Test
    void synchronize_onlyRemoteChanged_downloads() throws IOException {
        String originalContent = "{\"entries\":[{\"id\":1}]}";
        localRepo.writeFile(VAULT, originalContent);
        // Establish sync baseline
        String baseHash = SyncService.hashContent(originalContent);
        localRepo.saveSyncMeta(VAULT, baseHash);

        // Remote changed
        String remoteContent = "{\"entries\":[{\"id\":1},{\"id\":99}]}";
        remoteRepo.putRemoteFile(VAULT, remoteContent, System.currentTimeMillis());

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals("downloaded", result.getMessage());
        // Local should now contain remote content
        assertEquals(remoteContent, localRepo.readFile(VAULT));
    }

    // ---------------------------------------------------------------
    // synchronize() - three-way hash: both changed (conflict)
    // ---------------------------------------------------------------

    @Test
    void synchronize_bothChanged_conflict() throws IOException {
        String originalContent = "{\"entries\":[{\"id\":1}]}";
        localRepo.writeFile(VAULT, originalContent);
        String baseHash = SyncService.hashContent(originalContent);
        localRepo.saveSyncMeta(VAULT, baseHash);

        // Both sides changed
        String localContent = "{\"entries\":[{\"id\":1},{\"id\":2}]}";
        localRepo.writeFile(VAULT, localContent);
        String remoteContent = "{\"entries\":[{\"id\":1},{\"id\":99}]}";
        remoteRepo.putRemoteFile(VAULT, remoteContent, System.currentTimeMillis());

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertFalse(result.isSuccess());
        assertEquals("CONFLICT", result.getMessage());
        assertEquals("conflict", service.getSyncStatus());
    }

    // ---------------------------------------------------------------
    // synchronize() - no sync meta (first sync with existing remote)
    // ---------------------------------------------------------------

    @Test
    void synchronize_noSyncMeta_differentContent_conflict() throws IOException {
        String localContent = "{\"entries\":[{\"id\":1}]}";
        String remoteContent = "{\"entries\":[{\"id\":1},{\"id\":99}]}";
        localRepo.writeFile(VAULT, localContent);
        remoteRepo.putRemoteFile(VAULT, remoteContent, System.currentTimeMillis());
        // No sync meta set

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertFalse(result.isSuccess());
        assertEquals("CONFLICT", result.getMessage());
    }

    // ---------------------------------------------------------------
    // synchronize() - pending changes are applied first
    // ---------------------------------------------------------------

    @Test
    void synchronize_withPending_appliesPendingFirst() throws IOException {
        String pendingContent = "{\"entries\":[{\"id\":42}]}";
        localRepo.savePending(VAULT, pendingContent);

        SyncService.SyncResult result = service.synchronize(VAULT);

        assertTrue(result.isSuccess());
        assertEquals(pendingContent, localRepo.readFile(VAULT));
        assertFalse(localRepo.hasPending(VAULT));
        assertTrue(remoteRepo.files.containsKey(VAULT));
    }

    @Test
    void synchronize_withPending_matchesRemote_synced() throws IOException {
        String content = "{\"entries\":[{\"id\":42}]}";
        localRepo.savePending(VAULT, content);
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

        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictStrategy.KEEP_LOCAL);

        assertTrue(result.isSuccess());
        assertEquals("resolved", result.getMessage());
        assertTrue(remoteRepo.uploadCalled);
        assertEquals("synced", service.getSyncStatus());
        // Sync meta should be updated
        assertNotNull(localRepo.readSyncMeta(VAULT));
    }

    // ---------------------------------------------------------------
    // resolveConflict() - KEEP_REMOTE
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_keepRemote_downloadsFromRemote() throws IOException {
        String localContent = "{\"version\":\"2.0\",\"salt\":\"x\",\"entries\":[{\"local\":true}]}";
        String remoteContent = "{\"version\":\"2.0\",\"salt\":\"x\",\"entries\":[{\"remote\":true}]}";
        localRepo.writeFile(VAULT, localContent);
        remoteRepo.putRemoteFile(VAULT, remoteContent, System.currentTimeMillis());

        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictStrategy.KEEP_REMOTE);

        assertTrue(result.isSuccess());
        assertEquals("resolved", result.getMessage());
        assertEquals(remoteContent, localRepo.readFile(VAULT));
    }

    // ---------------------------------------------------------------
    // resolveConflict() - KEEP_BOTH
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_keepBoth_backsUpThenDownloads() throws IOException {
        String localContent = "{\"version\":\"2.0\",\"salt\":\"x\",\"entries\":[{\"local\":true}]}";
        String remoteContent = "{\"version\":\"2.0\",\"salt\":\"x\",\"entries\":[{\"remote\":true}]}";
        localRepo.writeFile(VAULT, localContent);
        remoteRepo.putRemoteFile(VAULT, remoteContent, System.currentTimeMillis());

        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictStrategy.KEEP_BOTH);

        assertTrue(result.isSuccess());
        assertEquals("resolved", result.getMessage());
        assertTrue(localRepo.backupCreated, "Backup should have been created before overwriting");
        assertEquals(remoteContent, localRepo.readFile(VAULT));
    }

    // ---------------------------------------------------------------
    // resolveConflict() - integrity check on downloaded vault (verifyDownload)
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_keepRemote_invalidRemoteRejectedAndLocalPreserved() throws IOException {
        // Remote vault is structurally invalid (no version/salt): it must be rejected
        // BEFORE the local vault is touched (R-A: verify-before-promote).
        String localContent = "{\"version\":\"2.0\",\"salt\":\"x\",\"entries\":[{\"local\":true}]}";
        localRepo.writeFile(VAULT, localContent);
        remoteRepo.putRemoteFile(VAULT, "{\"entries\":[]}", System.currentTimeMillis());

        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictStrategy.KEEP_REMOTE);

        assertFalse(result.isSuccess(), "An invalid downloaded vault must not be accepted");
        assertEquals(localContent, localRepo.readFile(VAULT), "Local vault must be preserved intact");
    }

    @Test
    void resolveConflict_keepBoth_invalidRemoteRejectedAndLocalPreserved() throws IOException {
        String localContent = "{\"version\":\"2.0\",\"salt\":\"x\",\"entries\":[{\"local\":true}]}";
        localRepo.writeFile(VAULT, localContent);
        remoteRepo.putRemoteFile(VAULT, "{\"entries\":[]}", System.currentTimeMillis());

        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictStrategy.KEEP_BOTH);

        assertFalse(result.isSuccess(), "An invalid downloaded vault must not be accepted");
        assertEquals(localContent, localRepo.readFile(VAULT), "Local vault must be preserved intact");
    }

    // ---------------------------------------------------------------
    // resolveConflict() - no remote configured
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_noRemote_returnsError() {
        SyncService localOnly = new SyncService(localRepo, null, StorageMode.REMOTE);
        SyncService.SyncResult result = localOnly.resolveConflict(VAULT, ConflictStrategy.KEEP_LOCAL);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("no remote configured"));
    }

    // ---------------------------------------------------------------
    // resolveConflict() - connection failure
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_connectionFails_returnsError() {
        remoteRepo.failOnConnect = true;
        SyncService.SyncResult result = service.resolveConflict(VAULT, ConflictStrategy.KEEP_LOCAL);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().startsWith("error:"));
        assertEquals("error", service.getSyncStatus());
    }

    // ---------------------------------------------------------------
    // syncAfterMerge() - save then upload
    // ---------------------------------------------------------------

    @Test
    void syncAfterMerge_savesBeforeUpload() {
        String mergedContent = "{\"entries\":[{\"id\":1},{\"id\":2}]}";

        SyncService.SyncResult result = service.syncAfterMerge(VAULT,
            () -> localRepo.writeFile(VAULT, mergedContent));

        assertTrue(result.isSuccess());
        assertEquals("merged", result.getMessage());
        assertTrue(remoteRepo.uploadCalled, "File should have been uploaded after save");
        assertEquals(mergedContent, remoteRepo.files.get(VAULT),
            "Remote should contain the merged content");
        assertEquals("synced", service.getSyncStatus());
        assertTrue(service.getLastSyncTime() > 0);
        // Sync meta should be updated
        assertNotNull(localRepo.readSyncMeta(VAULT));
    }

    @Test
    void syncAfterMerge_saveFails_skipsUpload() {
        SyncService.SyncResult result = service.syncAfterMerge(VAULT,
            () -> { throw new IOException("Disk full"); });

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("save failed"));
        assertFalse(remoteRepo.uploadCalled, "Upload must be skipped when save fails");
        assertEquals("error", service.getSyncStatus());
    }

    @Test
    void syncAfterMerge_saveSucceeds_uploadFails_returnsError() throws IOException {
        String mergedContent = "{\"entries\":[{\"merged\":true}]}";
        remoteRepo.failOnConnect = true;

        SyncService.SyncResult result = service.syncAfterMerge(VAULT,
            () -> localRepo.writeFile(VAULT, mergedContent));

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().startsWith("error:"));
        assertEquals(mergedContent, localRepo.readFile(VAULT));
        assertEquals("error", service.getSyncStatus());
    }

    @Test
    void syncAfterMerge_noRemote_returnsError() {
        SyncService localOnly = new SyncService(localRepo, null, StorageMode.REMOTE);
        SyncService.SyncResult result = localOnly.syncAfterMerge(VAULT,
            () -> localRepo.writeFile(VAULT, "{\"entries\":[]}"));

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("no remote configured"));
    }

    @Test
    void syncAfterMerge_uploadsCurrentContent_notStaleFile() throws IOException {
        String staleContent = "{\"entries\":[{\"stale\":true}]}";
        localRepo.writeFile(VAULT, staleContent);

        String mergedContent = "{\"entries\":[{\"stale\":true},{\"new\":true}]}";

        SyncService.SyncResult result = service.syncAfterMerge(VAULT,
            () -> localRepo.writeFile(VAULT, mergedContent));

        assertTrue(result.isSuccess());
        assertEquals(mergedContent, remoteRepo.files.get(VAULT),
            "Remote must contain the merged content, not the stale pre-merge content");
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

    // ---------------------------------------------------------------
    // synchronize() - sync meta is persisted across syncs
    // ---------------------------------------------------------------

    @Test
    void synchronize_syncMetaPersistsAcrossSyncs() throws IOException {
        String content = "{\"entries\":[{\"id\":1}]}";
        localRepo.writeFile(VAULT, content);

        // First sync: uploads and saves meta
        service.synchronize(VAULT);
        String meta1 = localRepo.readSyncMeta(VAULT);
        assertNotNull(meta1);

        // Set remote to same content (simulating successful previous upload)
        remoteRepo.putRemoteFile(VAULT, content, System.currentTimeMillis());

        // Second sync: hashes match, synced
        SyncService.SyncResult result2 = service.synchronize(VAULT);
        assertTrue(result2.isSuccess());
        assertEquals("synced", result2.getMessage());
    }

    // ===================================================================
    // In-memory test doubles
    // ===================================================================

    static class InMemoryLocalRepo implements LocalSyncRepository {
        final Map<String, String> files = new LinkedHashMap<>();
        final Map<String, Long> timestamps = new LinkedHashMap<>();
        final Map<String, String> pending = new LinkedHashMap<>();
        final Map<String, String> syncMeta = new LinkedHashMap<>();
        boolean backupCreated = false;

        @Override
        public String getFilePath(String filename) {
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

        @Override
        public void saveSyncMeta(String filename, String hash) {
            syncMeta.put(filename, hash);
        }

        @Override
        public String readSyncMeta(String filename) {
            return syncMeta.get(filename);
        }
    }

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
