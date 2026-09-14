package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_flat_query_params_t;

/**
 * High-level wrapper for {@code zvec_flat_query_params_t} (brute-force search
 * parameters).
 *
 * <p>Ownership: passing an instance to
 * {@link VectorQuery#setFlatParams(FlatQueryParams)},
 * {@link GroupByVectorQuery#setFlatParams(FlatQueryParams)} or
 * {@link SubQuery#setFlatParams(FlatQueryParams)} transfers ownership to the
 * query (mirroring the C API), and the wrapper becomes inert; {@link #close()}
 * afterwards is a no-op.
 */
public class FlatQueryParams implements AutoCloseable {

    private zvec_flat_query_params_t handle;

    /** Create flat query parameters with the C API defaults. */
    public FlatQueryParams() {
        this(false, 10.0f);
    }

    /**
     * Create flat query parameters.
     *
     * @param isUsingRefiner  whether to use the refiner
     * @param scaleFactor     candidate expansion factor used by the refiner
     */
    public FlatQueryParams(boolean isUsingRefiner, float scaleFactor) {
        this.handle = ZvecNative.zvec_query_params_flat_create(isUsingRefiner, scaleFactor);
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create FlatQueryParams");
        }
    }

    zvec_flat_query_params_t getHandle() {
        return handle;
    }

    /**
     * The live native handle, or a {@link ZvecException} once ownership has been
     * transferred to a query or the params have been closed.
     */
    private zvec_flat_query_params_t requireLive() {
        zvec_flat_query_params_t current = handle;
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
    zvec_flat_query_params_t takeHandle() {
        zvec_flat_query_params_t h = requireLive();
        handle = null;
        return h;
    }

    /** Set the candidate expansion factor used by the refiner. */
    public void setScaleFactor(float scaleFactor) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_flat_set_scale_factor(requireLive(), scaleFactor));
    }

    /** Get the candidate expansion factor used by the refiner. */
    public float getScaleFactor() {
        return ZvecNative.zvec_query_params_flat_get_scale_factor(requireLive());
    }

    public void setRadius(float radius) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_flat_set_radius(requireLive(), radius));
    }

    public float getRadius() {
        return ZvecNative.zvec_query_params_flat_get_radius(requireLive());
    }

    public void setIsLinear(boolean isLinear) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_flat_set_is_linear(requireLive(), isLinear));
    }

    public boolean getIsLinear() {
        return ZvecNative.zvec_query_params_flat_get_is_linear(requireLive());
    }

    public void setIsUsingRefiner(boolean isUsingRefiner) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_flat_set_is_using_refiner(requireLive(), isUsingRefiner));
    }

    public boolean getIsUsingRefiner() {
        return ZvecNative.zvec_query_params_flat_get_is_using_refiner(requireLive());
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_query_params_flat_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
