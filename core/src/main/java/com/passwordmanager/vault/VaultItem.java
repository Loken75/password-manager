package com.passwordmanager.vault;

import com.passwordmanager.util.DateUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class for all vault item types (passwords, apps, cards).
 * Holds common fields: id, title, notes, favorite, timestamps.
 */
public abstract class VaultItem {
    protected String id;
    protected String title;
    protected String notes;
    protected boolean favorite;
    protected String createdAt;
    protected String updatedAt;

    /** No-arg constructor for Gson deserialization. */
    protected VaultItem() {
        this.id = UUID.randomUUID().toString();
        String now = DateUtils.getCurrentTimestamp();
        this.createdAt = now;
        this.updatedAt = now;
    }

    protected VaultItem(String title, String notes) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.notes = notes;
        String now = DateUtils.getCurrentTimestamp();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Securely wipes all sensitive data from this item. */
    public abstract void wipe();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VaultItem that = (VaultItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id='" + id + "'}";
    }
}
