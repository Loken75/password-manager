package com.passwordmanager.crypto;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for PasswordStrengthAnalyzer.
 */
public class PasswordStrengthAnalyzerTest {

    @Test
    public void testWeak_Short() {
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze("abc"));
    }

    @Test
    public void testWeak_SingleType() {
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze("abcdefghij"));
    }

    @Test
    public void testMedium() {
        assertEquals(PasswordStrengthAnalyzer.Strength.MEDIUM, PasswordStrengthAnalyzer.analyze("Abcdefgh1"));
    }

    @Test
    public void testStrong() {
        assertEquals(PasswordStrengthAnalyzer.Strength.STRONG, PasswordStrengthAnalyzer.analyze("Abcdefgh123!"));
    }

    @Test
    public void testVeryStrong() {
        assertEquals(PasswordStrengthAnalyzer.Strength.VERY_STRONG, PasswordStrengthAnalyzer.analyze("Abcdefgh1234!@#$"));
    }

    @Test
    public void testNullAndEmpty() {
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze(null));
        assertEquals(PasswordStrengthAnalyzer.Strength.WEAK, PasswordStrengthAnalyzer.analyze(""));
    }

    @Test
    public void testScoreRange() {
        int score = PasswordStrengthAnalyzer.getScore("Abcdefgh1234!@#$");
        assertTrue("Score should be >= 0", score >= 0);
        assertTrue("Score should be <= 100", score <= 100);
    }

    @Test
    public void testCountCharTypes() {
        assertEquals(1, PasswordStrengthAnalyzer.countCharTypes("abcdef"));
        assertEquals(2, PasswordStrengthAnalyzer.countCharTypes("Abcdef"));
        assertEquals(3, PasswordStrengthAnalyzer.countCharTypes("Abcdef1"));
        assertEquals(4, PasswordStrengthAnalyzer.countCharTypes("Abcdef1!"));
    }
}
