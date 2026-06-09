package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.ui.components.Avatar;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.components.ControlIcon;
import com.passwordmanager.ui.components.EntryCardPanel;
import com.passwordmanager.ui.components.RoundedPanel;
import com.passwordmanager.ui.components.SecretFieldPanel;
import com.passwordmanager.ui.theme.DesignTokens;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.AppEntry;
import com.passwordmanager.vault.AppService;
import com.passwordmanager.vault.SortField;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * "Calme & confiance" applications (PIN) view: calm card list + detail, mirroring
 * {@link CoffrePasswordsPanel} (no bento/category/strength). Reuses {@link AppService} +
 * {@link AppEntryDialog}. Single + multi-selection with bulk actions.
 */
public class CoffreAppsPanel extends JPanel {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final AppService appService;
    private int clipboardClearSeconds;
    private final Runnable onVaultChanged;

    private final JTextField searchField = new JTextField();
    private final JPanel cardsHost = new JPanel();
    private final JPanel detailHost = new JPanel(new BorderLayout());

    private List<AppEntry> displayed = new ArrayList<>();
    private final java.util.LinkedHashSet<String> selectedIds = new java.util.LinkedHashSet<>();
    private String anchorId;
    private SortField currentSort = SortField.TITLE;
    private boolean sortAscending = true;
    private boolean favoritesOnly = false;
    private java.time.LocalDate createdSince, modifiedSince, createdOn, modifiedOn;
    private JPanel filterPanel;
    private final java.util.List<Runnable> filterResetters = new ArrayList<>();
    private javax.swing.Timer clipboardTimer;

    public CoffreAppsPanel(AppService appService, int clipboardClearSeconds, Runnable onVaultChanged) {
        this.appService = appService;
        this.clipboardClearSeconds = clipboardClearSeconds;
        this.onVaultChanged = onVaultChanged;
        initComponents();
        refresh();
    }

    public void setClipboardClearSeconds(int s) { this.clipboardClearSeconds = s; }
    public JTextField getSearchField() { return searchField; }
    public void cancelClipboardTimer() { if (clipboardTimer != null) { clipboardTimer.stop(); clipboardTimer = null; } }

    private void initComponents() {
        setLayout(new BorderLayout());
        cardsHost.setLayout(new BoxLayout(cardsHost, BoxLayout.Y_AXIS));
        cardsHost.setOpaque(false);
        cardsHost.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, DesignTokens.SPACE_MD,
            DesignTokens.SPACE_MD, DesignTokens.SPACE_MD));
        JScrollPane scroll = new JScrollPane(cardsHost);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(700, 470));

        detailHost.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL)));
        detailHost.setPreferredSize(new Dimension(360, 0));
        clearDetail();

        // Top controls: search (grows) + sort/filter icon controls + new-entry button.
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

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(row1);
        top.add(filterPanel);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(top, BorderLayout.NORTH);
        centerPanel.add(scroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerPanel, detailHost);
        split.setResizeWeight(1.0);
        split.setBorder(null);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
    }

    public void refresh() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        List<AppEntry> entries = new ArrayList<>(query.isEmpty() ? appService.getActiveList() : appService.search(query));
        if (favoritesOnly) entries.removeIf(e -> !e.isFavorite());
        if (createdSince != null || modifiedSince != null || createdOn != null || modifiedOn != null) {
            entries.removeIf(e -> {
                java.time.LocalDate c = dateOf(e.getCreatedAt()), m = dateOf(e.getUpdatedAt());
                if (createdSince != null && (c == null || c.isBefore(createdSince))) return true;
                if (modifiedSince != null && (m == null || m.isBefore(modifiedSince))) return true;
                if (createdOn != null && (c == null || !c.isEqual(createdOn))) return true;
                if (modifiedOn != null && (m == null || !m.isEqual(modifiedOn))) return true;
                return false;
            });
        }
        displayed = appService.sorted(entries, currentSort);
        if (!sortAscending) java.util.Collections.reverse(displayed);

        selectedIds.removeIf(id -> entryById(id) == null);
        if (selectedIds.isEmpty() && !displayed.isEmpty()) {
            selectedIds.add(displayed.get(0).getId());
            anchorId = displayed.get(0).getId();
        }
        rebuildList();
        updateDetailOrBulk();
    }

    private void rebuildList() {
        cardsHost.removeAll();
        for (AppEntry e : displayed) {
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

    private EntryCardPanel buildCard(AppEntry e) {
        // No strength/category for app entries; "PIN" shown as the discreet tag.
        EntryCardPanel card = new EntryCardPanel(e.getTitle(),
            e.getUsername() != null ? e.getUsername() : "", "PIN", null, null, null, e.isFavorite());
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
        return card;
    }

    private void maybePopup(MouseEvent ev, AppEntry e) {
        if (ev.isPopupTrigger()) {
            Point p = SwingUtilities.convertPoint(ev.getComponent(), ev.getPoint(), this);
            if (!selectedIds.contains(e.getId())) selectSingle(e);
            JPopupMenu menu = selectedIds.size() > 1 ? bulkMenu() : contextMenu(e);
            menu.show(this, p.x, p.y);
        }
    }

    private void selectSingle(AppEntry e) { selectedIds.clear(); selectedIds.add(e.getId()); anchorId = e.getId(); rebuildList(); updateDetailOrBulk(); }
    private void toggleSelect(AppEntry e) { if (!selectedIds.add(e.getId())) selectedIds.remove(e.getId()); anchorId = e.getId(); rebuildList(); updateDetailOrBulk(); }
    private void selectRange(AppEntry e) {
        int a = indexOf(anchorId), b = indexOf(e.getId());
        if (a < 0 || b < 0) { selectSingle(e); return; }
        selectedIds.clear();
        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) selectedIds.add(displayed.get(i).getId());
        rebuildList(); updateDetailOrBulk();
    }
    private int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < displayed.size(); i++) if (displayed.get(i).getId().equals(id)) return i;
        return -1;
    }
    private AppEntry entryById(String id) {
        if (id == null) return null;
        for (AppEntry e : displayed) if (id.equals(e.getId())) return e;
        return null;
    }
    private AppEntry getSelected() { return selectedIds.size() == 1 ? entryById(selectedIds.iterator().next()) : null; }

    private void updateDetailOrBulk() {
        if (selectedIds.size() > 1) showBulk(selectedIds.size());
        else { AppEntry s = getSelected(); if (s != null) showDetail(s); else clearDetail(); }
    }

    private void clearDetail() {
        detailHost.removeAll();
        JLabel empty = new JLabel(lang.getString("vault.details"), SwingConstants.CENTER);
        empty.setForeground(DesignTokens.onSurfaceFaint());
        detailHost.add(empty, BorderLayout.CENTER);
        detailHost.revalidate();
        detailHost.repaint();
    }

    private void showDetail(AppEntry e) {
        detailHost.removeAll();
        VScrollPanel col = new VScrollPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        Avatar avatar = new Avatar(56);
        avatar.set(e.getTitle(), null, null);
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

        if (e.getUsername() != null && !e.getUsername().isBlank()) {
            col.add(fieldRow(lang.getString("entry.username"), e.getUsername(), () -> copyText(e.getUsername())));
        }
        col.add(caption(lang.getString("entry.pin")));
        SecretFieldPanel secret = new SecretFieldPanel();
        char[] pin = e.getPin();
        secret.setValue(pin);
        SecureWiper.wipe(pin);
        secret.setAlignmentX(Component.LEFT_ALIGNMENT);
        secret.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        col.add(secret);
        if (e.getNotes() != null && !e.getNotes().isBlank()) {
            col.add(fieldRow(lang.getString("entry.notes"), e.getNotes(), null));
        }
        if (e.getCreatedAt() != null && !e.getCreatedAt().isBlank())
            col.add(fieldRow(lang.getString("entry.created"), formatDate(e.getCreatedAt()), null));
        if (e.getUpdatedAt() != null && !e.getUpdatedAt().isBlank())
            col.add(fieldRow(lang.getString("entry.updated"), formatDate(e.getUpdatedAt()), null));

        JScrollPane sc = new JScrollPane(col);
        sc.setBorder(null);
        sc.setOpaque(false);
        sc.getViewport().setOpaque(false);
        sc.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        detailHost.add(sc, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 3, DesignTokens.SPACE_SM, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, 0, 0, 0));
        JButton edit = Buttons.tonal(lang.getString("detail.modify"));
        edit.addActionListener(ev -> editSelected());
        JButton dup = new JButton(lang.getString("detail.duplicate"));
        dup.addActionListener(ev -> duplicate(e));
        JButton del = new JButton(lang.getString("detail.delete"));
        del.setForeground(DesignTokens.statusWeak());
        del.addActionListener(ev -> deleteEntry(e));
        actions.add(edit); actions.add(dup); actions.add(del);
        detailHost.add(actions, BorderLayout.SOUTH);

        detailHost.revalidate();
        detailHost.repaint();
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
        v.add(new JLabel(value), BorderLayout.CENTER);
        if (onCopy != null) {
            JButton copy = new JButton("Copier");
            copy.setMargin(new Insets(2, 8, 2, 8));
            copy.addActionListener(ev -> onCopy.run());
            v.add(copy, BorderLayout.EAST);
        }
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    private JPopupMenu contextMenu(AppEntry e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem(lang.getString("vault.edit_entry"));
        edit.addActionListener(ev -> editSelected());
        menu.add(edit);
        JMenuItem del = new JMenuItem(lang.getString("vault.delete_entry"));
        del.addActionListener(ev -> deleteEntry(e));
        menu.add(del);
        menu.addSeparator();
        JMenuItem fav = new JMenuItem(lang.getString("entry.toggle_favorite"));
        fav.addActionListener(ev -> { appService.toggleFavorite(e.getId()); refresh(); notifyChanged(); });
        menu.add(fav);
        JMenuItem copyPin = new JMenuItem(lang.getString("entry.copy_password"));
        copyPin.addActionListener(ev -> copyPin(e));
        menu.add(copyPin);
        JMenuItem dup = new JMenuItem(lang.getString("menu.duplicate"));
        dup.addActionListener(ev -> duplicate(e));
        menu.add(dup);
        return menu;
    }

    private JPopupMenu bulkMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem del = new JMenuItem(lang.getString("vault.bulk.delete"));
        del.addActionListener(e -> bulkDelete());
        m.add(del);
        JMenuItem fav = new JMenuItem(lang.getString("vault.bulk.toggleFavorite"));
        fav.addActionListener(e -> bulkToggleFavorite());
        m.add(fav);
        return m;
    }

    public void addNewEntry() {
        AppEntryDialog dlg = new AppEntryDialog((Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.new_entry"), null);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) { appService.addEntry(dlg.getEntry()); refresh(); notifyChanged(); }
    }

    public void editSelected() {
        AppEntry sel = getSelected();
        if (sel == null) return;
        AppEntryDialog dlg = new AppEntryDialog((Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.edit_entry"), sel);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) { appService.updateEntry(dlg.getEntry()); refresh(); updateDetailOrBulk(); notifyChanged(); }
    }

    private void deleteEntry(AppEntry e) {
        int c = JOptionPane.showConfirmDialog(this, lang.getString("vault.delete_confirm"),
            lang.getString("vault.delete_entry"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c == JOptionPane.YES_OPTION) {
            appService.bulkDelete(List.of(e.getId()));
            selectedIds.remove(e.getId());
            refresh();
            notifyChanged();
        }
    }

    private void duplicate(AppEntry selected) {
        char[] pin = selected.getPin();
        try {
            AppEntry dup = new AppEntry(lang.getString("menu.duplicate.prefix") + " " + selected.getTitle(),
                selected.getUsername(), pin, selected.getNotes());
            appService.addEntry(dup);
            refresh();
            notifyChanged();
        } finally {
            if (pin != null) SecureWiper.wipe(pin);
        }
    }

    private void bulkDelete() {
        List<String> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty()) return;
        int c = JOptionPane.showConfirmDialog(this,
            lang.getString("vault.bulk.confirm.delete").replace("{0}", String.valueOf(ids.size())),
            lang.getString("vault.delete_entry"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c == JOptionPane.YES_OPTION) { appService.bulkDelete(ids); selectedIds.clear(); refresh(); notifyChanged(); }
    }

    private void bulkToggleFavorite() {
        List<String> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty()) return;
        boolean anyNotFav = false;
        for (AppEntry e : displayed) if (ids.contains(e.getId()) && !e.isFavorite()) { anyNotFav = true; break; }
        appService.bulkSetFavorite(ids, anyNotFav);
        refresh();
        notifyChanged();
    }

    private void copyPin(AppEntry e) {
        char[] pin = e.getPin();
        if (pin == null) return;
        SecureClipboard.copyPassword(pin);
        SecureWiper.wipe(pin);
        scheduleClipboardClear();
    }

    private void copyText(String text) {
        if (text == null || text.isBlank()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        scheduleClipboardClear();
    }

    private void scheduleClipboardClear() {
        if (clipboardTimer != null) clipboardTimer.stop();
        clipboardTimer = new javax.swing.Timer(clipboardClearSeconds * 1000, evt ->
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(""), null));
        clipboardTimer.setRepeats(false);
        clipboardTimer.start();
    }

    private void notifyChanged() { if (onVaultChanged != null) onVaultChanged.run(); }

    private JToggleButton chip(String text, boolean selected) {
        JToggleButton b = new JToggleButton(text, selected);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        return b;
    }

    /** Sort menu opened from the sort icon: a direction toggle + one item per sort field. */
    private void showSortMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem dir = new JMenuItem((sortAscending ? "▲  " : "▼  ")
            + lang.getString(sortAscending ? "sort.ascending" : "sort.descending"));
        dir.addActionListener(e -> { sortAscending = !sortAscending; refresh(); });
        menu.add(dir);
        menu.addSeparator();

        String[] keys = { "entry.title", "entry.username", "sort.created", "sort.modified" };
        SortField[] fields = { SortField.TITLE, SortField.USERNAME, SortField.CREATED, SortField.DATE };
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

    private JPanel buildFilterPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_SM, DesignTokens.SPACE_MD, DesignTokens.SPACE_SM, DesignTokens.SPACE_MD)));

        // Favorites chip
        JToggleButton favChip = chip(lang.getString("filter.favorites"), false);
        favChip.addActionListener(e -> { favoritesOnly = favChip.isSelected(); refresh(); });
        filterResetters.add(() -> favChip.setSelected(false));
        JPanel favSection = new JPanel();
        favSection.setLayout(new BoxLayout(favSection, BoxLayout.Y_AXIS));
        favSection.setOpaque(false);
        favSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        favSection.add(sectionLabel(lang.getString("entry.favorite")));
        JPanel favRow = new JPanel(new FlowLayout(FlowLayout.LEFT, DesignTokens.SPACE_SM, DesignTokens.SPACE_XS));
        favRow.setOpaque(false);
        favRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        favRow.add(favChip);
        favSection.add(favRow);
        favSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, favSection.getPreferredSize().height));
        p.add(favSection);

        // Date filters
        p.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        p.add(sectionLabel(lang.getString("filter.dates")));
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
        return p;
    }

    private JLabel sectionLabel(String text) {
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

    /** A vertical-scroll panel that tracks the viewport width (no horizontal scrollbar). */
    private static class VScrollPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
