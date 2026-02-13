package com.passwordmanager.crypto;

/**
 * Analyzes password strength.
 */
public class PasswordStrengthAnalyzer {

    public enum Strength {
        WEAK, MEDIUM, STRONG, VERY_STRONG
    }

    /**
     * Analyzes the strength of a password.
     */
    public static Strength analyze(String password) {
        if (password == null || password.isEmpty()) return Strength.WEAK;

        int len = password.length();
        int types = countCharTypes(password);

        if (len < 8 || types <= 1) return Strength.WEAK;
        if (len >= 16 && types >= 4) return Strength.VERY_STRONG;
        if (len >= 12 && types >= 3) return Strength.STRONG;
        return Strength.MEDIUM;
    }

    /**
     * Returns a score from 0 to 100.
     */
    public static int getScore(String password) {
        if (password == null || password.isEmpty()) return 0;

        int score = 0;
        int len = password.length();

        // Length scoring
        score += Math.min(len * 4, 40);

        // Character diversity
        int types = countCharTypes(password);
        score += types * 15;

        // Bonus for length
        if (len >= 16) score += 10;
        if (len >= 20) score += 10;

        return Math.min(score, 100);
    }

    /**
     * Counts how many character types are present (upper, lower, digit, special).
     */
    public static int countCharTypes(String password) {
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
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
}
