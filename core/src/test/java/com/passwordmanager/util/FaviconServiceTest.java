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
}
