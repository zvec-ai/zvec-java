package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_fts_t;
import org.zvec.binding.ZvecNative.zvec_vector_query_t;
import org.bytedeco.javacpp.FloatPointer;

/**
 * High-level wrapper for {@code zvec_vector_query_t}.
 */
public class VectorQuery implements AutoCloseable {

    private zvec_vector_query_t handle;

    public VectorQuery() {
        this.handle = ZvecNative.zvec_vector_query_create();
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create VectorQuery");
        }
    }

    zvec_vector_query_t getHandle() {
        return handle;
    }

    public void setTopK(int topK) {
        ZvecException.throwIfError(ZvecNative.zvec_vector_query_set_topk(handle, topK));
    }

    public int getTopK() {
        return ZvecNative.zvec_vector_query_get_topk(handle);
    }

    public void setFieldName(String fieldName) {
        ZvecException.throwIfError(ZvecNative.zvec_vector_query_set_field_name(handle, NativeSupport.utf8(fieldName)));
    }

    public String getFieldName() {
        return NativeSupport.string(ZvecNative.zvec_vector_query_get_field_name(handle));
    }

    /** Set the query vector (float32).  The data is copied. */
    public void setQueryVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new ZvecException(ErrorCode.INVALID_ARGUMENT, "query vector cannot be empty");
        }
        FloatPointer data = new FloatPointer(vector);
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_query_vector(handle, data, (long) vector.length * 4));
    }

    public void setFilter(String filter) {
        ZvecException.throwIfError(ZvecNative.zvec_vector_query_set_filter(handle, NativeSupport.utf8(filter)));
    }

    public String getFilter() {
        return NativeSupport.string(ZvecNative.zvec_vector_query_get_filter(handle));
    }

    public void setIncludeVector(boolean include) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_include_vector(handle, include));
    }

    public boolean getIncludeVector() {
        return ZvecNative.zvec_vector_query_get_include_vector(handle);
    }

    public void setIncludeDocID(boolean include) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_include_doc_id(handle, include));
    }

    public boolean getIncludeDocID() {
        return ZvecNative.zvec_vector_query_get_include_doc_id(handle);
    }

    /**
     * Set the scalar fields to return.
     *
     * @param fields field names; {@code null} returns all fields, an empty
     *               array returns only the primary key / system columns
     */
    public void setOutputFields(String[] fields) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_output_fields(
                        handle, NativeSupport.strArray(fields), NativeSupport.strArrayCount(fields)));
    }

    /** Set HNSW query parameters. Ownership transfers to this query. */
    public void setHNSWParams(HnswQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_hnsw_params(handle, params.takeHandle()));
    }

    /** Set IVF query parameters. Ownership transfers to this query. */
    public void setIVFParams(IvfQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_ivf_params(handle, params.takeHandle()));
    }

    /** Set flat (brute-force) query parameters. Ownership transfers to this query. */
    public void setFlatParams(FlatQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_flat_params(handle, params.takeHandle()));
    }

    /** Set Vamana query parameters. Ownership transfers to this query. */
    public void setVamanaParams(VamanaQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_vamana_params(handle, params.takeHandle()));
    }

    /**
     * Set IVF RaBitQ query parameters (zvec &ge; v0.7.0). Ownership of the
     * params transfers to this query; the wrapper becomes inert.
     */
    public void setIvfRabitqParams(IvfRabitqQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_ivf_rabitq_params(handle, params.takeHandle()));
    }

    /**
     * Set DiskANN query parameters (zvec &ge; v0.7.0). Ownership of the
     * params transfers to this query; the wrapper becomes inert.
     */
    public void setDiskAnnParams(DiskAnnQueryParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_diskann_params(handle, params.takeHandle()));
    }

    /** Set FTS query parameters. Ownership transfers to this query. */
    public void setFtsParams(FtsQueryParams ftsParams) {
        ZvecException.throwIfError(
                ZvecNative.zvec_vector_query_set_fts_params(handle, ftsParams.takeHandle()));
    }

    public void setFts(FtsPayload fts) {
        ZvecException.throwIfError(ZvecNative.zvec_vector_query_set_fts(handle, fts.getHandle()));
    }

    public FtsPayload getFts() {
        zvec_fts_t fts = ZvecNative.zvec_vector_query_get_fts(handle);
        if (fts == null || fts.isNull()) return null;
        // The returned payload is owned by the query; do not free it.
        return new FtsPayload(fts, false);
    }

    @Override
    public void close() {
        destroy();
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_vector_query_destroy(handle);
            handle = null;
        }
    }
}
