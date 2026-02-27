package com.passwordmanager.vault;

import com.passwordmanager.util.DateUtils;

import java.util.*;
import java.util.Collections;

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
    private List<CardEntry> cardEntries;
    private List<String> categories;
    private Map<String, Object> settings;

    /** No-arg constructor required by Gson deserialization (jlink runtimes lack sun.misc.Unsafe). */
    @SuppressWarnings("unused")
    private Vault() {
        this.entries = new ArrayList<>();
        this.appEntries = new ArrayList<>();
        this.cardEntries = new ArrayList<>();
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
        this.cardEntries = new ArrayList<>();
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
        if (cardEntries != null) {
            for (CardEntry entry : cardEntries) {
                entry.wipe();
            }
            cardEntries.clear();
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
     * Direct mutable access — callers MUST hold VaultService's synchronized lock
     * when iterating or modifying. Used by Gson deserialization and bulk operations.
     */
    public List<PasswordEntry> getEntriesMutable() { return entries; }
    public synchronized void addEntry(PasswordEntry entry) { entries.add(entry); }
    public synchronized boolean removeEntry(PasswordEntry entry) { return entries.remove(entry); }
    public void setEntries(List<PasswordEntry> entries) { this.entries = entries; }

    /** Returns a read-only view of the app entries list. Null-safe for vaults loaded without this field. */
    public List<AppEntry> getAppEntries() {
        return Collections.unmodifiableList(appEntries != null ? appEntries : Collections.emptyList());
    }
    public List<AppEntry> getAppEntriesMutable() {
        if (appEntries == null) appEntries = new ArrayList<>();
        return appEntries;
    }
    public synchronized void addAppEntry(AppEntry entry) {
        if (appEntries == null) appEntries = new ArrayList<>();
        appEntries.add(entry);
    }
    public synchronized boolean removeAppEntry(AppEntry entry) {
        return appEntries != null && appEntries.remove(entry);
    }
    public void setAppEntries(List<AppEntry> appEntries) { this.appEntries = appEntries; }

    /** Returns a read-only view of the card entries list. Null-safe for vaults loaded without this field. */
    public List<CardEntry> getCardEntries() {
        return Collections.unmodifiableList(cardEntries != null ? cardEntries : Collections.emptyList());
    }
    public List<CardEntry> getCardEntriesMutable() {
        if (cardEntries == null) cardEntries = new ArrayList<>();
        return cardEntries;
    }
    public synchronized void addCardEntry(CardEntry entry) {
        if (cardEntries == null) cardEntries = new ArrayList<>();
        cardEntries.add(entry);
    }
    public synchronized boolean removeCardEntry(CardEntry entry) {
        return cardEntries != null && cardEntries.remove(entry);
    }
    public void setCardEntries(List<CardEntry> cardEntries) { this.cardEntries = cardEntries; }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }
    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) { this.settings = settings; }
}
