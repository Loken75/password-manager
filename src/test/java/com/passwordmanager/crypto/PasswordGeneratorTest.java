package com.passwordmanager.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PasswordGenerator.
 */
class PasswordGeneratorTest {

    @Test
    void testGenerateDefaultLength() {
        char[] pwd = PasswordGenerator.generate(16, true, true, true, true, false);
        assertEquals(16, pwd.length);
    }

    @Test
    void testMinimumLength() {
        char[] pwd = PasswordGenerator.generate(4, true, true, true, true, false);
        assertEquals(8, pwd.length); // Clamped to minimum 8
    }

    @Test
    void testContainsAllTypes() {
        for (int i = 0; i < 20; i++) {
            char[] pwd = PasswordGenerator.generate(20, true, true, true, true, false);
            boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
            for (char c : pwd) {
                if (Character.isUpperCase(c)) hasUpper = true;
                else if (Character.isLowerCase(c)) hasLower = true;
                else if (Character.isDigit(c)) hasDigit = true;
                else hasSpecial = true;
            }
            assertTrue(hasUpper, "Should contain uppercase");
            assertTrue(hasLower, "Should contain lowercase");
            assertTrue(hasDigit, "Should contain digit");
            assertTrue(hasSpecial, "Should contain special");
        }
    }

    @Test
    void testOnlyLowercase() {
        char[] pwd = PasswordGenerator.generate(16, false, true, false, false, false);
        for (char c : pwd) {
            assertTrue(Character.isLowerCase(c), "Should be lowercase: " + c);
        }
    }

    @Test
    void testExcludeAmbiguous() {
        for (int i = 0; i < 50; i++) {
            char[] pwd = PasswordGenerator.generate(32, true, true, true, false, true);
            String pwdStr = new String(pwd);
            assertFalse(pwdStr.contains("O"), "Should not contain O");
            assertFalse(pwdStr.contains("0"), "Should not contain 0");
            assertFalse(pwdStr.contains("l"), "Should not contain l");
            assertFalse(pwdStr.contains("I"), "Should not contain I");
        }
    }
}
