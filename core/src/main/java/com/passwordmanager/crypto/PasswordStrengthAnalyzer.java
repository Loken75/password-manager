package com.passwordmanager.crypto;

import com.passwordmanager.util.SecureWiper;

/**
 * Analyzes password strength with both char[] (preferred) and String overloads.
 */
public class PasswordStrengthAnalyzer {

    public enum Strength {
        WEAK, MEDIUM, STRONG, VERY_STRONG
    }

    /** Minimum acceptable password length. */
    private static final int MIN_LENGTH = 8;
    /** Threshold for detecting sequential or repeated character patterns. */
    private static final int PATTERN_THRESHOLD = 4;
    /** Effective-length penalty per detected weak pattern. */
    private static final int PATTERN_PENALTY_FACTOR = 4;
    /** Minimum effective length for strong classification. */
    private static final int STRONG_MIN_LENGTH = 12;
    /** Minimum effective length for very-strong classification. */
    private static final int VERY_STRONG_MIN_LENGTH = 16;
    /** Length bonus threshold (first tier). */
    private static final int SCORE_BONUS_LENGTH_1 = 16;
    /** Length bonus threshold (second tier). */
    private static final int SCORE_BONUS_LENGTH_2 = 20;
    /** Points awarded per character (capped). */
    private static final int SCORE_PER_CHAR = 4;
    /** Maximum points from character length. */
    private static final int SCORE_MAX_LENGTH_POINTS = 40;
    /** Points awarded per character type (upper, lower, digit, special). */
    private static final int SCORE_PER_TYPE = 15;
    /** Bonus/penalty points for length tiers and pattern detection. */
    private static final int SCORE_BONUS = 10;
    /** Score penalty per detected pattern. */
    private static final int SCORE_PATTERN_PENALTY = 15;

    public static Strength analyze(char[] password) {
        if (password == null || password.length == 0) return Strength.WEAK;

        int len = password.length;
        int types = countCharTypes(password);

        if (len < MIN_LENGTH || types <= 1) return Strength.WEAK;

        // Penalize weak patterns
        int penalty = 0;
        if (hasSequentialChars(password, PATTERN_THRESHOLD)) penalty++;
        if (hasRepeatedChars(password, PATTERN_THRESHOLD)) penalty++;
        if (isAllSameCase(password)) penalty++;

        int effectiveTypes = Math.max(1, types - penalty);
        int effectiveLen = len;
        if (penalty > 0) effectiveLen = Math.max(MIN_LENGTH, effectiveLen - penalty * PATTERN_PENALTY_FACTOR);

        if (effectiveLen >= VERY_STRONG_MIN_LENGTH && effectiveTypes >= 4) return Strength.VERY_STRONG;
        if (effectiveLen >= STRONG_MIN_LENGTH && effectiveTypes >= 3) return Strength.STRONG;
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

        score += Math.min(len * SCORE_PER_CHAR, SCORE_MAX_LENGTH_POINTS);

        int types = countCharTypes(password);
        score += types * SCORE_PER_TYPE;

        if (len >= SCORE_BONUS_LENGTH_1) score += SCORE_BONUS;
        if (len >= SCORE_BONUS_LENGTH_2) score += SCORE_BONUS;

        // Penalize detected patterns
        if (hasSequentialChars(password, PATTERN_THRESHOLD)) score -= SCORE_PATTERN_PENALTY;
        if (hasRepeatedChars(password, PATTERN_THRESHOLD)) score -= SCORE_PATTERN_PENALTY;

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
