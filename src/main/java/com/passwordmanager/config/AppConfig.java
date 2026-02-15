package com.passwordmanager.config;

import java.io.File;

/**
 * Application configuration holder.
 */
public class AppConfig {
    private String language = "fr";
    private StorageMode storageMode = StorageMode.LOCAL;
    private String sftpHost = "";
    private int sftpPort = 22;
    private String sftpUser = "";
    private String sftpKeyPath = "";
    private String sftpRemotePath = "/vault/data";
    private String localVaultDirectory;
    private int autoLockMinutes = 15;
    private int clipboardClearSeconds = 30;
    private ThemeMode theme = ThemeMode.LIGHT;

    public AppConfig() {
        String home = System.getProperty("user.home");
        this.localVaultDirectory = home + File.separator + ".password-manager" + File.separator + "vaults";
    }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public StorageMode getStorageMode() { return storageMode; }
    public void setStorageMode(StorageMode storageMode) { this.storageMode = storageMode; }
    public String getSftpHost() { return sftpHost; }
    public void setSftpHost(String sftpHost) { this.sftpHost = sftpHost; }
    public int getSftpPort() { return sftpPort; }
    public void setSftpPort(int sftpPort) { this.sftpPort = sftpPort; }
    public String getSftpUser() { return sftpUser; }
    public void setSftpUser(String sftpUser) { this.sftpUser = sftpUser; }
    public String getSftpKeyPath() { return sftpKeyPath; }
    public void setSftpKeyPath(String sftpKeyPath) { this.sftpKeyPath = sftpKeyPath; }
    public String getSftpRemotePath() { return sftpRemotePath; }
    public void setSftpRemotePath(String sftpRemotePath) { this.sftpRemotePath = sftpRemotePath; }
    public String getLocalVaultDirectory() { return localVaultDirectory; }
    public void setLocalVaultDirectory(String localVaultDirectory) { this.localVaultDirectory = localVaultDirectory; }
    public int getAutoLockMinutes() { return autoLockMinutes; }
    public void setAutoLockMinutes(int autoLockMinutes) { this.autoLockMinutes = autoLockMinutes; }
    public int getClipboardClearSeconds() { return clipboardClearSeconds; }
    public void setClipboardClearSeconds(int clipboardClearSeconds) { this.clipboardClearSeconds = clipboardClearSeconds; }
    public ThemeMode getTheme() { return theme; }
    public void setTheme(ThemeMode theme) { this.theme = theme; }
}
