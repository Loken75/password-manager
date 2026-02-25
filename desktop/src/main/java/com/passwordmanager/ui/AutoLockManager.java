package com.passwordmanager.ui;

import com.passwordmanager.config.AppConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;

/**
 * Manages auto-lock functionality: activity tracking via AWT events,
 * idle timer, and cleanup of timers/listeners.
 * Extracted from MainFrame to reduce class responsibilities.
 */
public class AutoLockManager {
    private final AppConfig appConfig;
    private final Runnable lockCallback;
    private Timer autoLockTimer;
    private AWTEventListener activityListener;
    private long lastActivity;

    public AutoLockManager(AppConfig appConfig, Runnable lockCallback) {
        this.appConfig = appConfig;
        this.lockCallback = lockCallback;
        this.lastActivity = System.currentTimeMillis();
    }

    /**
     * Installs the AWT event listener that tracks user activity (key and mouse events).
     * This should be called during UI initialization.
     */
    public void installActivityListener() {
        activityListener = event -> lastActivity = System.currentTimeMillis();
        Toolkit.getDefaultToolkit().addAWTEventListener(activityListener,
            AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    /**
     * Starts (or restarts) the auto-lock timer that checks for idle time
     * and triggers a lock when the configured timeout is exceeded.
     */
    public void startAutoLock() {
        if (autoLockTimer != null) {
            autoLockTimer.stop();
        }
        autoLockTimer = new Timer(30000, e -> {
            long idle = System.currentTimeMillis() - lastActivity;
            if (idle > appConfig.getAutoLockMinutes() * 60 * 1000L) {
                lockCallback.run();
            }
        });
        autoLockTimer.start();
    }

    /**
     * Stops the auto-lock timer and removes the AWT activity listener.
     * Should be called when the frame is being disposed or locked.
     */
    public void cleanup() {
        if (autoLockTimer != null) {
            autoLockTimer.stop();
            autoLockTimer = null;
        }
        if (activityListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
            activityListener = null;
        }
    }
}
