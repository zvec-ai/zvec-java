package io.zvec.binding;

/**
 * Quantization types. Maps to {@code ZVEC_QUANTIZE_TYPE_*} in c_api.h.
 */
public enum QuantizeType {
    UNDEFINED(0),
    FP16(1),
    INT8(2),
    INT4(3),
    RABITQ(4);

    private final int code;

    QuantizeType(int code) { this.code = code; }

    public int getCode() { return code; }

    public static QuantizeType fromCode(int code) {
        for (QuantizeType qt : values()) {
            if (qt.code == code) return qt;
        }
        return UNDEFINED;
    }
}
