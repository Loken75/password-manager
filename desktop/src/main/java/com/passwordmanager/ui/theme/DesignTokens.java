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

    // Accent — steel blue ramp (source: tokens.json primitive.color.accent), "Calme & confiance"
    public static final Color ACCENT = new Color(0x3B66C9);
    private static final Color ACCENT_20 = new Color(0x1E3A6E);
    private static final Color ACCENT_40 = new Color(0x3B66C9);
    private static final Color ACCENT_60 = new Color(0x6E92E8);
    private static final Color ACCENT_90 = new Color(0xD7E2F7);
    private static final Color ACCENT_95 = new Color(0xEAF0FB);

    // Neutrals (subset used by custom-painted components)
    private static final Color NEUTRAL_0 = new Color(0xFFFFFF);
    private static final Color NEUTRAL_5 = new Color(0xF7F7FB);
    private static final Color NEUTRAL_10 = new Color(0xEEEEF4);
    private static final Color NEUTRAL_20 = new Color(0xD9D9E3);
    private static final Color NEUTRAL_70 = new Color(0x3A3A44);
    private static final Color NEUTRAL_80 = new Color(0x2A2A33);
    private static final Color NEUTRAL_90 = new Color(0x1A1A20);
    private static final Color NEUTRAL_95 = new Color(0x141418);
    private static final Color NEUTRAL_100 = new Color(0x111116);

    /** Theme accent (light = accent.40, dark = accent.60). */
    public static Color accent() { return isDark() ? ACCENT_60 : ACCENT_40; }
    /** Readable foreground on {@link #accent()}. */
    public static Color onAccent() { return isDark() ? NEUTRAL_100 : NEUTRAL_0; }
    /** Tonal accent surface for secondary (tonal) buttons/chips. */
    public static Color accentContainer() { return isDark() ? ACCENT_20 : ACCENT_95; }
    public static Color onAccentContainer() { return isDark() ? ACCENT_90 : ACCENT_20; }

    /** Base surface (raised elements like the selected segment). */
    public static Color surface() { return isDark() ? NEUTRAL_95 : NEUTRAL_0; }
    /** Elevated card / container surface. */
    public static Color surfaceContainer() { return isDark() ? NEUTRAL_90 : NEUTRAL_5; }
    /** Hairline outline for cards/dividers. */
    public static Color outline() { return isDark() ? NEUTRAL_70 : NEUTRAL_20; }
    /** Subtle (hover/track) surface. */
    public static Color surfaceSubtle() { return isDark() ? NEUTRAL_80 : NEUTRAL_10; }
    public static Color onSurfaceFaint() { return isDark() ? NEUTRAL_20 : NEUTRAL_70; }

    // Security status primitives (source: tokens.json primitive.color.{green,amber,red,blue}), calm tones
    private static final Color GREEN_50 = new Color(0x2F9E68);
    private static final Color GREEN_60 = new Color(0x46B883);
    private static final Color AMBER_50 = new Color(0xB97D08);
    private static final Color AMBER_60 = new Color(0xE0A23A);
    private static final Color RED_50 = new Color(0xCF4747);
    private static final Color RED_60 = new Color(0xE36464);
    private static final Color BLUE_50 = new Color(0x2F6FB0);
    private static final Color BLUE_60 = new Color(0x5E93D6);
    private static final Color GOLD_50 = new Color(0xC79A33);
    private static final Color GOLD_60 = new Color(0xD9B95B);

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

    /** Favorite accent (gold), distinct from status colors, theme-aware. */
    public static Color favorite() {
        return isDark() ? GOLD_60 : GOLD_50;
    }

    /** Soft badge background: the status color at low opacity (tint over the card surface). */
    public static Color softTint(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), isDark() ? 46 : 34);
    }

    // Neutral avatar (color reserved for security signals)
    public static Color avatarBackground() { return surfaceSubtle(); }
    public static Color avatarForeground() { return onSurfaceFaint(); }

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
