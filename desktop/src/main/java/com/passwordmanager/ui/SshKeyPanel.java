package com.passwordmanager.ui;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.SshKeyEntry;
import com.passwordmanager.vault.SshKeyService;
import com.passwordmanager.vault.SortField;
import com.passwordmanager.util.SecureWiper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel displaying and managing SSH key entries with search, table, and detail view.
 */
public class SshKeyPanel extends JPanel {
    private final LanguageManager lang = LanguageManager.getInstance();
    private SshKeyService sshKeyService;
    private int clipboardClearSeconds;

    private JTextField searchField;
    private JTable entryTable;
    private SshKeyTableModel tableModel;
    private JLabel detailTitle, detailKeyType, detailFingerprint, detailCreated, detailUpdated;
    private JPasswordField detailPrivateKey;
    private JCheckBox showDetailPrivateKey;
    private JTextArea detailPublicKey;
    private JTextArea detailNotes;

    private static final SortField[] COLUMN_SORT_FIELDS = {
        SortField.FAVORITE, SortField.TITLE, null, null
    };

    private List<SshKeyEntry> displayedEntries = new ArrayList<>();
    private SortField currentSort = SortField.TITLE;
    private boolean sortAscending = true;
    private javax.swing.Timer clipboardTimer;
    private javax.swing.Timer keyVisibilityTimer;
    private static final int KEY_VISIBILITY_TIMEOUT_MS = 30_000;

    private JPanel bulkToolbar;
    private JLabel bulkSelectionLabel;

    private Runnable onVaultChanged;

    public SshKeyPanel(SshKeyService sshKeyService, int clipboardClearSeconds) {
        this.sshKeyService = sshKeyService;
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

        // Search bar + Generate/Import buttons
        searchField = new JTextField();
        searchField.setToolTipText(lang.getString("vault.search"));
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.add(new JLabel(lang.getString("vault.search")), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JPanel keyActionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton generateBtn = new JButton(lang.getString("ssh.generate"));
        JButton importBtn = new JButton(lang.getString("ssh.import"));
        JButton importContentBtn = new JButton(lang.getString("ssh.import_content"));
        keyActionBtns.add(generateBtn);
        keyActionBtns.add(importBtn);
        keyActionBtns.add(importContentBtn);
        searchPanel.add(keyActionBtns, BorderLayout.EAST);
        centerPanel.add(searchPanel, BorderLayout.NORTH);

        generateBtn.addActionListener(e -> generateSshKey());
        importBtn.addActionListener(e -> importSshKey());
        importContentBtn.addActionListener(e -> importSshKeyFromContent());

        // Table
        tableModel = new SshKeyTableModel();
        entryTable = new JTable(tableModel);
        entryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        entryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        entryTable.setRowHeight(28);
        entryTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        entryTable.getColumnModel().getColumn(0).setMaxWidth(30);
        entryTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        entryTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        entryTable.getColumnModel().getColumn(3).setPreferredWidth(200);

        entryTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = entryTable.columnAtPoint(e.getPoint());
                if (col >= 0 && col < COLUMN_SORT_FIELDS.length && COLUMN_SORT_FIELDS[col] != null) {
                    setSortMode(COLUMN_SORT_FIELDS[col]);
                }
            }
        });

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

        detailTitle = new JLabel(" ", SwingConstants.CENTER);
        detailTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        rightPanel.add(detailTitle, BorderLayout.NORTH);

        JPanel tablePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gl = new GridBagConstraints();
        gl.anchor = GridBagConstraints.NORTHWEST;
        gl.fill = GridBagConstraints.BOTH;

        Font boldFont = new Font("SansSerif", Font.BOLD, 12);
        int row = 0;

        // Key type
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblType = new JLabel(lang.getString("ssh.key_type"));
        lblType.setFont(boldFont);
        tablePanel.add(createCell(lblType, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailKeyType = new JLabel(" ");
        tablePanel.add(createCell(detailKeyType, false, true), gl);

        // Fingerprint with copy
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblFp = new JLabel(lang.getString("ssh.fingerprint"));
        lblFp.setFont(boldFont);
        tablePanel.add(createCell(lblFp, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailFingerprint = new JLabel(" ");
        tablePanel.add(createCell(createDetailValuePanel(detailFingerprint, this::copyFingerprintToClipboard), false, true), gl);

        // Private key with show/hide + copy
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblPk = new JLabel(lang.getString("ssh.private_key"));
        lblPk.setFont(boldFont);
        tablePanel.add(createCell(lblPk, true, true), gl);
        gl.gridx = 1; gl.weightx = 1;
        detailPrivateKey = new JPasswordField();
        detailPrivateKey.setEditable(false);
        showDetailPrivateKey = new JCheckBox();
        showDetailPrivateKey.setToolTipText(lang.getString("entry.show_password"));
        JPanel pkPanel = new JPanel(new BorderLayout());
        pkPanel.add(detailPrivateKey, BorderLayout.CENTER);
        JPanel pkBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pkBtns.add(showDetailPrivateKey);
        JButton copyPkSmall = new JButton("\u2398");
        copyPkSmall.setMargin(new Insets(1, 4, 1, 4));
        copyPkSmall.setToolTipText(lang.getString("entry.copy"));
        copyPkSmall.addActionListener(e -> copyPrivateKeyToClipboard());
        pkBtns.add(copyPkSmall);
        pkPanel.add(pkBtns, BorderLayout.EAST);
        tablePanel.add(createCell(pkPanel, false, true), gl);

        // Public key (multi-line, read-only)
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
        JLabel lblPub = new JLabel(lang.getString("ssh.public_key"));
        lblPub.setFont(boldFont);
        tablePanel.add(createCell(lblPub, true, true), gl);
        gl.gridx = 1; gl.weightx = 1; gl.weighty = 0.3;
        detailPublicKey = new JTextArea(2, 20);
        detailPublicKey.setEditable(false);
        detailPublicKey.setLineWrap(true);
        JPanel pubPanel = new JPanel(new BorderLayout());
        pubPanel.add(new JScrollPane(detailPublicKey), BorderLayout.CENTER);
        JButton copyPubBtn = new JButton("\u2398");
        copyPubBtn.setMargin(new Insets(1, 4, 1, 4));
        copyPubBtn.setToolTipText(lang.getString("entry.copy"));
        copyPubBtn.addActionListener(e -> copyPublicKeyToClipboard());
        JPanel pubBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pubBtnPanel.add(copyPubBtn);
        pubPanel.add(pubBtnPanel, BorderLayout.EAST);
        tablePanel.add(createCell(pubPanel, false, true), gl);
        gl.weighty = 0;

        // Notes
        row++;
        gl.gridx = 0; gl.gridy = row; gl.weightx = 0;
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

        // Updated
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

        // === Assemble ===
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

        final char echoChar = detailPrivateKey.getEchoChar();
        showDetailPrivateKey.addActionListener(e -> {
            if (showDetailPrivateKey.isSelected()) {
                detailPrivateKey.setEchoChar((char) 0);
                if (keyVisibilityTimer != null) keyVisibilityTimer.stop();
                keyVisibilityTimer = new javax.swing.Timer(KEY_VISIBILITY_TIMEOUT_MS, evt -> {
                    detailPrivateKey.setEchoChar(echoChar);
                    showDetailPrivateKey.setSelected(false);
                });
                keyVisibilityTimer.setRepeats(false);
                keyVisibilityTimer.start();
            } else {
                detailPrivateKey.setEchoChar(echoChar);
                if (keyVisibilityTimer != null) {
                    keyVisibilityTimer.stop();
                    keyVisibilityTimer = null;
                }
            }
        });

        entryTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = entryTable.columnAtPoint(e.getPoint());
                int mRow = entryTable.rowAtPoint(e.getPoint());
                if (col == 0 && mRow >= 0 && mRow < displayedEntries.size() && SwingUtilities.isLeftMouseButton(e)) {
                    sshKeyService.toggleFavorite(displayedEntries.get(mRow).getId());
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
        List<SshKeyEntry> entries = sshKeyService.search(query);

        displayedEntries = sshKeyService.sorted(entries, currentSort);
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
        SshKeyEntry e = displayedEntries.get(row);
        detailTitle.setText((e.isFavorite() ? "\u2605 " : "") + e.getTitle());
        detailKeyType.setText(e.getKeyType() != null ? e.getKeyType() : "");
        detailFingerprint.setText(e.getFingerprint() != null ? e.getFingerprint() : "");
        char[] pk = e.getPrivateKey();
        setPasswordFieldValue(detailPrivateKey, pk);
        SecureWiper.wipe(pk);
        detailPublicKey.setText(e.getPublicKey() != null ? e.getPublicKey() : "");
        detailNotes.setText(e.getNotes() != null ? e.getNotes() : "");
        detailCreated.setText(e.getCreatedAt() != null ? e.getCreatedAt() : "");
        detailUpdated.setText(e.getUpdatedAt() != null ? e.getUpdatedAt() : "");
    }

    private void clearDetails() {
        detailTitle.setText(" ");
        detailKeyType.setText(" ");
        detailFingerprint.setText(" ");
        detailPrivateKey.setText("");
        detailPublicKey.setText("");
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
            sshKeyService.bulkDelete(ids);
            refreshEntries();
            clearDetails();
            notifyChanged();
        }
    }

    private void bulkToggleFavoriteSelected() {
        List<String> ids = getSelectedEntryIds();
        if (ids.isEmpty()) return;
        boolean anyNotFav = false;
        for (SshKeyEntry e : displayedEntries) {
            if (ids.contains(e.getId()) && !e.isFavorite()) {
                anyNotFav = true;
                break;
            }
        }
        sshKeyService.bulkSetFavorite(ids, anyNotFav);
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
            SshKeyEntry se = getSelectedEntry();
            if (se != null) {
                sshKeyService.toggleFavorite(se.getId());
                refreshEntries();
                notifyChanged();
            }
        });
        menu.add(toggleFav);

        menu.addSeparator();

        JMenuItem copyPub = new JMenuItem(lang.getString("entry.copy") + " " + lang.getString("ssh.public_key"));
        copyPub.setEnabled(singleSelected);
        copyPub.addActionListener(e -> copyPublicKeyToClipboard());
        menu.add(copyPub);

        JMenuItem copyFp = new JMenuItem(lang.getString("entry.copy") + " " + lang.getString("ssh.fingerprint"));
        copyFp.setEnabled(singleSelected);
        copyFp.addActionListener(e -> copyFingerprintToClipboard());
        menu.add(copyFp);

        return menu;
    }

    private void copyFingerprintToClipboard() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return;
        String fp = displayedEntries.get(row).getFingerprint();
        if (fp == null || fp.isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(fp), null);
        scheduleClipboardClear();
    }

    private void copyPublicKeyToClipboard() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return;
        String pub = displayedEntries.get(row).getPublicKey();
        if (pub == null || pub.isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(pub), null);
        scheduleClipboardClear();
    }

    private void copyPrivateKeyToClipboard() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return;
        char[] pk = displayedEntries.get(row).getPrivateKey();
        if (pk == null) return;
        SecureClipboard.copyPassword(pk);
        SecureWiper.wipe(pk);
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

    public void cancelClipboardTimer() {
        if (clipboardTimer != null) {
            clipboardTimer.stop();
            clipboardTimer = null;
        }
        if (keyVisibilityTimer != null) {
            keyVisibilityTimer.stop();
            keyVisibilityTimer = null;
        }
    }

    public SshKeyEntry getSelectedEntry() {
        int row = entryTable.getSelectedRow();
        if (row < 0 || row >= displayedEntries.size()) return null;
        return displayedEntries.get(row);
    }

    public void editSelectedEntry() {
        if (entryTable.getSelectedRowCount() != 1) return;
        SshKeyEntry selected = getSelectedEntry();
        if (selected == null) return;
        SshKeyEntryDialog dlg = new SshKeyEntryDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.edit_entry"),
            selected);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            sshKeyService.updateEntry(dlg.getEntry());
            refreshEntries();
            notifyChanged();
        }
    }

    public void addNewEntry() {
        SshKeyEntryDialog dlg = new SshKeyEntryDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.new_entry"),
            null);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            sshKeyService.addEntry(dlg.getEntry());
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
            sshKeyService.bulkDelete(ids);
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

    private void generateSshKey() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel(lang.getString("ssh.key_name")), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JTextField nameField = new JTextField(20);
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel(lang.getString("ssh.key_type")), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"ED25519", "RSA"});
        panel.add(typeCombo, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel,
            lang.getString("ssh.generate"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        String type = (String) typeCombo.getSelectedItem();

        new SwingWorker<SshKeyEntry, Void>() {
            @Override
            protected SshKeyEntry doInBackground() throws Exception {
                JSch jsch = new JSch();
                int keyType = "RSA".equals(type) ? KeyPair.RSA : KeyPair.ED25519;
                int keySize = "RSA".equals(type) ? 4096 : 0;
                KeyPair kpair = KeyPair.genKeyPair(jsch, keyType, keySize);

                ByteArrayOutputStream privOut = new ByteArrayOutputStream();
                ByteArrayOutputStream pubOut = new ByteArrayOutputStream();
                kpair.writePrivateKey(privOut);
                kpair.writePublicKey(pubOut, name);
                String fingerprint = kpair.getFingerPrint();
                kpair.dispose();

                byte[] privBytes = privOut.toByteArray();
                char[] privChars = new String(privBytes, java.nio.charset.StandardCharsets.UTF_8).toCharArray();
                java.util.Arrays.fill(privBytes, (byte) 0);
                // Best-effort wipe of ByteArrayOutputStream
                int sz = privOut.size();
                privOut.reset();
                privOut.write(new byte[sz]);
                privOut.reset();

                String pubString = pubOut.toString(java.nio.charset.StandardCharsets.UTF_8);

                SshKeyEntry entry = new SshKeyEntry(name, privChars, pubString, type, fingerprint);
                SecureWiper.wipe(privChars);
                return entry;
            }

            @Override
            protected void done() {
                try {
                    SshKeyEntry entry = get();
                    sshKeyService.addEntry(entry);
                    refreshEntries();
                    notifyChanged();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SshKeyPanel.this,
                        lang.getString("ssh.generate_error"),
                        lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void importSshKey() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("PEM files", "pem", "key", "id_rsa", "id_ed25519"));
        fc.setAcceptAllFileFilterUsed(true);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        String defaultName = file.getName().replaceFirst("\\.[^.]+$", "");

        String name = (String) JOptionPane.showInputDialog(this,
            lang.getString("ssh.key_name"), lang.getString("ssh.import"),
            JOptionPane.PLAIN_MESSAGE, null, null, defaultName);
        if (name == null || name.trim().isEmpty()) return;
        final String keyName = name.trim();

        new SwingWorker<SshKeyEntry, Void>() {
            @Override
            protected SshKeyEntry doInBackground() throws Exception {
                byte[] pemBytes = Files.readAllBytes(file.toPath());
                try {
                    JSch jsch = new JSch();
                    KeyPair kpair = KeyPair.load(jsch, pemBytes, null);

                    ByteArrayOutputStream pubOut = new ByteArrayOutputStream();
                    kpair.writePublicKey(pubOut, keyName);
                    String fingerprint = kpair.getFingerPrint();
                    String keyType;
                    switch (kpair.getKeyType()) {
                        case KeyPair.RSA: keyType = "RSA"; break;
                        case KeyPair.ED25519: keyType = "ED25519"; break;
                        case KeyPair.ECDSA: keyType = "ECDSA"; break;
                        default: keyType = "UNKNOWN";
                    }
                    kpair.dispose();

                    char[] privChars = new String(pemBytes, java.nio.charset.StandardCharsets.UTF_8).toCharArray();
                    SshKeyEntry entry = new SshKeyEntry(keyName, privChars, pubOut.toString(java.nio.charset.StandardCharsets.UTF_8), keyType, fingerprint);
                    SecureWiper.wipe(privChars);
                    return entry;
                } finally {
                    java.util.Arrays.fill(pemBytes, (byte) 0);
                }
            }

            @Override
            protected void done() {
                try {
                    SshKeyEntry entry = get();
                    sshKeyService.addEntry(entry);
                    refreshEntries();
                    notifyChanged();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SshKeyPanel.this,
                        lang.getString("ssh.import_error"),
                        lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void importSshKeyFromContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel(lang.getString("ssh.key_name")), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JTextField nameField = new JTextField(20);
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.gridwidth = 2;
        panel.add(new JLabel(lang.getString("ssh.import_content_hint")), gbc);

        gbc.gridy = 2; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1;
        JTextArea contentArea = new JTextArea(10, 40);
        contentArea.setLineWrap(true);
        panel.add(new JScrollPane(contentArea), gbc);

        int result = JOptionPane.showConfirmDialog(this, panel,
            lang.getString("ssh.import_content"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        String content = contentArea.getText();
        if (content.isEmpty()) return;

        // Normalize pasted content: strip trailing spaces per line, normalize line endings
        String normalized = normalizePemContent(content);
        byte[] pemBytes = normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // Clear the text area content
        contentArea.setText("");

        new SwingWorker<SshKeyEntry, Void>() {
            @Override
            protected SshKeyEntry doInBackground() throws Exception {
                try {
                    JSch jsch = new JSch();
                    KeyPair kpair = KeyPair.load(jsch, pemBytes, null);

                    ByteArrayOutputStream pubOut = new ByteArrayOutputStream();
                    kpair.writePublicKey(pubOut, name);
                    String fingerprint = kpair.getFingerPrint();
                    String keyType;
                    switch (kpair.getKeyType()) {
                        case KeyPair.RSA: keyType = "RSA"; break;
                        case KeyPair.ED25519: keyType = "ED25519"; break;
                        case KeyPair.ECDSA: keyType = "ECDSA"; break;
                        default: keyType = "UNKNOWN";
                    }
                    kpair.dispose();

                    char[] privChars = new String(pemBytes, java.nio.charset.StandardCharsets.UTF_8).toCharArray();
                    SshKeyEntry entry = new SshKeyEntry(name, privChars, pubOut.toString(java.nio.charset.StandardCharsets.UTF_8), keyType, fingerprint);
                    SecureWiper.wipe(privChars);
                    return entry;
                } finally {
                    java.util.Arrays.fill(pemBytes, (byte) 0);
                }
            }

            @Override
            protected void done() {
                try {
                    SshKeyEntry entry = get();
                    sshKeyService.addEntry(entry);
                    refreshEntries();
                    notifyChanged();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SshKeyPanel.this,
                        lang.getString("ssh.import_content_error"),
                        lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Normalizes PEM content: strips trailing whitespace per line, normalizes line endings,
     * and ensures a trailing newline. This prevents JSch parse failures from copy-paste artifacts.
     */
    private static String normalizePemContent(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\\r?\\n")) {
            sb.append(line.stripTrailing()).append('\n');
        }
        return sb.toString().trim() + "\n";
    }

    // === Table Model ===
    private class SshKeyTableModel extends AbstractTableModel {
        private final String[] columns = {
            "\u2605",
            lang.getString("entry.title"),
            lang.getString("ssh.key_type"),
            lang.getString("ssh.fingerprint")
        };

        @Override public int getRowCount() { return displayedEntries.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            SshKeyEntry e = displayedEntries.get(row);
            switch (col) {
                case 0: return e.isFavorite() ? "\u2605" : "\u2606";
                case 1: return e.getTitle();
                case 2: return e.getKeyType();
                case 3: return e.getFingerprint();
                default: return "";
            }
        }

        @Override public boolean isCellEditable(int row, int col) { return false; }
    }

    private static JPanel createCell(Component comp, boolean rightBorder, boolean bottomBorder) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.add(comp, BorderLayout.CENTER);
        cell.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, bottomBorder ? 1 : 0, rightBorder ? 1 : 0, Color.GRAY),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        return cell;
    }

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
