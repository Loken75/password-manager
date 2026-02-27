package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;

/**
 * Represents an application PIN entry stored in the vault.
 * PIN is stored as char[] to allow explicit memory wiping.
 */
public class AppEntry extends VaultItem {
    private String username;
    private char[] pin;

    /** Default constructor for Gson deserialization. */
    public AppEntry() {
        super();
    }

    public AppEntry(String title, String username, char[] pin, String notes) {
        super(title, notes);
        this.username = username;
        this.pin = pin != null ? pin.clone() : null;
    }

    @Override
    public void wipe() {
        SecureWiper.wipe(pin);
        pin = null;
        title = null;
        username = null;
        notes = null;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    /**
     * Returns a defensive copy of the PIN. Caller is responsible for
     * wiping the returned array via {@link SecureWiper#wipe(char[])}.
     */
    public char[] getPin() { return pin != null ? pin.clone() : null; }
    public void setPin(char[] pin) {
        SecureWiper.wipe(this.pin);
        this.pin = pin != null ? pin.clone() : null;
    }
}
