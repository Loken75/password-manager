package com.passwordmanager.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigManager load/save cycle.
 */
class ConfigManagerTest {

    @TempDir
    Path tempDir;

    private String previousAppHome;

    @BeforeEach
    void setUp() {
        // Redirect app.home so ConfigEncryptor uses the temp directory
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
    void loadDefaultConfig() {
        String configPath = tempDir.resolve("config.properties").toString();
        ConfigManager manager = new ConfigManager(configPath);

        AppConfig config = manager.loadConfig();
        assertNotNull(config);
        assertEquals("fr", config.getLanguage());
        assertEquals(StorageMode.LOCAL, config.getStorageMode());
        assertEquals(ThemeMode.LIGHT, config.getTheme());
        assertEquals(22, config.getSftpPort());
        assertEquals(15, config.getAutoLockMinutes());
        assertEquals(30, config.getClipboardClearSeconds());
    }

    @Test
    void saveAndLoadRoundTrip() {
        String configPath = tempDir.resolve("config.properties").toString();
        ConfigManager manager = new ConfigManager(configPath);

        AppConfig config = new AppConfig();
        config.setLanguage("en");
        config.setTheme(ThemeMode.DARK);
        config.setStorageMode(StorageMode.REMOTE);
        config.setSftpHost("myhost.com");
        config.setSftpPort(2222);
        config.setSftpUser("admin");
        config.setSftpKeyPath("/home/user/.ssh/id_rsa");
        config.setSftpRemotePath("/data/vault");
        config.setAutoLockMinutes(5);
        config.setClipboardClearSeconds(10);

        manager.saveConfig(config);
        assertTrue(new File(configPath).exists());

        AppConfig loaded = manager.loadConfig();
        assertEquals("en", loaded.getLanguage());
        assertEquals(ThemeMode.DARK, loaded.getTheme());
        assertEquals(StorageMode.REMOTE, loaded.getStorageMode());
        assertEquals("myhost.com", loaded.getSftpHost());
        assertEquals(2222, loaded.getSftpPort());
        assertEquals("admin", loaded.getSftpUser());
        assertEquals("/home/user/.ssh/id_rsa", loaded.getSftpKeyPath());
        assertEquals("/data/vault", loaded.getSftpRemotePath());
        assertEquals(5, loaded.getAutoLockMinutes());
        assertEquals(10, loaded.getClipboardClearSeconds());
    }

    @Test
    void loadCreatesDefaultFileIfMissing() {
        String configPath = tempDir.resolve("subdir").resolve("config.properties").toString();
        ConfigManager manager = new ConfigManager(configPath);

        AppConfig config = manager.loadConfig();
        assertNotNull(config);
        assertTrue(new File(configPath).exists());
    }
}
