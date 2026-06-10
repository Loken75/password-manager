package com.passwordmanager.ui;

import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs a blocking task off the Swing event-dispatch thread (EDT) and delivers the
 * result — or the failure — back on the EDT, so crypto (PBKDF2) and file I/O never
 * freeze the UI.
 *
 * <p>While the task runs, the owner window's glass pane is shown with a wait cursor.
 * A visible glass pane both signals "busy" and intercepts stray input (preventing
 * double-submits); it is restored on every completion path.
 *
 * <p><b>Secrets:</b> the background {@code work} owns any secret it touches and MUST
 * wipe it itself (typically in a {@code finally}). The wipe must happen inside
 * {@code work}, not at the call site — code after the {@code run(...)} call executes
 * <i>before</i> the background task finishes and would wipe the secret too early.
 */
public final class BackgroundTask {

    /** A unit of background work that may throw; its return value is passed to onSuccess. */
    @FunctionalInterface
    public interface Work<T> {
        T run() throws Exception;
    }

    private BackgroundTask() {}

    /**
     * Executes {@code work} off the EDT. On completion, {@code onSuccess} (with the
     * result) or {@code onError} (with the unwrapped exception) is invoked on the EDT.
     *
     * @param owner any component in the window to mark busy (may be {@code null})
     */
    public static <T> void run(Component owner, Work<T> work,
                               Consumer<T> onSuccess, Consumer<Exception> onError) {
        final JRootPane root = owner == null ? null : SwingUtilities.getRootPane(owner);
        final Component glass = root == null ? null : root.getGlassPane();
        final Cursor previousCursor = glass == null ? null : glass.getCursor();
        if (glass != null) {
            glass.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            glass.setVisible(true);
        }
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.run();
            }

            @Override
            protected void done() {
                if (glass != null) {
                    glass.setVisible(false);
                    glass.setCursor(previousCursor);
                }
                T value;
                try {
                    value = get();
                } catch (ExecutionException ee) {
                    Throwable cause = ee.getCause();
                    onError.accept(cause instanceof Exception ? (Exception) cause
                            : new RuntimeException(cause));
                    return;
                } catch (Exception e) {
                    // InterruptedException / CancellationException
                    onError.accept(e);
                    return;
                }
                onSuccess.accept(value);
            }
        }.execute();
    }
}
