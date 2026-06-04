package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal recursive-descent JSON parser over a {@code char[]} (R3).
 * Reads string secrets straight into {@code char[]} ({@link #readStringChars}) so they
 * are never materialized as a {@link String}. Lenient enough to parse JSON previously
 * written by Gson (whitespace, {@code \\uXXXX} escapes). Package-private helper for
 * {@link VaultJsonCodec}.
 */
final class JsonCharReader {

    private final char[] s;
    private int pos = 0;

    JsonCharReader(char[] source) {
        this.s = source;
    }

    // ---- structural ----

    void skipWs() {
        while (pos < s.length) {
            char c = s[pos];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
            else break;
        }
    }

    char peek() {
        skipWs();
        if (pos >= s.length) throw err("unexpected end of input");
        return s[pos];
    }

    void expect(char c) {
        skipWs();
        if (pos >= s.length || s[pos] != c) throw err("expected '" + c + "'");
        pos++;
    }

    void expectColon() { expect(':'); }

    boolean tryComma() {
        skipWs();
        if (pos < s.length && s[pos] == ',') { pos++; return true; }
        return false;
    }

    boolean tryEndObject() {
        skipWs();
        if (pos < s.length && s[pos] == '}') { pos++; return true; }
        return false;
    }

    boolean tryEndArray() {
        skipWs();
        if (pos < s.length && s[pos] == ']') { pos++; return true; }
        return false;
    }

    // ---- scalar reads ----

    /** Reads a JSON string into a String (non-secret values only). */
    String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= s.length) throw err("unterminated string");
            char c = s[pos++];
            if (c == '"') break;
            if (c == '\\') sb.append(readEscape());
            else sb.append(c);
        }
        return sb.toString();
    }

    /** Reads a JSON string secret straight into a char[] (never a String). */
    char[] readStringChars() {
        expect('"');
        char[] out = new char[16];
        int n = 0;
        try {
            while (true) {
                if (pos >= s.length) throw err("unterminated string");
                char c = s[pos++];
                if (c == '"') break;
                char ch = (c == '\\') ? readEscape() : c;
                if (n >= out.length) {
                    char[] bigger = Arrays.copyOf(out, out.length * 2);
                    SecureWiper.wipe(out); // old buffer held secret chars
                    out = bigger;
                }
                out[n++] = ch;
            }
            return Arrays.copyOf(out, n);
        } finally {
            // Wipe the working buffer on success AND on exception (e.g. corrupt
            // escape mid-secret), so a partially-parsed secret never lingers.
            SecureWiper.wipe(out);
        }
    }

    String readStringOrNull() {
        skipWs();
        if (pos < s.length && s[pos] == 'n') { readNull(); return null; }
        return readString();
    }

    boolean readBoolean() {
        skipWs();
        if (match("true")) return true;
        if (match("false")) return false;
        throw err("expected boolean");
    }

    void readNull() {
        skipWs();
        if (!match("null")) throw err("expected null");
    }

    /** Generic JSON value (used for the settings map). Numbers become Double, matching Gson. */
    Object readValue() {
        char c = peek();
        switch (c) {
            case '{': return readGenericObject();
            case '[': return readGenericArray();
            case '"': return readString();
            case 't': case 'f': return readBoolean();
            case 'n': readNull(); return null;
            default:  return readNumber();
        }
    }

    void skipValue() { readValue(); }

    // ---- internals ----

    private Map<String, Object> readGenericObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        if (!tryEndObject()) {
            do {
                String key = readString();
                expectColon();
                map.put(key, readValue());
            } while (tryComma());
            expect('}');
        }
        return map;
    }

    private List<Object> readGenericArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        if (!tryEndArray()) {
            do {
                list.add(readValue());
            } while (tryComma());
            expect(']');
        }
        return list;
    }

    private Double readNumber() {
        skipWs();
        int start = pos;
        while (pos < s.length) {
            char c = s[pos];
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') pos++;
            else break;
        }
        if (pos == start) throw err("expected number");
        return Double.parseDouble(new String(s, start, pos - start));
    }

    private char readEscape() {
        if (pos >= s.length) throw err("bad escape");
        char e = s[pos++];
        switch (e) {
            case '"':  return '"';
            case '\\': return '\\';
            case '/':  return '/';
            case 'b':  return '\b';
            case 'f':  return '\f';
            case 'n':  return '\n';
            case 'r':  return '\r';
            case 't':  return '\t';
            case 'u':
                if (pos + 4 > s.length) throw err("bad unicode escape");
                int code = (hex(s[pos]) << 12) | (hex(s[pos + 1]) << 8)
                         | (hex(s[pos + 2]) << 4) | hex(s[pos + 3]);
                pos += 4;
                return (char) code;
            default:
                throw err("invalid escape '\\" + e + "'");
        }
    }

    private int hex(char c) {
        int d = Character.digit(c, 16);
        if (d < 0) throw err("invalid hex digit '" + c + "'");
        return d;
    }

    private boolean match(String lit) {
        if (pos + lit.length() > s.length) return false;
        for (int i = 0; i < lit.length(); i++) {
            if (s[pos + i] != lit.charAt(i)) return false;
        }
        pos += lit.length();
        return true;
    }

    private IllegalArgumentException err(String msg) {
        return new IllegalArgumentException("JSON parse error at " + pos + ": " + msg);
    }
}
