package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.AppEntry;
import com.passwordmanager.vault.AppService;
import com.passwordmanager.vault.SortField;
import com.passwordmanager.util.SecureWiper;

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
 * Panel displaying and managing application PIN entries with search, table, and detail view.
 */
public class AppPanel extends JPanel {
    private final LanguageManager lang = LanguageManager.getInstance();
    private AppService appService;
    private int clipboardClearSeconds;

    private JTextField searchField;
    private JTable entryTable;
    private AppTableModel tableModel;
    private JLabel detailTitle, detailUser, detailCreated, detailUpdated;
    private JPasswordField detailPin;
    private JCheckBox showDetailPin;
    private JTextArea detailNotes;

    // Star column (0), then data columns start at 1
    private static final SortField[] COLUMN_SORT_FIELDS = {
        SortField.FAVORITE, SortField.TITLE, SortField.USERNAME
    };

    private List<AppEntry> displayedEntries = new ArrayList<>();
    private SortField currentSort = SortField.TITLE;
    private boolean sortAscending = true;
    private javax.swing.Timer clipboardTimer;
    private javax.swing.Timer pinVisibilityTimer;
    private static final int PIN_VISIBILITY_TIMEOUT_MS = 30_000;

    // Bulk action toolbar
    private JPanel bulkToolbar;
    private JLabel bulkSelectionLabel;

    // Callbacks
    private Runnable onVaultChanged;

    public AppPanel(AppService appService, int clipboardClearSeconds) {
        this.appService = appService;
        this.clipboardClearSeconds = clipboardClearSeconds;
        initComponents();
        refreshEntries();
    }

    public void setOnVaultChanged(Runnable onVaultChanged) {
        this.onVaultChanged = onVaultChanged;
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        // === Center: Search + Table ===
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Search bar
        searchField = new JTextField();
        searchField.setToolTipText(lang.getString("vault.search"));
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.add(new JLabel(lang.getString("vault.search")), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        centerPanel.add(searchPanel, BorderLayout.NORTH);

        // Table
        tableModel = new AppTableModel();
        entryTable = new JTable(tableModel);
        entryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        entryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        entryTable.setRowHeight(28);
        // Column widths: star(30), title, username
        entryTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        entryTable.getColumnModel().getColumn(0).setMaxWidth(30);
        entryTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        entryTable.getColumnModel().getColumn(2).setPreferredWidth(180);

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

        centerPanel.add(new JScrollPane(entryTable), BorderLayout.CENTER);

        // Bulk action toolbar
        bulkToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bulkToolbar.setVisible(false);
        bulkSelectionLabel = new JLabel();
        JButton actionsBtn = new JButton(lang.getString("vault.bulk.actions") + "...");
        actionsBtn.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            JMenuItem deleteItem = new JMenuItem(lang.getString("vault.bulk.delete"));
            deleteItem.addActionListener(ev -> bulkDeleteSelected());
            popup.add(deleteItem);
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

        // PIN with inline copy + show/hide toggle
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblPin = new JLabel(lang.getString("entry.pin"));
        lblPin.setFont(boldFont);
        tablePanel.add(createCell(lblPin, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailPin = new JPasswordField();
        detailPin.setEditable(false);
        showDetailPin = new JCheckBox();
        showDetailPin.setToolTipText(lang.getString("entry.show_password"));
        JPanel pinPanel = new JPanel(new BorderLayout());
        pinPanel.add(detailPin, BorderLayout.CENTER);
        JPanel pinBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pinBtns.add(showDetailPin);
        JButton copyPinSmall = new JButton("\u2398");
        copyPinSmall.setMargin(new Insets(1, 4, 1, 4));
        copyPinSmall.setToolTipText(lang.getString("entry.copy"));
        copyPinSmall.addActionListener(e -> copyPinToClipboard());
        pinBtns.add(copyPinSmall);
        pinPanel.add(pinBtns, BorderLayout.EAST);
        tablePanel.add(createCell(pinPanel, false, true), gl);

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

        // Updated (last row -- no bottom border)
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

        // === Assemble with resizable JSplitPane ===
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerPanel, rightPanel);
        mainSplit.setResizeWeight(0.7);
        mainSplit.setDividerLocation(550);
        mainSplit.setContinuousLayout(true);

        add(mainSplit, BorderLayout.CENTER);

        // === Listeners ===
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

        final char echoChar = detailPin.getEchoChar();
        showDetailPin.addActionListener(e -> {
            if (showDetailPin.isSelected()) {
                detailPin.setEchoChar((char) 0);
                if (pinVisibilityTimer != null) pinVisibilityTimer.stop();
                pinVisibilityTimer = new javax.swing.Timer(PIN_VISIBILITY_TIMEOUT_MS, evt -> {
                    detailPin.setEchoChar(echoChar);
                    showDetailPin.setSelected(false);
                });
                pinVisibilityTimer.setRepeats(false);
                pinVisibilityTimer.start();
            } else {
                detailPin.setEchoChar(echoChar);
                if (pinVisibilityTimer != null) {
                    pinVisibilityTimer.stop();
                    pinVisibilityTimer = null;
                }
            }
        });

        // Click on star column to toggle favorite; double-click to edit; right-click for context menu
        entryTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = entryTable.columnAtPoint(e.getPoint());
                int mRow = entryTable.rowAtPoint(e.getPoint());
                if (col == 0 && mRow >= 0 && mRow < displayedEntries.size() && SwingUtilities.isLeftMouseButton(e)) {
                    appService.toggleFavorite(displayedEntries.get(mRow).getId());
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
                    int mRow = entryTable.rowAtPoint(e.getPoint());
                    if (mRow >= 0 && !entryTable.isRowSelected(mRow)) {
                        entryTable.setRowSelectionInterval(mRow, mRow);
                    }
                    createContextMenu().show(entryTable, e.getX(), e.getY());
                }
            }
        });
    }

    public void refreshEntries() {
        String query = searchField.getText().trim();
        List<AppEntry> entries = appService.search(query);

        displayedEntries = appService.sorted(entries, currentSort);
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
        AppEntry e = displayedEntries.get(row);
        detailTitle.setText((e.isFavorite() ? "\u2605 " : "") + e.getTitle());
        detailUser.setText(e.getUsername() != null ? e.getUsername() : "");
        char[] pin = e.getPin();
        setPasswordFieldValue(detailPin, pin);
        SecureWiper.wipe(pin);
        detailNotes.setText(e.getNotes() != null ? e.getNotes() : "");
        detailCreated.setText(e.getCreatedAt() != null ? e.getCreatedAt() : "");
        detailUpdated.setText(e.getUpdatedAt() != null ? e.getUpdatedAt() : "");
    }

    private void clearDetails() {
        detailTitle.setText(" ");
        detailUser.setText(" ");
        detailPin.setText("");
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
            appService.bulkDelete(ids);
            refreshEntries();
            clearDetails();
            notifyChanged();
        }
    }

    private void bulkToggleFavoriteSelected() {
        List<String> ids = getSelectedEntryIds();
        if (ids.isEmpty()) return;
        // Check if any selected entry is not a favorite
        boolean anyNotFav = false;
        for (AppEntry e : displayedEntries) {
            if (ids.contains(e.getId()) && !e.isFavorite()) {
                anyNotFav = true;
                break;
            }
        }
        // If any is not favorite, set all to favorite; otherwise unfavorite all
        appService.bulkSetFavorite(ids, anyNotFav);
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
            AppEntry se = getSelectedEntry();
            if (se != null) {
                appService.toggleFavorite(se.getId());
                refreshEntries();
                notifyChanged();
            }
        });
        menu.add(toggleFav);

        menu.addSeparator();

        JMenuItem copyPin = new JMenuItem(lang.getString("entry.copy") + " " + lang.getString("entry.pin"));
        copyPin.setEnabled(singleSelected);
        copyPin.addActionListener(e -> copyPinToClipboard());
        menu.add(copyPin);

        JMenuItem copyUser = new JMenuItem(lang.getString("entry.copy") + " " + lang.getString("entry.username"));
        copyUser.setEnabled(singleSelected);
        copyUser.addActionListener(e -> copyUsernameToClipboard());
        menu.add(copyUser);

        return menu;
    }

    private void copyUsernameToClipboard() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return;
        AppEntry e = displayedEntries.get(row);
        String username = e.getUsername();
        if (username == null || username.isEmpty()) return;

        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(username), null);

        scheduleClipboardClear();
    }

    private void copyPinToClipboard() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return;
        AppEntry e = displayedEntries.get(row);
        char[] clipPin = e.getPin();
        if (clipPin == null) return;

        SecureClipboard.copyPassword(clipPin);
        SecureWiper.wipe(clipPin);

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
     * Cancels the clipboard clear timer and PIN visibility timer if running.
     */
    public void cancelClipboardTimer() {
        if (clipboardTimer != null) {
            clipboardTimer.stop();
            clipboardTimer = null;
        }
        if (pinVisibilityTimer != null) {
            pinVisibilityTimer.stop();
            pinVisibilityTimer = null;
        }
    }

    public AppEntry getSelectedEntry() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return null;
        return displayedEntries.get(row);
    }

    public void editSelectedEntry() {
        if (entryTable.getSelectedRowCount() != 1) return;
        AppEntry selected = getSelectedEntry();
        if (selected == null) return;
        AppEntryDialog dlg = new AppEntryDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.edit_entry"),
            selected);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            appService.updateEntry(dlg.getEntry());
            refreshEntries();
            notifyChanged();
        }
    }

    public void addNewEntry() {
        AppEntryDialog dlg = new AppEntryDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.new_entry"),
            null);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            appService.addEntry(dlg.getEntry());
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
            appService.bulkDelete(ids);
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
    private class AppTableModel extends AbstractTableModel {
        private final String[] columns = {
            "\u2605",  // star column
            lang.getString("entry.title"),
            lang.getString("entry.username")
        };

        @Override public int getRowCount() { return displayedEntries.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            AppEntry e = displayedEntries.get(row);
            switch (col) {
                case 0: return e.isFavorite() ? "\u2605" : "\u2606";
                case 1: return e.getTitle();
                case 2: return e.getUsername();
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
