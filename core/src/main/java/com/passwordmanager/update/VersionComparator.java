package com.passwordmanager.update;

/**
 * Compares semantic version strings (major.minor.patch).
 */
public final class VersionComparator {

    private VersionComparator() {}

    /**
     * Compares two semantic version strings.
     * @return negative if v1 < v2, 0 if equal, positive if v1 > v2
     */
    public static int compare(String v1, String v2) {
        int[] a = parse(v1);
        int[] b = parse(v2);
        for (int i = 0; i < 3; i++) {
            int diff = a[i] - b[i];
            if (diff != 0) return diff;
        }
        return 0;
    }

    /**
     * Returns true if candidate is newer than current.
     * Pre-release versions (e.g. "2.0.0-rc1") are not considered newer
     * than the same base version.
     */
    public static boolean isNewer(String candidate, String current) {
        int cmp = compare(candidate, current);
        if (cmp > 0) return true;
        if (cmp < 0) return false;
        // Same base version: pre-release candidate is not newer
        return false;
    }

    /**
     * Returns true if the version string contains a pre-release suffix (e.g. "-rc1", "-beta").
     */
    public static boolean isPreRelease(String version) {
        if (version == null) return false;
        String v = version.startsWith("v") || version.startsWith("V") ? version.substring(1) : version;
        return v.contains("-");
    }

    private static int[] parse(String version) {
        int[] parts = {0, 0, 0};
        if (version == null || version.isEmpty()) return parts;
        // Strip leading 'v' or 'V'
        String v = version.startsWith("v") || version.startsWith("V") ? version.substring(1) : version;
        // Strip pre-release suffix (e.g. "2.0.0-rc1" -> "2.0.0")
        int hyphen = v.indexOf('-');
        if (hyphen >= 0) v = v.substring(0, hyphen);
        String[] tokens = v.split("\\.");
        for (int i = 0; i < Math.min(tokens.length, 3); i++) {
            try {
                parts[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException ignored) {
                // keep 0
            }
        }
        return parts;
    }
}
