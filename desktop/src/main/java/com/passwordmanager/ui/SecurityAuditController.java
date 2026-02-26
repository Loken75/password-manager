package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.Vault;
import com.passwordmanager.vault.VaultEntry;
import com.passwordmanager.vault.VaultService;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Handles security audit operations: full audit, weak password filtering,
 * and duplicate password filtering.
 * Extracted from MainFrame to reduce class responsibilities.
 */
public class SecurityAuditController {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final Component parentComponent;
    private final Vault vault;
    private final VaultService vaultService;

    public SecurityAuditController(Component parentComponent, Vault vault, VaultService vaultService) {
        this.parentComponent = parentComponent;
        this.vault = vault;
        this.vaultService = vaultService;
    }

    public void doSecurityAudit() {
        StringBuilder sb = new StringBuilder();
        int issues = 0;

        // Weak passwords
        sb.append("=== ").append(lang.getString("audit.weak_passwords")).append(" ===\n");
        for (VaultEntry e : vault.getEntries()) {
            char[] auditPwd = e.getPassword();
            PasswordStrengthAnalyzer.Strength strength = PasswordStrengthAnalyzer.analyze(auditPwd);
            SecureWiper.wipe(auditPwd);
            if (strength == PasswordStrengthAnalyzer.Strength.WEAK) {
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

        // Breached passwords (HIBP)
        sb.append("\n=== ").append(lang.getString("audit.breached_passwords")).append(" ===\n");
        for (VaultEntry e : vault.getEntries()) {
            char[] hibpPwd = e.getPassword();
            if (hibpPwd != null && hibpPwd.length > 0) {
                try {
                    int breachCount = com.passwordmanager.security.HibpChecker.checkPassword(hibpPwd);
                    if (breachCount > 0) {
                        sb.append("  - ").append(e.getTitle()).append(" (").append(breachCount).append("x)\n");
                        issues++;
                    }
                } finally {
                    SecureWiper.wipe(hibpPwd);
                }
            }
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
        JOptionPane.showMessageDialog(parentComponent, scroll, lang.getString("audit.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    public void doFilterWeak() {
        StringBuilder sb = new StringBuilder();
        for (VaultEntry e : vault.getEntries()) {
            char[] pwd = e.getPassword();
            PasswordStrengthAnalyzer.Strength s = PasswordStrengthAnalyzer.analyze(pwd);
            SecureWiper.wipe(pwd);
            if (s == PasswordStrengthAnalyzer.Strength.WEAK) {
                sb.append("- ").append(e.getTitle()).append("\n");
            }
        }
        showAuditResult(lang.getString("menu.view.filter_weak"), sb);
    }

    public void doFilterDuplicate() {
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

    public void showAuditResult(String title, StringBuilder sb) {
        if (sb.length() == 0) sb.append(lang.getString("audit.no_issues"));
        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(400, 300));
        JOptionPane.showMessageDialog(parentComponent, scroll, title, JOptionPane.INFORMATION_MESSAGE);
    }
}
