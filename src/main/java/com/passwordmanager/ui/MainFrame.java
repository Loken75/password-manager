package com.passwordmanager.ui;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.sync.SyncService;
import com.passwordmanager.vault.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Main application window with menu bar, vault panel, and status bar.
 */
public class MainFrame extends JFrame {
    private final LanguageManager lang = LanguageManager.getInstance();
    private Vault vault;
    private String username;
    private char[] masterPassword;
    private VaultManager vaultManager;
    private VaultService vaultService;
    private AppConfig appConfig;
    private ConfigManager configManager;
    private SyncService syncService;
    private VaultPanel vaultPanel;
    private JLabel statusLabel;
    private Timer autoLockTimer;
    private long lastActivity;

    public MainFrame(Vault vault, String username, char[] masterPassword,
                     VaultManager vaultManager, AppConfig appConfig, ConfigManager configManager) {
        this.vault = vault;
        this.username = username;
        this.masterPassword = masterPassword;
        this.vaultManager = vaultManager;
        this.appConfig = appConfig;
        this.configManager = configManager;
        this.vaultService = new VaultService(vault);
        this.syncService = new SyncService(appConfig);
        this.lastActivity = System.currentTimeMillis();
        initComponents();
        startAutoLock();
    }

    private void initComponents() {
        setTitle(lang.getString("app.title") + " - " + username);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { doQuit(); }
        });

        // Track activity for auto-lock
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            public void eventDispatched(AWTEvent event) { lastActivity = System.currentTimeMillis(); }
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);

        // Menu bar
        setJMenuBar(createMenuBar());

        // Toolbar
        JToolBar toolbar = createToolBar();
        add(toolbar, BorderLayout.NORTH);

        // Vault panel
        vaultPanel = new VaultPanel(vaultService, appConfig.getClipboardClearSeconds());
        vaultPanel.setOnVaultChanged(new Runnable() {
            public void run() { saveVault(); }
        });
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
        JMenuItem importCsv = new JMenuItem(lang.getString("menu.file.import_csv"));
        JMenuItem importJson = new JMenuItem(lang.getString("menu.file.import_json"));
        JMenuItem exportCsv = new JMenuItem(lang.getString("menu.file.export_csv"));
        JMenuItem exportJson = new JMenuItem(lang.getString("menu.file.export_json"));
        JMenuItem exportBackup = new JMenuItem(lang.getString("menu.file.export_backup"));
        JMenuItem settings = new JMenuItem(lang.getString("menu.file.settings"));
        JMenuItem lock = new JMenuItem(lang.getString("menu.file.lock"));
        JMenuItem quit = new JMenuItem(lang.getString("menu.file.quit"));

        fileMenu.add(importCsv);
        fileMenu.add(importJson);
        fileMenu.addSeparator();
        fileMenu.add(exportCsv);
        fileMenu.add(exportJson);
        fileMenu.add(exportBackup);
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
        JMenuItem sortDate = new JMenuItem(lang.getString("menu.view.sort_date"));
        JMenuItem sortCat = new JMenuItem(lang.getString("menu.view.sort_category"));
        JMenuItem filterWeak = new JMenuItem(lang.getString("menu.view.filter_weak"));
        JMenuItem filterDup = new JMenuItem(lang.getString("menu.view.filter_duplicate"));

        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));

        viewMenu.add(refresh);
        viewMenu.addSeparator();
        viewMenu.add(sortName);
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
        importCsv.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doImport("csv"); } });
        importJson.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doImport("json"); } });
        exportCsv.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doExport("csv"); } });
        exportJson.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doExport("json"); } });
        exportBackup.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doExportBackup(); } });
        settings.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doSettings(); } });
        lock.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doLock(); } });
        quit.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doQuit(); } });

        newEntry.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { vaultPanel.addNewEntry(); } });
        editEntry.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { vaultPanel.editSelectedEntry(); } });
        deleteEntry.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { vaultPanel.deleteSelectedEntry(); } });
        changeMaster.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doChangeMasterPassword(); } });

        refresh.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { vaultPanel.refreshAll(); } });
        sortName.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { vaultPanel.setSortMode("title"); } });
        sortDate.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { vaultPanel.setSortMode("date"); } });
        sortCat.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { vaultPanel.setSortMode("category"); } });
        filterWeak.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doFilterWeak(); } });
        filterDup.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doFilterDuplicate(); } });

        generator.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new PasswordGeneratorDialog(MainFrame.this).setVisible(true);
            }
        });
        audit.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doSecurityAudit(); } });
        syncNow.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doSync(); } });
        about.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(MainFrame.this,
                    lang.getString("about.description"),
                    lang.getString("about.title"), JOptionPane.INFORMATION_MESSAGE);
            }
        });

        return bar;
    }

    private JToolBar createToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        JButton newBtn = new JButton(lang.getString("vault.new_entry"));
        JButton genBtn = new JButton(lang.getString("menu.tools.generator"));
        JButton lockBtn = new JButton(lang.getString("menu.file.lock"));
        JButton syncBtn = new JButton(lang.getString("menu.tools.sync_now"));

        newBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { vaultPanel.addNewEntry(); } });
        genBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { new PasswordGeneratorDialog(MainFrame.this).setVisible(true); }
        });
        lockBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doLock(); } });
        syncBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doSync(); } });

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
            vaultManager.saveVault(vault, username, masterPassword);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                lang.getString("common.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doImport(String format) {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String content = new String(Files.readAllBytes(fc.getSelectedFile().toPath()), StandardCharsets.UTF_8);
            int count;
            if ("csv".equals(format)) {
                count = vaultManager.importFromCsv(vault, content);
            } else {
                count = vaultManager.importFromJson(vault, content);
            }
            saveVault();
            vaultPanel.refreshAll();
            JOptionPane.showMessageDialog(this,
                lang.getString("import.success").replace("{0}", String.valueOf(count)),
                lang.getString("import.title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                lang.getString("import.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doExport(String format) {
        int confirm = JOptionPane.showConfirmDialog(this,
            lang.getString("export.warning"),
            lang.getString("export.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("vault_export." + format));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String content;
            if ("csv".equals(format)) {
                content = vaultManager.exportAsCsv(username, masterPassword);
            } else {
                content = vaultManager.exportAsJson(username, masterPassword);
            }
            Files.write(fc.getSelectedFile().toPath(), content.getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(this,
                lang.getString("export.success"),
                lang.getString("export.title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                lang.getString("export.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doExportBackup() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("vault_" + username + "_backup.enc"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            vaultManager.exportBackup(username, masterPassword, fc.getSelectedFile().getAbsolutePath());
            JOptionPane.showMessageDialog(this,
                lang.getString("export.success"),
                lang.getString("export.title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                lang.getString("export.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doSettings() {
        SettingsDialog dlg = new SettingsDialog(this, appConfig, configManager);
        dlg.setVisible(true);
        if (dlg.isSaved()) {
            statusLabel.setText(getStatusText());
        }
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
                if (!Arrays.equals(op, masterPassword)) {
                    showError(lang.getString("error.invalid_password"));
                    return;
                }
                if (!Arrays.equals(np, cp)) {
                    showError(lang.getString("security.password_mismatch"));
                    return;
                }
                if (!validateMasterPassword(np)) {
                    showError(lang.getString("security.password_requirements"));
                    return;
                }
                vaultManager.changeMasterPassword(username, op, np);
                Arrays.fill(masterPassword, ' ');
                masterPassword = Arrays.copyOf(np, np.length);
                JOptionPane.showMessageDialog(this, lang.getString("security.password_changed"),
                    lang.getString("common.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showError(ex.getMessage());
            } finally {
                Arrays.fill(op, ' ');
                Arrays.fill(np, ' ');
                Arrays.fill(cp, ' ');
            }
        }
    }

    private void doLock() {
        saveVault();
        Arrays.fill(masterPassword, ' ');
        dispose();
        new LoginFrame().setVisible(true);
    }

    private void doQuit() {
        saveVault();
        Arrays.fill(masterPassword, ' ');
        System.exit(0);
    }

    private void doSync() {
        SyncService.SyncResult result = syncService.synchronize("vault_" + username + ".enc");
        statusLabel.setText(getStatusText());
        if (!result.isSuccess() && "CONFLICT".equals(result.getMessage())) {
            handleConflict();
        } else if (!result.isSuccess()) {
            JOptionPane.showMessageDialog(this, result.getMessage(),
                lang.getString("sync.status_error"), JOptionPane.WARNING_MESSAGE);
        }
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

        SyncService.SyncResult result = syncService.resolveConflict("vault_" + username + ".enc", resolution);
        if (resolution != com.passwordmanager.sync.ConflictResolver.KEEP_LOCAL) {
            try {
                vault = vaultManager.loadVault(username, masterPassword);
                vaultService.setVault(vault);
                vaultPanel.refreshAll();
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        }
        statusLabel.setText(getStatusText());
    }

    private void doFilterWeak() {
        StringBuilder sb = new StringBuilder();
        for (VaultEntry e : vault.getEntries()) {
            PasswordStrengthAnalyzer.Strength s = PasswordStrengthAnalyzer.analyze(e.getPassword());
            if (s == PasswordStrengthAnalyzer.Strength.WEAK) {
                sb.append("- ").append(e.getTitle()).append("\n");
            }
        }
        showAuditResult(lang.getString("menu.view.filter_weak"), sb);
    }

    private void doFilterDuplicate() {
        Map<String, List<VaultEntry>> dups = vaultService.findDuplicatePasswords();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<VaultEntry>> entry : dups.entrySet()) {
            for (VaultEntry e : entry.getValue()) {
                sb.append("- ").append(e.getTitle()).append("\n");
            }
            sb.append("\n");
        }
        showAuditResult(lang.getString("menu.view.filter_duplicate"), sb);
    }

    private void doSecurityAudit() {
        StringBuilder sb = new StringBuilder();
        int issues = 0;

        // Weak passwords
        sb.append("=== ").append(lang.getString("audit.weak_passwords")).append(" ===\n");
        for (VaultEntry e : vault.getEntries()) {
            if (PasswordStrengthAnalyzer.analyze(e.getPassword()) == PasswordStrengthAnalyzer.Strength.WEAK) {
                sb.append("  - ").append(e.getTitle()).append("\n");
                issues++;
            }
        }

        // Duplicates
        sb.append("\n=== ").append(lang.getString("audit.duplicate_passwords")).append(" ===\n");
        Map<String, List<VaultEntry>> dups = vaultService.findDuplicatePasswords();
        for (Map.Entry<String, List<VaultEntry>> entry : dups.entrySet()) {
            for (VaultEntry e : entry.getValue()) {
                sb.append("  - ").append(e.getTitle()).append("\n");
                issues++;
            }
        }

        // Old passwords
        int expiryDays = 180;
        Object expiry = vault.getSettings().get("password_expiry_days");
        if (expiry instanceof Number) expiryDays = ((Number) expiry).intValue();
        sb.append("\n=== ").append(lang.getString("audit.old_passwords").replace("{0}", String.valueOf(expiryDays))).append(" ===\n");
        for (VaultEntry e : vaultService.findOldPasswords(expiryDays)) {
            sb.append("  - ").append(e.getTitle()).append(" (").append(e.getUpdatedAt()).append(")\n");
            issues++;
        }

        if (issues == 0) {
            sb.append("\n").append(lang.getString("audit.no_issues"));
        } else {
            sb.insert(0, lang.getString("audit.issues_found").replace("{0}", String.valueOf(issues)) + "\n\n");
        }

        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(500, 400));
        JOptionPane.showMessageDialog(this, scroll, lang.getString("audit.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAuditResult(String title, StringBuilder sb) {
        if (sb.length() == 0) sb.append(lang.getString("audit.no_issues"));
        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(400, 300));
        JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void startAutoLock() {
        autoLockTimer = new Timer(30000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                long idle = System.currentTimeMillis() - lastActivity;
                if (idle > appConfig.getAutoLockMinutes() * 60 * 1000L) {
                    doLock();
                }
            }
        });
        autoLockTimer.start();
    }

    private String getStatusText() {
        String mode = "local".equals(appConfig.getStorageMode())
            ? lang.getString("sync.status_local")
            : syncService.getSyncStatus();
        return mode + "  |  " + username + "  |  " + vault.getEntries().size() + " " + lang.getString("vault.new_entry").toLowerCase() + "(s)";
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
