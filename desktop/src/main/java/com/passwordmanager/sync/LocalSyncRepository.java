package com.passwordmanager.sync;

import java.io.IOException;

/**
 * Abstraction for local vault file operations.
 * Implemented by {@link LocalRepository} for production use.
 */
public interface LocalSyncRepository {

    /** Returns the full filesystem path for the given filename. */
    String getFilePath(String filename);

    /** Returns true if the file exists locally. */
    boolean fileExists(String filename);

    /** Reads the file content as a UTF-8 string. */
    String readFile(String filename) throws IOException;

    /** Writes content to the file (atomic write). */
    void writeFile(String filename, String content) throws IOException;

    /** Returns the last-modified timestamp in millis, or 0 if absent. */
    long getLastModified(String filename);

    /** Returns true if there are pending (unsynced) changes for the file. */
    boolean hasPending(String filename);

    /** Reads the pending content for the file. */
    String readPending(String filename) throws IOException;

    /** Saves content as pending (unsynced) changes. */
    void savePending(String filename, String content) throws IOException;

    /** Clears any pending changes for the file. */
    void clearPending(String filename) throws IOException;

    /** Creates a timestamped backup of the file. */
    void createBackup(String filename) throws IOException;
}
