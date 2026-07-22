package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_index_params_t;
import org.bytedeco.javacpp.BoolPointer;
import org.bytedeco.javacpp.IntPointer;

/**
 * High-level wrapper for {@code zvec_index_params_t}.
 *
 * <p>Use the static factory methods to create type-specific parameters:
 * <ul>
 *   <li>{@link #createHNSW(MetricType, int, int)}</li>
 *   <li>{@link #createHNSWQuantized(MetricType, int, int, QuantizeType)}</li>
 *   <li>{@link #createIVF(MetricType, int, int, boolean)}</li>
 *   <li>{@link #createFlat(MetricType)}</li>
 *   <li>{@link #createInvert(boolean, boolean)}</li>
 * </ul>
 */
public class IndexParams implements AutoCloseable {

    private zvec_index_params_t handle;

    private IndexParams(zvec_index_params_t handle) {
        this.handle = handle;
    }

    zvec_index_params_t getHandle() {
        return handle;
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    public static IndexParams createHNSW(MetricType metricType, int m, int efConstruction) {
        zvec_index_params_t ptr = ZvecNative.zvec_index_params_create(IndexType.HNSW.getCode());
        if (ptr == null || ptr.isNull()) throw new ZvecException(ErrorCode.INTERNAL_ERROR, "createHNSW");
        IndexParams p = new IndexParams(ptr);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_metric_type(ptr, metricType.getCode()));
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_hnsw_params(ptr, m, efConstruction));
        return p;
    }

    public static IndexParams createHNSWQuantized(MetricType metricType, int m,
                                                   int efConstruction, QuantizeType quantizeType) {
        IndexParams p = createHNSW(metricType, m, efConstruction);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_quantize_type(p.handle, quantizeType.getCode()));
        return p;
    }

    public static IndexParams createIVF(MetricType metricType, int nList, int nIters, boolean useSoar) {
        zvec_index_params_t ptr = ZvecNative.zvec_index_params_create(IndexType.IVF.getCode());
        if (ptr == null || ptr.isNull()) throw new ZvecException(ErrorCode.INTERNAL_ERROR, "createIVF");
        IndexParams p = new IndexParams(ptr);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_metric_type(ptr, metricType.getCode()));
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_ivf_params(ptr, nList, nIters, useSoar));
        return p;
    }

    public static IndexParams createFlat(MetricType metricType) {
        zvec_index_params_t ptr = ZvecNative.zvec_index_params_create(IndexType.FLAT.getCode());
        if (ptr == null || ptr.isNull()) throw new ZvecException(ErrorCode.INTERNAL_ERROR, "createFlat");
        IndexParams p = new IndexParams(ptr);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_metric_type(ptr, metricType.getCode()));
        return p;
    }

    public static IndexParams createInvert(boolean enableRangeOpt, boolean enableWildcard) {
        zvec_index_params_t ptr = ZvecNative.zvec_index_params_create(IndexType.INVERT.getCode());
        if (ptr == null || ptr.isNull()) throw new ZvecException(ErrorCode.INTERNAL_ERROR, "createInvert");
        IndexParams p = new IndexParams(ptr);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_invert_params(ptr, enableRangeOpt, enableWildcard));
        return p;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public IndexType getType() {
        return IndexType.fromCode(ZvecNative.zvec_index_params_get_type(handle));
    }

    public MetricType getMetricType() {
        return MetricType.fromCode(ZvecNative.zvec_index_params_get_metric_type(handle));
    }

    public void setMetricType(MetricType metricType) {
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_metric_type(handle, metricType.getCode()));
    }

    public QuantizeType getQuantizeType() {
        return QuantizeType.fromCode(ZvecNative.zvec_index_params_get_quantize_type(handle));
    }

    public void setQuantizeType(QuantizeType quantizeType) {
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_quantize_type(handle, quantizeType.getCode()));
    }

    public int getHNSWM() {
        return ZvecNative.zvec_index_params_get_hnsw_m(handle);
    }

    public int getHNSWEfConstruction() {
        return ZvecNative.zvec_index_params_get_hnsw_ef_construction(handle);
    }

    public void setHNSWParams(int m, int efConstruction) {
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_hnsw_params(handle, m, efConstruction));
    }

    public void setIVFParams(int nList, int nIters, boolean useSoar) {
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_ivf_params(handle, nList, nIters, useSoar));
    }

    /** Returns IVF params as an array: [nList, nIters, useSoar (0 or 1)]. */
    public int[] getIVFParams() {
        IntPointer nList = new IntPointer(1);
        IntPointer nIters = new IntPointer(1);
        BoolPointer useSoar = new BoolPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_get_ivf_params(handle, nList, nIters, useSoar));
        return new int[]{nList.get(), nIters.get(), useSoar.get() ? 1 : 0};
    }

    /** Returns Invert params as an array: [enableRangeOpt, enableWildcard]. */
    public boolean[] getInvertParams() {
        BoolPointer enableRangeOpt = new BoolPointer(1);
        BoolPointer enableWildcard = new BoolPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_get_invert_params(handle, enableRangeOpt, enableWildcard));
        return new boolean[]{enableRangeOpt.get(), enableWildcard.get()};
    }

    public void setInvertParams(boolean enableRangeOpt, boolean enableWildcard) {
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_invert_params(handle, enableRangeOpt, enableWildcard));
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_index_params_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
