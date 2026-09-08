package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_index_params_t;
import org.zvec.binding.ZvecNative.zvec_string_array_t;
import org.bytedeco.javacpp.BoolPointer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;

/**
 * High-level wrapper for {@code zvec_index_params_t}.
 *
 * <p>Use the static factory methods to create type-specific parameters:
 * <ul>
 *   <li>{@link #createHNSW(MetricType, int, int)}</li>
 *   <li>{@link #createHNSWQuantized(MetricType, int, int, QuantizeType)}</li>
 *   <li>{@link #createIVF(MetricType, int, int, boolean)}</li>
 *   <li>{@link #createFlat(MetricType)}</li>
 *   <li>{@link #createDiskAnn(MetricType, int, int, int)}</li>
 *   <li>{@link #createDiskAnnQuantized(MetricType, int, int, int, QuantizeType)}</li>
 *   <li>{@link #createIvfRabitq(MetricType, int, int, int)}</li>
 *   <li>{@link #createVamana(MetricType, int, int, float, boolean, boolean)}</li>
 *   <li>{@link #createInvert(boolean, boolean)}</li>
 *   <li>{@link #createFTS(String, String[], String)}</li>
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

    /**
     * Create DiskANN index parameters.
     *
     * @param metricType  distance metric
     * @param maxDegree   graph connectivity (max degree of the Vamana graph)
     * @param listSize    build-time list size (candidate list during construction)
     * @param pqChunkNum  PQ chunk count (0 disables PQ)
     */
    public static IndexParams createDiskAnn(MetricType metricType, int maxDegree,
                                            int listSize, int pqChunkNum) {
        zvec_index_params_t ptr = ZvecNative.zvec_index_params_create(IndexType.DISKANN.getCode());
        if (ptr == null || ptr.isNull()) throw new ZvecException(ErrorCode.INTERNAL_ERROR, "createDiskAnn");
        IndexParams p = new IndexParams(ptr);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_metric_type(ptr, metricType.getCode()));
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_diskann_params(ptr, maxDegree, listSize, pqChunkNum));
        return p;
    }

    public static IndexParams createDiskAnnQuantized(MetricType metricType, int maxDegree,
                                                     int listSize, int pqChunkNum,
                                                     QuantizeType quantizeType) {
        IndexParams p = createDiskAnn(metricType, maxDegree, listSize, pqChunkNum);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_quantize_type(p.handle, quantizeType.getCode()));
        return p;
    }

    /**
     * Create IVF RaBitQ index parameters.
     *
     * @param metricType   distance metric
     * @param nlist        number of cluster centers
     * @param totalBits    total bits for RaBitQ quantization
     * @param sampleCount  sample count for training; 0 means use all vectors
     */
    public static IndexParams createIvfRabitq(MetricType metricType, int nlist,
                                              int totalBits, int sampleCount) {
        zvec_index_params_t ptr = ZvecNative.zvec_index_params_create(IndexType.IVF_RABITQ.getCode());
        if (ptr == null || ptr.isNull()) throw new ZvecException(ErrorCode.INTERNAL_ERROR, "createIvfRabitq");
        IndexParams p = new IndexParams(ptr);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_metric_type(ptr, metricType.getCode()));
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_ivf_rabitq_params(ptr, nlist, totalBits, sampleCount));
        return p;
    }

    /**
     * Create Vamana index parameters.
     *
     * @param metricType           distance metric
     * @param maxDegree            maximum out-degree
     * @param searchListSize       construction candidate list size
     * @param alpha                RobustPrune alpha factor (e.g. 1.2)
     * @param saturateGraph        force every node to reach max_degree
     * @param useContiguousMemory  allocate a contiguous memory arena
     */
    public static IndexParams createVamana(MetricType metricType, int maxDegree,
                                           int searchListSize, float alpha,
                                           boolean saturateGraph, boolean useContiguousMemory) {
        zvec_index_params_t ptr = ZvecNative.zvec_index_params_create(IndexType.VAMANA.getCode());
        if (ptr == null || ptr.isNull()) throw new ZvecException(ErrorCode.INTERNAL_ERROR, "createVamana");
        IndexParams p = new IndexParams(ptr);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_metric_type(ptr, metricType.getCode()));
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_vamana_params(
                        ptr, maxDegree, searchListSize, alpha, saturateGraph, useContiguousMemory));
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

    /**
     * Create an FTS (full-text search) index parameter set.
     *
     * @param tokenizerName tokenizer name, e.g. "standard"
     * @param filters       filter chain, e.g. {@code new String[]{"lowercase"}}
     * @param extraParams   optional extra params JSON (may be null)
     */
    public static IndexParams createFTS(String tokenizerName, String[] filters, String extraParams) {
        zvec_index_params_t ptr = ZvecNative.zvec_index_params_create(IndexType.FTS.getCode());
        if (ptr == null || ptr.isNull()) throw new ZvecException(ErrorCode.INTERNAL_ERROR, "createFTS");
        IndexParams p = new IndexParams(ptr);

        zvec_string_array_t filtersArray = null;
        BytePointer tokenizerPtr = null;
        BytePointer extraParamsPtr = null;
        try {
            filtersArray = NativeSupport.stringArray(filters);
            tokenizerPtr = NativeSupport.utf8(tokenizerName);
            extraParamsPtr = NativeSupport.utf8(extraParams);
            ZvecException.throwIfError(
                    ZvecNative.zvec_index_params_set_fts_params(ptr, tokenizerPtr, filtersArray, extraParamsPtr));
        } finally {
            if (tokenizerPtr != null) {
                tokenizerPtr.close();
            }
            if (filtersArray != null) {
                ZvecNative.zvec_string_array_destroy(filtersArray);
            }
            if (extraParamsPtr != null) {
                extraParamsPtr.close();
            }
        }
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

    public void setDiskAnnParams(int maxDegree, int listSize, int pqChunkNum) {
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_diskann_params(handle, maxDegree, listSize, pqChunkNum));
    }

    public int getDiskAnnMaxDegree() {
        return ZvecNative.zvec_index_params_get_diskann_max_degree(handle);
    }

    public int getDiskAnnListSize() {
        return ZvecNative.zvec_index_params_get_diskann_list_size(handle);
    }

    public int getDiskAnnPqChunkNum() {
        return ZvecNative.zvec_index_params_get_diskann_pq_chunk_num(handle);
    }

    public void setIvfRabitqParams(int nlist, int totalBits, int sampleCount) {
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_ivf_rabitq_params(handle, nlist, totalBits, sampleCount));
    }

    /** Returns IVF RaBitQ params as an array: [nlist, totalBits, sampleCount]. */
    public int[] getIvfRabitqParams() {
        IntPointer nlist = new IntPointer(1);
        IntPointer totalBits = new IntPointer(1);
        IntPointer sampleCount = new IntPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_get_ivf_rabitq_params(handle, nlist, totalBits, sampleCount));
        return new int[]{nlist.get(), totalBits.get(), sampleCount.get()};
    }

    public void setVamanaParams(int maxDegree, int searchListSize, float alpha,
                                boolean saturateGraph, boolean useContiguousMemory) {
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_vamana_params(
                        handle, maxDegree, searchListSize, alpha, saturateGraph, useContiguousMemory));
    }

    /** Returns Vamana params: [maxDegree, searchListSize, alpha, saturateGraph, useContiguousMemory]. */
    public Object[] getVamanaParams() {
        IntPointer maxDegree = new IntPointer(1);
        IntPointer searchListSize = new IntPointer(1);
        FloatPointer alpha = new FloatPointer(1);
        BoolPointer saturateGraph = new BoolPointer(1);
        BoolPointer useContiguousMemory = new BoolPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_get_vamana_params(
                        handle, maxDegree, searchListSize, alpha, saturateGraph, useContiguousMemory));
        return new Object[]{maxDegree.get(), searchListSize.get(), alpha.get(),
                saturateGraph.get(), useContiguousMemory.get()};
    }

    /** Enable or disable Vamana two-pass graph construction (VAMANA params only). */
    public void setVamanaTwoPassBuild(boolean twoPassBuild) {
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_set_vamana_two_pass_build(handle, twoPassBuild));
    }

    /** Whether Vamana two-pass graph construction is enabled. */
    public boolean getVamanaTwoPassBuild() {
        return ZvecNative.zvec_index_params_get_vamana_two_pass_build(handle);
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

    /** Returns FTS params as an array: [tokenizerName, filters..., extraParams]. */
    public String[] getFTSParams() {
        PointerPointer outTokenizer = new PointerPointer(1);
        PointerPointer outFilters = new PointerPointer(1);
        PointerPointer outExtra = new PointerPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_index_params_get_fts_params(handle, outTokenizer, outFilters, outExtra));

        String tokenizer = NativeSupport.string(new BytePointer(outTokenizer.get(0)));
        String extra = NativeSupport.string(new BytePointer(outExtra.get(0)));

        zvec_string_array_t filters = new zvec_string_array_t(outFilters.get(0));
        long filterCount = filters.count();
        String[] result = new String[2 + (int) filterCount];
        result[0] = tokenizer;
        for (int i = 0; i < filterCount; i++) {
            result[1 + i] = NativeSupport.string(filters.strings().position(i).data());
        }
        result[result.length - 1] = extra;
        ZvecNative.zvec_string_array_destroy(filters);
        return result;
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
