package com.passwordmanager.security;

import com.passwordmanager.util.SecureWiper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Checks passwords against the Have I Been Pwned (HIBP) API
 * using the k-Anonymity model to preserve privacy.
 *
 * Only the first 5 characters of the SHA-1 hash are sent to the API.
 */
public class HibpChecker {

    private static final String HIBP_API_URL = "https://api.pwnedpasswords.com/range/";
    private static final int TIMEOUT_MS = 5000;
    private static final String USER_AGENT = "PasswordManager/1.0";

    private HibpChecker() {}

    /**
     * Checks whether the given password has appeared in known data breaches.
     *
     * @param password the password to check (as char[] to avoid String interning)
     * @return the number of times the password was found in breaches,
     *         0 if not found (safe), or -1 on error
     */
    public static int checkPassword(char[] password) {
        if (password == null || password.length == 0) return -1;

        byte[] bytes = null;
        try {
            // Convert char[] to bytes via UTF-8 without creating a String
            ByteBuffer bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
            bytes = new byte[bb.remaining()];
            bb.get(bytes);
            if (bb.hasArray()) {
                SecureWiper.wipe(bb.array());
            }

            // SHA-1 hash
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = md.digest(bytes);
            char[] hexChars = bytesToHexChars(hashBytes);
            SecureWiper.wipe(hashBytes);

            // k-Anonymity: split into prefix (5 chars) and suffix
            char[] prefix = new char[5];
            char[] suffix = new char[hexChars.length - 5];
            System.arraycopy(hexChars, 0, prefix, 0, 5);
            System.arraycopy(hexChars, 5, suffix, 0, suffix.length);
            SecureWiper.wipe(hexChars);

            try {
                // Query the HIBP API
                URL url = new URL(HIBP_API_URL + new String(prefix));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", USER_AGENT);

                String suffixStr = new String(suffix);
                try {
                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) return -1;

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // Response format: "SUFFIX:COUNT"
                            String[] parts = line.split(":");
                            if (parts.length == 2 && parts[0].trim().equalsIgnoreCase(suffixStr)) {
                                return Integer.parseInt(parts[1].trim());
                            }
                        }
                    }
                } finally {
                    conn.disconnect();
                }

                // Not found in breach database
                return 0;
            } finally {
                SecureWiper.wipe(prefix);
                SecureWiper.wipe(suffix);
            }
        } catch (Exception e) {
            return -1;
        } finally {
            SecureWiper.wipe(bytes);
        }
    }

    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

    /**
     * Converts a byte array to uppercase hexadecimal char[] without creating a String.
     */
    private static char[] bytesToHexChars(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_UPPER[v >>> 4];
            hex[i * 2 + 1] = HEX_UPPER[v & 0x0F];
        }
        return hex;
    }
}
