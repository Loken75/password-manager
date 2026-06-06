package com.passwordmanager.ui.components;

import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * Circular avatar: a category-colored disc with the title's initial, or a favicon image clipped
 * to the circle. Source: docs/design-system/components.md (EntryCard avatar, 40px).
 */
public class Avatar extends JComponent {

    private final int size;
    private String initial = "?";
    private Color background = DesignTokens.categoryColor(null);
    private Image image;

    public Avatar(int size) {
        this.size = size;
        Dimension d = new Dimension(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    /** Configure from a title + optional category (color) and optional favicon image. */
    public Avatar set(String title, String category, Image favicon) {
        this.initial = (title == null || title.isBlank())
                ? "?" : title.trim().substring(0, 1).toUpperCase();
        this.background = DesignTokens.categoryColor(category != null ? category : title);
        this.image = favicon;
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            Ellipse2D circle = new Ellipse2D.Float(0, 0, size, size);
            if (image != null) {
                g2.setClip(circle);
                g2.drawImage(image, 0, 0, size, size, this);
            } else {
                g2.setColor(background);
                g2.fill(circle);
                g2.setColor(Color.WHITE);
                Font font = getFont().deriveFont(Font.BOLD, size * 0.42f);
                g2.setFont(font);
                FontRenderContext frc = g2.getFontRenderContext();
                Rectangle2D bounds = font.getStringBounds(initial, frc);
                float x = (float) ((size - bounds.getWidth()) / 2.0);
                float y = (float) ((size - bounds.getHeight()) / 2.0 - bounds.getY());
                g2.drawString(initial, x, y);
            }
        } finally {
            g2.dispose();
        }
    }
}
