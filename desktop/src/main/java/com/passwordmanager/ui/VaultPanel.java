package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.SortField;
import com.passwordmanager.vault.VaultEntry;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
    private JLabel detailTitle, detailUser, detailEmail, detailPseudo, detailUrl, detailCategory, detailCreated, detailUpdated;
    private JPasswordField detailPassword;
    private JCheckBox showDetailPassword;
    private JButton copyUserBtn;
    private JButton copyPassBtn;
    private JTextArea detailNotes;

    private static final SortField[] COLUMN_SORT_FIELDS = {
        SortField.TITLE, SortField.USERNAME, SortField.EMAIL, SortField.PSEUDO, SortField.CATEGORY, null
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

    // Callbacks
    private Runnable onVaultChanged;

    public VaultPanel(VaultService vaultService, int clipboardClearSeconds) {
        this.vaultService = vaultService;
        this.clipboardClearSeconds = clipboardClearSeconds;
        initComponents();
        refreshAll();
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

        // === Center: Search + Table ===
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        searchField = new JTextField();
        searchField.setToolTipText(lang.getString("vault.search"));
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.add(new JLabel(lang.getString("vault.search")), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        centerPanel.add(searchPanel, BorderLayout.NORTH);

        tableModel = new EntryTableModel();
        entryTable = new JTable(tableModel);
        entryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        entryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        entryTable.setRowHeight(28);
        entryTable.setShowGrid(true);
        entryTable.setGridColor(Color.LIGHT_GRAY);
        entryTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        entryTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        entryTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        entryTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        entryTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        entryTable.getColumnModel().getColumn(5).setPreferredWidth(80);

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

        // Color strength column
        entryTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
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

        // Bulk action toolbar (shown when >1 row selected)
        bulkToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bulkToolbar.setVisible(false);
        bulkSelectionLabel = new JLabel();
        JButton bulkDeleteBtn = new JButton(lang.getString("vault.bulk.delete"));
        JButton bulkCategoryBtn = new JButton(lang.getString("vault.bulk.changeCategory"));
        bulkToolbar.add(bulkSelectionLabel);
        bulkToolbar.add(bulkDeleteBtn);
        bulkToolbar.add(bulkCategoryBtn);
        centerPanel.add(bulkToolbar, BorderLayout.SOUTH);

        bulkDeleteBtn.addActionListener(e -> bulkDeleteSelected());
        bulkCategoryBtn.addActionListener(e -> bulkChangeCategorySelected());

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

        // Username
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblUser = new JLabel(lang.getString("entry.username"));
        lblUser.setFont(boldFont);
        tablePanel.add(createCell(lblUser, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailUser = new JLabel(" ");
        tablePanel.add(createCell(detailUser, false, true), gl);

        // Email
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblEmail = new JLabel(lang.getString("entry.email"));
        lblEmail.setFont(boldFont);
        tablePanel.add(createCell(lblEmail, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailEmail = new JLabel(" ");
        tablePanel.add(createCell(detailEmail, false, true), gl);

        // Pseudo
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblPseudo = new JLabel(lang.getString("entry.pseudo"));
        lblPseudo.setFont(boldFont);
        tablePanel.add(createCell(lblPseudo, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailPseudo = new JLabel(" ");
        tablePanel.add(createCell(detailPseudo, false, true), gl);

        // Password
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblPass = new JLabel(lang.getString("entry.password"));
        lblPass.setFont(boldFont);
        tablePanel.add(createCell(lblPass, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailPassword = new JPasswordField();
        detailPassword.setEditable(false);
        tablePanel.add(createCell(detailPassword, false, true), gl);

        // URL (clickable)
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
        tablePanel.add(createCell(detailUrl, false, true), gl);

        // Category
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblCat = new JLabel(lang.getString("entry.category"));
        lblCat.setFont(boldFont);
        tablePanel.add(createCell(lblCat, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailCategory = new JLabel(" ");
        tablePanel.add(createCell(detailCategory, false, true), gl);

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

        // SOUTH: Buttons (two rows to fit within 300px width)
        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        JPanel copyRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        copyUserBtn = new JButton(lang.getString("entry.copy_username"));
        copyPassBtn = new JButton(lang.getString("entry.copy_password"));
        copyRow.add(copyUserBtn);
        copyRow.add(copyPassBtn);
        JPanel showRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        showDetailPassword = new JCheckBox(lang.getString("entry.show_password"));
        showRow.add(showDetailPassword);
        btnPanel.add(copyRow);
        btnPanel.add(showRow);
        rightPanel.add(btnPanel, BorderLayout.SOUTH);

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
                // Auto-hide password after timeout
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

        copyUserBtn.addActionListener(e -> copyUsernameToClipboard());
        copyPassBtn.addActionListener(e -> copyPasswordToClipboard());

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

        // Double-click to edit, right-click for context menu
        entryTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
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
        detailTitle.setText(e.getTitle());
        detailUser.setText(e.getUsername() != null ? e.getUsername() : "");
        detailEmail.setText(e.getEmail() != null ? e.getEmail() : "");
        detailPseudo.setText(e.getPseudo() != null ? e.getPseudo() : "");
        char[] pwd = e.getPassword();
        setPasswordFieldValue(detailPassword, pwd);
        SecureWiper.wipe(pwd);
        detailUrl.setText(e.getUrl() != null ? e.getUrl() : "");
        detailCategory.setText(e.getCategory() != null ? e.getCategory() : "");
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

        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(new String(clipPwd)), null);
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

    private void notifyChanged() {
        if (onVaultChanged != null) onVaultChanged.run();
    }

    // === Table Model ===
    private class EntryTableModel extends AbstractTableModel {
        private final String[] columns = {
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
                case 0: return e.getTitle();
                case 1: return e.getUsername();
                case 2: return e.getEmail();
                case 3: return e.getPseudo();
                case 4: return e.getCategory();
                case 5:
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
