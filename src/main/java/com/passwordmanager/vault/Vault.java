package com.passwordmanager.vault;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Represents the complete password vault for a user.
 */
public class Vault {
    private String version;
    private String user;
    private String createdAt;
    private String updatedAt;
    private List<VaultEntry> entries;
    private List<String> categories;
    private Map<String, Object> settings;

    public Vault(String user) {
        this.version = "1.0";
        this.user = user;
        String now = getCurrentTimestamp();
        this.createdAt = now;
        this.updatedAt = now;
        this.entries = new ArrayList<VaultEntry>();
        this.categories = new ArrayList<String>();
        this.categories.add("Email");
        this.categories.add("Bancaire");
        this.categories.add("R\u00e9seaux sociaux");
        this.categories.add("Travail");
        this.categories.add("Autre");
        this.settings = new HashMap<String, Object>();
        this.settings.put("auto_lock_minutes", 15);
        this.settings.put("clipboard_clear_seconds", 30);
        this.settings.put("password_expiry_days", 180);
    }

    private static String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public void addEntry(VaultEntry entry) {
        this.entries.add(entry);
        this.updatedAt = getCurrentTimestamp();
    }

    public boolean removeEntry(String id) {
        Iterator<VaultEntry> it = entries.iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(id)) {
                it.remove();
                this.updatedAt = getCurrentTimestamp();
                return true;
            }
        }
        return false;
    }

    public VaultEntry findEntryById(String id) {
        for (VaultEntry entry : entries) {
            if (entry.getId().equals(id)) return entry;
        }
        return null;
    }

    public List<VaultEntry> getEntriesByCategory(String category) {
        List<VaultEntry> result = new ArrayList<VaultEntry>();
        for (VaultEntry entry : entries) {
            if (entry.getCategory() != null && entry.getCategory().equals(category)) {
                result.add(entry);
            }
        }
        return result;
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public List<VaultEntry> getEntries() { return entries; }
    public void setEntries(List<VaultEntry> entries) { this.entries = entries; }
    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }
    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) { this.settings = settings; }
}
