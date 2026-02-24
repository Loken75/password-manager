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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
     * Sets owner-only permissions on a file or directory.
     * Files get rw------- (600), directories get rwx------ (700).
     * Uses POSIX permissions on Linux/macOS, ACLs on Windows.
     */
    public static void setOwnerOnlyPermissions(Path path) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                if (Files.isDirectory(path)) {
                    Files.setPosixFilePermissions(path, EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
                } else {
                    Files.setPosixFilePermissions(path, EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE));
                }
            } else if (path.getFileSystem().supportedFileAttributeViews().contains("acl")) {
                setWindowsOwnerOnly(path);
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not set file permissions on: " + path, e);
        }
    }

    /**
     * Sets Windows ACL to owner-only full control.
     * Removes all non-owner, non-SYSTEM entries so that other users have no access.
     * The owner gets full control (equivalent to POSIX 700/600 where only the
     * owner has all rights).
     */
    private static void setWindowsOwnerOnly(Path path) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (aclView == null) return;

        UserPrincipal owner = aclView.getOwner();

        AclEntry ownerEntry = AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(EnumSet.allOf(AclEntryPermission.class))
            .build();

        // Preserve SYSTEM entries, replace everything else with owner-only
        List<AclEntry> newAcl = new ArrayList<>();
        newAcl.add(ownerEntry);
        for (AclEntry existing : aclView.getAcl()) {
            String principalName = existing.principal().getName();
            if (principalName.endsWith("\\SYSTEM") || principalName.equals("SYSTEM")
                    || principalName.endsWith("\\SYSTÈME")) {
                newAcl.add(existing);
            }
        }
        aclView.setAcl(newAcl);
    }
}
