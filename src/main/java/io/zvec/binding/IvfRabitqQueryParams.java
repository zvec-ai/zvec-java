package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_ivf_rabitq_query_params_t;

/**
 * High-level wrapper for {@code zvec_ivf_rabitq_query_params_t} (IVF RaBitQ
 * search parameters, available since zvec v0.7.0).
 *
 * <p>Ownership: passing an instance to
 * {@link VectorQuery#setIvfRabitqParams(IvfRabitqQueryParams)} or
 * {@link GroupByVectorQuery#setIvfRabitqParams(IvfRabitqQueryParams)}
 * transfers ownership to the query (mirroring the C API), and the wrapper
 * becomes inert; {@link #close()} afterwards is a no-op.
 */
public class IvfRabitqQueryParams implements AutoCloseable {

    private zvec_ivf_rabitq_query_params_t handle;

    /** Create IVF RaBitQ query parameters with defaults (nprobe=10). */
    public IvfRabitqQueryParams() {
        this(10, 0.0f, false, false);
    }

    /**
     * Create IVF RaBitQ query parameters.
     *
     * @param nprobe          number of clusters to probe
     * @param radius          search radius (0 disables radius filtering)
     * @param isLinear        whether to use linear search
     * @param isUsingRefiner  whether to use the refiner
     */
    public IvfRabitqQueryParams(int nprobe, float radius, boolean isLinear, boolean isUsingRefiner) {
        this.handle = ZvecNative.zvec_query_params_ivf_rabitq_create(nprobe, radius, isLinear, isUsingRefiner);
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create IvfRabitqQueryParams");
        }
    }

    zvec_ivf_rabitq_query_params_t getHandle() {
        return handle;
    }

    /**
     * Detach and return the native handle, transferring ownership to the
     * caller (the C query takes ownership of the params).
     */
    zvec_ivf_rabitq_query_params_t takeHandle() {
        zvec_ivf_rabitq_query_params_t h = handle;
        handle = null;
        return h;
    }

    public void setNprobe(int nprobe) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_rabitq_set_nprobe(handle, nprobe));
    }

    public int getNprobe() {
        return ZvecNative.zvec_query_params_ivf_rabitq_get_nprobe(handle);
    }

    /** Set the candidate expansion factor used by the refiner. */
    public void setScaleFactor(float scaleFactor) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_rabitq_set_scale_factor(handle, scaleFactor));
    }

    /** Get the candidate expansion factor used by the refiner. */
    public float getScaleFactor() {
        return ZvecNative.zvec_query_params_ivf_rabitq_get_scale_factor(handle);
    }

    public void setRadius(float radius) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_rabitq_set_radius(handle, radius));
    }

    public float getRadius() {
        return ZvecNative.zvec_query_params_ivf_rabitq_get_radius(handle);
    }

    public void setIsLinear(boolean isLinear) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_rabitq_set_is_linear(handle, isLinear));
    }

    public boolean getIsLinear() {
        return ZvecNative.zvec_query_params_ivf_rabitq_get_is_linear(handle);
    }

    public void setIsUsingRefiner(boolean isUsingRefiner) {
        ZvecException.throwIfError(
                ZvecNative.zvec_query_params_ivf_rabitq_set_is_using_refiner(handle, isUsingRefiner));
    }

    public boolean getIsUsingRefiner() {
        return ZvecNative.zvec_query_params_ivf_rabitq_get_is_using_refiner(handle);
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_query_params_ivf_rabitq_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
