package com.passwordmanager.ui;

import com.passwordmanager.crypto.VaultSession;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.util.FileSecurityUtils;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.Vault;
import com.passwordmanager.vault.VaultManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Handles import/export operations for the vault.
 * Extracted from MainFrame to reduce class responsibilities.
 */
public class ImportExportController {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final Component parentComponent;
    private final Vault vault;
    private final String username;
    private final VaultSession session;
    private final VaultManager vaultManager;
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
        this.saveVaultCallback = saveVaultCallback;
        this.refreshCallback = refreshCallback;
    }

    public void doImport(String format) {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(parentComponent) != JFileChooser.APPROVE_OPTION) return;
        try {
            File importFile = fc.getSelectedFile();
            if (importFile.length() > 10 * 1024 * 1024) {
                showError(lang.getString("import.error") + " (max 10 MB)");
                return;
            }
            String content = new String(Files.readAllBytes(importFile.toPath()), StandardCharsets.UTF_8);
            int count;
            if ("csv".equals(format)) {
                count = vaultManager.importFromCsv(vault, content);
            } else {
                count = vaultManager.importFromJson(vault, content);
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
    }

    public void doExport(String format) {
        int confirm = JOptionPane.showConfirmDialog(parentComponent,
            lang.getString("export.warning"),
            lang.getString("export.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("vault_export." + format));
        if (fc.showSaveDialog(parentComponent) != JFileChooser.APPROVE_OPTION) return;
        try {
            char[] content;
            if ("csv".equals(format)) {
                content = vaultManager.exportAsCsv(vault);
            } else {
                content = vaultManager.exportAsJson(vault);
            }
            java.nio.file.Path exportPath = fc.getSelectedFile().toPath();
            byte[] exportBytes = new String(content).getBytes(StandardCharsets.UTF_8);
            SecureWiper.wipe(content);
            try {
                Files.write(exportPath, exportBytes);
            } finally {
                SecureWiper.wipe(exportBytes);
            }
            FileSecurityUtils.setOwnerOnlyPermissions(exportPath);
            JOptionPane.showMessageDialog(parentComponent,
                lang.getString("export.success"),
                lang.getString("export.title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parentComponent,
                lang.getString("export.error") + ": " + ex.getMessage(),
                lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public void doExportBackup() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("vault_" + username + "_backup.enc"));
        if (fc.showSaveDialog(parentComponent) != JFileChooser.APPROVE_OPTION) return;
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
