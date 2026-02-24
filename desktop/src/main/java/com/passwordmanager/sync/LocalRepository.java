package com.passwordmanager.sync;

import com.passwordmanager.util.FileSecurityUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Manages local file operations for vault storage.
 */
public class LocalRepository {
    private final String baseDirectory;

    public LocalRepository(String baseDirectory) {
        this.baseDirectory = baseDirectory;
        File dir = new File(baseDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public String getFilePath(String filename) {
        validateFilename(filename);
        return baseDirectory + File.separator + filename;
    }

    /**
     * Validates that a filename does not contain path traversal sequences
     * or absolute path components.
     */
    private void validateFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename must not be empty");
        }
        if (filename.contains("..") || filename.contains(File.separator)
                || filename.contains("/") || filename.contains("\\")
                || filename.startsWith("~")) {
            throw new IllegalArgumentException("Invalid filename: path traversal detected");
        }
    }

    public boolean fileExists(String filename) {
        return new File(getFilePath(filename)).exists();
    }

    public String readFile(String filename) throws IOException {
        return new String(Files.readAllBytes(Paths.get(getFilePath(filename))), StandardCharsets.UTF_8);
    }

    public void writeFile(String filename, String content) throws IOException {
        Path path = Paths.get(getFilePath(filename));
        // Atomic write: write to temp, set permissions, then rename
        Path tempPath = Paths.get(getFilePath(filename + ".tmp"));
        Files.write(tempPath, content.getBytes(StandardCharsets.UTF_8));
        FileSecurityUtils.setOwnerOnlyPermissions(tempPath);
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    public void deleteFile(String filename) throws IOException {
        Files.deleteIfExists(Paths.get(getFilePath(filename)));
    }

    public long getLastModified(String filename) {
        File f = new File(getFilePath(filename));
        return f.exists() ? f.lastModified() : 0;
    }

    public void savePending(String filename, String content) throws IOException {
        writeFile(filename + ".pending", content);
    }

    public boolean hasPending(String filename) {
        return fileExists(filename + ".pending");
    }

    public String readPending(String filename) throws IOException {
        return readFile(filename + ".pending");
    }

    public void clearPending(String filename) throws IOException {
        deleteFile(filename + ".pending");
    }

    public void createBackup(String filename) throws IOException {
        if (fileExists(filename)) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String backupName = filename.replace(".enc", "_backup_" + timestamp + ".enc");
            Path backupPath = Paths.get(getFilePath(backupName));
            Files.copy(Paths.get(getFilePath(filename)), backupPath);
            FileSecurityUtils.setOwnerOnlyPermissions(backupPath);
            cleanupOldBackups(filename, 5);
        }
    }

    /**
     * Retains only the most recent N backup files for the given vault filename,
     * deleting older ones.
     */
    private void cleanupOldBackups(String filename, int maxBackups) {
        String prefix = filename.replace(".enc", "_backup_");
        File dir = new File(baseDirectory);
        File[] backups = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".enc"));
        if (backups == null || backups.length <= maxBackups) return;

        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = maxBackups; i < backups.length; i++) {
            backups[i].delete();
        }
    }
}
