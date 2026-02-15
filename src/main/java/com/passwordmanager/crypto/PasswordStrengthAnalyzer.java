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
        if (len >= 16 && types >= 4) return Strength.VERY_STRONG;
        if (len >= 12 && types >= 3) return Strength.STRONG;
        return Strength.MEDIUM;
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

        return Math.min(score, 100);
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
