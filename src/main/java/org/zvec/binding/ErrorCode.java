package org.zvec.binding;

/**
 * Error codes returned by the Zvec C API.
 * Maps to {@code zvec_error_code_t} in c_api.h.
 */
public enum ErrorCode {
    OK(0, "OK"),
    NOT_FOUND(1, "NotFound"),
    ALREADY_EXISTS(2, "AlreadyExists"),
    INVALID_ARGUMENT(3, "InvalidArgument"),
    PERMISSION_DENIED(4, "PermissionDenied"),
    FAILED_PRECONDITION(5, "FailedPrecondition"),
    RESOURCE_EXHAUSTED(6, "ResourceExhausted"),
    UNAVAILABLE(7, "Unavailable"),
    INTERNAL_ERROR(8, "InternalError"),
    NOT_SUPPORTED(9, "NotSupported"),
    UNKNOWN(10, "Unknown");

    private final int code;
    private final String name;

    ErrorCode(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int getCode() {
        return code;
    }

    /** Look up an ErrorCode by its integer value. */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) return ec;
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return name + "(" + code + ")";
    }
}
