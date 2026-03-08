package com.passwordmanager.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultSessionTest {

    private final CryptoService cryptoService = new CryptoService();

    private VaultSession createTestSession() throws VaultEncryptionException {
        return cryptoService.createSession("TestPassword123!".toCharArray());
    }

    @Test
    void newSession_isNotDestroyed() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        assertFalse(session.isDestroyed());
        assertNotNull(session.getDataKey());
        session.destroy();
    }

    @Test
    void destroy_setsDestroyedFlag() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        session.destroy();
        assertTrue(session.isDestroyed());
    }

    @Test
    void destroy_isIdempotent() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        session.destroy();
        session.destroy(); // should not throw
        assertTrue(session.isDestroyed());
    }

    @Test
    void close_delegatesToDestroy() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        session.close();
        assertTrue(session.isDestroyed());
    }

    @Test
    void tryWithResources_destroysSession() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        try (session) {
            assertFalse(session.isDestroyed());
        }
        assertTrue(session.isDestroyed());
    }

    @Test
    void getSalt_returnsDefensiveCopy() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        byte[] salt1 = session.getSalt();
        byte[] salt2 = session.getSalt();
        assertNotNull(salt1);
        assertArrayEquals(salt1, salt2);
        salt1[0] ^= 0xFF;
        assertNotEquals(salt1[0], session.getSalt()[0], "getSalt should return defensive copy");
        session.destroy();
    }

    @Test
    void getKekIv_returnsDefensiveCopy() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        byte[] iv1 = session.getKekIv();
        byte[] iv2 = session.getKekIv();
        assertNotNull(iv1);
        assertArrayEquals(iv1, iv2);
        iv1[0] ^= 0xFF;
        assertNotEquals(iv1[0], session.getKekIv()[0], "getKekIv should return defensive copy");
        session.destroy();
    }

    @Test
    void getEncryptedDek_returnsDefensiveCopy() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        byte[] dek1 = session.getEncryptedDek();
        byte[] dek2 = session.getEncryptedDek();
        assertNotNull(dek1);
        assertArrayEquals(dek1, dek2);
        dek1[0] ^= 0xFF;
        assertNotEquals(dek1[0], session.getEncryptedDek()[0], "getEncryptedDek should return defensive copy");
        session.destroy();
    }

    @Test
    void getKdfIterations_returnsConfiguredValue() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        assertEquals(KeyDerivation.getDefaultIterations(), session.getKdfIterations());
        session.destroy();
    }

    @Test
    void updateEnvelope_replacesMetadata() throws VaultEncryptionException {
        VaultSession session = createTestSession();
        byte[] oldSalt = session.getSalt();
        byte[] newSalt = new byte[]{1, 2, 3};
        byte[] newIv = new byte[]{4, 5, 6};
        byte[] newDek = new byte[]{7, 8, 9};
        session.updateEnvelope(newSalt, newIv, newDek, 100_000);
        assertArrayEquals(newSalt, session.getSalt());
        assertArrayEquals(newIv, session.getKekIv());
        assertArrayEquals(newDek, session.getEncryptedDek());
        assertEquals(100_000, session.getKdfIterations());
        assertFalse(java.util.Arrays.equals(oldSalt, session.getSalt()));
        session.destroy();
    }
}
