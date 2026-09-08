package io.zvec.binding;

/**
 * Index algorithm types.  Maps to {@code ZVEC_INDEX_TYPE_*} in c_api.h.
 */
public enum IndexType {
    UNDEFINED(0),
    HNSW(1),
    IVF(2),
    FLAT(3),
    HNSW_RABITQ(4),
    DISKANN(5),
    VAMANA(6),
    IVF_RABITQ(7),
    INVERT(10),
    FTS(11);

    private final int code;

    IndexType(int code) { this.code = code; }

    public int getCode() { return code; }

    public static IndexType fromCode(int code) {
        for (IndexType it : values()) {
            if (it.code == code) return it;
        }
        return UNDEFINED;
    }

    public String toStringName() {
        return NativeSupport.string(ZvecNative.zvec_index_type_to_string(this.code));
    }
}
