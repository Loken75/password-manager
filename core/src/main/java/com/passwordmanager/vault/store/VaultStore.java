package com.passwordmanager.vault.store;

import java.io.IOException;
import java.util.List;

/**
 * Storage abstraction for vault files, decoupling {@link com.passwordmanager.vault.VaultManager}
 * from the underlying filesystem. A store owns a single "working folder" and exposes
 * filename-based primitives over it.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link FileVaultStore} — backed by {@code java.nio.file} (desktop, Android internal storage).</li>
 *   <li>A SAF-backed store (Android) — backed by {@code content://} document trees.</li>
 * </ul>
 *
 * <p>Names are bare filenames (e.g. {@code "vault_alice.enc"}, {@code "vault_alice.enc.bak"}),
 * never paths; implementations must reject path-traversal sequences.
 */
public interface VaultStore {

    /** Lists the bare filenames currently present in the working folder. */
    List<String> list() throws IOException;

    /** Returns whether a file with the given name exists. */
    boolean exists(String name);

    /** Returns the size in bytes of the named file. */
    long size(String name) throws IOException;

    /** Reads the full contents of the named file. */
    byte[] read(String name) throws IOException;

    /**
     * Writes {@code data} to {@code name} as atomically as the backing store allows
     * (temp file + rename where supported) and applies owner-only permissions where applicable.
     */
    void writeAtomic(String name, byte[] data) throws IOException;

    /** Copies {@code from} to {@code to}, applying owner-only permissions on the destination where applicable. */
    void copy(String from, String to) throws IOException;

    /** Deletes the named file if it exists. */
    void delete(String name) throws IOException;

    /** Returns the last-modified time (epoch millis), or {@code 0} if the file does not exist. */
    long lastModified(String name);

    /**
     * Returns a real filesystem path for the named file.
     *
     * @throws UnsupportedOperationException for stores that have no filesystem path (e.g. SAF).
     */
    String pathOf(String name);

    /** Human-readable description of the working folder, for UI and logging. */
    String describe();
}
