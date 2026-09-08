package io.zvec.binding;

import io.zvec.binding.ZvecNative.zvec_collection_options_t;
import io.zvec.binding.ZvecNative.zvec_collection_t;
import io.zvec.binding.ZvecNative.zvec_config_data_t;
import org.bytedeco.javacpp.PointerPointer;

/**
 * Top-level entry point for the Zvec Java binding.
 *
 * <p>Provides library lifecycle ({@link #initialize}, {@link #shutdown}),
 * version information, and collection factory methods.
 *
 * <p>Typical usage:
 * <pre>{@code
 * Zvec.initialize(null);
 * try (Collection coll = Zvec.createAndOpen("/tmp/mydb", schema, null)) {
 *     // ...
 * }
 * Zvec.shutdown();
 * }</pre>
 */
public final class Zvec {

    static {
        // Eagerly resolve the native libraries through the three-tier loader so
        // that any failure surfaces here with an actionable message instead of a
        // raw UnsatisfiedLinkError deep inside a later native call.
        NativeLoader.load();
    }

    private Zvec() {} // utility class

    // =========================================================================
    // Version
    // =========================================================================

    public static String getVersion() {
        return NativeSupport.string(ZvecNative.zvec_get_version());
    }

    public static int getVersionMajor() {
        return ZvecNative.zvec_get_version_major();
    }

    public static int getVersionMinor() {
        return ZvecNative.zvec_get_version_minor();
    }

    public static int getVersionPatch() {
        return ZvecNative.zvec_get_version_patch();
    }

    public static boolean checkVersion(int major, int minor, int patch) {
        return ZvecNative.zvec_check_version(major, minor, patch);
    }

    // =========================================================================
    // I/O backend introspection (zvec >= v0.7.0)
    // =========================================================================

    /**
     * Get the I/O backend used for DiskAnn disk reads. On Linux zvec selects
     * the first usable backend in this order: io_uring, libaio, pread;
     * macOS uses pread.
     */
    public static IoBackendType getIoBackendType() {
        return IoBackendType.fromCode(ZvecNative.zvec_get_io_backend_type());
    }

    /** Returns the human-readable name of the given I/O backend type. */
    public static String getIoBackendTypeName(IoBackendType type) {
        return NativeSupport.string(ZvecNative.zvec_get_io_backend_type_name(type.getCode()));
    }

    /**
     * Human-readable description of the current I/O backend. On Linux with
     * the pread fallback it also explains how to enable an async backend.
     */
    public static String getIoBackendDescription() {
        return NativeSupport.string(ZvecNative.zvec_get_io_backend_description());
    }

    // =========================================================================
    // Jieba FTS dictionary
    // =========================================================================

    /**
     * Set the process-wide default jieba dictionary directory (a directory
     * containing {@code jieba.dict.utf8} and {@code hmm_model.utf8}).
     * Per-field {@code extra_params.jieba_dict_dir} and the
     * {@code ZVEC_JIEBA_DICT_DIR} environment variable take precedence.
     */
    public static void setDefaultJiebaDictDir(String dir) {
        ZvecNative.zvec_set_default_jieba_dict_dir(NativeSupport.utf8(dir));
    }

    /** Get the process-wide default jieba dictionary directory. */
    public static String getDefaultJiebaDictDir() {
        return NativeSupport.string(ZvecNative.zvec_get_default_jieba_dict_dir());
    }

    /**
     * Extract the jieba dictionary files bundled in this JAR (under
     * {@code zvec/jieba_dict/}) into a stable cache directory and register
     * that directory as the process-wide default. Idempotent.
     *
     * @return the directory holding the dictionary files
     * @throws ZvecException when the JAR does not bundle the dictionary
     */
    public static String useBundledJiebaDict() {
        if (!JiebaDictSupport.isBundledDictAvailable()) {
            throw new ZvecException(ErrorCode.INVALID_ARGUMENT,
                    "no bundled jieba dict found on the classpath (expected zvec/jieba_dict/jieba.dict.utf8)");
        }
        String dir = JiebaDictSupport.extractBundledDict();
        setDefaultJiebaDictDir(dir);
        return dir;
    }

    /**
     * Returns true when a jieba dictionary source is configured: a
     * process-wide default, the {@code ZVEC_JIEBA_DICT_DIR} environment
     * variable, or the dictionary bundled in this JAR.
     */
    public static boolean isJiebaDictAvailable() {
        String def = getDefaultJiebaDictDir();
        if (def != null && !def.isEmpty()) {
            return true;
        }
        String env = System.getenv("ZVEC_JIEBA_DICT_DIR");
        if (env != null && !env.trim().isEmpty()) {
            return true;
        }
        return JiebaDictSupport.isBundledDictAvailable();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Initialize the Zvec library.  Pass {@code null} for default config.
     *
     * <p>When no jieba dictionary source is configured (neither in
     * {@code config}, nor via {@code ZVEC_JIEBA_DICT_DIR}, nor via
     * {@link #setDefaultJiebaDictDir}), the dictionary bundled in the JAR is
     * automatically extracted and registered so the {@code jieba} FTS
     * tokenizer works out of the box.
     */
    public static void initialize(ConfigData config) {
        ensureJiebaDictConfigured(config);
        zvec_config_data_t configPtr = (config != null) ? config.getHandle() : null;
        ZvecException.throwIfError(ZvecNative.zvec_initialize(configPtr));
    }

    private static void ensureJiebaDictConfigured(ConfigData config) {
        if (config != null) {
            String dir = config.getJiebaDictDir();
            if (dir != null && !dir.isEmpty()) {
                return; // explicitly configured by the caller
            }
        }
        String env = System.getenv("ZVEC_JIEBA_DICT_DIR");
        if (env != null && !env.trim().isEmpty()) {
            return; // resolved by the native tokenizer from the environment
        }
        String def = getDefaultJiebaDictDir();
        if (def != null && !def.isEmpty()) {
            return; // process-wide default already registered
        }
        if (JiebaDictSupport.isBundledDictAvailable()) {
            try {
                useBundledJiebaDict();
            } catch (RuntimeException e) {
                // Dictionary setup is a convenience: never let it break
                // initialization (e.g. read-only home/tmp on locked-down
                // hosts). Users of the jieba FTS tokenizer can still point
                // zvec at a dictionary via ConfigData.setJiebaDictDir(),
                // Zvec.setDefaultJiebaDictDir() or ZVEC_JIEBA_DICT_DIR.
                System.err.println("[zvec-java] bundled jieba dict not activated: "
                        + e.getMessage());
            }
        }
    }

    /** Shutdown the Zvec library and release all global resources. */
    public static void shutdown() {
        ZvecException.throwIfError(ZvecNative.zvec_shutdown());
    }

    /** Returns true if the library has been initialized. */
    public static boolean isInitialized() {
        return ZvecNative.zvec_is_initialized();
    }

    /** Clear the last error status. */
    public static void clearError() {
        ZvecNative.zvec_clear_error();
    }

    // =========================================================================
    // Collection factory methods
    // =========================================================================

    /** Create a new collection at the given path and open it. */
    public static Collection createAndOpen(String path, CollectionSchema schema,
                                           CollectionOptions options) {
        zvec_collection_options_t optionsPtr = (options != null) ? options.getHandle() : null;
        PointerPointer collRef = new PointerPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_create_and_open(
                        NativeSupport.utf8(path), schema.getHandle(), optionsPtr, collRef));
        return new Collection(new zvec_collection_t(collRef.get(0)));
    }

    /** Open an existing collection. */
    public static Collection open(String path, CollectionOptions options) {
        zvec_collection_options_t optionsPtr = (options != null) ? options.getHandle() : null;
        PointerPointer collRef = new PointerPointer(1);
        ZvecException.throwIfError(
                ZvecNative.zvec_collection_open(NativeSupport.utf8(path), optionsPtr, collRef));
        return new Collection(new zvec_collection_t(collRef.get(0)));
    }
}
