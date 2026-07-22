package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_field_schema_t;
import io.zvec.binding.ZvecNative.zvec_string_t;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;

/**
 * High-level wrapper for {@code zvec_field_schema_t}.
 */
public class FieldSchema implements AutoCloseable {

    private zvec_field_schema_t handle;
    private boolean owned = true;

    public FieldSchema(String name, DataType dataType, boolean nullable, int dimension) {
        this.handle = ZvecNative.zvec_field_schema_create(
                NativeSupport.utf8(name), dataType.getCode(), nullable, dimension);
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create FieldSchema");
        }
    }

    FieldSchema(zvec_field_schema_t handle, boolean owned) {
        this.handle = handle;
        this.owned = owned;
    }

    zvec_field_schema_t getHandle() {
        return handle;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public String getName() {
        return NativeSupport.string(ZvecNative.zvec_field_schema_get_name(handle));
    }

    public void setName(String name) {
        ZvecException.throwIfError(
                ZvecNative.zvec_field_schema_set_name(handle, NativeSupport.utf8(name)));
    }

    public DataType getDataType() {
        return DataType.fromCode(ZvecNative.zvec_field_schema_get_data_type(handle));
    }

    public void setDataType(DataType dataType) {
        ZvecException.throwIfError(
                ZvecNative.zvec_field_schema_set_data_type(handle, dataType.getCode()));
    }

    public DataType getElementDataType() {
        return DataType.fromCode(ZvecNative.zvec_field_schema_get_element_data_type(handle));
    }

    public long getElementDataSize() {
        return ZvecNative.zvec_field_schema_get_element_data_size(handle);
    }

    public boolean isVectorField() {
        return ZvecNative.zvec_field_schema_is_vector_field(handle);
    }

    public boolean isDenseVector() {
        return ZvecNative.zvec_field_schema_is_dense_vector(handle);
    }

    public boolean isSparseVector() {
        return ZvecNative.zvec_field_schema_is_sparse_vector(handle);
    }

    public boolean isNullable() {
        return ZvecNative.zvec_field_schema_is_nullable(handle);
    }

    public void setNullable(boolean nullable) {
        ZvecException.throwIfError(
                ZvecNative.zvec_field_schema_set_nullable(handle, nullable));
    }

    public boolean hasInvertIndex() {
        return ZvecNative.zvec_field_schema_has_invert_index(handle);
    }

    public boolean isArrayType() {
        return ZvecNative.zvec_field_schema_is_array_type(handle);
    }

    public int getDimension() {
        return ZvecNative.zvec_field_schema_get_dimension(handle);
    }

    public void setDimension(int dimension) {
        ZvecException.throwIfError(
                ZvecNative.zvec_field_schema_set_dimension(handle, dimension));
    }

    public IndexType getIndexType() {
        return IndexType.fromCode(ZvecNative.zvec_field_schema_get_index_type(handle));
    }

    public boolean hasIndex() {
        return ZvecNative.zvec_field_schema_has_index(handle);
    }

    /**
     * Set index parameters.  The index params are deep-copied internally;
     * the caller retains ownership and must destroy the IndexParams separately.
     */
    public void setIndexParams(IndexParams params) {
        ZvecException.throwIfError(
                ZvecNative.zvec_field_schema_set_index_params(handle, params.getHandle()));
    }

    public void validate() {
        PointerPointer errorMsg = new PointerPointer(1);
        int code = ZvecNative.zvec_field_schema_validate(handle, errorMsg);
        if (code != 0) {
            throw new ZvecException(code, readAndFreeError(errorMsg));
        }
    }

    /** Read a {@code zvec_string_t*} out-parameter as a String and free it. */
    static String readAndFreeError(PointerPointer errorMsg) {
        Pointer p = errorMsg.get(0);
        if (p == null || p.isNull()) {
            return "";
        }
        zvec_string_t s = new zvec_string_t(p);
        String msg = NativeSupport.string(ZvecNative.zvec_string_c_str(s));
        ZvecNative.zvec_free_string(s);
        return msg;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void destroy() {
        if (owned && handle != null && !handle.isNull()) {
            ZvecNative.zvec_field_schema_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
