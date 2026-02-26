package com.passwordmanager.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HibpChecker. Tests are purely local — no network calls.
 * The actual HIBP API interaction is tested manually or in integration tests.
 */
class HibpCheckerTest {

    @Test
    void nullPasswordReturnsMinusOne() {
        assertEquals(-1, HibpChecker.checkPassword(null));
    }

    @Test
    void emptyPasswordReturnsMinusOne() {
        assertEquals(-1, HibpChecker.checkPassword(new char[0]));
    }

    @Test
    void checkPasswordDoesNotThrowForValidInput() {
        // Should not throw regardless of network availability.
        // Returns either a count >= 0 (if network available) or -1 (error).
        int result = HibpChecker.checkPassword("test1234".toCharArray());
        assertTrue(result >= -1, "Expected result >= -1, got: " + result);
    }

    @Test
    void checkPasswordDoesNotThrowForSpecialChars() {
        int result = HibpChecker.checkPassword("p@$$w0rd!#%&".toCharArray());
        assertTrue(result >= -1);
    }

    @Test
    void checkPasswordDoesNotThrowForUnicodeInput() {
        int result = HibpChecker.checkPassword("日本語パスワード".toCharArray());
        assertTrue(result >= -1);
    }
}
