package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_fts_t;

/**
 * High-level wrapper for {@code zvec_fts_t}.
 *
 * <p>Represents the FTS query payload: a boolean/advanced query expression
 * and/or a natural-language match string.</p>
 */
public class FtsPayload implements AutoCloseable {

    private zvec_fts_t handle;
    private final boolean ownsHandle;

    public FtsPayload() {
        this.handle = ZvecNative.zvec_fts_create();
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create FtsPayload");
        }
        this.ownsHandle = true;
    }

    FtsPayload(zvec_fts_t handle) {
        this(handle, true);
    }

    FtsPayload(zvec_fts_t handle, boolean ownsHandle) {
        this.handle = handle;
        this.ownsHandle = ownsHandle;
    }

    zvec_fts_t getHandle() {
        return handle;
    }

    public void setQueryString(String queryString) {
        // Marshal explicitly as UTF-8: the generated String overload goes
        // through GetStringUTFChars(), i.e. modified UTF-8, which encodes
        // astral characters (emoji, CJK extension B) as surrogate pairs the
        // C++ tokenizer would reject.
        ZvecException.throwIfError(
                ZvecNative.zvec_fts_set_query_string(handle, NativeSupport.utf8(queryString)));
    }

    public String getQueryString() {
        return NativeSupport.string(ZvecNative.zvec_fts_get_query_string(handle));
    }

    public void setMatchString(String matchString) {
        ZvecException.throwIfError(
                ZvecNative.zvec_fts_set_match_string(handle, NativeSupport.utf8(matchString)));
    }

    public String getMatchString() {
        return NativeSupport.string(ZvecNative.zvec_fts_get_match_string(handle));
    }

    @Override
    public void close() {
        destroy();
    }

    public void destroy() {
        if (ownsHandle && handle != null && !handle.isNull()) {
            ZvecNative.zvec_fts_destroy(handle);
        }
        handle = null;
    }
}
