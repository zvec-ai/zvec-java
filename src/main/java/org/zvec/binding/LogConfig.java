package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_log_config_t;

/**
 * High-level wrapper for {@code zvec_log_config_t}.
 * Created via the static factory methods {@link #createConsole(LogLevel)} and
 * {@link #createFile(LogLevel, String, String, int, int)}.
 */
public class LogConfig implements AutoCloseable {

    private zvec_log_config_t handle;

    private LogConfig(zvec_log_config_t handle) {
        this.handle = handle;
    }

    zvec_log_config_t getHandle() {
        return handle;
    }

    /**
     * Detach and return the native handle, transferring ownership to the caller.
     * {@link ConfigData#setLogConfig(LogConfig)} hands it to the C config, which
     * then owns it; {@link #close()} afterwards is a no-op.
     */
    zvec_log_config_t takeHandle() {
        zvec_log_config_t h = handle;
        handle = null;
        return h;
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    /** Create a console log configuration. */
    public static LogConfig createConsole(LogLevel level) {
        zvec_log_config_t ptr = ZvecNative.zvec_config_log_create_console(level.getCode());
        if (ptr == null || ptr.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create console log config");
        }
        return new LogConfig(ptr);
    }

    /** Create a file log configuration. */
    public static LogConfig createFile(LogLevel level, String dir, String basename,
                                       int fileSizeMB, int overdueDays) {
        zvec_log_config_t ptr = ZvecNative.zvec_config_log_create_file(
                level.getCode(), NativeSupport.utf8(dir), NativeSupport.utf8(basename),
                fileSizeMB, overdueDays);
        if (ptr == null || ptr.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create file log config");
        }
        return new LogConfig(ptr);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public LogLevel getLevel() {
        return LogLevel.fromCode(ZvecNative.zvec_config_log_get_level(handle));
    }

    public void setLevel(LogLevel level) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_log_set_level(handle, level.getCode()));
    }

    public boolean isFileType() {
        return ZvecNative.zvec_config_log_is_file_type(handle);
    }

    public String getDir() {
        return NativeSupport.string(ZvecNative.zvec_config_log_get_dir(handle));
    }

    public void setDir(String dir) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_log_set_dir(handle, NativeSupport.utf8(dir)));
    }

    public String getBasename() {
        return NativeSupport.string(ZvecNative.zvec_config_log_get_basename(handle));
    }

    public void setBasename(String basename) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_log_set_basename(handle, NativeSupport.utf8(basename)));
    }

    public int getFileSize() {
        return ZvecNative.zvec_config_log_get_file_size(handle);
    }

    public void setFileSize(int fileSizeMB) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_log_set_file_size(handle, fileSizeMB));
    }

    public int getOverdueDays() {
        return ZvecNative.zvec_config_log_get_overdue_days(handle);
    }

    public void setOverdueDays(int days) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_log_set_overdue_days(handle, days));
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Destroy the underlying C resource. */
    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_config_log_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
