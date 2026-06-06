package com.passwordmanager.ui.components;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength;
import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

/**
 * Password strength meter: a rounded track + colored fill proportional to the 0–100 score, with
 * the level label. Source: docs/design-system/components.md (StrengthMeter). Pairs the color with
 * a text label (color is never the sole signal).
 */
public class StrengthMeter extends JComponent {

    private static final int BAR_HEIGHT = 6;
    private static final int LABEL_GAP = 6;

    private int score = 0;
    private Strength strength = Strength.WEAK;
    private String label = "";

    public StrengthMeter update(char[] password) {
        if (password == null || password.length == 0) {
            this.score = 0;
            this.label = "";
            repaint();
            return this;
        }
        this.score = PasswordStrengthAnalyzer.getScore(password);
        this.strength = PasswordStrengthAnalyzer.analyze(password);
        LanguageManager lang = LanguageManager.getInstance();
        switch (strength) {
            case WEAK:        this.label = lang.getString("strength.weak"); break;
            case MEDIUM:      this.label = lang.getString("strength.medium"); break;
            case STRONG:      this.label = lang.getString("strength.strong"); break;
            case VERY_STRONG: this.label = lang.getString("strength.very_strong"); break;
        }
        repaint();
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(160, BAR_HEIGHT + LABEL_GAP + 16);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            // Track
            g2.setColor(DesignTokens.surfaceSubtle());
            g2.fillRoundRect(0, 0, w, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
            // Fill
            if (score > 0) {
                int fillW = Math.max(BAR_HEIGHT, (int) (w * (score / 100.0)));
                g2.setColor(DesignTokens.forStrength(strength));
                g2.fillRoundRect(0, 0, fillW, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
            }
            // Label
            if (!label.isEmpty()) {
                Font f = getFont().deriveFont(Font.PLAIN, 11f);
                g2.setFont(f);
                g2.setColor(DesignTokens.onSurfaceFaint());
                FontRenderContext frc = g2.getFontRenderContext();
                Rectangle2D b = f.getStringBounds(label, frc);
                float y = BAR_HEIGHT + LABEL_GAP + (float) -b.getY();
                g2.drawString(label, 0, y);
            }
        } finally {
            g2.dispose();
        }
    }
}
