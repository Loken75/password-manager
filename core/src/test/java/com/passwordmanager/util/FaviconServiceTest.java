package com.passwordmanager.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FaviconServiceTest {

    @Test
    void extractDomainFromHttpsUrl() {
        assertEquals("www.google.com", FaviconService.extractDomain("https://www.google.com/search?q=test"));
    }

    @Test
    void extractDomainFromHttpUrl() {
        assertEquals("example.com", FaviconService.extractDomain("http://example.com/path"));
    }

    @Test
    void extractDomainFromBareUrl() {
        assertEquals("example.com", FaviconService.extractDomain("example.com"));
    }

    @Test
    void extractDomainStripsPort() {
        assertEquals("localhost", FaviconService.extractDomain("http://localhost:8080/api"));
    }

    @Test
    void getCachedFaviconReturnsNullWhenEmpty(@TempDir Path tempDir) {
        FaviconService service = new FaviconService(tempDir.toString());
        assertNull(service.getCachedFavicon("https://nonexistent.example.com"));
    }

    /**
     * Locks the cache-only contract the UI now relies on for privacy: getCachedFavicon
     * serves bytes straight from the on-disk cache (no network). The cache filename
     * mirrors FaviconService's scheme: hex(SHA-256(domain)).
     */
    @Test
    void getCachedFaviconReturnsBytesFromDiskWithoutNetwork(@TempDir Path tempDir) throws Exception {
        FaviconService service = new FaviconService(tempDir.toString());
        String domain = "cached.example.com";
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4};

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(domain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder filename = new StringBuilder(hash.length * 2);
        for (byte b : hash) filename.append(String.format("%02x", b & 0xFF));
        java.nio.file.Files.write(tempDir.resolve(filename.toString()), png);

        assertArrayEquals(png, service.getCachedFavicon("https://" + domain + "/login"));
    }

    // --- R7: validate fetched bytes are an image (prevents caching HTML error pages) ---

    @Test
    void isLikelyImage_acceptsKnownFormats() {
        assertTrue(FaviconService.isLikelyImage(new byte[]{0x00, 0x00, 0x01, 0x00, 0x10})); // ICO
        assertTrue(FaviconService.isLikelyImage(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})); // PNG
        assertTrue(FaviconService.isLikelyImage(new byte[]{'G', 'I', 'F', '8', '9'}));        // GIF
        assertTrue(FaviconService.isLikelyImage(new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x00})); // JPEG
        assertTrue(FaviconService.isLikelyImage(new byte[]{'R', 'I', 'F', 'F'}));             // WEBP/RIFF
    }

    @Test
    void isLikelyImage_rejectsHtmlAndJunk() {
        assertFalse(FaviconService.isLikelyImage("<!DOCTYPE html>".getBytes()));
        assertFalse(FaviconService.isLikelyImage("{\"error\":1}".getBytes()));
        assertFalse(FaviconService.isLikelyImage(new byte[]{0x00, 0x01})); // too short
        assertFalse(FaviconService.isLikelyImage(new byte[0]));
        assertFalse(FaviconService.isLikelyImage(null));
    }
}
