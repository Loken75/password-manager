package com.passwordmanager.config;

import java.io.*;
import java.util.Properties;

/**
 * Loads and saves application configuration from config.properties.
 */
public class ConfigManager {
    private final String configPath;

    public ConfigManager() {
        String home = System.getProperty("user.home");
        this.configPath = home + File.separator + ".password-manager" + File.separator + "config.properties";
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
            return config;
        }

        config.setLanguage(props.getProperty("app.language", "fr"));
        config.setStorageMode(props.getProperty("storage.mode", "local"));
        config.setSftpHost(props.getProperty("sftp.host", ""));
        config.setSftpPort(Integer.parseInt(props.getProperty("sftp.port", "22")));
        config.setSftpUser(props.getProperty("sftp.user", ""));
        config.setSftpKeyPath(props.getProperty("sftp.key_path", ""));
        config.setSftpRemotePath(props.getProperty("sftp.remote_path", "/vault/data"));
        config.setLocalVaultDirectory(props.getProperty("local.vault_directory", config.getLocalVaultDirectory()));
        config.setPbkdf2Iterations(Integer.parseInt(props.getProperty("security.pbkdf2_iterations", "100000")));
        config.setAutoLockMinutes(Integer.parseInt(props.getProperty("security.auto_lock_minutes", "15")));
        config.setClipboardClearSeconds(Integer.parseInt(props.getProperty("security.clipboard_clear_seconds", "30")));
        config.setTheme(props.getProperty("app.theme", "light"));

        return config;
    }

    /**
     * Saves configuration to file.
     */
    public void saveConfig(AppConfig config) {
        Properties props = new Properties();
        props.setProperty("app.language", config.getLanguage());
        props.setProperty("app.theme", config.getTheme());
        props.setProperty("storage.mode", config.getStorageMode());
        props.setProperty("sftp.host", config.getSftpHost());
        props.setProperty("sftp.port", String.valueOf(config.getSftpPort()));
        props.setProperty("sftp.user", config.getSftpUser());
        props.setProperty("sftp.key_path", config.getSftpKeyPath());
        props.setProperty("sftp.remote_path", config.getSftpRemotePath());
        props.setProperty("local.vault_directory", config.getLocalVaultDirectory());
        props.setProperty("security.pbkdf2_iterations", String.valueOf(config.getPbkdf2Iterations()));
        props.setProperty("security.auto_lock_minutes", String.valueOf(config.getAutoLockMinutes()));
        props.setProperty("security.clipboard_clear_seconds", String.valueOf(config.getClipboardClearSeconds()));

        File file = new File(configPath);
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "Password Manager Configuration");
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }
}
