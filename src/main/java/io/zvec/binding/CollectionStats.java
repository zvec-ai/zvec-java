package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_collection_stats_t;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection statistics (value object).
 */
public class CollectionStats {
    private final long docCount;
    private final int indexCount;
    private final List<String> indexNames;
    private final List<Float> indexCompleteness;

    CollectionStats(zvec_collection_stats_t statsPtr) {
        this.docCount = ZvecNative.zvec_collection_stats_get_doc_count(statsPtr);
        this.indexCount = (int) ZvecNative.zvec_collection_stats_get_index_count(statsPtr);
        this.indexNames = new ArrayList<>(indexCount);
        this.indexCompleteness = new ArrayList<>(indexCount);
        for (int i = 0; i < indexCount; i++) {
            String name = NativeSupport.string(ZvecNative.zvec_collection_stats_get_index_name(statsPtr, i));
            float completeness = ZvecNative.zvec_collection_stats_get_index_completeness(statsPtr, i);
            this.indexNames.add(name);
            this.indexCompleteness.add(completeness);
        }
        // The C stats object is consumed here
        ZvecNative.zvec_collection_stats_destroy(statsPtr);
    }

    public long getDocCount() { return docCount; }
    public int getIndexCount() { return indexCount; }
    public List<String> getIndexNames() { return indexNames; }
    public List<Float> getIndexCompleteness() { return indexCompleteness; }

    @Override
    public String toString() {
        return "CollectionStats{docCount=" + docCount +
                ", indexCount=" + indexCount +
                ", indexNames=" + indexNames + "}";
    }
}
