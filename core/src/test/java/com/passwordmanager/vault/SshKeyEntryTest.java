package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SshKeyEntryTest {

    @Test
    void constructor_clonesPrivateKey() {
        char[] original = "-----BEGIN OPENSSH PRIVATE KEY-----".toCharArray();
        SshKeyEntry entry = new SshKeyEntry("test", original, "pub", "ED25519", "fp");
        original[0] = 'X';
        assertNotEquals('X', entry.getPrivateKey()[0], "Constructor should defensively copy");
    }

    @Test
    void getPrivateKey_returnsDefensiveCopy() {
        char[] key = "private-key-data".toCharArray();
        SshKeyEntry entry = new SshKeyEntry("test", key, "pub", "RSA", "fp");
        char[] copy1 = entry.getPrivateKey();
        char[] copy2 = entry.getPrivateKey();
        assertArrayEquals(copy1, copy2);
        copy1[0] = 'X';
        assertNotEquals('X', entry.getPrivateKey()[0], "getPrivateKey should return a copy");
    }

    @Test
    void getPrivateKey_nullWhenNotSet() {
        SshKeyEntry entry = new SshKeyEntry("test", null, "pub", "ED25519", "fp");
        assertNull(entry.getPrivateKey());
    }

    @Test
    void setPrivateKey_wipesOldValue() {
        char[] key1 = "first-key".toCharArray();
        SshKeyEntry entry = new SshKeyEntry("test", key1, "pub", "RSA", "fp");
        char[] key2 = "second-key".toCharArray();
        entry.setPrivateKey(key2);
        assertArrayEquals("second-key".toCharArray(), entry.getPrivateKey());
    }

    @Test
    void wipe_clearsAllFields() {
        SshKeyEntry entry = new SshKeyEntry("test", "key".toCharArray(), "pub", "RSA", "fp:aa:bb");
        entry.wipe();
        assertNull(entry.getPrivateKey());
        assertNull(entry.getPublicKey());
        assertNull(entry.getFingerprint());
    }

    @Test
    void fields_gettersSetters() {
        SshKeyEntry entry = new SshKeyEntry();
        entry.setTitle("My SSH Key");
        entry.setKeyType("ED25519");
        entry.setPublicKey("ssh-ed25519 AAAA...");
        entry.setFingerprint("SHA256:abc123");

        assertEquals("My SSH Key", entry.getTitle());
        assertEquals("ED25519", entry.getKeyType());
        assertEquals("ssh-ed25519 AAAA...", entry.getPublicKey());
        assertEquals("SHA256:abc123", entry.getFingerprint());
    }

    @Test
    void inheritsVaultItemFields() {
        SshKeyEntry entry = new SshKeyEntry("test", null, null, null, null);
        assertNotNull(entry.getId());
        assertNotNull(entry.getCreatedAt());
        assertNotNull(entry.getUpdatedAt());
        assertFalse(entry.isFavorite());
        assertFalse(entry.isDeleted());
    }

    @Test
    void markDeleted_setsDeletedFlag() {
        SshKeyEntry entry = new SshKeyEntry("test", null, null, "RSA", null);
        entry.markDeleted();
        assertTrue(entry.isDeleted());
        assertNotNull(entry.getDeletedAt());
    }
}
