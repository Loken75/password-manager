package com.passwordmanager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.config.ThemeMode;
import com.passwordmanager.ui.LoginFrame;

import javax.swing.*;

/**
 * Application entry point. Sets up FlatLaf look & feel and shows the login screen.
 */
public class Main {
    public static void main(String[] args) {
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
}
