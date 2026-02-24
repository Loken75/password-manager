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

    public void addEntry(VaultEntry entry) {
        entry.setUpdatedAt(DateUtils.getCurrentTimestamp());
        vault.getEntries().add(entry);
        vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
    }

    public boolean updateEntry(VaultEntry updated) {
        for (int i = 0; i < vault.getEntries().size(); i++) {
            if (vault.getEntries().get(i).getId().equals(updated.getId())) {
                updated.setUpdatedAt(DateUtils.getCurrentTimestamp());
                vault.getEntries().set(i, updated);
                vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
                return true;
            }
        }
        return false;
    }

    public boolean deleteEntry(String entryId) {
        Iterator<VaultEntry> it = vault.getEntries().iterator();
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

    public List<VaultEntry> search(String query) {
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
            || containsIC(e.getUrl(), q) || containsIC(e.getNotes(), q)
            || containsIC(e.getCategory(), q)
            || (e.getTags() != null && e.getTags().toString().toLowerCase().contains(q));
    }

    private boolean containsIC(String str, String q) {
        return str != null && str.toLowerCase().contains(q);
    }

    public List<VaultEntry> getByCategory(String category) {
        if (category == null || category.isEmpty()) {
            return new ArrayList<>(vault.getEntries());
        }
        List<VaultEntry> results = new ArrayList<>();
        for (VaultEntry e : vault.getEntries()) {
            if (category.equals(e.getCategory())) results.add(e);
        }
        return results;
    }

    public List<VaultEntry> sorted(List<VaultEntry> entries, SortField sortBy) {
        List<VaultEntry> sorted = new ArrayList<>(entries);
        Comparator<VaultEntry> comp;
        switch (sortBy) {
            case DATE:
                comp = (a, b) -> safe(b.getUpdatedAt()).compareTo(safe(a.getUpdatedAt()));
                break;
            case CATEGORY:
                comp = (a, b) -> safe(a.getCategory()).compareTo(safe(b.getCategory()));
                break;
            default:
                comp = (a, b) -> safe(a.getTitle()).compareToIgnoreCase(safe(b.getTitle()));
        }
        sorted.sort(comp);
        return sorted;
    }

    private String safe(String s) { return s == null ? "" : s; }

    /**
     * Finds entries that share the same password.
     * Uses SHA-256 hashes to avoid storing plaintext passwords as keys.
     */
    public Map<String, List<VaultEntry>> findDuplicatePasswords() {
        Map<String, List<VaultEntry>> map = new HashMap<>();
        for (VaultEntry e : vault.getEntries()) {
            if (e.getPassword() != null && e.getPassword().length > 0) {
                String hash = sha256(e.getPassword());
                map.computeIfAbsent(hash, k -> new ArrayList<>()).add(e);
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

    public List<VaultEntry> findOldPasswords(int days) {
        List<VaultEntry> old = new ArrayList<>();
        long threshold = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        for (VaultEntry e : vault.getEntries()) {
            try {
                Date d = DateUtils.parseTimestamp(e.getUpdatedAt());
                if (d.getTime() < threshold) old.add(e);
            } catch (Exception ex) {
                // skip entries with invalid dates
            }
        }
        return old;
    }

    public void addCategory(String category) {
        if (!vault.getCategories().contains(category)) {
            vault.getCategories().add(category);
        }
    }

    public boolean removeCategory(String category) {
        return vault.getCategories().remove(category);
    }
}
