package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_fts_query_params_t;

/**
 * High-level wrapper for {@code zvec_fts_query_params_t}.
 *
 * <p>Configures full-text search behavior, such as the default boolean operator
 * for adjacent bare terms.</p>
 */
public class FtsQueryParams implements AutoCloseable {

    private zvec_fts_query_params_t handle;

    public FtsQueryParams() {
        this(null);
    }

    public FtsQueryParams(String defaultOperator) {
        this.handle = ZvecNative.zvec_query_params_fts_create(defaultOperator);
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create FtsQueryParams");
        }
    }

    zvec_fts_query_params_t getHandle() {
        return handle;
    }

    /**
     * Transfer ownership of the native handle to a caller (e.g. a query setter
     * that takes ownership). Subsequent calls on this object are unsafe.
     */
    zvec_fts_query_params_t takeHandle() {
        zvec_fts_query_params_t h = handle;
        handle = null;
        return h;
    }

    public void setDefaultOperator(String defaultOperator) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_fts_set_default_operator(handle, defaultOperator));
    }

    public String getDefaultOperator() {
        return NativeSupport.string(ZvecNative.zvec_query_params_fts_get_default_operator(handle));
    }

    @Override
    public void close() {
        destroy();
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_query_params_fts_destroy(handle);
            handle = null;
        }
    }
}
