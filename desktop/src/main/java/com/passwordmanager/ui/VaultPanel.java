package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.SortField;
import com.passwordmanager.vault.VaultEntry;
import com.passwordmanager.vault.EntryFilter;
import com.passwordmanager.util.FaviconService;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.VaultService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main panel displaying vault entries with search, categories, and entry details.
 */
public class VaultPanel extends JPanel {
    private final LanguageManager lang = LanguageManager.getInstance();
    private VaultService vaultService;
    private int clipboardClearSeconds;

    private JTextField searchField;
    private JList<String> categoryList;
    private DefaultListModel<String> categoryModel;
    private JTable entryTable;
    private EntryTableModel tableModel;
    private JLabel detailTitle, detailUser, detailEmail, detailPseudo, detailUrl, detailCategory, detailTags, detailCreated, detailUpdated;
    private JPasswordField detailPassword;
    private JCheckBox showDetailPassword;
    private JTextArea detailNotes;

    // Star column (0), then data columns start at 1
    private static final SortField[] COLUMN_SORT_FIELDS = {
        SortField.FAVORITE, SortField.TITLE, SortField.USERNAME, SortField.EMAIL, SortField.PSEUDO, SortField.CATEGORY, SortField.STRENGTH
    };

    private List<VaultEntry> displayedEntries = new ArrayList<>();
    private String currentCategory = null;
    private SortField currentSort = SortField.TITLE;
    private boolean sortAscending = true;
    private javax.swing.Timer clipboardTimer;
    private javax.swing.Timer passwordVisibilityTimer;
    private static final int PASSWORD_VISIBILITY_TIMEOUT_MS = 30_000;

    // Bulk action toolbar
    private JPanel bulkToolbar;
    private JLabel bulkSelectionLabel;

    // Filter panel
    private JPanel filterPanel;
    private JComboBox<String> filterCategoryCombo;
    private JComboBox<String> filterStrengthCombo;
    private JCheckBox filterFavoritesCheckbox;
    private JButton clearFiltersBtn;
    private boolean filtersVisible = false;

    // Favicon support
    private FaviconService faviconService;
    private final Map<String, ImageIcon> faviconCache = new ConcurrentHashMap<>();
    private static final ImageIcon FAVICON_LOADING = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

    // Callbacks
    private Runnable onVaultChanged;

    public VaultPanel(VaultService vaultService, int clipboardClearSeconds) {
        this.vaultService = vaultService;
        this.clipboardClearSeconds = clipboardClearSeconds;
        initComponents();
        refreshAll();
    }

    public void setFaviconService(FaviconService faviconService) {
        this.faviconService = faviconService;
    }

    public void setOnVaultChanged(Runnable onVaultChanged) {
        this.onVaultChanged = onVaultChanged;
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        // === Left: Categories ===
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(180, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0));

        categoryModel = new DefaultListModel<>();
        categoryList = new JList<>(categoryModel);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leftPanel.add(new JScrollPane(categoryList), BorderLayout.CENTER);

        JButton addCatBtn = new JButton(lang.getString("category.add"));
        leftPanel.add(addCatBtn, BorderLayout.SOUTH);

        // === Center: Search + Filters + Table ===
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Search bar with filter toggle
        searchField = new JTextField();
        searchField.setToolTipText(lang.getString("vault.search"));
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.add(new JLabel(lang.getString("vault.search")), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JButton filterToggleBtn = new JButton(lang.getString("filter.toggle"));
        filterToggleBtn.addActionListener(e -> toggleFilterPanel());
        searchPanel.add(filterToggleBtn, BorderLayout.EAST);

        // Filter panel (collapsible)
        filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterPanel.setVisible(false);

        filterCategoryCombo = new JComboBox<>();
        filterCategoryCombo.addItem(lang.getString("category.all"));
        filterCategoryCombo.addActionListener(e -> refreshEntries());

        filterStrengthCombo = new JComboBox<>();
        filterStrengthCombo.addItem(lang.getString("category.all"));
        filterStrengthCombo.addItem(lang.getString("strength.weak"));
        filterStrengthCombo.addItem(lang.getString("strength.medium"));
        filterStrengthCombo.addItem(lang.getString("strength.strong"));
        filterStrengthCombo.addItem(lang.getString("strength.very_strong"));
        filterStrengthCombo.addActionListener(e -> refreshEntries());

        filterFavoritesCheckbox = new JCheckBox(lang.getString("filter.favorites_only"));
        filterFavoritesCheckbox.addActionListener(e -> refreshEntries());

        clearFiltersBtn = new JButton(lang.getString("filter.clear"));
        clearFiltersBtn.addActionListener(e -> clearFilters());

        filterPanel.add(new JLabel(lang.getString("entry.category") + ":"));
        filterPanel.add(filterCategoryCombo);
        filterPanel.add(new JLabel(lang.getString("filter.strength") + ":"));
        filterPanel.add(filterStrengthCombo);
        filterPanel.add(filterFavoritesCheckbox);
        filterPanel.add(clearFiltersBtn);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(searchPanel, BorderLayout.NORTH);
        northPanel.add(filterPanel, BorderLayout.SOUTH);
        centerPanel.add(northPanel, BorderLayout.NORTH);

        tableModel = new EntryTableModel();
        entryTable = new JTable(tableModel);
        entryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        entryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        entryTable.setRowHeight(28);
        // Column widths: star(30), title, username, email, pseudo, category, strength
        entryTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        entryTable.getColumnModel().getColumn(0).setMaxWidth(30);
        entryTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        entryTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        entryTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        entryTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        entryTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        entryTable.getColumnModel().getColumn(6).setPreferredWidth(80);

        // Clickable header for sorting
        entryTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = entryTable.columnAtPoint(e.getPoint());
                if (col >= 0 && col < COLUMN_SORT_FIELDS.length && COLUMN_SORT_FIELDS[col] != null) {
                    setSortMode(COLUMN_SORT_FIELDS[col]);
                }
            }
        });

        // Star column renderer
        entryTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        // Title column renderer with favicon
        entryTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (c instanceof JLabel && row >= 0 && row < displayedEntries.size()) {
                    JLabel label = (JLabel) c;
                    VaultEntry entry = displayedEntries.get(row);
                    ImageIcon icon = getFaviconForEntry(entry);
                    if (icon != null && icon != FAVICON_LOADING) {
                        label.setIcon(icon);
                    } else {
                        label.setIcon(null);
                    }
                    label.setIconTextGap(4);
                }
                return c;
            }
        });

        // Color strength column (now at index 6)
        entryTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String txt = value != null ? value.toString() : "";
                if (!isSelected) {
                    if (txt.equals(lang.getString("strength.weak"))) c.setForeground(Color.RED);
                    else if (txt.equals(lang.getString("strength.medium"))) c.setForeground(Color.ORANGE);
                    else if (txt.equals(lang.getString("strength.strong"))) c.setForeground(new Color(0, 160, 0));
                    else if (txt.equals(lang.getString("strength.very_strong"))) c.setForeground(new Color(0, 100, 200));
                    else c.setForeground(table.getForeground());
                }
                return c;
            }
        });

        centerPanel.add(new JScrollPane(entryTable), BorderLayout.CENTER);

        // Bulk action toolbar — "Actions..." dropdown replacing separate buttons
        bulkToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bulkToolbar.setVisible(false);
        bulkSelectionLabel = new JLabel();
        JButton actionsBtn = new JButton(lang.getString("vault.bulk.actions") + "...");
        actionsBtn.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            JMenuItem deleteItem = new JMenuItem(lang.getString("vault.bulk.delete"));
            deleteItem.addActionListener(ev -> bulkDeleteSelected());
            popup.add(deleteItem);
            JMenuItem categoryItem = new JMenuItem(lang.getString("vault.bulk.changeCategory"));
            categoryItem.addActionListener(ev -> bulkChangeCategorySelected());
            popup.add(categoryItem);
            JMenuItem toggleFavItem = new JMenuItem(lang.getString("vault.bulk.toggleFavorite"));
            toggleFavItem.addActionListener(ev -> bulkToggleFavoriteSelected());
            popup.add(toggleFavItem);
            popup.show(actionsBtn, 0, actionsBtn.getHeight());
        });
        bulkToolbar.add(bulkSelectionLabel);
        bulkToolbar.add(actionsBtn);
        centerPanel.add(bulkToolbar, BorderLayout.SOUTH);


        // === Right: Detail panel ===
        JPanel rightPanel = new JPanel(new BorderLayout(0, 5));
        rightPanel.setPreferredSize(new Dimension(300, 0));
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(lang.getString("vault.details")),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        // NORTH: Title
        detailTitle = new JLabel(" ", SwingConstants.CENTER);
        detailTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        rightPanel.add(detailTitle, BorderLayout.NORTH);

        // CENTER: Table layout with GridBag
        JPanel tablePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gl = new GridBagConstraints();
        gl.anchor = GridBagConstraints.NORTHWEST;
        gl.fill = GridBagConstraints.BOTH;

        Font boldFont = new Font("SansSerif", Font.BOLD, 12);
        int row = 0;

        // Username with inline copy
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblUser = new JLabel(lang.getString("entry.username"));
        lblUser.setFont(boldFont);
        tablePanel.add(createCell(lblUser, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailUser = new JLabel(" ");
        tablePanel.add(createCell(createDetailValuePanel(detailUser, this::copyUsernameToClipboard), false, true), gl);

        // Email with inline copy
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblEmail = new JLabel(lang.getString("entry.email"));
        lblEmail.setFont(boldFont);
        tablePanel.add(createCell(lblEmail, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailEmail = new JLabel(" ");
        tablePanel.add(createCell(createDetailValuePanel(detailEmail, this::copyEmailToClipboard), false, true), gl);

        // Pseudo with inline copy
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblPseudo = new JLabel(lang.getString("entry.pseudo"));
        lblPseudo.setFont(boldFont);
        tablePanel.add(createCell(lblPseudo, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailPseudo = new JLabel(" ");
        tablePanel.add(createCell(createDetailValuePanel(detailPseudo, this::copyPseudoToClipboard), false, true), gl);

        // Password with inline copy + show/hide toggle
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblPass = new JLabel(lang.getString("entry.password"));
        lblPass.setFont(boldFont);
        tablePanel.add(createCell(lblPass, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailPassword = new JPasswordField();
        detailPassword.setEditable(false);
        showDetailPassword = new JCheckBox();
        showDetailPassword.setToolTipText(lang.getString("entry.show_password"));
        JPanel pwdPanel = new JPanel(new BorderLayout());
        pwdPanel.add(detailPassword, BorderLayout.CENTER);
        JPanel pwdBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pwdBtns.add(showDetailPassword);
        JButton copyPwdSmall = new JButton("\u2398");
        copyPwdSmall.setMargin(new Insets(1, 4, 1, 4));
        copyPwdSmall.setToolTipText(lang.getString("entry.copy_password"));
        copyPwdSmall.addActionListener(e -> copyPasswordToClipboard());
        pwdBtns.add(copyPwdSmall);
        pwdPanel.add(pwdBtns, BorderLayout.EAST);
        tablePanel.add(createCell(pwdPanel, false, true), gl);

        // URL with inline copy (clickable)
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblUrl = new JLabel(lang.getString("entry.url"));
        lblUrl.setFont(boldFont);
        tablePanel.add(createCell(lblUrl, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailUrl = new JLabel(" ");
        detailUrl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        detailUrl.setForeground(new Color(0, 102, 204));
        detailUrl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String url = detailUrl.getText();
                if (url != null && !url.isBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    try {
                        Desktop.getDesktop().browse(new java.net.URI(url));
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            }
        });
        tablePanel.add(createCell(createDetailValuePanel(detailUrl, this::copyUrlToClipboard), false, true), gl);

        // Category
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblCat = new JLabel(lang.getString("entry.category"));
        lblCat.setFont(boldFont);
        tablePanel.add(createCell(lblCat, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailCategory = new JLabel(" ");
        tablePanel.add(createCell(detailCategory, false, true), gl);

        // Tags
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblTags = new JLabel(lang.getString("entry.tags"));
        lblTags.setFont(boldFont);
        tablePanel.add(createCell(lblTags, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailTags = new JLabel(" ");
        tablePanel.add(createCell(detailTags, false, true), gl);

        // Notes (takes remaining vertical space)
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0; gl.weighty = 0;
        JLabel lblNotes = new JLabel(lang.getString("entry.notes"));
        lblNotes.setFont(boldFont);
        tablePanel.add(createCell(lblNotes, true, true), gl);
        gl.gridx = 1; gl.weightx = 1; gl.weighty = 1;
        detailNotes = new JTextArea(3, 20);
        detailNotes.setEditable(false);
        detailNotes.setLineWrap(true);
        tablePanel.add(createCell(new JScrollPane(detailNotes), false, true), gl);
        gl.weighty = 0;

        // Created
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblCreated = new JLabel(lang.getString("entry.created"));
        lblCreated.setFont(boldFont);
        tablePanel.add(createCell(lblCreated, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailCreated = new JLabel(" ");
        tablePanel.add(createCell(detailCreated, false, true), gl);

        // Updated (last row — no bottom border)
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblUpdated = new JLabel(lang.getString("entry.updated"));
        lblUpdated.setFont(boldFont);
        tablePanel.add(createCell(lblUpdated, true, false), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailUpdated = new JLabel(" ");
        tablePanel.add(createCell(detailUpdated, false, false), gl);

        tablePanel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));
        rightPanel.add(new JScrollPane(tablePanel), BorderLayout.CENTER);

        // === Assemble with resizable JSplitPanes ===
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerPanel, rightPanel);
        rightSplit.setResizeWeight(0.7);
        rightSplit.setDividerLocation(550);
        rightSplit.setContinuousLayout(true);


        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setDividerLocation(180);
        mainSplit.setContinuousLayout(true);

        add(mainSplit, BorderLayout.CENTER);

        // === Listeners ===
        categoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = categoryList.getSelectedValue();
                currentCategory = (sel != null && sel.equals(lang.getString("category.all"))) ? null : sel;
                refreshEntries();
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshEntries(); }
            public void removeUpdate(DocumentEvent e) { refreshEntries(); }
            public void changedUpdate(DocumentEvent e) { refreshEntries(); }
        });

        entryTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int count = entryTable.getSelectedRowCount();
                if (count > 1) {
                    bulkToolbar.setVisible(true);
                    bulkSelectionLabel.setText(
                        lang.getString("vault.bulk.selected").replace("{0}", String.valueOf(count)));
                    clearDetails();
                } else {
                    bulkToolbar.setVisible(false);
                    showSelectedEntry();
                }
            }
        });

        final char echoChar = detailPassword.getEchoChar();
        showDetailPassword.addActionListener(e -> {
            if (showDetailPassword.isSelected()) {
                detailPassword.setEchoChar((char) 0);
                if (passwordVisibilityTimer != null) passwordVisibilityTimer.stop();
                passwordVisibilityTimer = new javax.swing.Timer(PASSWORD_VISIBILITY_TIMEOUT_MS, evt -> {
                    detailPassword.setEchoChar(echoChar);
                    showDetailPassword.setSelected(false);
                });
                passwordVisibilityTimer.setRepeats(false);
                passwordVisibilityTimer.start();
            } else {
                detailPassword.setEchoChar(echoChar);
                if (passwordVisibilityTimer != null) {
                    passwordVisibilityTimer.stop();
                    passwordVisibilityTimer = null;
                }
            }
        });

        addCatBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(VaultPanel.this,
                lang.getString("category.new"), lang.getString("category.add"),
                JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                vaultService.addCategory(name.trim());
                refreshCategories();
                notifyChanged();
            }
        });

        // Click on star column to toggle favorite; double-click to edit; right-click for context menu
        entryTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = entryTable.columnAtPoint(e.getPoint());
                int row = entryTable.rowAtPoint(e.getPoint());
                if (col == 0 && row >= 0 && row < displayedEntries.size() && SwingUtilities.isLeftMouseButton(e)) {
                    // Toggle favorite
                    vaultService.toggleFavorite(displayedEntries.get(row).getId());
                    refreshEntries();
                    notifyChanged();
                } else if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    editSelectedEntry();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) { showContextMenuIfPopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { showContextMenuIfPopup(e); }

            private void showContextMenuIfPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = entryTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && !entryTable.isRowSelected(row)) {
                        entryTable.setRowSelectionInterval(row, row);
                    }
                    createContextMenu().show(entryTable, e.getX(), e.getY());
                }
            }
        });
    }

    private void toggleFilterPanel() {
        filtersVisible = !filtersVisible;
        filterPanel.setVisible(filtersVisible);
        if (filtersVisible) {
            refreshFilterCategoryCombo();
        }
        revalidate();
    }

    private void refreshFilterCategoryCombo() {
        filterCategoryCombo.removeAllItems();
        filterCategoryCombo.addItem(lang.getString("category.all"));
        for (String cat : vaultService.getVault().getCategories()) {
            filterCategoryCombo.addItem(cat);
        }
    }

    private void clearFilters() {
        filterCategoryCombo.setSelectedIndex(0);
        filterStrengthCombo.setSelectedIndex(0);
        filterFavoritesCheckbox.setSelected(false);
        refreshEntries();
    }

    public void refreshAll() {
        refreshCategories();
        refreshEntries();
    }

    private void refreshCategories() {
        categoryModel.clear();
        categoryModel.addElement(lang.getString("category.all"));
        for (String cat : vaultService.getVault().getCategories()) {
            categoryModel.addElement(cat);
        }
    }

    public void refreshEntries() {
        String query = searchField.getText().trim();
        List<VaultEntry> entries;

        if (!query.isEmpty()) {
            entries = vaultService.search(query);
            if (currentCategory != null) {
                List<VaultEntry> filtered = new ArrayList<>();
                for (VaultEntry e : entries) {
                    if (currentCategory.equals(e.getCategory())) {
                        filtered.add(e);
                    }
                }
                entries = filtered;
            }
        } else if (currentCategory != null) {
            entries = vaultService.getByCategory(currentCategory);
        } else {
            entries = vaultService.search("");
        }

        // Apply advanced filters
        {
            EntryFilter.Builder fb = new EntryFilter.Builder();
            // Category and strength filters only when filter panel is visible
            if (filtersVisible) {
                String filterCat = (String) filterCategoryCombo.getSelectedItem();
                if (filterCat != null && !filterCat.equals(lang.getString("category.all"))) {
                    fb.category(filterCat);
                }
                String filterStr = (String) filterStrengthCombo.getSelectedItem();
                if (filterStr != null && !filterStr.equals(lang.getString("category.all"))) {
                    PasswordStrengthAnalyzer.Strength minStr = null;
                    if (filterStr.equals(lang.getString("strength.weak"))) minStr = PasswordStrengthAnalyzer.Strength.WEAK;
                    else if (filterStr.equals(lang.getString("strength.medium"))) minStr = PasswordStrengthAnalyzer.Strength.MEDIUM;
                    else if (filterStr.equals(lang.getString("strength.strong"))) minStr = PasswordStrengthAnalyzer.Strength.STRONG;
                    else if (filterStr.equals(lang.getString("strength.very_strong"))) minStr = PasswordStrengthAnalyzer.Strength.VERY_STRONG;
                    if (minStr != null) fb.exactStrength(minStr);
                }
            }
            // Favorites filter always applies (even when filter panel collapsed)
            if (filterFavoritesCheckbox.isSelected()) {
                fb.favoritesOnly(true);
            }
            EntryFilter filter = fb.build();
            if (filter.hasActiveFilters()) {
                entries = vaultService.filter(entries, filter);
            }
        }

        displayedEntries = vaultService.sorted(entries, currentSort);
        if (!sortAscending) {
            java.util.Collections.reverse(displayedEntries);
        }
        tableModel.fireTableDataChanged();
    }

    private void showSelectedEntry() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) {
            clearDetails();
            return;
        }
        VaultEntry e = displayedEntries.get(row);
        detailTitle.setText((e.isFavorite() ? "\u2605 " : "") + e.getTitle());
        detailUser.setText(e.getUsername() != null ? e.getUsername() : "");
        detailEmail.setText(e.getEmail() != null ? e.getEmail() : "");
        detailPseudo.setText(e.getPseudo() != null ? e.getPseudo() : "");
        char[] pwd = e.getPassword();
        setPasswordFieldValue(detailPassword, pwd);
        SecureWiper.wipe(pwd);
        detailUrl.setText(e.getUrl() != null ? e.getUrl() : "");
        detailCategory.setText(e.getCategory() != null ? e.getCategory() : "");
        detailTags.setText(e.getTags() != null && !e.getTags().isEmpty() ? String.join(", ", e.getTags()) : "");
        detailNotes.setText(e.getNotes() != null ? e.getNotes() : "");
        detailCreated.setText(e.getCreatedAt() != null ? e.getCreatedAt() : "");
        detailUpdated.setText(e.getUpdatedAt() != null ? e.getUpdatedAt() : "");
    }

    private void clearDetails() {
        detailTitle.setText(" ");
        detailUser.setText(" ");
        detailEmail.setText(" ");
        detailPseudo.setText(" ");
        detailPassword.setText("");
        detailUrl.setText(" ");
        detailCategory.setText(" ");
        detailTags.setText(" ");
        detailNotes.setText("");
        detailCreated.setText(" ");
        detailUpdated.setText(" ");
    }

    private List<String> getSelectedEntryIds() {
        List<String> ids = new ArrayList<>();
        for (int row : entryTable.getSelectedRows()) {
            if (row >= 0 && row < displayedEntries.size()) {
                ids.add(displayedEntries.get(row).getId());
            }
        }
        return ids;
    }

    private void bulkDeleteSelected() {
        List<String> ids = getSelectedEntryIds();
        if (ids.isEmpty()) return;
        String msg = lang.getString("vault.bulk.confirm.delete").replace("{0}", String.valueOf(ids.size()));
        int confirm = JOptionPane.showConfirmDialog(this, msg,
            lang.getString("vault.delete_entry"),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            vaultService.bulkDelete(ids);
            refreshEntries();
            clearDetails();
            notifyChanged();
        }
    }

    private void bulkChangeCategorySelected() {
        List<String> ids = getSelectedEntryIds();
        if (ids.isEmpty()) return;
        List<String> categories = vaultService.getVault().getCategories();
        String chosen = (String) JOptionPane.showInputDialog(this,
            lang.getString("vault.bulk.changeCategory"),
            lang.getString("entry.category"),
            JOptionPane.PLAIN_MESSAGE, null,
            categories.toArray(new String[0]),
            categories.isEmpty() ? null : categories.get(0));
        if (chosen != null) {
            vaultService.bulkChangeCategory(ids, chosen);
            refreshEntries();
            notifyChanged();
        }
    }

    private void bulkToggleFavoriteSelected() {
        List<String> ids = getSelectedEntryIds();
        if (ids.isEmpty()) return;
        // Check if any selected entry is not a favorite
        boolean anyNotFav = false;
        for (VaultEntry e : displayedEntries) {
            if (ids.contains(e.getId()) && !e.isFavorite()) {
                anyNotFav = true;
                break;
            }
        }
        // If any is not favorite, set all to favorite; otherwise unfavorite all
        vaultService.bulkSetFavorite(ids, anyNotFav);
        refreshEntries();
        notifyChanged();
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        boolean singleSelected = entryTable.getSelectedRowCount() == 1;

        JMenuItem editItem = new JMenuItem(lang.getString("vault.edit_entry"));
        editItem.setEnabled(singleSelected);
        editItem.addActionListener(e -> editSelectedEntry());
        menu.add(editItem);

        JMenuItem deleteItem = new JMenuItem(lang.getString("vault.delete_entry"));
        deleteItem.addActionListener(e -> deleteSelectedEntry());
        menu.add(deleteItem);

        menu.addSeparator();

        JMenuItem toggleFav = new JMenuItem(lang.getString("entry.toggle_favorite"));
        toggleFav.setEnabled(singleSelected);
        toggleFav.addActionListener(e -> {
            VaultEntry se = getSelectedEntry();
            if (se != null) {
                vaultService.toggleFavorite(se.getId());
                refreshEntries();
                notifyChanged();
            }
        });
        menu.add(toggleFav);

        menu.addSeparator();

        JMenuItem copyPwd = new JMenuItem(lang.getString("entry.copy_password"));
        copyPwd.setEnabled(singleSelected);
        copyPwd.addActionListener(e -> copyPasswordToClipboard());
        menu.add(copyPwd);

        JMenuItem copyUser = new JMenuItem(lang.getString("entry.copy_username"));
        copyUser.setEnabled(singleSelected);
        copyUser.addActionListener(e -> copyUsernameToClipboard());
        menu.add(copyUser);

        JMenuItem copyEmail = new JMenuItem(lang.getString("entry.copy_email"));
        copyEmail.setEnabled(singleSelected);
        copyEmail.addActionListener(e -> copyEmailToClipboard());
        menu.add(copyEmail);

        JMenuItem copyPseudo = new JMenuItem(lang.getString("entry.copy_pseudo"));
        copyPseudo.setEnabled(singleSelected);
        copyPseudo.addActionListener(e -> copyPseudoToClipboard());
        menu.add(copyPseudo);

        JMenuItem copyUrl = new JMenuItem(lang.getString("menu.copy.url"));
        copyUrl.setEnabled(singleSelected);
        copyUrl.addActionListener(e -> copyUrlToClipboard());
        menu.add(copyUrl);

        menu.addSeparator();

        JMenuItem openUrl = new JMenuItem(lang.getString("menu.open.url"));
        openUrl.setEnabled(singleSelected);
        openUrl.addActionListener(e -> openUrlInBrowser());
        menu.add(openUrl);

        JMenuItem duplicate = new JMenuItem(lang.getString("menu.duplicate"));
        duplicate.setEnabled(singleSelected);
        duplicate.addActionListener(e -> duplicateEntry());
        menu.add(duplicate);

        return menu;
    }

    private void copyEmailToClipboard() {
        VaultEntry e = getSelectedEntry();
        if (e == null || e.getEmail() == null || e.getEmail().isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(e.getEmail()), null);
        scheduleClipboardClear();
    }

    private void copyPseudoToClipboard() {
        VaultEntry e = getSelectedEntry();
        if (e == null || e.getPseudo() == null || e.getPseudo().isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(e.getPseudo()), null);
        scheduleClipboardClear();
    }

    private void copyUrlToClipboard() {
        VaultEntry e = getSelectedEntry();
        if (e == null || e.getUrl() == null || e.getUrl().isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(e.getUrl()), null);
        scheduleClipboardClear();
    }

    private void openUrlInBrowser() {
        VaultEntry e = getSelectedEntry();
        if (e == null || e.getUrl() == null || e.getUrl().isBlank()) return;
        String url = e.getUrl();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            // ignore
        }
    }

    private void duplicateEntry() {
        VaultEntry selected = getSelectedEntry();
        if (selected == null) return;
        char[] pwdCopy = selected.getPassword();
        try {
            VaultEntry dup = new VaultEntry(
                lang.getString("menu.duplicate.prefix") + " " + selected.getTitle(),
                selected.getUsername(),
                selected.getEmail(),
                selected.getPseudo(),
                pwdCopy,
                selected.getUrl(),
                selected.getNotes(),
                selected.getCategory(),
                selected.getTags() != null ? new ArrayList<>(selected.getTags()) : null
            );
            vaultService.addEntry(dup);
            refreshEntries();
            notifyChanged();
        } finally {
            SecureWiper.wipe(pwdCopy);
        }
    }

    private void copyUsernameToClipboard() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return;
        VaultEntry e = displayedEntries.get(row);
        String username = e.getUsername();
        if (username == null || username.isEmpty()) return;

        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(username), null);

        scheduleClipboardClear();
    }

    private void copyPasswordToClipboard() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return;
        VaultEntry e = displayedEntries.get(row);
        char[] clipPwd = e.getPassword();
        if (clipPwd == null) return;

        SecureClipboard.copyPassword(clipPwd);
        SecureWiper.wipe(clipPwd);

        scheduleClipboardClear();
    }

    private void scheduleClipboardClear() {
        if (clipboardTimer != null) {
            clipboardTimer.stop();
        }
        clipboardTimer = new javax.swing.Timer(clipboardClearSeconds * 1000, evt ->
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(""), null));
        clipboardTimer.setRepeats(false);
        clipboardTimer.start();
    }

    /**
     * Cancels the clipboard clear timer and password visibility timer if running.
     */
    public void cancelClipboardTimer() {
        if (clipboardTimer != null) {
            clipboardTimer.stop();
            clipboardTimer = null;
        }
        if (passwordVisibilityTimer != null) {
            passwordVisibilityTimer.stop();
            passwordVisibilityTimer = null;
        }
    }

    public VaultEntry getSelectedEntry() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return null;
        return displayedEntries.get(row);
    }

    public void editSelectedEntry() {
        if (entryTable.getSelectedRowCount() != 1) return;
        VaultEntry selected = getSelectedEntry();
        if (selected == null) return;
        EntryDialog dlg = new EntryDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.edit_entry"),
            selected,
            vaultService.getVault().getCategories());
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            vaultService.updateEntry(dlg.getEntry());
            refreshEntries();
            notifyChanged();
        }
    }

    public void addNewEntry() {
        EntryDialog dlg = new EntryDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.new_entry"),
            null,
            vaultService.getVault().getCategories());
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            vaultService.addEntry(dlg.getEntry());
            refreshEntries();
            notifyChanged();
        }
    }

    public void deleteSelectedEntry() {
        List<String> ids = getSelectedEntryIds();
        if (ids.isEmpty()) return;
        String msg = ids.size() == 1
            ? lang.getString("vault.delete_confirm")
            : lang.getString("vault.bulk.confirm.delete").replace("{0}", String.valueOf(ids.size()));
        int confirm = JOptionPane.showConfirmDialog(this, msg,
            lang.getString("vault.delete_entry"),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            vaultService.bulkDelete(ids);
            refreshEntries();
            clearDetails();
            notifyChanged();
        }
    }

    public void setClipboardClearSeconds(int seconds) {
        this.clipboardClearSeconds = seconds;
    }

    public void setSortMode(SortField sort) {
        if (this.currentSort == sort) {
            sortAscending = !sortAscending;
        } else {
            this.currentSort = sort;
            sortAscending = true;
        }
        refreshEntries();
    }

    private ImageIcon getFaviconForEntry(VaultEntry entry) {
        if (faviconService == null) return null;
        String url = entry.getUrl();
        if (url == null || url.isBlank()) return null;
        String domain = FaviconService.extractDomain(url);
        if (domain == null || domain.isEmpty()) return null;

        ImageIcon cached = faviconCache.get(domain);
        if (cached != null) return cached;

        // Mark as loading to avoid duplicate fetches
        faviconCache.put(domain, FAVICON_LOADING);

        // Async fetch via SwingWorker
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() {
                try {
                    byte[] data = faviconService.getFavicon(url);
                    if (data != null && data.length > 0) {
                        ImageIcon raw = new ImageIcon(data);
                        Image scaled = raw.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                        return new ImageIcon(scaled);
                    }
                } catch (Exception ignored) {}
                return null;
            }
            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        faviconCache.put(domain, icon);
                        tableModel.fireTableDataChanged();
                    } else {
                        faviconCache.remove(domain);
                    }
                } catch (Exception ignored) {
                    faviconCache.remove(domain);
                }
            }
        }.execute();
        return null;
    }

    private void notifyChanged() {
        if (onVaultChanged != null) onVaultChanged.run();
    }

    // === Table Model ===
    private class EntryTableModel extends AbstractTableModel {
        private final String[] columns = {
            "\u2605",  // star column
            lang.getString("entry.title"),
            lang.getString("entry.username"),
            lang.getString("entry.email"),
            lang.getString("entry.pseudo"),
            lang.getString("entry.category"),
            lang.getString("strength.label")
        };

        @Override public int getRowCount() { return displayedEntries.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            VaultEntry e = displayedEntries.get(row);
            switch (col) {
                case 0: return e.isFavorite() ? "\u2605" : "\u2606";
                case 1: return e.getTitle();
                case 2: return e.getUsername();
                case 3: return e.getEmail();
                case 4: return e.getPseudo();
                case 5: return e.getCategory();
                case 6:
                    char[] tablePwd = e.getPassword();
                    try {
                        PasswordStrengthAnalyzer.Strength st = PasswordStrengthAnalyzer.analyze(tablePwd);
                        switch (st) {
                            case WEAK: return lang.getString("strength.weak");
                            case MEDIUM: return lang.getString("strength.medium");
                            case STRONG: return lang.getString("strength.strong");
                            case VERY_STRONG: return lang.getString("strength.very_strong");
                        }
                        return "";
                    } finally {
                        SecureWiper.wipe(tablePwd);
                    }
                default: return "";
            }
        }

        @Override public boolean isCellEditable(int row, int col) { return false; }
    }

    /**
     * Wraps a component in a cell panel with border separators.
     */
    private static JPanel createCell(Component comp, boolean rightBorder, boolean bottomBorder) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.add(comp, BorderLayout.CENTER);
        cell.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, bottomBorder ? 1 : 0, rightBorder ? 1 : 0, Color.GRAY),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        return cell;
    }

    /**
     * Creates a detail value panel with the value component on the left and
     * a small copy button on the right.
     */
    private static JPanel createDetailValuePanel(JComponent value, Runnable onCopy) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(value, BorderLayout.CENTER);
        JButton copyBtn = new JButton("\u2398");
        copyBtn.setMargin(new Insets(1, 4, 1, 4));
        copyBtn.setToolTipText("Copy");
        copyBtn.addActionListener(e -> onCopy.run());
        panel.add(copyBtn, BorderLayout.EAST);
        return panel;
    }

    /**
     * Sets a JPasswordField value from char[] without creating an intermediate String.
     * Uses the Document model directly to minimize String interning.
     */
    private static void setPasswordFieldValue(JPasswordField field, char[] value) {
        Document doc = field.getDocument();
        try {
            doc.remove(0, doc.getLength());
            if (value != null) {
                doc.insertString(0, new String(value), null);
            }
        } catch (BadLocationException ignored) {
            // Should not happen with valid offsets
        }
    }
}
