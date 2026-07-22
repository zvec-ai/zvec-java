package io.zvec.binding;

/**
 * Field data types.  Maps to the {@code ZVEC_DATA_TYPE_*} constants in c_api.h.
 */
public enum DataType {
    UNDEFINED(0),
    BINARY(1),
    STRING(2),
    BOOL(3),
    INT32(4),
    INT64(5),
    UINT32(6),
    UINT64(7),
    FLOAT(8),
    DOUBLE(9),
    VECTOR_BINARY32(20),
    VECTOR_BINARY64(21),
    VECTOR_FP16(22),
    VECTOR_FP32(23),
    VECTOR_FP64(24),
    VECTOR_INT4(25),
    VECTOR_INT8(26),
    VECTOR_INT16(27),
    SPARSE_VECTOR_FP16(30),
    SPARSE_VECTOR_FP32(31),
    ARRAY_BINARY(40),
    ARRAY_STRING(41),
    ARRAY_BOOL(42),
    ARRAY_INT32(43),
    ARRAY_INT64(44),
    ARRAY_UINT32(45),
    ARRAY_UINT64(46),
    ARRAY_FLOAT(47),
    ARRAY_DOUBLE(48);

    private final int code;

    DataType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static DataType fromCode(int code) {
        for (DataType dt : values()) {
            if (dt.code == code) return dt;
        }
        return UNDEFINED;
    }

    /** Return the C API string representation via the native library. */
    public String toStringName() {
        return NativeSupport.string(ZvecNative.zvec_data_type_to_string(this.code));
    }
}
