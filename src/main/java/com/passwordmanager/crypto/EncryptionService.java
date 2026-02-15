package com.passwordmanager.crypto;

import javax.crypto.SecretKey;

/**
 * Abstraction for vault encryption operations (DEK/KEK envelope encryption).
 * Enables testing VaultManager without real cryptographic operations.
 */
public interface EncryptionService {

    /**
     * Creates a new vault session: generates a random DEK, encrypts it with
     * a KEK derived from the master password.
     */
    VaultSession createSession(char[] masterPassword) throws VaultEncryptionException;

    /**
     * Opens an existing vault session: derives the KEK from the master password,
     * decrypts the DEK.
     */
    VaultSession openSession(byte[] salt, byte[] kekIv, byte[] encryptedDek,
                             int kdfIterations, char[] masterPassword) throws VaultDecryptionException;

    /**
     * Encrypts vault data with the session's DEK.
     */
    EncryptedPayload encryptData(byte[] plaintext, SecretKey dataKey) throws VaultEncryptionException;

    /**
     * Decrypts vault data with the session's DEK.
     */
    byte[] decryptData(byte[] iv, byte[] ciphertext, SecretKey dataKey) throws VaultDecryptionException;

    /**
     * Re-encrypts the DEK with a new password-derived KEK (for password change).
     */
    VaultSession changePassword(VaultSession session, char[] newPassword) throws VaultEncryptionException;

    /**
     * Decrypts a v1.0 legacy vault (password directly derives the data key).
     */
    byte[] decryptLegacy(byte[] salt, byte[] iv, byte[] ciphertext,
                         char[] masterPassword) throws VaultDecryptionException;
}
