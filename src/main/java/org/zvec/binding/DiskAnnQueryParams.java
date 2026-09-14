package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_diskann_query_params_t;

/**
 * High-level wrapper for {@code zvec_diskann_query_params_t} (DiskANN search
 * parameters, available since zvec v0.7.0).
 *
 * <p>Ownership: passing an instance to
 * {@link VectorQuery#setDiskAnnParams(DiskAnnQueryParams)} or
 * {@link GroupByVectorQuery#setDiskAnnParams(DiskAnnQueryParams)} transfers
 * ownership to the query (mirroring the C API), and the wrapper becomes
 * inert; {@link #close()} afterwards is a no-op.
 */
public class DiskAnnQueryParams implements AutoCloseable {

    private zvec_diskann_query_params_t handle;

    /** Create DiskANN query parameters with the default search frontier size (300). */
    public DiskAnnQueryParams() {
        this(300);
    }

    /**
     * Create DiskANN query parameters.
     *
     * @param listSize search frontier size
     */
    public DiskAnnQueryParams(int listSize) {
        this.handle = ZvecNative.zvec_query_params_diskann_create(listSize);
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create DiskAnnQueryParams");
        }
    }

    zvec_diskann_query_params_t getHandle() {
        return handle;
    }

    /**
     * The live native handle, or a {@link ZvecException} once ownership has been
     * transferred to a query or the params have been closed. Without this guard
     * the C API would receive a {@code NULL} handle and fail with a generic
     * invalid-argument error that does not say what went wrong.
     */
    private zvec_diskann_query_params_t requireLive() {
        zvec_diskann_query_params_t current = handle;
        if (current == null || current.isNull()) {
            throw new ZvecException(ErrorCode.FAILED_PRECONDITION,
                    "params are closed or already owned by a query");
        }
        return current;
    }

    /**
     * Detach and return the native handle, transferring ownership to the
     * caller (the C query takes ownership of the params).
     *
     * @throws ZvecException when the handle has already been transferred
     */
    zvec_diskann_query_params_t takeHandle() {
        zvec_diskann_query_params_t h = requireLive();
        handle = null;
        return h;
    }

    public void setListSize(int listSize) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_diskann_set_list_size(requireLive(), listSize));
    }

    public int getListSize() {
        return ZvecNative.zvec_query_params_diskann_get_list_size(requireLive());
    }

    public void setRadius(float radius) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_diskann_set_radius(requireLive(), radius));
    }

    public float getRadius() {
        return ZvecNative.zvec_query_params_diskann_get_radius(requireLive());
    }

    public void setIsLinear(boolean isLinear) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_diskann_set_is_linear(requireLive(), isLinear));
    }

    public boolean getIsLinear() {
        return ZvecNative.zvec_query_params_diskann_get_is_linear(requireLive());
    }

    public void setIsUsingRefiner(boolean isUsingRefiner) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_diskann_set_is_using_refiner(requireLive(), isUsingRefiner));
    }

    public boolean getIsUsingRefiner() {
        return ZvecNative.zvec_query_params_diskann_get_is_using_refiner(requireLive());
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_query_params_diskann_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
