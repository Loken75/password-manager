package com.passwordmanager.crypto;

/**
 * Holds the result of an AES-GCM encryption: IV + ciphertext (with appended auth tag).
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
}
