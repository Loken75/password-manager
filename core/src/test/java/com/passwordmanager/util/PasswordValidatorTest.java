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

    // --- Common password rejection tests ---

    @Test
    void rejectsExactCommonPassword() {
        // "password" padded to meet length+complexity is still rejected via alpha-base extraction
        assertFalse(PasswordValidator.validate("Password1234!".toCharArray()));
    }

    @Test
    void rejectsCommonPasswordVariant() {
        // "letmein" with added digits/special to meet complexity rules
        assertFalse(PasswordValidator.validate("Letmein12345!".toCharArray()));
    }

    @Test
    void rejectsCommonPasswordWithExtraChars() {
        // "dragon" extracted from alpha base
        assertFalse(PasswordValidator.validate("Dragon123456!".toCharArray()));
    }

    @Test
    void rejectsMasterVariant() {
        // "master" is in the common list
        assertFalse(PasswordValidator.validate("Master123456!".toCharArray()));
    }

    @Test
    void rejectsMotDePasseFrench() {
        // "motdepasse" is in the common list
        assertFalse(PasswordValidator.validate("Motdepasse12!".toCharArray()));
    }

    @Test
    void acceptsNonCommonPassword() {
        // Not in the common password list
        assertTrue(PasswordValidator.validate("Xk9$mPz4!wQ2vR".toCharArray()));
    }
}
