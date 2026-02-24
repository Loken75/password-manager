package com.passwordmanager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.config.ThemeMode;
import com.passwordmanager.ui.LoginFrame;

import javax.swing.*;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application entry point. Sets up FlatLaf look & feel and shows the login screen.
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        detectAppDirectory();
        ConfigManager configManager = new ConfigManager();
        AppConfig config = configManager.loadConfig();

        try {
            boolean dark;
            switch (config.getTheme()) {
                case DARK:
                    dark = true;
                    break;
                case SYSTEM:
                    dark = isSystemDark();
                    break;
                default:
                    dark = false;
                    break;
            }
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // ignore
            }
        }

        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    /**
     * Detects whether the OS is using a dark theme.
     * Checks AWT desktop property first (works on macOS / GNOME 42+ / KDE),
     * then falls back to the Windows registry key.
     */
    public static boolean isSystemDark() {
        try {
            Object appearance = Toolkit.getDefaultToolkit().getDesktopProperty("awt.appearance");
            if (appearance != null) {
                return "dark".equalsIgnoreCase(appearance.toString());
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not read awt.appearance", e);
        }

        // Windows: HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize -> AppsUseLightTheme (0 = dark)
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            try {
                Process proc = Runtime.getRuntime().exec(new String[]{
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"
                });
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("AppsUseLightTheme")) {
                            return line.trim().endsWith("0x0");
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Could not read Windows theme from registry", e);
            }
        }

        return false;
    }

    /**
     * Detects the application directory (where the JAR resides) and sets
     * the "app.home" system property. This allows config and vault files
     * to be stored relative to the application rather than in user.home.
     */
    static void detectAppDirectory() {
        try {
            // Priority 1: jpackage sets this when running as a native image
            String jpackagePath = System.getProperty("jpackage.app-path");
            if (jpackagePath != null && !jpackagePath.isEmpty()) {
                File appDir = new File(jpackagePath).getParentFile();
                if (appDir != null && appDir.isDirectory()) {
                    System.setProperty("app.home", appDir.getAbsolutePath());
                    return;
                }
            }

            // Priority 2: location of the running JAR
            URI codeLocation = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File codeFile = new File(codeLocation);
            if (codeFile.getName().endsWith(".jar")) {
                File jarDir = codeFile.getParentFile();
                if (jarDir != null && jarDir.isDirectory()) {
                    System.setProperty("app.home", jarDir.getAbsolutePath());
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not detect app directory from JAR location", e);
        }

        // Fallback: current working directory
        System.setProperty("app.home", System.getProperty("user.dir"));
    }
}
