package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_collection_schema_t;
import org.zvec.binding.ZvecNative.zvec_field_schema_t;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.SizeTPointer;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level wrapper for {@code zvec_collection_schema_t}.
 */
public class CollectionSchema implements AutoCloseable {

    private zvec_collection_schema_t handle;

    public CollectionSchema(String name) {
        this.handle = ZvecNative.zvec_collection_schema_create(NativeSupport.utf8(name));
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create CollectionSchema");
        }
    }

    CollectionSchema(zvec_collection_schema_t handle) {
        this.handle = handle;
    }

    zvec_collection_schema_t getHandle() {
        return handle;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public String getName() {
        return NativeSupport.string(ZvecNative.zvec_collection_schema_get_name(handle));
    }

    public void setName(String name) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_schema_set_name(handle, NativeSupport.utf8(name)));
    }

    /** Add a field.  The field schema is deep-copied; caller retains ownership. */
    public void addField(FieldSchema field) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_schema_add_field(handle, field.getHandle()));
    }

    public void alterField(String fieldName, FieldSchema newField) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_schema_alter_field(handle, NativeSupport.utf8(fieldName), newField.getHandle()));
    }

    public void dropField(String fieldName) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_schema_drop_field(handle, NativeSupport.utf8(fieldName)));
    }

    public boolean hasField(String fieldName) {
        return ZvecNative.zvec_collection_schema_has_field(handle, NativeSupport.utf8(fieldName));
    }

    /** Returns a non-owning FieldSchema. Do NOT destroy it. */
    public FieldSchema getField(String fieldName) {
        zvec_field_schema_t ptr = ZvecNative.zvec_collection_schema_get_field(handle, NativeSupport.utf8(fieldName));
        return (ptr == null || ptr.isNull()) ? null : new FieldSchema(ptr, false);
    }

    /** Returns a non-owning FieldSchema for a forward (scalar) field. */
    public FieldSchema getForwardField(String fieldName) {
        zvec_field_schema_t ptr = ZvecNative.zvec_collection_schema_get_forward_field(handle, NativeSupport.utf8(fieldName));
        return (ptr == null || ptr.isNull()) ? null : new FieldSchema(ptr, false);
    }

    /** Returns a non-owning FieldSchema for a vector field. */
    public FieldSchema getVectorField(String fieldName) {
        zvec_field_schema_t ptr = ZvecNative.zvec_collection_schema_get_vector_field(handle, NativeSupport.utf8(fieldName));
        return (ptr == null || ptr.isNull()) ? null : new FieldSchema(ptr, false);
    }

    /** Get all field names. */
    public List<String> getAllFieldNames() {
        PointerPointer names = new PointerPointer(1);
        SizeTPointer count = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_schema_get_all_field_names(handle, names, count));
        return readStringArrayAndFree(names, count);
    }

    public long getMaxDocCountPerSegment() {
        return ZvecNative.zvec_collection_schema_get_max_doc_count_per_segment(handle);
    }

    public void setMaxDocCountPerSegment(long maxDocCount) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_schema_set_max_doc_count_per_segment(handle, maxDocCount));
    }

    public void addIndex(String fieldName, IndexParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_schema_add_index(handle, NativeSupport.utf8(fieldName), params.getHandle()));
    }

    public void dropIndex(String fieldName) {
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_schema_drop_index(handle, NativeSupport.utf8(fieldName)));
    }

    public boolean hasIndex(String fieldName) {
        return ZvecNative.zvec_collection_schema_has_index(handle, NativeSupport.utf8(fieldName));
    }

    public void validate() {
        PointerPointer errorMsg = new PointerPointer(1);
        int code = ZvecNative.zvec_collection_schema_validate(handle, errorMsg);
        if (code != 0) {
            throw new ZvecException(code, FieldSchema.readAndFreeError(errorMsg));
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /** Read a C string array (const char**) and free the array (but not the strings). */
    private List<String> readStringArrayAndFree(PointerPointer arrRef, SizeTPointer countRef) {
        long count = countRef.get();
        List<String> result = new ArrayList<>((int) count);
        // The array pointer was written back into arrRef itself (@ByPtrPtr).
        if (count > 0 && arrRef != null && !arrRef.isNull()) {
            arrRef.capacity(count);
            for (long i = 0; i < count; i++) {
                result.add(NativeSupport.string(arrRef.get(i)));
            }
            ZvecNative.zvec_free(arrRef);
        }
        return result;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_collection_schema_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
