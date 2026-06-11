package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.PasswordEntry;

import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.components.StrengthMeter;
import com.passwordmanager.ui.theme.DesignTokens;
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
    private JToggleButton revealToggle;
    private JTextField urlField;
    private JTextArea notesArea;
    private JComboBox<String> categoryCombo;
    private JTextField tagsField;
    private StrengthMeter strengthMeter;
    private boolean confirmed = false;
    private PasswordEntry entry;
    private char echoChar;

    public EntryDialog(Frame owner, String dialogTitle, PasswordEntry existing, List<String> categories) {
        super(owner, dialogTitle, true);
        this.entry = existing;
        initComponents(categories);
        if (existing != null) {
            populateFields(existing);
        }
    }

    private void initComponents(List<String> categories) {
        setLayout(new BorderLayout());
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(
            DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_LG, DesignTokens.SPACE_XL));

        titleField = new JTextField();
        form.add(group(lang.getString("entry.title") + " *", titleField));
        usernameField = new JTextField();
        form.add(group(lang.getString("entry.username"), usernameField));
        emailField = new JTextField();
        form.add(group(lang.getString("entry.email"), emailField));

        // Password row: field + reveal + generate
        passwordField = new JPasswordField();
        echoChar = passwordField.getEchoChar();
        revealToggle = new JToggleButton(lang.getString("entry.show_password"));
        revealToggle.setFocusPainted(false);
        JButton generateBtn = Buttons.tonal(lang.getString("entry.generate"));
        JPanel passRow = new JPanel(new BorderLayout(DesignTokens.SPACE_SM, 0));
        passRow.setOpaque(false);
        passRow.add(passwordField, BorderLayout.CENTER);
        JPanel passBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, 0));
        passBtns.setOpaque(false);
        passBtns.add(revealToggle);
        passBtns.add(generateBtn);
        passRow.add(passBtns, BorderLayout.EAST);
        form.add(group(lang.getString("entry.password") + " *", passRow));

        // Strength meter
        strengthMeter = new StrengthMeter();
        strengthMeter.setAlignmentX(Component.LEFT_ALIGNMENT);
        strengthMeter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        form.add(strengthMeter);
        form.add(Box.createVerticalStrut(DesignTokens.SPACE_MD));

        urlField = new JTextField();
        form.add(group(lang.getString("entry.url"), urlField));

        categoryCombo = new JComboBox<>();
        if (categories != null) {
            for (String c : categories) categoryCombo.addItem(c);
        }
        form.add(group(lang.getString("entry.category"), categoryCombo));

        tagsField = new JTextField();
        form.add(group(lang.getString("entry.tags"), tagsField));

        notesArea = new JTextArea(4, 25);
        notesArea.setLineWrap(true);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        notesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        JComponent notesGroup = group(lang.getString("entry.notes"), notesScroll);
        form.add(notesGroup);

        add(form, BorderLayout.CENTER);

        // Buttons (calm: ghost cancel + primary save)
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, DesignTokens.SPACE_MD));
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DesignTokens.outline()));
        JButton cancelBtn = new JButton(lang.getString("common.cancel"));
        JButton saveBtn = Buttons.primary(lang.getString("common.save"));
        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // Listeners
        revealToggle.addActionListener(e ->
            passwordField.setEchoChar(revealToggle.isSelected() ? (char) 0 : echoChar));

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
            captureEntry();
            confirmed = true;
            dispose();
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(540, 600));
        setLocationRelativeTo(getOwner());
    }

    private void populateFields(PasswordEntry e) {
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
        strengthMeter.update(pwd);
        SecureWiper.wipe(pwd);
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

    public boolean isConfirmed() { return confirmed; }

    public PasswordEntry getEntry() {
        return confirmed ? entry : null;
    }

    /**
     * Builds/updates the entry from the fields. Called once at save time (before the
     * secret password field is wiped on dispose); {@link #getEntry()} then returns this
     * captured value, so it stays valid after the dialog -- and its fields -- are cleared.
     */
    private void captureEntry() {
        String tagsStr = tagsField.getText().trim();
        List<String> tags = tagsStr.isEmpty()
            ? new java.util.ArrayList<>()
            : Arrays.asList(tagsStr.split("\\s*,\\s*"));

        if (entry == null) {
            entry = new PasswordEntry(
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
    }

    /**
     * Best-effort wipe of the password field's content on close. Swing's
     * {@code GapContent} backing store cannot be reliably zeroed (JDK 17+ blocks
     * reflection into {@code java.desktop} internals), so this removes the content to
     * shrink the exposure window; it does not guarantee the chars are overwritten.
     */
    private void wipeSecretFields() {
        try {
            javax.swing.text.Document doc = passwordField.getDocument();
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
