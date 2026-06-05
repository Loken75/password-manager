package com.passwordmanager.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Application configuration holder with validated setters.
 */
public class AppConfig {
    /** Maximum number of remembered working folders. */
    private static final int MAX_RECENT_WORKSPACES = 8;
    private String language = "fr";
    private StorageMode storageMode = StorageMode.LOCAL;
    private String sftpHost = "";
    private int sftpPort = 22;
    private String sftpUser = "";
    private String sftpKeyPath = "";
    private String sftpVaultKeyId = "";
    private String sftpRemotePath = "/vault/data";
    private String localVaultDirectory;
    private final List<String> recentWorkspaces = new ArrayList<>();
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

    /** Returns the most-recently-used working folders, most recent first (defensive copy). */
    public List<String> getRecentWorkspaces() { return new ArrayList<>(recentWorkspaces); }

    /** Replaces the recent-workspaces list (trimmed, de-duplicated, capped). */
    public void setRecentWorkspaces(List<String> workspaces) {
        recentWorkspaces.clear();
        if (workspaces == null) return;
        for (String w : workspaces) {
            if (w == null) continue;
            String t = w.trim();
            if (!t.isEmpty() && !recentWorkspaces.contains(t)) {
                recentWorkspaces.add(t);
                if (recentWorkspaces.size() >= MAX_RECENT_WORKSPACES) break;
            }
        }
    }

    /** Records a working folder as most-recently-used (moves it to the front, capped). */
    public void addRecentWorkspace(String workspace) {
        if (workspace == null || workspace.trim().isEmpty()) return;
        String t = workspace.trim();
        recentWorkspaces.remove(t);
        recentWorkspaces.add(0, t);
        while (recentWorkspaces.size() > MAX_RECENT_WORKSPACES) {
            recentWorkspaces.remove(recentWorkspaces.size() - 1);
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
