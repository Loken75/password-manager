package com.passwordmanager.config;

public enum ThemeMode {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    private final String value;

    ThemeMode(String value) { this.value = value; }

    public String getValue() { return value; }

    public static ThemeMode fromValue(String value) {
        for (ThemeMode m : values()) {
            if (m.value.equals(value)) return m;
        }
        return LIGHT;
    }
}
