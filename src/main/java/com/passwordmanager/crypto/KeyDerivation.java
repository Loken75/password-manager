package com.passwordmanager.crypto;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

/**
 * Key derivation using PBKDF2WithHmacSHA256.
 */
public class KeyDerivation {
    private static final int ITERATIONS = 100000;
    private static final int KEY_SIZE = 256;
    private static final int SALT_LENGTH = 32;

    /**
     * Derives an AES-256 key from the given password and salt.
     */
    public static SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_SIZE);
        try {
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } finally {
            spec.clearPassword();
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
