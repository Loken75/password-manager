package com.passwordmanager.crypto;

import com.passwordmanager.util.SecureWiper;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

/**
 * Key derivation using PBKDF2WithHmacSHA256.
 * OWASP 2025 minimum: 600,000 iterations for PBKDF2-HMAC-SHA256.
 */
public class KeyDerivation {
    private static final int DEFAULT_ITERATIONS = 600_000;
    private static final int KEY_SIZE = 256;
    private static final int SALT_LENGTH = 32;

    public static int getDefaultIterations() {
        return DEFAULT_ITERATIONS;
    }

    /**
     * Derives an AES-256 key from the given password and salt with default iterations.
     */
    public static SecretKey deriveKey(char[] password, byte[] salt) throws VaultEncryptionException {
        return deriveKey(password, salt, DEFAULT_ITERATIONS);
    }

    /**
     * Derives an AES-256 key from the given password, salt, and iteration count.
     */
    public static SecretKey deriveKey(char[] password, byte[] salt, int iterations) throws VaultEncryptionException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_SIZE);
            try {
                SecretKey tmp = factory.generateSecret(spec);
                byte[] encoded = tmp.getEncoded();
                try {
                    return new SecretKeySpec(encoded, "AES");
                } finally {
                    SecureWiper.wipe(encoded);
                }
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            throw new VaultEncryptionException("Key derivation failed", e);
        }
    }

    /**
     * Generates a cryptographically random 32-byte salt.
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
}
