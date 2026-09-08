package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_iterator_options_t;

import org.bytedeco.javacpp.PointerPointer;

/**
 * Options for {@link Collection#createIterator(IteratorOptions)} (zvec &ge;
 * v0.7.0).
 *
 * <p>Defaults: all scalar fields are returned and vectors are included.
 * Pass {@code null} to {@link Collection#createIterator(IteratorOptions)} to
 * use the C-level defaults without allocating an options object.
 */
public class IteratorOptions implements AutoCloseable {

    private zvec_iterator_options_t handle;

    public IteratorOptions() {
        this.handle = ZvecNative.zvec_iterator_options_create();
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create IteratorOptions");
        }
    }

    zvec_iterator_options_t getHandle() {
        return handle;
    }

    /**
     * Set the scalar fields to return.
     *
     * @param fields field names; {@code null} returns all fields, an empty
     *               array returns only the primary key / system columns
     */
    public void setOutputFields(String[] fields) {
        if (fields == null) {
            ZvecException.throwIfError(
                    ZvecNative.zvec_iterator_options_set_output_fields(
                            handle, (PointerPointer) null, 0L));
        } else {
            ZvecException.throwIfError(
                    ZvecNative.zvec_iterator_options_set_output_fields(
                            handle, NativeSupport.strArray(fields), (long) fields.length));
        }
    }

    /** Set whether to include vector fields in the returned documents. */
    public void setIncludeVector(boolean include) {
        ZvecException.throwIfError(
                ZvecNative.zvec_iterator_options_set_include_vector(handle, include));
    }

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_iterator_options_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
