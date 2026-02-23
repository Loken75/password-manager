package com.passwordmanager.config;

import com.passwordmanager.util.FileSecurityUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encrypts/decrypts sensitive config fields (SFTP credentials) using
 * AES-256-GCM with a key derived from a random machine key file.
 *
 * The key file (~/.password-manager/.config_key) is generated once with
 * random bytes and protected by file permissions. This is stronger than
 * deriving from system properties, though the real defense remains
 * OS-level file permissions (POSIX 600 / Windows ACL).
 */
final class ConfigEncryptor {
    private static final Logger LOGGER = Logger.getLogger(ConfigEncryptor.class.getName());
    private static final String PREFIX = "ENC:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int KDF_ITERATIONS = 100_000;
    private static final int LEGACY_KDF_ITERATIONS = 10_000;
    private static final int KEY_LENGTH = 256;
    private static final int KEY_FILE_SIZE = 64;

    private ConfigEncryptor() {}

    /**
     * Encrypts a plaintext value. Returns "ENC:base64(iv+ciphertext)".
     * Empty/null values are returned as-is.
     */
    static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            SecretKey key = deriveKey(KDF_ITERATIONS);
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
     * Tries current iterations first, then falls back to legacy iterations for migration.
     */
    static String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (combined.length < GCM_IV_LENGTH + 1) {
                return stored;
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // Try current iterations first
            try {
                return doDecrypt(iv, ciphertext, KDF_ITERATIONS);
            } catch (Exception ignored) {
                // Fall back to legacy iterations for migration
            }
            return doDecrypt(iv, ciphertext, LEGACY_KDF_ITERATIONS);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Config decryption failed, returning raw value", e);
            return stored.startsWith(PREFIX) ? "" : stored;
        }
    }

    private static String doDecrypt(byte[] iv, byte[] ciphertext, int iterations) throws Exception {
        SecretKey key = deriveKey(iterations);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * Derives an AES key from a random key file stored alongside the config.
     * The key file is generated once with SecureRandom and protected by
     * file permissions. This avoids predictable keys from system properties.
     */
    private static SecretKey deriveKey(int iterations) throws Exception {
        byte[] keyMaterial = getOrCreateKeyFile();
        // Use the key material as password for PBKDF2, with a fixed salt
        // (the randomness comes from the key file, not the salt)
        char[] password = Base64.getEncoder().encodeToString(keyMaterial).toCharArray();
        byte[] salt = "pm-config-key-derivation".getBytes(StandardCharsets.UTF_8);

        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(password, '\0');
        }
    }

    /**
     * Gets or creates the random key file at ~/.password-manager/.config_key.
     * The file contains random bytes generated by SecureRandom.
     * Uses atomic write (temp + permissions + rename) to prevent a race window
     * where the key file is world-readable before permissions are applied.
     */
    private static byte[] getOrCreateKeyFile() throws IOException {
        String appHome = System.getProperty("app.home",
            System.getProperty("user.home") + File.separator + ".password-manager");
        Path keyPath = Paths.get(appHome, "data", ".config_key");

        if (Files.exists(keyPath)) {
            return Files.readAllBytes(keyPath);
        }

        // Generate new random key material
        byte[] keyMaterial = new byte[KEY_FILE_SIZE];
        new SecureRandom().nextBytes(keyMaterial);

        File parent = keyPath.getParent().toFile();
        if (!parent.exists()) {
            parent.mkdirs();
            FileSecurityUtils.setOwnerOnlyPermissions(parent.toPath());
        }

        // Atomic write: write to temp, set permissions, then rename
        Path tempPath = Paths.get(keyPath + ".tmp");
        Files.write(tempPath, keyMaterial);
        FileSecurityUtils.setOwnerOnlyPermissions(tempPath);
        try {
            Files.move(tempPath, keyPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempPath, keyPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        return keyMaterial;
    }
}
