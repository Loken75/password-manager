package com.passwordmanager.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * UTF-8 conversions between {@code char[]} and {@code byte[]} that never create an
 * intermediate {@link String}.
 *
 * <p>A {@code String} built from secret material cannot be wiped and lingers in the
 * heap until garbage collection, defeating the project's {@code char[]}/{@code byte[]}
 * secret-handling discipline. These helpers go through the NIO encoder/decoder and zero
 * the transient buffer that held the secret before returning. The caller owns the
 * returned array and must wipe it with {@link SecureWiper} once done.
 */
public final class SecureCharsets {

    private SecureCharsets() {}

    /** UTF-8 encodes {@code chars} into a fresh {@code byte[]} without creating a String. */
    public static byte[] toUtf8Bytes(char[] chars) {
        ByteBuffer bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] out = new byte[bb.remaining()];
        bb.get(out);
        if (bb.hasArray()) Arrays.fill(bb.array(), (byte) 0);
        return out;
    }

    /** UTF-8 decodes {@code bytes} into a fresh {@code char[]} without creating a String. */
    public static char[] toChars(byte[] bytes) {
        CharBuffer cb = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] out = new char[cb.remaining()];
        cb.get(out);
        if (cb.hasArray()) Arrays.fill(cb.array(), '\0');
        return out;
    }
}
