package com.passwordmanager.crypto;

import com.passwordmanager.util.SecureWiper;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKey;
import javax.security.auth.Destroyable;

/**
 * Holds the DEK (data encryption key) and envelope metadata for an unlocked vault.
 * Must be destroyed when the vault is locked or the application exits.
 * Implements AutoCloseable to support try-with-resources patterns.
 */
public class VaultSession implements Destroyable, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(VaultSession.class.getName());
    private SecretKey dataKey;
    private byte[] salt;
    private byte[] kekIv;
    private byte[] encryptedDek;
    private int kdfIterations;
    private boolean destroyed = false;

    VaultSession(SecretKey dataKey, byte[] salt, byte[] kekIv,
                 byte[] encryptedDek, int kdfIterations) {
        this.dataKey = dataKey;
        this.salt = salt;
        this.kekIv = kekIv;
        this.encryptedDek = encryptedDek;
        this.kdfIterations = kdfIterations;
    }

    public SecretKey getDataKey() { return dataKey; }
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
            if (dataKey != null) {
                try { dataKey.destroy(); } catch (Exception e) {
                    LOGGER.log(Level.FINE, "SecretKey.destroy() not supported", e);
                }
            }
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
}
