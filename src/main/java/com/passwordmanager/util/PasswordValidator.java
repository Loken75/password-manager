package com.passwordmanager.util;

/**
 * Shared master password validation logic.
 * Requires: min 12 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special.
 */
public class PasswordValidator {

    public static boolean validate(char[] password) {
        if (password == null || password.length < 12) return false;
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}
