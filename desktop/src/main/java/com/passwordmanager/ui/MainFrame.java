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
import com.passwordmanager.sync.SyncService;
import com.passwordmanager.util.PasswordValidator;
import com.passwordmanager.update.DesktopUpdateManager;
import com.passwordmanager.vault.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.Arrays;

/**
 * Main application window with menu bar, vault panel, and status bar.
 * Holds the VaultSession (DEK) instead of the master password.
 * Delegates import/export, security audit, and auto-lock to dedicated controllers.
 */
public class MainFrame extends JFrame {
    private final LanguageManager lang = LanguageManager.getInstance();
    private Vault vault;
    private String username;
    private VaultSession session;
    private VaultManager vaultManager;
    private VaultService vaultService;
    private AppConfig appConfig;
    private ConfigManager configManager;
    private SyncService syncService;
    private VaultPanel vaultPanel;
    private JLabel statusLabel;
    private Thread shutdownHook;

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
        this.syncService = new SyncService(appConfig);

        // Initialize controllers
        this.importExportController = new ImportExportController(
            this, vault, username, session, vaultManager,
            this::saveVault, () -> vaultPanel.refreshAll());
        this.securityAuditController = new SecurityAuditController(this, vault, vaultService);
        this.autoLockManager = new AutoLockManager(appConfig, this::doLock);

        initComponents();
        autoLockManager.startAutoLock();
        shutdownHook = new Thread(() -> {
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

        // Vault panel
        vaultPanel = new VaultPanel(vaultService, appConfig.getClipboardClearSeconds());
        vaultPanel.setOnVaultChanged(this::saveVault);
        add(vaultPanel, BorderLayout.CENTER);

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
        JMenuItem sortPseudo = new JMenuItem(lang.getString("menu.view.sort_pseudo"));
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
        viewMenu.add(sortPseudo);
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
        JMenuItem syncNow = new JMenuItem(lang.getString("menu.tools.sync_now"));

        toolsMenu.add(generator);
        toolsMenu.add(audit);
        toolsMenu.addSeparator();
        toolsMenu.add(syncNow);
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

        newEntry.addActionListener(e -> vaultPanel.addNewEntry());
        editEntry.addActionListener(e -> vaultPanel.editSelectedEntry());
        deleteEntry.addActionListener(e -> vaultPanel.deleteSelectedEntry());
        changeMaster.addActionListener(e -> doChangeMasterPassword());

        refresh.addActionListener(e -> vaultPanel.refreshAll());
        sortName.addActionListener(e -> vaultPanel.setSortMode(SortField.TITLE));
        sortUsername.addActionListener(e -> vaultPanel.setSortMode(SortField.USERNAME));
        sortEmail.addActionListener(e -> vaultPanel.setSortMode(SortField.EMAIL));
        sortPseudo.addActionListener(e -> vaultPanel.setSortMode(SortField.PSEUDO));
        sortUrl.addActionListener(e -> vaultPanel.setSortMode(SortField.URL));
        sortDate.addActionListener(e -> vaultPanel.setSortMode(SortField.DATE));
        sortCat.addActionListener(e -> vaultPanel.setSortMode(SortField.CATEGORY));
        filterWeak.addActionListener(e -> securityAuditController.doFilterWeak());
        filterDup.addActionListener(e -> securityAuditController.doFilterDuplicate());

        generator.addActionListener(e ->
            new PasswordGeneratorDialog(MainFrame.this).setVisible(true));
        audit.addActionListener(e -> securityAuditController.doSecurityAudit());
        syncNow.addActionListener(e -> doSync());
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
        JButton syncBtn = new JButton(lang.getString("menu.tools.sync_now"));

        newBtn.addActionListener(e -> vaultPanel.addNewEntry());
        genBtn.addActionListener(e -> new PasswordGeneratorDialog(MainFrame.this).setVisible(true));
        lockBtn.addActionListener(e -> doLock());
        syncBtn.addActionListener(e -> doSync());

        tb.add(newBtn);
        tb.addSeparator();
        tb.add(genBtn);
        tb.addSeparator();
        tb.add(syncBtn);
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

    private void doSettings() {
        String oldLang = appConfig.getLanguage();
        ThemeMode oldTheme = appConfig.getTheme();

        SettingsDialog dlg = new SettingsDialog(this, appConfig, configManager);
        dlg.setVisible(true);
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
            syncService.refreshConfig(appConfig);
            statusLabel.setText(getStatusText());
            vaultPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
            autoLockManager.startAutoLock();

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
        } catch (Exception ignored) {
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
                        handleConflict();
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

    private void handleConflict() {
        Object[] options = {
            lang.getString("sync.keep_local"),
            lang.getString("sync.keep_remote"),
            lang.getString("sync.keep_both")
        };
        int choice = JOptionPane.showOptionDialog(this,
            lang.getString("sync.conflict_message"),
            lang.getString("sync.conflict_title"),
            JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

        com.passwordmanager.sync.ConflictResolver resolution;
        switch (choice) {
            case 1: resolution = com.passwordmanager.sync.ConflictResolver.KEEP_REMOTE; break;
            case 2: resolution = com.passwordmanager.sync.ConflictResolver.KEEP_BOTH; break;
            default: resolution = com.passwordmanager.sync.ConflictResolver.KEEP_LOCAL;
        }

        syncService.resolveConflict("vault_" + username + ".enc", resolution);
        if (resolution != com.passwordmanager.sync.ConflictResolver.KEEP_LOCAL) {
            try {
                Vault reloaded = vaultManager.reloadVault(username, session);
                vault = reloaded;
                vaultService.setVault(vault);
                vaultPanel.refreshAll();
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        }
        statusLabel.setText(getStatusText());
    }

    private String getStatusText() {
        String mode = appConfig.getStorageMode() == StorageMode.LOCAL
            ? lang.getString("sync.status_local")
            : syncService.getSyncStatus();
        return mode + "  |  " + username + "  |  " + vault.getEntries().size() + " " + lang.getString("vault.entries");
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
