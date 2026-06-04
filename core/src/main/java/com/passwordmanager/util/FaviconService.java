package com.passwordmanager.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Service for fetching and caching website favicons.
 *
 * <p>Privacy: favicons are fetched DIRECTLY from each site's own
 * {@code /favicon.ico} over HTTPS, never via a third-party favicon resolver.
 * This avoids disclosing the user's stored domains to any party other than the
 * site they already use. A disk-based cache with 7-day expiry limits requests.
 */
public class FaviconService {

    private static final int TIMEOUT_MS = 5000;
    private static final long CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000; // 7 days
    /** Cap on favicon download size to avoid caching oversized/garbage responses. */
    private static final int MAX_FAVICON_BYTES = 256 * 1024;

    private final String cacheDirectory;

    public FaviconService(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        File dir = new File(cacheDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Fetches the favicon for the given URL, using cache if available and fresh.
     *
     * @param url the website URL
     * @return PNG bytes of the favicon, or null on failure
     */
    public byte[] getFavicon(String url) {
        if (url == null || url.isEmpty()) return null;

        String domain = extractDomain(url);
        if (domain == null || domain.isEmpty()) return null;

        // Check cache first
        byte[] cached = getCachedFavicon(url);
        if (cached != null) return cached;

        // Fetch directly from the site's own favicon over HTTPS (no third party).
        try {
            URL favicon = new URL("https://" + domain + "/favicon.ico");
            HttpURLConnection conn = (HttpURLConnection) favicon.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "PasswordManager-favicon");
            // HttpURLConnection refuses https->http redirects by default, so a
            // redirect cannot silently downgrade to an insecure transport.

            try {
                if (conn.getResponseCode() != 200) return null;

                byte[] data = readCapped(conn.getInputStream(), MAX_FAVICON_BYTES);
                if (data == null || !isLikelyImage(data)) return null;

                saveToCacheFile(domain, data);
                return data;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads up to {@code maxBytes} from the stream; returns null if the content
     * exceeds the cap (an oversized response is treated as invalid).
     */
    private static byte[] readCapped(InputStream in, int maxBytes) throws java.io.IOException {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            int total = 0;
            while ((bytesRead = stream.read(buffer)) != -1) {
                total += bytesRead;
                if (total > maxBytes) return null;
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        }
    }

    /**
     * Heuristically validates that the bytes are a known image format
     * (ICO/CUR, PNG, GIF, JPEG, BMP, WEBP) so that HTML error pages served at
     * {@code /favicon.ico} are not cached as icons. Package-private for testing.
     */
    static boolean isLikelyImage(byte[] d) {
        if (d == null || d.length < 4) return false;
        int b0 = d[0] & 0xFF, b1 = d[1] & 0xFF, b2 = d[2] & 0xFF, b3 = d[3] & 0xFF;
        if (b0 == 0x00 && b1 == 0x00 && (b2 == 0x01 || b2 == 0x02) && b3 == 0x00) return true; // ICO/CUR
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return true;                 // PNG
        if (b0 == 'G' && b1 == 'I' && b2 == 'F') return true;                                  // GIF
        if (b0 == 0xFF && b1 == 0xD8) return true;                                              // JPEG
        if (b0 == 'B' && b1 == 'M') return true;                                               // BMP
        if (b0 == 'R' && b1 == 'I' && b2 == 'F' && b3 == 'F') return true;                     // RIFF/WEBP
        return false;
    }

    /**
     * Returns the cached favicon for the given URL without making any network request.
     *
     * @param url the website URL
     * @return PNG bytes from cache, or null if not cached or expired
     */
    public byte[] getCachedFavicon(String url) {
        if (url == null || url.isEmpty()) return null;

        String domain = extractDomain(url);
        if (domain == null || domain.isEmpty()) return null;

        try {
            String filename = hashFilename(domain);
            File cacheFile = new File(cacheDirectory, filename);

            if (!cacheFile.exists()) return null;

            // Check expiry
            long age = System.currentTimeMillis() - cacheFile.lastModified();
            if (age > CACHE_MAX_AGE_MS) {
                cacheFile.delete();
                return null;
            }

            // Read from cache
            try (FileInputStream fis = new FileInputStream(cacheFile);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts the domain from a URL string.
     *
     * @param url the URL to extract the domain from
     * @return the domain, or null if extraction fails
     */
    public static String extractDomain(String url) {
        if (url == null || url.isEmpty()) return null;

        String working = url.trim();

        // Remove protocol
        int protoIdx = working.indexOf("://");
        if (protoIdx >= 0) {
            working = working.substring(protoIdx + 3);
        }

        // Remove path, query, and fragment
        int slashIdx = working.indexOf('/');
        if (slashIdx >= 0) {
            working = working.substring(0, slashIdx);
        }
        int queryIdx = working.indexOf('?');
        if (queryIdx >= 0) {
            working = working.substring(0, queryIdx);
        }
        int fragIdx = working.indexOf('#');
        if (fragIdx >= 0) {
            working = working.substring(0, fragIdx);
        }

        // Remove port
        int portIdx = working.lastIndexOf(':');
        if (portIdx >= 0) {
            working = working.substring(0, portIdx);
        }

        // Remove userinfo (user:pass@)
        int atIdx = working.indexOf('@');
        if (atIdx >= 0) {
            working = working.substring(atIdx + 1);
        }

        return working.isEmpty() ? null : working;
    }

    /**
     * Generates a SHA-256 hashed filename for the given domain.
     */
    private String hashFilename(String domain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(domain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback: use domain directly (sanitized)
            return domain.replaceAll("[^a-zA-Z0-9.-]", "_");
        }
    }

    /**
     * Saves favicon data to the disk cache.
     */
    private void saveToCacheFile(String domain, byte[] data) {
        try {
            String filename = hashFilename(domain);
            File cacheFile = new File(cacheDirectory, filename);
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(data);
            }
        } catch (Exception e) {
            // Cache write failure is non-fatal
        }
    }
}
