package com.passwordmanager.ui;

import com.passwordmanager.config.AppConfig;
import com.passwordmanager.config.ConfigManager;
import com.passwordmanager.config.StorageMode;
import com.passwordmanager.config.ThemeMode;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.sync.SFTPRepository;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.theme.DesignTokens;
import com.passwordmanager.vault.PasswordEntry;
import com.passwordmanager.vault.SshKeyEntry;
import com.passwordmanager.vault.VaultService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings as a full in-shell panel (not a dialog): a header with Back, tabbed sections
 * (General / Security / Sync), and an Apply button that applies while staying on this view.
 * Replaces the old SettingsDialog. Ported logic; validation + persistence unchanged.
 */
public class CoffreSettingsPanel extends JPanel {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final AppConfig config;
    private final ConfigManager configManager;
    private final List<SshKeyEntry> vaultKeys;
    private final VaultService vaultService;
    private final Runnable onApply;
    private final Runnable onBack;
    private final Runnable onWorkspaceChange;
    private final Runnable onCategoriesChanged;

    private JComboBox<String> langCombo, themeCombo;
    private JSpinner autoLockSpinner, clipboardSpinner;
    private JCheckBox faviconsCheck;
    private JRadioButton localRadio, remoteRadio;
    private JTextField hostField, userField, keyPathField, remotePathField;
    private JSpinner portSpinner;
    private JRadioButton keySourceFile, keySourceVault;
    private JComboBox<String> vaultKeyCombo;
    private JTextField newCategoryField;
    private JPanel categoryListPanel;

    public CoffreSettingsPanel(AppConfig config, ConfigManager configManager, List<SshKeyEntry> vaultKeys,
                               VaultService vaultService, Runnable onApply, Runnable onBack,
                               Runnable onWorkspaceChange, Runnable onCategoriesChanged) {
        this.config = config;
        this.configManager = configManager;
        this.vaultKeys = vaultKeys;
        this.vaultService = vaultService;
        this.onApply = onApply;
        this.onBack = onBack;
        this.onWorkspaceChange = onWorkspaceChange;
        this.onCategoriesChanged = onCategoriesChanged;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // Header: Back + title
        JPanel header = new JPanel(new BorderLayout(DesignTokens.SPACE_MD, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, DesignTokens.SPACE_LG, DesignTokens.SPACE_MD, DesignTokens.SPACE_LG)));
        JLabel title = new JLabel(lang.getString("settings.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(lang.getString("settings.general"), buildGeneral());
        tabs.addTab(lang.getString("settings.categories"), buildCategories());
        tabs.addTab(lang.getString("settings.security"), buildSecurity());
        tabs.addTab(lang.getString("settings.sync"), buildSync());
        add(tabs, BorderLayout.CENTER);

        // Footer: Back (tonal) + Apply (primary), side by side.
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, DesignTokens.SPACE_MD));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DesignTokens.outline()));
        JButton backBtn = Buttons.tonal(lang.getString("common.back"));
        backBtn.addActionListener(e -> { if (onBack != null) onBack.run(); });
        JButton applyBtn = Buttons.primary(lang.getString("common.apply"));
        applyBtn.addActionListener(e -> apply());
        footer.add(backBtn);
        footer.add(applyBtn);
        add(footer, BorderLayout.SOUTH);
    }

    private JComponent wrapScroll(JPanel inner) {
        JScrollPane sc = new JScrollPane(inner);
        sc.setBorder(null);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        return sc;
    }

    private JPanel buildGeneral() {
        JPanel p = grid();
        GridBagConstraints g = gbc();
        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        p.add(new JLabel(lang.getString("settings.language")), g);
        g.gridx = 1; g.weightx = 1;
        langCombo = new JComboBox<>(new String[]{"Français", "English"});
        langCombo.setSelectedIndex("en".equals(config.getLanguage()) ? 1 : 0);
        p.add(langCombo, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        p.add(new JLabel(lang.getString("settings.theme")), g);
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
        p.add(themeCombo, g);

        g.gridx = 0; g.gridy = 2; g.weightx = 0;
        p.add(new JLabel(lang.getString("settings.workspace")), g);
        g.gridx = 1; g.weightx = 1;
        JTextField workspaceField = new JTextField(config.getLocalVaultDirectory());
        workspaceField.setEditable(false);
        workspaceField.setToolTipText(config.getLocalVaultDirectory());
        p.add(workspaceField, g);

        g.gridx = 1; g.gridy = 3;
        JButton changeWorkspaceBtn = new JButton(lang.getString("settings.change_workspace"));
        changeWorkspaceBtn.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, lang.getString("settings.workspace_relogin"),
                lang.getString("settings.change_workspace"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (c == JOptionPane.YES_OPTION && onWorkspaceChange != null) onWorkspaceChange.run();
        });
        p.add(changeWorkspaceBtn, g);

        g.gridx = 0; g.gridy = 4; g.gridwidth = 2;
        faviconsCheck = new JCheckBox(lang.getString("settings.favicons"));
        faviconsCheck.setSelected(config.isFaviconsEnabled());
        p.add(faviconsCheck, g);
        g.gridwidth = 1;

        g.gridx = 0; g.gridy = 5; g.weighty = 1;
        p.add(Box.createVerticalGlue(), g);
        return p;
    }

    private JComponent buildCategories() {
        JPanel p = grid();
        GridBagConstraints g = gbc();

        // Add row: text field + add button
        g.gridx = 0; g.gridy = 0; g.weightx = 1;
        newCategoryField = new JTextField();
        newCategoryField.putClientProperty("JTextField.placeholderText", lang.getString("category.new"));
        newCategoryField.addActionListener(e -> addCategory());
        p.add(newCategoryField, g);
        g.gridx = 1; g.weightx = 0;
        JButton add = Buttons.primary(lang.getString("category.add"));
        add.addActionListener(e -> addCategory());
        p.add(add, g);

        // List of existing categories with per-row delete
        g.gridx = 0; g.gridy = 1; g.gridwidth = 2; g.weightx = 1; g.weighty = 1;
        g.fill = GridBagConstraints.BOTH;
        categoryListPanel = new JPanel();
        categoryListPanel.setLayout(new BoxLayout(categoryListPanel, BoxLayout.Y_AXIS));
        categoryListPanel.setOpaque(false);
        JScrollPane sc = new JScrollPane(categoryListPanel);
        sc.setBorder(null);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        p.add(sc, g);

        rebuildCategoryList();
        return p;
    }

    private void rebuildCategoryList() {
        categoryListPanel.removeAll();
        List<String> cats = new ArrayList<>(vaultService.getVault().getCategories());
        if (cats.isEmpty()) {
            JLabel empty = new JLabel(lang.getString("category.all"));
            empty.setForeground(DesignTokens.onSurfaceFaint());
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, DesignTokens.SPACE_XS, 0, 0));
            categoryListPanel.add(empty);
        }
        for (String c : cats) {
            JPanel row = new JPanel(new BorderLayout(DesignTokens.SPACE_SM, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DesignTokens.outline()),
                BorderFactory.createEmptyBorder(DesignTokens.SPACE_SM, DesignTokens.SPACE_XS, DesignTokens.SPACE_SM, DesignTokens.SPACE_XS)));
            row.add(new JLabel(c), BorderLayout.WEST);
            JButton del = new JButton(lang.getString("category.delete"));
            del.setForeground(DesignTokens.statusWeak());
            del.addActionListener(e -> deleteCategory(c));
            row.add(del, BorderLayout.EAST);
            categoryListPanel.add(row);
        }
        categoryListPanel.revalidate();
        categoryListPanel.repaint();
    }

    private void addCategory() {
        String name = newCategoryField.getText() == null ? "" : newCategoryField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, lang.getString("category.name_required"),
                lang.getString("category.add"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (vaultService.getVault().getCategories().contains(name)) {
            JOptionPane.showMessageDialog(this, lang.getString("category.exists"),
                lang.getString("category.add"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        vaultService.addCategory(name);
        newCategoryField.setText("");
        rebuildCategoryList();
        if (onCategoriesChanged != null) onCategoriesChanged.run();
    }

    private void deleteCategory(String category) {
        int c = JOptionPane.showConfirmDialog(this, lang.getString("category.delete_confirm"),
            lang.getString("category.delete"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c != JOptionPane.YES_OPTION) return;
        // Reassign entries in this category to uncategorized (mirrors the Android behaviour).
        for (PasswordEntry e : vaultService.search("")) {
            if (category.equals(e.getCategory())) e.setCategory("");
        }
        vaultService.removeCategory(category);
        rebuildCategoryList();
        if (onCategoriesChanged != null) onCategoriesChanged.run();
    }

    private JPanel buildSecurity() {
        JPanel p = grid();
        GridBagConstraints s = gbc();
        s.gridx = 0; s.gridy = 0; s.weightx = 0;
        p.add(new JLabel(lang.getString("settings.auto_lock")), s);
        s.gridx = 1; s.weightx = 1;
        autoLockSpinner = new JSpinner(new SpinnerNumberModel(config.getAutoLockMinutes(), 1, 60, 1));
        p.add(autoLockSpinner, s);

        s.gridx = 0; s.gridy = 1; s.weightx = 0;
        p.add(new JLabel(lang.getString("settings.clipboard_clear")), s);
        s.gridx = 1; s.weightx = 1;
        clipboardSpinner = new JSpinner(new SpinnerNumberModel(config.getClipboardClearSeconds(), 5, 120, 5));
        p.add(clipboardSpinner, s);

        s.gridx = 0; s.gridy = 2; s.weighty = 1;
        p.add(Box.createVerticalGlue(), s);
        return p;
    }

    private JPanel buildSync() {
        JPanel p = grid();
        GridBagConstraints c = gbc();
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        localRadio = new JRadioButton(lang.getString("settings.local_only"));
        remoteRadio = new JRadioButton(lang.getString("settings.remote_server"));
        ButtonGroup bg = new ButtonGroup();
        bg.add(localRadio); bg.add(remoteRadio);
        if (config.getStorageMode() == StorageMode.REMOTE) remoteRadio.setSelected(true);
        else localRadio.setSelected(true);
        p.add(localRadio, c);
        c.gridy = 1; p.add(remoteRadio, c);
        c.gridwidth = 1;

        c.gridy = 2; c.gridx = 0; c.weightx = 0;
        p.add(new JLabel(lang.getString("settings.host")), c);
        c.gridx = 1; c.weightx = 1;
        hostField = new JTextField(config.getSftpHost(), 20);
        p.add(hostField, c);

        c.gridy = 3; c.gridx = 0; c.weightx = 0;
        p.add(new JLabel(lang.getString("settings.port")), c);
        c.gridx = 1; c.weightx = 1;
        portSpinner = new JSpinner(new SpinnerNumberModel(config.getSftpPort(), 1, 65535, 1));
        p.add(portSpinner, c);

        c.gridy = 4; c.gridx = 0; c.weightx = 0;
        p.add(new JLabel(lang.getString("settings.user")), c);
        c.gridx = 1; c.weightx = 1;
        userField = new JTextField(config.getSftpUser(), 20);
        p.add(userField, c);

        c.gridy = 5; c.gridx = 0; c.weightx = 0;
        p.add(new JLabel(lang.getString("settings.ssh_key_source")), c);
        c.gridx = 1; c.weightx = 1;
        JPanel keySourcePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        keySourcePanel.setOpaque(false);
        keySourceFile = new JRadioButton(lang.getString("settings.ssh_key_source.file"));
        keySourceVault = new JRadioButton(lang.getString("settings.ssh_key_source.vault"));
        ButtonGroup keySourceGroup = new ButtonGroup();
        keySourceGroup.add(keySourceFile); keySourceGroup.add(keySourceVault);
        if (config.isUsingVaultKey()) keySourceVault.setSelected(true); else keySourceFile.setSelected(true);
        keySourcePanel.add(keySourceFile); keySourcePanel.add(keySourceVault);
        p.add(keySourcePanel, c);

        c.gridy = 6; c.gridx = 0; c.weightx = 0;
        p.add(new JLabel(lang.getString("settings.ssh_key")), c);
        c.gridx = 1; c.weightx = 1;
        JPanel keyPanel = new JPanel(new CardLayout());
        keyPanel.setOpaque(false);
        JPanel fileKeyPanel = new JPanel(new BorderLayout(5, 0));
        fileKeyPanel.setOpaque(false);
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
        p.add(keyPanel, c);
        keySourceFile.addActionListener(e -> keyCardLayout.show(keyPanel, "file"));
        keySourceVault.addActionListener(e -> keyCardLayout.show(keyPanel, "vault"));
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                keyPathField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        c.gridy = 7; c.gridx = 0; c.weightx = 0;
        p.add(new JLabel(lang.getString("settings.remote_path")), c);
        c.gridx = 1; c.weightx = 1;
        remotePathField = new JTextField(config.getSftpRemotePath(), 20);
        p.add(remotePathField, c);

        c.gridy = 8; c.gridx = 1;
        JButton testBtn = new JButton(lang.getString("settings.test_connection"));
        testBtn.addActionListener(e -> testConnection(testBtn));
        p.add(testBtn, c);

        c.gridy = 9; c.weighty = 1;
        p.add(Box.createVerticalGlue(), c);
        return p;
    }

    private void testConnection(JButton testBtn) {
        testBtn.setEnabled(false);
        testBtn.setText(lang.getString("settings.test_connection") + "...");
        SFTPRepository repo = new SFTPRepository(hostField.getText(), (Integer) portSpinner.getValue(),
            userField.getText(), keyPathField.getText(), remotePathField.getText());
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() { return repo.testConnection(); }
            @Override protected void done() {
                testBtn.setEnabled(true);
                testBtn.setText(lang.getString("settings.test_connection"));
                try {
                    boolean ok = get();
                    JOptionPane.showMessageDialog(CoffreSettingsPanel.this,
                        ok ? lang.getString("settings.connection_ok") : lang.getString("settings.connection_fail"),
                        lang.getString("settings.test_connection"),
                        ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CoffreSettingsPanel.this, lang.getString("settings.connection_fail"),
                        lang.getString("settings.test_connection"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void apply() {
        if (remoteRadio.isSelected()) {
            if (hostField.getText().trim().isEmpty()) { showValidationError(lang.getString("settings.host")); return; }
            if (userField.getText().trim().isEmpty()) { showValidationError(lang.getString("settings.user")); return; }
            if (keySourceFile.isSelected()) {
                String keyPath = keyPathField.getText().trim();
                if (keyPath.isEmpty()) { showValidationError(lang.getString("settings.ssh_key")); return; }
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
            } else if (vaultKeys.isEmpty() || vaultKeyCombo.getSelectedIndex() < 0) {
                showValidationError(lang.getString("settings.ssh_key")); return;
            }
            if (remotePathField.getText().trim().isEmpty()) { showValidationError(lang.getString("settings.remote_path")); return; }
        }

        config.setLanguage(langCombo.getSelectedIndex() == 0 ? "fr" : "en");
        ThemeMode[] themes = { ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK };
        config.setTheme(themes[themeCombo.getSelectedIndex()]);
        config.setAutoLockMinutes((Integer) autoLockSpinner.getValue());
        config.setFaviconsEnabled(faviconsCheck.isSelected());
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
        if (onApply != null) onApply.run();
    }

    private void showValidationError(String fieldLabel) {
        JOptionPane.showMessageDialog(this,
            fieldLabel + " " + lang.getString("error.field_required"),
            lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
    }

    private static JPanel grid() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_LG, DesignTokens.SPACE_LG, DesignTokens.SPACE_LG, DesignTokens.SPACE_LG));
        return p;
    }

    private static GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;
        return g;
    }
}
