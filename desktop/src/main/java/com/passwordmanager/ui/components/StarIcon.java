package com.passwordmanager.ui.components;

import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * A vector five-point star (filled when favorite, outlined otherwise) — drawn rather than using a
 * glyph so it renders consistently across platforms/fonts. Source: docs/design-system/components.md.
 */
public class StarIcon extends JComponent {

    private final int size;
    private boolean favorite;

    public StarIcon(int size, boolean favorite) {
        this.size = size;
        this.favorite = favorite;
        Dimension d = new Dimension(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = size / 2.0;
            double cy = size / 2.0;
            double outer = size / 2.0 - 1;
            double inner = outer * 0.42;
            Path2D path = new Path2D.Double();
            for (int i = 0; i < 10; i++) {
                double r = (i % 2 == 0) ? outer : inner;
                double angle = -Math.PI / 2 + i * Math.PI / 5;
                double x = cx + r * Math.cos(angle);
                double y = cy + r * Math.sin(angle);
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.closePath();
            if (favorite) {
                g2.setColor(DesignTokens.favorite());
                g2.fill(path);
            } else {
                g2.setColor(DesignTokens.onSurfaceFaint());
                g2.draw(path);
            }
        } finally {
            g2.dispose();
        }
    }
}
