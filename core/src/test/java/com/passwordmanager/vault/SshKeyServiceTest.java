package com.passwordmanager.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SshKeyServiceTest {

    private Vault vault;
    private SshKeyService service;

    @BeforeEach
    void setUp() {
        vault = new Vault("user");
        service = new SshKeyService(vault);
    }

    @Test
    void addAndRetrieve() {
        SshKeyEntry entry = new SshKeyEntry("mykey", null, "pub", "ED25519", "fp:aa:bb");
        service.addEntry(entry);
        assertEquals(1, service.getActiveList().size());
        assertEquals("mykey", service.getActiveList().get(0).getTitle());
    }

    @Test
    void deleteEntry() {
        SshKeyEntry entry = new SshKeyEntry("mykey", null, "pub", "RSA", "fp");
        service.addEntry(entry);
        assertTrue(service.deleteEntry(entry.getId()));
        assertEquals(0, service.getActiveList().size());
    }

    @Test
    void searchByTitle() {
        service.addEntry(new SshKeyEntry("production-key", null, "pub1", "ED25519", "fp1"));
        service.addEntry(new SshKeyEntry("staging-key", null, "pub2", "RSA", "fp2"));
        List<SshKeyEntry> results = service.search("prod");
        assertEquals(1, results.size());
        assertEquals("production-key", results.get(0).getTitle());
    }

    @Test
    void searchByKeyType() {
        service.addEntry(new SshKeyEntry("key1", null, "pub1", "ED25519", "fp1"));
        service.addEntry(new SshKeyEntry("key2", null, "pub2", "RSA", "fp2"));
        List<SshKeyEntry> results = service.search("rsa");
        assertEquals(1, results.size());
        assertEquals("key2", results.get(0).getTitle());
    }

    @Test
    void searchByFingerprint() {
        service.addEntry(new SshKeyEntry("key1", null, "pub1", "ED25519", "SHA256:abc123"));
        List<SshKeyEntry> results = service.search("abc123");
        assertEquals(1, results.size());
    }

    @Test
    void sortedByTitle() {
        service.addEntry(new SshKeyEntry("beta", null, "pub", "RSA", "fp"));
        service.addEntry(new SshKeyEntry("alpha", null, "pub", "ED25519", "fp"));
        List<SshKeyEntry> sorted = service.sorted(service.getActiveList(), SortField.TITLE);
        assertEquals("alpha", sorted.get(0).getTitle());
        assertEquals("beta", sorted.get(1).getTitle());
    }

    @Test
    void sortedByDate() {
        SshKeyEntry e1 = new SshKeyEntry("old", null, "pub", "RSA", "fp");
        SshKeyEntry e2 = new SshKeyEntry("new", null, "pub", "ED25519", "fp");
        service.addEntry(e1);
        service.addEntry(e2);
        // Set timestamps after addEntry (which overrides updatedAt)
        e1.setUpdatedAt("2024-01-01T00:00:00Z");
        e2.setUpdatedAt("2025-01-01T00:00:00Z");
        List<SshKeyEntry> sorted = service.sorted(service.getActiveList(), SortField.DATE);
        assertEquals("new", sorted.get(0).getTitle());
    }

    @Test
    void toggleFavorite() {
        SshKeyEntry entry = new SshKeyEntry("key", null, "pub", "ED25519", "fp");
        service.addEntry(entry);
        assertFalse(service.getActiveList().get(0).isFavorite());
        service.toggleFavorite(entry.getId());
        assertTrue(service.getActiveList().get(0).isFavorite());
    }

    @Test
    void bulkDelete() {
        SshKeyEntry e1 = new SshKeyEntry("k1", null, "pub", "RSA", "fp1");
        SshKeyEntry e2 = new SshKeyEntry("k2", null, "pub", "ED25519", "fp2");
        service.addEntry(e1);
        service.addEntry(e2);
        assertEquals(2, service.bulkDelete(List.of(e1.getId(), e2.getId())));
        assertEquals(0, service.getActiveList().size());
    }

    @Test
    void favoritesFirst() {
        SshKeyEntry e1 = new SshKeyEntry("alpha", null, "pub", "RSA", "fp");
        SshKeyEntry e2 = new SshKeyEntry("beta", null, "pub", "ED25519", "fp");
        e2.setFavorite(true);
        service.addEntry(e1);
        service.addEntry(e2);
        List<SshKeyEntry> sorted = service.sorted(service.getActiveList(), SortField.TITLE);
        assertEquals("beta", sorted.get(0).getTitle());
    }
}
