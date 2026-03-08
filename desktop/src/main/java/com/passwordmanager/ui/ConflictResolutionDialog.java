package com.passwordmanager.ui;

import com.passwordmanager.i18n.LanguageManager;
import com.passwordmanager.sync.EntryMerger;
import com.passwordmanager.vault.AppEntry;
import com.passwordmanager.vault.PasswordEntry;
import com.passwordmanager.vault.VaultItem;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for resolving entry-level sync conflicts.
 * Shows local vs remote versions side-by-side with radio button selection.
 * Supports PasswordEntry and AppEntry conflicts.
 */
public class ConflictResolutionDialog extends JDialog {
    private final LanguageManager lang = LanguageManager.getInstance();
    private final List<EntryMerger.Conflict<? extends VaultItem>> conflicts;
    private final List<Boolean> keepLocal;
    private boolean confirmed = false;

    public ConflictResolutionDialog(Frame owner, List<? extends EntryMerger.Conflict<? extends VaultItem>> conflicts) {
        super(owner, LanguageManager.getInstance().getString("sync.merge.conflicts"), true);
        this.conflicts = new ArrayList<>(conflicts);
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
            EntryMerger.Conflict<? extends VaultItem> conflict = conflicts.get(i);
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

    private JPanel createConflictPanel(EntryMerger.Conflict<? extends VaultItem> conflict, int index) {
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

    private String formatEntry(VaultItem item) {
        if (item instanceof PasswordEntry) {
            PasswordEntry e = (PasswordEntry) item;
            return (e.getUsername() != null ? e.getUsername() : "") + " — " +
                (e.getUrl() != null ? e.getUrl() : "") + " — " +
                (e.getUpdatedAt() != null ? e.getUpdatedAt() : "");
        } else if (item instanceof AppEntry) {
            AppEntry e = (AppEntry) item;
            return (e.getUsername() != null ? e.getUsername() : "") + " — " +
                (e.getPin() != null ? "****" : "") + " — " +
                (e.getUpdatedAt() != null ? e.getUpdatedAt() : "");
        }
        // Fallback for unknown types
        return item.getUpdatedAt() != null ? item.getUpdatedAt() : "";
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Returns the resolved entries, one per conflict, chosen by the user.
     */
    @SuppressWarnings("unchecked")
    public <T extends VaultItem> List<T> getResolvedEntries() {
        List<T> resolved = new ArrayList<>();
        for (int i = 0; i < conflicts.size(); i++) {
            EntryMerger.Conflict<? extends VaultItem> conflict = conflicts.get(i);
            resolved.add((T) (keepLocal.get(i)
                ? conflict.getLocalEntry()
                : conflict.getRemoteEntry()));
        }
        return resolved;
    }
}
