package com.passwordmanager.ui;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.i18n.LanguageManager;

import javax.swing.*;
import java.awt.*;

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
        switch (strength) {
            case WEAK:
                bar.setForeground(Color.RED);
                label.setText(lang.getString("strength.weak"));
                break;
            case MEDIUM:
                bar.setForeground(Color.ORANGE);
                label.setText(lang.getString("strength.medium"));
                break;
            case STRONG:
                bar.setForeground(new Color(0, 180, 0));
                label.setText(lang.getString("strength.strong"));
                break;
            case VERY_STRONG:
                bar.setForeground(new Color(0, 100, 200));
                label.setText(lang.getString("strength.very_strong"));
                break;
        }
    }
}
