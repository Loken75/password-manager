package com.passwordmanager.sync;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
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
        return baseDirectory + File.separator + filename;
    }

    public boolean fileExists(String filename) {
        return new File(getFilePath(filename)).exists();
    }

    public String readFile(String filename) throws IOException {
        return new String(Files.readAllBytes(Paths.get(getFilePath(filename))), StandardCharsets.UTF_8);
    }

    public void writeFile(String filename, String content) throws IOException {
        Files.write(Paths.get(getFilePath(filename)), content.getBytes(StandardCharsets.UTF_8));
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
            Files.copy(Paths.get(getFilePath(filename)), Paths.get(getFilePath(backupName)));
        }
    }
}
