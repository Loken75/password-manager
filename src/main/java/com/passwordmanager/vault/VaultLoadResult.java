package com.passwordmanager.vault;

import com.passwordmanager.crypto.VaultSession;

/**
 * Result of loading a vault: contains both the decrypted vault and the session (DEK).
 */
public class VaultLoadResult {
    private final Vault vault;
    private final VaultSession session;

    public VaultLoadResult(Vault vault, VaultSession session) {
        this.vault = vault;
        this.session = session;
    }

    public Vault getVault() { return vault; }
    public VaultSession getSession() { return session; }
}
