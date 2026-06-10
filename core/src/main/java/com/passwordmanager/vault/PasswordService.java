package com.passwordmanager.vault;

import com.passwordmanager.util.DateUtils;
import com.passwordmanager.util.SecureWiper;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Service for CRUD operations on password entries.
 * Contains all logic previously in VaultService related to PasswordEntry.
 */
public class PasswordService extends BaseVaultService<PasswordEntry> {

    public PasswordService(Vault vault) {
        super(vault);
    }

    @Override
    protected List<PasswordEntry> getMutableList() {
        return vault.getEntriesMutable();
    }

    @Override
    public List<PasswordEntry> getReadOnlyList() {
        return vault.getEntries();
    }

    @Override
    protected boolean matchesBase(PasswordEntry e, String q) {
        return containsIC(e.getTitle(), q) || containsIC(e.getUsername(), q)
            || containsIC(e.getEmail(), q) || containsIC(e.getUrl(), q)
            || containsIC(e.getNotes(), q) || containsIC(e.getCategory(), q)
            || (e.getTags() != null && e.getTags().toString().toLowerCase().contains(q));
    }

    public List<PasswordEntry> getByCategory(String category) {
        synchronized (vault) {
            if (category == null || category.isEmpty()) {
                return getActiveList();
            }
            List<PasswordEntry> results = new ArrayList<>();
            for (PasswordEntry e : getReadOnlyList()) {
                if (!e.isDeleted() && category.equals(e.getCategory())) results.add(e);
            }
            return results;
        }
    }

    /** Pure function of the supplied list; needs no synchronization. */
    public List<PasswordEntry> sorted(List<PasswordEntry> entries, SortField sortBy) {
        return sorted(entries, sortBy, false);
    }

    /**
     * Sorts entries with favorites ALWAYS grouped first (two blocks: favorites, then the rest),
     * each block ordered by {@code sortBy}. {@code descending} reverses only the in-block field
     * order — it never moves favorites below non-favorites.
     *
     * <p>Pure function of the supplied list; needs no synchronization. The caller is
     * responsible for passing a stable list (not a live view being mutated concurrently).
     */
    public List<PasswordEntry> sorted(List<PasswordEntry> entries, SortField sortBy, boolean descending) {
        List<PasswordEntry> sorted = new ArrayList<>(entries);
        Comparator<PasswordEntry> field;
        switch (sortBy) {
            case USERNAME:
                field = (a, b) -> safe(a.getUsername()).compareToIgnoreCase(safe(b.getUsername()));
                break;
            case EMAIL:
                field = (a, b) -> safe(a.getEmail()).compareToIgnoreCase(safe(b.getEmail()));
                break;
            case URL:
                field = (a, b) -> safe(a.getUrl()).compareToIgnoreCase(safe(b.getUrl()));
                break;
            case DATE:
                field = (a, b) -> safe(b.getUpdatedAt()).compareTo(safe(a.getUpdatedAt()));
                break;
            case CREATED:
                field = (a, b) -> safe(b.getCreatedAt()).compareTo(safe(a.getCreatedAt()));
                break;
            case CATEGORY:
                field = (a, b) -> safe(a.getCategory()).compareToIgnoreCase(safe(b.getCategory()));
                break;
            case STRENGTH:
                field = (a, b) -> Integer.compare(strengthOrdinal(a), strengthOrdinal(b));
                break;
            case FAVORITE:
            default:
                // Favorites are already floated first below; secondary order is by title.
                field = (a, b) -> safe(a.getTitle()).compareToIgnoreCase(safe(b.getTitle()));
        }
        Comparator<PasswordEntry> directed = descending ? field.reversed() : field;
        // Favorites are always grouped first, regardless of sort direction.
        Comparator<PasswordEntry> withFavorites = (a, b) -> {
            int favCmp = Boolean.compare(b.isFavorite(), a.isFavorite());
            return favCmp != 0 ? favCmp : directed.compare(a, b);
        };
        sorted.sort(withFavorites);
        return sorted;
    }

    /** Pure function of the supplied list; needs no synchronization. */
    public List<PasswordEntry> filter(List<PasswordEntry> entries, EntryFilter filter) {
        List<PasswordEntry> result = new ArrayList<>();
        for (PasswordEntry e : entries) {
            if (filter.matches(e)) result.add(e);
        }
        return result;
    }

    private static int strengthOrdinal(PasswordEntry entry) {
        char[] pw = entry.getPassword();
        if (pw == null || pw.length == 0) return -1;
        try {
            return com.passwordmanager.crypto.PasswordStrengthAnalyzer.analyze(pw).ordinal();
        } finally {
            SecureWiper.wipe(pw);
        }
    }

    public Map<String, List<PasswordEntry>> findDuplicatePasswords() {
        synchronized (vault) {
            Map<String, List<PasswordEntry>> map = new HashMap<>();
            for (PasswordEntry e : getActiveList()) {
                char[] pw = e.getPassword();
                if (pw != null && pw.length > 0) {
                    try {
                        String hash = sha256(pw);
                        map.computeIfAbsent(hash, k -> new ArrayList<>()).add(e);
                    } finally {
                        SecureWiper.wipe(pw);
                    }
                }
            }
            Map<String, List<PasswordEntry>> duplicates = new HashMap<>();
            for (Map.Entry<String, List<PasswordEntry>> entry : map.entrySet()) {
                if (entry.getValue().size() > 1) {
                    duplicates.put(entry.getKey(), entry.getValue());
                }
            }
            return duplicates;
        }
    }

    private static String sha256(char[] input) {
        ByteBuffer bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(input));
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } finally {
            SecureWiper.wipe(bytes);
            if (bb.hasArray()) {
                SecureWiper.wipe(bb.array());
            }
        }
    }

    public List<PasswordEntry> findOldPasswords(int days) {
        synchronized (vault) {
            List<PasswordEntry> old = new ArrayList<>();
            long threshold = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
            for (PasswordEntry e : getActiveList()) {
                try {
                    java.util.Date d = DateUtils.parseTimestamp(e.getUpdatedAt());
                    if (d.getTime() < threshold) old.add(e);
                } catch (Exception ex) {
                    java.util.logging.Logger.getLogger(PasswordService.class.getName())
                        .fine("Skipping entry with invalid date: " + e.getId());
                }
            }
            return old;
        }
    }

    public int bulkChangeCategory(List<String> entryIds, String newCategory) {
        synchronized (vault) {
            int count = 0;
            for (PasswordEntry entry : getMutableList()) {
                if (entryIds.contains(entry.getId())) {
                    entry.setCategory(newCategory);
                    entry.setUpdatedAt(DateUtils.getCurrentTimestamp());
                    count++;
                }
            }
            if (count > 0) {
                vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
            }
            return count;
        }
    }

    public void addCategory(String category) {
        synchronized (vault) {
            if (!vault.getCategories().contains(category)) {
                vault.getCategoriesMutable().add(category);
            }
        }
    }

    public boolean removeCategory(String category) {
        synchronized (vault) {
            return vault.getCategoriesMutable().remove(category);
        }
    }
}
