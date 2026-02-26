package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.sync.EntryMerger;
import com.passwordmanager.vault.VaultEntry;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for resolving entry-level sync conflicts.
 * Shows local vs remote versions side-by-side with radio button selection.
 */
public class ConflictResolutionDialog extends JDialog {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final List<EntryMerger.Conflict> conflicts;
    private final List<Boolean> keepLocal;
    private boolean confirmed = false;

    public ConflictResolutionDialog(Frame owner, List<EntryMerger.Conflict> conflicts) {
        super(owner, LanguageManager.getInstance().getString("sync.merge.conflicts"), true);
        this.conflicts = conflicts;
        this.keepLocal = new ArrayList<>();
        for (int i = 0; i < conflicts.size(); i++) {
            keepLocal.add(true);
        }
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setSize(600, 500);
        setLocationRelativeTo(getOwner());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        for (int i = 0; i < conflicts.size(); i++) {
            EntryMerger.Conflict conflict = conflicts.get(i);
            content.add(createConflictPanel(conflict, i));
            content.add(Box.createVerticalStrut(10));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(scroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton resolveBtn = new JButton(lang.getString("sync.merge.resolve"));
        resolveBtn.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        JButton cancelBtn = new JButton(lang.getString("common.cancel"));
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(resolveBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private JPanel createConflictPanel(EntryMerger.Conflict conflict, int index) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
            conflict.getLocalEntry().getTitle() != null
                ? conflict.getLocalEntry().getTitle()
                : conflict.getRemoteEntry().getTitle()));

        ButtonGroup group = new ButtonGroup();
        JRadioButton localBtn = new JRadioButton(lang.getString("sync.merge.local_version") + ": "
            + formatEntry(conflict.getLocalEntry()), true);
        JRadioButton remoteBtn = new JRadioButton(lang.getString("sync.merge.remote_version") + ": "
            + formatEntry(conflict.getRemoteEntry()));

        localBtn.addActionListener(e -> keepLocal.set(index, true));
        remoteBtn.addActionListener(e -> keepLocal.set(index, false));

        group.add(localBtn);
        group.add(remoteBtn);

        JPanel radioPanel = new JPanel(new GridLayout(2, 1));
        radioPanel.add(localBtn);
        radioPanel.add(remoteBtn);
        panel.add(radioPanel, BorderLayout.CENTER);

        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        return panel;
    }

    private String formatEntry(VaultEntry e) {
        return (e.getUsername() != null ? e.getUsername() : "") + " — " +
            (e.getUpdatedAt() != null ? e.getUpdatedAt() : "");
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public List<VaultEntry> getResolvedEntries() {
        List<VaultEntry> resolved = new ArrayList<>();
        for (int i = 0; i < conflicts.size(); i++) {
            resolved.add(keepLocal.get(i)
                ? conflicts.get(i).getLocalEntry()
                : conflicts.get(i).getRemoteEntry());
        }
        return resolved;
    }
}
