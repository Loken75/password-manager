package com.passwordmanager.ui;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.AppVersion;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.components.RoundedPanel;
import com.passwordmanager.ui.theme.DesignTokens;
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

        // Calm centered card on a neutral backdrop (design: test_design/PC/01-login.html).
        JPanel backdrop = new JPanel(new GridBagLayout());
        backdrop.setBackground(DesignTokens.surfaceContainer());

        // Fixed width, natural height (centered by the backdrop's GridBagLayout).
        RoundedPanel card = new RoundedPanel() {
            @Override public Dimension getPreferredSize() {
                return new Dimension(404, super.getPreferredSize().height);
            }
            @Override public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        card.setArc(18);
        card.setFillColor(DesignTokens.surface());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(34, 36, 30, 36));

        // Logo (app icon on an accent rounded square)
        JLabel logo = new JLabel();
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        try {
            java.awt.Image img = javax.imageio.ImageIO.read(getClass().getResourceAsStream("/icons/icon.png"));
            if (img != null) logo.setIcon(new ImageIcon(img.getScaledInstance(52, 52, java.awt.Image.SCALE_SMOOTH)));
        } catch (Exception ignored) {}
        card.add(logo);
        card.add(Box.createVerticalStrut(14));

        JLabel titleLabel = new JLabel(lang.getString("app.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 21f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(4));

        JLabel subtitleLabel = new JLabel(lang.getString("app.version").replace("{0}", AppVersion.get()));
        subtitleLabel.setForeground(DesignTokens.onSurfaceFaint());
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(subtitleLabel);
        card.add(Box.createVerticalStrut(24));

        // Working folder (workspace)
        workspaceCombo = new JComboBox<>();
        workspaceCombo.setToolTipText(appConfig.getLocalVaultDirectory());
        JButton browseWorkspaceBtn = new JButton("...");
        browseWorkspaceBtn.setMargin(new Insets(0, 8, 0, 8));
        browseWorkspaceBtn.setToolTipText(lang.getString("settings.change_workspace"));
        refreshWorkspaceCombo();
        JPanel workspaceRow = new JPanel(new BorderLayout(8, 0));
        workspaceRow.setOpaque(false);
        workspaceRow.add(workspaceCombo, BorderLayout.CENTER);
        workspaceRow.add(browseWorkspaceBtn, BorderLayout.EAST);
        card.add(fieldGroup(lang.getString("login.workspace"), workspaceRow));

        workspaceCombo.addActionListener(e -> {
            if (updatingWorkspaceCombo) return;
            String selected = (String) workspaceCombo.getSelectedItem();
            if (selected != null && !selected.equals(appConfig.getLocalVaultDirectory())) {
                switchWorkspace(selected);
            }
        });
        browseWorkspaceBtn.addActionListener(e -> doBrowseWorkspace());

        // User
        userCombo = new JComboBox<>();
        refreshUserList();
        card.add(fieldGroup(lang.getString("login.username"), userCombo));

        // Password with inline reveal toggle
        passwordField = new JPasswordField();
        char echoChar = passwordField.getEchoChar();
        JToggleButton revealBtn = new JToggleButton(lang.getString("entry.show_password"));
        revealBtn.setFocusPainted(false);
        revealBtn.setForeground(DesignTokens.accent());
        revealBtn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        revealBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        revealBtn.addActionListener(e ->
            passwordField.setEchoChar(revealBtn.isSelected() ? (char) 0 : echoChar));
        JPanel passwordRow = new JPanel(new BorderLayout(8, 0));
        passwordRow.setOpaque(false);
        passwordRow.add(passwordField, BorderLayout.CENTER);
        passwordRow.add(revealBtn, BorderLayout.EAST);
        card.add(fieldGroup(lang.getString("login.password"), passwordRow));
        card.add(Box.createVerticalStrut(8));

        // Login (primary) with the "create a vault" action aligned to its right, on one row.
        loginButton = Buttons.primary(lang.getString("login.button"));
        createUserButton = linkButton(lang.getString("login.create_user"));
        JPanel buttonRow = new JPanel(new BorderLayout(8, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        buttonRow.add(loginButton, BorderLayout.CENTER);
        buttonRow.add(createUserButton, BorderLayout.EAST);
        card.add(buttonRow);
        card.add(Box.createVerticalStrut(12));

        // Status
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(DesignTokens.statusWeak());
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(statusLabel);
        card.add(Box.createVerticalStrut(16));

        // Footer: language (left) + check updates (right)
        JSeparator sep = new JSeparator();
        sep.setForeground(DesignTokens.outline());
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        card.add(sep);
        card.add(Box.createVerticalStrut(12));

        languageCombo = new JComboBox<>(new String[]{"Fran\u00e7ais", "English"});
        languageCombo.setSelectedIndex("en".equals(appConfig.getLanguage()) ? 1 : 0);
        JPanel langWrap = new JPanel(new BorderLayout(8, 0));
        langWrap.setOpaque(false);
        JLabel langLabel = new JLabel(lang.getString("settings.language") + " :");
        langLabel.setForeground(DesignTokens.onSurfaceFaint());
        langWrap.add(langLabel, BorderLayout.WEST);
        langWrap.add(languageCombo, BorderLayout.CENTER);

        JButton checkUpdateBtn = linkButton(lang.getString("update.check"));
        checkUpdateBtn.addActionListener(e -> new DesktopUpdateManager().checkManually(this));

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        footer.add(langWrap, BorderLayout.WEST);
        footer.add(checkUpdateBtn, BorderLayout.EAST);
        card.add(footer);

        backdrop.add(card);
        setContentPane(backdrop);

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
        setMinimumSize(new Dimension(520, 640));
        setLocationRelativeTo(null);
        setFrameIcon(this);
    }

    /** A labeled field group: small caption above a full-width control, with bottom spacing. */
    private JComponent fieldGroup(String caption, JComponent control) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.CENTER_ALIGNMENT);
        group.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        JLabel cap = new JLabel(caption);
        cap.setFont(cap.getFont().deriveFont(Font.BOLD, 12f));
        cap.setForeground(DesignTokens.onSurfaceFaint());
        cap.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        group.add(cap);
        group.add(Box.createVerticalStrut(6));
        group.add(control);
        return group;
    }

    /** A borderless accent-colored link-style button. */
    private JButton linkButton(String text) {
        JButton b = new JButton(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setForeground(DesignTokens.accent());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
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
