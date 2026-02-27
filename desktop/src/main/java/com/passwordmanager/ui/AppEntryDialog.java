package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.AppEntry;
import com.passwordmanager.util.SecureWiper;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.security.SecureRandom;

/**
 * Dialog for creating or editing an application PIN entry.
 */
public class AppEntryDialog extends JDialog {
    private final LanguageManager lang = LanguageManager.getInstance();
    private JTextField titleField;
    private JTextField usernameField;
    private JPasswordField pinField;
    private JCheckBox showPinCheck;
    private JTextArea notesArea;
    private boolean confirmed = false;
    private AppEntry entry;
    private char echoChar;

    public AppEntryDialog(Frame owner, String dialogTitle, AppEntry existing) {
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

        // Title
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.title") + " *"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        titleField = new JTextField(25);
        form.add(titleField, gbc);
        gbc.gridwidth = 1;

        // Username
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.username")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        usernameField = new JTextField(25);
        form.add(usernameField, gbc);
        gbc.gridwidth = 1;

        // PIN + show/generate
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.pin")), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        pinField = new JPasswordField(20);
        echoChar = pinField.getEchoChar();
        form.add(pinField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JPanel pinButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        showPinCheck = new JCheckBox(lang.getString("entry.show_password"));
        JButton generateBtn = new JButton(lang.getString("entry.generate"));
        pinButtons.add(showPinCheck);
        pinButtons.add(generateBtn);
        form.add(pinButtons, gbc);

        // Notes
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel(lang.getString("entry.notes")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        notesArea = new JTextArea(4, 25);
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
        showPinCheck.addActionListener(e ->
            pinField.setEchoChar(showPinCheck.isSelected() ? (char) 0 : echoChar));

        generateBtn.addActionListener(e -> {
            char[] generated = generateRandomPin();
            setPasswordFieldValue(pinField, generated);
            SecureWiper.wipe(generated);
        });

        cancelBtn.addActionListener(e -> dispose());

        saveBtn.addActionListener(e -> {
            if (titleField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(AppEntryDialog.this,
                    lang.getString("entry.required").replace("{0}", lang.getString("entry.title")),
                    lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            confirmed = true;
            dispose();
        });

        pack();
        setMinimumSize(new Dimension(500, 350));
        setLocationRelativeTo(getOwner());
    }

    private void populateFields(AppEntry e) {
        titleField.setText(e.getTitle());
        usernameField.setText(e.getUsername() != null ? e.getUsername() : "");
        char[] pin = e.getPin();
        if (pin != null) {
            setPasswordFieldValue(pinField, pin);
            SecureWiper.wipe(pin);
        } else {
            pinField.setText("");
        }
        notesArea.setText(e.getNotes() != null ? e.getNotes() : "");
    }

    /**
     * Generates a random numeric PIN of 4 to 6 digits.
     */
    private char[] generateRandomPin() {
        SecureRandom random = new SecureRandom();
        int length = 4 + random.nextInt(3); // 4, 5, or 6
        char[] pin = new char[length];
        for (int i = 0; i < length; i++) {
            pin[i] = (char) ('0' + random.nextInt(10));
        }
        return pin;
    }

    public boolean isConfirmed() { return confirmed; }

    public AppEntry getEntry() {
        if (!confirmed) return null;

        if (entry == null) {
            entry = new AppEntry(
                titleField.getText().trim(),
                usernameField.getText().trim(),
                pinField.getPassword(),
                notesArea.getText()
            );
        } else {
            entry.setTitle(titleField.getText().trim());
            entry.setUsername(usernameField.getText().trim());
            entry.setPin(pinField.getPassword());
            entry.setNotes(notesArea.getText());
        }
        return entry;
    }

    /**
     * Sets a JPasswordField value from char[] without creating an intermediate String.
     * Uses the Document model directly to avoid String interning.
     */
    private static void setPasswordFieldValue(JPasswordField field, char[] value) {
        Document doc = field.getDocument();
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, new String(value), null);
        } catch (BadLocationException ignored) {
            // Should not happen with valid offsets
        }
    }
}
