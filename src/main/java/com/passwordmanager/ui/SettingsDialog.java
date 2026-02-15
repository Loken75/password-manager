package com.passwordmanager.ui;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.config.StorageMode;
import com.passwordmanager.config.ThemeMode;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.sync.SFTPRepository;

import javax.swing.*;
import java.awt.*;

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

    public SettingsDialog(Frame owner, AppConfig config, ConfigManager configManager) {
        super(owner, LanguageManager.getInstance().getString("settings.title"), true);
        this.config = config;
        this.configManager = configManager;
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
            lang.getString("settings.theme_light"),
            lang.getString("settings.theme_dark")
        });
        themeCombo.setSelectedIndex(config.getTheme() == ThemeMode.DARK ? 1 : 0);
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
        syncPanel.add(new JLabel(lang.getString("settings.ssh_key")), c);
        c.gridx = 1; c.weightx = 1;
        JPanel keyPanel = new JPanel(new BorderLayout(5, 0));
        keyPathField = new JTextField(config.getSftpKeyPath(), 15);
        JButton browseBtn = new JButton(lang.getString("common.browse"));
        keyPanel.add(keyPathField, BorderLayout.CENTER);
        keyPanel.add(browseBtn, BorderLayout.EAST);
        syncPanel.add(keyPanel, c);

        c.gridy = 6; c.gridx = 0; c.weightx = 0;
        syncPanel.add(new JLabel(lang.getString("settings.remote_path")), c);
        c.gridx = 1; c.weightx = 1;
        remotePathField = new JTextField(config.getSftpRemotePath(), 20);
        syncPanel.add(remotePathField, c);

        c.gridy = 7; c.gridx = 1;
        JButton testBtn = new JButton(lang.getString("settings.test_connection"));
        syncPanel.add(testBtn, c);

        c.gridy = 8; c.weighty = 1;
        syncPanel.add(Box.createVerticalGlue(), c);

        tabs.addTab(lang.getString("settings.sync"), syncPanel);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);

        // Bottom buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton(lang.getString("common.cancel"));
        JButton saveBtn = new JButton(lang.getString("common.save"));
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
            SFTPRepository repo = new SFTPRepository(
                hostField.getText(), (Integer) portSpinner.getValue(),
                userField.getText(), keyPathField.getText(), remotePathField.getText());
            boolean ok = repo.testConnection();
            JOptionPane.showMessageDialog(SettingsDialog.this,
                ok ? lang.getString("settings.connection_ok") : lang.getString("settings.connection_fail"),
                lang.getString("settings.test_connection"),
                ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        });

        cancelBtn.addActionListener(e -> dispose());
        saveBtn.addActionListener(e -> doSave());

        pack();
        setMinimumSize(new Dimension(500, 450));
        setLocationRelativeTo(getOwner());
    }

    private void doSave() {
        config.setLanguage(langCombo.getSelectedIndex() == 0 ? "fr" : "en");
        config.setTheme(themeCombo.getSelectedIndex() == 0 ? ThemeMode.LIGHT : ThemeMode.DARK);
        config.setAutoLockMinutes((Integer) autoLockSpinner.getValue());
        config.setClipboardClearSeconds((Integer) clipboardSpinner.getValue());
        config.setStorageMode(remoteRadio.isSelected() ? StorageMode.REMOTE : StorageMode.LOCAL);
        config.setSftpHost(hostField.getText());
        config.setSftpPort((Integer) portSpinner.getValue());
        config.setSftpUser(userField.getText());
        config.setSftpKeyPath(keyPathField.getText());
        config.setSftpRemotePath(remotePathField.getText());

        configManager.saveConfig(config);
        saved = true;
        dispose();
    }

    public boolean isSaved() { return saved; }
}
