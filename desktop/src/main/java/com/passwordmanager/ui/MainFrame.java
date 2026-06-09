package com.passwordmanager.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.passwordmanager.Main;
import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.AppVersion;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.config.StorageMode;
import com.passwordmanager.config.ThemeMode;
import com.passwordmanager.crypto.VaultSession;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.sync.DesktopSyncFactory;
import com.passwordmanager.sync.SyncService;
import com.passwordmanager.util.PasswordValidator;
import com.passwordmanager.update.DesktopUpdateManager;
import com.passwordmanager.sync.EntryMerger;
import com.passwordmanager.util.FaviconService;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.components.RoundedPanel;
import com.passwordmanager.ui.theme.DesignTokens;
import com.passwordmanager.vault.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application window with menu bar, vault panel, and status bar.
 * Holds the VaultSession (DEK) instead of the master password.
 * Delegates import/export, security audit, and auto-lock to dedicated controllers.
 */
public class MainFrame extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(MainFrame.class.getName());
    private final LanguageManager lang = LanguageManager.getInstance();
    private Vault vault;
    private String username;
    private VaultSession session;
    private VaultManager vaultManager;
    private VaultService vaultService;
    private AppConfig appConfig;
    private ConfigManager configManager;
    private SyncService syncService;
    /** Cached SSH key material (vault-key auth) used when (re)building the sync service. */
    private byte[] vaultKeyBytes;
    private static final String VIEW_PASSWORDS = "passwords";
    private static final String VIEW_APPS = "apps";
    private static final String VIEW_AUDIT = "audit";
    private static final String VIEW_SETTINGS = "settings";

    private CoffrePasswordsPanel coffrePasswordsPanel;
    private CoffreAppsPanel appPanel;
    private CoffreSshPanel sshKeyPanel;
    private CoffreSettingsPanel settingsPanel;
    private String lastVaultView = VIEW_PASSWORDS;
    private JPanel contentCards;
    private CardLayout contentLayout;
    private String currentView = VIEW_PASSWORDS;
    private final java.util.Map<String, JToggleButton> typeButtons = new java.util.LinkedHashMap<>();
    private JPanel auditHost;
    private JLabel statusLabel;
    private Thread shutdownHook;
    // Applied language/theme, to detect changes when (re)applying settings.
    private String appliedLang;
    private ThemeMode appliedTheme;

    // Extracted controllers
    private ImportExportController importExportController;
    private SecurityAuditController securityAuditController;
    private AutoLockManager autoLockManager;
    private DesktopUpdateManager updateManager;


    public MainFrame(Vault vault, String username, VaultSession session,
                     VaultManager vaultManager, AppConfig appConfig, ConfigManager configManager) {
        this.vault = vault;
        this.username = username;
        this.session = session;
        this.vaultManager = vaultManager;
        this.appConfig = appConfig;
        this.configManager = configManager;
        this.vaultService = new VaultService(vault);
        this.syncService = DesktopSyncFactory.create(appConfig, null);
        this.appliedLang = appConfig.getLanguage();
        this.appliedTheme = appConfig.getTheme();

        // Initialize controllers
        this.importExportController = new ImportExportController(
            this, vault, username, session, vaultManager,
            this::saveVault, () -> { refreshAllPanels(); statusLabel.setText(getStatusText()); });
        this.securityAuditController = new SecurityAuditController(this, vault, vaultService);
        this.autoLockManager = new AutoLockManager(appConfig, this::doLock);

        initComponents();
        autoLockManager.startAutoLock();
        shutdownHook = new Thread(() -> {
            SecureClipboard.clear();
            if (vault != null) vault.wipe();
            if (session != null && !session.isDestroyed()) session.destroy();
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void initComponents() {
        setTitle(lang.getString("app.title") + " - " + username);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);
        LoginFrame.setFrameIcon(this);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { doQuit(); }
        });

        // Track activity for auto-lock
        autoLockManager.installActivityListener();

        // Content panels (one per entry type)
        coffrePasswordsPanel = new CoffrePasswordsPanel(vaultService, appConfig.getClipboardClearSeconds(),
            () -> { saveVault(); statusLabel.setText(getStatusText()); refreshTypeCounts(); });
        try {
            String cacheDir = System.getProperty("user.home") + "/.password-manager/data/favicons";
            coffrePasswordsPanel.setFaviconService(new FaviconService(cacheDir));
            coffrePasswordsPanel.setFaviconsEnabled(appConfig.isFaviconsEnabled());
        } catch (Exception ignored) {}

        appPanel = new CoffreAppsPanel(vaultService.getAppService(), appConfig.getClipboardClearSeconds(),
            () -> { saveVault(); statusLabel.setText(getStatusText()); refreshTypeCounts(); });

        sshKeyPanel = new CoffreSshPanel(vaultService.getSshKeyService(), appConfig.getClipboardClearSeconds(),
            () -> { saveVault(); statusLabel.setText(getStatusText()); refreshTypeCounts(); });

        // SSH keys are no longer a top-level page — they live in a Settings tab (after Sync).
        settingsPanel = new CoffreSettingsPanel(appConfig, configManager,
            vaultService.getSshKeyService().getActiveList(), vaultService,
            this::applySettings, () -> switchView(lastVaultView), this::doLock,
            () -> { saveVault(); coffrePasswordsPanel.refresh(); }, sshKeyPanel);

        // Audit is now a first-class page (rebuilt on each visit so its metrics stay current).
        auditHost = new JPanel(new BorderLayout());

        contentLayout = new CardLayout();
        contentCards = new JPanel(contentLayout);
        contentCards.add(coffrePasswordsPanel, VIEW_PASSWORDS);
        contentCards.add(appPanel, VIEW_APPS);
        contentCards.add(auditHost, VIEW_AUDIT);
        contentCards.add(settingsPanel, VIEW_SETTINGS);

        // Window menu bar: global actions (file/edit/tools/help) including logout.
        setJMenuBar(createMenuBar());

        // North: update notification bar only (the old top toolbar is gone — search, sort, filter
        // and "new entry" now live inside each content panel; global actions are in the menu bar).
        updateManager = new DesktopUpdateManager();
        add(updateManager.createNotificationBar(), BorderLayout.NORTH);
        updateManager.startPeriodicCheck();

        // West: sidebar (page navigation + "more" menu with logout)
        add(createSidebar(), BorderLayout.WEST);

        // Center: content
        add(contentCards, BorderLayout.CENTER);

        // South: status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        statusLabel = new JLabel(getStatusText());
        statusLabel.setForeground(DesignTokens.onSurfaceFaint());
        statusBar.add(statusLabel, BorderLayout.WEST);
        add(statusBar, BorderLayout.SOUTH);

        installKeyBindings();
        String startView = System.getProperty("pm.view");
        switchView("apps".equals(startView) ? VIEW_APPS
            : "settings".equals(startView) ? VIEW_SETTINGS : VIEW_PASSWORDS);

        // Size the window to fit all components (pack) so nothing is clipped and there is
        // no horizontal scroll; also avoids the blank-first-paint issue on XWayland.
        pack();
        setLocationRelativeTo(null);
    }

    private JComponent createSidebar() {
        // Clean, modern navigation: pages only (no categories — those moved to Settings).
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_LG, DesignTokens.SPACE_MD, DesignTokens.SPACE_LG, DesignTokens.SPACE_MD)));
        side.setPreferredSize(new Dimension(220, 0));

        side.add(navTitle(lang.getString("nav.title")));
        side.add(typeNav(VIEW_PASSWORDS, lang.getString("tab.passwords")));
        side.add(Box.createVerticalStrut(DesignTokens.SPACE_XS));
        side.add(typeNav(VIEW_APPS, lang.getString("tab.applications")));
        side.add(Box.createVerticalStrut(DesignTokens.SPACE_XS));
        side.add(typeNav(VIEW_AUDIT, lang.getString("nav.audit")));
        side.add(Box.createVerticalStrut(DesignTokens.SPACE_XS));
        side.add(typeNav(VIEW_SETTINGS, lang.getString("menu.file.settings")));
        side.add(Box.createVerticalGlue());

        refreshTypeCounts();
        return side;
    }

    private JComponent navTitle(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(DesignTokens.onSurfaceFaint());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 6, 8, 6));
        return l;
    }

    private JToggleButton typeNav(String view, String label) {
        JToggleButton b = new JToggleButton(label);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("baseLabel", label);
        b.addActionListener(e -> switchView(view));
        typeButtons.put(view, b);
        return b;
    }

    private void styleNav(JToggleButton b, boolean active) {
        b.setSelected(active);
        b.setContentAreaFilled(active);
        b.setOpaque(active);
        b.setBackground(active ? DesignTokens.accentContainer() : null);
        b.setForeground(active ? DesignTokens.accent() : DesignTokens.onSurfaceFaint());
    }

    private void switchView(String view) {
        currentView = view;
        if (VIEW_PASSWORDS.equals(view) || VIEW_APPS.equals(view)) {
            lastVaultView = view;
        }
        if (VIEW_AUDIT.equals(view)) rebuildAuditView();
        contentLayout.show(contentCards, view);
        typeButtons.forEach((k, b) -> styleNav(b, k.equals(view)));
        revalidate();
        repaint();
    }

    /** Rebuilds the audit page content (title header + freshly computed audit view). */
    private void rebuildAuditView() {
        auditHost.removeAll();
        JPanel header = new JPanel(new BorderLayout(DesignTokens.SPACE_MD, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, DesignTokens.SPACE_LG, DesignTokens.SPACE_MD, DesignTokens.SPACE_LG)));
        JLabel title = new JLabel(lang.getString("nav.audit"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        header.add(title, BorderLayout.WEST);
        auditHost.add(header, BorderLayout.NORTH);
        auditHost.add(securityAuditController.buildAuditView(), BorderLayout.CENTER);
        auditHost.revalidate();
        auditHost.repaint();
    }

    private void refreshTypeCounts() {
        setNavCount(VIEW_PASSWORDS, vault.getEntries().size());
        setNavCount(VIEW_APPS, vault.getAppEntries().size());
    }

    private void setNavCount(String view, int count) {
        JToggleButton b = typeButtons.get(view);
        if (b != null) {
            Object base = b.getClientProperty("baseLabel");
            b.setText((base != null ? base.toString() : "") + "   " + count);
        }
    }

    /** Window menu bar: File (import/export/logout/quit), Edit, Tools (sync/generator), Help. */
    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu(lang.getString("menu.file"));
        JMenuItem imp = new JMenuItem(lang.getString("menu.file.import"));
        imp.addActionListener(e -> importExportController.doImport());
        JMenuItem exp = new JMenuItem(lang.getString("menu.file.export"));
        exp.addActionListener(e -> importExportController.doExport());
        JMenuItem lock = new JMenuItem(lang.getString("menu.file.lock"));
        lock.addActionListener(e -> doLock());
        JMenuItem quit = new JMenuItem(lang.getString("menu.file.quit"));
        quit.addActionListener(e -> doQuit());
        file.add(imp);
        file.add(exp);
        file.addSeparator();
        file.add(lock);
        file.add(quit);
        bar.add(file);

        JMenu edit = new JMenu(lang.getString("menu.edit"));
        JMenuItem cm = new JMenuItem(lang.getString("menu.edit.change_master"));
        cm.addActionListener(e -> doChangeMasterPassword());
        edit.add(cm);
        bar.add(edit);

        JMenu tools = new JMenu(lang.getString("menu.tools"));
        JMenuItem sync = new JMenuItem(lang.getString("menu.tools.sync_now"));
        sync.setEnabled(appConfig.getStorageMode() == StorageMode.REMOTE);
        sync.addActionListener(e -> doSync());
        JMenuItem gen = new JMenuItem(lang.getString("menu.tools.generator"));
        gen.addActionListener(e -> new PasswordGeneratorDialog(MainFrame.this).setVisible(true));
        tools.add(sync);
        tools.add(gen);
        bar.add(tools);

        JMenu help = new JMenu(lang.getString("menu.help"));
        JMenuItem about = new JMenuItem(lang.getString("menu.help.about"));
        about.addActionListener(e -> JOptionPane.showMessageDialog(MainFrame.this,
            lang.getString("about.description").replace("{0}", AppVersion.get()),
            lang.getString("about.title"), JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(help);

        return bar;
    }

    private void installKeyBindings() {
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "newEntry");
        root.getActionMap().put("newEntry", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { addNewEntryForActiveView(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "refresh");
        root.getActionMap().put("refresh", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { refreshAllPanels(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focusSearch");
        root.getActionMap().put("focusSearch", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                JTextField sf = activeSearchField();
                if (sf != null) sf.requestFocusInWindow();
            }
        });
    }

    private JTextField activeSearchField() {
        switch (currentView) {
            case VIEW_PASSWORDS: return coffrePasswordsPanel.getSearchField();
            case VIEW_APPS: return appPanel.getSearchField();
            default: return null;
        }
    }

    private void addNewEntryForActiveView() {
        switch (currentView) {
            case VIEW_APPS: appPanel.addNewEntry(); break;
            default: coffrePasswordsPanel.addNewEntry(); break;
        }
    }

    private void saveVault() {
        try {
            vaultManager.saveVault(vault, username, session);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                lang.getString("common.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateSyncVaultKey() {
        if (appConfig.isUsingVaultKey()) {
            String keyId = appConfig.getSftpVaultKeyId();
            com.passwordmanager.vault.SshKeyEntry keyEntry = vaultService.getSshKeyService()
                .getActiveList().stream()
                .filter(k -> k.getId().equals(keyId))
                .findFirst().orElse(null);
            if (keyEntry != null) {
                char[] pk = keyEntry.getPrivateKey();
                if (pk != null) {
                    byte[] bytes = new String(pk).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    com.passwordmanager.util.SecureWiper.wipe(pk);
                    this.vaultKeyBytes = bytes;
                } else {
                    this.vaultKeyBytes = null;
                }
            } else {
                this.vaultKeyBytes = null;
            }
        } else {
            this.vaultKeyBytes = null;
        }
    }

    /**
     * Applies the (already-saved) settings to the running app, called by the Settings panel's
     * Apply. A language or theme change rebuilds the whole window so every component is
     * reconstructed with fresh colors/strings (Swing's updateComponentTreeUI does not refresh
     * explicitly-set foreground colors); the rebuilt window reopens on the Settings view so the
     * user stays where they were. Other changes apply live without a rebuild.
     */
    private void applySettings() {
        boolean langChanged = !appliedLang.equals(appConfig.getLanguage());
        boolean themeChanged = appliedTheme != appConfig.getTheme();
        if (langChanged || themeChanged) {
            lang.setLanguage(appConfig.getLanguage());
            applyTheme();
            rebuildMainFrame(VIEW_SETTINGS); // stay on Settings after the rebuild
            return;
        }
        updateSyncVaultKey();
        syncService = DesktopSyncFactory.create(appConfig, vaultKeyBytes);
        statusLabel.setText(getStatusText());
        coffrePasswordsPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
        appPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
        sshKeyPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
        coffrePasswordsPanel.setFaviconsEnabled(appConfig.isFaviconsEnabled());
        autoLockManager.startAutoLock();
    }

    private void applyTheme() {
        try {
            boolean dark;
            switch (appConfig.getTheme()) {
                case DARK:
                    dark = true;
                    break;
                case SYSTEM:
                    dark = Main.isSystemDark();
                    break;
                default:
                    dark = false;
                    break;
            }
            if (dark) FlatDarkLaf.setup();
            else FlatLightLaf.setup();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply theme", e);
        }
    }

    private void rebuildMainFrame() {
        rebuildMainFrame(VIEW_PASSWORDS);
    }

    private void rebuildMainFrame(String startView) {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        cleanup();
        dispose();
        MainFrame f = new MainFrame(vault, username, session, vaultManager, appConfig, configManager);
        f.setVisible(true);
        f.switchView(startView);
    }

    private void doChangeMasterPassword() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        JPasswordField oldPass = new JPasswordField();
        JPasswordField newPass = new JPasswordField();
        JPasswordField confirmPass = new JPasswordField();

        panel.add(new JLabel(lang.getString("security.old_password")));
        panel.add(oldPass);
        panel.add(new JLabel(lang.getString("security.new_password")));
        panel.add(newPass);
        panel.add(new JLabel(lang.getString("security.confirm_password")));
        panel.add(confirmPass);
        panel.add(new JLabel("<html><i>" + lang.getString("security.password_requirements") + "</i></html>"));

        int result = JOptionPane.showConfirmDialog(this, panel,
            lang.getString("settings.change_master"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            char[] op = oldPass.getPassword();
            char[] np = newPass.getPassword();
            char[] cp = confirmPass.getPassword();

            try {
                // Verify old password by attempting to load the vault
                VaultLoadResult check = vaultManager.loadVault(username, op);
                check.getVault().wipe();
                check.getSession().destroy();

                if (!Arrays.equals(np, cp)) {
                    showError(lang.getString("security.password_mismatch"));
                    return;
                }
                if (!PasswordValidator.validate(np)) {
                    showError(lang.getString("security.password_requirements"));
                    return;
                }
                session = vaultManager.changeMasterPassword(username, vault, session, np);
                JOptionPane.showMessageDialog(this, lang.getString("security.password_changed"),
                    lang.getString("common.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showError(lang.getString("error.invalid_password"));
            } finally {
                Arrays.fill(op, '\0');
                Arrays.fill(np, '\0');
                Arrays.fill(cp, '\0');
            }
        }
    }

    private void cleanup() {
        autoLockManager.cleanup();
        coffrePasswordsPanel.cancelClipboardTimer();
        appPanel.cancelClipboardTimer();
        sshKeyPanel.cancelClipboardTimer();
        if (updateManager != null) updateManager.stop();
    }

    private void clearClipboard() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(""), null);
        } catch (Exception ignored) {
            // Clipboard may not be accessible
        }
    }

    private void doLock() {
        saveVault();
        cleanup();
        clearClipboard();
        vault.wipe();
        session.destroy();
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        dispose();
        new LoginFrame().setVisible(true);
    }

    private void doQuit() {
        saveVault();
        cleanup();
        clearClipboard();
        vault.wipe();
        session.destroy();
        System.exit(0);
    }

    private void doSync() {
        statusLabel.setText(lang.getString("sync.syncing") + "...");
        new SwingWorker<SyncService.SyncResult, Void>() {
            @Override
            protected SyncService.SyncResult doInBackground() {
                return syncService.synchronize("vault_" + username + ".enc");
            }
            @Override
            protected void done() {
                try {
                    SyncService.SyncResult result = get();
                    statusLabel.setText(getStatusText());
                    if (!result.isSuccess() && "CONFLICT".equals(result.getMessage())) {
                        handleConflict(result.getRemoteTempPath());
                    } else if (result.isSuccess() && "downloaded".equals(result.getMessage())) {
                        // Remote was newer: reload vault from disk
                        try {
                            Vault reloaded = vaultManager.reloadVault(username, session);
                            vault = reloaded;
                            vaultService.setVault(vault);
                            // R4: adopt the downloaded envelope so a master-password change
                            // made on another device is reflected for future saves.
                            vaultManager.adoptEnvelopeFromFile(session, vaultManager.getVaultPath(username));
                            refreshAllPanels();
                        } catch (Exception reloadEx) {
                            showError(reloadEx.getMessage());
                        }
                    } else if (!result.isSuccess()) {
                        JOptionPane.showMessageDialog(MainFrame.this, result.getMessage(),
                            lang.getString("sync.status_error"), JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    statusLabel.setText(getStatusText());
                    showError(ex.getMessage());
                }
            }
        }.execute();
    }

    private void handleConflict(String remoteTempPath) {
        // Entry-level merge: decrypt the DOWNLOADED REMOTE vault (not a re-read of the
        // local file) and compare entry-by-entry. R-merge fix.
        try {
            Vault remoteVault = vaultManager.decryptVaultFile(remoteTempPath, session);
            // R4: adopt the remote envelope so a master-password change made on another
            // device is preserved when we save+upload the merged vault (not reverted).
            vaultManager.adoptEnvelopeFromFile(session, remoteTempPath);

            // Merge all three entry lists (including tombstones for deletion propagation)
            EntryMerger.MergeResult<PasswordEntry> passwordMerge = syncService.mergeEntries(
                vault.getEntriesMutable(), remoteVault.getEntriesMutable());
            EntryMerger.MergeResult<AppEntry> appMerge = syncService.mergeEntries(
                vault.getAppEntriesMutable(), remoteVault.getAppEntriesMutable());
            EntryMerger.MergeResult<SshKeyEntry> sshKeyMerge = syncService.mergeEntries(
                vault.getSshKeyEntriesMutable(), remoteVault.getSshKeyEntriesMutable());
            boolean hasConflicts = passwordMerge.hasConflicts()
                || appMerge.hasConflicts()
                || sshKeyMerge.hasConflicts();

            String vaultFilename = "vault_" + username + ".enc";
            if (!hasConflicts) {
                // Auto-merge: no conflicts, apply merged entries directly
                applyMerge(passwordMerge, appMerge, sshKeyMerge);
                SyncService.SyncResult mergeResult = syncService.syncAfterMerge(
                    vaultFilename,
                    () -> vaultManager.saveVault(vault, username, session));
                refreshAllPanels();
                if (mergeResult.isSuccess()) {
                    JOptionPane.showMessageDialog(this, lang.getString("sync.merge.auto_merged"),
                        lang.getString("common.success"), JOptionPane.INFORMATION_MESSAGE);
                } else {
                    showError(mergeResult.getMessage());
                }
            } else {
                // Collect all conflicts (password, app) into a single dialog
                List<EntryMerger.Conflict<? extends VaultItem>> allConflicts = new ArrayList<>();
                allConflicts.addAll(passwordMerge.getConflicts());
                allConflicts.addAll(appMerge.getConflicts());
                allConflicts.addAll(sshKeyMerge.getConflicts());

                ConflictResolutionDialog dlg = new ConflictResolutionDialog(this, allConflicts);
                dlg.setVisible(true);

                // Apply the non-conflicting merged entries first. By contract,
                // mergedEntries excludes conflicts, so this never introduces duplicates.
                applyMerge(passwordMerge, appMerge, sshKeyMerge);

                // Resolve each conflict: the user's choice if confirmed, otherwise keep
                // the local version (Option A: dismissing keeps local state as-is).
                List<VaultItem> resolved;
                if (dlg.isConfirmed()) {
                    resolved = dlg.getResolvedEntries();
                } else {
                    resolved = new ArrayList<>();
                    for (EntryMerger.Conflict<PasswordEntry> c : passwordMerge.getConflicts()) {
                        resolved.add(c.getLocalEntry());
                    }
                    for (EntryMerger.Conflict<AppEntry> c : appMerge.getConflicts()) {
                        resolved.add(c.getLocalEntry());
                    }
                    for (EntryMerger.Conflict<SshKeyEntry> c : sshKeyMerge.getConflicts()) {
                        resolved.add(c.getLocalEntry());
                    }
                }
                for (VaultItem item : resolved) {
                    if (item instanceof PasswordEntry) {
                        vault.addEntry((PasswordEntry) item);
                    } else if (item instanceof AppEntry) {
                        vault.addAppEntry((AppEntry) item);
                    } else if (item instanceof SshKeyEntry) {
                        vault.addSshKeyEntry((SshKeyEntry) item);
                    }
                }

                if (dlg.isConfirmed()) {
                    // User resolved: persist and upload the merged result.
                    SyncService.SyncResult mergeResult = syncService.syncAfterMerge(
                        vaultFilename,
                        () -> vaultManager.saveVault(vault, username, session));
                    refreshAllPanels();
                    if (!mergeResult.isSuccess()) {
                        showError(mergeResult.getMessage());
                    }
                } else {
                    // Dismissed: keep local, persist locally only (no upload until resolved).
                    try {
                        vaultManager.saveVault(vault, username, session);
                    } catch (Exception saveEx) {
                        showError(saveEx.getMessage());
                    }
                    refreshAllPanels();
                }
            }
            remoteVault.wipe();
        } catch (Exception ex) {
            // Fallback to file-level conflict resolution if entry merge fails
            Object[] options = {
                lang.getString("sync.keep_local"),
                lang.getString("sync.keep_remote"),
                lang.getString("sync.keep_both")
            };
            int choice = JOptionPane.showOptionDialog(this,
                lang.getString("sync.conflict_message"),
                lang.getString("sync.conflict_title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

            com.passwordmanager.sync.ConflictStrategy resolution;
            switch (choice) {
                case 1: resolution = com.passwordmanager.sync.ConflictStrategy.KEEP_REMOTE; break;
                case 2: resolution = com.passwordmanager.sync.ConflictStrategy.KEEP_BOTH; break;
                default: resolution = com.passwordmanager.sync.ConflictStrategy.KEEP_LOCAL;
            }

            syncService.resolveConflict("vault_" + username + ".enc", resolution);
            if (resolution != com.passwordmanager.sync.ConflictStrategy.KEEP_LOCAL) {
                try {
                    Vault reloaded = vaultManager.reloadVault(username, session);
                    vault = reloaded;
                    vaultService.setVault(vault);
                    refreshAllPanels();
                } catch (Exception reloadEx) {
                    showError(reloadEx.getMessage());
                }
            }
        } finally {
            if (remoteTempPath != null) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(remoteTempPath));
                } catch (Exception ignore) {
                    // best-effort cleanup of the downloaded remote temp
                }
            }
        }
        statusLabel.setText(getStatusText());
    }

    private void applyMerge(EntryMerger.MergeResult<PasswordEntry> passwordMerge,
                            EntryMerger.MergeResult<AppEntry> appMerge,
                            EntryMerger.MergeResult<SshKeyEntry> sshKeyMerge) {
        synchronized (vault) {
            vault.getEntriesMutable().clear();
            for (PasswordEntry e : passwordMerge.getMergedEntries()) {
                vault.addEntry(e);
            }
            vault.getAppEntriesMutable().clear();
            for (AppEntry e : appMerge.getMergedEntries()) {
                vault.addAppEntry(e);
            }
            vault.getSshKeyEntriesMutable().clear();
            for (SshKeyEntry e : sshKeyMerge.getMergedEntries()) {
                vault.addSshKeyEntry(e);
            }
        }
    }

    private void refreshAllPanels() {
        coffrePasswordsPanel.refresh();
        appPanel.refresh();
        sshKeyPanel.refresh();
        refreshTypeCounts();
    }

    private String getStatusText() {
        String mode = appConfig.getStorageMode() == StorageMode.LOCAL
            ? lang.getString("sync.status_local")
            : syncService.getSyncStatus();
        int total = vault.getEntries().size() + vault.getAppEntries().size() + vault.getSshKeyEntries().size();
        return mode + "  |  " + username + "  |  " + total + " " + lang.getString("vault.entries");
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
