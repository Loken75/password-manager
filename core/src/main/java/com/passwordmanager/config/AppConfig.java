package com.passwordmanager.config;

import java.io.File;

/**
 * Application configuration holder with validated setters.
 */
public class AppConfig {
    private String language = "fr";
    private StorageMode storageMode = StorageMode.LOCAL;
    private String sftpHost = "";
    private int sftpPort = 22;
    private String sftpUser = "";
    private String sftpKeyPath = "";
    private String sftpVaultKeyId = "";
    private String sftpRemotePath = "/vault/data";
    private String localVaultDirectory;
    private int autoLockMinutes = 15;
    private int clipboardClearSeconds = 30;
    private ThemeMode theme = ThemeMode.LIGHT;

    public AppConfig() {
        String appHome = System.getProperty("app.home",
            System.getProperty("user.home") + File.separator + ".password-manager");
        this.localVaultDirectory = appHome + File.separator + "data" + File.separator + "vaults";
    }

    public String getLanguage() { return language; }
    public void setLanguage(String language) {
        this.language = language != null ? language.trim() : "fr";
    }

    public StorageMode getStorageMode() { return storageMode; }
    public void setStorageMode(StorageMode storageMode) {
        this.storageMode = storageMode != null ? storageMode : StorageMode.LOCAL;
    }

    public String getSftpHost() { return sftpHost; }
    public void setSftpHost(String sftpHost) {
        this.sftpHost = sftpHost != null ? sftpHost.trim() : "";
    }

    public int getSftpPort() { return sftpPort; }
    public void setSftpPort(int sftpPort) {
        this.sftpPort = Math.max(1, Math.min(sftpPort, 65535));
    }

    public String getSftpUser() { return sftpUser; }
    public void setSftpUser(String sftpUser) {
        this.sftpUser = sftpUser != null ? sftpUser.trim() : "";
    }

    public String getSftpKeyPath() { return sftpKeyPath; }
    public void setSftpKeyPath(String sftpKeyPath) {
        this.sftpKeyPath = sftpKeyPath != null ? sftpKeyPath.trim() : "";
    }

    public String getSftpVaultKeyId() { return sftpVaultKeyId; }
    public void setSftpVaultKeyId(String sftpVaultKeyId) {
        this.sftpVaultKeyId = sftpVaultKeyId != null ? sftpVaultKeyId.trim() : "";
    }

    /** Returns true if SFTP should use a vault-stored SSH key instead of a file. */
    public boolean isUsingVaultKey() {
        return sftpVaultKeyId != null && !sftpVaultKeyId.isEmpty();
    }

    public String getSftpRemotePath() { return sftpRemotePath; }
    public void setSftpRemotePath(String sftpRemotePath) {
        this.sftpRemotePath = sftpRemotePath != null ? sftpRemotePath.trim() : "/vault/data";
    }

    public String getLocalVaultDirectory() { return localVaultDirectory; }
    public void setLocalVaultDirectory(String localVaultDirectory) {
        if (localVaultDirectory != null && !localVaultDirectory.trim().isEmpty()) {
            this.localVaultDirectory = localVaultDirectory.trim();
        }
    }

    public int getAutoLockMinutes() { return autoLockMinutes; }
    public void setAutoLockMinutes(int autoLockMinutes) {
        this.autoLockMinutes = Math.max(1, Math.min(autoLockMinutes, 60));
    }

    public int getClipboardClearSeconds() { return clipboardClearSeconds; }
    public void setClipboardClearSeconds(int clipboardClearSeconds) {
        this.clipboardClearSeconds = Math.max(5, Math.min(clipboardClearSeconds, 120));
    }

    public ThemeMode getTheme() { return theme; }
    public void setTheme(ThemeMode theme) {
        this.theme = theme != null ? theme : ThemeMode.LIGHT;
    }
}
