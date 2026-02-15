package com.passwordmanager.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

/**
 * Centralized file permission utilities for securing sensitive files.
 */
public final class FileSecurityUtils {

    private FileSecurityUtils() {}

    /**
     * Sets owner-only read/write permissions on a file (POSIX systems).
     * No-op on non-POSIX file systems (e.g. Windows).
     */
    public static void setOwnerOnlyPermissions(Path path) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
        } catch (IOException e) {
            // Best effort on non-POSIX systems
        }
    }
}
