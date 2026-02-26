package com.passwordmanager.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the application version at runtime.
 * Reads from version.properties generated at build time.
 */
public final class AppVersion {

    private static final String VERSION;

    static {
        String v = "dev";
        try (InputStream is = AppVersion.class.getClassLoader().getResourceAsStream("version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                v = props.getProperty("app.version", "dev");
            }
        } catch (IOException ignored) {
            // keep "dev" as fallback
        }
        VERSION = v;
    }

    private AppVersion() {}

    /** Returns the application version (e.g. "1.2.0"). */
    public static String get() {
        return VERSION;
    }

    /** Returns a display string like "V1.2.0". */
    public static String display() {
        return "V" + VERSION;
    }
}
