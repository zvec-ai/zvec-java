package io.zvec.binding;

/**
 * I/O backend type codes for DiskANN async disk reads.
 *
 * <p>Maps to {@code ZVEC_IO_BACKEND_TYPE_*} in {@code c_api.h}.</p>
 */
public enum IoBackendType {
    PREAD(0),
    LIBAIO(1);

    private final int code;

    IoBackendType(int code) { this.code = code; }

    public int getCode() { return code; }

    public static IoBackendType fromCode(int code) {
        for (IoBackendType t : values()) {
            if (t.code == code) return t;
        }
        return PREAD;
    }
}
