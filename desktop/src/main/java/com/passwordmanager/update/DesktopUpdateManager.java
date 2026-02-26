package com.passwordmanager.update;

import com.passwordmanager.config.AppVersion;
import com.passwordmanager.i18n.LanguageManager;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

/**
 * Manages update checks for the desktop application.
 * Runs checks in the background and displays a non-intrusive notification bar.
 */
public class DesktopUpdateManager {

    private final LanguageManager lang = LanguageManager.getInstance();
    private final UpdateChecker checker = new UpdateChecker();
    private JPanel notificationBar;
    private javax.swing.Timer periodicTimer;

    /**
     * Creates and returns a notification bar panel (initially hidden).
     * Add this to the NORTH of the MainFrame content pane.
     */
    public JPanel createNotificationBar() {
        notificationBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        notificationBar.setBackground(new Color(255, 243, 205));
        notificationBar.setVisible(false);
        return notificationBar;
    }

    /**
     * Starts background update checking: immediately and then periodically.
     */
    public void startPeriodicCheck() {
        if (!checker.isEnabled()) return;
        checkInBackground();
        int intervalMs = checker.getCheckIntervalMinutes() * 60 * 1000;
        periodicTimer = new javax.swing.Timer(intervalMs, e -> checkInBackground());
        periodicTimer.setRepeats(true);
        periodicTimer.start();
    }

    /**
     * Stops periodic checking.
     */
    public void stop() {
        if (periodicTimer != null) {
            periodicTimer.stop();
            periodicTimer = null;
        }
    }

    /**
     * Performs a single check in the background and shows notification if update available.
     */
    public void checkInBackground() {
        new SwingWorker<UpdateInfo, Void>() {
            @Override
            protected UpdateInfo doInBackground() {
                try {
                    return checker.checkForUpdate();
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    UpdateInfo info = get();
                    if (info != null) {
                        showUpdateNotification(info);
                    }
                } catch (Exception ignored) {
                    // silent failure
                }
            }
        }.execute();
    }

    /**
     * Performs a synchronous check and shows result dialog.
     * Used for manual "Check for updates" button.
     */
    public void checkManually(Component parent) {
        new SwingWorker<UpdateInfo, Void>() {
            @Override
            protected UpdateInfo doInBackground() {
                try {
                    return checker.checkForUpdate();
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    UpdateInfo info = get();
                    if (info != null) {
                        showUpdateNotification(info);
                        JOptionPane.showMessageDialog(parent,
                            lang.getString("update.available").replace("{0}", info.getVersion()),
                            lang.getString("update.check"),
                            JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(parent,
                            lang.getString("update.upToDate") + "\n"
                                + lang.getString("update.current").replace("{0}", AppVersion.get()),
                            lang.getString("update.check"),
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(parent,
                        lang.getString("update.error"),
                        lang.getString("common.error"),
                        JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void showUpdateNotification(UpdateInfo info) {
        if (notificationBar == null) return;
        notificationBar.removeAll();

        JLabel label = new JLabel(lang.getString("update.available").replace("{0}", info.getVersion()));
        label.setForeground(new Color(102, 77, 3));

        JButton downloadBtn = new JButton(lang.getString("update.download"));
        downloadBtn.addActionListener(e -> {
            String url = info.getReleaseNotesUrl();
            if (url == null || !url.startsWith("https://github.com/")) return;
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                // ignore
            }
        });

        JButton dismissBtn = new JButton("\u00d7");
        dismissBtn.setBorderPainted(false);
        dismissBtn.setContentAreaFilled(false);
        dismissBtn.addActionListener(e -> notificationBar.setVisible(false));

        notificationBar.add(label);
        notificationBar.add(downloadBtn);
        notificationBar.add(dismissBtn);
        notificationBar.setVisible(true);
        notificationBar.revalidate();
    }
}
