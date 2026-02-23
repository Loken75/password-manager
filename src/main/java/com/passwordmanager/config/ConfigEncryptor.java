package com.passwordmanager.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encrypts/decrypts sensitive config fields (SFTP credentials) using
 * AES-256-GCM with a key derived from machine-specific properties.
 *
 * This is obfuscated storage: it prevents casual reading of config.properties
 * but would not resist an attacker who reverse-engineers the application.
 * The real defense remains POSIX 600 file permissions.
 */
final class ConfigEncryptor {
    private static final Logger LOGGER = Logger.getLogger(ConfigEncryptor.class.getName());
    private static final String PREFIX = "ENC:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int KDF_ITERATIONS = 10_000;
    private static final int KEY_LENGTH = 256;

    private ConfigEncryptor() {}

    /**
     * Encrypts a plaintext value. Returns "ENC:base64(iv+ciphertext)".
     * Empty/null values are returned as-is.
     */
    static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            SecretKey key = deriveKey();
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Config encryption failed, storing plaintext", e);
            return plaintext;
        }
    }

    /**
     * Decrypts an "ENC:..." value. If not prefixed, returns as-is (backward compat).
     */
    static String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Config decryption failed, returning raw value", e);
            return stored.substring(PREFIX.length());
        }
    }

    /**
     * Derives an AES key from machine-specific properties.
     * Uses user.home + os.name + user.name as seed material.
     */
    private static SecretKey deriveKey() throws Exception {
        String seed = System.getProperty("user.home", "")
                + "|" + System.getProperty("os.name", "")
                + "|" + System.getProperty("user.name", "");
        // Stable salt derived from the seed itself (not random, intentionally)
        byte[] salt = ("pm-config-salt-" + seed.hashCode()).getBytes(StandardCharsets.UTF_8);

        PBEKeySpec spec = new PBEKeySpec(seed.toCharArray(), salt, KDF_ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
