package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordGenerator;
import com.passwordmanager.i18n.LanguageManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Dialog for generating secure passwords.
 */
public class PasswordGeneratorDialog extends JDialog {
    private final LanguageManager lang = LanguageManager.getInstance();
    private JTextField resultField;
    private JSpinner lengthSpinner;
    private JCheckBox upperCheck, lowerCheck, digitsCheck, specialCheck, ambiguousCheck;
    private JProgressBar strengthBar;
    private JLabel strengthLabel;
    private char[] generatedPassword;

    public PasswordGeneratorDialog(Dialog owner) {
        super(owner, LanguageManager.getInstance().getString("generator.title"), true);
        initComponents();
    }

    public PasswordGeneratorDialog(Frame owner) {
        super(owner, LanguageManager.getInstance().getString("generator.title"), true);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));

        // Result field
        JPanel resultPanel = new JPanel(new BorderLayout(5, 0));
        resultPanel.setBorder(BorderFactory.createTitledBorder(lang.getString("generator.title")));
        resultField = new JTextField(25);
        resultField.setEditable(false);
        resultField.setFont(new Font("Monospaced", Font.BOLD, 14));
        resultPanel.add(resultField, BorderLayout.CENTER);

        JPanel resultButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        JButton copyBtn = new JButton(lang.getString("generator.copy"));
        JButton refreshBtn = new JButton(lang.getString("generator.generate"));
        resultButtons.add(copyBtn);
        resultButtons.add(refreshBtn);
        resultPanel.add(resultButtons, BorderLayout.EAST);
        mainPanel.add(resultPanel);
        mainPanel.add(Box.createVerticalStrut(5));

        // Strength
        JPanel strengthPanel = new JPanel(new BorderLayout(5, 0));
        strengthBar = new JProgressBar(0, 100);
        strengthBar.setStringPainted(true);
        strengthLabel = new JLabel("  ");
        strengthPanel.add(new JLabel(lang.getString("strength.label") + " : "), BorderLayout.WEST);
        strengthPanel.add(strengthBar, BorderLayout.CENTER);
        strengthPanel.add(strengthLabel, BorderLayout.EAST);
        mainPanel.add(strengthPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Options
        JPanel optPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        optPanel.setBorder(BorderFactory.createTitledBorder("Options"));

        JPanel lenPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lenPanel.add(new JLabel(lang.getString("generator.length") + " :"));
        lengthSpinner = new JSpinner(new SpinnerNumberModel(16, 8, 128, 1));
        lenPanel.add(lengthSpinner);
        optPanel.add(lenPanel);

        upperCheck = new JCheckBox(lang.getString("generator.uppercase"), true);
        lowerCheck = new JCheckBox(lang.getString("generator.lowercase"), true);
        digitsCheck = new JCheckBox(lang.getString("generator.digits"), true);
        specialCheck = new JCheckBox(lang.getString("generator.special"), true);
        ambiguousCheck = new JCheckBox(lang.getString("generator.exclude_ambiguous"), false);

        optPanel.add(upperCheck);
        optPanel.add(lowerCheck);
        optPanel.add(digitsCheck);
        optPanel.add(specialCheck);
        optPanel.add(ambiguousCheck);
        mainPanel.add(optPanel);

        add(mainPanel, BorderLayout.CENTER);

        // Bottom buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton(lang.getString("common.cancel"));
        JButton useBtn = new JButton(lang.getString("generator.use"));
        btnPanel.add(cancelBtn);
        btnPanel.add(useBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // Actions
        refreshBtn.addActionListener(e -> doGenerate());
        copyBtn.addActionListener(e -> {
            if (resultField.getText().length() > 0) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(resultField.getText()), null);
            }
        });
        cancelBtn.addActionListener(e -> {
            generatedPassword = null;
            dispose();
        });
        useBtn.addActionListener(e -> {
            generatedPassword = resultField.getText().toCharArray();
            dispose();
        });

        doGenerate();

        pack();
        setMinimumSize(new Dimension(450, 420));
        setLocationRelativeTo(getOwner());
    }

    private void doGenerate() {
        int length = (Integer) lengthSpinner.getValue();
        char[] pwd = PasswordGenerator.generate(length,
            upperCheck.isSelected(), lowerCheck.isSelected(),
            digitsCheck.isSelected(), specialCheck.isSelected(),
            ambiguousCheck.isSelected());
        resultField.setText(new String(pwd));
        StrengthBarHelper.update(strengthBar, strengthLabel, pwd);
    }

    public char[] getGeneratedPassword() { return generatedPassword; }
}
