package com.passwordmanager.crypto;

import com.passwordmanager.util.SecureWiper;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CryptoService DEK/KEK envelope encryption.
 */
class CryptoServiceTest {
    private final CryptoService service = new CryptoService();

    @Test
    void createSessionAndEncryptDecrypt() throws Exception {
        char[] password = "MyStr0ng!Passw0rd#2026".toCharArray();

        VaultSession session = service.createSession(password);
        assertNotNull(session);
        assertNotNull(session.getDataKey());
        assertNotNull(session.getSalt());
        assertNotNull(session.getKekIv());
        assertNotNull(session.getEncryptedDek());
        assertTrue(session.getKdfIterations() > 0);

        byte[] plaintext = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        EncryptedPayload payload = service.encryptData(plaintext, session.getDataKey());
        assertNotNull(payload.getIv());
        assertNotNull(payload.getCiphertext());

        byte[] decrypted = service.decryptData(payload.getIv(), payload.getCiphertext(), session.getDataKey());
        assertArrayEquals(plaintext, decrypted);

        session.destroy();
    }

    @Test
    void openSessionWithCorrectPassword() throws Exception {
        char[] password = "TestP@ssw0rd!123".toCharArray();

        VaultSession original = service.createSession(password);
        byte[] plaintext = "Secret data".getBytes(StandardCharsets.UTF_8);
        EncryptedPayload payload = service.encryptData(plaintext, original.getDataKey());

        // Re-open session with same password
        VaultSession reopened = service.openSession(
            original.getSalt(), original.getKekIv(),
            original.getEncryptedDek(), original.getKdfIterations(),
            password);

        byte[] decrypted = service.decryptData(payload.getIv(), payload.getCiphertext(), reopened.getDataKey());
        assertArrayEquals(plaintext, decrypted);

        original.destroy();
        reopened.destroy();
    }

    @Test
    void openSessionWithWrongPasswordThrows() throws Exception {
        char[] correct = "CorrectP@ss123!".toCharArray();
        char[] wrong = "Wr0ngP@ssword!1".toCharArray();

        VaultSession session = service.createSession(correct);

        assertThrows(VaultDecryptionException.class, () ->
            service.openSession(session.getSalt(), session.getKekIv(),
                session.getEncryptedDek(), session.getKdfIterations(), wrong));

        session.destroy();
    }

    @Test
    void changePasswordPreservesData() throws Exception {
        char[] oldPassword = "OldP@ssw0rd!123".toCharArray();
        char[] newPassword = "NewP@ssw0rd!456".toCharArray();

        VaultSession session = service.createSession(oldPassword);
        byte[] plaintext = "Data to survive password change".getBytes(StandardCharsets.UTF_8);
        EncryptedPayload payload = service.encryptData(plaintext, session.getDataKey());

        service.changePassword(session, newPassword);

        // Data still decryptable with same session (DEK unchanged)
        byte[] decrypted = service.decryptData(payload.getIv(), payload.getCiphertext(), session.getDataKey());
        assertArrayEquals(plaintext, decrypted);

        // Session reopenable with new password
        VaultSession reopened = service.openSession(
            session.getSalt(), session.getKekIv(),
            session.getEncryptedDek(), session.getKdfIterations(),
            newPassword);
        decrypted = service.decryptData(payload.getIv(), payload.getCiphertext(), reopened.getDataKey());
        assertArrayEquals(plaintext, decrypted);

        // Old password no longer works
        assertThrows(VaultDecryptionException.class, () ->
            service.openSession(session.getSalt(), session.getKekIv(),
                session.getEncryptedDek(), session.getKdfIterations(), oldPassword));

        session.destroy();
        reopened.destroy();
    }

    @Test
    void differentSessionsProduceDifferentCiphertexts() throws Exception {
        char[] password = "S@meP@ssw0rd!12".toCharArray();
        byte[] plaintext = "Same text".getBytes(StandardCharsets.UTF_8);

        VaultSession session = service.createSession(password);
        EncryptedPayload enc1 = service.encryptData(plaintext, session.getDataKey());
        EncryptedPayload enc2 = service.encryptData(plaintext, session.getDataKey());

        // Different IV each time
        assertFalse(java.util.Arrays.equals(enc1.getIv(), enc2.getIv()));

        // Both decrypt to same plaintext
        assertArrayEquals(plaintext, service.decryptData(enc1.getIv(), enc1.getCiphertext(), session.getDataKey()));
        assertArrayEquals(plaintext, service.decryptData(enc2.getIv(), enc2.getCiphertext(), session.getDataKey()));

        session.destroy();
    }

    @Test
    void encryptDecryptEmptyPayload() throws Exception {
        char[] password = "P@ssw0rd!123456".toCharArray();
        VaultSession session = service.createSession(password);

        byte[] empty = new byte[0];
        EncryptedPayload payload = service.encryptData(empty, session.getDataKey());
        byte[] decrypted = service.decryptData(payload.getIv(), payload.getCiphertext(), session.getDataKey());
        assertArrayEquals(empty, decrypted);

        session.destroy();
    }

    @Test
    void encryptDecryptLargePayload() throws Exception {
        char[] password = "LargeP@yload!12".toCharArray();
        VaultSession session = service.createSession(password);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("Entry ").append(i).append(": password_").append(i).append("\n");
        }
        byte[] plaintext = sb.toString().getBytes(StandardCharsets.UTF_8);

        EncryptedPayload payload = service.encryptData(plaintext, session.getDataKey());
        byte[] decrypted = service.decryptData(payload.getIv(), payload.getCiphertext(), session.getDataKey());
        assertArrayEquals(plaintext, decrypted);

        session.destroy();
    }

    @Test
    void sessionDestroyMarksDestroyed() throws Exception {
        char[] password = "Destr0y!Test123".toCharArray();
        VaultSession session = service.createSession(password);
        assertFalse(session.isDestroyed());

        session.destroy();
        assertTrue(session.isDestroyed());

        // Calling destroy again should be safe (idempotent)
        assertDoesNotThrow(session::destroy);
        assertTrue(session.isDestroyed());
    }

    @Test
    void sessionCloseCallsDestroy() throws Exception {
        char[] password = "Cl0se!Test12345".toCharArray();
        VaultSession session = service.createSession(password);
        assertFalse(session.isDestroyed());

        session.close(); // AutoCloseable
        assertTrue(session.isDestroyed());
    }

    @Test
    void decryptWithTamperedCiphertextThrows() throws Exception {
        char[] password = "T@mper!Test1234".toCharArray();
        VaultSession session = service.createSession(password);

        byte[] plaintext = "Sensitive data".getBytes(StandardCharsets.UTF_8);
        EncryptedPayload payload = service.encryptData(plaintext, session.getDataKey());

        // Tamper with ciphertext
        byte[] tampered = payload.getCiphertext().clone();
        tampered[0] ^= 0xFF;

        assertThrows(VaultDecryptionException.class, () ->
            service.decryptData(payload.getIv(), tampered, session.getDataKey()));

        session.destroy();
    }

    @Test
    void decryptWithTamperedIvThrows() throws Exception {
        char[] password = "IvT@mper!123456".toCharArray();
        VaultSession session = service.createSession(password);

        byte[] plaintext = "More data".getBytes(StandardCharsets.UTF_8);
        EncryptedPayload payload = service.encryptData(plaintext, session.getDataKey());

        // Tamper with IV
        byte[] tamperedIv = payload.getIv().clone();
        tamperedIv[0] ^= 0xFF;

        assertThrows(VaultDecryptionException.class, () ->
            service.decryptData(tamperedIv, payload.getCiphertext(), session.getDataKey()));

        session.destroy();
    }

    @Test
    void decryptLegacyWithCorrectKeyWorks() throws Exception {
        char[] password = "LegacyP@ss!1234".toCharArray();
        byte[] salt = KeyDerivation.generateSalt();

        // Simulate legacy encryption: derive key with legacy iterations, encrypt data
        SecretKey legacyKey = KeyDerivation.deriveKey(password, salt, 100_000);
        byte[] plaintext = "Legacy vault data".getBytes(StandardCharsets.UTF_8);

        // Encrypt with the legacy key manually using the same algorithm
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, legacyKey,
            new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Decrypt with the legacy API
        byte[] decrypted = service.decryptLegacy(salt, iv, ciphertext, password);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void decryptLegacyWithWrongPasswordThrows() throws Exception {
        char[] correct = "LegacyC0rrect!1".toCharArray();
        char[] wrong = "LegacyWr0ng!123".toCharArray();
        byte[] salt = KeyDerivation.generateSalt();

        SecretKey legacyKey = KeyDerivation.deriveKey(correct, salt, 100_000);
        byte[] plaintext = "Secret".getBytes(StandardCharsets.UTF_8);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, legacyKey,
            new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        assertThrows(VaultDecryptionException.class, () ->
            service.decryptLegacy(salt, iv, ciphertext, wrong));
    }
}
