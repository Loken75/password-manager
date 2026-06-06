package com.passwordmanager.ui.components;

import com.passwordmanager.ui.SecureClipboard;
import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * Secret value field: monospace, masked by default, with an explicit reveal (auto re-masks after
 * 30s) and a copy-to-clipboard action wired to {@link SecureClipboard}. Source:
 * docs/design-system/components.md (SecretField). The value stays in the JPasswordField's char[]
 * buffer; callers read it via {@link #getValue()} and wipe.
 */
public class SecretFieldPanel extends JPanel {

    private static final char MASK = '•';
    private static final int AUTO_HIDE_MS = 30_000;

    private final JPasswordField field = new JPasswordField();
    private final JToggleButton revealButton = new JToggleButton("Afficher");
    private final JButton copyButton = new JButton("Copier");
    private final Timer autoHide;

    public SecretFieldPanel() {
        super(new BorderLayout(DesignTokens.SPACE_SM, 0));
        setOpaque(false);

        field.setEchoChar(MASK);
        field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        field.setEditable(false);

        revealButton.setToolTipText("Afficher / masquer");
        revealButton.getAccessibleContext().setAccessibleName("Afficher ou masquer le secret");
        revealButton.setFocusPainted(false);
        revealButton.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        revealButton.addActionListener(e -> setRevealed(revealButton.isSelected()));

        copyButton.setToolTipText("Copier");
        copyButton.getAccessibleContext().setAccessibleName("Copier le secret");
        copyButton.setFocusPainted(false);
        copyButton.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        copyButton.addActionListener(e -> SecureClipboard.copyPassword(field.getPassword()));

        JPanel actions = new JPanel(new BorderLayout(DesignTokens.SPACE_XS, 0));
        actions.setOpaque(false);
        actions.add(revealButton, BorderLayout.WEST);
        actions.add(copyButton, BorderLayout.EAST);

        add(field, BorderLayout.CENTER);
        add(actions, BorderLayout.EAST);

        autoHide = new Timer(AUTO_HIDE_MS, e -> setRevealed(false));
        autoHide.setRepeats(false);
    }

    public void setValue(char[] value) {
        field.setText(value == null ? "" : new String(value));
    }

    public char[] getValue() {
        return field.getPassword();
    }

    private void setRevealed(boolean revealed) {
        field.setEchoChar(revealed ? (char) 0 : MASK);
        revealButton.setSelected(revealed);
        revealButton.setText(revealed ? "Masquer" : "Afficher");
        if (revealed) {
            autoHide.restart();
        } else {
            autoHide.stop();
        }
    }
}
