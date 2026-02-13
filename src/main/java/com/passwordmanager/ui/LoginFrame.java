package com.passwordmanager.ui;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.Vault;
import com.passwordmanager.vault.VaultManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

/**
 * Login screen: user selection, master password, user creation, language switch.
 */
public class LoginFrame extends JFrame {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final ConfigManager configManager = new ConfigManager();
    private AppConfig appConfig;
    private VaultManager vaultManager;

    private JComboBox<String> userCombo;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton createUserButton;
    private JComboBox<String> languageCombo;
    private JLabel statusLabel;

    public LoginFrame() {
        appConfig = configManager.loadConfig();
        lang.setLanguage(appConfig.getLanguage());
        vaultManager = new VaultManager(appConfig.getLocalVaultDirectory());
        initComponents();
    }

    private void initComponents() {
        setTitle(lang.getString("app.title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        // Title
        JLabel titleLabel = new JLabel(lang.getString("app.title"));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(5));

        JLabel subtitleLabel = new JLabel(lang.getString("app.version"));
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(subtitleLabel);
        mainPanel.add(Box.createVerticalStrut(30));

        // User selection
        JPanel userPanel = new JPanel(new BorderLayout(10, 0));
        userPanel.setMaximumSize(new Dimension(350, 30));
        JLabel userLabel = new JLabel(lang.getString("login.username"));
        userLabel.setPreferredSize(new Dimension(140, 25));
        userCombo = new JComboBox<String>();
        refreshUserList();
        userPanel.add(userLabel, BorderLayout.WEST);
        userPanel.add(userCombo, BorderLayout.CENTER);
        mainPanel.add(userPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Password
        JPanel passPanel = new JPanel(new BorderLayout(10, 0));
        passPanel.setMaximumSize(new Dimension(350, 30));
        JLabel passLabel = new JLabel(lang.getString("login.password"));
        passLabel.setPreferredSize(new Dimension(140, 25));
        passwordField = new JPasswordField();
        passPanel.add(passLabel, BorderLayout.WEST);
        passPanel.add(passwordField, BorderLayout.CENTER);
        mainPanel.add(passPanel);
        mainPanel.add(Box.createVerticalStrut(20));

        // Login button
        loginButton = new JButton(lang.getString("login.button"));
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(350, 38));
        loginButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        mainPanel.add(loginButton);
        mainPanel.add(Box.createVerticalStrut(10));

        // Create user link
        createUserButton = new JButton(lang.getString("login.create_user"));
        createUserButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        createUserButton.setBorderPainted(false);
        createUserButton.setContentAreaFilled(false);
        createUserButton.setForeground(new Color(0, 102, 204));
        createUserButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        mainPanel.add(createUserButton);
        mainPanel.add(Box.createVerticalStrut(15));

        // Status
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(statusLabel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Language selector
        JPanel langPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        langPanel.add(new JLabel(lang.getString("settings.language") + " :"));
        languageCombo = new JComboBox<String>(new String[]{"Fran\u00e7ais", "English"});
        languageCombo.setSelectedIndex("en".equals(appConfig.getLanguage()) ? 1 : 0);
        langPanel.add(languageCombo);
        mainPanel.add(langPanel);

        setContentPane(mainPanel);

        // -- Actions --
        loginButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { doLogin(); }
        });
        passwordField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { doLogin(); }
        });
        createUserButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { doCreateUser(); }
        });
        languageCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String newLang = languageCombo.getSelectedIndex() == 0 ? "fr" : "en";
                if (!newLang.equals(appConfig.getLanguage())) {
                    appConfig.setLanguage(newLang);
                    configManager.saveConfig(appConfig);
                    lang.setLanguage(newLang);
                    dispose();
                    new LoginFrame().setVisible(true);
                }
            }
        });

        pack();
        setMinimumSize(new Dimension(450, 420));
        setLocationRelativeTo(null);
    }

    private void refreshUserList() {
        userCombo.removeAllItems();
        for (String u : vaultManager.listUsers()) {
            userCombo.addItem(u);
        }
    }

    private void doLogin() {
        String username = (String) userCombo.getSelectedItem();
        char[] password = passwordField.getPassword();
        if (username == null || username.isEmpty()) {
            statusLabel.setText(lang.getString("login.no_user"));
            return;
        }
        if (password.length == 0) {
            statusLabel.setText(lang.getString("error.empty_password"));
            return;
        }
        try {
            Vault vault = vaultManager.loadVault(username, password);
            statusLabel.setText("");
            dispose();
            new MainFrame(vault, username, password, vaultManager, appConfig, configManager).setVisible(true);
        } catch (Exception ex) {
            statusLabel.setText(lang.getString("error.invalid_password"));
            passwordField.setText("");
        }
    }

    private void doCreateUser() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        JTextField usernameField = new JTextField();
        JPasswordField pass1 = new JPasswordField();
        JPasswordField pass2 = new JPasswordField();

        panel.add(new JLabel(lang.getString("login.new_username")));
        panel.add(usernameField);
        panel.add(new JLabel(lang.getString("security.new_password")));
        panel.add(pass1);
        panel.add(new JLabel(lang.getString("security.confirm_password")));
        panel.add(pass2);
        panel.add(new JLabel(" "));
        panel.add(new JLabel("<html><font color='red'><b>" + lang.getString("security.no_recovery") + "</b></font></html>"));
        panel.add(new JLabel("<html><i>" + lang.getString("security.password_requirements") + "</i></html>"));

        int result = JOptionPane.showConfirmDialog(this, panel,
            lang.getString("login.create_user"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String newUser = usernameField.getText().trim();
            char[] p1 = pass1.getPassword();
            char[] p2 = pass2.getPassword();

            try {
                if (newUser.isEmpty()) {
                    showError(lang.getString("error.empty_username"));
                    return;
                }
                if (!newUser.matches("[a-zA-Z0-9_]+")) {
                    showError(lang.getString("error.invalid_username"));
                    return;
                }
                if (vaultManager.vaultExists(newUser)) {
                    showError(lang.getString("error.user_exists"));
                    return;
                }
                if (!Arrays.equals(p1, p2)) {
                    showError(lang.getString("security.password_mismatch"));
                    return;
                }
                if (!validateMasterPassword(p1)) {
                    showError(lang.getString("security.password_requirements"));
                    return;
                }
                vaultManager.createVault(newUser, p1);
                refreshUserList();
                userCombo.setSelectedItem(newUser);
                JOptionPane.showMessageDialog(this, lang.getString("login.user_created"),
                    lang.getString("common.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showError(ex.getMessage());
            } finally {
                Arrays.fill(p1, ' ');
                Arrays.fill(p2, ' ');
            }
        }
    }

    private boolean validateMasterPassword(char[] password) {
        if (password.length < 12) return false;
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
