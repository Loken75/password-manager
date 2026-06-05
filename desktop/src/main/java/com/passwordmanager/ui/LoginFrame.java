package com.passwordmanager.ui;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.AppVersion;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.update.DesktopUpdateManager;
import com.passwordmanager.util.PasswordValidator;
import com.passwordmanager.vault.VaultLoadResult;
import com.passwordmanager.vault.VaultManager;
import com.passwordmanager.vault.VaultStoreMigrator;
import com.passwordmanager.vault.store.FileVaultStore;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Login screen: user selection, master password, user creation, language switch.
 */
public class LoginFrame extends JFrame {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final ConfigManager configManager = new ConfigManager();
    private AppConfig appConfig;
    private VaultManager vaultManager;

    private JComboBox<String> userCombo;
    private JComboBox<String> workspaceCombo;
    private boolean updatingWorkspaceCombo = false;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton createUserButton;
    private JComboBox<String> languageCombo;
    private JLabel statusLabel;
    // Static intentionally: persists across LoginFrame instances (lock/unlock cycles).
    // Per-username tracking prevents one user's failures from affecting another.
    private static final Map<String, Integer> failedAttemptsMap = new HashMap<>();
    private Timer rateLimitTimer;

    public LoginFrame() {
        appConfig = configManager.loadConfig();
        lang.setLanguage(appConfig.getLanguage());
        vaultManager = new VaultManager(appConfig.getLocalVaultDirectory());
        vaultManager.getImporter().setDefaultImportCategory(lang.getString("category.default.other"));
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

        JLabel subtitleLabel = new JLabel(lang.getString("app.version").replace("{0}", AppVersion.get()));
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(subtitleLabel);
        mainPanel.add(Box.createVerticalStrut(30));

        // Working folder (workspace) selection
        JPanel workspacePanel = new JPanel(new BorderLayout(10, 0));
        workspacePanel.setMaximumSize(new Dimension(350, 30));
        JLabel workspaceLabel = new JLabel(lang.getString("login.workspace"));
        workspaceLabel.setPreferredSize(new Dimension(140, 25));
        workspaceCombo = new JComboBox<>();
        workspaceCombo.setToolTipText(appConfig.getLocalVaultDirectory());
        JButton browseWorkspaceBtn = new JButton("...");
        browseWorkspaceBtn.setMargin(new Insets(0, 6, 0, 6));
        browseWorkspaceBtn.setToolTipText(lang.getString("settings.change_workspace"));
        refreshWorkspaceCombo();
        JPanel workspaceRight = new JPanel(new BorderLayout(4, 0));
        workspaceRight.add(workspaceCombo, BorderLayout.CENTER);
        workspaceRight.add(browseWorkspaceBtn, BorderLayout.EAST);
        workspacePanel.add(workspaceLabel, BorderLayout.WEST);
        workspacePanel.add(workspaceRight, BorderLayout.CENTER);
        mainPanel.add(workspacePanel);
        mainPanel.add(Box.createVerticalStrut(15));

        workspaceCombo.addActionListener(e -> {
            if (updatingWorkspaceCombo) return;
            String selected = (String) workspaceCombo.getSelectedItem();
            if (selected != null && !selected.equals(appConfig.getLocalVaultDirectory())) {
                switchWorkspace(selected);
            }
        });
        browseWorkspaceBtn.addActionListener(e -> doBrowseWorkspace());

        // User selection
        JPanel userPanel = new JPanel(new BorderLayout(10, 0));
        userPanel.setMaximumSize(new Dimension(350, 30));
        JLabel userLabel = new JLabel(lang.getString("login.username"));
        userLabel.setPreferredSize(new Dimension(140, 25));
        userCombo = new JComboBox<>();
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
        mainPanel.add(Box.createVerticalStrut(5));

        // Show/hide password checkbox
        char echoChar = passwordField.getEchoChar();
        JCheckBox showPasswordCheck = new JCheckBox(lang.getString("entry.show_password"));
        showPasswordCheck.setAlignmentX(Component.CENTER_ALIGNMENT);
        showPasswordCheck.addActionListener(e -> {
            passwordField.setEchoChar(showPasswordCheck.isSelected() ? (char) 0 : echoChar);
        });
        mainPanel.add(showPasswordCheck);
        mainPanel.add(Box.createVerticalStrut(15));

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
        languageCombo = new JComboBox<>(new String[]{"Fran\u00e7ais", "English"});
        languageCombo.setSelectedIndex("en".equals(appConfig.getLanguage()) ? 1 : 0);
        langPanel.add(languageCombo);
        mainPanel.add(langPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Check for updates link
        JButton checkUpdateBtn = new JButton(lang.getString("update.check"));
        checkUpdateBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        checkUpdateBtn.setBorderPainted(false);
        checkUpdateBtn.setContentAreaFilled(false);
        checkUpdateBtn.setForeground(new Color(0, 102, 204));
        checkUpdateBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        checkUpdateBtn.addActionListener(e -> new DesktopUpdateManager().checkManually(this));
        mainPanel.add(checkUpdateBtn);

        setContentPane(mainPanel);

        // -- Actions --
        loginButton.addActionListener(e -> doLogin());
        passwordField.addActionListener(e -> doLogin());
        createUserButton.addActionListener(e -> doCreateUser());
        languageCombo.addActionListener(e -> {
            String newLang = languageCombo.getSelectedIndex() == 0 ? "fr" : "en";
            if (!newLang.equals(appConfig.getLanguage())) {
                appConfig.setLanguage(newLang);
                configManager.saveConfig(appConfig);
                lang.setLanguage(newLang);
                dispose();
                new LoginFrame().setVisible(true);
            }
        });

        pack();
        setMinimumSize(new Dimension(450, 450));
        setLocationRelativeTo(null);
        setFrameIcon(this);
    }

    static void setFrameIcon(java.awt.Window frame) {
        try {
            java.util.List<java.awt.Image> icons = new java.util.ArrayList<>();
            java.awt.Image img = javax.imageio.ImageIO.read(
                frame.getClass().getResourceAsStream("/icons/icon.png"));
            if (img != null) {
                icons.add(img.getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH));
                icons.add(img.getScaledInstance(32, 32, java.awt.Image.SCALE_SMOOTH));
                icons.add(img.getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH));
                icons.add(img);
                frame.setIconImages(icons);
            }
        } catch (Exception ignored) {}
    }

    private void refreshUserList() {
        userCombo.removeAllItems();
        for (String u : vaultManager.listUsers()) {
            userCombo.addItem(u);
        }
    }

    /** Rebuilds the workspace dropdown: current folder first, then recent folders. */
    private void refreshWorkspaceCombo() {
        updatingWorkspaceCombo = true;
        try {
            workspaceCombo.removeAllItems();
            Set<String> dirs = new LinkedHashSet<>();
            dirs.add(appConfig.getLocalVaultDirectory());
            dirs.addAll(appConfig.getRecentWorkspaces());
            for (String d : dirs) {
                workspaceCombo.addItem(d);
            }
            workspaceCombo.setSelectedItem(appConfig.getLocalVaultDirectory());
            workspaceCombo.setToolTipText(appConfig.getLocalVaultDirectory());
        } finally {
            updatingWorkspaceCombo = false;
        }
    }

    private void doBrowseWorkspace() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(lang.getString("workspace.choose_title"));
        File current = new File(appConfig.getLocalVaultDirectory());
        if (current.isDirectory()) fc.setCurrentDirectory(current);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            switchWorkspace(fc.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Switches the active working folder: validates write access, optionally migrates
     * existing vaults, persists the choice, and rebuilds the VaultManager + user list.
     * On any abort/error the dropdown is reset to the current folder.
     */
    private void switchWorkspace(String newDir) {
        String oldDir = appConfig.getLocalVaultDirectory();
        String canonNew = canonicalize(newDir);
        // Compare canonical paths so "/foo", "/foo/" and relative spellings are the same folder.
        if (canonNew == null || canonNew.equals(canonicalize(oldDir))) {
            refreshWorkspaceCombo();
            return;
        }
        File dir = new File(canonNew);
        if (!dir.exists()) dir.mkdirs();
        if (!dir.isDirectory() || !dir.canWrite()) {
            showError(lang.getString("error.dir_not_writable"));
            refreshWorkspaceCombo();
            return;
        }

        // Offer to migrate existing vaults from the old folder to the new one.
        File[] existing = new File(oldDir).listFiles((d, name) ->
            name.startsWith(VaultManager.VAULT_FILE_PREFIX) && name.endsWith(VaultManager.VAULT_FILE_SUFFIX));
        int vaultCount = existing == null ? 0 : existing.length;
        if (vaultCount > 0) {
            int choice = JOptionPane.showConfirmDialog(this,
                lang.getString("workspace.migrate_prompt").replace("{0}", String.valueOf(vaultCount)),
                lang.getString("workspace.migrate_title"),
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                refreshWorkspaceCombo();
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                try {
                    VaultStoreMigrator.Result r = VaultStoreMigrator.migrate(
                        new FileVaultStore(oldDir), new FileVaultStore(canonNew));
                    if (r.hasFailures()) {
                        showError(lang.getString("workspace.migrate_failed")
                            .replace("{0}", String.valueOf(r.failed)));
                    }
                } catch (IOException ex) {
                    showError(lang.getString("workspace.migrate_failed").replace("{0}", "?"));
                }
            }
        }

        // Keep the folder we are leaving reachable from the dropdown, then switch.
        appConfig.addRecentWorkspace(canonicalize(oldDir));
        appConfig.setLocalVaultDirectory(canonNew);
        appConfig.addRecentWorkspace(canonNew);
        configManager.saveConfig(appConfig);
        vaultManager = new VaultManager(canonNew);
        vaultManager.getImporter().setDefaultImportCategory(lang.getString("category.default.other"));
        refreshUserList();
        refreshWorkspaceCombo();
        statusLabel.setText("");
        passwordField.setText("");
    }

    /** Resolves a path to its canonical form (collapses {@code ..}, trailing separators); falls back to absolute. */
    private static String canonicalize(String path) {
        if (path == null) return null;
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            return new File(path).getAbsolutePath();
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
            VaultLoadResult result = vaultManager.loadVault(username, password);
            failedAttemptsMap.remove(username);
            statusLabel.setText("");
            dispose();
            new MainFrame(result.getVault(), username, result.getSession(),
                vaultManager, appConfig, configManager).setVisible(true);
        } catch (com.passwordmanager.crypto.VaultDecryptionException ex) {
            int attempts = failedAttemptsMap.getOrDefault(username, 0) + 1;
            failedAttemptsMap.put(username, attempts);
            statusLabel.setText(lang.getString("error.invalid_password"));
            passwordField.setText("");
            // Rate-limit after 3 consecutive failures
            if (attempts >= 3) {
                loginButton.setEnabled(false);
                passwordField.setEnabled(false);
                statusLabel.setText(lang.getString("error.too_many_attempts"));
                int delay = Math.min(2000 * (1 << Math.min(attempts - 3, 4)), 30000);
                if (rateLimitTimer != null) rateLimitTimer.stop();
                rateLimitTimer = new Timer(delay, evt -> {
                    loginButton.setEnabled(true);
                    passwordField.setEnabled(true);
                    statusLabel.setText(lang.getString("error.invalid_password"));
                });
                rateLimitTimer.setRepeats(false);
                rateLimitTimer.start();
            }
        } catch (Exception ex) {
            showError(lang.getString("common.error") + ": " + ex.getMessage());
        } finally {
            Arrays.fill(password, '\0');
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

        char echoChar = pass1.getEchoChar();
        JCheckBox showPasswordCheck = new JCheckBox(lang.getString("entry.show_password"));
        showPasswordCheck.addActionListener(e -> {
            char c = showPasswordCheck.isSelected() ? (char) 0 : echoChar;
            pass1.setEchoChar(c);
            pass2.setEchoChar(c);
        });
        panel.add(showPasswordCheck);
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
                if (!PasswordValidator.validate(p1)) {
                    showError(lang.getString("security.password_requirements"));
                    return;
                }
                List<String> localizedCategories = List.of(
                    lang.getString("category.default.email"),
                    lang.getString("category.default.banking"),
                    lang.getString("category.default.social"),
                    lang.getString("category.default.work"),
                    lang.getString("category.default.other")
                );
                vaultManager.createVault(newUser, p1, localizedCategories);
                refreshUserList();
                userCombo.setSelectedItem(newUser);
                JOptionPane.showMessageDialog(this, lang.getString("login.user_created"),
                    lang.getString("common.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showError(ex.getMessage());
            } finally {
                Arrays.fill(p1, '\0');
                Arrays.fill(p2, '\0');
            }
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
