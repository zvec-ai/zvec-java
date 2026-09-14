package org.zvec.binding;

/**
 * Zvec-specific exception.  Wraps the error code and the message the native
 * layer recorded for the failure.
 *
 * <p>The message is read from the C API's thread-local error state
 * ({@code zvec_get_last_error_details}) when the exception is created from a
 * return code, so {@link #getMessage()} explains <em>why</em> the call was
 * rejected instead of only naming the error code.
 */
public class ZvecException extends RuntimeException {

    private final ErrorCode errorCode;

    public ZvecException(ErrorCode errorCode, String message) {
        super(format(errorCode, message));
        this.errorCode = errorCode;
    }

    public ZvecException(ErrorCode errorCode) {
        super(format(errorCode, null));
        this.errorCode = errorCode;
    }

    public ZvecException(int code, String message) {
        this(ErrorCode.fromCode(code), message);
    }

    /** Build an exception for {@code code}, carrying the native error message. */
    public ZvecException(int code) {
        this(ErrorCode.fromCode(code), NativeSupport.lastErrorMessage());
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
            throw new ZvecException(ErrorCode.fromCode(code),
                    combine(context, NativeSupport.lastErrorMessage()));
        }
    }

    // Convenience predicates
    public boolean isNotFound()       { return errorCode == ErrorCode.NOT_FOUND; }
    public boolean isAlreadyExists()  { return errorCode == ErrorCode.ALREADY_EXISTS; }
    public boolean isInvalidArgument(){ return errorCode == ErrorCode.INVALID_ARGUMENT; }

    private static String combine(String context, String nativeMessage) {
        if (context == null || context.isEmpty()) {
            return nativeMessage;
        }
        if (nativeMessage == null || nativeMessage.isEmpty()) {
            return context;
        }
        return context + " (" + nativeMessage + ")";
    }

    private static String format(ErrorCode code, String message) {
        return (message == null || message.isEmpty())
                ? code.toString()
                : code + ": " + message;
    }
}
