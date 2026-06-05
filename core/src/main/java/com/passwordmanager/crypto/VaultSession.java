package com.passwordmanager.crypto;

import com.passwordmanager.util.SecureWiper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.Destroyable;

/**
 * Holds the DEK (data encryption key) and envelope metadata for an unlocked vault.
 * Must be destroyed when the vault is locked or the application exits.
 * Implements AutoCloseable to support try-with-resources patterns.
 *
 * <p>The DEK is held as a raw {@code byte[]} owned by this session so that
 * {@link #destroy()} can actually zero it (unlike {@code SecretKeySpec.destroy()},
 * which is a no-op on the standard JCA provider). {@link #getDataKey()} rebuilds a
 * transient {@link SecretKeySpec} on demand for each cipher operation; that
 * short-lived copy is outside this session's control (a residual JCA limitation),
 * but the authoritative key material kept here is wiped on destroy.
 */
public class VaultSession implements Destroyable, AutoCloseable {
    private final byte[] rawDek;
    private byte[] salt;
    private byte[] kekIv;
    private byte[] encryptedDek;
    private int kdfIterations;
    private volatile boolean destroyed = false;

    VaultSession(byte[] rawDek, byte[] salt, byte[] kekIv,
                 byte[] encryptedDek, int kdfIterations) {
        // Defensive copy: this session owns its DEK and wipes it on destroy,
        // independently of the caller's buffer (which the caller wipes itself).
        this.rawDek = rawDek.clone();
        this.salt = salt;
        this.kekIv = kekIv;
        this.encryptedDek = encryptedDek;
        this.kdfIterations = kdfIterations;
    }

    /**
     * Rebuilds a transient AES key from the owned DEK bytes for a single use.
     *
     * @throws IllegalStateException if the session has already been destroyed
     */
    public SecretKey getDataKey() {
        if (destroyed) {
            throw new IllegalStateException("VaultSession has been destroyed");
        }
        return new SecretKeySpec(rawDek, "AES");
    }

    public byte[] getSalt() { return salt != null ? salt.clone() : null; }
    public byte[] getKekIv() { return kekIv != null ? kekIv.clone() : null; }
    public byte[] getEncryptedDek() { return encryptedDek != null ? encryptedDek.clone() : null; }
    public int getKdfIterations() { return kdfIterations; }

    /**
     * Updates the envelope metadata after a password change.
     */
    void updateEnvelope(byte[] newSalt, byte[] newKekIv, byte[] newEncryptedDek, int newIterations) {
        SecureWiper.wipe(this.salt);
        SecureWiper.wipe(this.kekIv);
        SecureWiper.wipe(this.encryptedDek);
        this.salt = newSalt;
        this.kekIv = newKekIv;
        this.encryptedDek = newEncryptedDek;
        this.kdfIterations = newIterations;
    }

    @Override
    public void destroy() {
        if (!destroyed) {
            SecureWiper.wipe(rawDek);
            SecureWiper.wipe(salt);
            SecureWiper.wipe(kekIv);
            SecureWiper.wipe(encryptedDek);
            destroyed = true;
        }
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * AutoCloseable implementation delegates to destroy().
     */
    @Override
    public void close() {
        destroy();
    }

    /**
     * Package-private accessor exposing the live DEK buffer so tests can assert it
     * is zeroed after {@link #destroy()}. Not for production use.
     */
    byte[] dataKeyBytesForTest() {
        return rawDek;
    }
}
