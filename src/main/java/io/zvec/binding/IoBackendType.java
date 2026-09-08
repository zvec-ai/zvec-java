package io.zvec.binding;

/**
 * I/O backend types used for DiskAnn disk reads (zvec &ge; v0.7.0).
 * Maps to {@code ZVEC_IO_BACKEND_TYPE_*} in c_api.h.
 *
 * <p>On Linux, zvec selects the first usable backend in this order:
 * {@link #IO_URING}, {@link #LIBAIO}, then {@link #PREAD}. macOS uses
 * {@link #PREAD}.
 */
public enum IoBackendType {
    /** Synchronous pread(); no async I/O. */
    PREAD(0),
    /** libaio loaded at runtime via dlopen(). */
    LIBAIO(1),
    /** io_uring via raw kernel syscalls (zero dependency). */
    IO_URING(2);

    private final int code;

    IoBackendType(int code) { this.code = code; }

    public int getCode() { return code; }

    public static IoBackendType fromCode(int code) {
        for (IoBackendType t : values()) {
            if (t.code == code) return t;
        }
        return PREAD;
    }

    /** Human-readable backend name as reported by the native library. */
    public String getName() {
        return NativeSupport.string(ZvecNative.zvec_get_io_backend_type_name(this.code));
    }
}
