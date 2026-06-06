package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.ui.components.BentoCard;
import com.passwordmanager.ui.components.EntryCardPanel;
import com.passwordmanager.ui.components.RoundedPanel;
import com.passwordmanager.ui.components.StrengthMeter;
import com.passwordmanager.ui.theme.DesignTokens;
import com.passwordmanager.util.FaviconService;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.EntryFilter;
import com.passwordmanager.vault.PasswordEntry;
import com.passwordmanager.vault.SortField;
import com.passwordmanager.vault.VaultService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * "Calme & confiance" passwords view: a bento health header, a calm card list (left/center) and a
 * detail pane (right). Reuses {@link VaultService} + {@link EntryDialog}; design reference:
 * test_design/PC/02-vault-passwords.html. Single-selection with a right-click context menu.
 */
public class CoffrePasswordsPanel extends JPanel {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final VaultService vaultService;
    private int clipboardClearSeconds;
    private Runnable onVaultChanged;
    private FaviconService faviconService;
    private boolean faviconsEnabled = true;

    private final JTextField searchField = new JTextField();
    private final JPanel cardsHost = new JPanel();
    private final JPanel detailHost = new JPanel(new BorderLayout());
    private final BentoCard[] bento = new BentoCard[3];
    private final JPanel bentoRow = new JPanel(new GridLayout(1, 3, DesignTokens.SPACE_MD, 0));

    private List<PasswordEntry> displayed = new ArrayList<>();
    private String selectedId;
    private String currentCategory;
    private SortField currentSort = SortField.TITLE;
    private boolean sortAscending = true;
    private boolean favoritesOnly = false;
    private boolean weakOnly = false;

    private javax.swing.Timer clipboardTimer;

    public CoffrePasswordsPanel(VaultService vaultService, int clipboardClearSeconds, Runnable onVaultChanged) {
        this.vaultService = vaultService;
        this.clipboardClearSeconds = clipboardClearSeconds;
        this.onVaultChanged = onVaultChanged;
        initComponents();
        refresh();
    }

    public void setFaviconService(FaviconService s) { this.faviconService = s; }
    public void setFaviconsEnabled(boolean b) { this.faviconsEnabled = b; refresh(); }
    public void setClipboardClearSeconds(int s) { this.clipboardClearSeconds = s; }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // CENTER: filter chips + bento + card list
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);

        // Filter chip row
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, DesignTokens.SPACE_SM, DesignTokens.SPACE_SM));
        chips.setOpaque(false);
        JToggleButton allChip = chip(lang.getString("filter.all_short"), true);
        JToggleButton favChip = chip(lang.getString("filter.favorites_only"), false);
        JToggleButton weakChip = chip(lang.getString("strength.weak"), false);
        ButtonGroup ignoredGroup = new ButtonGroup(); // allow independent toggles; keep refs
        allChip.addActionListener(e -> { favoritesOnly = false; weakOnly = false; favChip.setSelected(false); weakChip.setSelected(false); allChip.setSelected(true); refresh(); });
        favChip.addActionListener(e -> { favoritesOnly = favChip.isSelected(); allChip.setSelected(!favoritesOnly && !weakOnly); refresh(); });
        weakChip.addActionListener(e -> { weakOnly = weakChip.isSelected(); allChip.setSelected(!favoritesOnly && !weakOnly); refresh(); });
        chips.add(allChip); chips.add(favChip); chips.add(weakChip);

        // Bento
        bentoRow.setOpaque(false);
        bentoRow.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_SM, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(chips, BorderLayout.NORTH);
        top.add(bentoRow, BorderLayout.SOUTH);
        center.add(top, BorderLayout.NORTH);

        // Card list
        cardsHost.setLayout(new BoxLayout(cardsHost, BoxLayout.Y_AXIS));
        cardsHost.setOpaque(false);
        cardsHost.setBorder(BorderFactory.createEmptyBorder(0, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD));
        JScrollPane scroll = new JScrollPane(cardsHost);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);

        // RIGHT: detail
        detailHost.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL)));
        detailHost.setPreferredSize(new Dimension(330, 0));
        clearDetail();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, detailHost);
        split.setResizeWeight(1.0);
        split.setDividerLocation(620);
        split.setBorder(null);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
    }

    /** Search field is owned by the toolbar in the shell; expose it for wiring. */
    public JTextField getSearchField() { return searchField; }

    private JToggleButton chip(String text, boolean selected) {
        JToggleButton b = new JToggleButton(text, selected);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        return b;
    }

    public void setCategory(String category) {
        this.currentCategory = category;
        refresh();
    }

    public void setSortMode(SortField sort) {
        if (this.currentSort == sort) sortAscending = !sortAscending;
        else { this.currentSort = sort; sortAscending = true; }
        refresh();
    }

    public void refresh() {
        // Build the displayed list (search + category + chips), like VaultPanel.
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        List<PasswordEntry> entries;
        if (!query.isEmpty()) {
            entries = vaultService.search(query);
            if (currentCategory != null) {
                List<PasswordEntry> f = new ArrayList<>();
                for (PasswordEntry e : entries) if (currentCategory.equals(e.getCategory())) f.add(e);
                entries = f;
            }
        } else if (currentCategory != null) {
            entries = vaultService.getByCategory(currentCategory);
        } else {
            entries = vaultService.search("");
        }

        EntryFilter.Builder fb = new EntryFilter.Builder();
        if (favoritesOnly) fb.favoritesOnly(true);
        if (weakOnly) fb.exactStrength(Strength.WEAK);
        EntryFilter filter = fb.build();
        if (filter.hasActiveFilters()) entries = vaultService.filter(entries, filter);

        displayed = vaultService.sorted(entries, currentSort);
        if (!sortAscending) java.util.Collections.reverse(displayed);

        // Keep a selection so the detail pane is populated (auto-select first).
        if ((selectedId == null || getSelected() == null) && !displayed.isEmpty()) {
            selectedId = displayed.get(0).getId();
        }
        rebuildBento();
        rebuildList();
        PasswordEntry sel = getSelected();
        if (sel != null) showDetail(sel);
        else clearDetail();
    }

    private void rebuildList() {
        cardsHost.removeAll();
        for (PasswordEntry e : displayed) {
            cardsHost.add(buildCard(e));
            cardsHost.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        }
        if (displayed.isEmpty()) {
            JLabel empty = new JLabel(lang.getString("vault.no_entries"), SwingConstants.CENTER);
            empty.setForeground(DesignTokens.onSurfaceFaint());
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            cardsHost.add(Box.createVerticalStrut(DesignTokens.SPACE_XXL));
            cardsHost.add(empty);
        }
        cardsHost.add(Box.createVerticalGlue());
        cardsHost.revalidate();
        cardsHost.repaint();
    }

    private EntryCardPanel buildCard(PasswordEntry e) {
        char[] pwd = e.getPassword();
        Strength strength;
        String label;
        try {
            strength = PasswordStrengthAnalyzer.analyze(pwd);
            label = strengthLabel(strength);
        } finally {
            SecureWiper.wipe(pwd);
        }
        String subtitle = !isBlank(e.getUsername()) ? e.getUsername()
            : (!isBlank(e.getEmail()) ? e.getEmail() : (!isBlank(e.getUrl()) ? e.getUrl() : ""));
        EntryCardPanel card = new EntryCardPanel(e.getTitle(), subtitle, e.getCategory(),
            faviconImage(e), strength, label, e.isFavorite());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (e.getId().equals(selectedId)) {
            card.setFillColor(DesignTokens.softTint(DesignTokens.accent()));
        }
        card.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent ev) { maybePopup(ev, e); }
            @Override public void mouseReleased(MouseEvent ev) { maybePopup(ev, e); }
            @Override public void mouseClicked(MouseEvent ev) {
                if (SwingUtilities.isLeftMouseButton(ev)) {
                    if (ev.getClickCount() == 2) { selectedId = e.getId(); editSelected(); }
                    else { select(e); }
                }
            }
        });
        return card;
    }

    private void maybePopup(MouseEvent ev, PasswordEntry e) {
        if (ev.isPopupTrigger()) {
            select(e);
            contextMenu(e).show(ev.getComponent(), ev.getX(), ev.getY());
        }
    }

    private void select(PasswordEntry e) {
        selectedId = e.getId();
        showDetail(e);
        rebuildList(); // refresh highlight
    }

    // ---- Bento ----
    private void rebuildBento() {
        List<PasswordEntry> all = vaultService.search("");
        int total = all.size();
        int weak = 0, sumPoints = 0;
        for (PasswordEntry e : all) {
            char[] p = e.getPassword();
            try {
                Strength s = PasswordStrengthAnalyzer.analyze(p);
                switch (s) {
                    case WEAK: weak++; sumPoints += 25; break;
                    case MEDIUM: sumPoints += 55; break;
                    case STRONG: sumPoints += 85; break;
                    case VERY_STRONG: sumPoints += 100; break;
                }
            } finally { SecureWiper.wipe(p); }
        }
        int score = total == 0 ? 100 : Math.round(sumPoints / (float) total);
        int reused = vaultService.findDuplicatePasswords().values().stream().mapToInt(List::size).sum();

        bentoRow.removeAll();
        Color scoreColor = score >= 80 ? DesignTokens.statusStrong()
            : (score >= 50 ? DesignTokens.statusMedium() : DesignTokens.statusWeak());
        bentoRow.add(new BentoCard(lang.getString("audit.title"), String.valueOf(score),
            total + " " + lang.getString("vault.entries"), scoreColor));
        bentoRow.add(new BentoCard(lang.getString("strength.weak"), String.valueOf(weak),
            lang.getString("filter.strength"), weak > 0 ? DesignTokens.statusWeak() : DesignTokens.onSurfaceFaint()));
        bentoRow.add(new BentoCard(lang.getString("audit.duplicate_passwords"), String.valueOf(reused),
            lang.getString("audit.duplicate_passwords"), reused > 0 ? DesignTokens.statusMedium() : DesignTokens.onSurfaceFaint()));
        bentoRow.revalidate();
        bentoRow.repaint();
    }

    // ---- Detail pane ----
    private void clearDetail() {
        detailHost.removeAll();
        JLabel empty = new JLabel(lang.getString("vault.details"), SwingConstants.CENTER);
        empty.setForeground(DesignTokens.onSurfaceFaint());
        detailHost.add(empty, BorderLayout.CENTER);
        detailHost.revalidate();
        detailHost.repaint();
    }

    private void showDetail(PasswordEntry e) {
        detailHost.removeAll();
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        com.passwordmanager.ui.components.Avatar avatar = new com.passwordmanager.ui.components.Avatar(56);
        avatar.set(e.getTitle(), e.getCategory(), faviconImage(e));
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(avatar);
        col.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));

        JLabel title = new JLabel(e.getTitle());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(title);
        col.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        if (!isBlank(e.getUsername())) col.add(fieldRow(lang.getString("entry.username"), e.getUsername(), () -> copyText(e.getUsername())));
        if (!isBlank(e.getEmail())) col.add(fieldRow(lang.getString("entry.email"), e.getEmail(), () -> copyText(e.getEmail())));

        // Password (mono, masked) via SecretFieldPanel + strength meter
        col.add(caption(lang.getString("entry.password")));
        com.passwordmanager.ui.components.SecretFieldPanel secret = new com.passwordmanager.ui.components.SecretFieldPanel();
        char[] pwd = e.getPassword();
        secret.setValue(pwd);
        StrengthMeter meter = new StrengthMeter();
        meter.update(pwd);
        SecureWiper.wipe(pwd);
        secret.setAlignmentX(Component.LEFT_ALIGNMENT);
        secret.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        col.add(secret);
        meter.setAlignmentX(Component.LEFT_ALIGNMENT);
        meter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        col.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        col.add(meter);

        if (!isBlank(e.getUrl())) {
            col.add(caption(lang.getString("entry.url")));
            JLabel url = new JLabel(e.getUrl());
            url.setForeground(DesignTokens.accent());
            url.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            url.setAlignmentX(Component.LEFT_ALIGNMENT);
            url.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent ev) { openUrl(e.getUrl()); }
            });
            col.add(url);
            col.add(Box.createVerticalStrut(DesignTokens.SPACE_MD));
        }
        if (!isBlank(e.getCategory())) col.add(fieldRow(lang.getString("entry.category"), e.getCategory(), null));
        if (!isBlank(e.getNotes())) col.add(fieldRow(lang.getString("entry.notes"), e.getNotes(), null));

        col.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, DesignTokens.SPACE_SM, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JButton edit = com.passwordmanager.ui.components.Buttons.tonal(lang.getString("vault.edit_entry"));
        edit.addActionListener(ev -> editSelected());
        JButton dup = new JButton(lang.getString("menu.duplicate"));
        dup.addActionListener(ev -> duplicate(e));
        actions.add(edit); actions.add(dup);
        col.add(actions);

        JScrollPane sc = new JScrollPane(col);
        sc.setBorder(null);
        sc.setOpaque(false);
        sc.getViewport().setOpaque(false);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        detailHost.add(sc, BorderLayout.CENTER);
        detailHost.revalidate();
        detailHost.repaint();
    }

    private JComponent caption(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(DesignTokens.onSurfaceFaint());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, 0, 5, 0));
        return l;
    }

    private JComponent fieldRow(String captionText, String value, Runnable onCopy) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, 0, 0, 0));
        p.add(caption(captionText), BorderLayout.NORTH);
        RoundedPanel v = new RoundedPanel();
        v.setArc(DesignTokens.RADIUS_BUTTON);
        v.setFillColor(DesignTokens.surfaceSubtle());
        v.setDrawBorder(false);
        v.setLayout(new BorderLayout(DesignTokens.SPACE_SM, 0));
        v.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 8));
        JLabel val = new JLabel(value);
        v.add(val, BorderLayout.CENTER);
        if (onCopy != null) {
            JButton copy = new JButton("⎘");
            copy.setMargin(new Insets(1, 6, 1, 6));
            copy.setToolTipText(lang.getString("entry.copy_password"));
            copy.addActionListener(ev -> onCopy.run());
            v.add(copy, BorderLayout.EAST);
        }
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    // ---- Context menu + actions ----
    private JPopupMenu contextMenu(PasswordEntry e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem(lang.getString("vault.edit_entry"));
        edit.addActionListener(ev -> editSelected());
        menu.add(edit);
        JMenuItem del = new JMenuItem(lang.getString("vault.delete_entry"));
        del.addActionListener(ev -> deleteEntry(e));
        menu.add(del);
        menu.addSeparator();
        JMenuItem fav = new JMenuItem(lang.getString("entry.toggle_favorite"));
        fav.addActionListener(ev -> { vaultService.toggleFavorite(e.getId()); refresh(); notifyChanged(); });
        menu.add(fav);
        menu.addSeparator();
        JMenuItem copyPwd = new JMenuItem(lang.getString("entry.copy_password"));
        copyPwd.addActionListener(ev -> copyPassword(e));
        menu.add(copyPwd);
        if (!isBlank(e.getUsername())) {
            JMenuItem cu = new JMenuItem(lang.getString("entry.copy_username"));
            cu.addActionListener(ev -> copyText(e.getUsername()));
            menu.add(cu);
        }
        if (!isBlank(e.getUrl())) {
            JMenuItem ou = new JMenuItem(lang.getString("menu.open.url"));
            ou.addActionListener(ev -> openUrl(e.getUrl()));
            menu.add(ou);
        }
        JMenuItem dupItem = new JMenuItem(lang.getString("menu.duplicate"));
        dupItem.addActionListener(ev -> duplicate(e));
        menu.add(dupItem);
        return menu;
    }

    public void addNewEntry() {
        EntryDialog dlg = new EntryDialog((Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.new_entry"), null, vaultService.getVault().getCategories());
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            vaultService.addEntry(dlg.getEntry());
            warmFavicon(dlg.getEntry());
            refresh();
            notifyChanged();
        }
    }

    public void editSelected() {
        PasswordEntry sel = getSelected();
        if (sel == null) return;
        EntryDialog dlg = new EntryDialog((Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.edit_entry"), sel, vaultService.getVault().getCategories());
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            vaultService.updateEntry(dlg.getEntry());
            warmFavicon(dlg.getEntry());
            refresh();
            showDetail(dlg.getEntry());
            notifyChanged();
        }
    }

    public void deleteSelected() {
        PasswordEntry sel = getSelected();
        if (sel != null) deleteEntry(sel);
    }

    private void deleteEntry(PasswordEntry e) {
        int confirm = JOptionPane.showConfirmDialog(this, lang.getString("vault.delete_confirm"),
            lang.getString("vault.delete_entry"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            vaultService.bulkDelete(List.of(e.getId()));
            if (e.getId().equals(selectedId)) { selectedId = null; clearDetail(); }
            refresh();
            notifyChanged();
        }
    }

    private void duplicate(PasswordEntry selected) {
        char[] pwdCopy = selected.getPassword();
        try {
            PasswordEntry dup = new PasswordEntry(
                lang.getString("menu.duplicate.prefix") + " " + selected.getTitle(),
                selected.getUsername(), selected.getEmail(), pwdCopy, selected.getUrl(),
                selected.getNotes(), selected.getCategory(),
                selected.getTags() != null ? new ArrayList<>(selected.getTags()) : null);
            vaultService.addEntry(dup);
            refresh();
            notifyChanged();
        } finally {
            SecureWiper.wipe(pwdCopy);
        }
    }

    private PasswordEntry getSelected() {
        if (selectedId == null) return null;
        for (PasswordEntry e : displayed) if (selectedId.equals(e.getId())) return e;
        return null;
    }

    private void copyPassword(PasswordEntry e) {
        char[] p = e.getPassword();
        if (p == null) return;
        SecureClipboard.copyPassword(p);
        SecureWiper.wipe(p);
        scheduleClipboardClear();
    }

    private void copyText(String text) {
        if (isBlank(text)) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        scheduleClipboardClear();
    }

    private void openUrl(String url) {
        if (isBlank(url)) return;
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        try { Desktop.getDesktop().browse(new URI(url)); } catch (Exception ignored) {}
    }

    private void scheduleClipboardClear() {
        if (clipboardTimer != null) clipboardTimer.stop();
        clipboardTimer = new javax.swing.Timer(clipboardClearSeconds * 1000, evt ->
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(""), null));
        clipboardTimer.setRepeats(false);
        clipboardTimer.start();
    }

    public void cancelClipboardTimer() {
        if (clipboardTimer != null) { clipboardTimer.stop(); clipboardTimer = null; }
    }

    // ---- Favicons (cache-only display, network warm on create/edit) ----
    private Image faviconImage(PasswordEntry e) {
        if (faviconService == null || !faviconsEnabled || isBlank(e.getUrl())) return null;
        try {
            byte[] data = faviconService.getCachedFavicon(e.getUrl());
            if (data != null && data.length > 0) {
                return new ImageIcon(data).getImage();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void warmFavicon(PasswordEntry entry) {
        if (faviconService == null || !faviconsEnabled || entry == null || isBlank(entry.getUrl())) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try { faviconService.getFavicon(entry.getUrl()); } catch (Exception ignored) {}
                return null;
            }
            @Override protected void done() { refresh(); }
        }.execute();
    }

    private String strengthLabel(Strength s) {
        switch (s) {
            case WEAK: return lang.getString("strength.weak");
            case MEDIUM: return lang.getString("strength.medium");
            case STRONG: return lang.getString("strength.strong");
            case VERY_STRONG: return lang.getString("strength.very_strong");
            default: return "";
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private void notifyChanged() { if (onVaultChanged != null) onVaultChanged.run(); }
}
