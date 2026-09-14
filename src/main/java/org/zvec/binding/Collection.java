package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_collection_options_t;
import org.zvec.binding.ZvecNative.zvec_collection_schema_t;
import org.zvec.binding.ZvecNative.zvec_collection_stats_t;
import org.zvec.binding.ZvecNative.zvec_collection_t;
import org.zvec.binding.ZvecNative.zvec_doc_iterator_t;
import org.zvec.binding.ZvecNative.zvec_doc_t;
import org.zvec.binding.ZvecNative.zvec_field_schema_t;
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
 *
 * <p>After {@link #close()} every operation on this object throws a
 * {@link ZvecException} with {@link ErrorCode#FAILED_PRECONDITION} instead of
 * passing a released handle into the native layer.
 *
 * <p>{@link #close()} and {@link #destroy()} are safe to call from any thread
 * and are idempotent. The operations themselves are not synchronized: the
 * native collection is safe to use from several threads, but a caller must not
 * use a collection concurrently with closing it.
 */
public class Collection implements AutoCloseable {

    private zvec_collection_t handle;

    Collection(zvec_collection_t handle) {
        this.handle = handle;
    }

    zvec_collection_t getHandle() {
        return handle;
    }

    /**
     * The live native handle, or a {@link ZvecException} when this collection
     * has already been closed. Keeps a released handle from reaching the native
     * layer, where it would surface as a JVM crash rather than an exception.
     */
    private zvec_collection_t requireOpen() {
        zvec_collection_t current = handle;
        if (current == null || current.isNull()) {
            throw new ZvecException(ErrorCode.FAILED_PRECONDITION, "collection is closed");
        }
        return current;
    }

    /** Returns {@code true} while the native handle is still usable. */
    public boolean isOpen() {
        return handle != null && !handle.isNull();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Close the collection.  Safe to call multiple times and from any thread. */
    public synchronized void close() {
        if (handle != null && !handle.isNull()) {
            ZvecException.throwIfError(ZvecNative.zvec_collection_close(handle));
            handle = null;
        }
    }

    /** Destroy collection data on disk AND close the handle. */
    public synchronized void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecException.throwIfError(ZvecNative.zvec_collection_destroy(handle));
            ZvecNative.zvec_collection_close(handle);
            handle = null;
        }
    }

    public void flush() {
        ZvecException.throwIfError(ZvecNative.zvec_collection_flush(requireOpen()));
    }

    public void optimize() {
        ZvecException.throwIfError(ZvecNative.zvec_collection_optimize(requireOpen()));
    }

    // =========================================================================
    // Schema / Options / Stats
    // =========================================================================

    /** Get the collection schema.  Caller must destroy the returned schema. */
    public CollectionSchema getSchema() {
        PointerPointer ref = new PointerPointer(1);
        ZvecException.throwIfError(ZvecNative.zvec_collection_get_schema(requireOpen(), ref));
        return new CollectionSchema(new zvec_collection_schema_t(ref.get(0)));
    }

    /** Get the collection options.  Caller must destroy the returned options. */
    public CollectionOptions getOptions() {
        PointerPointer ref = new PointerPointer(1);
        ZvecException.throwIfError(ZvecNative.zvec_collection_get_options(requireOpen(), ref));
        return new CollectionOptions(new zvec_collection_options_t(ref.get(0)));
    }

    /** Get collection statistics.  The C stats pointer is consumed internally. */
    public CollectionStats getStats() {
        PointerPointer ref = new PointerPointer(1);
        ZvecException.throwIfError(ZvecNative.zvec_collection_get_stats(requireOpen(), ref));
        return new CollectionStats(new zvec_collection_stats_t(ref.get(0)));
    }

    // =========================================================================
    // DDL
    // =========================================================================

    public void createIndex(String fieldName, IndexParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_create_index(requireOpen(), NativeSupport.utf8(fieldName), params.getHandle()));
    }

    public void dropIndex(String fieldName) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_drop_index(requireOpen(), NativeSupport.utf8(fieldName)));
    }

    public void addColumn(FieldSchema fieldSchema, String defaultExpr) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_add_column(requireOpen(), fieldSchema.getHandle(), NativeSupport.utf8(defaultExpr)));
    }

    public void dropColumn(String columnName) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_drop_column(requireOpen(), NativeSupport.utf8(columnName)));
    }

    public void alterColumn(String columnName, String newName, FieldSchema newSchema) {
        zvec_field_schema_t newSchemaPtr = (newSchema != null) ? newSchema.getHandle() : null;
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_alter_column(requireOpen(), NativeSupport.utf8(columnName),
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
                ZvecNative.zvec_collection_insert(requireOpen(), handles, docs.size(), success, error));
        return new WriteResult(success.get(), error.get());
    }

    public WriteResult update(List<Doc> docs) {
        if (docs == null || docs.isEmpty()) return new WriteResult(0, 0);
        PointerPointer handles = docHandles(docs);
        SizeTPointer success = new SizeTPointer(1);
        SizeTPointer error = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_update(requireOpen(), handles, docs.size(), success, error));
        return new WriteResult(success.get(), error.get());
    }

    public WriteResult upsert(List<Doc> docs) {
        if (docs == null || docs.isEmpty()) return new WriteResult(0, 0);
        PointerPointer handles = docHandles(docs);
        SizeTPointer success = new SizeTPointer(1);
        SizeTPointer error = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_upsert(requireOpen(), handles, docs.size(), success, error));
        return new WriteResult(success.get(), error.get());
    }

    public WriteResult delete(List<String> pks) {
        if (pks == null || pks.isEmpty()) return new WriteResult(0, 0);
        String[] pkArray = pks.toArray(new String[0]);
        PointerPointer pkPtr = NativeSupport.strArray(pkArray);
        SizeTPointer success = new SizeTPointer(1);
        SizeTPointer error = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_delete(requireOpen(), pkPtr, pkArray.length, success, error));
        return new WriteResult(success.get(), error.get());
    }

    public void deleteByFilter(String filter) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_delete_by_filter(requireOpen(), NativeSupport.utf8(filter)));
    }

    // =========================================================================
    // DQL – Query / Fetch
    // =========================================================================

    /** Execute a vector query.  Returns a list of owned Docs; caller must free them. */
    public List<Doc> query(VectorQuery query) {
        PointerPointer results = new PointerPointer(1);
        SizeTPointer count = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_query(requireOpen(), query.getHandle(), results, count));
        return readDocArray(results, count);
    }

    /** Execute a multi-query (e.g., vector + FTS fusion).  Returns a list of owned Docs. */
    public List<Doc> query(MultiQuery query) {
        PointerPointer results = new PointerPointer(1);
        SizeTPointer count = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_multi_query(requireOpen(), query.getHandle(), results, count));
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
                ZvecNative.zvec_collection_fetch(requireOpen(), pkPtr, pkArray.length,
                        null, 0, true, docs, foundCount));
        return readDocArray(docs, foundCount);
    }

    // =========================================================================
    // Iteration (zvec >= v0.7.0)
    // =========================================================================

    /**
     * Create a snapshot document iterator over this collection.
     *
     * <p>The iterator sees an isolated snapshot taken at call time. While any
     * iterator is open, schema changes (create/drop index, add/alter/drop
     * column) and {@link #destroy()} return an error; close every iterator
     * before releasing the last collection handle.
     *
     * @param options iterator options, or {@code null} for defaults (all
     *                fields, vectors included)
     */
    public DocIterator createIterator(IteratorOptions options) {
        PointerPointer iterRef = new PointerPointer(1);
        ZvecException.throwIfError(ZvecNative.zvec_collection_create_iterator(
                requireOpen(), (options != null) ? options.getHandle() : null, iterRef));
        return new DocIterator(new zvec_doc_iterator_t(iterRef.get(0)));
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
