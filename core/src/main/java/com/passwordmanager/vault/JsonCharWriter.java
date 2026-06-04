package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;

import java.util.Arrays;

/**
 * Minimal streaming JSON writer backed by a growable {@code char[]} (R3).
 * Supports writing string values from {@code char[]} ({@link #valueChars}) so that
 * secrets are escaped and emitted without ever being turned into a {@link String}.
 * Package-private helper for {@link VaultJsonCodec}.
 */
final class JsonCharWriter {

    private static final int OBJECT = 0;
    private static final int ARRAY = 1;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private char[] buf = new char[256];
    private int len = 0;

    private int[] stackType = new int[16];
    private boolean[] stackHasItem = new boolean[16];
    private int top = -1;

    // ---- container management ----

    JsonCharWriter beginObject() { maybeArrayComma(); put('{'); push(OBJECT); return this; }
    JsonCharWriter endObject() { pop(); put('}'); return this; }
    JsonCharWriter beginArray() { maybeArrayComma(); put('['); push(ARRAY); return this; }
    JsonCharWriter endArray() { pop(); put(']'); return this; }

    /** Writes an object member name; handles the preceding comma. */
    JsonCharWriter name(String key) {
        if (stackHasItem[top]) put(',');
        writeQuoted(key);
        put(':');
        stackHasItem[top] = true;
        return this;
    }

    /** Writes {@code "key":"value"} only when value is non-null (Gson-like null omission). */
    void nameStringOrSkip(String key, String value) {
        if (value != null) {
            name(key);
            writeQuoted(value);
        }
    }

    // ---- values ----

    void valueString(String s) {
        maybeArrayComma();
        if (s == null) appendRaw("null"); else writeQuoted(s);
    }

    /** Writes a JSON string from a char[] secret without creating a String. */
    void valueChars(char[] s) {
        maybeArrayComma();
        put('"');
        for (char c : s) escape(c);
        put('"');
    }

    void valueBoolean(boolean b) { maybeArrayComma(); appendRaw(b ? "true" : "false"); }
    void valueNumberRaw(String number) { maybeArrayComma(); appendRaw(number); }
    void valueNull() { maybeArrayComma(); appendRaw("null"); }

    /**
     * Returns the serialized JSON as a fresh char[] and wipes the internal buffer.
     * The returned array contains secret characters; the caller must wipe it.
     */
    char[] toCharArrayAndWipe() {
        char[] out = Arrays.copyOf(buf, len);
        SecureWiper.wipe(buf);
        len = 0;
        return out;
    }

    // ---- internals ----

    private void maybeArrayComma() {
        if (top >= 0 && stackType[top] == ARRAY) {
            if (stackHasItem[top]) put(',');
            stackHasItem[top] = true;
        }
    }

    private void push(int type) {
        if (top + 1 >= stackType.length) {
            stackType = Arrays.copyOf(stackType, stackType.length * 2);
            stackHasItem = Arrays.copyOf(stackHasItem, stackHasItem.length * 2);
        }
        top++;
        stackType[top] = type;
        stackHasItem[top] = false;
    }

    private void pop() { top--; }

    private void writeQuoted(String s) {
        put('"');
        for (int i = 0; i < s.length(); i++) escape(s.charAt(i));
        put('"');
    }

    private void escape(char c) {
        switch (c) {
            case '"':  appendRaw("\\\""); break;
            case '\\': appendRaw("\\\\"); break;
            case '\n': appendRaw("\\n"); break;
            case '\r': appendRaw("\\r"); break;
            case '\t': appendRaw("\\t"); break;
            case '\b': appendRaw("\\b"); break;
            case '\f': appendRaw("\\f"); break;
            default:
                if (c < 0x20) {
                    appendRaw("\\u00");
                    put(HEX[(c >> 4) & 0xF]);
                    put(HEX[c & 0xF]);
                } else {
                    put(c);
                }
        }
    }

    private void appendRaw(String s) {
        for (int i = 0; i < s.length(); i++) put(s.charAt(i));
    }

    private void put(char c) {
        if (len >= buf.length) {
            char[] bigger = Arrays.copyOf(buf, buf.length * 2);
            SecureWiper.wipe(buf); // old buffer held secret chars; wipe before discarding
            buf = bigger;
        }
        buf[len++] = c;
    }
}
