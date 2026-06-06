package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.*;

/**
 * Shared helper to update a password strength bar and label.
 */
public class StrengthBarHelper {

    public static void update(JProgressBar bar, JLabel label, char[] password) {
        int score = PasswordStrengthAnalyzer.getScore(password);
        PasswordStrengthAnalyzer.Strength strength = PasswordStrengthAnalyzer.analyze(password);
        applyStrength(bar, label, score, strength);
    }

    public static void update(JProgressBar bar, JLabel label, String password) {
        int score = PasswordStrengthAnalyzer.getScore(password);
        PasswordStrengthAnalyzer.Strength strength = PasswordStrengthAnalyzer.analyze(password);
        applyStrength(bar, label, score, strength);
    }

    private static void applyStrength(JProgressBar bar, JLabel label,
                                       int score, PasswordStrengthAnalyzer.Strength strength) {
        LanguageManager lang = LanguageManager.getInstance();
        bar.setValue(score);
        bar.setForeground(DesignTokens.forStrength(strength));
        switch (strength) {
            case WEAK:
                label.setText(lang.getString("strength.weak"));
                break;
            case MEDIUM:
                label.setText(lang.getString("strength.medium"));
                break;
            case STRONG:
                label.setText(lang.getString("strength.strong"));
                break;
            case VERY_STRONG:
                label.setText(lang.getString("strength.very_strong"));
                break;
        }
    }
}
