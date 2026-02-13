package com.passwordmanager.model;

/**
 * Represents an application user with their vault file reference.
 */
public class User {
    private String username;
    private String vaultFileName;

    public User(String username) {
        this.username = username;
        this.vaultFileName = "vault_" + username + ".enc";
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getVaultFileName() { return vaultFileName; }
    public void setVaultFileName(String vaultFileName) { this.vaultFileName = vaultFileName; }

    @Override
    public String toString() { return username; }
}
