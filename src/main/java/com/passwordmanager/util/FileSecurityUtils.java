package com.passwordmanager.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized file permission utilities for securing sensitive files.
 * Supports both POSIX (Linux/macOS) and ACL (Windows) file systems.
 */
public final class FileSecurityUtils {
    private static final Logger LOGGER = Logger.getLogger(FileSecurityUtils.class.getName());

    private FileSecurityUtils() {}

    /**
     * Sets owner-only read/write permissions on a file.
     * Uses POSIX permissions on Linux/macOS, ACLs on Windows.
     */
    public static void setOwnerOnlyPermissions(Path path) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } else if (path.getFileSystem().supportedFileAttributeViews().contains("acl")) {
                setWindowsOwnerOnly(path);
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not set file permissions on: " + path, e);
        }
    }

    /**
     * Sets Windows ACL to owner-only read/write access.
     */
    private static void setWindowsOwnerOnly(Path path) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (aclView == null) return;

        UserPrincipal owner = aclView.getOwner();
        AclEntry entry = AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(
                AclEntryPermission.READ_DATA,
                AclEntryPermission.WRITE_DATA,
                AclEntryPermission.READ_ATTRIBUTES,
                AclEntryPermission.WRITE_ATTRIBUTES,
                AclEntryPermission.DELETE,
                AclEntryPermission.READ_ACL,
                AclEntryPermission.SYNCHRONIZE
            )
            .build();
        // Replace all ACL entries with owner-only access
        aclView.setAcl(Collections.singletonList(entry));
    }
}
