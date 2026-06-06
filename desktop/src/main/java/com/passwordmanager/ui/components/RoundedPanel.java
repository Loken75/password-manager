package com.passwordmanager.ui.components;

import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A panel painted as a rounded, optionally outlined surface — the base for the design-system
 * card components. Antialiased; honors the active light/dark theme via {@link DesignTokens}.
 */
public class RoundedPanel extends JPanel {

    private int arc = DesignTokens.RADIUS_CARD;
    private Color fillColor;
    private Color borderColor;
    private boolean drawBorder = true;

    public RoundedPanel() {
        setOpaque(false);
    }

    public RoundedPanel setArc(int arc) {
        this.arc = arc;
        return this;
    }

    public RoundedPanel setFillColor(Color color) {
        this.fillColor = color;
        return this;
    }

    public RoundedPanel setBorderColor(Color color) {
        this.borderColor = color;
        return this;
    }

    public RoundedPanel setDrawBorder(boolean drawBorder) {
        this.drawBorder = drawBorder;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = fillColor != null ? fillColor : DesignTokens.surfaceContainer();
            int w = getWidth();
            int h = getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            if (drawBorder) {
                g2.setColor(borderColor != null ? borderColor : DesignTokens.outline());
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
