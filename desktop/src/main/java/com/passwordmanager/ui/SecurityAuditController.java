package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.security.HibpChecker;
import com.passwordmanager.ui.components.BentoCard;
import com.passwordmanager.ui.components.RoundedPanel;
import com.passwordmanager.ui.theme.DesignTokens;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.Vault;
import com.passwordmanager.vault.PasswordEntry;
import com.passwordmanager.vault.VaultService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the security-audit view and the legacy weak/duplicate quick filters.
 *
 * <p>The audit mirrors the Android redesign: an overview row (score /20, issues to fix, strong
 * count), collapsible "at risk" sections (weak, duplicate, old, HIBP breaches), and read-only
 * cards for strengths, composition, completeness and activity. All metrics are derived from
 * {@code :core} services — no business logic lives here. Each password clone is wiped right after
 * its strength is analysed.
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

    /**
     * Builds the security-audit view as an embeddable, scrollable component (the audit is now a
     * first-class page in the shell, not a dialog). Rebuilt by the caller on each visit so its
     * metrics reflect the current vault.
     */
    public JComponent buildAuditView() {
        // ── Metrics (single strength pass; clones wiped immediately) ──
        List<PasswordEntry> all = vault.getEntries();
        List<PasswordEntry> weak = new ArrayList<>();
        List<PasswordEntry> strong = new ArrayList<>();
        int points = 0;
        for (PasswordEntry e : all) {
            char[] pw = e.getPassword();
            if (pw == null) continue;
            try {
                switch (PasswordStrengthAnalyzer.analyze(pw)) {
                    case WEAK:        weak.add(e);   points += 25;  break;
                    case MEDIUM:      weak.add(e);   points += 55;  break;
                    case STRONG:      strong.add(e); points += 85;  break;
                    case VERY_STRONG: strong.add(e); points += 100; break;
                }
            } finally {
                SecureWiper.wipe(pw);
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

        int total = all.size();
        int score = total == 0 ? 100 : Math.round(points / (float) total);
        int score20 = (int) Math.round(score / 5.0);
        int uniquePercent = total == 0 ? 100 : Math.round((total - dupEntries.size()) * 100f / total);
        int totalIssues = weak.size() + dupEntries.size() + oldEntries.size();

        int categoriesCount = (int) all.stream()
            .map(PasswordEntry::getCategory).filter(c -> c != null && !c.isBlank()).distinct().count();
        int favoritesCount = (int) all.stream().filter(PasswordEntry::isFavorite).count();
        int noUrlCount = (int) all.stream().filter(e -> isBlank(e.getUrl())).count();
        int noEmailCount = (int) all.stream().filter(e -> isBlank(e.getEmail())).count();

        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusDays(30);
        int addedLast30 = (int) all.stream()
            .map(e -> parseDate(e.getCreatedAt())).filter(d -> d != null && d.isAfter(cutoff)).count();
        int modifiedLast30 = (int) all.stream()
            .map(e -> parseDate(e.getUpdatedAt())).filter(d -> d != null && d.isAfter(cutoff)).count();
        LocalDate oldest = all.stream()
            .map(e -> parseDate(e.getUpdatedAt())).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        Long oldestAgeDays = oldest == null ? null : ChronoUnit.DAYS.between(oldest, today);

        // ── Layout ──
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(true);
        mainPanel.setBackground(DesignTokens.surface());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(
            DesignTokens.SPACE_LG, DesignTokens.SPACE_LG, DesignTokens.SPACE_LG, DesignTokens.SPACE_LG));

        // Overview
        mainPanel.add(sectionLabel(lang.getString("audit.overview")));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(statRow(
            new BentoCard(lang.getString("audit.stat_score"), score20 + "/20", null, scoreColor(score)),
            new BentoCard(lang.getString("audit.stat_to_fix"), String.valueOf(totalIssues), null,
                totalIssues > 0 ? DesignTokens.statusWeak() : DesignTokens.statusStrong()),
            new BentoCard(lang.getString("audit.stat_strong"), String.valueOf(strong.size()), null,
                strong.isEmpty() ? DesignTokens.onSurfaceFaint() : DesignTokens.statusStrong())));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        // At risk
        mainPanel.add(sectionLabel(lang.getString("audit.at_risk")));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(collapsible(
            count(lang.getString("audit.weak_passwords"), weak.size()), entriesBody(weak), null));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(collapsible(
            count(lang.getString("audit.duplicate_passwords"), dupEntries.size()), entriesBody(dupEntries), null));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(collapsible(
            count(lang.getString("audit.old_passwords").replace("{0}", String.valueOf(expiryDays)), oldEntries.size()),
            entriesBody(oldEntries), null));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(hibpSection());
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        // Strengths
        mainPanel.add(sectionLabel(lang.getString("audit.strengths")));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(collapsible(
            count(lang.getString("audit.strong_passwords"), strong.size()), entriesBody(strong), null));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(infoRow(lang.getString("audit.stat_unique"), uniquePercent + " %", scoreColor(uniquePercent)));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        // Composition
        mainPanel.add(sectionLabel(lang.getString("audit.composition")));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(statRow(
            new BentoCard(lang.getString("audit.stat_categories"), String.valueOf(categoriesCount), null, null),
            new BentoCard(lang.getString("audit.stat_favorites"), String.valueOf(favoritesCount), null, DesignTokens.favorite())));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        // Completeness
        mainPanel.add(sectionLabel(lang.getString("audit.completeness")));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(statRow(
            new BentoCard(lang.getString("audit.stat_no_url"), String.valueOf(noUrlCount), null, DesignTokens.onSurfaceFaint()),
            new BentoCard(lang.getString("audit.stat_no_email"), String.valueOf(noEmailCount), null, DesignTokens.onSurfaceFaint())));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        // Activity
        mainPanel.add(sectionLabel(lang.getString("audit.activity")));
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        mainPanel.add(statRow(
            new BentoCard(lang.getString("audit.stat_added"), String.valueOf(addedLast30), null, null),
            new BentoCard(lang.getString("audit.stat_modified"), String.valueOf(modifiedLast30), null, null),
            new BentoCard(lang.getString("audit.stat_oldest"), ageLabel(oldestAgeDays), null, null)));

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    // ── Building blocks ───────────────────────────────────────────────────────

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(DesignTokens.onSurfaceFaint());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, DesignTokens.SPACE_XS, 0, 0));
        return l;
    }

    private JComponent statRow(JComponent... cards) {
        JPanel row = new JPanel(new GridLayout(1, cards.length, DesignTokens.SPACE_MD, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JComponent c : cards) row.add(c);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        return row;
    }

    private JComponent infoRow(String label, String value, Color valueColor) {
        RoundedPanel card = fullWidthCard();
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(
            DesignTokens.SPACE_MD, DesignTokens.SPACE_LG, DesignTokens.SPACE_MD, DesignTokens.SPACE_LG));
        card.add(new JLabel(label), BorderLayout.WEST);
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 14f));
        if (valueColor != null) v.setForeground(valueColor);
        card.add(v, BorderLayout.EAST);
        return card;
    }

    /** A collapsible card: clickable header (title + optional trailing control + chevron) over a body. */
    private JComponent collapsible(String title, JComponent body, JComponent headerTrailing) {
        RoundedPanel card = fullWidthCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(DesignTokens.SPACE_SM, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(
            DesignTokens.SPACE_MD, DesignTokens.SPACE_LG, DesignTokens.SPACE_MD, DesignTokens.SPACE_LG));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        header.add(titleLabel, BorderLayout.WEST);

        JLabel chevron = new JLabel("▸"); // ▸ collapsed
        chevron.setForeground(DesignTokens.onSurfaceFaint());

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, 0));
        east.setOpaque(false);
        if (headerTrailing != null) east.add(headerTrailing);
        east.add(chevron);
        header.add(east, BorderLayout.EAST);

        body.setVisible(false);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        MouseAdapter toggle = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                setExpanded(body, chevron, !body.isVisible());
                card.revalidate();
                card.repaint();
            }
        };
        header.addMouseListener(toggle);
        titleLabel.addMouseListener(toggle);
        east.addMouseListener(toggle);
        chevron.addMouseListener(toggle);

        card.putClientProperty("chevron", chevron); // let callers expand programmatically (HIBP button)
        card.putClientProperty("body", body);

        card.add(header);
        card.add(body);
        return card;
    }

    private static void setExpanded(JComponent body, JLabel chevron, boolean expanded) {
        body.setVisible(expanded);
        chevron.setText(expanded ? "▾" : "▸"); // ▾ / ▸
    }

    private JComponent entriesBody(List<PasswordEntry> entries) {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(
            0, DesignTokens.SPACE_LG, DesignTokens.SPACE_MD, DesignTokens.SPACE_LG));
        body.add(divider());
        if (entries.isEmpty()) {
            JLabel l = new JLabel(lang.getString("audit.no_issues"));
            l.setForeground(DesignTokens.onSurfaceFaint());
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, 0, 0, 0));
            body.add(l);
        } else {
            for (PasswordEntry e : entries) body.add(entryRow(e));
        }
        return body;
    }

    private JComponent entryRow(PasswordEntry e) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_SM, 0, DesignTokens.SPACE_SM, 0));

        JLabel title = new JLabel(e.getTitle());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(title);

        String sub = !isBlank(e.getUsername()) ? e.getUsername()
                   : !isBlank(e.getEmail()) ? e.getEmail() : null;
        if (sub != null) {
            JLabel u = new JLabel(sub);
            u.setFont(u.getFont().deriveFont(Font.PLAIN, 11f));
            u.setForeground(DesignTokens.onSurfaceFaint());
            u.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(u);
        }
        col.setMaximumSize(new Dimension(Integer.MAX_VALUE, col.getPreferredSize().height));
        return col;
    }

    private JComponent divider() {
        JPanel d = new JPanel();
        d.setBackground(DesignTokens.outline());
        d.setAlignmentX(Component.LEFT_ALIGNMENT);
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        d.setPreferredSize(new Dimension(10, 1));
        return d;
    }

    /** HIBP breach section: collapsible, with an async "check now" button in its header. */
    private JComponent hibpSection() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(
            0, DesignTokens.SPACE_LG, DesignTokens.SPACE_MD, DesignTokens.SPACE_LG));
        body.add(divider());

        JLabel status = new JLabel(lang.getString("audit.breach_hint"));
        status.setForeground(DesignTokens.onSurfaceFaint());
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, 0, 0, 0));

        JProgressBar progress = new JProgressBar(0, Math.max(1, vault.getEntries().size()));
        progress.setStringPainted(true);
        progress.setVisible(false);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        JPanel results = new JPanel();
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setOpaque(false);
        results.setAlignmentX(Component.LEFT_ALIGNMENT);

        body.add(status);
        body.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        body.add(progress);
        body.add(results);

        JButton checkBtn = new JButton(lang.getString("audit.check_now"));
        checkBtn.setFocusable(false);

        JComponent card = (JComponent) collapsible(
            lang.getString("audit.breached_passwords"), body, checkBtn);
        JLabel chevron = (JLabel) card.getClientProperty("chevron");

        checkBtn.addActionListener(ev -> {
            setExpanded(body, chevron, true);
            card.revalidate();
            checkBtn.setEnabled(false);
            progress.setVisible(true);
            progress.setValue(0);
            results.removeAll();
            status.setText(lang.getString("audit.breach_checking"));
            status.setForeground(DesignTokens.onSurfaceFaint());

            // Snapshot on the EDT (where all vault mutations happen) so the background
            // HIBP loop iterates a stable copy, never the live vault view -- avoids a
            // read/write race if the user edits entries during the (slow) check.
            final List<PasswordEntry> auditEntries = new ArrayList<>(vault.getEntries());
            new SwingWorker<List<PasswordEntry>, Integer>() {
                @Override
                protected List<PasswordEntry> doInBackground() {
                    List<PasswordEntry> breached = new ArrayList<>();
                    List<PasswordEntry> entries = auditEntries;
                    for (int i = 0; i < entries.size(); i++) {
                        PasswordEntry entry = entries.get(i);
                        char[] pwd = entry.getPassword();
                        if (pwd != null && pwd.length > 0) {
                            try {
                                if (HibpChecker.checkPassword(pwd) > 0) breached.add(entry);
                            } finally {
                                SecureWiper.wipe(pwd);
                            }
                        }
                        publish(i + 1);
                    }
                    return breached;
                }

                @Override
                protected void process(List<Integer> chunks) {
                    if (!chunks.isEmpty()) progress.setValue(chunks.get(chunks.size() - 1));
                }

                @Override
                protected void done() {
                    progress.setVisible(false);
                    try {
                        List<PasswordEntry> breached = get();
                        if (breached.isEmpty()) {
                            status.setText("✅ " + lang.getString("audit.no_issues"));
                            status.setForeground(DesignTokens.statusStrong());
                        } else {
                            status.setText("⚠ " + lang.getString("audit.breach_count")
                                .replace("{0}", String.valueOf(breached.size())));
                            status.setForeground(DesignTokens.statusWeak());
                            for (PasswordEntry b : breached) results.add(entryRow(b));
                        }
                    } catch (Exception ex) {
                        status.setText("❌ " + lang.getString("audit.breach_error"));
                        status.setForeground(DesignTokens.statusWeak());
                    }
                    checkBtn.setEnabled(true);
                    card.revalidate();
                    card.repaint();
                }
            }.execute();
        });

        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RoundedPanel fullWidthCard() {
        return new RoundedPanel() {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
    }

    private String count(String label, int n) {
        return label + " (" + n + ")";
    }

    private Color scoreColor(int score) {
        if (score >= 80) return DesignTokens.statusStrong();
        if (score >= 50) return DesignTokens.statusMedium();
        return DesignTokens.statusWeak();
    }

    private String ageLabel(Long days) {
        if (days == null) return lang.getString("audit.age_none");
        if (days < 60)  return lang.getString("audit.age_days").replace("{0}", String.valueOf(days));
        if (days < 730) return lang.getString("audit.age_months").replace("{0}", String.valueOf(days / 30));
        return lang.getString("audit.age_years").replace("{0}", String.valueOf(days / 365));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private LocalDate parseDate(String iso) {
        if (iso == null || iso.length() < 10) return null;
        try {
            return LocalDate.parse(iso.substring(0, 10));
        } catch (Exception ex) {
            return null;
        }
    }

    // ── Legacy quick filters (kept for the View menu) ──────────────────────────

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
