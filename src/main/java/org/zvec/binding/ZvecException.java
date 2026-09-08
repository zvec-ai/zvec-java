package org.zvec.binding;

/**
 * Zvec-specific exception.  Wraps the error code and optional message
 * returned by the C API.
 */
public class ZvecException extends RuntimeException {

    private final ErrorCode errorCode;

    public ZvecException(ErrorCode errorCode, String message) {
        super(errorCode + ": " + message);
        this.errorCode = errorCode;
    }

    public ZvecException(ErrorCode errorCode) {
        super(errorCode.toString());
        this.errorCode = errorCode;
    }

    public ZvecException(int code, String message) {
        this(ErrorCode.fromCode(code), message);
    }

    public ZvecException(int code) {
        this(ErrorCode.fromCode(code));
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Throw if code != ZVEC_OK (0). */
    public static void throwIfError(int code) {
        if (code != 0) {
            throw new ZvecException(code);
        }
    }

    /** Throw if code != ZVEC_OK, with additional context message. */
    public static void throwIfError(int code, String context) {
        if (code != 0) {
            throw new ZvecException(code, context);
        }
    }

    // Convenience predicates
    public boolean isNotFound()       { return errorCode == ErrorCode.NOT_FOUND; }
    public boolean isAlreadyExists()  { return errorCode == ErrorCode.ALREADY_EXISTS; }
    public boolean isInvalidArgument(){ return errorCode == ErrorCode.INVALID_ARGUMENT; }
}
