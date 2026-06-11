package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.SshKeyEntry;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.theme.DesignTokens;
import com.passwordmanager.util.SecureWiper;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;

/**
 * Dialog for creating or editing an SSH key entry.
 */
public class SshKeyEntryDialog extends JDialog {
    private final LanguageManager lang = LanguageManager.getInstance();
    private JTextField titleField;
    private JComboBox<String> keyTypeCombo;
    private JPasswordField privateKeyField;
    private JToggleButton revealToggle;
    private JTextArea publicKeyArea;
    private JTextField fingerprintField;
    private JTextArea notesArea;
    private boolean confirmed = false;
    private SshKeyEntry entry;
    private char echoChar;

    public SshKeyEntryDialog(Frame owner, String dialogTitle, SshKeyEntry existing) {
        super(owner, dialogTitle, true);
        this.entry = existing;
        initComponents();
        if (existing != null) {
            populateFields(existing);
        }
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(
            DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_LG, DesignTokens.SPACE_XL));

        titleField = new JTextField();
        form.add(group(lang.getString("entry.title") + " *", titleField));

        keyTypeCombo = new JComboBox<>(new String[]{"ED25519", "RSA"});
        form.add(group(lang.getString("ssh.key_type"), keyTypeCombo));

        // Private key row: field + reveal
        privateKeyField = new JPasswordField();
        echoChar = privateKeyField.getEchoChar();
        revealToggle = new JToggleButton(lang.getString("entry.show_password"));
        revealToggle.setFocusPainted(false);
        JPanel pkRow = new JPanel(new BorderLayout(DesignTokens.SPACE_SM, 0));
        pkRow.setOpaque(false);
        pkRow.add(privateKeyField, BorderLayout.CENTER);
        pkRow.add(revealToggle, BorderLayout.EAST);
        form.add(group(lang.getString("ssh.private_key"), pkRow));

        publicKeyArea = new JTextArea(3, 25);
        publicKeyArea.setLineWrap(true);
        form.add(group(lang.getString("ssh.public_key"), new JScrollPane(publicKeyArea)));

        fingerprintField = new JTextField();
        form.add(group(lang.getString("ssh.fingerprint"), fingerprintField));

        notesArea = new JTextArea(3, 25);
        notesArea.setLineWrap(true);
        form.add(group(lang.getString("entry.notes"), new JScrollPane(notesArea)));

        add(form, BorderLayout.CENTER);

        // Buttons (ghost cancel + primary save)
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, DesignTokens.SPACE_MD));
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DesignTokens.outline()));
        JButton cancelBtn = new JButton(lang.getString("common.cancel"));
        JButton saveBtn = Buttons.primary(lang.getString("common.save"));
        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // Listeners
        revealToggle.addActionListener(e ->
            privateKeyField.setEchoChar(revealToggle.isSelected() ? (char) 0 : echoChar));

        cancelBtn.addActionListener(e -> dispose());

        saveBtn.addActionListener(e -> {
            if (titleField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(SshKeyEntryDialog.this,
                    lang.getString("entry.required").replace("{0}", lang.getString("entry.title")),
                    lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            captureEntry();
            confirmed = true;
            dispose();
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(540, 520));
        setLocationRelativeTo(getOwner());
    }

    /** A captioned field group: small muted caption above a full-width control. */
    private JComponent group(String caption, JComponent comp) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, DesignTokens.SPACE_MD, 0));
        JLabel c = new JLabel(caption);
        c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
        c.setForeground(DesignTokens.onSurfaceFaint());
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        comp.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(comp.getPreferredSize().height, 32)));
        p.add(c);
        p.add(Box.createVerticalStrut(6));
        p.add(comp);
        return p;
    }

    private void populateFields(SshKeyEntry e) {
        titleField.setText(e.getTitle());
        keyTypeCombo.setSelectedItem(e.getKeyType() != null ? e.getKeyType() : "ED25519");
        char[] pk = e.getPrivateKey();
        if (pk != null) {
            setPasswordFieldValue(privateKeyField, pk);
            SecureWiper.wipe(pk);
        }
        publicKeyArea.setText(e.getPublicKey() != null ? e.getPublicKey() : "");
        fingerprintField.setText(e.getFingerprint() != null ? e.getFingerprint() : "");
        notesArea.setText(e.getNotes() != null ? e.getNotes() : "");
    }

    public boolean isConfirmed() { return confirmed; }

    public SshKeyEntry getEntry() {
        return confirmed ? entry : null;
    }

    /**
     * Builds/updates the entry from the fields. Called once at save time (before the
     * secret private-key field is wiped on dispose); {@link #getEntry()} then returns this
     * captured value, so it stays valid after the dialog -- and its fields -- are cleared.
     */
    private void captureEntry() {
        if (entry == null) {
            entry = new SshKeyEntry(
                titleField.getText().trim(),
                privateKeyField.getPassword(),
                publicKeyArea.getText().trim(),
                (String) keyTypeCombo.getSelectedItem(),
                fingerprintField.getText().trim()
            );
        } else {
            entry.setTitle(titleField.getText().trim());
            entry.setPrivateKey(privateKeyField.getPassword());
            entry.setPublicKey(publicKeyArea.getText().trim());
            entry.setKeyType((String) keyTypeCombo.getSelectedItem());
            entry.setFingerprint(fingerprintField.getText().trim());
            entry.setNotes(notesArea.getText());
        }
    }

    /**
     * Best-effort wipe of the private-key field's content on close. Swing's
     * {@code GapContent} backing store cannot be reliably zeroed (JDK 17+ blocks
     * reflection into {@code java.desktop} internals), so this removes the content to
     * shrink the exposure window; it does not guarantee the chars are overwritten.
     */
    private void wipeSecretFields() {
        try {
            javax.swing.text.Document doc = privateKeyField.getDocument();
            doc.remove(0, doc.getLength());
        } catch (javax.swing.text.BadLocationException ignored) {
            // offsets are always valid here
        }
    }

    @Override
    public void dispose() {
        wipeSecretFields();
        super.dispose();
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
