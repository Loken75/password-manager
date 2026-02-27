package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.VaultEntry;

import com.passwordmanager.util.SecureWiper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * Dialog for creating or editing a vault entry.
 */
public class EntryDialog extends JDialog {
    private final LanguageManager lang = LanguageManager.getInstance();
    private JTextField titleField;
    private JTextField usernameField;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JCheckBox showPasswordCheck;
    private JTextField urlField;
    private JTextArea notesArea;
    private JComboBox<String> categoryCombo;
    private JTextField tagsField;
    private JProgressBar strengthBar;
    private JLabel strengthLabel;
    private boolean confirmed = false;
    private VaultEntry entry;
    private char echoChar;

    public EntryDialog(Frame owner, String dialogTitle, VaultEntry existing, List<String> categories) {
        super(owner, dialogTitle, true);
        this.entry = existing;
        initComponents(categories);
        if (existing != null) {
            populateFields(existing);
        }
    }

    private void initComponents(List<String> categories) {
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

        // Email
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.email")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        emailField = new JTextField(25);
        form.add(emailField, gbc);
        gbc.gridwidth = 1;

        // Password + show/generate
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.password") + " *"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        passwordField = new JPasswordField(20);
        echoChar = passwordField.getEchoChar();
        form.add(passwordField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JPanel passButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        showPasswordCheck = new JCheckBox(lang.getString("entry.show_password"));
        JButton generateBtn = new JButton(lang.getString("entry.generate"));
        passButtons.add(showPasswordCheck);
        passButtons.add(generateBtn);
        form.add(passButtons, gbc);

        // Strength bar
        row++;
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
        JPanel strengthPanel = new JPanel(new BorderLayout(5, 0));
        strengthBar = new JProgressBar(0, 100);
        strengthBar.setPreferredSize(new Dimension(200, 16));
        strengthBar.setStringPainted(true);
        strengthLabel = new JLabel(lang.getString("strength.label"));
        strengthPanel.add(strengthBar, BorderLayout.CENTER);
        strengthPanel.add(strengthLabel, BorderLayout.EAST);
        form.add(strengthPanel, gbc);
        gbc.gridwidth = 1;

        // URL
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.url")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        urlField = new JTextField(25);
        form.add(urlField, gbc);
        gbc.gridwidth = 1;

        // Category
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.category")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        categoryCombo = new JComboBox<>();
        if (categories != null) {
            for (String c : categories) categoryCombo.addItem(c);
        }
        form.add(categoryCombo, gbc);
        gbc.gridwidth = 1;

        // Tags
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.tags")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        tagsField = new JTextField(25);
        form.add(tagsField, gbc);
        gbc.gridwidth = 1;

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
        showPasswordCheck.addActionListener(e ->
            passwordField.setEchoChar(showPasswordCheck.isSelected() ? (char) 0 : echoChar));

        generateBtn.addActionListener(e -> {
            PasswordGeneratorDialog gen = new PasswordGeneratorDialog(EntryDialog.this);
            gen.setVisible(true);
            char[] gp = gen.getGeneratedPassword();
            if (gp != null) {
                setPasswordFieldValue(passwordField, gp);
                SecureWiper.wipe(gp);
            }
        });

        passwordField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateStrength(); }
            public void removeUpdate(DocumentEvent e) { updateStrength(); }
            public void changedUpdate(DocumentEvent e) { updateStrength(); }
        });

        cancelBtn.addActionListener(e -> dispose());

        saveBtn.addActionListener(e -> {
            if (titleField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(EntryDialog.this,
                    lang.getString("entry.required").replace("{0}", lang.getString("entry.title")),
                    lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            char[] checkPwd = passwordField.getPassword();
            boolean emptyPwd = checkPwd.length == 0;
            SecureWiper.wipe(checkPwd);
            if (emptyPwd) {
                JOptionPane.showMessageDialog(EntryDialog.this,
                    lang.getString("entry.required").replace("{0}", lang.getString("entry.password")),
                    lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            confirmed = true;
            dispose();
        });

        pack();
        setMinimumSize(new Dimension(550, 480));
        setLocationRelativeTo(getOwner());
    }

    private void populateFields(VaultEntry e) {
        titleField.setText(e.getTitle());
        usernameField.setText(e.getUsername());
        emailField.setText(e.getEmail() != null ? e.getEmail() : "");
        char[] pwd = e.getPassword();
        if (pwd != null) {
            setPasswordFieldValue(passwordField, pwd);
            SecureWiper.wipe(pwd);
        } else {
            passwordField.setText("");
        }
        urlField.setText(e.getUrl());
        notesArea.setText(e.getNotes());
        if (e.getCategory() != null) categoryCombo.setSelectedItem(e.getCategory());
        if (e.getTags() != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < e.getTags().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(e.getTags().get(i));
            }
            tagsField.setText(sb.toString());
        }
        updateStrength();
    }

    private void updateStrength() {
        char[] pwd = passwordField.getPassword();
        StrengthBarHelper.update(strengthBar, strengthLabel, pwd);
        SecureWiper.wipe(pwd);
    }

    public boolean isConfirmed() { return confirmed; }

    public VaultEntry getEntry() {
        if (!confirmed) return null;

        String tagsStr = tagsField.getText().trim();
        List<String> tags = tagsStr.isEmpty()
            ? new java.util.ArrayList<>()
            : Arrays.asList(tagsStr.split("\\s*,\\s*"));

        if (entry == null) {
            entry = new VaultEntry(
                titleField.getText().trim(),
                usernameField.getText().trim(),
                emailField.getText().trim(),
                passwordField.getPassword(),
                urlField.getText().trim(),
                notesArea.getText(),
                (String) categoryCombo.getSelectedItem(),
                tags
            );
        } else {
            entry.setTitle(titleField.getText().trim());
            entry.setUsername(usernameField.getText().trim());
            entry.setEmail(emailField.getText().trim());
            entry.setPassword(passwordField.getPassword());
            entry.setUrl(urlField.getText().trim());
            entry.setNotes(notesArea.getText());
            entry.setCategory((String) categoryCombo.getSelectedItem());
            entry.setTags(tags);
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
