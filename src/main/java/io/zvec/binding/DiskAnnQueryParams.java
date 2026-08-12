package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_diskann_query_params_t;

/**
 * High-level wrapper for {@code zvec_diskann_query_params_t}.
 *
 * <p>Controls DiskANN vector-search behavior such as the search frontier
 * size, radius filtering, linear scan fallback, and refiner usage.</p>
 */
public class DiskAnnQueryParams implements AutoCloseable {

    private zvec_diskann_query_params_t handle;

    public DiskAnnQueryParams() {
        this(300);
    }

    public DiskAnnQueryParams(int listSize) {
        this.handle = ZvecNative.zvec_query_params_diskann_create(listSize);
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create DiskAnnQueryParams");
        }
    }

    zvec_diskann_query_params_t getHandle() {
        return handle;
    }

    public void setListSize(int listSize) {
        ZvecException.throwIfError(ZvecNative.zvec_query_params_diskann_set_list_size(handle, listSize));
    }

    public int getListSize() {
        return ZvecNative.zvec_query_params_diskann_get_list_size(handle);
    }

    public void setRadius(float radius) {
        ZvecException.throwIfError(ZvecNative.zvec_query_params_diskann_set_radius(handle, radius));
    }

    public float getRadius() {
        return ZvecNative.zvec_query_params_diskann_get_radius(handle);
    }

    public void setLinear(boolean linear) {
        ZvecException.throwIfError(ZvecNative.zvec_query_params_diskann_set_is_linear(handle, linear));
    }

    public boolean isLinear() {
        return ZvecNative.zvec_query_params_diskann_get_is_linear(handle);
    }

    public void setUsingRefiner(boolean usingRefiner) {
        ZvecException.throwIfError(ZvecNative.zvec_query_params_diskann_set_is_using_refiner(handle, usingRefiner));
    }

    public boolean isUsingRefiner() {
        return ZvecNative.zvec_query_params_diskann_get_is_using_refiner(handle);
    }

    @Override
    public void close() {
        destroy();
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_query_params_diskann_destroy(handle);
            handle = null;
        }
    }
}
