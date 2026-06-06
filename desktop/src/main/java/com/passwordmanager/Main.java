package com.passwordmanager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
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

        // Apply the design-system FlatLaf theme (generated from docs/design-system/tokens.json).
        // Registered once; persists across runtime theme switches.
        FlatLaf.registerCustomDefaultsSource("com.passwordmanager.ui.theme");

        try {
            boolean dark;
            // Optional override for testing/preview: -Dpm.theme=dark|light|system
            String themeOverride = System.getProperty("pm.theme");
            if (themeOverride != null) {
                switch (themeOverride.toLowerCase()) {
                    case "dark":   dark = true; break;
                    case "light":  dark = false; break;
                    default:        dark = isSystemDark(); break;
                }
            } else {
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
            }
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // ignore
            }
        }

        if (Boolean.getBoolean("pm.demo")) {
            SwingUtilities.invokeLater(() -> launchDemo(config, configManager));
        } else {
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }

    /**
     * Preview/testing only (-Dpm.demo=true): opens the main window directly on a throwaway
     * in-memory demo vault, bypassing login. Never triggered in normal use.
     */
    private static void launchDemo(AppConfig config, ConfigManager configManager) {
        try {
            File dir = java.nio.file.Files.createTempDirectory("pm-demo").toFile();
            char[] master = "Demo!Pass123_X".toCharArray();
            com.passwordmanager.vault.VaultManager vm =
                new com.passwordmanager.vault.VaultManager(dir.getAbsolutePath());
            java.util.List<String> cats = java.util.List.of("Travail", "Banque", "Réseaux sociaux", "Autre");
            vm.createVault("demo", master.clone(), cats);
            com.passwordmanager.vault.VaultLoadResult r = vm.loadVault("demo", master.clone());
            com.passwordmanager.vault.Vault vault = r.getVault();
            com.passwordmanager.vault.VaultService vs = new com.passwordmanager.vault.VaultService(vault);
            vs.addEntry(new com.passwordmanager.vault.PasswordEntry("github.com", "alice", "alice@example.com",
                "Tr0ub4dour&3xpl0it!Zq".toCharArray(), "https://github.com", "2FA activé", "Travail", java.util.List.of("dev")));
            vs.addEntry(new com.passwordmanager.vault.PasswordEntry("banque-x", "alice", null,
                "1234".toCharArray(), "https://banque-x.fr", null, "Banque", null));
            vs.addEntry(new com.passwordmanager.vault.PasswordEntry("reddit.com", "a_l", null,
                "redditpass1".toCharArray(), "https://reddit.com", null, "Réseaux sociaux", null));
            vs.addEntry(new com.passwordmanager.vault.PasswordEntry("proton.me", "alice", "alice@example.com",
                "S3cure&Vault!2026x".toCharArray(), "https://proton.me", null, "Travail", null));
            vault.addAppEntry(new com.passwordmanager.vault.AppEntry("Netflix", "alice_home", "4821".toCharArray(), "Compte familial"));
            config.setLocalVaultDirectory(dir.getAbsolutePath());
            // Align config theme with the -Dpm.theme override so live theme/language changes stay consistent.
            String themeOverride = System.getProperty("pm.theme");
            if ("dark".equalsIgnoreCase(themeOverride)) config.setTheme(com.passwordmanager.config.ThemeMode.DARK);
            else if ("light".equalsIgnoreCase(themeOverride)) config.setTheme(com.passwordmanager.config.ThemeMode.LIGHT);
            new com.passwordmanager.ui.MainFrame(vault, "demo", r.getSession(), vm, config, configManager).setVisible(true);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Demo launch failed", e);
            new LoginFrame().setVisible(true);
        }
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
