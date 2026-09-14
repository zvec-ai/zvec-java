package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_ivf_query_params_t;

/**
 * High-level wrapper for {@code zvec_ivf_query_params_t} (IVF search
 * parameters).
 *
 * <p>Ownership: passing an instance to
 * {@link VectorQuery#setIVFParams(IvfQueryParams)},
 * {@link GroupByVectorQuery#setIVFParams(IvfQueryParams)} or
 * {@link SubQuery#setIVFParams(IvfQueryParams)} transfers ownership to the
 * query (mirroring the C API), and the wrapper becomes inert; {@link #close()}
 * afterwards is a no-op.
 */
public class IvfQueryParams implements AutoCloseable {

    private zvec_ivf_query_params_t handle;

    /** Create IVF query parameters with the C API defaults (nprobe=10). */
    public IvfQueryParams() {
        this(10, false, 10.0f);
    }

    /**
     * Create IVF query parameters.
     *
     * @param nprobe          number of clusters to probe
     * @param isUsingRefiner  whether to use the refiner
     * @param scaleFactor     candidate expansion factor used by the refiner
     */
    public IvfQueryParams(int nprobe, boolean isUsingRefiner, float scaleFactor) {
        this.handle = ZvecNative.zvec_query_params_ivf_create(nprobe, isUsingRefiner, scaleFactor);
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create IvfQueryParams");
        }
    }

    zvec_ivf_query_params_t getHandle() {
        return handle;
    }

    /**
     * The live native handle, or a {@link ZvecException} once ownership has been
     * transferred to a query or the params have been closed.
     */
    private zvec_ivf_query_params_t requireLive() {
        zvec_ivf_query_params_t current = handle;
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
    zvec_ivf_query_params_t takeHandle() {
        zvec_ivf_query_params_t h = requireLive();
        handle = null;
        return h;
    }

    public void setNprobe(int nprobe) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_set_nprobe(requireLive(), nprobe));
    }

    public int getNprobe() {
        return ZvecNative.zvec_query_params_ivf_get_nprobe(requireLive());
    }

    /** Set the candidate expansion factor used by the refiner. */
    public void setScaleFactor(float scaleFactor) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_set_scale_factor(requireLive(), scaleFactor));
    }

    /** Get the candidate expansion factor used by the refiner. */
    public float getScaleFactor() {
        return ZvecNative.zvec_query_params_ivf_get_scale_factor(requireLive());
    }

    public void setRadius(float radius) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_set_radius(requireLive(), radius));
    }

    public float getRadius() {
        return ZvecNative.zvec_query_params_ivf_get_radius(requireLive());
    }

    public void setIsLinear(boolean isLinear) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_set_is_linear(requireLive(), isLinear));
    }

    public boolean getIsLinear() {
        return ZvecNative.zvec_query_params_ivf_get_is_linear(requireLive());
    }

    public void setIsUsingRefiner(boolean isUsingRefiner) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_set_is_using_refiner(requireLive(), isUsingRefiner));
    }

    public boolean getIsUsingRefiner() {
        return ZvecNative.zvec_query_params_ivf_get_is_using_refiner(requireLive());
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_query_params_ivf_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
