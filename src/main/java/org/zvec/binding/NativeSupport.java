package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_error_details_t;
import org.zvec.binding.ZvecNative.zvec_string_array_t;
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

    /**
     * Read the message the native layer recorded for the most recent failure on
     * this thread, or {@code ""} when there is none. Lets a bare
     * {@code zvec_error_code_t} surface as an exception a caller can act on.
     *
     * <p>The recorded error is consumed (cleared) once read, so a later failure
     * that records no message of its own cannot inherit this one.
     *
     * <p>Never throws: failing to read a diagnostic must not mask the error
     * that triggered the lookup.
     */
    static String lastErrorMessage() {
        try (zvec_error_details_t details = new zvec_error_details_t()) {
            if (ZvecNative.zvec_get_last_error_details(details) != 0) {
                return "";
            }
            // Copy into a Java String before clearing: the struct holds pointers
            // into the native thread-local message buffer, which the clear
            // below releases.
            String message = string(details.message());
            ZvecNative.zvec_clear_error();
            return message;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Build a native {@code zvec_string_array_t} populated with NUL-terminated
     * UTF-8 copies of {@code items}. Caller must destroy the returned array with
     * {@link ZvecNative#zvec_string_array_destroy(zvec_string_array_t)}.
     */
    static zvec_string_array_t stringArray(String[] items) {
        if (items == null) {
            items = new String[0];
        }
        zvec_string_array_t array = ZvecNative.zvec_string_array_create(items.length);
        if (array == null || array.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create string array");
        }
        for (int i = 0; i < items.length; i++) {
            ZvecNative.zvec_string_array_add(array, i, items[i]);
        }
        return array;
    }
}
