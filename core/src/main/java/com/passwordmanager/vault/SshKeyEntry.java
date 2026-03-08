package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;

/**
 * Represents an SSH key stored inside the vault.
 * The private key is stored as char[] (PEM format) and follows the same
 * defensive-copy / wipe-on-set pattern for sensitive fields.
 */
public class SshKeyEntry extends VaultItem {

    private char[] privateKey;
    private String publicKey;
    private String keyType;    // "ED25519" or "RSA"
    private String fingerprint;

    /** No-arg constructor for Gson deserialization. */
    public SshKeyEntry() {
        super();
    }

    public SshKeyEntry(String name, char[] privateKey, String publicKey,
                       String keyType, String fingerprint) {
        super(name, null);
        this.privateKey = privateKey != null ? privateKey.clone() : null;
        this.publicKey = publicKey;
        this.keyType = keyType;
        this.fingerprint = fingerprint;
    }

    /** Returns a defensive copy of the private key. Caller must wipe after use. */
    public char[] getPrivateKey() {
        return privateKey != null ? privateKey.clone() : null;
    }

    /** Wipes the old value before storing a defensive copy of the new one. */
    public void setPrivateKey(char[] privateKey) {
        SecureWiper.wipe(this.privateKey);
        this.privateKey = privateKey != null ? privateKey.clone() : null;
    }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    @Override
    public void wipe() {
        SecureWiper.wipe(this.privateKey);
        this.privateKey = null;
        this.publicKey = null;
        this.fingerprint = null;
    }
}
