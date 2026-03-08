package com.passwordmanager.ui;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.config.StorageMode;
import com.passwordmanager.config.ThemeMode;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.sync.SFTPRepository;

import com.passwordmanager.vault.SshKeyEntry;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Settings dialog with tabs: General, Security, Synchronization.
 */
public class SettingsDialog extends JDialog {
    private final LanguageManager lang = LanguageManager.getInstance();
    private AppConfig config;
    private ConfigManager configManager;
    private boolean saved = false;

    // General
    private JComboBox<String> langCombo;
    private JComboBox<String> themeCombo;

    // Security
    private JSpinner autoLockSpinner;
    private JSpinner clipboardSpinner;

    // Sync
    private JRadioButton localRadio, remoteRadio;
    private JTextField hostField, userField, keyPathField, remotePathField;
    private JSpinner portSpinner;
    private JRadioButton keySourceFile, keySourceVault;
    private JComboBox<String> vaultKeyCombo;
    private List<SshKeyEntry> vaultKeys;

    public SettingsDialog(Frame owner, AppConfig config, ConfigManager configManager) {
        this(owner, config, configManager, List.of());
    }

    public SettingsDialog(Frame owner, AppConfig config, ConfigManager configManager, List<SshKeyEntry> vaultKeys) {
        super(owner, LanguageManager.getInstance().getString("settings.title"), true);
        this.config = config;
        this.configManager = configManager;
        this.vaultKeys = vaultKeys;
        initComponents();
    }

    private void initComponents() {
        JTabbedPane tabs = new JTabbedPane();

        // === General Tab ===
        JPanel generalPanel = new JPanel(new GridBagLayout());
        generalPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        generalPanel.add(new JLabel(lang.getString("settings.language")), g);
        g.gridx = 1; g.weightx = 1;
        langCombo = new JComboBox<>(new String[]{"Fran\u00e7ais", "English"});
        langCombo.setSelectedIndex("en".equals(config.getLanguage()) ? 1 : 0);
        generalPanel.add(langCombo, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        generalPanel.add(new JLabel(lang.getString("settings.theme")), g);
        g.gridx = 1; g.weightx = 1;
        themeCombo = new JComboBox<>(new String[]{
            lang.getString("settings.theme_system"),
            lang.getString("settings.theme_light"),
            lang.getString("settings.theme_dark")
        });
        switch (config.getTheme()) {
            case SYSTEM: themeCombo.setSelectedIndex(0); break;
            case LIGHT:  themeCombo.setSelectedIndex(1); break;
            case DARK:   themeCombo.setSelectedIndex(2); break;
        }
        generalPanel.add(themeCombo, g);

        g.gridx = 0; g.gridy = 2; g.weighty = 1;
        generalPanel.add(Box.createVerticalGlue(), g);

        tabs.addTab(lang.getString("settings.general"), generalPanel);

        // === Security Tab ===
        JPanel secPanel = new JPanel(new GridBagLayout());
        secPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints s = new GridBagConstraints();
        s.insets = new Insets(8, 8, 8, 8);
        s.fill = GridBagConstraints.HORIZONTAL;
        s.anchor = GridBagConstraints.WEST;

        s.gridx = 0; s.gridy = 0; s.weightx = 0;
        secPanel.add(new JLabel(lang.getString("settings.auto_lock")), s);
        s.gridx = 1; s.weightx = 1;
        autoLockSpinner = new JSpinner(new SpinnerNumberModel(config.getAutoLockMinutes(), 1, 60, 1));
        secPanel.add(autoLockSpinner, s);

        s.gridx = 0; s.gridy = 1; s.weightx = 0;
        secPanel.add(new JLabel(lang.getString("settings.clipboard_clear")), s);
        s.gridx = 1; s.weightx = 1;
        clipboardSpinner = new JSpinner(new SpinnerNumberModel(config.getClipboardClearSeconds(), 5, 120, 5));
        secPanel.add(clipboardSpinner, s);

        s.gridx = 0; s.gridy = 2; s.weighty = 1;
        secPanel.add(Box.createVerticalGlue(), s);

        tabs.addTab(lang.getString("settings.security"), secPanel);

        // === Sync Tab ===
        JPanel syncPanel = new JPanel(new GridBagLayout());
        syncPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        localRadio = new JRadioButton(lang.getString("settings.local_only"));
        remoteRadio = new JRadioButton(lang.getString("settings.remote_server"));
        ButtonGroup bg = new ButtonGroup();
        bg.add(localRadio);
        bg.add(remoteRadio);
        if (config.getStorageMode() == StorageMode.REMOTE) remoteRadio.setSelected(true);
        else localRadio.setSelected(true);

        syncPanel.add(localRadio, c);
        c.gridy = 1;
        syncPanel.add(remoteRadio, c);
        c.gridwidth = 1;

        c.gridy = 2; c.gridx = 0; c.weightx = 0;
        syncPanel.add(new JLabel(lang.getString("settings.host")), c);
        c.gridx = 1; c.weightx = 1;
        hostField = new JTextField(config.getSftpHost(), 20);
        syncPanel.add(hostField, c);

        c.gridy = 3; c.gridx = 0; c.weightx = 0;
        syncPanel.add(new JLabel(lang.getString("settings.port")), c);
        c.gridx = 1; c.weightx = 1;
        portSpinner = new JSpinner(new SpinnerNumberModel(config.getSftpPort(), 1, 65535, 1));
        syncPanel.add(portSpinner, c);

        c.gridy = 4; c.gridx = 0; c.weightx = 0;
        syncPanel.add(new JLabel(lang.getString("settings.user")), c);
        c.gridx = 1; c.weightx = 1;
        userField = new JTextField(config.getSftpUser(), 20);
        syncPanel.add(userField, c);

        c.gridy = 5; c.gridx = 0; c.weightx = 0;
        syncPanel.add(new JLabel(lang.getString("settings.ssh_key_source")), c);
        c.gridx = 1; c.weightx = 1;
        JPanel keySourcePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        keySourceFile = new JRadioButton(lang.getString("settings.ssh_key_source.file"));
        keySourceVault = new JRadioButton(lang.getString("settings.ssh_key_source.vault"));
        ButtonGroup keySourceGroup = new ButtonGroup();
        keySourceGroup.add(keySourceFile);
        keySourceGroup.add(keySourceVault);
        if (config.isUsingVaultKey()) keySourceVault.setSelected(true);
        else keySourceFile.setSelected(true);
        keySourcePanel.add(keySourceFile);
        keySourcePanel.add(keySourceVault);
        syncPanel.add(keySourcePanel, c);

        c.gridy = 6; c.gridx = 0; c.weightx = 0;
        syncPanel.add(new JLabel(lang.getString("settings.ssh_key")), c);
        c.gridx = 1; c.weightx = 1;
        JPanel keyPanel = new JPanel(new CardLayout());
        JPanel fileKeyPanel = new JPanel(new BorderLayout(5, 0));
        keyPathField = new JTextField(config.getSftpKeyPath(), 15);
        JButton browseBtn = new JButton(lang.getString("common.browse"));
        fileKeyPanel.add(keyPathField, BorderLayout.CENTER);
        fileKeyPanel.add(browseBtn, BorderLayout.EAST);
        vaultKeyCombo = new JComboBox<>();
        int selectedVaultKeyIdx = -1;
        for (int i = 0; i < vaultKeys.size(); i++) {
            SshKeyEntry vk = vaultKeys.get(i);
            vaultKeyCombo.addItem(vk.getTitle() + " (" + vk.getKeyType() + ")");
            if (vk.getId().equals(config.getSftpVaultKeyId())) selectedVaultKeyIdx = i;
        }
        if (selectedVaultKeyIdx >= 0) vaultKeyCombo.setSelectedIndex(selectedVaultKeyIdx);
        keyPanel.add(fileKeyPanel, "file");
        keyPanel.add(vaultKeyCombo, "vault");
        CardLayout keyCardLayout = (CardLayout) keyPanel.getLayout();
        keyCardLayout.show(keyPanel, config.isUsingVaultKey() ? "vault" : "file");
        syncPanel.add(keyPanel, c);

        keySourceFile.addActionListener(e -> keyCardLayout.show(keyPanel, "file"));
        keySourceVault.addActionListener(e -> keyCardLayout.show(keyPanel, "vault"));

        c.gridy = 7; c.gridx = 0; c.weightx = 0;
        syncPanel.add(new JLabel(lang.getString("settings.remote_path")), c);
        c.gridx = 1; c.weightx = 1;
        remotePathField = new JTextField(config.getSftpRemotePath(), 20);
        syncPanel.add(remotePathField, c);

        c.gridy = 8; c.gridx = 1;
        JButton testBtn = new JButton(lang.getString("settings.test_connection"));
        syncPanel.add(testBtn, c);

        c.gridy = 9; c.weighty = 1;
        syncPanel.add(Box.createVerticalGlue(), c);

        tabs.addTab(lang.getString("settings.sync"), syncPanel);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);

        // Bottom buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton(lang.getString("common.cancel"));
        JButton saveBtn = new JButton(lang.getString("common.apply"));
        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // Actions
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(SettingsDialog.this) == JFileChooser.APPROVE_OPTION) {
                keyPathField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        testBtn.addActionListener(e -> {
            testBtn.setEnabled(false);
            testBtn.setText(lang.getString("settings.test_connection") + "...");
            SFTPRepository repo = new SFTPRepository(
                hostField.getText(), (Integer) portSpinner.getValue(),
                userField.getText(), keyPathField.getText(), remotePathField.getText());
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return repo.testConnection();
                }
                @Override
                protected void done() {
                    testBtn.setEnabled(true);
                    testBtn.setText(lang.getString("settings.test_connection"));
                    try {
                        boolean ok = get();
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                            ok ? lang.getString("settings.connection_ok") : lang.getString("settings.connection_fail"),
                            lang.getString("settings.test_connection"),
                            ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                            lang.getString("settings.connection_fail"),
                            lang.getString("settings.test_connection"),
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        cancelBtn.addActionListener(e -> dispose());
        saveBtn.addActionListener(e -> doSave());

        pack();
        setMinimumSize(new Dimension(500, 450));
        setLocationRelativeTo(getOwner());
    }

    private void doSave() {
        // SYNC-04: Validate all required SFTP fields when remote mode is selected
        if (remoteRadio.isSelected()) {
            if (hostField.getText().trim().isEmpty()) {
                showValidationError(lang.getString("settings.host"));
                return;
            }
            if (userField.getText().trim().isEmpty()) {
                showValidationError(lang.getString("settings.user"));
                return;
            }
            if (keySourceFile.isSelected()) {
                String keyPath = keyPathField.getText().trim();
                if (keyPath.isEmpty()) {
                    showValidationError(lang.getString("settings.ssh_key"));
                    return;
                }
                File keyFile = new File(keyPath);
                if (!keyFile.exists() || !keyFile.isFile()) {
                    JOptionPane.showMessageDialog(this,
                        lang.getString("settings.ssh_key") + ": " + lang.getString("error.file_not_found"),
                        lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (!keyFile.canRead()) {
                    JOptionPane.showMessageDialog(this,
                        lang.getString("settings.ssh_key") + ": " + lang.getString("error.file_not_readable"),
                        lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                if (vaultKeys.isEmpty() || vaultKeyCombo.getSelectedIndex() < 0) {
                    showValidationError(lang.getString("settings.ssh_key"));
                    return;
                }
            }
            if (remotePathField.getText().trim().isEmpty()) {
                showValidationError(lang.getString("settings.remote_path"));
                return;
            }
        }

        config.setLanguage(langCombo.getSelectedIndex() == 0 ? "fr" : "en");
        ThemeMode[] themes = { ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK };
        config.setTheme(themes[themeCombo.getSelectedIndex()]);
        config.setAutoLockMinutes((Integer) autoLockSpinner.getValue());
        config.setClipboardClearSeconds((Integer) clipboardSpinner.getValue());
        config.setStorageMode(remoteRadio.isSelected() ? StorageMode.REMOTE : StorageMode.LOCAL);
        config.setSftpHost(hostField.getText());
        config.setSftpPort((Integer) portSpinner.getValue());
        config.setSftpUser(userField.getText());
        if (keySourceVault.isSelected() && vaultKeyCombo.getSelectedIndex() >= 0) {
            config.setSftpVaultKeyId(vaultKeys.get(vaultKeyCombo.getSelectedIndex()).getId());
            config.setSftpKeyPath("");
        } else {
            config.setSftpVaultKeyId("");
            config.setSftpKeyPath(keyPathField.getText());
        }
        config.setSftpRemotePath(remotePathField.getText());

        configManager.saveConfig(config);
        saved = true;
        dispose();
    }

    private void showValidationError(String fieldLabel) {
        JOptionPane.showMessageDialog(this,
            fieldLabel + " " + lang.getString("error.field_required"),
            lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
    }

    public boolean isSaved() { return saved; }
}
