package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_doc_t;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.SizeTPointer;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level wrapper for {@code zvec_doc_t}.
 *
 * <p>Documents are the fundamental unit of data.  Each document has a primary key
 * and a set of typed fields.  Use the typed add/get methods for convenient access.
 */
public class Doc implements AutoCloseable {

    private zvec_doc_t handle;
    private boolean owned = true;

    public Doc() {
        this.handle = ZvecNative.zvec_doc_create();
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create Doc");
        }
    }

    Doc(zvec_doc_t handle, boolean owned) {
        this.handle = handle;
        this.owned = owned;
    }

    zvec_doc_t getHandle() {
        return handle;
    }

    // =========================================================================
    // Metadata
    // =========================================================================

    public void setPK(String pk) {
        ZvecNative.zvec_doc_set_pk(handle, NativeSupport.utf8(pk));
    }

    public String getPK() {
        BytePointer pkPtr = ZvecNative.zvec_doc_get_pk_copy(handle);
        if (pkPtr == null || pkPtr.isNull()) {
            return "";
        }
        String pk = NativeSupport.string(pkPtr);
        ZvecNative.zvec_free(pkPtr);
        return pk;
    }

    public void setDocID(long docID) {
        ZvecNative.zvec_doc_set_doc_id(handle, docID);
    }

    public long getDocID() {
        return ZvecNative.zvec_doc_get_doc_id(handle);
    }

    public void setScore(float score) {
        ZvecNative.zvec_doc_set_score(handle, score);
    }

    public float getScore() {
        return ZvecNative.zvec_doc_get_score(handle);
    }

    public void setOperator(DocOperator op) {
        ZvecNative.zvec_doc_set_operator(handle, op.getCode());
    }

    public DocOperator getOperator() {
        return DocOperator.fromCode(ZvecNative.zvec_doc_get_operator(handle));
    }

    public int getFieldCount() {
        return (int) ZvecNative.zvec_doc_get_field_count(handle);
    }

    public boolean isEmpty() {
        return ZvecNative.zvec_doc_is_empty(handle);
    }

    public void clear() {
        ZvecNative.zvec_doc_clear(handle);
    }

    public boolean hasField(String name) {
        return ZvecNative.zvec_doc_has_field(handle, NativeSupport.utf8(name));
    }

    public boolean hasFieldValue(String name) {
        return ZvecNative.zvec_doc_has_field_value(handle, NativeSupport.utf8(name));
    }

    public boolean isFieldNull(String name) {
        return ZvecNative.zvec_doc_is_field_null(handle, NativeSupport.utf8(name));
    }

    public void setFieldNull(String name) {
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_set_field_null(handle, NativeSupport.utf8(name)));
    }

    public void removeField(String name) {
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_remove_field(handle, NativeSupport.utf8(name)));
    }

    public List<String> getFieldNames() {
        PointerPointer names = new PointerPointer(1);
        SizeTPointer count = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_names(handle, names, count));
        long n = count.get();
        List<String> result = new ArrayList<>((int) n);
        // The array pointer was written back into `names` itself (@ByPtrPtr).
        if (n > 0 && names != null && !names.isNull()) {
            names.capacity(n);
            for (long i = 0; i < n; i++) {
                result.add(NativeSupport.string(names.get(i)));
            }
            ZvecNative.zvec_free_str_array(names, n);
        }
        return result;
    }

    // =========================================================================
    // Typed field setters (using zvec_doc_add_field_by_value)
    // =========================================================================

    public void addStringField(String name, String value) {
        BytePointer valueMem = NativeSupport.utf8(value);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.STRING.getCode(),
                        valueMem, NativeSupport.utf8Length(value)));
    }

    public void addBoolField(String name, boolean value) {
        BytePointer mem = new BytePointer(1);
        mem.put(0, (byte) (value ? 1 : 0));
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.BOOL.getCode(), mem, 1));
    }

    public void addInt32Field(String name, int value) {
        IntPointer mem = new IntPointer(1).put(value);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.INT32.getCode(), mem, 4));
    }

    public void addInt64Field(String name, long value) {
        LongPointer mem = new LongPointer(1).put(value);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.INT64.getCode(), mem, 8));
    }

    public void addUint32Field(String name, long value) {
        IntPointer mem = new IntPointer(1).put((int) value);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.UINT32.getCode(), mem, 4));
    }

    public void addUint64Field(String name, long value) {
        LongPointer mem = new LongPointer(1).put(value);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.UINT64.getCode(), mem, 8));
    }

    public void addFloatField(String name, float value) {
        FloatPointer mem = new FloatPointer(1).put(value);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.FLOAT.getCode(), mem, 4));
    }

    public void addDoubleField(String name, double value) {
        DoublePointer mem = new DoublePointer(1).put(value);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.DOUBLE.getCode(), mem, 8));
    }

    /** Add a float32 vector field.  The data is copied into C-managed memory. */
    public void addVectorFP32Field(String name, float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new ZvecException(ErrorCode.INVALID_ARGUMENT, "vector cannot be empty");
        }
        FloatPointer mem = new FloatPointer(vector);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.VECTOR_FP32.getCode(),
                        mem, (long) vector.length * 4));
    }

    /** Add a binary field. */
    public void addBinaryField(String name, byte[] data) {
        if (data == null || data.length == 0) {
            throw new ZvecException(ErrorCode.INVALID_ARGUMENT, "binary data cannot be empty");
        }
        BytePointer mem = new BytePointer(data);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_add_field_by_value(
                        handle, NativeSupport.utf8(name), DataType.BINARY.getCode(), mem, data.length));
    }

    // =========================================================================
    // Typed field getters
    // =========================================================================

    public String getStringField(String name) {
        PointerPointer valueRef = new PointerPointer(1);
        SizeTPointer sizeRef = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_value_pointer(
                        handle, NativeSupport.utf8(name), DataType.STRING.getCode(), valueRef, sizeRef));
        Pointer valuePtr = valueRef.get(0);
        long size = sizeRef.get();
        if (valuePtr == null || valuePtr.isNull() || size == 0) {
            return "";
        }
        // The C API returns raw char* + size for STRING fields
        return NativeSupport.string(valuePtr);
    }

    public boolean getBoolField(String name) {
        BytePointer mem = new BytePointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_value_basic(
                        handle, NativeSupport.utf8(name), DataType.BOOL.getCode(), mem, 1));
        return mem.get(0) != 0;
    }

    public int getInt32Field(String name) {
        IntPointer mem = new IntPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_value_basic(
                        handle, NativeSupport.utf8(name), DataType.INT32.getCode(), mem, 4));
        return mem.get();
    }

    public long getInt64Field(String name) {
        LongPointer mem = new LongPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_value_basic(
                        handle, NativeSupport.utf8(name), DataType.INT64.getCode(), mem, 8));
        return mem.get();
    }

    public long getUint32Field(String name) {
        IntPointer mem = new IntPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_value_basic(
                        handle, NativeSupport.utf8(name), DataType.UINT32.getCode(), mem, 4));
        return mem.get() & 0xFFFFFFFFL;
    }

    public long getUint64Field(String name) {
        LongPointer mem = new LongPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_value_basic(
                        handle, NativeSupport.utf8(name), DataType.UINT64.getCode(), mem, 8));
        return mem.get();
    }

    public float getFloatField(String name) {
        FloatPointer mem = new FloatPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_value_basic(
                        handle, NativeSupport.utf8(name), DataType.FLOAT.getCode(), mem, 4));
        return mem.get();
    }

    public double getDoubleField(String name) {
        DoublePointer mem = new DoublePointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_value_basic(
                        handle, NativeSupport.utf8(name), DataType.DOUBLE.getCode(), mem, 8));
        return mem.get();
    }

    /** Get a float32 vector field.  Returns a copy of the underlying data. */
    public float[] getVectorFP32Field(String name) {
        PointerPointer valueRef = new PointerPointer(1);
        SizeTPointer sizeRef = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_get_field_value_pointer(
                        handle, NativeSupport.utf8(name), DataType.VECTOR_FP32.getCode(), valueRef, sizeRef));
        Pointer valuePtr = valueRef.get(0);
        long byteSize = sizeRef.get();
        if (valuePtr == null || valuePtr.isNull() || byteSize == 0) {
            return new float[0];
        }
        int count = (int) (byteSize / 4);
        FloatPointer fp = new FloatPointer(valuePtr);
        fp.capacity(count);
        float[] result = new float[count];
        fp.get(result);
        return result;
    }

    // =========================================================================
    // Serialize / Deserialize
    // =========================================================================

    public byte[] serialize() {
        PointerPointer dataRef = new PointerPointer(1);
        SizeTPointer sizeRef = new SizeTPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_serialize(handle, dataRef, sizeRef));
        Pointer data = dataRef.get(0);
        long size = sizeRef.get();
        if (data == null || data.isNull() || size == 0) {
            return new byte[0];
        }
        BytePointer bp = new BytePointer(data);
        bp.capacity(size);
        byte[] result = new byte[(int) size];
        bp.get(result);
        ZvecNative.zvec_free_uint8_array(bp);
        return result;
    }

    public static Doc deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            throw new ZvecException(ErrorCode.INVALID_ARGUMENT, "empty data");
        }
        BytePointer mem = new BytePointer(data);
        PointerPointer docRef = new PointerPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_deserialize(mem, data.length, docRef));
        return new Doc(new zvec_doc_t(docRef.get(0)), true);
    }

    public void merge(Doc other) {
        ZvecNative.zvec_doc_merge(handle, other.handle);
    }

    public long memoryUsage() {
        return ZvecNative.zvec_doc_memory_usage(handle);
    }

    public String toDetailString() {
        PointerPointer sRef = new PointerPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_doc_to_detail_string(handle, sRef));
        Pointer s = sRef.get(0);
        String result = NativeSupport.string(s);
        if (s != null && !s.isNull()) {
            ZvecNative.zvec_free(s);
        }
        return result;
    }

    /**
     * Document-level validation is not exposed by the Zvec C API; use
     * {@link CollectionSchema#validate()} / {@link FieldSchema#validate()} instead.
     */
    public void validate(CollectionSchema schema, boolean isUpdate) {
        throw new UnsupportedOperationException(
                "zvec_doc_validate is not provided by the Zvec C API; validate the schema instead");
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void destroy() {
        if (owned && handle != null && !handle.isNull()) {
            ZvecNative.zvec_doc_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }

    /** Free a list of docs. */
    public static void freeDocs(List<Doc> docs) {
        if (docs == null) return;
        for (Doc d : docs) {
            if (d != null) d.destroy();
        }
    }
}
