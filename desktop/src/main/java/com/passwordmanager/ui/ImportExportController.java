package com.passwordmanager.ui;

import com.passwordmanager.crypto.VaultSession;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.util.FileSecurityUtils;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.AppEntry;
import com.passwordmanager.vault.Vault;
import com.passwordmanager.vault.PasswordEntry;
import com.passwordmanager.vault.VaultManager;
import com.passwordmanager.vault.VaultService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.awt.Window;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Handles import/export operations for the vault with unified dialogs.
 * Supports CSV, JSON, and encrypted (.enc) formats.
 */
public class ImportExportController {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final Component parentComponent;
    private final Vault vault;
    private final String username;
    private final VaultSession session;
    private final VaultManager vaultManager;
    private final VaultService vaultService;
    private final Runnable saveVaultCallback;
    private final Runnable refreshCallback;

    public ImportExportController(Component parentComponent, Vault vault, String username,
                                  VaultSession session, VaultManager vaultManager,
                                  Runnable saveVaultCallback, Runnable refreshCallback) {
        this.parentComponent = parentComponent;
        this.vault = vault;
        this.username = username;
        this.session = session;
        this.vaultManager = vaultManager;
        this.vaultService = new VaultService(vault);
        this.saveVaultCallback = saveVaultCallback;
        this.refreshCallback = refreshCallback;
    }

    /**
     * Shows a unified import dialog with format selection (CSV / JSON / Encrypted).
     */
    public void doImport() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel(lang.getString("import.format")), gbc);

        JRadioButton csvRadio = new JRadioButton("CSV", true);
        JRadioButton jsonRadio = new JRadioButton("JSON");
        JRadioButton encRadio = new JRadioButton(lang.getString("import.encrypted"));
        ButtonGroup bg = new ButtonGroup();
        bg.add(csvRadio);
        bg.add(jsonRadio);
        bg.add(encRadio);

        gbc.gridy = 1; gbc.gridwidth = 2;
        panel.add(csvRadio, gbc);
        gbc.gridy = 2;
        panel.add(jsonRadio, gbc);
        gbc.gridy = 3;
        panel.add(encRadio, gbc);

        // Password field (only for .enc)
        gbc.gridy = 4; gbc.gridwidth = 1;
        JLabel passLabel = new JLabel(lang.getString("import.password"));
        panel.add(passLabel, gbc);
        gbc.gridx = 1;
        JPasswordField passField = new JPasswordField(20);
        panel.add(passField, gbc);
        passLabel.setVisible(false);
        passField.setVisible(false);

        Runnable resizeDialog = () -> {
            panel.revalidate();
            panel.repaint();
            Window w = SwingUtilities.getWindowAncestor(panel);
            if (w != null) w.pack();
        };
        encRadio.addActionListener(e -> { passLabel.setVisible(true); passField.setVisible(true); resizeDialog.run(); });
        csvRadio.addActionListener(e -> { passLabel.setVisible(false); passField.setVisible(false); resizeDialog.run(); });
        jsonRadio.addActionListener(e -> { passLabel.setVisible(false); passField.setVisible(false); resizeDialog.run(); });

        int result = JOptionPane.showConfirmDialog(parentComponent, panel,
            lang.getString("import.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        if (encRadio.isSelected()) {
            doImportEncrypted(passField.getPassword());
        } else {
            String format = csvRadio.isSelected() ? "csv" : "json";
            doImportFile(format);
        }
    }

    private void doImportFile(String format) {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(parentComponent) != JFileChooser.APPROVE_OPTION) return;
        File importFile = fc.getSelectedFile();
        if (importFile.length() > 10 * 1024 * 1024) {
            showError(lang.getString("import.error") + " (max 10 MB)");
            return;
        }
        // Only the file read runs off the EDT. Parsing + vault mutation + save stay on the
        // EDT, serialized with the sync-merge path (which also touches the vault on the EDT).
        BackgroundTask.run(parentComponent,
            () -> new String(Files.readAllBytes(importFile.toPath()), StandardCharsets.UTF_8),
            content -> {
                try {
                    int count = "csv".equals(format)
                        ? vaultManager.importFromCsv(vault, content)
                        : vaultManager.importFromJson(vault, content);
                    saveVaultCallback.run();
                    refreshCallback.run();
                    JOptionPane.showMessageDialog(parentComponent,
                        lang.getString("import.success").replace("{0}", String.valueOf(count)),
                        lang.getString("import.title"), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parentComponent,
                        lang.getString("import.error") + ": " + ex.getMessage(),
                        lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            },
            ex -> JOptionPane.showMessageDialog(parentComponent,
                lang.getString("import.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE));
    }

    private void doImportEncrypted(char[] password) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Encrypted vault (*.enc)", "enc"));
        if (fc.showOpenDialog(parentComponent) != JFileChooser.APPROVE_OPTION) {
            Arrays.fill(password, '\0');
            return;
        }
        String path = fc.getSelectedFile().getAbsolutePath();
        // PBKDF2 decrypt of the foreign vault runs off the EDT (it builds a SEPARATE
        // sourceVault, not the live vault); the password is wiped in the task. Merging into
        // the live vault + save run on the EDT, serialized with the sync-merge path.
        BackgroundTask.run(parentComponent,
            () -> {
                try {
                    return vaultManager.importEncryptedVault(password, path);
                } finally {
                    Arrays.fill(password, '\0');
                }
            },
            sourceVault -> {
                try {
                    int count = 0;
                    for (PasswordEntry entry : sourceVault.getEntries()) {
                        vault.addEntry(entry);
                        count++;
                    }
                    for (AppEntry entry : sourceVault.getAppEntries()) {
                        vault.addAppEntry(entry);
                        count++;
                    }
                    for (com.passwordmanager.vault.SshKeyEntry entry : sourceVault.getSshKeyEntries()) {
                        vault.addSshKeyEntry(entry);
                        count++;
                    }
                    saveVaultCallback.run();
                    refreshCallback.run();
                    JOptionPane.showMessageDialog(parentComponent,
                        lang.getString("import.success").replace("{0}", String.valueOf(count)),
                        lang.getString("import.title"), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parentComponent,
                        lang.getString("import.error") + ": " + ex.getMessage(),
                        lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            },
            ex -> JOptionPane.showMessageDialog(parentComponent,
                lang.getString("import.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE));
    }

    /**
     * Shows a unified export dialog with format selection (CSV / JSON / Encrypted).
     */
    public void doExport() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel(lang.getString("export.format")), gbc);

        JRadioButton csvRadio = new JRadioButton("CSV", true);
        JRadioButton jsonRadio = new JRadioButton("JSON");
        JRadioButton encRadio = new JRadioButton(lang.getString("export.encrypted"));
        ButtonGroup bg = new ButtonGroup();
        bg.add(csvRadio);
        bg.add(jsonRadio);
        bg.add(encRadio);

        gbc.gridy = 1; gbc.gridwidth = 2;
        panel.add(csvRadio, gbc);
        gbc.gridy = 2;
        panel.add(jsonRadio, gbc);
        gbc.gridy = 3;
        panel.add(encRadio, gbc);

        // Warning label for unencrypted exports
        gbc.gridy = 4; gbc.gridwidth = 2;
        JLabel warningLabel = new JLabel("<html><i>" + lang.getString("export.unencrypted_warning") + "</i></html>");
        warningLabel.setForeground(Color.ORANGE.darker());
        panel.add(warningLabel, gbc);

        encRadio.addActionListener(e -> warningLabel.setVisible(false));
        csvRadio.addActionListener(e -> warningLabel.setVisible(true));
        jsonRadio.addActionListener(e -> warningLabel.setVisible(true));

        int result = JOptionPane.showConfirmDialog(parentComponent, panel,
            lang.getString("export.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        if (encRadio.isSelected()) {
            doExportBackup();
        } else {
            String format = csvRadio.isSelected() ? "csv" : "json";
            doExportFile(format);
        }
    }

    private void doExportFile(String format) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("vault_export." + format));
        if (fc.showSaveDialog(parentComponent) != JFileChooser.APPROVE_OPTION) return;
        final java.nio.file.Path exportPath = fc.getSelectedFile().toPath();
        // Serialize the live vault on the EDT (serialized with the sync-merge path), then
        // offload only the file write.
        final byte[] exportBytes;
        try {
            char[] content = "csv".equals(format) ? vaultManager.exportAsCsv(vault)
                                                   : vaultManager.exportAsJson(vault);
            exportBytes = new String(content).getBytes(StandardCharsets.UTF_8);
            SecureWiper.wipe(content);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parentComponent,
                lang.getString("export.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        BackgroundTask.run(parentComponent,
            () -> {
                try {
                    Files.write(exportPath, exportBytes);
                } finally {
                    SecureWiper.wipe(exportBytes);
                }
                FileSecurityUtils.setOwnerOnlyPermissions(exportPath);
                return null;
            },
            ignored -> JOptionPane.showMessageDialog(parentComponent,
                lang.getString("export.success"),
                lang.getString("export.title"), JOptionPane.INFORMATION_MESSAGE),
            ex -> JOptionPane.showMessageDialog(parentComponent,
                lang.getString("export.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE));
    }

    private void doExportBackup() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("vault_" + username + "_backup.enc"));
        if (fc.showSaveDialog(parentComponent) != JFileChooser.APPROVE_OPTION) return;
        // exportBackup serializes the live vault internally, so it runs on the EDT
        // (serialized with the sync-merge path). It is DEK-encrypt + write (no PBKDF2),
        // so the pause is short.
        try {
            vaultManager.exportBackup(username, session, fc.getSelectedFile().getAbsolutePath());
            JOptionPane.showMessageDialog(parentComponent,
                lang.getString("export.success"),
                lang.getString("export.title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parentComponent,
                lang.getString("export.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(parentComponent, msg, lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
