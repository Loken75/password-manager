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
import java.util.Timer;
import java.util.TimerTask;

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
    private JLabel detailTitle, detailUser, detailUrl, detailCategory, detailCreated, detailUpdated;
    private JPasswordField detailPassword;
    private JCheckBox showDetailPassword;
    private JTextArea detailNotes;

    private List<VaultEntry> displayedEntries = new ArrayList<>();
    private String currentCategory = null;
    private SortField currentSort = SortField.TITLE;
    private Timer clipboardTimer;
    private Timer passwordVisibilityTimer;
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
        entryTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        entryTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        entryTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        entryTable.getColumnModel().getColumn(3).setPreferredWidth(80);

        // Color strength column
        entryTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
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
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setPreferredSize(new Dimension(260, 0));
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(lang.getString("vault.details")),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        detailTitle = new JLabel(" ");
        detailTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        rightPanel.add(detailTitle);
        rightPanel.add(Box.createVerticalStrut(10));

        rightPanel.add(new JLabel(lang.getString("entry.username")));
        detailUser = new JLabel(" ");
        rightPanel.add(detailUser);
        rightPanel.add(Box.createVerticalStrut(8));

        rightPanel.add(new JLabel(lang.getString("entry.password")));
        JPanel dpPanel = new JPanel(new BorderLayout(3, 0));
        detailPassword = new JPasswordField();
        detailPassword.setEditable(false);
        showDetailPassword = new JCheckBox(lang.getString("entry.show_password"));
        dpPanel.add(detailPassword, BorderLayout.CENTER);
        dpPanel.add(showDetailPassword, BorderLayout.EAST);
        rightPanel.add(dpPanel);

        JButton copyPassBtn = new JButton(lang.getString("entry.copy"));
        copyPassBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightPanel.add(copyPassBtn);
        rightPanel.add(Box.createVerticalStrut(8));

        rightPanel.add(new JLabel(lang.getString("entry.url")));
        detailUrl = new JLabel(" ");
        rightPanel.add(detailUrl);
        rightPanel.add(Box.createVerticalStrut(8));

        rightPanel.add(new JLabel(lang.getString("entry.category")));
        detailCategory = new JLabel(" ");
        rightPanel.add(detailCategory);
        rightPanel.add(Box.createVerticalStrut(8));

        rightPanel.add(new JLabel(lang.getString("entry.notes")));
        detailNotes = new JTextArea(3, 20);
        detailNotes.setEditable(false);
        detailNotes.setLineWrap(true);
        rightPanel.add(new JScrollPane(detailNotes));
        rightPanel.add(Box.createVerticalStrut(8));

        rightPanel.add(new JLabel(lang.getString("entry.created")));
        detailCreated = new JLabel(" ");
        rightPanel.add(detailCreated);
        rightPanel.add(Box.createVerticalStrut(4));
        rightPanel.add(new JLabel(lang.getString("entry.updated")));
        detailUpdated = new JLabel(" ");
        rightPanel.add(detailUpdated);

        rightPanel.add(Box.createVerticalGlue());

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
                if (passwordVisibilityTimer != null) passwordVisibilityTimer.cancel();
                passwordVisibilityTimer = new Timer();
                passwordVisibilityTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(() -> {
                            detailPassword.setEchoChar(echoChar);
                            showDetailPassword.setSelected(false);
                        });
                    }
                }, PASSWORD_VISIBILITY_TIMEOUT_MS);
            } else {
                detailPassword.setEchoChar(echoChar);
                if (passwordVisibilityTimer != null) {
                    passwordVisibilityTimer.cancel();
                    passwordVisibilityTimer = null;
                }
            }
        });

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
        detailPassword.setText("");
        detailUrl.setText(" ");
        detailCategory.setText(" ");
        detailNotes.setText("");
        detailCreated.setText(" ");
        detailUpdated.setText(" ");
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

        // Cancel previous clipboard clear timer if any
        if (clipboardTimer != null) {
            clipboardTimer.cancel();
        }
        clipboardTimer = new Timer();
        clipboardTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Clear clipboard on EDT since it interacts with the system toolkit
                SwingUtilities.invokeLater(() ->
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(""), null));
            }
        }, clipboardClearSeconds * 1000L);
    }

    /**
     * Cancels the clipboard clear timer and password visibility timer if running.
     */
    public void cancelClipboardTimer() {
        if (clipboardTimer != null) {
            clipboardTimer.cancel();
            clipboardTimer = null;
        }
        if (passwordVisibilityTimer != null) {
            passwordVisibilityTimer.cancel();
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
                case 2: return e.getCategory();
                case 3:
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
