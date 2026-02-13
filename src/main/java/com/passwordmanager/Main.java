package com.passwordmanager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.ui.LoginFrame;

import javax.swing.*;

/**
 * Application entry point. Sets up FlatLaf look & feel and shows the login screen.
 */
public class Main {
    public static void main(String[] args) {
        // Load config to determine theme
        ConfigManager configManager = new ConfigManager();
        AppConfig config = configManager.loadConfig();

        // Set Look & Feel
        try {
            if ("dark".equals(config.getTheme())) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
        } catch (Exception e) {
            // Fallback to system L&F
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // ignore
            }
        }

        // Launch UI on EDT
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new LoginFrame().setVisible(true);
            }
        });
    }
}
