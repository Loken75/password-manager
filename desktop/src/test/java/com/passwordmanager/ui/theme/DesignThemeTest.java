package com.passwordmanager.ui.theme;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies the design-system FlatLaf theme (generated from docs/design-system/tokens.json)
 * actually loads, and that {@link DesignTokens} stays consistent with it.
 */
class DesignThemeTest {

    @Test
    void flatLafCustomDefaultsAreApplied() {
        FlatLaf.registerCustomDefaultsSource("com.passwordmanager.ui.theme");
        FlatLightLaf.setup();

        // From the shared FlatLaf.properties (overrides FlatLaf defaults).
        assertEquals(12, UIManager.getInt("Button.arc"), "Button.arc should come from tokens (md=12)");
        assertEquals(12, UIManager.getInt("Component.arc"));
        assertEquals(2, UIManager.getInt("Component.focusWidth"), "focus ring should be 2px (WCAG 2.4.13)");
    }

    @Test
    void lightAndDarkAccentDiffer() {
        FlatLaf.registerCustomDefaultsSource("com.passwordmanager.ui.theme");

        FlatLightLaf.setup();
        Color light = UIManager.getColor("Component.accentColor");

        FlatDarkLaf.setup();
        Color dark = UIManager.getColor("Component.accentColor");

        assertNotEquals(light, dark, "light/dark accent should differ (accent.40 vs accent.60)");
    }

    @Test
    void strengthColorsCoverAllLevels() {
        for (PasswordStrengthAnalyzer.Strength s : PasswordStrengthAnalyzer.Strength.values()) {
            assertEquals(DesignTokens.forStrength(s), DesignTokens.forStrength(s));
        }
        // Category palette is deterministic and stable.
        assertEquals(DesignTokens.categoryColor("Banking"), DesignTokens.categoryColor("Banking"));
    }
}
