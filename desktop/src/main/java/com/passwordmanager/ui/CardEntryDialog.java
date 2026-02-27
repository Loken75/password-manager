package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.vault.CardEntry;
import com.passwordmanager.vault.CardType;
import com.passwordmanager.util.SecureWiper;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dialog for creating or editing a bank card entry.
 */
public class CardEntryDialog extends JDialog {
    private final LanguageManager lang = LanguageManager.getInstance();
    private JTextField titleField;
    private JTextField cardholderNameField;
    private JPasswordField cardNumberField;
    private JCheckBox showCardNumberCheck;
    private JTextField expiryDateField;
    private JPasswordField cvvField;
    private JCheckBox showCvvCheck;
    private JPasswordField cardPinField;
    private JCheckBox showCardPinCheck;
    private JComboBox<String> cardTypeCombo;
    /** Maps internal key (e.g. "VISA") -> localized display name (e.g. "Visa"). */
    private final Map<String, String> cardTypeKeyToDisplay = new LinkedHashMap<>();
    private JTextArea notesArea;
    private boolean confirmed = false;
    private CardEntry entry;
    private char cardNumberEchoChar;
    private char cvvEchoChar;
    private char cardPinEchoChar;

    public CardEntryDialog(Frame owner, String dialogTitle, CardEntry existing) {
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

        // Cardholder Name
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.cardholder_name")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        cardholderNameField = new JTextField(25);
        form.add(cardholderNameField, gbc);
        gbc.gridwidth = 1;

        // Card Number + show
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.card_number")), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        cardNumberField = new JPasswordField(20);
        cardNumberEchoChar = cardNumberField.getEchoChar();
        form.add(cardNumberField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        showCardNumberCheck = new JCheckBox(lang.getString("entry.show_password"));
        form.add(showCardNumberCheck, gbc);

        // Expiry Date
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.expiry_date")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        expiryDateField = new JTextField(7);
        expiryDateField.setToolTipText("MM/YY");
        form.add(expiryDateField, gbc);
        gbc.gridwidth = 1;

        // CVV + show
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.cvv")), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        cvvField = new JPasswordField(6);
        cvvEchoChar = cvvField.getEchoChar();
        form.add(cvvField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        showCvvCheck = new JCheckBox(lang.getString("entry.show_password"));
        form.add(showCvvCheck, gbc);

        // Card PIN + show
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.card_pin")), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        cardPinField = new JPasswordField(6);
        cardPinEchoChar = cardPinField.getEchoChar();
        form.add(cardPinField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        showCardPinCheck = new JCheckBox(lang.getString("entry.show_password"));
        form.add(showCardPinCheck, gbc);

        // Card Type
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(lang.getString("entry.card_type")), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        // Build key -> localized display name mapping
        for (String key : CardType.ALL) {
            cardTypeKeyToDisplay.put(key, lang.getString(CardType.toDesktopMessageKey(key)));
        }
        cardTypeCombo = new JComboBox<>(CardType.ALL.toArray(new String[0]));
        // Render localized names instead of raw keys
        cardTypeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                String display = cardTypeKeyToDisplay.getOrDefault(value, String.valueOf(value));
                return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
            }
        });
        form.add(cardTypeCombo, gbc);
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

        // Listeners - show/hide toggles
        showCardNumberCheck.addActionListener(e ->
            cardNumberField.setEchoChar(showCardNumberCheck.isSelected() ? (char) 0 : cardNumberEchoChar));

        showCvvCheck.addActionListener(e ->
            cvvField.setEchoChar(showCvvCheck.isSelected() ? (char) 0 : cvvEchoChar));

        showCardPinCheck.addActionListener(e ->
            cardPinField.setEchoChar(showCardPinCheck.isSelected() ? (char) 0 : cardPinEchoChar));

        cancelBtn.addActionListener(e -> dispose());

        saveBtn.addActionListener(e -> {
            if (titleField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(CardEntryDialog.this,
                    lang.getString("entry.required").replace("{0}", lang.getString("entry.title")),
                    lang.getString("common.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            confirmed = true;
            dispose();
        });

        pack();
        setMinimumSize(new Dimension(500, 450));
        setLocationRelativeTo(getOwner());
    }

    private void populateFields(CardEntry e) {
        titleField.setText(e.getTitle());
        cardholderNameField.setText(e.getCardholderName() != null ? e.getCardholderName() : "");

        char[] cardNum = e.getCardNumber();
        if (cardNum != null) {
            setPasswordFieldValue(cardNumberField, cardNum);
            SecureWiper.wipe(cardNum);
        }

        expiryDateField.setText(e.getExpiryDate() != null ? e.getExpiryDate() : "");

        char[] cvv = e.getCvv();
        if (cvv != null) {
            setPasswordFieldValue(cvvField, cvv);
            SecureWiper.wipe(cvv);
        }

        char[] pin = e.getCardPin();
        if (pin != null) {
            setPasswordFieldValue(cardPinField, pin);
            SecureWiper.wipe(pin);
        }

        if (e.getCardType() != null) {
            cardTypeCombo.setSelectedItem(CardType.normalize(e.getCardType()));
        }

        notesArea.setText(e.getNotes() != null ? e.getNotes() : "");
    }

    public boolean isConfirmed() { return confirmed; }

    public CardEntry getEntry() {
        if (!confirmed) return null;

        char[] cardNum = cardNumberField.getPassword();
        char[] cvv = cvvField.getPassword();
        char[] pin = cardPinField.getPassword();

        try {
            if (entry == null) {
                entry = new CardEntry(
                    titleField.getText().trim(),
                    cardholderNameField.getText().trim(),
                    cardNum,
                    expiryDateField.getText().trim(),
                    cvv,
                    pin,
                    (String) cardTypeCombo.getSelectedItem(),
                    notesArea.getText()
                );
            } else {
                entry.setTitle(titleField.getText().trim());
                entry.setCardholderName(cardholderNameField.getText().trim());
                entry.setCardNumber(cardNum);
                entry.setExpiryDate(expiryDateField.getText().trim());
                entry.setCvv(cvv);
                entry.setCardPin(pin);
                entry.setCardType((String) cardTypeCombo.getSelectedItem());
                entry.setNotes(notesArea.getText());
            }
        } finally {
            SecureWiper.wipe(cardNum);
            SecureWiper.wipe(cvv);
            SecureWiper.wipe(pin);
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
