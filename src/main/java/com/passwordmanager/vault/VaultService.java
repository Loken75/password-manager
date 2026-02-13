package com.passwordmanager.vault;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Service for CRUD operations on vault entries.
 */
public class VaultService {
    private Vault vault;
    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    static {
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public VaultService(Vault vault) {
        this.vault = vault;
    }

    public Vault getVault() { return vault; }
    public void setVault(Vault vault) { this.vault = vault; }

    public void addEntry(VaultEntry entry) {
        entry.setUpdatedAt(ISO_FORMAT.format(new Date()));
        vault.getEntries().add(entry);
        vault.setUpdatedAt(ISO_FORMAT.format(new Date()));
    }

    public boolean updateEntry(VaultEntry updated) {
        for (int i = 0; i < vault.getEntries().size(); i++) {
            if (vault.getEntries().get(i).getId().equals(updated.getId())) {
                updated.setUpdatedAt(ISO_FORMAT.format(new Date()));
                vault.getEntries().set(i, updated);
                vault.setUpdatedAt(ISO_FORMAT.format(new Date()));
                return true;
            }
        }
        return false;
    }

    public boolean deleteEntry(String entryId) {
        Iterator<VaultEntry> it = vault.getEntries().iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(entryId)) {
                it.remove();
                vault.setUpdatedAt(ISO_FORMAT.format(new Date()));
                return true;
            }
        }
        return false;
    }

    public List<VaultEntry> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<VaultEntry>(vault.getEntries());
        }
        String q = query.toLowerCase();
        List<VaultEntry> results = new ArrayList<VaultEntry>();
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
            return new ArrayList<VaultEntry>(vault.getEntries());
        }
        List<VaultEntry> results = new ArrayList<VaultEntry>();
        for (VaultEntry e : vault.getEntries()) {
            if (category.equals(e.getCategory())) results.add(e);
        }
        return results;
    }

    public List<VaultEntry> sorted(List<VaultEntry> entries, String sortBy) {
        List<VaultEntry> sorted = new ArrayList<VaultEntry>(entries);
        Comparator<VaultEntry> comp;
        switch (sortBy) {
            case "date":
                comp = new Comparator<VaultEntry>() {
                    public int compare(VaultEntry a, VaultEntry b) {
                        return safe(b.getUpdatedAt()).compareTo(safe(a.getUpdatedAt()));
                    }
                };
                break;
            case "category":
                comp = new Comparator<VaultEntry>() {
                    public int compare(VaultEntry a, VaultEntry b) {
                        return safe(a.getCategory()).compareTo(safe(b.getCategory()));
                    }
                };
                break;
            default:
                comp = new Comparator<VaultEntry>() {
                    public int compare(VaultEntry a, VaultEntry b) {
                        return safe(a.getTitle()).compareToIgnoreCase(safe(b.getTitle()));
                    }
                };
        }
        Collections.sort(sorted, comp);
        return sorted;
    }

    private String safe(String s) { return s == null ? "" : s; }

    public Map<String, List<VaultEntry>> findDuplicatePasswords() {
        Map<String, List<VaultEntry>> map = new HashMap<String, List<VaultEntry>>();
        for (VaultEntry e : vault.getEntries()) {
            if (e.getPassword() != null && !e.getPassword().isEmpty()) {
                if (!map.containsKey(e.getPassword())) {
                    map.put(e.getPassword(), new ArrayList<VaultEntry>());
                }
                map.get(e.getPassword()).add(e);
            }
        }
        Map<String, List<VaultEntry>> duplicates = new HashMap<String, List<VaultEntry>>();
        for (Map.Entry<String, List<VaultEntry>> entry : map.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), entry.getValue());
            }
        }
        return duplicates;
    }

    public List<VaultEntry> findOldPasswords(int days) {
        List<VaultEntry> old = new ArrayList<VaultEntry>();
        long threshold = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        for (VaultEntry e : vault.getEntries()) {
            try {
                Date d = ISO_FORMAT.parse(e.getUpdatedAt());
                if (d.getTime() < threshold) old.add(e);
            } catch (Exception ex) {
                // skip
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
