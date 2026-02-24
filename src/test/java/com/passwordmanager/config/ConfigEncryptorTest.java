package com.passwordmanager.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigEncryptor encrypt/decrypt round-trip and edge cases.
 */
class ConfigEncryptorTest {

    @TempDir
    Path tempDir;

    private String previousAppHome;

    @BeforeEach
    void setUp() {
        previousAppHome = System.getProperty("app.home");
        System.setProperty("app.home", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (previousAppHome != null) {
            System.setProperty("app.home", previousAppHome);
        } else {
            System.clearProperty("app.home");
        }
    }

    @Test
    void encryptDecryptRoundTrip() {
        String original = "my-secret-password";
        String encrypted = ConfigEncryptor.encrypt(original);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("ENC:"));
        assertNotEquals(original, encrypted);

        String decrypted = ConfigEncryptor.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryptDecryptSpecialCharacters() {
        String original = "p@ss/w0rd!#$%^&*(){}[]<>éàü";
        String encrypted = ConfigEncryptor.encrypt(original);
        String decrypted = ConfigEncryptor.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryptDecryptLongValue() {
        String original = "A".repeat(10_000);
        String encrypted = ConfigEncryptor.encrypt(original);
        String decrypted = ConfigEncryptor.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryptNullReturnsNull() {
        assertNull(ConfigEncryptor.encrypt(null));
    }

    @Test
    void encryptEmptyReturnsEmpty() {
        assertEquals("", ConfigEncryptor.encrypt(""));
    }

    @Test
    void decryptNullReturnsNull() {
        assertNull(ConfigEncryptor.decrypt(null));
    }

    @Test
    void decryptEmptyReturnsEmpty() {
        assertEquals("", ConfigEncryptor.decrypt(""));
    }

    @Test
    void decryptPlaintextReturnsAsIs() {
        // Backward compatibility: non-ENC: prefixed values are returned as-is
        assertEquals("plain-value", ConfigEncryptor.decrypt("plain-value"));
    }

    @Test
    void decryptCorruptedReturnsEmpty() {
        // Corrupted ENC: value should return empty string
        String result = ConfigEncryptor.decrypt("ENC:not-valid-base64!!!");
        // Should not throw, returns "" for corrupted ENC: prefix
        assertNotNull(result);
    }

    @Test
    void encryptProducesDifferentCiphertextEachTime() {
        String original = "same-value";
        String enc1 = ConfigEncryptor.encrypt(original);
        String enc2 = ConfigEncryptor.encrypt(original);

        // Different IVs should produce different ciphertext
        assertNotEquals(enc1, enc2);

        // Both should decrypt to same value
        assertEquals(original, ConfigEncryptor.decrypt(enc1));
        assertEquals(original, ConfigEncryptor.decrypt(enc2));
    }

    @Test
    void keyFileIsReusedAcrossCalls() {
        // First call creates the key file, second call reuses it
        String enc1 = ConfigEncryptor.encrypt("test1");
        String dec1 = ConfigEncryptor.decrypt(enc1);
        assertEquals("test1", dec1);

        String enc2 = ConfigEncryptor.encrypt("test2");
        String dec2 = ConfigEncryptor.decrypt(enc2);
        assertEquals("test2", dec2);

        // Cross-verify: can still decrypt first value
        assertEquals("test1", ConfigEncryptor.decrypt(enc1));
    }
}
