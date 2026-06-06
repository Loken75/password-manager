package com.passwordmanager.ui.components;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer;
import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

/**
 * A small rounded pill conveying a security state (fill color + label). Color is never the sole
 * signal — the level label is always shown. Source: docs/design-system/components.md (StatusBadge).
 */
public class StatusBadge extends JComponent {

    private static final Insets PAD = new Insets(2, 10, 2, 10);

    private String text = "";
    private Color fill = DesignTokens.statusMedium();

    public StatusBadge() {
        setFont(getFont());
    }

    public StatusBadge setStrength(PasswordStrengthAnalyzer.Strength strength, String label) {
        this.fill = DesignTokens.forStrength(strength);
        this.text = label;
        revalidate();
        repaint();
        return this;
    }

    public StatusBadge set(Color fill, String label) {
        this.fill = fill;
        this.text = label;
        revalidate();
        repaint();
        return this;
    }

    private Font badgeFont() {
        return getFont().deriveFont(Font.BOLD, 11f);
    }

    @Override
    public Dimension getPreferredSize() {
        Font f = badgeFont();
        FontRenderContext frc = new FontRenderContext(null, true, true);
        Rectangle2D b = f.getStringBounds(text, frc);
        int w = (int) Math.ceil(b.getWidth()) + PAD.left + PAD.right;
        int h = (int) Math.ceil(b.getHeight()) + PAD.top + PAD.bottom;
        return new Dimension(w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            g2.setColor(contrastingText(fill));
            g2.setFont(badgeFont());
            FontRenderContext frc = g2.getFontRenderContext();
            Rectangle2D b = badgeFont().getStringBounds(text, frc);
            float x = (float) ((w - b.getWidth()) / 2.0);
            float y = (float) ((h - b.getHeight()) / 2.0 - b.getY());
            g2.drawString(text, x, y);
        } finally {
            g2.dispose();
        }
    }

    /** Pick white or near-black text for adequate contrast on the fill. */
    private static Color contrastingText(Color bg) {
        double luminance = (0.2126 * bg.getRed() + 0.7152 * bg.getGreen() + 0.0722 * bg.getBlue()) / 255.0;
        return luminance > 0.55 ? new Color(0x111116) : Color.WHITE;
    }
}
