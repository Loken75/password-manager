package com.passwordmanager.vault;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.util.SecureWiper;

import java.util.List;

/**
 * Composable filter for vault entries with Builder pattern.
 * All criteria are combined with AND logic.
 */
public class EntryFilter {

    private final String category;
    private final List<String> tags;
    private final PasswordStrengthAnalyzer.Strength exactStrength;
    private final boolean favoritesOnly;
    private final String searchQuery;

    private EntryFilter(Builder builder) {
        this.category = builder.category;
        this.tags = builder.tags;
        this.exactStrength = builder.exactStrength;
        this.favoritesOnly = builder.favoritesOnly;
        this.searchQuery = builder.searchQuery;
    }

    /**
     * Tests whether the given entry matches all configured filter criteria.
     * All non-null criteria are combined with AND logic.
     */
    public boolean matches(PasswordEntry entry) {
        if (entry == null) return false;

        // Category filter
        if (category != null && !category.isEmpty()) {
            if (!category.equals(entry.getCategory())) return false;
        }

        // Tags filter - entry must contain all specified tags
        if (tags != null && !tags.isEmpty()) {
            List<String> entryTags = entry.getTags();
            if (entryTags == null) return false;
            for (String tag : tags) {
                if (!entryTags.contains(tag)) return false;
            }
        }

        // Strength filter
        if (exactStrength != null) {
            char[] pw = entry.getPassword();
            try {
                if (pw == null) return false;
                PasswordStrengthAnalyzer.Strength strength = PasswordStrengthAnalyzer.analyze(pw);
                if (strength != exactStrength) return false;
            } finally {
                SecureWiper.wipe(pw);
            }
        }

        // Favorites filter
        if (favoritesOnly) {
            if (!entry.isFavorite()) return false;
        }

        // Search query filter
        if (searchQuery != null && !searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase();
            if (!containsIC(entry.getTitle(), q)
                    && !containsIC(entry.getUsername(), q)
                    && !containsIC(entry.getEmail(), q)
                    && !containsIC(entry.getUrl(), q)
                    && !containsIC(entry.getNotes(), q)
                    && !containsIC(entry.getCategory(), q)
                    && !tagsContain(entry.getTags(), q)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true if any filter criterion is active.
     */
    public boolean hasActiveFilters() {
        return (category != null && !category.isEmpty())
            || (tags != null && !tags.isEmpty())
            || exactStrength != null
            || favoritesOnly
            || (searchQuery != null && !searchQuery.isEmpty());
    }

    private static boolean containsIC(String str, String q) {
        return str != null && str.toLowerCase().contains(q);
    }

    private static boolean tagsContain(List<String> tags, String q) {
        if (tags == null) return false;
        for (String tag : tags) {
            if (tag != null && tag.toLowerCase().contains(q)) return true;
        }
        return false;
    }

    public static class Builder {
        private String category;
        private List<String> tags;
        private PasswordStrengthAnalyzer.Strength exactStrength;
        private boolean favoritesOnly;
        private String searchQuery;

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder exactStrength(PasswordStrengthAnalyzer.Strength exactStrength) {
            this.exactStrength = exactStrength;
            return this;
        }

        public Builder favoritesOnly(boolean favoritesOnly) {
            this.favoritesOnly = favoritesOnly;
            return this;
        }

        public Builder searchQuery(String searchQuery) {
            this.searchQuery = searchQuery;
            return this;
        }

        public EntryFilter build() {
            return new EntryFilter(this);
        }
    }
}
