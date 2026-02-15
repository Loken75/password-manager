package com.passwordmanager.util;

import java.util.Arrays;

/**
 * Utility for securely zeroing sensitive data in memory.
 * Uses volatile reads after fill to prevent JIT dead-store elimination.
 */
public final class SecureWiper {

    private static volatile byte volatileByte;
    private static volatile char volatileChar;

    private SecureWiper() {}

    public static void wipe(byte[] data) {
        if (data != null && data.length > 0) {
            Arrays.fill(data, (byte) 0);
            volatileByte = data[0];
        }
    }

    public static void wipe(char[] data) {
        if (data != null && data.length > 0) {
            Arrays.fill(data, '\0');
            volatileChar = data[0];
        }
    }
}
