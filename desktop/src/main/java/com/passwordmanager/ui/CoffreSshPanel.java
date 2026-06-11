package com.passwordmanager.ui;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.ui.components.Avatar;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.components.EntryCardPanel;
import com.passwordmanager.ui.components.RoundedPanel;
import com.passwordmanager.ui.components.SecretFieldPanel;
import com.passwordmanager.ui.theme.DesignTokens;
import com.passwordmanager.util.SecureCharsets;
import com.passwordmanager.util.SecureWiper;
import com.passwordmanager.vault.SshKeyEntry;
import com.passwordmanager.vault.SshKeyService;
import com.passwordmanager.vault.SortField;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * "Calme & confiance" SSH keys view: calm card list + detail, plus a header with
 * generate / import / import-from-content (ported from the old SshKeyPanel). Reuses
 * {@link SshKeyService} + {@link SshKeyEntryDialog}.
 */
public class CoffreSshPanel extends JPanel {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final SshKeyService sshKeyService;
    private int clipboardClearSeconds;
    private final Runnable onVaultChanged;

    private final JTextField searchField = new JTextField();
    private final JPanel cardsHost = new JPanel();
    private final JPanel detailHost = new JPanel(new BorderLayout());

    private List<SshKeyEntry> displayed = new ArrayList<>();
    private final java.util.LinkedHashSet<String> selectedIds = new java.util.LinkedHashSet<>();
    private String anchorId;
    private EntryCardPanel focusedCard;
    private javax.swing.Timer clipboardTimer;

    public CoffreSshPanel(SshKeyService sshKeyService, int clipboardClearSeconds, Runnable onVaultChanged) {
        this.sshKeyService = sshKeyService;
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

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);

        // Header: search (grows) + generate / import actions.
        searchField.putClientProperty("JTextField.placeholderText", lang.getString("vault.search"));
        JButton genBtn = Buttons.tonal(lang.getString("ssh.generate"));
        genBtn.addActionListener(e -> generateSshKey());
        JButton impBtn = new JButton(lang.getString("ssh.import"));
        impBtn.addActionListener(e -> importSshKey());
        JButton newKeyBtn = Buttons.primary("+ " + lang.getString("ssh.new_key"));
        newKeyBtn.addActionListener(e -> addNewEntry());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, 0));
        actions.setOpaque(false);
        actions.add(genBtn);
        actions.add(impBtn);
        actions.add(newKeyBtn);

        JPanel header = new JPanel(new BorderLayout(DesignTokens.SPACE_SM, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(DesignTokens.SPACE_MD, DesignTokens.SPACE_MD, DesignTokens.SPACE_SM, DesignTokens.SPACE_MD));
        header.add(searchField, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        center.add(header, BorderLayout.NORTH);

        cardsHost.setLayout(new BoxLayout(cardsHost, BoxLayout.Y_AXIS));
        cardsHost.setOpaque(false);
        cardsHost.setBorder(BorderFactory.createEmptyBorder(0, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD, DesignTokens.SPACE_MD));
        JScrollPane scroll = new JScrollPane(cardsHost);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(700, 440));
        center.add(scroll, BorderLayout.CENTER);

        detailHost.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, DesignTokens.outline()),
            BorderFactory.createEmptyBorder(DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL)));
        detailHost.setPreferredSize(new Dimension(360, 0));
        clearDetail();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, detailHost);
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
        List<SshKeyEntry> entries = query.isEmpty() ? sshKeyService.getActiveList() : sshKeyService.search(query);
        displayed = sshKeyService.sorted(new ArrayList<>(entries), SortField.TITLE);

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
        focusedCard = null;
        for (SshKeyEntry e : displayed) {
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

    private EntryCardPanel buildCard(SshKeyEntry e) {
        EntryCardPanel card = new EntryCardPanel(e.getTitle(),
            e.getFingerprint() != null ? e.getFingerprint() : "",
            e.getKeyType(), null, null, null, e.isFavorite());
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

    private void maybePopup(MouseEvent ev, SshKeyEntry e) {
        if (ev.isPopupTrigger()) {
            Point p = SwingUtilities.convertPoint(ev.getComponent(), ev.getPoint(), this);
            if (!selectedIds.contains(e.getId())) selectSingle(e);
            JPopupMenu menu = selectedIds.size() > 1 ? bulkMenu() : contextMenu(e);
            menu.show(this, p.x, p.y);
        }
    }

    private void selectSingle(SshKeyEntry e) { selectedIds.clear(); selectedIds.add(e.getId()); anchorId = e.getId(); rebuildList(); updateDetailOrBulk(); focusSelectedCard(); }
    private void selectByOffset(int delta) {
        if (displayed.isEmpty()) return;
        int idx = selectedIds.size() == 1 ? indexOf(selectedIds.iterator().next()) : -1;
        int next = Math.max(0, Math.min(displayed.size() - 1, idx + delta));
        selectSingle(displayed.get(next));
    }
    private void focusSelectedCard() { if (focusedCard != null) focusedCard.requestFocusInWindow(); }
    private void toggleSelect(SshKeyEntry e) { if (!selectedIds.add(e.getId())) selectedIds.remove(e.getId()); anchorId = e.getId(); rebuildList(); updateDetailOrBulk(); }
    private void selectRange(SshKeyEntry e) {
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
    private SshKeyEntry entryById(String id) {
        if (id == null) return null;
        for (SshKeyEntry e : displayed) if (id.equals(e.getId())) return e;
        return null;
    }
    private SshKeyEntry getSelected() { return selectedIds.size() == 1 ? entryById(selectedIds.iterator().next()) : null; }

    private void updateDetailOrBulk() {
        if (selectedIds.size() > 1) showBulk(selectedIds.size());
        else { SshKeyEntry s = getSelected(); if (s != null) showDetail(s); else clearDetail(); }
    }

    private void clearDetail() {
        detailHost.removeAll();
        JLabel empty = new JLabel(lang.getString("vault.details"), SwingConstants.CENTER);
        empty.setForeground(DesignTokens.onSurfaceFaint());
        detailHost.add(empty, BorderLayout.CENTER);
        detailHost.revalidate();
        detailHost.repaint();
    }

    private void showDetail(SshKeyEntry e) {
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

        if (e.getKeyType() != null) col.add(fieldRow(lang.getString("ssh.key_type"), e.getKeyType(), null));
        if (e.getFingerprint() != null) col.add(fieldRow(lang.getString("ssh.fingerprint"), e.getFingerprint(), () -> copyText(e.getFingerprint())));

        // Public key (mono multiline + copy)
        if (e.getPublicKey() != null && !e.getPublicKey().isBlank()) {
            col.add(caption(lang.getString("ssh.public_key")));
            JTextArea pub = new JTextArea(e.getPublicKey());
            pub.setEditable(false);
            pub.setLineWrap(true);
            pub.setWrapStyleWord(false);
            pub.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            pub.setRows(3);
            JButton copyPub = Buttons.copyButton(lang.getString("entry.copy"), lang.getString("common.copied"),
                () -> copyText(e.getPublicKey()));
            JPanel pubWrap = new JPanel(new BorderLayout(DesignTokens.SPACE_SM, 0));
            pubWrap.setOpaque(false);
            pubWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            pubWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            pubWrap.add(new JScrollPane(pub), BorderLayout.CENTER);
            pubWrap.add(copyPub, BorderLayout.EAST);
            col.add(pubWrap);
        }

        // Private key (mono masked)
        col.add(caption(lang.getString("ssh.private_key")));
        SecretFieldPanel secret = new SecretFieldPanel();
        char[] pk = e.getPrivateKey();
        secret.setValue(pk);
        if (pk != null) SecureWiper.wipe(pk);
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
        JLabel val = new JLabel(value);
        v.add(val, BorderLayout.CENTER);
        if (onCopy != null) {
            v.add(Buttons.copyButton(lang.getString("entry.copy"), lang.getString("common.copied"), onCopy),
                BorderLayout.EAST);
        }
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    private JPopupMenu contextMenu(SshKeyEntry e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem(lang.getString("vault.edit_entry"));
        edit.addActionListener(ev -> editSelected());
        menu.add(edit);
        JMenuItem del = new JMenuItem(lang.getString("vault.delete_entry"));
        del.addActionListener(ev -> deleteEntry(e));
        menu.add(del);
        menu.addSeparator();
        JMenuItem fav = new JMenuItem(lang.getString("entry.toggle_favorite"));
        fav.addActionListener(ev -> { sshKeyService.toggleFavorite(e.getId()); refresh(); notifyChanged(); });
        menu.add(fav);
        if (e.getPublicKey() != null) {
            JMenuItem copyPub = new JMenuItem(lang.getString("entry.copy") + " " + lang.getString("ssh.public_key"));
            copyPub.addActionListener(ev -> copyText(e.getPublicKey()));
            menu.add(copyPub);
        }
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
        SshKeyEntryDialog dlg = new SshKeyEntryDialog((Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.new_entry"), null);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) { sshKeyService.addEntry(dlg.getEntry()); refresh(); notifyChanged(); }
    }

    public void editSelected() {
        SshKeyEntry sel = getSelected();
        if (sel == null) return;
        SshKeyEntryDialog dlg = new SshKeyEntryDialog((Frame) SwingUtilities.getWindowAncestor(this),
            lang.getString("vault.edit_entry"), sel);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) { sshKeyService.updateEntry(dlg.getEntry()); refresh(); updateDetailOrBulk(); notifyChanged(); }
    }

    private void deleteEntry(SshKeyEntry e) {
        int c = JOptionPane.showConfirmDialog(this, lang.getString("vault.delete_confirm"),
            lang.getString("vault.delete_entry"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c == JOptionPane.YES_OPTION) {
            sshKeyService.bulkDelete(List.of(e.getId()));
            selectedIds.remove(e.getId());
            refresh();
            notifyChanged();
        }
    }

    private void duplicate(SshKeyEntry selected) {
        char[] pk = selected.getPrivateKey();
        try {
            SshKeyEntry dup = new SshKeyEntry(lang.getString("menu.duplicate.prefix") + " " + selected.getTitle(),
                pk, selected.getPublicKey(), selected.getKeyType(), selected.getFingerprint());
            sshKeyService.addEntry(dup);
            refresh();
            notifyChanged();
        } finally {
            if (pk != null) SecureWiper.wipe(pk);
        }
    }

    private void bulkDelete() {
        List<String> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty()) return;
        int c = JOptionPane.showConfirmDialog(this,
            lang.getString("vault.bulk.confirm.delete").replace("{0}", String.valueOf(ids.size())),
            lang.getString("vault.delete_entry"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c == JOptionPane.YES_OPTION) { sshKeyService.bulkDelete(ids); selectedIds.clear(); refresh(); notifyChanged(); }
    }

    private void bulkToggleFavorite() {
        List<String> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty()) return;
        boolean anyNotFav = false;
        for (SshKeyEntry e : displayed) if (ids.contains(e.getId()) && !e.isFavorite()) { anyNotFav = true; break; }
        sshKeyService.bulkSetFavorite(ids, anyNotFav);
        refresh();
        notifyChanged();
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

    // ---- Generate / Import (ported from SshKeyPanel) ----
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
            @Override protected SshKeyEntry doInBackground() throws Exception {
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
                char[] privChars = SecureCharsets.toChars(privBytes);
                java.util.Arrays.fill(privBytes, (byte) 0);
                privOut.reset();
                String pubString = pubOut.toString(java.nio.charset.StandardCharsets.UTF_8);
                SshKeyEntry entry = new SshKeyEntry(name, privChars, pubString, type, fingerprint);
                SecureWiper.wipe(privChars);
                return entry;
            }
            @Override protected void done() {
                try {
                    sshKeyService.addEntry(get());
                    refresh();
                    notifyChanged();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CoffreSshPanel.this, lang.getString("ssh.generate_error"),
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
        String name = (String) JOptionPane.showInputDialog(this, lang.getString("ssh.key_name"),
            lang.getString("ssh.import"), JOptionPane.PLAIN_MESSAGE, null, null, defaultName);
        if (name == null || name.trim().isEmpty()) return;
        final String keyName = name.trim();

        new SwingWorker<SshKeyEntry, Void>() {
            @Override protected SshKeyEntry doInBackground() throws Exception {
                byte[] pemBytes = Files.readAllBytes(file.toPath());
                try {
                    KeyPair kpair = KeyPair.load(new JSch(), pemBytes, null);
                    ByteArrayOutputStream pubOut = new ByteArrayOutputStream();
                    kpair.writePublicKey(pubOut, keyName);
                    String fingerprint = kpair.getFingerPrint();
                    String keyType = keyTypeName(kpair.getKeyType());
                    kpair.dispose();
                    char[] privChars = SecureCharsets.toChars(pemBytes);
                    SshKeyEntry entry = new SshKeyEntry(keyName, privChars,
                        pubOut.toString(java.nio.charset.StandardCharsets.UTF_8), keyType, fingerprint);
                    SecureWiper.wipe(privChars);
                    return entry;
                } finally {
                    java.util.Arrays.fill(pemBytes, (byte) 0);
                }
            }
            @Override protected void done() {
                try {
                    sshKeyService.addEntry(get());
                    refresh();
                    notifyChanged();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CoffreSshPanel.this, lang.getString("ssh.import_error"),
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
        byte[] pemBytes = normalizePemContent(content).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        contentArea.setText("");

        new SwingWorker<SshKeyEntry, Void>() {
            @Override protected SshKeyEntry doInBackground() throws Exception {
                try {
                    KeyPair kpair = KeyPair.load(new JSch(), pemBytes, null);
                    ByteArrayOutputStream pubOut = new ByteArrayOutputStream();
                    kpair.writePublicKey(pubOut, name);
                    String fingerprint = kpair.getFingerPrint();
                    String keyType = keyTypeName(kpair.getKeyType());
                    kpair.dispose();
                    char[] privChars = SecureCharsets.toChars(pemBytes);
                    SshKeyEntry entry = new SshKeyEntry(name, privChars,
                        pubOut.toString(java.nio.charset.StandardCharsets.UTF_8), keyType, fingerprint);
                    SecureWiper.wipe(privChars);
                    return entry;
                } finally {
                    java.util.Arrays.fill(pemBytes, (byte) 0);
                }
            }
            @Override protected void done() {
                try {
                    sshKeyService.addEntry(get());
                    refresh();
                    notifyChanged();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CoffreSshPanel.this, lang.getString("ssh.import_content_error"),
                        lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static String keyTypeName(int t) {
        switch (t) {
            case KeyPair.RSA: return "RSA";
            case KeyPair.ED25519: return "ED25519";
            case KeyPair.ECDSA: return "ECDSA";
            default: return "UNKNOWN";
        }
    }

    private static String formatDate(String iso) {
        if (iso == null) return "";
        if (iso.length() >= 16 && iso.charAt(10) == 'T') return iso.substring(0, 10) + " " + iso.substring(11, 16);
        return iso.length() >= 10 ? iso.substring(0, 10) : iso;
    }

    private static String normalizePemContent(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\\r?\\n")) {
            sb.append(line.stripTrailing()).append('\n');
        }
        return sb.toString().trim() + "\n";
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
