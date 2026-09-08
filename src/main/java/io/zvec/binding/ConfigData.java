package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_config_data_t;

/**
 * High-level wrapper for {@code zvec_config_data_t}.
 */
public class ConfigData implements AutoCloseable {

    private zvec_config_data_t handle;

    public ConfigData() {
        this.handle = ZvecNative.zvec_config_data_create();
        if (handle == null || handle.isNull()) {
            throw new ZvecException(ErrorCode.INTERNAL_ERROR, "failed to create ConfigData");
        }
    }

    ConfigData(zvec_config_data_t handle) {
        this.handle = handle;
    }

    zvec_config_data_t getHandle() {
        return handle;
    }

    // =========================================================================
    // Setters / Getters
    // =========================================================================

    public void setMemoryLimit(long bytes) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_data_set_memory_limit(handle, bytes));
    }

    public long getMemoryLimit() {
        return ZvecNative.zvec_config_data_get_memory_limit(handle);
    }

    /**
     * Set the jieba dictionary directory used by the {@code jieba} FTS
     * tokenizer (directory containing {@code jieba.dict.utf8} and
     * {@code hmm_model.utf8}). Applied at {@link Zvec#initialize} time as
     * the process-wide default. Resolution priority at tokenization time:
     * per-field {@code extra_params.jieba_dict_dir} &gt; the
     * {@code ZVEC_JIEBA_DICT_DIR} environment variable &gt; this default.
     */
    public void setJiebaDictDir(String dir) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_data_set_jieba_dict_dir(handle, NativeSupport.utf8(dir)));
    }

    /** Get the jieba dictionary directory; empty string when not set. */
    public String getJiebaDictDir() {
        return NativeSupport.string(ZvecNative.zvec_config_data_get_jieba_dict_dir(handle));
    }

    /**
     * Set log configuration.  Ownership is transferred to this ConfigData;
     * do not destroy the LogConfig separately afterward.
     */
    public void setLogConfig(LogConfig logConfig) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_data_set_log_config(handle, logConfig.handle));
        // Ownership transferred – prevent double-free.
        logConfig.handle = null;
    }

    public int getLogType() {
        return ZvecNative.zvec_config_data_get_log_type(handle);
    }

    /** Convenience: set up console logging in one call. */
    public void setConsoleLog(LogLevel level) {
        try (LogConfig lc = LogConfig.createConsole(level)) {
            setLogConfig(lc);
        }
    }

    /** Convenience: set up file logging in one call. */
    public void setFileLog(LogLevel level, String dir, String basename,
                           int fileSizeMB, int overdueDays) {
        try (LogConfig lc = LogConfig.createFile(level, dir, basename, fileSizeMB, overdueDays)) {
            setLogConfig(lc);
        }
    }

    public void setQueryThreadCount(int count) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_data_set_query_thread_count(handle, count));
    }

    public int getQueryThreadCount() {
        return ZvecNative.zvec_config_data_get_query_thread_count(handle);
    }

    public void setInvertToForwardScanRatio(float ratio) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_data_set_invert_to_forward_scan_ratio(handle, ratio));
    }

    public float getInvertToForwardScanRatio() {
        return ZvecNative.zvec_config_data_get_invert_to_forward_scan_ratio(handle);
    }

    public void setBruteForceByKeysRatio(float ratio) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_data_set_brute_force_by_keys_ratio(handle, ratio));
    }

    public float getBruteForceByKeysRatio() {
        return ZvecNative.zvec_config_data_get_brute_force_by_keys_ratio(handle);
    }

    public void setOptimizeThreadCount(int count) {
        ZvecException.throwIfError(
                ZvecNative.zvec_config_data_set_optimize_thread_count(handle, count));
    }

    public int getOptimizeThreadCount() {
        return ZvecNative.zvec_config_data_get_optimize_thread_count(handle);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void destroy() {
        if (handle != null && !handle.isNull()) {
            ZvecNative.zvec_config_data_destroy(handle);
            handle = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
