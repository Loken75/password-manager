package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single password entry stored in the vault.
 * Password is stored as char[] to allow explicit memory wiping.
 */
public class PasswordEntry extends VaultItem {
    private String username;
    private String email;
    private char[] password;
    private String url;
    private String category;
    private List<String> tags;

    /** Default constructor for Gson deserialization. */
    public PasswordEntry() {
        super();
        this.tags = new ArrayList<>();
    }

    public PasswordEntry(String title, String username, char[] password, String url,
                         String notes, String category, List<String> tags) {
        this(title, username, null, password, url, notes, category, tags);
    }

    public PasswordEntry(String title, String username, String email,
                         char[] password, String url, String notes, String category,
                         List<String> tags) {
        super(title, notes);
        this.username = username;
        this.email = email;
        this.password = password != null ? password.clone() : null;
        this.url = url;
        this.category = category;
        this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
    }

    @Override
    public void wipe() {
        SecureWiper.wipe(password);
        password = null;
        title = null;
        username = null;
        email = null;
        url = null;
        notes = null;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    /**
     * Returns a defensive copy of the password. Caller is responsible for
     * wiping the returned array via {@link SecureWiper#wipe(char[])}.
     */
    public char[] getPassword() { return password != null ? password.clone() : null; }
    public void setPassword(char[] password) {
        SecureWiper.wipe(this.password);
        this.password = password != null ? password.clone() : null;
    }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags != null ? Collections.unmodifiableList(tags) : null; }
    public void setTags(List<String> tags) { this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>(); }
}
