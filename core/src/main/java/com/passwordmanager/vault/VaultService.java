package com.passwordmanager.vault;

import java.util.List;
import java.util.Map;

/**
 * Facade providing backward-compatible access to vault operations.
 * Delegates password-related operations to PasswordService,
 * and exposes the three specialized services.
 *
 * <p>This facade does not add its own lock: each delegated call is a single
 * operation that is already serialized on the {@link Vault} monitor by the
 * underlying service, so a facade-level {@code synchronized} would only add a
 * redundant second monitor.
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

    public void addEntry(PasswordEntry entry) {
        passwordService.addEntry(entry);
    }

    public boolean updateEntry(PasswordEntry updated) {
        return passwordService.updateEntry(updated);
    }

    public boolean deleteEntry(String entryId) {
        return passwordService.deleteEntry(entryId);
    }

    public List<PasswordEntry> search(String query) {
        return passwordService.search(query);
    }

    public List<PasswordEntry> getByCategory(String category) {
        return passwordService.getByCategory(category);
    }

    public List<PasswordEntry> sorted(List<PasswordEntry> entries, SortField sortBy) {
        return passwordService.sorted(entries, sortBy);
    }

    public List<PasswordEntry> sorted(List<PasswordEntry> entries, SortField sortBy, boolean descending) {
        return passwordService.sorted(entries, sortBy, descending);
    }

    public List<PasswordEntry> filter(List<PasswordEntry> entries, EntryFilter filter) {
        return passwordService.filter(entries, filter);
    }

    public Map<String, List<PasswordEntry>> findDuplicatePasswords() {
        return passwordService.findDuplicatePasswords();
    }

    public List<PasswordEntry> findOldPasswords(int days) {
        return passwordService.findOldPasswords(days);
    }

    public int bulkDelete(List<String> entryIds) {
        return passwordService.bulkDelete(entryIds);
    }

    public int bulkChangeCategory(List<String> entryIds, String newCategory) {
        return passwordService.bulkChangeCategory(entryIds, newCategory);
    }

    public boolean toggleFavorite(String entryId) {
        return passwordService.toggleFavorite(entryId);
    }

    public int bulkSetFavorite(List<String> entryIds, boolean favorite) {
        return passwordService.bulkSetFavorite(entryIds, favorite);
    }

    public void addCategory(String category) {
        passwordService.addCategory(category);
    }

    public boolean removeCategory(String category) {
        return passwordService.removeCategory(category);
    }
}
