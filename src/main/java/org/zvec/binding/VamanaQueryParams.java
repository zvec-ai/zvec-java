package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_vamana_query_params_t;

/**
 * High-level wrapper for {@code zvec_vamana_query_params_t} (Vamana graph
 * search parameters).
 *
 * <p>Ownership: passing an instance to
 * {@link VectorQuery#setVamanaParams(VamanaQueryParams)},
 * {@link GroupByVectorQuery#setVamanaParams(VamanaQueryParams)} or
 * {@link SubQuery#setVamanaParams(VamanaQueryParams)} transfers ownership to
 * the query (mirroring the C API), and the wrapper becomes inert;
 * {@link #close()} afterwards is a no-op.
 */
public class VamanaQueryParams implements AutoCloseable {

    private zvec_vamana_query_params_t handle;

    /** Create Vamana query parameters with the C API defaults (efSearch=200). */
    public VamanaQueryParams() {
        this(200, 0.0f, false, false);
    }

    /**
     * Create Vamana query parameters.
     *
     * @param efSearch        search-time candidate list size
     * @param radius          search radius (0 disables radius filtering)
     * @param isLinear        whether to use linear search
     * @param isUsingRefiner  whether to use the refiner
     */
    public VamanaQueryParams(int efSearch, float radius, boolean isLinear, boolean isUsingRefiner) {
        this.handle = ZvecNative.zvec_query_params_vamana_create(
                efSearch, radius, isLinear, isUsingRefiner);
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create VamanaQueryParams");
        }
    }

    zvec_vamana_query_params_t getHandle() {
        return handle;
    }

    /**
     * The live native handle, or a {@link ZvecException} once ownership has been
     * transferred to a query or the params have been closed.
     */
    private zvec_vamana_query_params_t requireLive() {
        zvec_vamana_query_params_t current = handle;
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
    zvec_vamana_query_params_t takeHandle() {
        zvec_vamana_query_params_t h = requireLive();
        handle = null;
        return h;
    }

    public void setEfSearch(int efSearch) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_vamana_set_ef_search(requireLive(), efSearch));
    }

    public int getEfSearch() {
        return ZvecNative.zvec_query_params_vamana_get_ef_search(requireLive());
    }

    public void setRadius(float radius) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_vamana_set_radius(requireLive(), radius));
    }

    public float getRadius() {
        return ZvecNative.zvec_query_params_vamana_get_radius(requireLive());
    }

    public void setIsLinear(boolean isLinear) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_vamana_set_is_linear(requireLive(), isLinear));
    }

    public boolean getIsLinear() {
        return ZvecNative.zvec_query_params_vamana_get_is_linear(requireLive());
    }

    public void setIsUsingRefiner(boolean isUsingRefiner) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_vamana_set_is_using_refiner(requireLive(), isUsingRefiner));
    }

    public boolean getIsUsingRefiner() {
        return ZvecNative.zvec_query_params_vamana_get_is_using_refiner(requireLive());
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_query_params_vamana_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
