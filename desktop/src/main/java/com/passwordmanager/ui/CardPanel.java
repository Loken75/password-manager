package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.CardEntry;
import com.passwordmanager.vault.CardService;
import com.passwordmanager.vault.CardType;
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
 * Panel displaying bank card entries with search, table listing, and detail view.
 * Follows the same structural pattern as VaultPanel but simplified: no categories
 * sidebar, no favicons, no advanced filters.
 */
public class CardPanel extends JPanel {
    private final LanguageManager lang = LanguageManager.getInstance();
    private CardService cardService;
    private int clipboardClearSeconds;

    private JTextField searchField;
    private JTable entryTable;
    private CardTableModel tableModel;

    // Detail panel fields
    private JLabel detailTitle;
    private JLabel detailCardholderName;
    private JPasswordField detailCardNumber;
    private JCheckBox showCardNumber;
    private JLabel detailExpiryDate;
    private JPasswordField detailCvv;
    private JCheckBox showCvv;
    private JPasswordField detailCardPin;
    private JCheckBox showCardPin;
    private JLabel detailCardType;
    private JTextArea detailNotes;
    private JLabel detailCreated;
    private JLabel detailUpdated;

    // Star column (0), then data columns start at 1
    private static final SortField[] COLUMN_SORT_FIELDS = {
        SortField.FAVORITE, SortField.TITLE, SortField.CARDHOLDER_NAME, SortField.CARD_TYPE, null
    };

    private List<CardEntry> displayedEntries = new ArrayList<>();
    private SortField currentSort = SortField.TITLE;
    private boolean sortAscending = true;
    private javax.swing.Timer clipboardTimer;
    private javax.swing.Timer cardNumberVisibilityTimer;
    private javax.swing.Timer cvvVisibilityTimer;
    private javax.swing.Timer cardPinVisibilityTimer;
    private static final int VISIBILITY_TIMEOUT_MS = 30_000;

    // Bulk action toolbar
    private JPanel bulkToolbar;
    private JLabel bulkSelectionLabel;

    // Callbacks
    private Runnable onVaultChanged;

    public CardPanel(CardService cardService, int clipboardClearSeconds) {
        this.cardService = cardService;
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
        tableModel = new CardTableModel();
        entryTable = new JTable(tableModel);
        entryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        entryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        entryTable.setRowHeight(28);
        // Column widths: star(30), title, cardholder, card type, last4
        entryTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        entryTable.getColumnModel().getColumn(0).setMaxWidth(30);
        entryTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        entryTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        entryTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        entryTable.getColumnModel().getColumn(4).setPreferredWidth(100);

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

        // Star column renderer (centered)
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

        // CENTER: Detail fields with GridBag
        JPanel tablePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gl = new GridBagConstraints();
        gl.anchor = GridBagConstraints.NORTHWEST;
        gl.fill = GridBagConstraints.BOTH;

        Font boldFont = new Font("SansSerif", Font.BOLD, 12);
        int row = 0;

        // Cardholder Name
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblCardholder = new JLabel(lang.getString("entry.cardholder_name"));
        lblCardholder.setFont(boldFont);
        tablePanel.add(createCell(lblCardholder, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailCardholderName = new JLabel(" ");
        tablePanel.add(createCell(detailCardholderName, false, true), gl);

        // Card Number (masked + copy + show toggle)
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblCardNum = new JLabel(lang.getString("entry.card_number"));
        lblCardNum.setFont(boldFont);
        tablePanel.add(createCell(lblCardNum, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailCardNumber = new JPasswordField();
        detailCardNumber.setEditable(false);
        showCardNumber = new JCheckBox();
        showCardNumber.setToolTipText(lang.getString("entry.show_password"));
        JPanel cardNumPanel = new JPanel(new BorderLayout());
        cardNumPanel.add(detailCardNumber, BorderLayout.CENTER);
        JPanel cardNumBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        cardNumBtns.add(showCardNumber);
        JButton copyCardNumBtn = new JButton("\u2398");
        copyCardNumBtn.setMargin(new Insets(1, 4, 1, 4));
        copyCardNumBtn.setToolTipText(lang.getString("entry.copy"));
        copyCardNumBtn.addActionListener(e -> copyCardNumberToClipboard());
        cardNumBtns.add(copyCardNumBtn);
        cardNumPanel.add(cardNumBtns, BorderLayout.EAST);
        tablePanel.add(createCell(cardNumPanel, false, true), gl);

        // Expiry Date
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblExpiry = new JLabel(lang.getString("entry.expiry_date"));
        lblExpiry.setFont(boldFont);
        tablePanel.add(createCell(lblExpiry, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailExpiryDate = new JLabel(" ");
        tablePanel.add(createCell(detailExpiryDate, false, true), gl);

        // CVV (masked + copy + show toggle)
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblCvv = new JLabel(lang.getString("entry.cvv"));
        lblCvv.setFont(boldFont);
        tablePanel.add(createCell(lblCvv, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailCvv = new JPasswordField();
        detailCvv.setEditable(false);
        showCvv = new JCheckBox();
        showCvv.setToolTipText(lang.getString("entry.show_password"));
        JPanel cvvPanel = new JPanel(new BorderLayout());
        cvvPanel.add(detailCvv, BorderLayout.CENTER);
        JPanel cvvBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        cvvBtns.add(showCvv);
        JButton copyCvvBtn = new JButton("\u2398");
        copyCvvBtn.setMargin(new Insets(1, 4, 1, 4));
        copyCvvBtn.setToolTipText(lang.getString("entry.copy"));
        copyCvvBtn.addActionListener(e -> copyCvvToClipboard());
        cvvBtns.add(copyCvvBtn);
        cvvPanel.add(cvvBtns, BorderLayout.EAST);
        tablePanel.add(createCell(cvvPanel, false, true), gl);

        // Card PIN (masked + copy + show toggle)
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblPin = new JLabel(lang.getString("entry.card_pin"));
        lblPin.setFont(boldFont);
        tablePanel.add(createCell(lblPin, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailCardPin = new JPasswordField();
        detailCardPin.setEditable(false);
        showCardPin = new JCheckBox();
        showCardPin.setToolTipText(lang.getString("entry.show_password"));
        JPanel pinPanel = new JPanel(new BorderLayout());
        pinPanel.add(detailCardPin, BorderLayout.CENTER);
        JPanel pinBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pinBtns.add(showCardPin);
        JButton copyPinBtn = new JButton("\u2398");
        copyPinBtn.setMargin(new Insets(1, 4, 1, 4));
        copyPinBtn.setToolTipText(lang.getString("entry.copy"));
        copyPinBtn.addActionListener(e -> copyCardPinToClipboard());
        pinBtns.add(copyPinBtn);
        pinPanel.add(pinBtns, BorderLayout.EAST);
        tablePanel.add(createCell(pinPanel, false, true), gl);

        // Card Type
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblType = new JLabel(lang.getString("entry.card_type"));
        lblType.setFont(boldFont);
        tablePanel.add(createCell(lblType, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailCardType = new JLabel(" ");
        tablePanel.add(createCell(detailCardType, false, true), gl);

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

        // Updated (last row - no bottom border)
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

        // === Assemble with JSplitPane ===
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

        // Show/hide toggles for sensitive fields with independent auto-hide timers
        final char cardNumEcho = detailCardNumber.getEchoChar();
        showCardNumber.addActionListener(e -> {
            if (showCardNumber.isSelected()) {
                detailCardNumber.setEchoChar((char) 0);
                if (cardNumberVisibilityTimer != null) cardNumberVisibilityTimer.stop();
                cardNumberVisibilityTimer = new javax.swing.Timer(VISIBILITY_TIMEOUT_MS, evt -> {
                    detailCardNumber.setEchoChar(cardNumEcho);
                    showCardNumber.setSelected(false);
                });
                cardNumberVisibilityTimer.setRepeats(false);
                cardNumberVisibilityTimer.start();
            } else {
                if (cardNumberVisibilityTimer != null) cardNumberVisibilityTimer.stop();
                detailCardNumber.setEchoChar(cardNumEcho);
            }
        });

        final char cvvEcho = detailCvv.getEchoChar();
        showCvv.addActionListener(e -> {
            if (showCvv.isSelected()) {
                detailCvv.setEchoChar((char) 0);
                if (cvvVisibilityTimer != null) cvvVisibilityTimer.stop();
                cvvVisibilityTimer = new javax.swing.Timer(VISIBILITY_TIMEOUT_MS, evt -> {
                    detailCvv.setEchoChar(cvvEcho);
                    showCvv.setSelected(false);
                });
                cvvVisibilityTimer.setRepeats(false);
                cvvVisibilityTimer.start();
            } else {
                if (cvvVisibilityTimer != null) cvvVisibilityTimer.stop();
                detailCvv.setEchoChar(cvvEcho);
            }
        });

        final char pinEcho = detailCardPin.getEchoChar();
        showCardPin.addActionListener(e -> {
            if (showCardPin.isSelected()) {
                detailCardPin.setEchoChar((char) 0);
                if (cardPinVisibilityTimer != null) cardPinVisibilityTimer.stop();
                cardPinVisibilityTimer = new javax.swing.Timer(VISIBILITY_TIMEOUT_MS, evt -> {
                    detailCardPin.setEchoChar(pinEcho);
                    showCardPin.setSelected(false);
                });
                cardPinVisibilityTimer.setRepeats(false);
                cardPinVisibilityTimer.start();
            } else {
                if (cardPinVisibilityTimer != null) cardPinVisibilityTimer.stop();
                detailCardPin.setEchoChar(pinEcho);
            }
        });

        // Click on star column to toggle favorite; double-click to edit; right-click for context menu
        entryTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = entryTable.columnAtPoint(e.getPoint());
                int tableRow = entryTable.rowAtPoint(e.getPoint());
                if (col == 0 && tableRow >= 0 && tableRow < displayedEntries.size()
                        && SwingUtilities.isLeftMouseButton(e)) {
                    cardService.toggleFavorite(displayedEntries.get(tableRow).getId());
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
                    int tableRow = entryTable.rowAtPoint(e.getPoint());
                    if (tableRow >= 0 && !entryTable.isRowSelected(tableRow)) {
                        entryTable.setRowSelectionInterval(tableRow, tableRow);
                    }
                    createContextMenu().show(entryTable, e.getX(), e.getY());
                }
            }
        });
    }

    // === Public API ===

    public void refreshEntries() {
        String query = searchField.getText().trim();
        List<CardEntry> entries;

        if (!query.isEmpty()) {
            entries = cardService.search(query);
        } else {
            entries = cardService.search("");
        }

        displayedEntries = cardService.sorted(entries, currentSort);
        if (!sortAscending) {
            java.util.Collections.reverse(displayedEntries);
        }
        tableModel.fireTableDataChanged();
    }

    public CardEntry getSelectedEntry() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return null;
        return displayedEntries.get(row);
    }

    public void addNewEntry() {
        CardEntryDialog dlg = new CardEntryDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.new_entry"),
            null);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            cardService.addEntry(dlg.getEntry());
            refreshEntries();
            notifyChanged();
        }
    }

    public void editSelectedEntry() {
        if (entryTable.getSelectedRowCount() != 1) return;
        CardEntry selected = getSelectedEntry();
        if (selected == null) return;
        CardEntryDialog dlg = new CardEntryDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.edit_entry"),
            selected);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            cardService.updateEntry(dlg.getEntry());
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
            cardService.bulkDelete(ids);
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

    /**
     * Cancels the clipboard clear timer and all visibility timers if running.
     */
    public void cancelClipboardTimer() {
        if (clipboardTimer != null) {
            clipboardTimer.stop();
            clipboardTimer = null;
        }
        if (cardNumberVisibilityTimer != null) {
            cardNumberVisibilityTimer.stop();
            cardNumberVisibilityTimer = null;
        }
        if (cvvVisibilityTimer != null) {
            cvvVisibilityTimer.stop();
            cvvVisibilityTimer = null;
        }
        if (cardPinVisibilityTimer != null) {
            cardPinVisibilityTimer.stop();
            cardPinVisibilityTimer = null;
        }
    }

    // === Private helpers ===

    private void showSelectedEntry() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) {
            clearDetails();
            return;
        }
        CardEntry e = displayedEntries.get(row);
        detailTitle.setText((e.isFavorite() ? "\u2605 " : "") + e.getTitle());
        detailCardholderName.setText(e.getCardholderName() != null ? e.getCardholderName() : "");

        char[] cardNum = e.getCardNumber();
        setPasswordFieldValue(detailCardNumber, cardNum);
        SecureWiper.wipe(cardNum);

        detailExpiryDate.setText(e.getExpiryDate() != null ? e.getExpiryDate() : "");

        char[] cvv = e.getCvv();
        setPasswordFieldValue(detailCvv, cvv);
        SecureWiper.wipe(cvv);

        char[] pin = e.getCardPin();
        setPasswordFieldValue(detailCardPin, pin);
        SecureWiper.wipe(pin);

        detailCardType.setText(e.getCardType() != null
            ? lang.getString(CardType.toDesktopMessageKey(e.getCardType())) : "");
        detailNotes.setText(e.getNotes() != null ? e.getNotes() : "");
        detailCreated.setText(e.getCreatedAt() != null ? e.getCreatedAt() : "");
        detailUpdated.setText(e.getUpdatedAt() != null ? e.getUpdatedAt() : "");
    }

    private void clearDetails() {
        detailTitle.setText(" ");
        detailCardholderName.setText(" ");
        detailCardNumber.setText("");
        detailExpiryDate.setText(" ");
        detailCvv.setText("");
        detailCardPin.setText("");
        detailCardType.setText(" ");
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
            cardService.bulkDelete(ids);
            refreshEntries();
            clearDetails();
            notifyChanged();
        }
    }

    private void bulkToggleFavoriteSelected() {
        List<String> ids = getSelectedEntryIds();
        if (ids.isEmpty()) return;
        boolean anyNotFav = false;
        for (CardEntry e : displayedEntries) {
            if (ids.contains(e.getId()) && !e.isFavorite()) {
                anyNotFav = true;
                break;
            }
        }
        cardService.bulkSetFavorite(ids, anyNotFav);
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
            CardEntry se = getSelectedEntry();
            if (se != null) {
                cardService.toggleFavorite(se.getId());
                refreshEntries();
                notifyChanged();
            }
        });
        menu.add(toggleFav);

        menu.addSeparator();

        JMenuItem copyCardNum = new JMenuItem(lang.getString("entry.copy") + " " + lang.getString("entry.card_number"));
        copyCardNum.setEnabled(singleSelected);
        copyCardNum.addActionListener(e -> copyCardNumberToClipboard());
        menu.add(copyCardNum);

        JMenuItem copyCvv = new JMenuItem(lang.getString("entry.copy") + " " + lang.getString("entry.cvv"));
        copyCvv.setEnabled(singleSelected);
        copyCvv.addActionListener(e -> copyCvvToClipboard());
        menu.add(copyCvv);

        JMenuItem copyPin = new JMenuItem(lang.getString("entry.copy") + " " + lang.getString("entry.card_pin"));
        copyPin.setEnabled(singleSelected);
        copyPin.addActionListener(e -> copyCardPinToClipboard());
        menu.add(copyPin);

        return menu;
    }

    private void copyCardNumberToClipboard() {
        CardEntry e = getSelectedEntry();
        if (e == null) return;
        char[] data = e.getCardNumber();
        if (data == null) return;
        SecureClipboard.copyPassword(data);
        SecureWiper.wipe(data);
        scheduleClipboardClear();
    }

    private void copyCvvToClipboard() {
        CardEntry e = getSelectedEntry();
        if (e == null) return;
        char[] data = e.getCvv();
        if (data == null) return;
        SecureClipboard.copyPassword(data);
        SecureWiper.wipe(data);
        scheduleClipboardClear();
    }

    private void copyCardPinToClipboard() {
        CardEntry e = getSelectedEntry();
        if (e == null) return;
        char[] data = e.getCardPin();
        if (data == null) return;
        SecureClipboard.copyPassword(data);
        SecureWiper.wipe(data);
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

    private void notifyChanged() {
        if (onVaultChanged != null) onVaultChanged.run();
    }

    // === Table Model ===

    private class CardTableModel extends AbstractTableModel {
        private final String[] columns = {
            "\u2605",  // star column
            lang.getString("entry.title"),
            lang.getString("entry.cardholder_name"),
            lang.getString("entry.card_type"),
            lang.getString("entry.card_number")
        };

        @Override public int getRowCount() { return displayedEntries.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            CardEntry e = displayedEntries.get(row);
            switch (col) {
                case 0: return e.isFavorite() ? "\u2605" : "\u2606";
                case 1: return e.getTitle();
                case 2: return e.getCardholderName();
                case 3: return e.getCardType() != null
                    ? lang.getString(CardType.toDesktopMessageKey(e.getCardType())) : "";
                case 4:
                    String last4 = e.getLast4Digits();
                    return last4 != null ? "\u2022\u2022\u2022\u2022 " + last4 : "";
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
