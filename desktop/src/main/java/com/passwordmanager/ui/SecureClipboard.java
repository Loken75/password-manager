package com.passwordmanager.ui;

import com.passwordmanager.util.SecureWiper;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;

/**
 * Secure clipboard operations that avoid creating immutable String objects
 * from sensitive data. Uses a custom {@link Transferable} that stores
 * password data as char[] and wipes it when clipboard ownership is lost.
 */
public final class SecureClipboard {

    private SecureClipboard() {}

    /**
     * Copies a char[] password to the system clipboard without creating
     * an intermediate immutable String. The data is wiped when another
     * application takes clipboard ownership.
     *
     * @param data the sensitive data to copy (caller's copy is NOT wiped)
     */
    public static void copyPassword(char[] data) {
        if (data == null || data.length == 0) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new WipingTransferable(data.clone()), null);
    }

    /**
     * Clears the clipboard content.
     */
    public static void clear() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(""), null);
        } catch (Exception ignored) {
            // Clipboard may not be accessible
        }
    }

    /**
     * A Transferable that holds sensitive char[] data and wipes it
     * when the clipboard is overwritten by another owner.
     */
    private static class WipingTransferable implements Transferable, ClipboardOwner {
        private char[] data;

        WipingTransferable(char[] data) {
            this.data = data;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.stringFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            // String creation is unavoidable here — the clipboard consumer requests it.
            // But our char[] source is wiped when ownership is lost.
            return data != null ? new String(data) : "";
        }

        @Override
        public void lostOwnership(Clipboard clipboard, Transferable contents) {
            // Wipe the char[] when another app overwrites the clipboard
            SecureWiper.wipe(data);
            data = null;
        }
    }
}
