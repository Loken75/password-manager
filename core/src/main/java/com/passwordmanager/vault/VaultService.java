package com.passwordmanager.vault;

import java.util.List;
import java.util.Map;

/**
 * Facade providing backward-compatible access to vault operations.
 * Delegates password-related operations to PasswordService,
 * and exposes the three specialized services.
 */
public class VaultService {
    private Vault vault;
    private PasswordService passwordService;
    private AppService appService;
    private SshKeyService sshKeyService;

    public VaultService(Vault vault) {
        this.vault = vault;
        this.passwordService = new PasswordService(vault);
        this.appService = new AppService(vault);
        this.sshKeyService = new SshKeyService(vault);
    }

    public Vault getVault() { return vault; }

    public void setVault(Vault vault) {
        this.vault = vault;
        this.passwordService = new PasswordService(vault);
        this.appService = new AppService(vault);
        this.sshKeyService = new SshKeyService(vault);
    }

    public PasswordService getPasswordService() { return passwordService; }
    public AppService getAppService() { return appService; }
    public SshKeyService getSshKeyService() { return sshKeyService; }

    // === Delegate to PasswordService for backward compatibility ===

    public synchronized void addEntry(PasswordEntry entry) {
        passwordService.addEntry(entry);
    }

    public synchronized boolean updateEntry(PasswordEntry updated) {
        return passwordService.updateEntry(updated);
    }

    public synchronized boolean deleteEntry(String entryId) {
        return passwordService.deleteEntry(entryId);
    }

    public synchronized List<PasswordEntry> search(String query) {
        return passwordService.search(query);
    }

    public synchronized List<PasswordEntry> getByCategory(String category) {
        return passwordService.getByCategory(category);
    }

    public synchronized List<PasswordEntry> sorted(List<PasswordEntry> entries, SortField sortBy) {
        return passwordService.sorted(entries, sortBy);
    }

    public synchronized List<PasswordEntry> sorted(List<PasswordEntry> entries, SortField sortBy, boolean descending) {
        return passwordService.sorted(entries, sortBy, descending);
    }

    public synchronized List<PasswordEntry> filter(List<PasswordEntry> entries, EntryFilter filter) {
        return passwordService.filter(entries, filter);
    }

    public synchronized Map<String, List<PasswordEntry>> findDuplicatePasswords() {
        return passwordService.findDuplicatePasswords();
    }

    public synchronized List<PasswordEntry> findOldPasswords(int days) {
        return passwordService.findOldPasswords(days);
    }

    public synchronized int bulkDelete(List<String> entryIds) {
        return passwordService.bulkDelete(entryIds);
    }

    public synchronized int bulkChangeCategory(List<String> entryIds, String newCategory) {
        return passwordService.bulkChangeCategory(entryIds, newCategory);
    }

    public synchronized boolean toggleFavorite(String entryId) {
        return passwordService.toggleFavorite(entryId);
    }

    public synchronized int bulkSetFavorite(List<String> entryIds, boolean favorite) {
        return passwordService.bulkSetFavorite(entryIds, favorite);
    }

    public synchronized void addCategory(String category) {
        passwordService.addCategory(category);
    }

    public synchronized boolean removeCategory(String category) {
        return passwordService.removeCategory(category);
    }
}
