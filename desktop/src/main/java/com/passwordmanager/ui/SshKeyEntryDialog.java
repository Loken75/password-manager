package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.SshKeyEntry;
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
    private JCheckBox showPrivateKeyCheck;
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
        setLayout(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Title (name)
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.title") + " *"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        titleField = new JTextField(25);
        form.add(titleField, gbc);
        gbc.gridwidth = 1;

        // Key type
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("ssh.key_type")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        keyTypeCombo = new JComboBox<>(new String[]{"ED25519", "RSA"});
        form.add(keyTypeCombo, gbc);
        gbc.gridwidth = 1;

        // Private key + show toggle
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("ssh.private_key")), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        privateKeyField = new JPasswordField(25);
        echoChar = privateKeyField.getEchoChar();
        form.add(privateKeyField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        showPrivateKeyCheck = new JCheckBox(lang.getString("entry.show_password"));
        form.add(showPrivateKeyCheck, gbc);

        // Public key (multi-line, read-only style but editable for input)
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel(lang.getString("ssh.public_key")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        publicKeyArea = new JTextArea(3, 25);
        publicKeyArea.setLineWrap(true);
        form.add(new JScrollPane(publicKeyArea), gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;

        // Fingerprint
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("ssh.fingerprint")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        fingerprintField = new JTextField(25);
        form.add(fingerprintField, gbc);
        gbc.gridwidth = 1;

        // Notes
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel(lang.getString("entry.notes")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        notesArea = new JTextArea(3, 25);
        notesArea.setLineWrap(true);
        form.add(new JScrollPane(notesArea), gbc);

        add(form, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton(lang.getString("common.cancel"));
        JButton saveBtn = new JButton(lang.getString("common.save"));
        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // Listeners
        showPrivateKeyCheck.addActionListener(e ->
            privateKeyField.setEchoChar(showPrivateKeyCheck.isSelected() ? (char) 0 : echoChar));

        cancelBtn.addActionListener(e -> dispose());

        saveBtn.addActionListener(e -> {
            if (titleField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(SshKeyEntryDialog.this,
                    lang.getString("entry.required").replace("{0}", lang.getString("entry.title")),
                    lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            confirmed = true;
            dispose();
        });

        pack();
        setMinimumSize(new Dimension(550, 450));
        setLocationRelativeTo(getOwner());
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
        if (!confirmed) return null;

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
        return entry;
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
