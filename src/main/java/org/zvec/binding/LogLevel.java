package org.zvec.binding;

/**
 * Log levels. Maps to {@code zvec_log_level_t} in c_api.h.
 */
public enum LogLevel {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3),
    FATAL(4);

    private final int code;

    LogLevel(int code) { this.code = code; }

    public int getCode() { return code; }

    public static LogLevel fromCode(int code) {
        for (LogLevel ll : values()) {
            if (ll.code == code) return ll;
        }
        return INFO;
    }
}
