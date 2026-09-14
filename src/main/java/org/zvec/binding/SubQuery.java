package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_sub_query_t;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;

/**
 * High-level wrapper for {@code zvec_sub_query_t}.
 *
 * <p>A sub-query is a building block of {@link MultiQuery}. Each sub-query targets
 * one field and can carry vector-search parameters or an FTS payload.</p>
 */
public class SubQuery implements AutoCloseable {

    private zvec_sub_query_t handle;

    public SubQuery() {
        this.handle = ZvecNative.zvec_sub_query_create();
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create SubQuery");
        }
    }

    zvec_sub_query_t getHandle() {
        return handle;
    }

    public void setFieldName(String fieldName) {
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_field_name(handle, NativeSupport.utf8(fieldName)));
    }

    public String getFieldName() {
        return NativeSupport.string(ZvecNative.zvec_sub_query_get_field_name(handle));
    }

    public void setNumCandidates(int numCandidates) {
        ZvecException.throwIfError(ZvecNative.zvec_sub_query_set_num_candidates(handle, numCandidates));
    }

    public int getNumCandidates() {
        return ZvecNative.zvec_sub_query_get_num_candidates(handle);
    }

    /** Set the query vector (float32). The data is copied. */
    public void setQueryVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new ZvecException(ErrorCode.INVALID_ARGUMENT, "query vector cannot be empty");
        }
        FloatPointer data = new FloatPointer(vector);
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_query_vector(handle, data, (long) vector.length * 4));
    }

    /** Set sparse vector entries in one call. */
    public void setSparseVector(int[] indices, float[] values) {
        if (indices == null || values == null || indices.length != values.length) {
            throw new ZvecException(ErrorCode.INVALID_ARGUMENT, "indices and values must match");
        }
        IntPointer idx = new IntPointer(indices);
        FloatPointer vals = new FloatPointer(values);
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_sparse_vector(handle, idx, vals, indices.length));
    }

    public void setSparseIndices(int[] indices) {
        IntPointer idx = new IntPointer(indices);
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_sparse_indices(handle, idx, indices.length));
    }

    public void setSparseValues(float[] values) {
        FloatPointer vals = new FloatPointer(values);
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_sparse_values(handle, vals, values.length));
    }

    /** Set HNSW query parameters. Ownership transfers to this sub-query. */
    public void setHNSWParams(HnswQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_hnsw_params(handle, params.takeHandle()));
    }

    /** Set IVF query parameters. Ownership transfers to this sub-query. */
    public void setIVFParams(IvfQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_ivf_params(handle, params.takeHandle()));
    }

    /** Set flat (brute-force) query parameters. Ownership transfers to this sub-query. */
    public void setFlatParams(FlatQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_flat_params(handle, params.takeHandle()));
    }

    /** Set Vamana query parameters. Ownership transfers to this sub-query. */
    public void setVamanaParams(VamanaQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_vamana_params(handle, params.takeHandle()));
    }

    /**
     * Set IVF RaBitQ query parameters (zvec &ge; v0.7.0). Ownership of the
     * params transfers to this sub-query; the wrapper becomes inert.
     */
    public void setIvfRabitqParams(IvfRabitqQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_ivf_rabitq_params(handle, params.takeHandle()));
    }

    /**
     * Set DiskANN query parameters (zvec &ge; v0.7.0). Ownership of the params
     * transfers to this sub-query; the wrapper becomes inert. Passing the handle
     * without transferring it would leave the query pointing at memory that
     * {@link DiskAnnQueryParams#close()} frees.
     */
    public void setDiskAnnParams(DiskAnnQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_diskann_params(handle, params.takeHandle()));
    }

    /** Set FTS query parameters. Ownership transfers to this sub-query. */
    public void setFtsParams(FtsQueryParams ftsParams) {
        ZvecException.throwIfError(
                ZvecNative.zvec_sub_query_set_fts_params(handle, ftsParams.takeHandle()));
    }

    public void setFts(FtsPayload fts) {
        ZvecException.throwIfError(ZvecNative.zvec_sub_query_set_fts(handle, fts.getHandle()));
    }

    @Override
    public void close() {
        destroy();
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_sub_query_destroy(handle);
            handle = null;
        }
    }
}
