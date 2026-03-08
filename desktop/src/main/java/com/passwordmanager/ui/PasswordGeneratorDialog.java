package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordGenerator;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.util.SecureWiper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;

/**
 * Dialog for generating secure passwords.
 */
public class PasswordGeneratorDialog extends JDialog {
    private final LanguageManager lang = LanguageManager.getInstance();
    private JPasswordField resultField;
    private JSpinner lengthSpinner;
    private JCheckBox upperCheck, lowerCheck, digitsCheck, specialCheck, ambiguousCheck;
    private JProgressBar strengthBar;
    private JLabel strengthLabel;
    private char[] generatedPassword;
    private int clipboardClearSeconds = 30;
    private Timer clipboardTimer;

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
        resultField = new JPasswordField(25);
        resultField.setEchoChar((char) 0); // Show password in clear by default
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
        optPanel.setBorder(BorderFactory.createTitledBorder(lang.getString("generator.options")));

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
            char[] pwd = resultField.getPassword();
            if (pwd != null && pwd.length > 0) {
                SecureClipboard.copyPassword(pwd);
                SecureWiper.wipe(pwd);
                // Cancel any previous clipboard clear timer
                if (clipboardTimer != null) {
                    clipboardTimer.stop();
                }
                // Auto-clear clipboard after delay (Swing Timer runs on EDT)
                clipboardTimer = new Timer(clipboardClearSeconds * 1000, evt ->
                    SecureClipboard.clear());
                clipboardTimer.setRepeats(false);
                clipboardTimer.start();
            }
        });
        cancelBtn.addActionListener(e -> {
            generatedPassword = null;
            cancelClipboardTimer();
            dispose();
        });
        useBtn.addActionListener(e -> {
            // generatedPassword is already set by doGenerate(), no need to read from UI field
            cancelClipboardTimer();
            dispose();
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                cancelClipboardTimer();
            }
        });

        doGenerate();

        pack();
        setMinimumSize(new Dimension(450, 420));
        setLocationRelativeTo(getOwner());
    }

    private void doGenerate() {
        int length = (Integer) lengthSpinner.getValue();
        // Wipe previous generated password before generating new one
        SecureWiper.wipe(generatedPassword);
        generatedPassword = PasswordGenerator.generate(length,
            upperCheck.isSelected(), lowerCheck.isSelected(),
            digitsCheck.isSelected(), specialCheck.isSelected(),
            ambiguousCheck.isSelected());
        // JPasswordField stores internally as char[] — setText creates a temporary String
        // but the field's Document model stores char[]. This is the minimal conversion path.
        resultField.setText(new String(generatedPassword));
        StrengthBarHelper.update(strengthBar, strengthLabel, generatedPassword);
    }

    private void cancelClipboardTimer() {
        if (clipboardTimer != null) {
            clipboardTimer.stop();
            clipboardTimer = null;
        }
    }

    public char[] getGeneratedPassword() { return generatedPassword; }
}
