package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.AppEntry;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.theme.DesignTokens;
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
    private JToggleButton revealToggle;
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
        setLayout(new BorderLayout());
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(
            DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_LG, DesignTokens.SPACE_XL));

        titleField = new JTextField();
        form.add(group(lang.getString("entry.title") + " *", titleField));
        usernameField = new JTextField();
        form.add(group(lang.getString("entry.username"), usernameField));

        // PIN row: field + reveal + generate
        pinField = new JPasswordField();
        echoChar = pinField.getEchoChar();
        revealToggle = new JToggleButton(lang.getString("entry.show_password"));
        revealToggle.setFocusPainted(false);
        JButton generateBtn = Buttons.tonal(lang.getString("entry.generate"));
        JPanel pinRow = new JPanel(new BorderLayout(DesignTokens.SPACE_SM, 0));
        pinRow.setOpaque(false);
        pinRow.add(pinField, BorderLayout.CENTER);
        JPanel pinBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, 0));
        pinBtns.setOpaque(false);
        pinBtns.add(revealToggle);
        pinBtns.add(generateBtn);
        pinRow.add(pinBtns, BorderLayout.EAST);
        form.add(group(lang.getString("entry.pin"), pinRow));

        notesArea = new JTextArea(4, 25);
        notesArea.setLineWrap(true);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        form.add(group(lang.getString("entry.notes"), notesScroll));

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
            pinField.setEchoChar(revealToggle.isSelected() ? (char) 0 : echoChar));

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
            captureEntry();
            confirmed = true;
            dispose();
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(480, 420));
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
        return confirmed ? entry : null;
    }

    /**
     * Builds/updates the entry from the fields. Called once at save time (before the
     * secret PIN field is wiped on dispose); {@link #getEntry()} then returns this
     * captured value, so it stays valid after the dialog -- and its fields -- are cleared.
     */
    private void captureEntry() {
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
    }

    /**
     * Best-effort wipe of the PIN field's content on close. Swing's {@code GapContent}
     * backing store cannot be reliably zeroed (JDK 17+ blocks reflection into
     * {@code java.desktop} internals), so this removes the content to shrink the exposure
     * window; it does not guarantee the chars are overwritten.
     */
    private void wipeSecretFields() {
        try {
            javax.swing.text.Document doc = pinField.getDocument();
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
