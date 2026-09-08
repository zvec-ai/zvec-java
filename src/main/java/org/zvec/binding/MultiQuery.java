package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_multi_query_t;
import org.bytedeco.javacpp.DoublePointer;

/**
 * High-level wrapper for {@code zvec_multi_query_t}.
 *
 * <p>Combines multiple {@link SubQuery} instances (e.g., vector + FTS) and
 * supports re-ranking strategies such as RRF and weighted fusion.</p>
 */
public class MultiQuery implements AutoCloseable {

    private zvec_multi_query_t handle;

    public MultiQuery() {
        this.handle = ZvecNative.zvec_multi_query_create();
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create MultiQuery");
        }
    }

    zvec_multi_query_t getHandle() {
        return handle;
    }

    /** Add a sub-query. The sub-query is copied; caller retains ownership. */
    public void addSubQuery(SubQuery subQuery) {
        ZvecException.throwIfError(ZvecNative.zvec_multi_query_add_sub_query(handle, subQuery.getHandle()));
    }

    public long getSubQueryCount() {
        return ZvecNative.zvec_multi_query_get_sub_query_count(handle);
    }

    public void setTopk(int topk) {
        ZvecException.throwIfError(ZvecNative.zvec_multi_query_set_topk(handle, topk));
    }

    public int getTopk() {
        return ZvecNative.zvec_multi_query_get_topk(handle);
    }

    public void setFilter(String filter) {
        ZvecException.throwIfError(ZvecNative.zvec_multi_query_set_filter(handle, NativeSupport.utf8(filter)));
    }

    public String getFilter() {
        return NativeSupport.string(ZvecNative.zvec_multi_query_get_filter(handle));
    }

    public void setIncludeVector(boolean include) {
        ZvecException.throwIfError(ZvecNative.zvec_multi_query_set_include_vector(handle, include));
    }

    public boolean getIncludeVector() {
        return ZvecNative.zvec_multi_query_get_include_vector(handle);
    }

    public void setOutputFields(String[] fields) {
        ZvecException.throwIfError(
                ZvecNative.zvec_multi_query_set_output_fields(handle, NativeSupport.strArray(fields), fields.length));
    }

    /** Enable reciprocal rank fusion (RRF) re-ranking. */
    public void setRerankRrf(int rankConstant) {
        ZvecException.throwIfError(ZvecNative.zvec_multi_query_set_rerank_rrf(handle, rankConstant));
    }

    /** Enable weighted fusion re-ranking. */
    public void setRerankWeighted(double[] weights) {
        DoublePointer wp = new DoublePointer(weights);
        ZvecException.throwIfError(ZvecNative.zvec_multi_query_set_rerank_weighted(handle, wp, weights.length));
    }

    @Override
    public void close() {
        destroy();
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_multi_query_destroy(handle);
            handle = null;
        }
    }
}
