package com.passwordmanager.ui.components;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Tiny vector icons for the toolbar controls (sort / filter), painted with the host component's
 * foreground colour so they adapt to theme and selection state. Avoids depending on an icon font
 * or SVG framework — mirrors the Material "Sort" and "FilterList/funnel" glyphs used on Android.
 */
public final class ControlIcon implements Icon {
    public enum Kind { SORT, FILTER }

    private final Kind kind;
    private final int size;

    public ControlIcon(Kind kind) { this(kind, 18); }

    public ControlIcon(Kind kind, int size) {
        this.kind = kind;
        this.size = size;
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c.getForeground());
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int s = size;
        if (kind == Kind.SORT) {
            int left = x + 2;
            int maxLen = s - 4;
            int[] lens = { maxLen, Math.round(maxLen * 0.66f), Math.round(maxLen * 0.33f) };
            int[] ys = { y + s / 4, y + s / 2, y + 3 * s / 4 };
            for (int i = 0; i < 3; i++) g.drawLine(left, ys[i], left + lens[i], ys[i]);
        } else {
            int pad = 3;
            int top = y + pad;
            int leftX = x + pad;
            int rightX = x + s - pad;
            int midX = x + s / 2;
            int neckY = y + s / 2;
            int botY = y + s - pad;
            g.drawLine(leftX, top, rightX, top);          // funnel mouth
            g.drawLine(leftX, top, midX - 1, neckY);       // left slope
            g.drawLine(rightX, top, midX + 1, neckY);      // right slope
            g.drawLine(midX - 1, neckY, midX - 1, botY);   // stem (left edge)
            g.drawLine(midX + 1, neckY, midX + 1, botY);   // stem (right edge)
            g.drawLine(midX - 1, botY, midX + 1, botY);    // stem foot
        }
        g.dispose();
    }
}
