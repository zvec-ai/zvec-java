package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_hnsw_query_params_t;

/**
 * High-level wrapper for {@code zvec_hnsw_query_params_t} (HNSW search
 * parameters).
 *
 * <p>Ownership: passing an instance to
 * {@link VectorQuery#setHNSWParams(HnswQueryParams)},
 * {@link GroupByVectorQuery#setHNSWParams(HnswQueryParams)} or
 * {@link SubQuery#setHNSWParams(HnswQueryParams)} transfers ownership to the
 * query (mirroring the C API), and the wrapper becomes inert; {@link #close()}
 * afterwards is a no-op.
 */
public class HnswQueryParams implements AutoCloseable {

    private zvec_hnsw_query_params_t handle;

    /** Create HNSW query parameters with the C API defaults (ef=40). */
    public HnswQueryParams() {
        this(40, 0.0f, false, false);
    }

    /**
     * Create HNSW query parameters.
     *
     * @param ef              exploration factor during search
     * @param radius          search radius (0 disables radius filtering)
     * @param isLinear        whether to use linear search
     * @param isUsingRefiner  whether to use the refiner
     */
    public HnswQueryParams(int ef, float radius, boolean isLinear, boolean isUsingRefiner) {
        this.handle = ZvecNative.zvec_query_params_hnsw_create(ef, radius, isLinear, isUsingRefiner);
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create HnswQueryParams");
        }
    }

    zvec_hnsw_query_params_t getHandle() {
        return handle;
    }

    /**
     * The live native handle, or a {@link ZvecException} once ownership has been
     * transferred to a query or the params have been closed. Without this guard
     * the C API would receive a {@code NULL} handle and fail with a generic
     * invalid-argument error that does not say what went wrong.
     */
    private zvec_hnsw_query_params_t requireLive() {
        zvec_hnsw_query_params_t current = handle;
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
    zvec_hnsw_query_params_t takeHandle() {
        zvec_hnsw_query_params_t h = requireLive();
        handle = null;
        return h;
    }

    public void setEf(int ef) {
        ZvecException.throwIfError(ZvecNative.zvec_query_params_hnsw_set_ef(requireLive(), ef));
    }

    public int getEf() {
        return ZvecNative.zvec_query_params_hnsw_get_ef(requireLive());
    }

    public void setRadius(float radius) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_hnsw_set_radius(requireLive(), radius));
    }

    public float getRadius() {
        return ZvecNative.zvec_query_params_hnsw_get_radius(requireLive());
    }

    public void setIsLinear(boolean isLinear) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_hnsw_set_is_linear(requireLive(), isLinear));
    }

    public boolean getIsLinear() {
        return ZvecNative.zvec_query_params_hnsw_get_is_linear(requireLive());
    }

    public void setIsUsingRefiner(boolean isUsingRefiner) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_hnsw_set_is_using_refiner(requireLive(), isUsingRefiner));
    }

    public boolean getIsUsingRefiner() {
        return ZvecNative.zvec_query_params_hnsw_get_is_using_refiner(requireLive());
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_query_params_hnsw_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
