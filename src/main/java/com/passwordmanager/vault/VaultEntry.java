package com.passwordmanager.vault;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Represents a single password entry stored in the vault.
 */
public class VaultEntry {
    private String id;
    private String title;
    private String username;
    private String password;
    private String url;
    private String notes;
    private String category;
    private List<String> tags;
    private String createdAt;
    private String updatedAt;

    /** Default constructor for Gson deserialization. */
    public VaultEntry() {
        this.id = UUID.randomUUID().toString();
        String now = getCurrentTimestamp();
        this.createdAt = now;
        this.updatedAt = now;
        this.tags = new ArrayList<String>();
    }

    public VaultEntry(String title, String username, String password, String url,
                      String notes, String category, List<String> tags) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.username = username;
        this.password = password;
        this.url = url;
        this.notes = notes;
        this.category = category;
        this.tags = (tags != null) ? new ArrayList<String>(tags) : new ArrayList<String>();
        String now = getCurrentTimestamp();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private static String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "VaultEntry{id='" + id + "', title='" + title + "', username='" + username + "'}";
    }
}
