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
