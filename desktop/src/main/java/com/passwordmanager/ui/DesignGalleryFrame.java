package com.passwordmanager.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength;
import com.passwordmanager.ui.components.BentoCard;
import com.passwordmanager.ui.components.Buttons;
import com.passwordmanager.ui.components.EmptyStatePanel;
import com.passwordmanager.ui.components.EntryCardPanel;
import com.passwordmanager.ui.components.SecretFieldPanel;
import com.passwordmanager.ui.components.SegmentedControl;
import com.passwordmanager.ui.components.StatusBadge;
import com.passwordmanager.ui.components.StrengthMeter;
import com.passwordmanager.ui.theme.DesignTokens;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Standalone gallery showcasing the design-system components on desktop, in light and dark.
 * Not part of the shipped app — a visual reference / review surface for the redesign.
 * Run: java -cp &lt;classpath&gt; com.passwordmanager.ui.DesignGalleryFrame
 */
public class DesignGalleryFrame extends JFrame {

    public DesignGalleryFrame() {
        setTitle("Design System — Galerie");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(buildContent());
        setSize(820, 940);
        setLocationRelativeTo(null);
        // Review tool: keep it above other windows so it can be captured for design review.
        setAlwaysOnTop(true);
    }

    private JComponent buildContent() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(
                DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL, DesignTokens.SPACE_XL));

        // Header + theme toggle
        JPanel header = new JPanel(new BorderLayout());
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("Gestionnaire de Mots de Passe");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        header.add(title, BorderLayout.WEST);
        JToggleButton themeToggle = new JToggleButton(DesignTokens.isDark() ? "Thème clair" : "Thème sombre");
        themeToggle.setSelected(DesignTokens.isDark());
        themeToggle.addActionListener(e -> toggleTheme());
        header.add(themeToggle, BorderLayout.EAST);
        root.add(header);
        root.add(Box.createVerticalStrut(DesignTokens.SPACE_LG));

        // Segmented control
        root.add(section("Navigation des types"));
        SegmentedControl segments = new SegmentedControl("Mots de passe", "Applications", "Clés SSH");
        leftAlign(segments);
        root.add(segments);
        root.add(Box.createVerticalStrut(DesignTokens.SPACE_XL));

        // Bento dashboard
        root.add(section("Dashboard bento"));
        JPanel bento = new JPanel(new GridLayout(1, 3, DesignTokens.SPACE_LG, 0));
        bento.setAlignmentX(Component.LEFT_ALIGNMENT);
        bento.add(new BentoCard("Santé du coffre", "82", "12 forts · 3 faibles", DesignTokens.statusStrong()));
        bento.add(new BentoCard("Alertes HIBP", "2", "compromis · à vérifier", DesignTokens.statusWeak()));
        bento.add(new BentoCard("Activité", "+3", "récents · hier", null));
        root.add(bento);
        root.add(Box.createVerticalStrut(DesignTokens.SPACE_XL));

        // Entry cards
        root.add(section("Cartes d'entrée"));
        root.add(entryCard("github.com", "alice@example.com", "Travail", Strength.VERY_STRONG, "Très fort", true));
        root.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        root.add(entryCard("banque-x", "alice", "Banque", Strength.WEAK, "Faible", false));
        root.add(Box.createVerticalStrut(DesignTokens.SPACE_SM));
        root.add(entryCard("reddit", "a_l", "Réseaux sociaux", Strength.MEDIUM, "Moyen", false));
        root.add(Box.createVerticalStrut(DesignTokens.SPACE_XL));

        // Buttons + badges
        root.add(section("Boutons & badges"));
        JPanel controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, DesignTokens.SPACE_MD, 0));
        controls.setOpaque(false);
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        controls.add(Buttons.primary("Connexion"));
        controls.add(Buttons.tonal("Générer"));
        controls.add(badge(Strength.STRONG, "Fort"));
        controls.add(badge(Strength.MEDIUM, "Moyen"));
        controls.add(badge(Strength.WEAK, "Faible"));
        root.add(controls);
        root.add(Box.createVerticalStrut(DesignTokens.SPACE_XL));

        // Secret field + strength meter
        root.add(section("Champ secret & jauge de force"));
        SecretFieldPanel secret = new SecretFieldPanel();
        secret.setValue("Tr0ub4dour&3xpl0it!".toCharArray());
        secret.setMaximumSize(new Dimension(420, 40));
        leftAlign(secret);
        root.add(secret);
        root.add(Box.createVerticalStrut(DesignTokens.SPACE_MD));
        StrengthMeter meter = new StrengthMeter();
        meter.update("Tr0ub4dour&3xpl0it!".toCharArray());
        meter.setMaximumSize(new Dimension(420, 30));
        leftAlign(meter);
        root.add(meter);
        root.add(Box.createVerticalStrut(DesignTokens.SPACE_XL));

        // Empty state
        root.add(section("État vide"));
        EmptyStatePanel empty = new EmptyStatePanel("+", "Aucune entrée",
                "Ajoutez votre premier mot de passe pour commencer.", Buttons.primary("Nouvelle entrée"));
        leftAlign(empty);
        root.add(empty);

        JScrollPane scroll = new JScrollPane(root);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private void toggleTheme() {
        try {
            if (DesignTokens.isDark()) {
                FlatLightLaf.setup();
            } else {
                FlatDarkLaf.setup();
            }
            FlatLaf.updateUI();
        } catch (Exception ignored) {
            // ignore
        }
        setContentPane(buildContent());
        revalidate();
        repaint();
    }

    private JComponent section(String label) {
        JLabel l = new JLabel(label.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(DesignTokens.onSurfaceFaint());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, DesignTokens.SPACE_SM, 0));
        return l;
    }

    private EntryCardPanel entryCard(String title, String subtitle, String category,
                                     Strength strength, String label, boolean favorite) {
        EntryCardPanel card = new EntryCardPanel(title, subtitle, category, null, strength, label, favorite);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        return card;
    }

    private StatusBadge badge(Strength strength, String label) {
        StatusBadge b = new StatusBadge();
        b.setStrength(strength, label);
        return b;
    }

    private void leftAlign(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    public static void main(String[] args) {
        FlatLaf.registerCustomDefaultsSource("com.passwordmanager.ui.theme");
        boolean dark = args.length > 0 && "dark".equalsIgnoreCase(args[0]);
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        SwingUtilities.invokeLater(() -> new DesignGalleryFrame().setVisible(true));
    }
}
