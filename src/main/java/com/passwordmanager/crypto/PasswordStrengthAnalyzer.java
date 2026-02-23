package com.passwordmanager.crypto;

import com.passwordmanager.util.SecureWiper;

/**
 * Analyzes password strength with both char[] (preferred) and String overloads.
 */
public class PasswordStrengthAnalyzer {

    public enum Strength {
        WEAK, MEDIUM, STRONG, VERY_STRONG
    }

    public static Strength analyze(char[] password) {
        if (password == null || password.length == 0) return Strength.WEAK;

        int len = password.length;
        int types = countCharTypes(password);

        if (len < 8 || types <= 1) return Strength.WEAK;

        // Penalize weak patterns
        int penalty = 0;
        if (hasSequentialChars(password, 4)) penalty++;
        if (hasRepeatedChars(password, 4)) penalty++;
        if (isAllSameCase(password)) penalty++;

        int effectiveTypes = Math.max(1, types - penalty);
        int effectiveLen = len;
        if (penalty > 0) effectiveLen = Math.max(8, effectiveLen - penalty * 4);

        if (effectiveLen >= 16 && effectiveTypes >= 4) return Strength.VERY_STRONG;
        if (effectiveLen >= 12 && effectiveTypes >= 3) return Strength.STRONG;
        if (effectiveTypes <= 1) return Strength.WEAK;
        return Strength.MEDIUM;
    }

    /**
     * Detects sequential characters (e.g. "abcd", "1234", "dcba").
     */
    static boolean hasSequentialChars(char[] password, int threshold) {
        if (password.length < threshold) return false;
        int ascending = 1, descending = 1;
        for (int i = 1; i < password.length; i++) {
            if (password[i] == password[i - 1] + 1) {
                ascending++;
                if (ascending >= threshold) return true;
            } else {
                ascending = 1;
            }
            if (password[i] == password[i - 1] - 1) {
                descending++;
                if (descending >= threshold) return true;
            } else {
                descending = 1;
            }
        }
        return false;
    }

    /**
     * Detects repeated characters (e.g. "aaaa", "1111").
     */
    static boolean hasRepeatedChars(char[] password, int threshold) {
        if (password.length < threshold) return false;
        int count = 1;
        for (int i = 1; i < password.length; i++) {
            if (password[i] == password[i - 1]) {
                count++;
                if (count >= threshold) return true;
            } else {
                count = 1;
            }
        }
        return false;
    }

    /**
     * Checks if all alphabetic characters are the same case.
     */
    private static boolean isAllSameCase(char[] password) {
        boolean hasUpper = false, hasLower = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isLowerCase(c)) hasLower = true;
            if (hasUpper && hasLower) return false;
        }
        return true;
    }

    /** Convenience overload for non-sensitive contexts (e.g. generator preview). */
    public static Strength analyze(String password) {
        if (password == null) return Strength.WEAK;
        char[] chars = password.toCharArray();
        try {
            return analyze(chars);
        } finally {
            SecureWiper.wipe(chars);
        }
    }

    public static int getScore(char[] password) {
        if (password == null || password.length == 0) return 0;

        int score = 0;
        int len = password.length;

        score += Math.min(len * 4, 40);

        int types = countCharTypes(password);
        score += types * 15;

        if (len >= 16) score += 10;
        if (len >= 20) score += 10;

        // Penalize detected patterns
        if (hasSequentialChars(password, 4)) score -= 15;
        if (hasRepeatedChars(password, 4)) score -= 15;

        return Math.max(0, Math.min(score, 100));
    }

    /** Convenience overload for non-sensitive contexts. */
    public static int getScore(String password) {
        if (password == null) return 0;
        char[] chars = password.toCharArray();
        try {
            return getScore(chars);
        } finally {
            SecureWiper.wipe(chars);
        }
    }

    public static int countCharTypes(char[] password) {
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        int count = 0;
        if (hasUpper) count++;
        if (hasLower) count++;
        if (hasDigit) count++;
        if (hasSpecial) count++;
        return count;
    }

    /** Convenience overload. */
    public static int countCharTypes(String password) {
        if (password == null) return 0;
        char[] chars = password.toCharArray();
        try {
            return countCharTypes(chars);
        } finally {
            SecureWiper.wipe(chars);
        }
    }
}
