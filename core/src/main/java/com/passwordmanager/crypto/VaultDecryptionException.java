package com.passwordmanager.crypto;

/**
 * Thrown when vault decryption fails (wrong password or corrupted data).
 */
public class VaultDecryptionException extends Exception {

    public VaultDecryptionException(String message) {
        super(message);
    }

    public VaultDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
