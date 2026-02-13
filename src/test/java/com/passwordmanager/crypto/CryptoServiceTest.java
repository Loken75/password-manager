package com.passwordmanager.crypto;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for CryptoService (AES-256-GCM encrypt/decrypt).
 */
public class CryptoServiceTest {
    private final CryptoService service = new CryptoService();

    @Test
    public void testEncryptDecrypt() throws Exception {
        String plaintext = "Hello, World! This is a secret message.";
        char[] password = "MyStr0ng!Passw0rd#2026".toCharArray();

        String encrypted = service.encrypt(plaintext, password);
        assertNotNull(encrypted);
        assertFalse(encrypted.contains(plaintext));

        String decrypted = service.decrypt(encrypted, password);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void testEncryptDecryptUnicode() throws Exception {
        String plaintext = "Mot de passe: \u00e9\u00e0\u00fc\u00f1 \u00e7a marche!";
        char[] password = "TestP@ssw0rd!123".toCharArray();

        String encrypted = service.encrypt(plaintext, password);
        String decrypted = service.decrypt(encrypted, password);
        assertEquals(plaintext, decrypted);
    }

    @Test(expected = Exception.class)
    public void testDecryptWithWrongPassword() throws Exception {
        String plaintext = "Secret data";
        char[] correctPassword = "CorrectP@ss123!".toCharArray();
        char[] wrongPassword = "Wr0ngP@ssword!1".toCharArray();

        String encrypted = service.encrypt(plaintext, correctPassword);
        service.decrypt(encrypted, wrongPassword); // Should throw
    }

    @Test
    public void testReEncrypt() throws Exception {
        String plaintext = "Data to re-encrypt";
        char[] oldPassword = "OldP@ssw0rd!123".toCharArray();
        char[] newPassword = "NewP@ssw0rd!456".toCharArray();

        String encrypted = service.encrypt(plaintext, oldPassword);
        String reEncrypted = service.reEncrypt(encrypted, oldPassword, newPassword);

        String decrypted = service.decrypt(reEncrypted, newPassword);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void testDifferentEncryptionsProduceDifferentOutput() throws Exception {
        String plaintext = "Same text";
        char[] password = "S@meP@ssw0rd!12".toCharArray();

        String enc1 = service.encrypt(plaintext, password);
        String enc2 = service.encrypt(plaintext, password);
        // Different salt and IV each time
        assertNotEquals(enc1, enc2);

        // But both decrypt to same plaintext
        assertEquals(plaintext, service.decrypt(enc1, password));
        assertEquals(plaintext, service.decrypt(enc2, password));
    }

    @Test
    public void testEmptyString() throws Exception {
        char[] password = "P@ssw0rd!123456".toCharArray();
        String encrypted = service.encrypt("", password);
        String decrypted = service.decrypt(encrypted, password);
        assertEquals("", decrypted);
    }

    @Test
    public void testLargePayload() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("Entry ").append(i).append(": password_").append(i).append("\n");
        }
        String plaintext = sb.toString();
        char[] password = "LargeP@yload!12".toCharArray();

        String encrypted = service.encrypt(plaintext, password);
        String decrypted = service.decrypt(encrypted, password);
        assertEquals(plaintext, decrypted);
    }
}
