package com.passwordmanager.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

class KeyDerivationTest {

    @Test
    void deriveKey_producesAes256Key() throws VaultEncryptionException {
        char[] password = "TestPassword123!".toCharArray();
        byte[] salt = KeyDerivation.generateSalt();
        SecretKey key = KeyDerivation.deriveKey(password, salt);
        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length); // 256 bits
    }

    @Test
    void deriveKey_sameSaltProducesSameKey() throws VaultEncryptionException {
        char[] password = "TestPassword123!".toCharArray();
        byte[] salt = KeyDerivation.generateSalt();
        SecretKey key1 = KeyDerivation.deriveKey(password, salt, 1000);
        SecretKey key2 = KeyDerivation.deriveKey(password, salt, 1000);
        assertArrayEquals(key1.getEncoded(), key2.getEncoded());
    }

    @Test
    void deriveKey_differentSaltsProduceDifferentKeys() throws VaultEncryptionException {
        char[] password = "TestPassword123!".toCharArray();
        SecretKey key1 = KeyDerivation.deriveKey(password, KeyDerivation.generateSalt(), 1000);
        SecretKey key2 = KeyDerivation.deriveKey(password, KeyDerivation.generateSalt(), 1000);
        assertFalse(java.util.Arrays.equals(key1.getEncoded(), key2.getEncoded()));
    }

    @Test
    void deriveKey_differentIterationsProduceDifferentKeys() throws VaultEncryptionException {
        char[] password = "TestPassword123!".toCharArray();
        byte[] salt = KeyDerivation.generateSalt();
        SecretKey key1 = KeyDerivation.deriveKey(password, salt, 1000);
        SecretKey key2 = KeyDerivation.deriveKey(password, salt, 2000);
        assertFalse(java.util.Arrays.equals(key1.getEncoded(), key2.getEncoded()));
    }

    @Test
    void generateSalt_returns32Bytes() {
        byte[] salt = KeyDerivation.generateSalt();
        assertEquals(32, salt.length);
    }

    @Test
    void generateSalt_producesUniqueSalts() {
        byte[] salt1 = KeyDerivation.generateSalt();
        byte[] salt2 = KeyDerivation.generateSalt();
        assertFalse(java.util.Arrays.equals(salt1, salt2));
    }

    @Test
    void getDefaultIterations_returns600k() {
        assertEquals(600_000, KeyDerivation.getDefaultIterations());
    }
}
