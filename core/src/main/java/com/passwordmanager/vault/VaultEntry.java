package com.passwordmanager.vault;

import com.passwordmanager.util.DateUtils;
import com.passwordmanager.util.SecureWiper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single password entry stored in the vault.
 * Password is stored as char[] to allow explicit memory wiping.
 */
public class VaultEntry {
    private String id;
    private String title;
    private String username;
    private String email;
    private char[] password;
    private String url;
    private String notes;
    private String category;
    private List<String> tags;
    private boolean favorite;
    private String createdAt;
    private String updatedAt;

    /** Default constructor for Gson deserialization. */
    public VaultEntry() {
        this.id = UUID.randomUUID().toString();
        String now = DateUtils.getCurrentTimestamp();
        this.createdAt = now;
        this.updatedAt = now;
        this.tags = new ArrayList<>();
    }

    public VaultEntry(String title, String username, char[] password, String url,
                      String notes, String category, List<String> tags) {
        this(title, username, null, password, url, notes, category, tags);
    }

    public VaultEntry(String title, String username, String email,
                      char[] password, String url, String notes, String category,
                      List<String> tags) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.username = username;
        this.email = email;
        this.password = password != null ? password.clone() : null;
        this.url = url;
        this.notes = notes;
        this.category = category;
        this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
        String now = DateUtils.getCurrentTimestamp();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Securely wipes all sensitive data from this entry.
     * Call when the vault is locked or the entry is no longer needed.
     */
    public void wipe() {
        SecureWiper.wipe(password);
        password = null;
        title = null;
        username = null;
        email = null;
        url = null;
        notes = null;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    /**
     * Returns a defensive copy of the password. Caller is responsible for
     * wiping the returned array via {@link com.passwordmanager.util.SecureWiper#wipe(char[])}.
     */
    public char[] getPassword() { return password != null ? password.clone() : null; }
    public void setPassword(char[] password) {
        SecureWiper.wipe(this.password);
        this.password = password != null ? password.clone() : null;
    }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags != null ? Collections.unmodifiableList(tags) : null; }
    public void setTags(List<String> tags) { this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>(); }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "VaultEntry{id='" + id + "'}";
    }
}
