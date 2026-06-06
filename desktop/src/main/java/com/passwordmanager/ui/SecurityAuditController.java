package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.security.HibpChecker;
import com.passwordmanager.ui.theme.DesignTokens;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.Vault;
import com.passwordmanager.vault.PasswordEntry;
import com.passwordmanager.vault.VaultService;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles security audit operations: full audit, weak password filtering,
 * and duplicate password filtering.
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
        // Collect audit data (non-HIBP, fast)
        List<PasswordEntry> weakEntries = new ArrayList<>();
        for (PasswordEntry e : vault.getEntries()) {
            char[] auditPwd = e.getPassword();
            PasswordStrengthAnalyzer.Strength strength = PasswordStrengthAnalyzer.analyze(auditPwd);
            SecureWiper.wipe(auditPwd);
            if (strength == PasswordStrengthAnalyzer.Strength.WEAK) {
                weakEntries.add(e);
            }
        }

        Map<String, List<PasswordEntry>> dups = vaultService.findDuplicatePasswords();
        List<PasswordEntry> dupEntries = new ArrayList<>();
        for (List<PasswordEntry> group : dups.values()) {
            dupEntries.addAll(group);
        }

        int expiryDays = 180;
        Object expiry = vault.getSettings().get("password_expiry_days");
        if (expiry instanceof Number) expiryDays = ((Number) expiry).intValue();
        List<PasswordEntry> oldEntries = vaultService.findOldPasswords(expiryDays);

        int totalIssues = weakEntries.size() + dupEntries.size() + oldEntries.size();

        // Build the visual dialog
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Summary banner
        JPanel summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel summaryLabel = new JLabel();
        summaryLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        summaryPanel.setBackground(DesignTokens.surfaceSubtle());
        // Normal high-contrast text (readable in both themes) + a colored status dot.
        summaryLabel.setForeground(UIManager.getColor("Label.foreground"));
        if (totalIssues == 0) {
            summaryLabel.setText("<html><font color='" + hex(DesignTokens.statusStrong()) + "'>●</font>&nbsp; "
                + lang.getString("audit.no_issues") + "</html>");
        } else {
            summaryLabel.setText("<html><font color='" + hex(DesignTokens.statusWeak()) + "'>●</font>&nbsp; "
                + lang.getString("audit.issues_found").replace("{0}", String.valueOf(totalIssues)) + "</html>");
        }
        summaryPanel.add(summaryLabel, BorderLayout.CENTER);
        summaryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        mainPanel.add(summaryPanel);
        mainPanel.add(Box.createVerticalStrut(8));

        // Weak passwords section
        mainPanel.add(createAuditSection(
            lang.getString("audit.weak_passwords") + " (" + weakEntries.size() + ")",
            weakEntries, DesignTokens.statusWeak()));
        mainPanel.add(Box.createVerticalStrut(6));

        // Duplicate passwords section
        mainPanel.add(createAuditSection(
            lang.getString("audit.duplicate_passwords") + " (" + dupEntries.size() + ")",
            dupEntries, DesignTokens.statusMedium()));
        mainPanel.add(Box.createVerticalStrut(6));

        // Old passwords section
        mainPanel.add(createAuditSection(
            lang.getString("audit.old_passwords").replace("{0}", String.valueOf(expiryDays))
                + " (" + oldEntries.size() + ")",
            oldEntries, DesignTokens.statusMedium()));
        mainPanel.add(Box.createVerticalStrut(6));

        // HIBP breach section with async check button
        JPanel hibpSection = new JPanel(new BorderLayout(8, 4));
        hibpSection.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(DesignTokens.statusWeak()),
            lang.getString("audit.breached_passwords"),
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 12), DesignTokens.statusWeak()));

        JPanel hibpTopRow = new JPanel(new BorderLayout(8, 0));
        JLabel hibpStatus = new JLabel(lang.getString("audit.breach_checking").replace("...", ""));
        hibpStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JButton checkBtn = new JButton(lang.getString("audit.breach_checking").contains("V") ? "Vérifier maintenant" : "Check now");
        JProgressBar progressBar = new JProgressBar(0, vault.getEntries().size());
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        hibpTopRow.add(hibpStatus, BorderLayout.CENTER);
        hibpTopRow.add(checkBtn, BorderLayout.EAST);
        hibpSection.add(hibpTopRow, BorderLayout.NORTH);
        hibpSection.add(progressBar, BorderLayout.CENTER);

        DefaultListModel<String> hibpListModel = new DefaultListModel<>();
        JList<String> hibpList = new JList<>(hibpListModel);
        hibpList.setVisibleRowCount(5);
        JScrollPane hibpScroll = new JScrollPane(hibpList);
        hibpScroll.setPreferredSize(new Dimension(0, 100));
        hibpScroll.setVisible(false);
        hibpSection.add(hibpScroll, BorderLayout.SOUTH);

        checkBtn.addActionListener(e -> {
            checkBtn.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setValue(0);
            hibpStatus.setText(lang.getString("audit.breach_checking"));
            hibpListModel.clear();

            new SwingWorker<List<String>, Integer>() {
                @Override
                protected List<String> doInBackground() {
                    List<String> breached = new ArrayList<>();
                    List<PasswordEntry> entries = vault.getEntries();
                    for (int i = 0; i < entries.size(); i++) {
                        PasswordEntry entry = entries.get(i);
                        char[] pwd = entry.getPassword();
                        if (pwd != null && pwd.length > 0) {
                            try {
                                int count = HibpChecker.checkPassword(pwd);
                                if (count > 0) {
                                    breached.add(entry.getTitle() + "  (" + count + "x)");
                                }
                            } finally {
                                SecureWiper.wipe(pwd);
                            }
                        }
                        publish(i + 1);
                    }
                    return breached;
                }

                @Override
                protected void process(java.util.List<Integer> chunks) {
                    if (!chunks.isEmpty()) {
                        progressBar.setValue(chunks.get(chunks.size() - 1));
                    }
                }

                @Override
                protected void done() {
                    try {
                        List<String> breached = get();
                        progressBar.setVisible(false);
                        if (breached.isEmpty()) {
                            hibpStatus.setText("\u2705 " + lang.getString("audit.no_issues"));
                            hibpStatus.setForeground(DesignTokens.statusStrong());
                        } else {
                            String msg = lang.getString("audit.breach_count")
                                .replace("{0}", String.valueOf(breached.size()));
                            hibpStatus.setText("\u26a0 " + msg);
                            hibpStatus.setForeground(DesignTokens.statusWeak());
                            for (String s : breached) {
                                hibpListModel.addElement("  \u2022 " + s);
                            }
                            hibpScroll.setVisible(true);
                            hibpSection.revalidate();
                        }
                    } catch (Exception ex) {
                        progressBar.setVisible(false);
                        hibpStatus.setText("\u274c " + lang.getString("audit.breach_error"));
                        hibpStatus.setForeground(DesignTokens.statusWeak());
                    }
                    checkBtn.setEnabled(true);
                }
            }.execute();
        });

        hibpSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        mainPanel.add(hibpSection);

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setPreferredSize(new Dimension(550, 500));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JOptionPane.showMessageDialog(parentComponent, scrollPane,
            lang.getString("audit.title"), JOptionPane.PLAIN_MESSAGE);
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private JPanel createAuditSection(String title, List<PasswordEntry> entries, Color accentColor) {
        JPanel section = new JPanel(new BorderLayout(4, 4));
        section.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(accentColor),
            title,
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 12), accentColor));

        if (entries.isEmpty()) {
            JLabel noIssue = new JLabel("\u2705 " + lang.getString("audit.no_issues"));
            noIssue.setForeground(DesignTokens.statusStrong());
            noIssue.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            section.add(noIssue, BorderLayout.CENTER);
        } else {
            DefaultListModel<String> model = new DefaultListModel<>();
            for (PasswordEntry e : entries) {
                String detail = e.getTitle();
                if (e.getUsername() != null && !e.getUsername().isEmpty()) {
                    detail += "  (" + e.getUsername() + ")";
                }
                model.addElement("  \u2022 " + detail);
            }
            JList<String> list = new JList<>(model);
            list.setVisibleRowCount(Math.min(entries.size(), 5));
            JScrollPane sp = new JScrollPane(list);
            sp.setPreferredSize(new Dimension(0, Math.min(entries.size() * 22, 110)));
            section.add(sp, BorderLayout.CENTER);
        }

        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        return section;
    }

    public void doFilterWeak() {
        StringBuilder sb = new StringBuilder();
        for (PasswordEntry e : vault.getEntries()) {
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
        Map<String, List<PasswordEntry>> dups = vaultService.findDuplicatePasswords();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<PasswordEntry>> entry : dups.entrySet()) {
            for (PasswordEntry e : entry.getValue()) {
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
