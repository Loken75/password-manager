package com.passwordmanager.ui.components;

import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JToggleButton;
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

    /** Borderless icon button (e.g. the sort control that opens a menu). */
    public static JButton icon(Icon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setForeground(DesignTokens.onSurfaceFaint());
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return b;
    }

    /** Borderless icon toggle button (e.g. the filter control that reveals the filter panel). */
    public static JToggleButton iconToggle(Icon icon, String tooltip) {
        JToggleButton b = new JToggleButton(icon);
        b.setToolTipText(tooltip);
        b.setForeground(DesignTokens.onSurfaceFaint());
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        // Tint when active so the toggle state is legible without a filled background.
        b.addChangeListener(e ->
            b.setForeground(b.isSelected() ? DesignTokens.accent() : DesignTokens.onSurfaceFaint()));
        return b;
    }
}
