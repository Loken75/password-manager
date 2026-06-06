package com.passwordmanager.ui.components;

import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/**
 * Bento dashboard tile: a rounded surface with a caption, a large value and an optional detail
 * line. Used for the vault-home dashboard (health score, HIBP alerts, activity).
 * Source: docs/design-system/components.md (BentoCard).
 */
public class BentoCard extends RoundedPanel {

    public BentoCard(String caption, String value, String detail, Color valueColor) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(
                DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL));

        JLabel captionLabel = new JLabel(caption.toUpperCase());
        captionLabel.setFont(captionLabel.getFont().deriveFont(Font.BOLD, 11f));
        captionLabel.setForeground(DesignTokens.onSurfaceFaint());
        captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 30f));
        if (valueColor != null) {
            valueLabel.setForeground(valueColor);
        }
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        add(captionLabel);
        add(javax.swing.Box.createVerticalStrut(DesignTokens.SPACE_SM));
        add(valueLabel);

        if (detail != null && !detail.isBlank()) {
            JLabel detailLabel = new JLabel(detail);
            detailLabel.setForeground(DesignTokens.onSurfaceFaint());
            detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(javax.swing.Box.createVerticalStrut(DesignTokens.SPACE_XS));
            add(detailLabel);
        }
    }
}
