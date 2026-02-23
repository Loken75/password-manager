package com.passwordmanager.crypto;

import com.passwordmanager.util.SecureWiper;

/**
 * Holds the result of an AES-GCM encryption: IV + ciphertext (with appended auth tag).
 * Call {@link #wipe()} when the payload is no longer needed.
 */
public class EncryptedPayload {
    private final byte[] iv;
    private final byte[] ciphertext;

    public EncryptedPayload(byte[] iv, byte[] ciphertext) {
        this.iv = iv;
        this.ciphertext = ciphertext;
    }

    public byte[] getIv() { return iv; }
    public byte[] getCiphertext() { return ciphertext; }

    /**
     * Securely wipes the IV and ciphertext from memory.
     */
    public void wipe() {
        SecureWiper.wipe(iv);
        SecureWiper.wipe(ciphertext);
    }
}
