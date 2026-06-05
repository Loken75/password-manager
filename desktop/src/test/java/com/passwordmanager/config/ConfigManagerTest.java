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
    void recentWorkspacesRoundTrip() {
        String configPath = tempDir.resolve("config.properties").toString();
        ConfigManager manager = new ConfigManager(configPath);

        AppConfig config = new AppConfig();
        config.setLocalVaultDirectory("/home/user/vaultsA");
        config.addRecentWorkspace("/home/user/vaultsA");
        config.addRecentWorkspace("/home/user/vaultsB");
        manager.saveConfig(config);

        AppConfig loaded = manager.loadConfig();
        // addRecentWorkspace puts most-recent first.
        assertEquals(java.util.List.of("/home/user/vaultsB", "/home/user/vaultsA"),
            loaded.getRecentWorkspaces());
    }

    @Test
    void addRecentWorkspaceDeduplicatesOrdersAndCaps() {
        AppConfig config = new AppConfig();
        config.addRecentWorkspace("/a");
        config.addRecentWorkspace("/b");
        config.addRecentWorkspace("/a"); // re-adding moves it to the front
        assertEquals(java.util.List.of("/a", "/b"), config.getRecentWorkspaces());

        for (int i = 0; i < 12; i++) {
            config.addRecentWorkspace("/dir" + i);
        }
        assertEquals(8, config.getRecentWorkspaces().size(), "list is capped at 8");
        assertEquals("/dir11", config.getRecentWorkspaces().get(0), "most recent is first");
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
