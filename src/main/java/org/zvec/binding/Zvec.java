package org.zvec.binding;

import org.zvec.binding.ZvecNative.zvec_collection_options_t;
import org.zvec.binding.ZvecNative.zvec_collection_t;
import org.zvec.binding.ZvecNative.zvec_config_data_t;
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
     * macOS uses pread; Windows uses unbuffered overlapped I/O.
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
    // Lifecycle
    // =========================================================================

    /**
     * Initialize the Zvec library.  Pass {@code null} for default config.
     *
     * <p>The {@code jieba} FTS tokenizer needs no setup: its default dictionary
     * and HMM model are embedded in the native library (zvec &ge; v0.8.0), so
     * there is nothing to extract or register. An external dictionary can still
     * be selected per field through the analyzer's
     * {@code extra_params.jieba_dict_dir}, or process-wide through the
     * {@code ZVEC_JIEBA_DICT_DIR} environment variable; the per-field value
     * wins over the environment, which wins over the embedded default.
     */
    public static void initialize(ConfigData config) {
        zvec_config_data_t configPtr = (config != null) ? config.getHandle() : null;
        ZvecException.throwIfError(ZvecNative.zvec_initialize(configPtr));
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
