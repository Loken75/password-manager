package com.passwordmanager.sync;

/**
 * Enumerates conflict resolution strategies for file-level sync conflicts.
 */
public enum ConflictStrategy {
    KEEP_LOCAL,
    KEEP_REMOTE,
    KEEP_BOTH
}
