package com.passwordmanager.config;

public enum StorageMode {
    LOCAL("local"),
    REMOTE("remote");

    private final String value;

    StorageMode(String value) { this.value = value; }

    public String getValue() { return value; }

    public static StorageMode fromValue(String value) {
        for (StorageMode m : values()) {
            if (m.value.equals(value)) return m;
        }
        return LOCAL;
    }
}
