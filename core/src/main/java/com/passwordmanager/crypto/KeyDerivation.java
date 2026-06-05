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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
     *
     * <p>The returned {@link SecretKeySpec} copies the key bytes internally and
     * cannot be reliably wiped (JCA limitation). Callers that need to zero the key
     * material after use should prefer {@link #deriveKeyBytes} and wipe the array.
     */
    public static SecretKey deriveKey(char[] password, byte[] salt, int iterations) throws VaultEncryptionException {
        byte[] keyBytes = deriveKeyBytes(password, salt, iterations);
        try {
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            SecureWiper.wipe(keyBytes);
        }
    }

    /**
     * Derives raw AES-256 key material (32 bytes) from the given password, salt,
     * and iteration count. Unlike {@link #deriveKey}, the caller owns the returned
     * array and is responsible for wiping it with {@link SecureWiper#wipe(byte[])}
     * once the derived key is no longer needed. This enables real zeroing of the
     * KEK, which {@code SecretKeySpec.destroy()} cannot provide.
     */
    public static byte[] deriveKeyBytes(char[] password, byte[] salt, int iterations) throws VaultEncryptionException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_SIZE);
            try {
                // tmp.getEncoded() returns a fresh copy that the caller now owns.
                return factory.generateSecret(spec).getEncoded();
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
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }
}
