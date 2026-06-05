package com.passwordmanager.vault.store;

import com.passwordmanager.util.FileSecurityUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link VaultStore} backed by the local filesystem via {@code java.nio.file}.
 * Used by the desktop client and by Android internal storage. Writes are atomic
 * (temp file + {@code ATOMIC_MOVE}, with a non-atomic fallback) and files are
 * restricted to owner-only permissions through {@link FileSecurityUtils}.
 */
public class FileVaultStore implements VaultStore {
    private final String baseDirectory;

    public FileVaultStore(String baseDirectory) {
        this.baseDirectory = baseDirectory;
        File dir = new File(baseDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            FileSecurityUtils.setOwnerOnlyPermissions(dir.toPath());
        }
    }

    /**
     * Validates that a name is a bare filename without path-traversal components.
     */
    private static void validateName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Filename must not be empty");
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")
                || name.startsWith("~")) {
            throw new IllegalArgumentException("Invalid filename: path traversal detected");
        }
    }

    @Override
    public String pathOf(String name) {
        validateName(name);
        return baseDirectory + File.separator + name;
    }

    @Override
    public List<String> list() throws IOException {
        File[] files = new File(baseDirectory).listFiles();
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) names.add(f.getName());
            }
        }
        return names;
    }

    @Override
    public boolean exists(String name) {
        return new File(pathOf(name)).exists();
    }

    @Override
    public long size(String name) throws IOException {
        return Files.size(Paths.get(pathOf(name)));
    }

    @Override
    public byte[] read(String name) throws IOException {
        return Files.readAllBytes(Paths.get(pathOf(name)));
    }

    @Override
    public void writeAtomic(String name, byte[] data) throws IOException {
        Path path = Paths.get(pathOf(name));
        Path tempPath = Paths.get(pathOf(name + ".tmp"));
        Files.write(tempPath, data);
        FileSecurityUtils.setOwnerOnlyPermissions(tempPath);
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempPath);
        }
        FileSecurityUtils.setOwnerOnlyPermissions(path);
    }

    @Override
    public void copy(String from, String to) throws IOException {
        Path dest = Paths.get(pathOf(to));
        Files.copy(Paths.get(pathOf(from)), dest, StandardCopyOption.REPLACE_EXISTING);
        FileSecurityUtils.setOwnerOnlyPermissions(dest);
    }

    @Override
    public void delete(String name) throws IOException {
        Files.deleteIfExists(Paths.get(pathOf(name)));
    }

    @Override
    public long lastModified(String name) {
        File f = new File(pathOf(name));
        return f.exists() ? f.lastModified() : 0;
    }

    @Override
    public String describe() {
        return baseDirectory;
    }
}
