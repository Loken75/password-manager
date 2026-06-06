package com.passwordmanager.ui.components;

import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Cursor;

/**
 * Factory for the design-system button variants. Corner radius and focus ring come from the
 * FlatLaf theme; this only sets the color intent. Source: docs/design-system/components.md.
 */
public final class Buttons {
    private Buttons() {}

    /** Filled accent button for the primary action. */
    public static JButton primary(String text) {
        JButton b = new JButton(text);
        b.setBackground(DesignTokens.accent());
        b.setForeground(DesignTokens.onAccent());
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        return b;
    }

    /** Tonal (secondary) button on an accent-container surface. */
    public static JButton tonal(String text) {
        JButton b = new JButton(text);
        b.setBackground(DesignTokens.accentContainer());
        b.setForeground(DesignTokens.onAccentContainer());
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        return b;
    }
}
