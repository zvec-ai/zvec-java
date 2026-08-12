package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_collection_options_t;
import io.zvec.binding.ZvecNative.zvec_collection_schema_t;
import io.zvec.binding.ZvecNative.zvec_collection_stats_t;
import io.zvec.binding.ZvecNative.zvec_collection_t;
import io.zvec.binding.ZvecNative.zvec_doc_t;
import io.zvec.binding.ZvecNative.zvec_field_schema_t;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.SizeTPointer;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level wrapper for {@code zvec_collection_t}.
 *
 * <p>Obtain via {@link Zvec#createAndOpen} or {@link Zvec#open}.
 * Always call {@link #close()} when done.
 */
public class Collection implements AutoCloseable {

    private zvec_collection_t handle;

    Collection(zvec_collection_t handle) {
        this.handle = handle;
    }

    zvec_collection_t getHandle() {
        return handle;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Close the collection.  Safe to call multiple times. */
    public void close() {
        if (handle != null && !handle.isNull()) {
            ZvecException.throwIfError(ZvecNative.zvec_collection_close(handle));
            handle = null;
        }
    }

    /** Destroy collection data on disk AND close the handle. */
    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecException.throwIfError(ZvecNative.zvec_collection_destroy(handle));
            ZvecNative.zvec_collection_close(handle);
            handle = null;
        }
    }

    public void flush() {
        ZvecException.throwIfError(ZvecNative.zvec_collection_flush(handle));
    }

    public void optimize() {
        ZvecException.throwIfError(ZvecNative.zvec_collection_optimize(handle));
    }

    // =========================================================================
    // Schema / Options / Stats
    // =========================================================================

    /** Get the collection schema.  Caller must destroy the returned schema. */
    public CollectionSchema getSchema() {
        PointerPointer ref = new PointerPointer(1);
        ZvecException.throwIfError(ZvecNative.zvec_collection_get_schema(handle, ref));
        return new CollectionSchema(new zvec_collection_schema_t(ref.get(0)));
    }

    /** Get the collection options.  Caller must destroy the returned options. */
    public CollectionOptions getOptions() {
        PointerPointer ref = new PointerPointer(1);
        ZvecException.throwIfError(ZvecNative.zvec_collection_get_options(handle, ref));
        return new CollectionOptions(new zvec_collection_options_t(ref.get(0)));
    }

    /** Get collection statistics.  The C stats pointer is consumed internally. */
    public CollectionStats getStats() {
        PointerPointer ref = new PointerPointer(1);
        ZvecException.throwIfError(ZvecNative.zvec_collection_get_stats(handle, ref));
        return new CollectionStats(new zvec_collection_stats_t(ref.get(0)));
    }

    // =========================================================================
    // DDL
    // =========================================================================

    public void createIndex(String fieldName, IndexParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_create_index(handle, NativeSupport.utf8(fieldName), params.getHandle()));
    }

    public void dropIndex(String fieldName) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_drop_index(handle, NativeSupport.utf8(fieldName)));
    }

    public void addColumn(FieldSchema fieldSchema, String defaultExpr) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_add_column(handle, fieldSchema.getHandle(), NativeSupport.utf8(defaultExpr)));
    }

    public void dropColumn(String columnName) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_drop_column(handle, NativeSupport.utf8(columnName)));
    }

    public void alterColumn(String columnName, String newName, FieldSchema newSchema) {
        zvec_field_schema_t newSchemaPtr = (newSchema != null) ? newSchema.getHandle() : null;
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_alter_column(handle, NativeSupport.utf8(columnName),
                        NativeSupport.utf8(newName), newSchemaPtr));
    }

    // =========================================================================
    // DML – Insert / Update / Upsert / Delete
    // =========================================================================

    /** Write result aggregate counts. */
    public static class WriteResult {
        public final long successCount;
        public final long errorCount;

        WriteResult(long successCount, long errorCount) {
            this.successCount = successCount;
            this.errorCount = errorCount;
        }

        @Override
        public String toString() {
            return "WriteResult{success=" + successCount + ", errors=" + errorCount + "}";
        }
    }

    /** Per-document write result. */
    public static class DocWriteResult {
        public final ErrorCode code;
        public final String message;

        DocWriteResult(int code, String message) {
            this.code = ErrorCode.fromCode(code);
            this.message = message;
        }

        @Override
        public String toString() {
            return "DocWriteResult{code=" + code + ", msg=" + message + "}";
        }
    }

    public WriteResult insert(List<Doc> docs) {
        if (docs == null || docs.isEmpty()) return new WriteResult(0, 0);
        PointerPointer handles = docHandles(docs);
        SizeTPointer success = new SizeTPointer(1);
        SizeTPointer error = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_insert(handle, handles, docs.size(), success, error));
        return new WriteResult(success.get(), error.get());
    }

    public WriteResult update(List<Doc> docs) {
        if (docs == null || docs.isEmpty()) return new WriteResult(0, 0);
        PointerPointer handles = docHandles(docs);
        SizeTPointer success = new SizeTPointer(1);
        SizeTPointer error = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_update(handle, handles, docs.size(), success, error));
        return new WriteResult(success.get(), error.get());
    }

    public WriteResult upsert(List<Doc> docs) {
        if (docs == null || docs.isEmpty()) return new WriteResult(0, 0);
        PointerPointer handles = docHandles(docs);
        SizeTPointer success = new SizeTPointer(1);
        SizeTPointer error = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_upsert(handle, handles, docs.size(), success, error));
        return new WriteResult(success.get(), error.get());
    }

    public WriteResult delete(List<String> pks) {
        if (pks == null || pks.isEmpty()) return new WriteResult(0, 0);
        String[] pkArray = pks.toArray(new String[0]);
        PointerPointer pkPtr = NativeSupport.strArray(pkArray);
        SizeTPointer success = new SizeTPointer(1);
        SizeTPointer error = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_delete(handle, pkPtr, pkArray.length, success, error));
        return new WriteResult(success.get(), error.get());
    }

    public void deleteByFilter(String filter) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_delete_by_filter(handle, NativeSupport.utf8(filter)));
    }

    // =========================================================================
    // DQL – Query / Fetch
    // =========================================================================

    /** Execute a vector query.  Returns a list of owned Docs; caller must free them. */
    public List<Doc> query(VectorQuery query) {
        PointerPointer results = new PointerPointer(1);
        SizeTPointer count = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_query(handle, query.getHandle(), results, count));
        return readDocArray(results, count);
    }

    /** Execute a multi-query (e.g., vector + FTS fusion).  Returns a list of owned Docs. */
    public List<Doc> query(MultiQuery query) {
        PointerPointer results = new PointerPointer(1);
        SizeTPointer count = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_multi_query(handle, query.getHandle(), results, count));
        return readDocArray(results, count);
    }

    /** Fetch documents by primary key.  Returns a list of owned Docs; caller must free them. */
    public List<Doc> fetch(List<String> pks) {
        if (pks == null || pks.isEmpty()) return new ArrayList<>();
        String[] pkArray = pks.toArray(new String[0]);
        PointerPointer pkPtr = NativeSupport.strArray(pkArray);
        PointerPointer docs = new PointerPointer(1);
        SizeTPointer foundCount = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_fetch(handle, pkPtr, pkArray.length,
                        null, 0, true, docs, foundCount));
        return readDocArray(docs, foundCount);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private PointerPointer docHandles(List<Doc> docs) {
        Pointer[] handles = new Pointer[docs.size()];
        for (int i = 0; i < docs.size(); i++) {
            handles[i] = docs.get(i).getHandle();
        }
        return new PointerPointer(handles);
    }

    private List<Doc> readDocArray(PointerPointer arrRef, SizeTPointer countRef) {
        long count = countRef.get();
        List<Doc> result = new ArrayList<>((int) count);
        // The array pointer was written back into arrRef itself (@ByPtrPtr).
        if (count > 0 && arrRef != null && !arrRef.isNull()) {
            arrRef.capacity(count);
            for (long i = 0; i < count; i++) {
                Pointer docPtr = arrRef.get(i);
                if (docPtr != null && !docPtr.isNull()) {
                    result.add(new Doc(new zvec_doc_t(docPtr), true));
                }
            }
            ZvecNative.zvec_free(arrRef);
        }
        return result;
    }
}
