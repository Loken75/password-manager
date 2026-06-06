package com.passwordmanager.ui.theme;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * Design tokens for the desktop client. Source of truth: docs/design-system/tokens.json.
 *
 * <p>FlatLaf-themable values (accent, corner radii, surfaces, focus) live in the FlatLaf
 * {@code .properties} under {@code resources/com/passwordmanager/ui/theme/}. This class holds
 * the values FlatLaf does not own: security-status colors, the favorite accent and the category
 * palette. Mirror any change here in {@code tokens.json} (and vice-versa).
 *
 * <p>Light/dark variants are resolved from FlatLaf's {@code laf.dark} flag so callers don't have
 * to track the active theme.
 */
public final class DesignTokens {
    private DesignTokens() {}

    // Accent — indigo (matches @accentColor in the FlatLaf properties)
    public static final Color ACCENT = new Color(0x4F46E5);

    // Security status primitives (source: tokens.json primitive.color.{green,amber,red,blue})
    private static final Color GREEN_50 = new Color(0x16A34A);
    private static final Color GREEN_60 = new Color(0x22C55E);
    private static final Color AMBER_50 = new Color(0xF59E0B);
    private static final Color AMBER_60 = new Color(0xFBBF24);
    private static final Color RED_50 = new Color(0xDC2626);
    private static final Color RED_60 = new Color(0xEF4444);
    private static final Color BLUE_50 = new Color(0x2563EB);
    private static final Color BLUE_60 = new Color(0x3B82F6);

    /** True when the active FlatLaf theme is dark. */
    public static boolean isDark() {
        return UIManager.getBoolean("laf.dark");
    }

    public static Color statusWeak()       { return isDark() ? RED_60 : RED_50; }
    public static Color statusMedium()     { return isDark() ? AMBER_60 : AMBER_50; }
    public static Color statusStrong()     { return isDark() ? GREEN_60 : GREEN_50; }
    public static Color statusVeryStrong() { return isDark() ? BLUE_60 : BLUE_50; }

    /** Fixed security-status color for a strength level (fill: bars/badges/avatars). */
    public static Color forStrength(PasswordStrengthAnalyzer.Strength strength) {
        switch (strength) {
            case WEAK:        return statusWeak();
            case MEDIUM:      return statusMedium();
            case STRONG:      return statusStrong();
            case VERY_STRONG: return statusVeryStrong();
            default:          return statusMedium();
        }
    }

    /** Favorite accent (amber), theme-aware. */
    public static Color favorite() {
        return isDark() ? AMBER_60 : AMBER_50;
    }

    // Category palette (source: tokens.json primitive.color.category) — deterministic by name.
    private static final Color[] CATEGORY = {
        new Color(0x6366F1), // indigo
        new Color(0x14B8A6), // teal
        new Color(0xF43F5E), // rose
        new Color(0xF59E0B), // amber
        new Color(0xA855F7), // violet
        new Color(0x06B6D4), // cyan
        new Color(0x84CC16), // lime
        new Color(0xF97316), // orange
        new Color(0x0EA5E9), // sky
        new Color(0x64748B), // slate
    };

    public static Color categoryColor(String category) {
        if (category == null || category.isBlank()) {
            return CATEGORY[CATEGORY.length - 1];
        }
        return CATEGORY[Math.abs(category.hashCode()) % CATEGORY.length];
    }

    // Spacing (px) — 8px grid (source: tokens.json primitive.space / semantic.shared.space)
    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;
    public static final int SPACE_XL = 24;
    public static final int SPACE_XXL = 32;

    // Corner radii (px)
    public static final int RADIUS_BUTTON = 12;
    public static final int RADIUS_CARD = 16;
}
