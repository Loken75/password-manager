package com.passwordmanager.ui.components;

import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JToggleButton;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.util.function.IntConsumer;

/**
 * A segmented control (iOS/Material "segmented buttons") used to switch between the three entry
 * types — surfacing Passwords / Applications / SSH keys consistently on both clients.
 * Source: docs/design-system/components.md (SegmentedTabs).
 */
public class SegmentedControl extends RoundedPanel {

    private final ButtonGroup group = new ButtonGroup();
    private IntConsumer onSelect;

    public SegmentedControl(String... segments) {
        setArc(DesignTokens.RADIUS_BUTTON);
        setFillColor(DesignTokens.surfaceSubtle());
        setDrawBorder(false);
        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2));

        for (int i = 0; i < segments.length; i++) {
            final int index = i;
            JToggleButton tab = new JToggleButton(segments[i]);
            tab.setFocusPainted(false);
            tab.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tab.addItemListener(e -> {
                applyStyle(tab);
                if (tab.isSelected() && onSelect != null) {
                    onSelect.accept(index);
                }
            });
            group.add(tab);
            add(tab);
            if (i == 0) {
                tab.setSelected(true);
                applyStyle(tab);
            }
        }
    }

    private void applyStyle(AbstractButton tab) {
        if (tab.isSelected()) {
            // Sober "raised card" selected segment with accent text (calm, not a loud fill).
            tab.setContentAreaFilled(true);
            tab.setOpaque(true);
            tab.setBackground(DesignTokens.surface());
            tab.setForeground(DesignTokens.accent());
        } else {
            tab.setContentAreaFilled(false);
            tab.setOpaque(false);
            tab.setForeground(DesignTokens.onSurfaceFaint());
        }
    }

    public SegmentedControl onSelect(IntConsumer listener) {
        this.onSelect = listener;
        return this;
    }
}
