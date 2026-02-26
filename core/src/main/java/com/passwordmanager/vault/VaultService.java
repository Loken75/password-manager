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
 * Service for CRUD operations on vault entries.
 */
public class VaultService {
    private Vault vault;

    public VaultService(Vault vault) {
        this.vault = vault;
    }

    public Vault getVault() { return vault; }
    public void setVault(Vault vault) { this.vault = vault; }

    public synchronized void addEntry(VaultEntry entry) {
        entry.setUpdatedAt(DateUtils.getCurrentTimestamp());
        vault.addEntry(entry);
        vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
    }

    public synchronized boolean updateEntry(VaultEntry updated) {
        List<VaultEntry> entries = vault.getEntriesMutable();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(updated.getId())) {
                updated.setUpdatedAt(DateUtils.getCurrentTimestamp());
                entries.set(i, updated);
                vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
                return true;
            }
        }
        return false;
    }

    public synchronized boolean deleteEntry(String entryId) {
        Iterator<VaultEntry> it = vault.getEntriesMutable().iterator();
        while (it.hasNext()) {
            VaultEntry entry = it.next();
            if (entry.getId().equals(entryId)) {
                entry.wipe();
                it.remove();
                vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
                return true;
            }
        }
        return false;
    }

    public synchronized List<VaultEntry> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(vault.getEntries());
        }
        String q = query.toLowerCase();
        List<VaultEntry> results = new ArrayList<>();
        for (VaultEntry e : vault.getEntries()) {
            if (matches(e, q)) results.add(e);
        }
        return results;
    }

    private boolean matches(VaultEntry e, String q) {
        return containsIC(e.getTitle(), q) || containsIC(e.getUsername(), q)
            || containsIC(e.getEmail(), q) || containsIC(e.getPseudo(), q)
            || containsIC(e.getUrl(), q) || containsIC(e.getNotes(), q)
            || containsIC(e.getCategory(), q)
            || (e.getTags() != null && e.getTags().toString().toLowerCase().contains(q));
    }

    private boolean containsIC(String str, String q) {
        return str != null && str.toLowerCase().contains(q);
    }

    public synchronized List<VaultEntry> getByCategory(String category) {
        if (category == null || category.isEmpty()) {
            return new ArrayList<>(vault.getEntries());
        }
        List<VaultEntry> results = new ArrayList<>();
        for (VaultEntry e : vault.getEntries()) {
            if (category.equals(e.getCategory())) results.add(e);
        }
        return results;
    }

    public synchronized List<VaultEntry> sorted(List<VaultEntry> entries, SortField sortBy) {
        List<VaultEntry> sorted = new ArrayList<>(entries);
        Comparator<VaultEntry> comp;
        switch (sortBy) {
            case USERNAME:
                comp = (a, b) -> safe(a.getUsername()).compareToIgnoreCase(safe(b.getUsername()));
                break;
            case EMAIL:
                comp = (a, b) -> safe(a.getEmail()).compareToIgnoreCase(safe(b.getEmail()));
                break;
            case PSEUDO:
                comp = (a, b) -> safe(a.getPseudo()).compareToIgnoreCase(safe(b.getPseudo()));
                break;
            case URL:
                comp = (a, b) -> safe(a.getUrl()).compareToIgnoreCase(safe(b.getUrl()));
                break;
            case DATE:
                comp = (a, b) -> safe(b.getUpdatedAt()).compareTo(safe(a.getUpdatedAt()));
                break;
            case CATEGORY:
                comp = (a, b) -> safe(a.getCategory()).compareToIgnoreCase(safe(b.getCategory()));
                break;
            default:
                comp = (a, b) -> safe(a.getTitle()).compareToIgnoreCase(safe(b.getTitle()));
        }
        Comparator<VaultEntry> withFavorites = (a, b) -> {
            int favCmp = Boolean.compare(b.isFavorite(), a.isFavorite());
            return favCmp != 0 ? favCmp : comp.compare(a, b);
        };
        sorted.sort(withFavorites);
        return sorted;
    }

    public synchronized List<VaultEntry> filter(List<VaultEntry> entries, EntryFilter filter) {
        List<VaultEntry> result = new ArrayList<>();
        for (VaultEntry e : entries) {
            if (filter.matches(e)) result.add(e);
        }
        return result;
    }

    private String safe(String s) { return s == null ? "" : s; }

    /**
     * Finds entries that share the same password.
     * Uses SHA-256 hashes to avoid storing plaintext passwords as keys.
     */
    public synchronized Map<String, List<VaultEntry>> findDuplicatePasswords() {
        Map<String, List<VaultEntry>> map = new HashMap<>();
        for (VaultEntry e : vault.getEntries()) {
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
        Map<String, List<VaultEntry>> duplicates = new HashMap<>();
        for (Map.Entry<String, List<VaultEntry>> entry : map.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), entry.getValue());
            }
        }
        return duplicates;
    }

    /**
     * SHA-256 hash of a char[] without creating an intermediate String.
     */
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

    public synchronized List<VaultEntry> findOldPasswords(int days) {
        List<VaultEntry> old = new ArrayList<>();
        long threshold = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        for (VaultEntry e : vault.getEntries()) {
            try {
                Date d = DateUtils.parseTimestamp(e.getUpdatedAt());
                if (d.getTime() < threshold) old.add(e);
            } catch (Exception ex) {
                // Skip entries with unparseable dates (e.g. legacy data)
                java.util.logging.Logger.getLogger(VaultService.class.getName())
                    .fine("Skipping entry with invalid date: " + e.getId());
            }
        }
        return old;
    }

    public synchronized int bulkDelete(List<String> entryIds) {
        int count = 0;
        for (String id : entryIds) {
            if (deleteEntry(id)) count++;
        }
        return count;
    }

    public synchronized int bulkChangeCategory(List<String> entryIds, String newCategory) {
        int count = 0;
        for (VaultEntry entry : vault.getEntriesMutable()) {
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

    public synchronized boolean toggleFavorite(String entryId) {
        for (VaultEntry entry : vault.getEntriesMutable()) {
            if (entry.getId().equals(entryId)) {
                entry.setFavorite(!entry.isFavorite());
                entry.setUpdatedAt(DateUtils.getCurrentTimestamp());
                vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
                return true;
            }
        }
        return false;
    }

    public synchronized int bulkSetFavorite(List<String> entryIds, boolean favorite) {
        int count = 0;
        for (VaultEntry entry : vault.getEntriesMutable()) {
            if (entryIds.contains(entry.getId())) {
                entry.setFavorite(favorite);
                entry.setUpdatedAt(DateUtils.getCurrentTimestamp());
                count++;
            }
        }
        if (count > 0) {
            vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
        }
        return count;
    }

    public synchronized void addCategory(String category) {
        if (!vault.getCategories().contains(category)) {
            vault.getCategories().add(category);
        }
    }

    public synchronized boolean removeCategory(String category) {
        return vault.getCategories().remove(category);
    }
}
