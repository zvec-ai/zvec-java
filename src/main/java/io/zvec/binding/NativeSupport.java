package io.zvec.binding;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;

import java.nio.charset.StandardCharsets;

/**
 * Internal helpers bridging Java types and JavaCPP native pointers.
 *
 * <p>Package-private; not part of the public API. Centralizes the
 * String &lt;-&gt; {@code const char*} marshalling and native string-array
 * construction shared by the high-level wrapper classes.
 */
final class NativeSupport {

    private NativeSupport() {
    }

    /**
     * Encode a Java String as a freshly allocated, NUL-terminated UTF-8
     * {@code const char*}. Returns {@code null} for a {@code null} input.
     *
     * <p>The returned {@link BytePointer} is managed by JavaCPP; keep a
     * reference to it alive for the duration of the native call that consumes it.
     */
    static BytePointer utf8(String s) {
        if (s == null) {
            return null;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        BytePointer p = new BytePointer(bytes.length + 1L);
        if (bytes.length > 0) {
            p.put(bytes, 0, bytes.length);
        }
        p.put(bytes.length, (byte) 0);
        return p;
    }

    /** Number of bytes in the UTF-8 encoding of {@code s} (excluding NUL). */
    static int utf8Length(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Read a native {@code const char*} (from a BytePointer) as a Java String; "" for null. */
    static String string(BytePointer p) {
        return (p == null || p.isNull()) ? "" : p.getString(StandardCharsets.UTF_8);
    }

    /** Read a native {@code char*} at the given address as a Java String; "" for null. */
    static String string(Pointer p) {
        return (p == null || p.isNull()) ? "" : new BytePointer(p).getString(StandardCharsets.UTF_8);
    }

    /**
     * Build a native {@code const char**} array of NUL-terminated UTF-8 copies of
     * {@code items}. The returned {@link PointerPointer} retains references to its
     * element pointers so they stay alive for the duration of a native call.
     */
    static PointerPointer strArray(String[] items) {
        BytePointer[] elems = new BytePointer[items.length];
        for (int i = 0; i < items.length; i++) {
            elems[i] = utf8(items[i]);
        }
        return new PointerPointer(elems);
    }
}
