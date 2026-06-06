package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordGenerator;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.components.RoundedPanel;
import com.passwordmanager.ui.components.StrengthMeter;
import com.passwordmanager.ui.theme.DesignTokens;
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
    private StrengthMeter strengthMeter;
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
        setLayout(new BorderLayout());
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(
            DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_LG, DesignTokens.SPACE_XL));

        // Result card (mono value + regenerate/copy)
        RoundedPanel resultPanel = new RoundedPanel();
        resultPanel.setArc(DesignTokens.RADIUS_CARD);
        resultPanel.setFillColor(DesignTokens.surfaceSubtle());
        resultPanel.setDrawBorder(false);
        resultPanel.setLayout(new BorderLayout(DesignTokens.SPACE_SM, 0));
        resultPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 12));
        resultPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        resultField = new JPasswordField();
        resultField.setEchoChar((char) 0);
        resultField.setEditable(false);
        resultField.setBorder(null);
        resultField.setOpaque(false);
        resultField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        resultPanel.add(resultField, BorderLayout.CENTER);
        JPanel resultButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, 0));
        resultButtons.setOpaque(false);
        JButton refreshBtn = Buttons.tonal(lang.getString("generator.generate"));
        JButton copyBtn = new JButton(lang.getString("generator.copy"));
        resultButtons.add(refreshBtn);
        resultButtons.add(copyBtn);
        resultPanel.add(resultButtons, BorderLayout.EAST);
        mainPanel.add(resultPanel);
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_MD));

        // Strength meter
        strengthMeter = new StrengthMeter();
        strengthMeter.setAlignmentX(Component.LEFT_ALIGNMENT);
        strengthMeter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        mainPanel.add(strengthMeter);
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        // Length
        JPanel lenPanel = new JPanel(new BorderLayout());
        lenPanel.setOpaque(false);
        lenPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        lenPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JLabel lenLabel = new JLabel(lang.getString("generator.length"));
        lenPanel.add(lenLabel, BorderLayout.WEST);
        lengthSpinner = new JSpinner(new SpinnerNumberModel(16, 8, 128, 1));
        JPanel spinnerWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        spinnerWrap.setOpaque(false);
        spinnerWrap.add(lengthSpinner);
        lenPanel.add(spinnerWrap, BorderLayout.EAST);
        mainPanel.add(lenPanel);
        mainPanel.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));

        upperCheck = new JCheckBox(lang.getString("generator.uppercase"), true);
        lowerCheck = new JCheckBox(lang.getString("generator.lowercase"), true);
        digitsCheck = new JCheckBox(lang.getString("generator.digits"), true);
        specialCheck = new JCheckBox(lang.getString("generator.special"), true);
        ambiguousCheck = new JCheckBox(lang.getString("generator.exclude_ambiguous"), false);
        for (JCheckBox cb : new JCheckBox[]{upperCheck, lowerCheck, digitsCheck, specialCheck, ambiguousCheck}) {
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            mainPanel.add(cb);
        }

        add(mainPanel, BorderLayout.CENTER);

        // Bottom buttons (ghost cancel + primary use)
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, DesignTokens.SPACE_SM, DesignTokens.SPACE_MD));
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DesignTokens.outline()));
        JButton cancelBtn = new JButton(lang.getString("common.cancel"));
        JButton useBtn = Buttons.primary(lang.getString("generator.use"));
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
        setMinimumSize(new Dimension(470, 440));
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
        strengthMeter.update(generatedPassword);
    }

    private void cancelClipboardTimer() {
        if (clipboardTimer != null) {
            clipboardTimer.stop();
            clipboardTimer = null;
        }
    }

    public char[] getGeneratedPassword() { return generatedPassword; }
}
