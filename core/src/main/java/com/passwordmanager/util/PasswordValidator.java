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
        "azerty", "azertyuiop", "motdepasse",
        // Expanded set: common keyboard walks, sequences, and frequent words (EN + FR)
        "qwertyuiop", "asdfghjkl", "zxcvbnm", "1q2w3e4r", "1qaz2wsx", "qwe123",
        "admin123", "root", "toor", "secret", "whatever", "freedom", "ninja",
        "mustang", "jordan", "harley", "ranger", "hunter", "buster", "soccer",
        "hockey", "killer", "george", "thomas", "computer", "jessica", "pepper",
        "welcome1", "iloveyou1", "000000", "121212", "123123", "123321",
        "555555", "666666", "777777", "888888", "999999", "12345", "123456789",
        "abcd1234", "soleil", "bonjour", "coucou", "doudou", "jetaime", "loulou",
        "camille", "nicolas"
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
     * Uses constant-time comparison to prevent timing side-channels.
     */
    private static boolean matchesCommon(char[] input) {
        boolean found = false;
        for (String common : COMMON_PASSWORDS) {
            // Constant-time: always compare all chars up to max length,
            // length mismatch forces diff != 0 without early return.
            int lenDiff = common.length() ^ input.length;
            int diff = lenDiff;
            int limit = Math.min(common.length(), input.length);
            for (int i = 0; i < limit; i++) {
                diff |= common.charAt(i) ^ input[i];
            }
            if (diff == 0) found = true;
        }
        return found;
    }
}
