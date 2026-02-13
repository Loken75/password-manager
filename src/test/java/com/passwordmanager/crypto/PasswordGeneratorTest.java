package com.passwordmanager.crypto;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for PasswordGenerator.
 */
public class PasswordGeneratorTest {

    @Test
    public void testGenerateDefaultLength() {
        String pwd = PasswordGenerator.generate(16, true, true, true, true, false);
        assertEquals(16, pwd.length());
    }

    @Test
    public void testMinimumLength() {
        String pwd = PasswordGenerator.generate(4, true, true, true, true, false);
        assertEquals(8, pwd.length()); // Clamped to minimum 8
    }

    @Test
    public void testContainsAllTypes() {
        // Generate many times to ensure all types are included
        for (int i = 0; i < 20; i++) {
            String pwd = PasswordGenerator.generate(20, true, true, true, true, false);
            boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
            for (char c : pwd.toCharArray()) {
                if (Character.isUpperCase(c)) hasUpper = true;
                else if (Character.isLowerCase(c)) hasLower = true;
                else if (Character.isDigit(c)) hasDigit = true;
                else hasSpecial = true;
            }
            assertTrue("Should contain uppercase", hasUpper);
            assertTrue("Should contain lowercase", hasLower);
            assertTrue("Should contain digit", hasDigit);
            assertTrue("Should contain special", hasSpecial);
        }
    }

    @Test
    public void testOnlyLowercase() {
        String pwd = PasswordGenerator.generate(16, false, true, false, false, false);
        for (char c : pwd.toCharArray()) {
            assertTrue("Should be lowercase: " + c, Character.isLowerCase(c));
        }
    }

    @Test
    public void testExcludeAmbiguous() {
        for (int i = 0; i < 50; i++) {
            String pwd = PasswordGenerator.generate(32, true, true, true, false, true);
            assertFalse("Should not contain O", pwd.contains("O"));
            assertFalse("Should not contain 0", pwd.contains("0"));
            assertFalse("Should not contain l", pwd.contains("l"));
            assertFalse("Should not contain I", pwd.contains("I"));
        }
    }
}
