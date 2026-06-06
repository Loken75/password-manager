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
    private static final String VIEW_SSH = "ssh";

    private CoffrePasswordsPanel coffrePasswordsPanel;
    private AppPanel appPanel;
    private SshKeyPanel sshKeyPanel;
    private JPanel contentCards;
    private CardLayout contentLayout;
    private String currentView = VIEW_PASSWORDS;
    private JTextField toolbarSearch;
    private DefaultListModel<String> sideCategoryModel;
    private JList<String> sideCategoryList;
    private final java.util.Map<String, JToggleButton> typeButtons = new java.util.LinkedHashMap<>();
    private JPanel categorySection;
    private JLabel statusLabel;
    private Thread shutdownHook;

    // Extracted controllers
    private ImportExportController importExportController;
    private SecurityAuditController securityAuditController;
    private AutoLockManager autoLockManager;
    private DesktopUpdateManager updateManager;

    // Toolbar buttons that need dynamic state
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

        appPanel = new AppPanel(vaultService.getAppService(), appConfig.getClipboardClearSeconds());
        appPanel.setOnVaultChanged(() -> { saveVault(); statusLabel.setText(getStatusText()); refreshTypeCounts(); });

        sshKeyPanel = new SshKeyPanel(vaultService.getSshKeyService(), appConfig.getClipboardClearSeconds());
        sshKeyPanel.setOnVaultChanged(() -> { saveVault(); statusLabel.setText(getStatusText()); refreshTypeCounts(); });

        contentLayout = new CardLayout();
        contentCards = new JPanel(contentLayout);
        contentCards.add(coffrePasswordsPanel, VIEW_PASSWORDS);
        contentCards.add(appPanel, VIEW_APPS);
        contentCards.add(sshKeyPanel, VIEW_SSH);

        // North: update notification bar + calm toolbar
        updateManager = new DesktopUpdateManager();
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(updateManager.createNotificationBar(), BorderLayout.NORTH);
        northPanel.add(createToolBar(), BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);
        updateManager.startPeriodicCheck();

        // West: sidebar (type nav + categories + audit/settings)
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
        switchView(VIEW_PASSWORDS);
    }

    private JComponent createToolBar() {
        JPanel bar = new JPanel(new BorderLayout(DesignTokens.SPACE_MD, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, DesignTokens.SPACE_LG, DesignTokens.SPACE_MD, DesignTokens.SPACE_LG)));

        JLabel brand = new JLabel(lang.getString("app.brand"));
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 17f));

        toolbarSearch = new JTextField();
        toolbarSearch.putClientProperty("JTextField.placeholderText", lang.getString("vault.search"));
        toolbarSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onSearch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onSearch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onSearch(); }
        });
        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.add(toolbarSearch, BorderLayout.CENTER);
        searchWrap.setBorder(BorderFactory.createEmptyBorder(0, DesignTokens.SPACE_LG, 0, DesignTokens.SPACE_LG));

        JButton newBtn = Buttons.primary("+ " + lang.getString("vault.new_entry"));
        newBtn.addActionListener(e -> addNewEntryForActiveView());
        syncToolbarBtn = Buttons.tonal(lang.getString("menu.tools.sync_now"));
        syncToolbarBtn.setEnabled(appConfig.getStorageMode() == StorageMode.REMOTE);
        syncToolbarBtn.addActionListener(e -> doSync());
        JButton lockBtn = new JButton(lang.getString("menu.file.lock"));
        lockBtn.addActionListener(e -> doLock());
        JButton overflow = new JButton("⋯");
        overflow.setToolTipText(lang.getString("menu.file"));
        overflow.addActionListener(e -> overflowMenu().show(overflow, 0, overflow.getHeight()));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, 0));
        right.add(syncToolbarBtn);
        right.add(newBtn);
        right.add(lockBtn);
        right.add(overflow);

        bar.add(brand, BorderLayout.WEST);
        bar.add(searchWrap, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent createSidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_LG, DesignTokens.SPACE_MD, DesignTokens.SPACE_LG, DesignTokens.SPACE_MD)));
        side.setPreferredSize(new Dimension(234, 0));

        side.add(navTitle(lang.getString("menu.view")));
        side.add(typeNav(VIEW_PASSWORDS, lang.getString("tab.passwords")));
        side.add(typeNav(VIEW_APPS, lang.getString("tab.applications")));
        side.add(typeNav(VIEW_SSH, lang.getString("tab.ssh_keys")));
        side.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        // Categories (passwords only)
        categorySection = new JPanel();
        categorySection.setOpaque(false);
        categorySection.setLayout(new BoxLayout(categorySection, BoxLayout.Y_AXIS));
        categorySection.setAlignmentX(Component.LEFT_ALIGNMENT);
        categorySection.add(navTitle(lang.getString("entry.category")));
        sideCategoryModel = new DefaultListModel<>();
        sideCategoryList = new JList<>(sideCategoryModel);
        sideCategoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sideCategoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = sideCategoryList.getSelectedValue();
                String cat = (sel == null || sel.equals(lang.getString("category.all"))) ? null : sel;
                coffrePasswordsPanel.setCategory(cat);
            }
        });
        JScrollPane catScroll = new JScrollPane(sideCategoryList);
        catScroll.setBorder(null);
        catScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        catScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        categorySection.add(catScroll);
        JPanel catBtns = new JPanel(new GridLayout(1, 2, 4, 0));
        catBtns.setOpaque(false);
        catBtns.setAlignmentX(Component.LEFT_ALIGNMENT);
        catBtns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JButton addCat = new JButton(lang.getString("category.add"));
        JButton delCat = new JButton(lang.getString("category.delete"));
        addCat.addActionListener(e -> addCategory());
        delCat.addActionListener(e -> deleteCategory());
        catBtns.add(addCat);
        catBtns.add(delCat);
        categorySection.add(Box.createVerticalStrut(4));
        categorySection.add(catBtns);
        side.add(categorySection);

        side.add(Box.createVerticalGlue());
        side.add(sidebarAction(lang.getString("menu.tools.security_audit"), () -> securityAuditController.doSecurityAudit()));
        side.add(sidebarAction(lang.getString("menu.file.settings"), this::doSettings));

        refreshSideCategories();
        refreshTypeCounts();
        return side;
    }

    private JComponent navTitle(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(DesignTokens.onSurfaceFaint());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(6, 6, 8, 6));
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

    private JButton sidebarAction(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setForeground(DesignTokens.onSurfaceFaint());
        b.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
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
        contentLayout.show(contentCards, view);
        typeButtons.forEach((k, b) -> styleNav(b, k.equals(view)));
        if (categorySection != null) categorySection.setVisible(VIEW_PASSWORDS.equals(view));
        if (VIEW_PASSWORDS.equals(view)) refreshSideCategories();
        revalidate();
        repaint();
    }

    private void refreshSideCategories() {
        if (sideCategoryModel == null) return;
        String prev = sideCategoryList.getSelectedValue();
        sideCategoryModel.clear();
        sideCategoryModel.addElement(lang.getString("category.all"));
        for (String c : vaultService.getVault().getCategories()) {
            sideCategoryModel.addElement(c);
        }
        if (prev != null) sideCategoryList.setSelectedValue(prev, false);
    }

    private void refreshTypeCounts() {
        setNavCount(VIEW_PASSWORDS, vault.getEntries().size());
        setNavCount(VIEW_APPS, vault.getAppEntries().size());
        setNavCount(VIEW_SSH, vault.getSshKeyEntries().size());
    }

    private void setNavCount(String view, int count) {
        JToggleButton b = typeButtons.get(view);
        if (b != null) {
            Object base = b.getClientProperty("baseLabel");
            b.setText((base != null ? base.toString() : "") + "   " + count);
        }
    }

    private void addCategory() {
        String name = JOptionPane.showInputDialog(this, lang.getString("category.new"),
            lang.getString("category.add"), JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            vaultService.addCategory(name.trim());
            refreshSideCategories();
            saveVault();
        }
    }

    private void deleteCategory() {
        String sel = sideCategoryList.getSelectedValue();
        if (sel == null || sel.equals(lang.getString("category.all"))) return;
        int c = JOptionPane.showConfirmDialog(this, lang.getString("category.delete_confirm"),
            lang.getString("category.delete"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c == JOptionPane.YES_OPTION) {
            for (PasswordEntry e : vaultService.search("")) {
                if (sel.equals(e.getCategory())) e.setCategory("");
            }
            vaultService.removeCategory(sel);
            refreshSideCategories();
            coffrePasswordsPanel.refresh();
            saveVault();
        }
    }

    private JPopupMenu overflowMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem imp = new JMenuItem(lang.getString("menu.file.import"));
        imp.addActionListener(e -> importExportController.doImport());
        m.add(imp);
        JMenuItem exp = new JMenuItem(lang.getString("menu.file.export"));
        exp.addActionListener(e -> importExportController.doExport());
        m.add(exp);
        m.addSeparator();
        JMenuItem gen = new JMenuItem(lang.getString("menu.tools.generator"));
        gen.addActionListener(e -> new PasswordGeneratorDialog(MainFrame.this).setVisible(true));
        m.add(gen);
        JMenuItem cm = new JMenuItem(lang.getString("menu.edit.change_master"));
        cm.addActionListener(e -> doChangeMasterPassword());
        m.add(cm);
        m.addSeparator();
        JMenuItem about = new JMenuItem(lang.getString("menu.help.about"));
        about.addActionListener(e -> JOptionPane.showMessageDialog(MainFrame.this,
            lang.getString("about.description").replace("{0}", AppVersion.get()),
            lang.getString("about.title"), JOptionPane.INFORMATION_MESSAGE));
        m.add(about);
        return m;
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
    }

    private void onSearch() {
        if (coffrePasswordsPanel != null) {
            coffrePasswordsPanel.getSearchField().setText(toolbarSearch.getText());
        }
    }

    private void addNewEntryForActiveView() {
        switch (currentView) {
            case VIEW_APPS: appPanel.addNewEntry(); break;
            case VIEW_SSH: sshKeyPanel.addNewEntry(); break;
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
            coffrePasswordsPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
            appPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
            sshKeyPanel.setClipboardClearSeconds(appConfig.getClipboardClearSeconds());
            coffrePasswordsPanel.setFaviconsEnabled(appConfig.isFaviconsEnabled());
            autoLockManager.startAutoLock();
            boolean remoteEnabled = appConfig.getStorageMode() == StorageMode.REMOTE;
            syncToolbarBtn.setEnabled(remoteEnabled);

            if (themeChanged) {
                applyTheme();
                SwingUtilities.updateComponentTreeUI(this);
                pack();
                setSize(1180, 760);
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
        appPanel.refreshEntries();
        sshKeyPanel.refreshEntries();
        refreshSideCategories();
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
