package com.passwordmanager.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared master password validation logic.
 * Requires: min 12 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special.
 * Rejects common passwords that match known weak patterns.
 * All operations use char[] to avoid leaking passwords as immutable Strings.
 */
public class PasswordValidator {

    private static final Set<String> COMMON_PASSWORDS = new HashSet<>(Arrays.asList(
        "password", "123456", "12345678", "qwerty", "abc123", "monkey", "master",
        "dragon", "111111", "baseball", "iloveyou", "trustno1", "sunshine",
        "princess", "football", "charlie", "shadow", "michael", "qwerty123",
        "password1", "password123", "admin", "letmein", "welcome", "login",
        "starwars", "654321", "batman", "access", "hello", "passw0rd",
        "pass1234", "1234567890", "superman", "qazwsx", "default",
        "password1234", "changeme", "p@ssw0rd", "p@ssword", "password!",
        "azerty", "azertyuiop", "motdepasse"
    ));

    public static boolean validate(char[] password) {
        if (password == null || password.length < 12) return false;
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (!(hasUpper && hasLower && hasDigit && hasSpecial)) return false;

        // Build lowercase copy in char[] (no String creation from user password)
        char[] lower = new char[password.length];
        for (int i = 0; i < password.length; i++) {
            lower[i] = Character.toLowerCase(password[i]);
        }
        try {
            if (matchesCommon(lower)) return false;

            // Build alpha-only base to catch variants like "Password123!"
            int alphaCount = 0;
            for (char c : lower) {
                if (c >= 'a' && c <= 'z') alphaCount++;
            }
            char[] base = new char[alphaCount];
            int idx = 0;
            for (char c : lower) {
                if (c >= 'a' && c <= 'z') base[idx++] = c;
            }
            try {
                return !matchesCommon(base);
            } finally {
                SecureWiper.wipe(base);
            }
        } finally {
            SecureWiper.wipe(lower);
        }
    }

    /**
     * Compares a char[] against each common password without creating
     * a String from the user's input.
     */
    private static boolean matchesCommon(char[] input) {
        for (String common : COMMON_PASSWORDS) {
            if (common.length() != input.length) continue;
            boolean match = true;
            for (int i = 0; i < input.length; i++) {
                if (common.charAt(i) != input[i]) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }
}
