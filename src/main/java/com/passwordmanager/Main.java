package com.passwordmanager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.config.ThemeMode;
import com.passwordmanager.ui.LoginFrame;

import javax.swing.*;
import java.io.File;
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
            if (config.getTheme() == ThemeMode.DARK) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
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
