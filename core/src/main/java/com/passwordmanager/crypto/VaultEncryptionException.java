package com.passwordmanager.crypto;

/**
 * Thrown when vault encryption or key management operations fail.
 */
public class VaultEncryptionException extends Exception {

    public VaultEncryptionException(String message) {
        super(message);
    }

    public VaultEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
