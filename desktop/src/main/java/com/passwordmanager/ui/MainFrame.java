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
    private VaultPanel vaultPanel;
    private AppPanel appPanel;
    private SshKeyPanel sshKeyPanel;
    private JTabbedPane tabbedPane;
    private JLabel statusLabel;
    private Thread shutdownHook;

    // Extracted controllers
    private ImportExportController importExportController;
    private SecurityAuditController securityAuditController;
    private AutoLockManager autoLockManager;
    private DesktopUpdateManager updateManager;

    // Menu items that need dynamic state
    private JMenuItem syncNowMenuItem;
    private JButton syncToolbarBtn;

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
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        LoginFrame.setFrameIcon(this);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { doQuit(); }
        });

        // Track activity for auto-lock
        autoLockManager.installActivityListener();

        // Menu bar
        setJMenuBar(createMenuBar());

        // Update notification bar + Toolbar
        updateManager = new DesktopUpdateManager();
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(updateManager.createNotificationBar(), BorderLayout.NORTH);
        JToolBar toolbar = createToolBar();
        northPanel.add(toolbar, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);
        updateManager.startPeriodicCheck();

        // Tabbed pane with 3 entry types
        tabbedPane = new JTabbedPane();

        vaultPanel = new VaultPanel(vaultService, appConfig.getClipboardClearSeconds());
        vaultPanel.setOnVaultChanged(() -> {
            saveVault();
            statusLabel.setText(getStatusText());
        });
        try {
            String cacheDir = System.getProperty("user.home") + "/.password-manager/data/favicons";
            vaultPanel.setFaviconService(new FaviconService(cacheDir));
        } catch (Exception ignored) {}

        appPanel = new AppPanel(vaultService.getAppService(), appConfig.getClipboardClearSeconds());
        appPanel.setOnVaultChanged(() -> {
            saveVault();
            statusLabel.setText(getStatusText());
        });

        sshKeyPanel = new SshKeyPanel(vaultService.getSshKeyService(), appConfig.getClipboardClearSeconds());
        sshKeyPanel.setOnVaultChanged(() -> {
            saveVault();
            statusLabel.setText(getStatusText());
        });

        tabbedPane.addTab(lang.getString("tab.passwords"), vaultPanel);
        tabbedPane.addTab(lang.getString("tab.applications"), appPanel);
        tabbedPane.addTab(lang.getString("tab.ssh_keys"), sshKeyPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        statusLabel = new JLabel(getStatusText());
        statusBar.add(statusLabel, BorderLayout.WEST);
        add(statusBar, BorderLayout.SOUTH);
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu(lang.getString("menu.file"));
        JMenuItem importItem = new JMenuItem(lang.getString("menu.file.import"));
        JMenuItem exportItem = new JMenuItem(lang.getString("menu.file.export"));
        JMenuItem settings = new JMenuItem(lang.getString("menu.file.settings"));
        JMenuItem lock = new JMenuItem(lang.getString("menu.file.lock"));
        JMenuItem quit = new JMenuItem(lang.getString("menu.file.quit"));

        fileMenu.add(importItem);
        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(settings);
        fileMenu.addSeparator();
        fileMenu.add(lock);
        fileMenu.add(quit);
        bar.add(fileMenu);

        // Edit menu
        JMenu editMenu = new JMenu(lang.getString("menu.edit"));
        JMenuItem newEntry = new JMenuItem(lang.getString("menu.edit.new_entry"));
        JMenuItem editEntry = new JMenuItem(lang.getString("menu.edit.edit_entry"));
        JMenuItem deleteEntry = new JMenuItem(lang.getString("menu.edit.delete_entry"));
        JMenuItem changeMaster = new JMenuItem(lang.getString("menu.edit.change_master"));

        newEntry.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        deleteEntry.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));

        editMenu.add(newEntry);
        editMenu.add(editEntry);
        editMenu.add(deleteEntry);
        editMenu.addSeparator();
        editMenu.add(changeMaster);
        bar.add(editMenu);

        // View menu
        JMenu viewMenu = new JMenu(lang.getString("menu.view"));
        JMenuItem refresh = new JMenuItem(lang.getString("menu.view.refresh"));
        JMenuItem sortName = new JMenuItem(lang.getString("menu.view.sort_name"));
        JMenuItem sortUsername = new JMenuItem(lang.getString("menu.view.sort_username"));
        JMenuItem sortEmail = new JMenuItem(lang.getString("menu.view.sort_email"));
        JMenuItem sortUrl = new JMenuItem(lang.getString("menu.view.sort_url"));
        JMenuItem sortDate = new JMenuItem(lang.getString("menu.view.sort_date"));
        JMenuItem sortCat = new JMenuItem(lang.getString("menu.view.sort_category"));
        JMenuItem filterWeak = new JMenuItem(lang.getString("menu.view.filter_weak"));
        JMenuItem filterDup = new JMenuItem(lang.getString("menu.view.filter_duplicate"));

        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));

        viewMenu.add(refresh);
        viewMenu.addSeparator();
        viewMenu.add(sortName);
        viewMenu.add(sortUsername);
        viewMenu.add(sortEmail);
        viewMenu.add(sortUrl);
        viewMenu.add(sortDate);
        viewMenu.add(sortCat);
        viewMenu.addSeparator();
        viewMenu.add(filterWeak);
        viewMenu.add(filterDup);
        bar.add(viewMenu);

        // Tools menu
        JMenu toolsMenu = new JMenu(lang.getString("menu.tools"));
        JMenuItem generator = new JMenuItem(lang.getString("menu.tools.generator"));
        JMenuItem audit = new JMenuItem(lang.getString("menu.tools.security_audit"));
        syncNowMenuItem = new JMenuItem(lang.getString("menu.tools.sync_now"));
        syncNowMenuItem.setEnabled(appConfig.getStorageMode() == StorageMode.REMOTE);

        toolsMenu.add(generator);
        toolsMenu.add(audit);
        toolsMenu.addSeparator();
        toolsMenu.add(syncNowMenuItem);
        bar.add(toolsMenu);

        // Help menu
        JMenu helpMenu = new JMenu(lang.getString("menu.help"));
        JMenuItem about = new JMenuItem(lang.getString("menu.help.about"));
        helpMenu.add(about);
        bar.add(helpMenu);

        // === Actions ===
        importItem.addActionListener(e -> importExportController.doImport());
        exportItem.addActionListener(e -> importExportController.doExport());
        settings.addActionListener(e -> doSettings());
        lock.addActionListener(e -> doLock());
        quit.addActionListener(e -> doQuit());

        newEntry.addActionListener(e -> addNewEntryForActiveTab());
        editEntry.addActionListener(e -> editEntryForActiveTab());
        deleteEntry.addActionListener(e -> deleteEntryForActiveTab());
        changeMaster.addActionListener(e -> doChangeMasterPassword());

        refresh.addActionListener(e -> refreshAllPanels());
        sortName.addActionListener(e -> setSortModeForActiveTab(SortField.TITLE));
        sortUsername.addActionListener(e -> setSortModeForActiveTab(SortField.USERNAME));
        sortEmail.addActionListener(e -> setSortModeForActiveTab(SortField.EMAIL));
        sortUrl.addActionListener(e -> setSortModeForActiveTab(SortField.URL));
        sortDate.addActionListener(e -> setSortModeForActiveTab(SortField.DATE));
        sortCat.addActionListener(e -> setSortModeForActiveTab(SortField.CATEGORY));
        filterWeak.addActionListener(e -> securityAuditController.doFilterWeak());
        filterDup.addActionListener(e -> securityAuditController.doFilterDuplicate());

        generator.addActionListener(e ->
            new PasswordGeneratorDialog(MainFrame.this).setVisible(true));
        audit.addActionListener(e -> securityAuditController.doSecurityAudit());
        syncNowMenuItem.addActionListener(e -> doSync());
        about.addActionListener(e ->
            JOptionPane.showMessageDialog(MainFrame.this,
                lang.getString("about.description").replace("{0}", AppVersion.get()),
                lang.getString("about.title"), JOptionPane.INFORMATION_MESSAGE));

        return bar;
    }

    private JToolBar createToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        JButton newBtn = new JButton(lang.getString("vault.new_entry"));
        JButton genBtn = new JButton(lang.getString("menu.tools.generator"));
        JButton lockBtn = new JButton(lang.getString("menu.file.lock"));
        syncToolbarBtn = new JButton(lang.getString("menu.tools.sync_now"));
        syncToolbarBtn.setEnabled(appConfig.getStorageMode() == StorageMode.REMOTE);

        newBtn.addActionListener(e -> addNewEntryForActiveTab());
        genBtn.addActionListener(e -> new PasswordGeneratorDialog(MainFrame.this).setVisible(true));
        lockBtn.addActionListener(e -> doLock());
        syncToolbarBtn.addActionListener(e -> doSync());

        tb.add(newBtn);
        tb.addSeparator();
        tb.add(genBtn);
        tb.addSeparator();
        tb.add(syncToolbarBtn);
        tb.add(Box.createHorizontalGlue());
        tb.add(lockBtn);

        return tb;
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

    private void doSettings() {
        String oldLang = appConfig.getLanguage();
        ThemeMode oldTheme = appConfig.getTheme();

        SettingsDialog dlg = new SettingsDialog(this, appConfig, configManager,
            vaultService.getSshKeyService().getActiveList());
        dlg.setVisible(true);

        // Changing the working folder requires a clean re-login: save+wipe the current
        // session and return to the login screen, where the new folder is chosen. Checked
        // before isSaved() because this path intentionally discards other dialog edits.
        if (dlg.isWorkspaceChangeRequested()) {
            doLock();
            return;
        }
        if (!dlg.isSaved()) return;

        boolean langChanged = !oldLang.equals(appConfig.getLanguage());
        boolean themeChanged = oldTheme != appConfig.getTheme();

        if (langChanged) {
            // Rebuild entirely: the new MainFrame handles sync/status/clipboard/timer
            lang.setLanguage(appConfig.getLanguage());
            applyTheme();
            rebuildMainFrame();
        } else {
            // Apply live on current frame
            updateSyncVaultKey();
            syncService = DesktopSyncFactory.create(appConfig, vaultKeyBytes);
            statusLabel.setText(getStatusText());
            vaultPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
            appPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
            sshKeyPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
            autoLockManager.startAutoLock();
            boolean remoteEnabled = appConfig.getStorageMode() == StorageMode.REMOTE;
            syncNowMenuItem.setEnabled(remoteEnabled);
            syncToolbarBtn.setEnabled(remoteEnabled);

            if (themeChanged) {
                applyTheme();
                SwingUtilities.updateComponentTreeUI(this);
                pack();
                setSize(1100, 700);
            }
        }
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
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        cleanup();
        dispose();
        new MainFrame(vault, username, session, vaultManager, appConfig, configManager).setVisible(true);
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
        vaultPanel.cancelClipboardTimer();
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

    private void addNewEntryForActiveTab() {
        int idx = tabbedPane.getSelectedIndex();
        switch (idx) {
            case 1: appPanel.addNewEntry(); break;
            case 2: sshKeyPanel.addNewEntry(); break;
            default: vaultPanel.addNewEntry(); break;
        }
    }

    private void editEntryForActiveTab() {
        int idx = tabbedPane.getSelectedIndex();
        switch (idx) {
            case 1: appPanel.editSelectedEntry(); break;
            case 2: sshKeyPanel.editSelectedEntry(); break;
            default: vaultPanel.editSelectedEntry(); break;
        }
    }

    private void deleteEntryForActiveTab() {
        int idx = tabbedPane.getSelectedIndex();
        switch (idx) {
            case 1: appPanel.deleteSelectedEntry(); break;
            case 2: sshKeyPanel.deleteSelectedEntry(); break;
            default: vaultPanel.deleteSelectedEntry(); break;
        }
    }

    private void refreshAllPanels() {
        vaultPanel.refreshAll();
        appPanel.refreshEntries();
        sshKeyPanel.refreshEntries();
    }

    /**
     * Dispatches a sort-field request to the panel that corresponds to the
     * currently selected tab.  If the requested field is not valid for the
     * active panel, TITLE is used as a safe fallback.
     *
     * Valid fields per panel:
     *   Passwords  (0) - TITLE, USERNAME, EMAIL, URL, DATE, CATEGORY
     *   Applications (1) - TITLE, USERNAME, DATE
     *   SSH Keys (2) - TITLE, DATE
     */
    private void setSortModeForActiveTab(SortField field) {
        int idx = tabbedPane.getSelectedIndex();
        switch (idx) {
            case 1: // Applications
                switch (field) {
                    case TITLE:
                    case USERNAME:
                    case DATE:
                        appPanel.setSortMode(field);
                        break;
                    default:
                        appPanel.setSortMode(SortField.TITLE);
                        break;
                }
                break;
            case 2: // SSH Keys
                switch (field) {
                    case TITLE:
                    case DATE:
                        sshKeyPanel.setSortMode(field);
                        break;
                    default:
                        sshKeyPanel.setSortMode(SortField.TITLE);
                        break;
                }
                break;
            default: // Passwords (index 0)
                vaultPanel.setSortMode(field);
                break;
        }
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
