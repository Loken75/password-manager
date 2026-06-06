package com.passwordmanager.ui.components;

import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength;
import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;

/**
 * The unified list card for an entry: a colored strength stripe, a category/favicon avatar, a
 * title + subtitle, an optional security badge and a favorite star. Replaces the dense JTable rows.
 * Source: docs/design-system/components.md (EntryCard).
 */
public class EntryCardPanel extends RoundedPanel {

    private static final int STRIPE_WIDTH = 5;

    private Strength strength;

    public EntryCardPanel(String title, String subtitle, String category, Image favicon,
                          Strength strength, String strengthLabel, boolean favorite) {
        this.strength = strength;
        setLayout(new BorderLayout(DesignTokens.SPACE_MD, 0));
        setBorder(BorderFactory.createEmptyBorder(
                DesignTokens.SPACE_MD, DesignTokens.SPACE_MD + STRIPE_WIDTH,
                DesignTokens.SPACE_MD, DesignTokens.SPACE_MD));

        Avatar avatar = new Avatar(40);
        avatar.set(title, category, favicon);
        JPanel avatarWrap = new JPanel(new BorderLayout());
        avatarWrap.setOpaque(false);
        avatarWrap.add(avatar, BorderLayout.CENTER);
        add(avatarWrap, BorderLayout.WEST);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(0, DesignTokens.SPACE_MD, 0, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(titleLabel);

        if (subtitle != null && !subtitle.isBlank()) {
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setForeground(DesignTokens.onSurfaceFaint());
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            center.add(Box.createVerticalStrut(2));
            center.add(subtitleLabel);
        }
        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setOpaque(false);
        centerWrap.add(center, BorderLayout.CENTER);
        add(centerWrap, BorderLayout.CENTER);

        JPanel east = new JPanel();
        east.setOpaque(false);
        east.setLayout(new BoxLayout(east, BoxLayout.X_AXIS));
        // Discreet category label (secondary info: muted, no chip background)
        if (category != null && !category.isBlank()) {
            JLabel cat = new JLabel(category);
            cat.setFont(cat.getFont().deriveFont(11f));
            cat.setForeground(DesignTokens.onSurfaceFaint());
            east.add(cat);
            east.add(Box.createHorizontalStrut(DesignTokens.SPACE_MD));
        }
        if (strength != null) {
            StatusBadge badge = new StatusBadge();
            badge.setStrength(strength, strengthLabel);
            east.add(badge);
            east.add(Box.createHorizontalStrut(DesignTokens.SPACE_MD));
        }
        east.add(new StarIcon(18, favorite));
        JPanel eastWrap = new JPanel(new BorderLayout());
        eastWrap.setOpaque(false);
        eastWrap.add(east, BorderLayout.CENTER);
        add(eastWrap, BorderLayout.EAST);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (strength == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(0, 0, STRIPE_WIDTH, getHeight());
            g2.setColor(DesignTokens.forStrength(strength));
            int arc = DesignTokens.RADIUS_CARD;
            g2.fillRoundRect(0, 0, STRIPE_WIDTH + arc, getHeight() - 1, arc, arc);
        } finally {
            g2.dispose();
        }
    }
}
