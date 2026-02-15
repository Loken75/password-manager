package com.passwordmanager.crypto;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Cryptographically secure password generator.
 */
public class PasswordGenerator {
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+[]{}|;:',.<>?/";
    private static final String AMBIGUOUS = "0O1lI";
    private static final SecureRandom random = new SecureRandom();

    /**
     * Generates a secure random password.
     *
     * @param length           password length (8-128)
     * @param useUpper         include uppercase letters
     * @param useLower         include lowercase letters
     * @param useDigits        include digits
     * @param useSpecial       include special characters
     * @param excludeAmbiguous exclude ambiguous characters (0/O, 1/l/I)
     * @return the generated password
     */
    public static char[] generate(int length, boolean useUpper, boolean useLower,
                                  boolean useDigits, boolean useSpecial, boolean excludeAmbiguous) {
        if (length < 8) length = 8;
        if (length > 128) length = 128;

        StringBuilder pool = new StringBuilder();
        List<String> requiredSets = new ArrayList<String>();

        if (useUpper) {
            String chars = excludeAmbiguous ? removeAmbiguous(UPPER) : UPPER;
            pool.append(chars);
            requiredSets.add(chars);
        }
        if (useLower) {
            String chars = excludeAmbiguous ? removeAmbiguous(LOWER) : LOWER;
            pool.append(chars);
            requiredSets.add(chars);
        }
        if (useDigits) {
            String chars = excludeAmbiguous ? removeAmbiguous(DIGITS) : DIGITS;
            pool.append(chars);
            requiredSets.add(chars);
        }
        if (useSpecial) {
            pool.append(SPECIAL);
            requiredSets.add(SPECIAL);
        }

        if (pool.length() == 0) {
            pool.append(LOWER);
            requiredSets.add(LOWER);
        }

        char[] password = new char[length];

        // Ensure at least one character from each required set
        int idx = 0;
        for (String set : requiredSets) {
            if (idx < length) {
                password[idx++] = set.charAt(random.nextInt(set.length()));
            }
        }

        // Fill remaining positions
        String poolStr = pool.toString();
        for (int i = idx; i < length; i++) {
            password[i] = poolStr.charAt(random.nextInt(poolStr.length()));
        }

        // Shuffle using Fisher-Yates
        for (int i = length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }

        return password;
    }

    private static String removeAmbiguous(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (AMBIGUOUS.indexOf(c) == -1) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
