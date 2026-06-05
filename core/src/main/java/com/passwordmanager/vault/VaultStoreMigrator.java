package com.passwordmanager.vault;

import com.passwordmanager.vault.store.VaultStore;

import java.io.IOException;
import java.util.List;

/**
 * Moves vault-related files (everything matching the {@code vault_} prefix: the vault
 * itself plus {@code .bak}/{@code .sync_meta}/{@code .pending} sidecars) from one
 * {@link VaultStore} to another. Store-agnostic, so the same logic serves desktop folder
 * switches and Android SAF migrations.
 *
 * <p>Semantics: copy-then-delete (never a destructive move), and an existing file in the
 * destination is never overwritten. A per-file failure leaves that file in the source.
 */
public final class VaultStoreMigrator {

    private VaultStoreMigrator() {}

    /** Outcome of a migration: how many files moved, were skipped (already present), or failed. */
    public static final class Result {
        public final int moved;
        public final int skipped;
        public final int failed;

        public Result(int moved, int skipped, int failed) {
            this.moved = moved;
            this.skipped = skipped;
            this.failed = failed;
        }

        public boolean hasFailures() { return failed > 0; }
    }

    /**
     * Copies every {@code vault_*} file from {@code from} to {@code to} (without clobbering)
     * and removes the source copy once written.
     *
     * @throws IOException if the source listing itself cannot be read (per-file errors are
     *                     counted in {@link Result#failed}, not thrown)
     */
    public static Result migrate(VaultStore from, VaultStore to) throws IOException {
        int moved = 0, skipped = 0, failed = 0;
        List<String> names = from.list();
        for (String name : names) {
            if (!name.startsWith(VaultManager.VAULT_FILE_PREFIX)) continue;
            if (to.exists(name)) {
                skipped++;
                continue;
            }
            try {
                to.writeAtomic(name, from.read(name));
                from.delete(name);
                moved++;
            } catch (IOException e) {
                failed++;
            }
        }
        return new Result(moved, skipped, failed);
    }
}
