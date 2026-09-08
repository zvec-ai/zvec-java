package org.zvec.binding;

/**
 * Distance metric types.  Maps to {@code ZVEC_METRIC_TYPE_*} in c_api.h.
 *
 * <p>Note: {@code zvec_metric_type_to_string} is NOT exported by the C library,
 * so {@link #toStringName()} is implemented in Java.
 */
public enum MetricType {
    UNDEFINED(0, "Undefined"),
    L2(1, "L2"),
    IP(2, "IP"),
    COSINE(3, "Cosine"),
    MIPSL2(4, "MIPSL2");

    private final int code;
    private final String name;

    MetricType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int getCode() { return code; }

    public static MetricType fromCode(int code) {
        for (MetricType mt : values()) {
            if (mt.code == code) return mt;
        }
        return UNDEFINED;
    }

    /** Implemented in Java because the C symbol is not exported. */
    public String toStringName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
