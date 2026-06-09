package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.ui.components.BentoCard;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.components.ControlIcon;
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
import java.awt.event.ActionEvent;
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
    private final java.util.LinkedHashSet<String> selectedIds = new java.util.LinkedHashSet<>();
    private String anchorId;
    private EntryCardPanel focusedCard;
    // In-memory MRU of recently "used" entries (copied/edited), most-recent first.
    private final java.util.LinkedList<String> recentIds = new java.util.LinkedList<>();
    private final JPanel recentRow = new JPanel();
    private SortField currentSort = SortField.TITLE;
    private boolean sortAscending = true;
    private boolean favoritesOnly = false;
    // Multi-select filters (mirrors the Android FilterSheet): empty set = no filter on that axis.
    private final java.util.LinkedHashSet<String> selectedCategories = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Strength> selectedStrengths = new java.util.LinkedHashSet<>();
    private java.time.LocalDate createdSince, modifiedSince, createdOn, modifiedOn;
    private JPanel filterPanel;
    private JPanel filterChipsHost;
    private final java.util.List<Runnable> filterResetters = new ArrayList<>();

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

        // Row 1: search (grows) + sort/filter icon controls + new-entry button.
        searchField.putClientProperty("JTextField.placeholderText", lang.getString("vault.search"));

        JButton sortBtn = Buttons.icon(new ControlIcon(ControlIcon.Kind.SORT), lang.getString("filter.sort"));
        sortBtn.addActionListener(e -> showSortMenu(sortBtn));
        JToggleButton filterBtn = Buttons.iconToggle(new ControlIcon(ControlIcon.Kind.FILTER), lang.getString("filter.filters"));
        filterBtn.addActionListener(e -> { filterPanel.setVisible(filterBtn.isSelected()); revalidate(); });
        JButton newBtn = Buttons.primary("+ " + lang.getString("vault.new_entry"));
        newBtn.addActionListener(e -> addNewEntry());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, 0));
        actions.setOpaque(false);
        actions.add(sortBtn);
        actions.add(filterBtn);
        actions.add(newBtn);

        JPanel row1 = new JPanel(new BorderLayout(DesignTokens.SPACE_SM, 0));
        row1.setOpaque(false);
        row1.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, DesignTokens.SPACE_MD, DesignTokens.SPACE_SM, DesignTokens.SPACE_MD));
        row1.add(searchField, BorderLayout.CENTER);
        row1.add(actions, BorderLayout.EAST);

        filterPanel = buildFilterPanel();

        // Bento
        bentoRow.setOpaque(false);
        bentoRow.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_SM, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD));

        // Recently used (in-memory MRU; hidden until something is used)
        recentRow.setOpaque(false);
        recentRow.setLayout(new BoxLayout(recentRow, BoxLayout.Y_AXIS));
        recentRow.setBorder(BorderFactory.createEmptyBorder(0, DesignTokens.SPACE_MD, DesignTokens.SPACE_SM, DesignTokens.SPACE_MD));
        recentRow.setVisible(false);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(row1);
        top.add(filterPanel);
        top.add(bentoRow);
        top.add(recentRow);
        center.add(top, BorderLayout.NORTH);

        // Card list
        cardsHost.setLayout(new BoxLayout(cardsHost, BoxLayout.Y_AXIS));
        cardsHost.setOpaque(false);
        cardsHost.setBorder(BorderFactory.createEmptyBorder(0, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD));
        JScrollPane scroll = new JScrollPane(cardsHost);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(700, 470));
        center.add(scroll, BorderLayout.CENTER);

        // RIGHT: detail
        detailHost.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL)));
        detailHost.setPreferredSize(new Dimension(360, 0));
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

    /** Sort menu opened from the sort icon: a direction toggle + one item per sort field. */
    private void showSortMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem dir = new JMenuItem((sortAscending ? "▲  " : "▼  ")
            + lang.getString(sortAscending ? "sort.ascending" : "sort.descending"));
        dir.addActionListener(e -> { sortAscending = !sortAscending; refresh(); });
        menu.add(dir);
        menu.addSeparator();

        String[] keys = { "entry.title", "entry.username", "entry.email", "entry.url",
            "entry.category", "strength.label", "sort.created", "sort.modified" };
        SortField[] fields = { SortField.TITLE, SortField.USERNAME, SortField.EMAIL, SortField.URL,
            SortField.CATEGORY, SortField.STRENGTH, SortField.CREATED, SortField.DATE };
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < fields.length; i++) {
            SortField f = fields[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(lang.getString(keys[i]), currentSort == f);
            item.addActionListener(e -> { currentSort = f; refresh(); });
            group.add(item);
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    private JToggleButton chip(String text, boolean selected) {
        JToggleButton b = new JToggleButton(text, selected);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        return b;
    }

    private JPanel buildFilterPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_SM, DesignTokens.SPACE_MD, DesignTokens.SPACE_SM, DesignTokens.SPACE_MD)));

        // On/off multi-select chips (favorites / categories / strengths), rebuilt from the live vault.
        filterChipsHost = new JPanel();
        filterChipsHost.setLayout(new BoxLayout(filterChipsHost, BoxLayout.Y_AXIS));
        filterChipsHost.setOpaque(false);
        filterChipsHost.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(filterChipsHost);

        // Date filters (state preserved across refreshes; built once)
        p.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        p.add(filterSectionLabel(lang.getString("filter.dates")));
        JPanel dates = new JPanel(new GridBagLayout());
        dates.setOpaque(false);
        dates.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        addDateFilter(dates, g, 0, "filter.created_since", d -> createdSince = d);
        addDateFilter(dates, g, 1, "filter.modified_since", d -> modifiedSince = d);
        addDateFilter(dates, g, 2, "filter.created_on", d -> createdOn = d);
        addDateFilter(dates, g, 3, "filter.modified_on", d -> modifiedOn = d);
        dates.setMaximumSize(new Dimension(Integer.MAX_VALUE, dates.getPreferredSize().height));
        p.add(dates);

        JButton clear = new JButton(lang.getString("filter.clear"));
        clear.setAlignmentX(Component.LEFT_ALIGNMENT);
        clear.addActionListener(e -> clearFilters());
        p.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        p.add(clear);

        p.setVisible(false);
        rebuildFilterChips();
        return p;
    }

    /** Rebuilds the favorites/category/strength chip sections from the current vault + selections. */
    private void rebuildFilterChips() {
        if (filterChipsHost == null) return;
        filterChipsHost.removeAll();

        JToggleButton favChip = chip(lang.getString("filter.favorites"), favoritesOnly);
        favChip.addActionListener(e -> { favoritesOnly = favChip.isSelected(); refresh(); });
        filterChipsHost.add(filterSection(lang.getString("entry.favorite"), List.of(favChip)));

        List<String> cats = vaultService.getVault().getCategories();
        if (!cats.isEmpty()) {
            List<JToggleButton> catChips = new ArrayList<>();
            for (String c : cats) {
                JToggleButton b = chip(c, selectedCategories.contains(c));
                b.addActionListener(e -> {
                    if (b.isSelected()) selectedCategories.add(c); else selectedCategories.remove(c);
                    refresh();
                });
                catChips.add(b);
            }
            filterChipsHost.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
            filterChipsHost.add(filterSection(lang.getString("entry.category"), catChips));
        }

        Strength[] strengths = { Strength.WEAK, Strength.MEDIUM, Strength.STRONG, Strength.VERY_STRONG };
        List<JToggleButton> strengthChips = new ArrayList<>();
        for (Strength s : strengths) {
            JToggleButton b = chip(strengthLabel(s), selectedStrengths.contains(s));
            b.addActionListener(e -> {
                if (b.isSelected()) selectedStrengths.add(s); else selectedStrengths.remove(s);
                refresh();
            });
            strengthChips.add(b);
        }
        filterChipsHost.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        filterChipsHost.add(filterSection(lang.getString("filter.strength"), strengthChips));

        filterChipsHost.revalidate();
        filterChipsHost.repaint();
    }

    private JComponent filterSection(String title, List<JToggleButton> chips) {
        JPanel sec = new JPanel();
        sec.setLayout(new BoxLayout(sec, BoxLayout.Y_AXIS));
        sec.setOpaque(false);
        sec.setAlignmentX(Component.LEFT_ALIGNMENT);
        sec.add(filterSectionLabel(title));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, DesignTokens.SPACE_SM, DesignTokens.SPACE_XS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JToggleButton b : chips) row.add(b);
        sec.add(row);
        sec.setMaximumSize(new Dimension(Integer.MAX_VALUE, sec.getPreferredSize().height));
        return sec;
    }

    private JLabel filterSectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(DesignTokens.onSurfaceFaint());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void addDateFilter(JPanel p, GridBagConstraints g, int row, String key,
                               java.util.function.Consumer<java.time.LocalDate> setter) {
        JCheckBox cb = new JCheckBox(lang.getString(key));
        JSpinner sp = new JSpinner(new SpinnerDateModel());
        sp.setEditor(new JSpinner.DateEditor(sp, "yyyy-MM-dd"));
        sp.setEnabled(false);
        Runnable upd = () -> { setter.accept(cb.isSelected() ? toLocalDate((java.util.Date) sp.getValue()) : null); refresh(); };
        cb.addActionListener(e -> { sp.setEnabled(cb.isSelected()); upd.run(); });
        sp.addChangeListener(e -> { if (cb.isSelected()) upd.run(); });
        g.gridx = 0; g.gridy = row; p.add(cb, g);
        g.gridx = 1; p.add(sp, g);
        filterResetters.add(() -> { cb.setSelected(false); sp.setEnabled(false); });
    }

    private void clearFilters() {
        for (Runnable r : filterResetters) r.run();
        selectedCategories.clear();
        selectedStrengths.clear();
        favoritesOnly = false;
        createdSince = modifiedSince = createdOn = modifiedOn = null;
        refresh();
    }

    private static java.time.LocalDate toLocalDate(java.util.Date d) {
        return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    private static java.time.LocalDate dateOf(String iso) {
        if (iso == null || iso.length() < 10) return null;
        try { return java.time.LocalDate.parse(iso.substring(0, 10)); } catch (Exception e) { return null; }
    }

    private static String formatDate(String iso) {
        if (iso == null) return "";
        if (iso.length() >= 16 && iso.charAt(10) == 'T') return iso.substring(0, 10) + " " + iso.substring(11, 16);
        return iso.length() >= 10 ? iso.substring(0, 10) : iso;
    }

    public void setSortMode(SortField sort) {
        if (this.currentSort == sort) sortAscending = !sortAscending;
        else { this.currentSort = sort; sortAscending = true; }
        refresh();
    }

    public void refresh() {
        // Build the displayed list: search, then multi-select filters (favorites/category/strength)
        // + date filters. Mirrors the Android FilterSheet semantics (empty set = no filter).
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        List<PasswordEntry> entries = new ArrayList<>(
            query.isEmpty() ? vaultService.search("") : vaultService.search(query));

        if (favoritesOnly) entries.removeIf(e -> !e.isFavorite());
        if (!selectedCategories.isEmpty()) entries.removeIf(e -> !selectedCategories.contains(e.getCategory()));
        if (!selectedStrengths.isEmpty()) {
            entries.removeIf(e -> {
                char[] pw = e.getPassword();
                if (pw == null) return true;
                Strength s = PasswordStrengthAnalyzer.analyze(pw);
                SecureWiper.wipe(pw);
                return !selectedStrengths.contains(s);
            });
        }

        // Date filters (UI-side; :core EntryFilter has no date support)
        if (createdSince != null || modifiedSince != null || createdOn != null || modifiedOn != null) {
            entries.removeIf(en -> {
                java.time.LocalDate c = dateOf(en.getCreatedAt());
                java.time.LocalDate m = dateOf(en.getUpdatedAt());
                if (createdSince != null && (c == null || c.isBefore(createdSince))) return true;
                if (modifiedSince != null && (m == null || m.isBefore(modifiedSince))) return true;
                if (createdOn != null && (c == null || !c.isEqual(createdOn))) return true;
                if (modifiedOn != null && (m == null || !m.isEqual(modifiedOn))) return true;
                return false;
            });
        }

        displayed = vaultService.sorted(entries, currentSort);
        if (!sortAscending) java.util.Collections.reverse(displayed);

        // Drop selections no longer present; auto-select first if nothing valid remains.
        selectedIds.removeIf(id -> entryById(id) == null);
        if (selectedIds.isEmpty() && !displayed.isEmpty()) {
            selectedIds.add(displayed.get(0).getId());
            anchorId = displayed.get(0).getId();
        }
        rebuildFilterChips();
        rebuildBento();
        rebuildRecent();
        rebuildList();
        updateDetailOrBulk();
    }

    private void rebuildList() {
        cardsHost.removeAll();
        focusedCard = null;
        for (PasswordEntry e : displayed) {
            EntryCardPanel c = buildCard(e);
            if (selectedIds.size() == 1 && selectedIds.contains(e.getId())) focusedCard = c;
            cardsHost.add(c);
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
        if (selectedIds.contains(e.getId())) {
            card.setFillColor(DesignTokens.softTint(DesignTokens.accent()));
        }
        card.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent ev) { maybePopup(ev, e); }
            @Override public void mouseReleased(MouseEvent ev) { maybePopup(ev, e); }
            @Override public void mouseClicked(MouseEvent ev) {
                if (SwingUtilities.isLeftMouseButton(ev)) {
                    if (ev.getClickCount() == 2) { selectSingle(e); editSelected(); }
                    else if (ev.isShiftDown()) { selectRange(e); }
                    else if (ev.isControlDown() || ev.isMetaDown()) { toggleSelect(e); }
                    else { selectSingle(e); }
                }
            }
        });
        // Keyboard navigation: arrows move the selection, Enter edits.
        card.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("UP"), "navUp");
        card.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"), "navDown");
        card.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "navEnter");
        card.getActionMap().put("navUp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { selectByOffset(-1); } });
        card.getActionMap().put("navDown", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { selectByOffset(1); } });
        card.getActionMap().put("navEnter", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { editSelected(); } });
        return card;
    }

    private void maybePopup(MouseEvent ev, PasswordEntry e) {
        if (ev.isPopupTrigger()) {
            // Convert the click to this panel's coordinates BEFORE (re)selecting: selectSingle
            // rebuilds the list and detaches the clicked card, so we must anchor the popup on a
            // component that stays on screen (this panel).
            Point p = SwingUtilities.convertPoint(ev.getComponent(), ev.getPoint(), this);
            if (!selectedIds.contains(e.getId())) selectSingle(e);
            JPopupMenu menu = selectedIds.size() > 1 ? bulkMenu() : contextMenu(e);
            menu.show(this, p.x, p.y);
        }
    }

    private void selectSingle(PasswordEntry e) {
        selectedIds.clear();
        selectedIds.add(e.getId());
        anchorId = e.getId();
        rebuildList();
        updateDetailOrBulk();
        focusSelectedCard();
    }

    /** Moves the single selection by {@code delta} rows (keyboard navigation) and keeps focus. */
    private void selectByOffset(int delta) {
        if (displayed.isEmpty()) return;
        int idx = selectedIds.size() == 1 ? indexOf(selectedIds.iterator().next()) : -1;
        int next = Math.max(0, Math.min(displayed.size() - 1, idx + delta));
        selectSingle(displayed.get(next));
    }

    private void focusSelectedCard() {
        if (focusedCard != null) focusedCard.requestFocusInWindow();
    }

    private void toggleSelect(PasswordEntry e) {
        if (!selectedIds.add(e.getId())) selectedIds.remove(e.getId());
        anchorId = e.getId();
        rebuildList();
        updateDetailOrBulk();
    }

    private void selectRange(PasswordEntry e) {
        int a = indexOf(anchorId), b = indexOf(e.getId());
        if (a < 0 || b < 0) { selectSingle(e); return; }
        selectedIds.clear();
        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
            selectedIds.add(displayed.get(i).getId());
        }
        rebuildList();
        updateDetailOrBulk();
    }

    private int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < displayed.size(); i++) {
            if (displayed.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private void updateDetailOrBulk() {
        if (selectedIds.size() > 1) {
            showBulk(selectedIds.size());
        } else {
            PasswordEntry s = getSelected();
            if (s != null) showDetail(s);
            else clearDetail();
        }
    }

    // ---- Bento (Entries / Favorites / Security, computed on the whole vault) ----
    private void rebuildBento() {
        List<PasswordEntry> all = vaultService.search("");
        int total = all.size();
        int favorites = 0, sumPoints = 0;
        for (PasswordEntry e : all) {
            if (e.isFavorite()) favorites++;
            char[] p = e.getPassword();
            if (p == null) continue;
            try {
                switch (PasswordStrengthAnalyzer.analyze(p)) {
                    case WEAK: sumPoints += 25; break;
                    case MEDIUM: sumPoints += 55; break;
                    case STRONG: sumPoints += 85; break;
                    case VERY_STRONG: sumPoints += 100; break;
                }
            } finally { SecureWiper.wipe(p); }
        }
        int score = total == 0 ? 100 : Math.round(sumPoints / (float) total);
        int score20 = (int) Math.round(score / 5.0);
        Color scoreColor = score >= 80 ? DesignTokens.statusStrong()
            : (score >= 50 ? DesignTokens.statusMedium() : DesignTokens.statusWeak());

        bentoRow.removeAll();
        bentoRow.add(new BentoCard(lang.getString("dashboard.entries"), String.valueOf(total), null, null));
        bentoRow.add(new BentoCard(lang.getString("dashboard.favorites"), String.valueOf(favorites), null,
            favorites > 0 ? DesignTokens.favorite() : DesignTokens.onSurfaceFaint()));
        bentoRow.add(new BentoCard(lang.getString("dashboard.security"), score20 + "/20", null, scoreColor));
        bentoRow.revalidate();
        bentoRow.repaint();
    }

    // ---- Recently used (in-memory MRU) ----
    private void markRecent(String id) {
        if (id == null) return;
        recentIds.remove(id);
        recentIds.addFirst(id);
        while (recentIds.size() > 6) recentIds.removeLast();
        rebuildRecent();
    }

    private void rebuildRecent() {
        recentRow.removeAll();
        List<PasswordEntry> items = new ArrayList<>();
        for (String id : recentIds) {
            PasswordEntry e = fullEntryById(id);
            if (e != null) items.add(e);
            if (items.size() >= 6) break;
        }
        if (items.isEmpty()) {
            recentRow.setVisible(false);
            recentRow.revalidate();
            recentRow.repaint();
            return;
        }
        recentRow.setVisible(true);
        recentRow.add(filterSectionLabel(lang.getString("dashboard.recent")));
        recentRow.add(Box.createVerticalStrut(DesignTokens.SPACE_XS));
        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, DesignTokens.SPACE_SM, 0));
        pills.setOpaque(false);
        pills.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (PasswordEntry e : items) pills.add(recentPill(e));
        pills.setMaximumSize(new Dimension(Integer.MAX_VALUE, pills.getPreferredSize().height));
        recentRow.add(pills);
        recentRow.revalidate();
        recentRow.repaint();
    }

    private JComponent recentPill(PasswordEntry e) {
        RoundedPanel pill = new RoundedPanel();
        pill.setArc(20);
        pill.setFillColor(DesignTokens.surfaceContainer());
        pill.setLayout(new FlowLayout(FlowLayout.LEFT, DesignTokens.SPACE_SM, DesignTokens.SPACE_XS));
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        com.passwordmanager.ui.components.Avatar av = new com.passwordmanager.ui.components.Avatar(24);
        av.set(e.getTitle(), e.getCategory(), faviconImage(e));
        pill.add(av);
        pill.add(new JLabel(e.getTitle()));
        pill.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent ev) {
                if (indexOf(e.getId()) >= 0) selectSingle(e); else showDetail(e);
            }
        });
        return pill;
    }

    private PasswordEntry fullEntryById(String id) {
        for (PasswordEntry e : vaultService.search("")) if (e.getId().equals(id)) return e;
        return null;
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
        VScrollPanel col = new VScrollPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        com.passwordmanager.ui.components.Avatar avatar = new com.passwordmanager.ui.components.Avatar(56);
        avatar.set(e.getTitle(), e.getCategory(), faviconImage(e));
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(avatar);
        col.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));

        JLabel title = new JLabel(e.getTitle());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, title.getPreferredSize().height + 2));
        col.add(title);
        col.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        if (!isBlank(e.getUsername())) col.add(fieldRow(lang.getString("entry.username"), e.getUsername(),
            () -> { copyText(e.getUsername()); markRecent(e.getId()); }));
        if (!isBlank(e.getEmail())) col.add(fieldRow(lang.getString("entry.email"), e.getEmail(),
            () -> { copyText(e.getEmail()); markRecent(e.getId()); }));

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
        JButton copyPw = Buttons.copyButton(lang.getString("entry.copy"), lang.getString("common.copied"),
            () -> copyPassword(e));
        copyPw.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(Box.createVerticalStrut(DesignTokens.SPACE_XS));
        col.add(copyPw);

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
        // Category as an editable dropdown (changes persist immediately)
        col.add(caption(lang.getString("entry.category")));
        JComboBox<String> catCombo = new JComboBox<>();
        catCombo.addItem("");
        for (String c : vaultService.getVault().getCategories()) catCombo.addItem(c);
        catCombo.setSelectedItem(e.getCategory() != null ? e.getCategory() : "");
        catCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        catCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        catCombo.addActionListener(ev -> {
            String chosen = (String) catCombo.getSelectedItem();
            if (chosen != null && !chosen.equals(e.getCategory() == null ? "" : e.getCategory())) {
                e.setCategory(chosen);
                vaultService.updateEntry(e);
                notifyChanged();
                refresh();
            }
        });
        col.add(catCombo);

        if (!isBlank(e.getNotes())) col.add(fieldRow(lang.getString("entry.notes"), e.getNotes(), null));
        if (!isBlank(e.getCreatedAt())) col.add(fieldRow(lang.getString("entry.created"), formatDate(e.getCreatedAt()), null));
        if (!isBlank(e.getUpdatedAt())) col.add(fieldRow(lang.getString("entry.updated"), formatDate(e.getUpdatedAt()), null));

        JScrollPane sc = new JScrollPane(col);
        sc.setBorder(null);
        sc.setOpaque(false);
        sc.getViewport().setOpaque(false);
        sc.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        detailHost.add(sc, BorderLayout.CENTER);

        // Actions pinned at the bottom (always visible), equal-width columns
        JPanel actions = new JPanel(new GridLayout(1, 3, DesignTokens.SPACE_SM, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, 0, 0, 0));
        JButton edit = com.passwordmanager.ui.components.Buttons.tonal(lang.getString("detail.modify"));
        edit.addActionListener(ev -> editSelected());
        JButton dup = new JButton(lang.getString("detail.duplicate"));
        dup.addActionListener(ev -> duplicate(e));
        JButton del = new JButton(lang.getString("detail.delete"));
        del.setForeground(DesignTokens.statusWeak());
        del.addActionListener(ev -> deleteEntry(e));
        actions.add(edit);
        actions.add(dup);
        actions.add(del);
        detailHost.add(actions, BorderLayout.SOUTH);

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
            v.add(Buttons.copyButton(lang.getString("entry.copy"), lang.getString("common.copied"), onCopy),
                BorderLayout.EAST);
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
            markRecent(dlg.getEntry().getId());
            notifyChanged();
        }
    }

    public void deleteSelected() {
        if (selectedIds.size() > 1) { bulkDelete(); return; }
        PasswordEntry sel = getSelected();
        if (sel != null) deleteEntry(sel);
    }

    private void deleteEntry(PasswordEntry e) {
        int confirm = JOptionPane.showConfirmDialog(this, lang.getString("vault.delete_confirm"),
            lang.getString("vault.delete_entry"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            vaultService.bulkDelete(List.of(e.getId()));
            selectedIds.remove(e.getId());
            refresh();
            notifyChanged();
        }
    }

    // ---- Bulk (multi-selection) ----
    private JPopupMenu bulkMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem del = new JMenuItem(lang.getString("vault.bulk.delete"));
        del.addActionListener(e -> bulkDelete());
        m.add(del);
        JMenuItem cat = new JMenuItem(lang.getString("vault.bulk.changeCategory"));
        cat.addActionListener(e -> bulkChangeCategory());
        m.add(cat);
        JMenuItem fav = new JMenuItem(lang.getString("vault.bulk.toggleFavorite"));
        fav.addActionListener(e -> bulkToggleFavorite());
        m.add(fav);
        return m;
    }

    private void showBulk(int count) {
        detailHost.removeAll();
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(lang.getString("vault.bulk.selected").replace("{0}", String.valueOf(count)));
        t.setFont(t.getFont().deriveFont(Font.BOLD, 16f));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(t);
        col.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));
        col.add(bulkButton(lang.getString("vault.bulk.delete"), this::bulkDelete, true));
        col.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        col.add(bulkButton(lang.getString("vault.bulk.changeCategory"), this::bulkChangeCategory, false));
        col.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        col.add(bulkButton(lang.getString("vault.bulk.toggleFavorite"), this::bulkToggleFavorite, false));
        detailHost.add(col, BorderLayout.NORTH);
        detailHost.revalidate();
        detailHost.repaint();
    }

    private JButton bulkButton(String text, Runnable action, boolean danger) {
        JButton b = new JButton(text);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        if (danger) b.setForeground(DesignTokens.statusWeak());
        b.addActionListener(e -> action.run());
        return b;
    }

    private void bulkDelete() {
        List<String> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty()) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            lang.getString("vault.bulk.confirm.delete").replace("{0}", String.valueOf(ids.size())),
            lang.getString("vault.delete_entry"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            vaultService.bulkDelete(ids);
            selectedIds.clear();
            refresh();
            notifyChanged();
        }
    }

    private void bulkChangeCategory() {
        List<String> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty()) return;
        List<String> cats = vaultService.getVault().getCategories();
        String chosen = (String) JOptionPane.showInputDialog(this,
            lang.getString("vault.bulk.changeCategory"), lang.getString("entry.category"),
            JOptionPane.PLAIN_MESSAGE, null, cats.toArray(new String[0]), cats.isEmpty() ? null : cats.get(0));
        if (chosen != null) {
            vaultService.bulkChangeCategory(ids, chosen);
            refresh();
            notifyChanged();
        }
    }

    private void bulkToggleFavorite() {
        List<String> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty()) return;
        boolean anyNotFav = false;
        for (PasswordEntry e : displayed) {
            if (ids.contains(e.getId()) && !e.isFavorite()) { anyNotFav = true; break; }
        }
        vaultService.bulkSetFavorite(ids, anyNotFav);
        refresh();
        notifyChanged();
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
        if (selectedIds.size() != 1) return null;
        return entryById(selectedIds.iterator().next());
    }

    private PasswordEntry entryById(String id) {
        if (id == null) return null;
        for (PasswordEntry e : displayed) if (id.equals(e.getId())) return e;
        return null;
    }

    private void copyPassword(PasswordEntry e) {
        char[] p = e.getPassword();
        if (p == null) return;
        SecureClipboard.copyPassword(p);
        SecureWiper.wipe(p);
        scheduleClipboardClear();
        markRecent(e.getId());
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

    /** A vertical-scroll panel that tracks the viewport width (no horizontal scrollbar). */
    private static class VScrollPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
