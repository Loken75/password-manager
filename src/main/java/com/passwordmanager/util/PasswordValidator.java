package com.passwordmanager.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared master password validation logic.
 * Requires: min 12 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special.
 * Rejects common passwords that match known weak patterns.
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

        // Reject passwords whose lowercase base matches a common password
        String lower = new String(password).toLowerCase();
        // Strip trailing digits and special chars to catch variants like "Password123!"
        String base = lower.replaceAll("[^a-z]", "");
        boolean rejected = COMMON_PASSWORDS.contains(lower) || COMMON_PASSWORDS.contains(base);
        return !rejected;
    }
}
