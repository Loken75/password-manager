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
     * @param aad optional Additional Authenticated Data (e.g. vault version) bound to ciphertext
     */
    EncryptedPayload encryptData(byte[] plaintext, SecretKey dataKey, byte[] aad) throws VaultEncryptionException;

    /**
     * Encrypts vault data with the session's DEK (no AAD).
     */
    default EncryptedPayload encryptData(byte[] plaintext, SecretKey dataKey) throws VaultEncryptionException {
        return encryptData(plaintext, dataKey, null);
    }

    /**
     * Decrypts vault data with the session's DEK.
     * @param aad optional AAD that was used during encryption (must match exactly)
     */
    byte[] decryptData(byte[] iv, byte[] ciphertext, SecretKey dataKey, byte[] aad) throws VaultDecryptionException;

    /**
     * Decrypts vault data with the session's DEK (no AAD).
     */
    default byte[] decryptData(byte[] iv, byte[] ciphertext, SecretKey dataKey) throws VaultDecryptionException {
        return decryptData(iv, ciphertext, dataKey, null);
    }

    /**
     * Re-encrypts the DEK with a new password-derived KEK (for password change).
     */
    VaultSession changePassword(VaultSession session, char[] newPassword) throws VaultEncryptionException;

    /**
     * Decrypts a v1.0 legacy vault (password directly derives the data key).
     */
    byte[] decryptLegacy(byte[] salt, byte[] iv, byte[] ciphertext,
                         char[] masterPassword) throws VaultDecryptionException;

    /**
     * Adopts an envelope (password-derived key material) from another copy of the
     * SAME vault into this session, keeping the existing DEK. Used to propagate a
     * master-password change made on another device without knowing the new password:
     * because synced copies share the DEK, the foreign envelope still wraps it (R4).
     *
     * <p>Only safe between copies of the same vault (the shared-DEK invariant of sync).
     */
    default void adoptEnvelope(VaultSession session, byte[] salt, byte[] kekIv,
                               byte[] encryptedDek, int kdfIterations) {
        session.updateEnvelope(
            java.util.Arrays.copyOf(salt, salt.length),
            java.util.Arrays.copyOf(kekIv, kekIv.length),
            java.util.Arrays.copyOf(encryptedDek, encryptedDek.length),
            kdfIterations);
    }
}
