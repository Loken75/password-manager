package com.passwordmanager.config;

import com.passwordmanager.util.FileSecurityUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and saves application configuration from config.properties.
 */
public class ConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());
    private final String configPath;

    public ConfigManager() {
        String home = System.getProperty("user.home");
        this.configPath = home + File.separator + ".password-manager" + File.separator + "config.properties";
    }

    public ConfigManager(String configPath) {
        this.configPath = configPath;
    }

    /**
     * Loads configuration from file. Creates default if not exists.
     */
    public AppConfig loadConfig() {
        AppConfig config = new AppConfig();
        File file = new File(configPath);

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            saveConfig(config);
            return config;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load config, using defaults", e);
            return config;
        }

        config.setLanguage(props.getProperty("app.language", "fr"));
        config.setStorageMode(StorageMode.fromValue(props.getProperty("storage.mode", "local")));
        config.setSftpHost(ConfigEncryptor.decrypt(props.getProperty("sftp.host", "")));
        config.setSftpPort(parseIntSafe(props.getProperty("sftp.port"), 22));
        config.setSftpUser(ConfigEncryptor.decrypt(props.getProperty("sftp.user", "")));
        config.setSftpKeyPath(ConfigEncryptor.decrypt(props.getProperty("sftp.key_path", "")));
        config.setSftpRemotePath(ConfigEncryptor.decrypt(props.getProperty("sftp.remote_path", "/vault/data")));
        config.setLocalVaultDirectory(props.getProperty("local.vault_directory", config.getLocalVaultDirectory()));
        config.setAutoLockMinutes(parseIntSafe(props.getProperty("security.auto_lock_minutes"), 15));
        config.setClipboardClearSeconds(parseIntSafe(props.getProperty("security.clipboard_clear_seconds"), 30));
        config.setTheme(ThemeMode.fromValue(props.getProperty("app.theme", "light")));

        return config;
    }

    /**
     * Saves configuration to file with restrictive permissions.
     */
    public void saveConfig(AppConfig config) {
        Properties props = new Properties();
        props.setProperty("app.language", config.getLanguage());
        props.setProperty("app.theme", config.getTheme().getValue());
        props.setProperty("storage.mode", config.getStorageMode().getValue());
        props.setProperty("sftp.host", ConfigEncryptor.encrypt(config.getSftpHost()));
        props.setProperty("sftp.port", String.valueOf(config.getSftpPort()));
        props.setProperty("sftp.user", ConfigEncryptor.encrypt(config.getSftpUser()));
        props.setProperty("sftp.key_path", ConfigEncryptor.encrypt(config.getSftpKeyPath()));
        props.setProperty("sftp.remote_path", ConfigEncryptor.encrypt(config.getSftpRemotePath()));
        props.setProperty("local.vault_directory", config.getLocalVaultDirectory());
        props.setProperty("security.auto_lock_minutes", String.valueOf(config.getAutoLockMinutes()));
        props.setProperty("security.clipboard_clear_seconds", String.valueOf(config.getClipboardClearSeconds()));

        File file = new File(configPath);
        file.getParentFile().mkdirs();
        // Atomic write: write to temp, set permissions, then rename
        File tempFile = new File(configPath + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            props.store(fos, "Password Manager Configuration");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save config", e);
            return;
        }
        FileSecurityUtils.setOwnerOnlyPermissions(tempFile.toPath());
        try {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to rename config file", ex);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to rename config file", e);
        }
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
