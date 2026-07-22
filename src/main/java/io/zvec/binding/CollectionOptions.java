package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_collection_options_t;

/**
 * High-level wrapper for {@code zvec_collection_options_t}.
 */
public class CollectionOptions implements AutoCloseable {

    private zvec_collection_options_t handle;

    public CollectionOptions() {
        this.handle = ZvecNative.zvec_collection_options_create();
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create CollectionOptions");
        }
    }

    CollectionOptions(zvec_collection_options_t handle) {
        this.handle = handle;
    }

    zvec_collection_options_t getHandle() {
        return handle;
    }

    public void setEnableMmap(boolean enable) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_options_set_enable_mmap(handle, enable));
    }

    public boolean getEnableMmap() {
        return ZvecNative.zvec_collection_options_get_enable_mmap(handle);
    }

    public void setMaxBufferSize(long size) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_options_set_max_buffer_size(handle, size));
    }

    public long getMaxBufferSize() {
        return ZvecNative.zvec_collection_options_get_max_buffer_size(handle);
    }

    public void setReadOnly(boolean readOnly) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_options_set_read_only(handle, readOnly));
    }

    public boolean getReadOnly() {
        return ZvecNative.zvec_collection_options_get_read_only(handle);
    }

    @Override
    public void close() {
        destroy();
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_collection_options_destroy(handle);
            handle = null;
        }
    }
}
