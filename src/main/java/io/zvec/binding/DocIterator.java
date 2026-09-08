package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_doc_iterator_t;
import io.zvec.binding.ZvecNative.zvec_doc_t;
import org.bytedeco.javacpp.PointerPointer;

/**
 * A snapshot document iterator over a collection (zvec &ge; v0.7.0), created
 * via {@link Collection#createIterator(IteratorOptions)}.
 *
 * <p>The iterator takes an isolated snapshot at creation time. Always
 * {@link #close()} the iterator before releasing the last handle of its
 * collection; while an iterator is open, schema changes and destroy on the
 * collection return an error.
 *
 * <p>Typical usage:
 * <pre>{@code
 * try (DocIterator it = collection.createIterator(null)) {
 *     Doc doc;
 *     while ((doc = it.next()) != null) {
 *         try {
 *             // ... read doc ...
 *         } finally {
 *             doc.close();
 *         }
 *     }
 * }
 * }</pre>
 */
public class DocIterator implements AutoCloseable {

    private zvec_doc_iterator_t handle;

    DocIterator(zvec_doc_iterator_t handle) {
        this.handle = handle;
    }

    zvec_doc_iterator_t getHandle() {
        return handle;
    }

    /**
     * Advance the iterator and return the next document, or {@code null} when
     * the end of the snapshot is reached (EOF). The returned {@link Doc} is
     * owned by the caller; release it with {@link Doc#close()} when done.
     */
    public Doc next() {
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INVALID_ARGUMENT, "iterator is closed");
        }
        PointerPointer docRef = new PointerPointer(1);
        ZvecException.throwIfError(ZvecNative.zvec_doc_iterator_next(handle, docRef));
        if (docRef.isNull() || docRef.get(0) == null || docRef.get(0).isNull()) {
            return null; // EOF
        }
        return new Doc(new zvec_doc_t(docRef.get(0)), true);
    }

    /** Close the iterator and release all its resources. Idempotent. */
    @Override
    public void close() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_doc_iterator_close(handle);
            handle = null;
        }
    }
}
