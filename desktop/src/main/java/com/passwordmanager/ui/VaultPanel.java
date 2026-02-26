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
    private javax.swing.Timer clipboardTimer;
    private javax.swing.Timer passwordVisibilityTimer;
    private static final int PASSWORD_VISIBILITY_TIMEOUT_MS = 30_000;

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

        add(leftPanel, BorderLayout.WEST);

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
        entryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entryTable.setRowHeight(28);
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
        add(centerPanel, BorderLayout.CENTER);

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

        add(rightPanel, BorderLayout.EAST);

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
            if (!e.getValueIsAdjusting()) showSelectedEntry();
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

        // Double-click to edit
        entryTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelectedEntry();
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
        VaultEntry selected = getSelectedEntry();
        if (selected == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            lang.getString("vault.delete_confirm"),
            lang.getString("vault.delete_entry"),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            vaultService.deleteEntry(selected.getId());
            refreshEntries();
            clearDetails();
            notifyChanged();
        }
    }

    public void setClipboardClearSeconds(int seconds) {
        this.clipboardClearSeconds = seconds;
    }

    public void setSortMode(SortField sort) {
        this.currentSort = sort;
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
