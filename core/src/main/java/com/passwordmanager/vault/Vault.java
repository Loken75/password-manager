package com.passwordmanager.vault;

import com.passwordmanager.util.DateUtils;

import java.util.*;

/**
 * Represents the complete password vault for a user.
 * Pure data model -- use VaultService for operations.
 */
public class Vault {
    /** English fallback categories (used when no locale-specific list is provided). */
    public static final List<String> DEFAULT_CATEGORIES = List.of(
        "Email", "Banking", "Social networks", "Work", "Other"
    );

    private String version;
    private String user;
    private String createdAt;
    private String updatedAt;
    private List<PasswordEntry> entries;
    private List<AppEntry> appEntries;
    private List<SshKeyEntry> sshKeyEntries;
    private List<String> categories;
    private Map<String, Object> settings;

    /** No-arg constructor required by Gson deserialization (jlink runtimes lack sun.misc.Unsafe). */
    @SuppressWarnings("unused")
    private Vault() {
        this.entries = new ArrayList<>();
        this.appEntries = new ArrayList<>();
        this.sshKeyEntries = new ArrayList<>();
        this.categories = new ArrayList<>();
        this.settings = new HashMap<>();
    }

    public Vault(String user) {
        this(user, DEFAULT_CATEGORIES);
    }

    public Vault(String user, List<String> defaultCategories) {
        this.version = "2.0";
        this.user = user;
        String now = DateUtils.getCurrentTimestamp();
        this.createdAt = now;
        this.updatedAt = now;
        this.entries = new ArrayList<>();
        this.appEntries = new ArrayList<>();
        this.sshKeyEntries = new ArrayList<>();
        this.categories = new ArrayList<>(defaultCategories);
        this.settings = new HashMap<>();
        this.settings.put("auto_lock_minutes", 15);
        this.settings.put("clipboard_clear_seconds", 30);
        this.settings.put("password_expiry_days", 180);
    }

    /**
     * Securely wipes all sensitive data from memory: entries, categories, settings.
     */
    public void wipe() {
        if (entries != null) {
            for (PasswordEntry entry : entries) {
                entry.wipe();
            }
            entries.clear();
        }
        if (appEntries != null) {
            for (AppEntry entry : appEntries) {
                entry.wipe();
            }
            appEntries.clear();
        }
        if (sshKeyEntries != null) {
            for (SshKeyEntry entry : sshKeyEntries) {
                entry.wipe();
            }
            sshKeyEntries.clear();
        }
        if (categories != null) {
            categories.clear();
        }
        if (settings != null) {
            settings.clear();
        }
        user = null;
        createdAt = null;
        updatedAt = null;
    }

    /**
     * Ensures all collection fields are non-null after Gson deserialization.
     * Call this after deserializing a Vault from JSON to guard against missing fields.
     */
    public void ensureInitialized() {
        if (entries == null) entries = new ArrayList<>();
        if (appEntries == null) appEntries = new ArrayList<>();
        if (sshKeyEntries == null) sshKeyEntries = new ArrayList<>();
        if (categories == null) categories = new ArrayList<>();
        if (settings == null) settings = new HashMap<>();
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    /**
     * Returns a read-only view of the entries list.
     * Use {@link #addEntry(PasswordEntry)} and {@link #removeEntry(PasswordEntry)} for mutations.
     */
    public List<PasswordEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
    /**
     * Direct mutable access to the backing list. Callers MUST hold this Vault's
     * monitor ({@code synchronized (vault)}) while iterating or modifying — the same
     * monitor used by {@link #addEntry}/{@link #removeEntry}, by the {@code *Service}
     * classes, and by save-time serialization. Used by deserialization and bulk operations.
     */
    public List<PasswordEntry> getEntriesMutable() { return entries; }
    /** Adds an entry, replacing any existing entry with the same id (equals is id-based). */
    public synchronized void addEntry(PasswordEntry entry) { entries.remove(entry); entries.add(entry); }
    public synchronized boolean removeEntry(PasswordEntry entry) { return entries.remove(entry); }
    public synchronized void setEntries(List<PasswordEntry> entries) { this.entries = entries; }

    /** Returns a read-only view of the app entries list. Null-safe for vaults loaded without this field. */
    public List<AppEntry> getAppEntries() {
        return Collections.unmodifiableList(appEntries != null ? appEntries : Collections.emptyList());
    }
    public List<AppEntry> getAppEntriesMutable() {
        if (appEntries == null) appEntries = new ArrayList<>();
        return appEntries;
    }
    /** Adds an app entry, replacing any existing entry with the same id (equals is id-based). */
    public synchronized void addAppEntry(AppEntry entry) {
        if (appEntries == null) appEntries = new ArrayList<>();
        appEntries.remove(entry);
        appEntries.add(entry);
    }
    public synchronized boolean removeAppEntry(AppEntry entry) {
        return appEntries != null && appEntries.remove(entry);
    }
    public synchronized void setAppEntries(List<AppEntry> appEntries) { this.appEntries = appEntries; }

    /** Returns a read-only view of the SSH key entries list. Null-safe for vaults loaded without this field. */
    public List<SshKeyEntry> getSshKeyEntries() {
        return Collections.unmodifiableList(sshKeyEntries != null ? sshKeyEntries : Collections.emptyList());
    }
    public List<SshKeyEntry> getSshKeyEntriesMutable() {
        if (sshKeyEntries == null) sshKeyEntries = new ArrayList<>();
        return sshKeyEntries;
    }
    /** Adds an SSH key entry, replacing any existing entry with the same id (equals is id-based). */
    public synchronized void addSshKeyEntry(SshKeyEntry entry) {
        if (sshKeyEntries == null) sshKeyEntries = new ArrayList<>();
        sshKeyEntries.remove(entry);
        sshKeyEntries.add(entry);
    }
    public synchronized boolean removeSshKeyEntry(SshKeyEntry entry) {
        return sshKeyEntries != null && sshKeyEntries.remove(entry);
    }
    public synchronized void setSshKeyEntries(List<SshKeyEntry> sshKeyEntries) { this.sshKeyEntries = sshKeyEntries; }

    public List<String> getCategories() {
        return categories != null ? Collections.unmodifiableList(categories) : Collections.emptyList();
    }
    public List<String> getCategoriesMutable() {
        if (categories == null) categories = new ArrayList<>();
        return categories;
    }
    public void setCategories(List<String> categories) { this.categories = categories; }
    public Map<String, Object> getSettings() {
        return settings != null ? Collections.unmodifiableMap(settings) : Collections.emptyMap();
    }
    public Map<String, Object> getSettingsMutable() {
        if (settings == null) settings = new HashMap<>();
        return settings;
    }
    public void setSettings(Map<String, Object> settings) { this.settings = settings; }
}
