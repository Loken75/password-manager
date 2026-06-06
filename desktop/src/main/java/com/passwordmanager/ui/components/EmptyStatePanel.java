package com.passwordmanager.ui.components;

import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Font;

/**
 * Standardized empty state: large glyph + title + subtitle + optional action. Shared style across
 * empty lists, no-search-results and new-vault screens. Source: docs/design-system/components.md.
 */
public class EmptyStatePanel extends JPanel {

    public EmptyStatePanel(String glyph, String title, String subtitle, JButton action) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(
                DesignTokens.SPACE_XXL, DesignTokens.SPACE_XXL, DesignTokens.SPACE_XXL, DesignTokens.SPACE_XXL));

        JLabel glyphLabel = new JLabel(glyph);
        glyphLabel.setFont(glyphLabel.getFont().deriveFont(Font.PLAIN, 48f));
        glyphLabel.setForeground(DesignTokens.onSurfaceFaint());
        glyphLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(DesignTokens.onSurfaceFaint());
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        add(glyphLabel);
        add(javax.swing.Box.createVerticalStrut(DesignTokens.SPACE_MD));
        add(titleLabel);
        add(javax.swing.Box.createVerticalStrut(DesignTokens.SPACE_SM));
        add(subtitleLabel);

        if (action != null) {
            action.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(javax.swing.Box.createVerticalStrut(DesignTokens.SPACE_LG));
            add(action);
        }
    }
}
