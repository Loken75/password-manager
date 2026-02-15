package com.passwordmanager.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PasswordValidator.
 */
class PasswordValidatorTest {

    @Test
    void validPassword() {
        assertTrue(PasswordValidator.validate("Abcdefgh123!".toCharArray()));
    }

    @Test
    void tooShort() {
        assertFalse(PasswordValidator.validate("Abc123!".toCharArray()));
    }

    @Test
    void exactlyMinLength() {
        assertTrue(PasswordValidator.validate("Abcdefgh12!@".toCharArray()));
    }

    @Test
    void missingUppercase() {
        assertFalse(PasswordValidator.validate("abcdefgh123!".toCharArray()));
    }

    @Test
    void missingLowercase() {
        assertFalse(PasswordValidator.validate("ABCDEFGH123!".toCharArray()));
    }

    @Test
    void missingDigit() {
        assertFalse(PasswordValidator.validate("Abcdefghijk!".toCharArray()));
    }

    @Test
    void missingSpecial() {
        assertFalse(PasswordValidator.validate("Abcdefgh1234".toCharArray()));
    }

    @Test
    void nullPassword() {
        assertFalse(PasswordValidator.validate(null));
    }

    @Test
    void emptyPassword() {
        assertFalse(PasswordValidator.validate(new char[0]));
    }
}
