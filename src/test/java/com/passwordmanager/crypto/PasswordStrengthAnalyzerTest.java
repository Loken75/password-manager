package com.passwordmanager.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PasswordStrengthAnalyzer (both String and char[] overloads).
 */
class PasswordStrengthAnalyzerTest {

    @Test
    void weakShortPassword() {
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze("abc"));
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze("abc".toCharArray()));
    }

    @Test
    void weakSingleType() {
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze("abcdefghij"));
    }

    @Test
    void medium() {
        assertEquals(PasswordStrengthAnalyzer.Strength.MEDIUM, PasswordStrengthAnalyzer.analyze("Abcdefgh1"));
    }

    @Test
    void strong() {
        assertEquals(PasswordStrengthAnalyzer.Strength.STRONG, PasswordStrengthAnalyzer.analyze("Kx9m!pRwZn3q"));
    }

    @Test
    void veryStrong() {
        assertEquals(PasswordStrengthAnalyzer.Strength.VERY_STRONG, PasswordStrengthAnalyzer.analyze("Kx9m!pRwZn3q@Lf7"));
    }

    @Test
    void nullAndEmpty() {
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze((String) null));
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze(""));
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze((char[]) null));
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze(new char[0]));
    }

    @Test
    void scoreRange() {
        int score = PasswordStrengthAnalyzer.getScore("Kx9m!pRwZn3q@Lf7");
        assertTrue(score >= 0, "Score should be >= 0");
        assertTrue(score <= 100, "Score should be <= 100");

        int scoreChar = PasswordStrengthAnalyzer.getScore("Kx9m!pRwZn3q@Lf7".toCharArray());
        assertEquals(score, scoreChar);
    }

    @Test
    void countCharTypes() {
        assertEquals(1, PasswordStrengthAnalyzer.countCharTypes("abcdef"));
        assertEquals(2, PasswordStrengthAnalyzer.countCharTypes("Abcdef"));
        assertEquals(3, PasswordStrengthAnalyzer.countCharTypes("Abcdef1"));
        assertEquals(4, PasswordStrengthAnalyzer.countCharTypes("Abcdef1!"));
    }

    @Test
    void charArrayAndStringProduceSameResults() {
        String pwd = "Tx9m!pRw3qZn";
        char[] chars = pwd.toCharArray();

        assertEquals(PasswordStrengthAnalyzer.analyze(pwd), PasswordStrengthAnalyzer.analyze(chars));
        assertEquals(PasswordStrengthAnalyzer.getScore(pwd), PasswordStrengthAnalyzer.getScore(chars));
        assertEquals(PasswordStrengthAnalyzer.countCharTypes(pwd), PasswordStrengthAnalyzer.countCharTypes(chars));
    }
}
