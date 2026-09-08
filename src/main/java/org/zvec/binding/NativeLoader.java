package org.zvec.binding;

import org.bytedeco.javacpp.Loader;

import java.io.File;

/**
 * Centralized, three-tier native library loader for the Zvec binding.
 *
 * <p>The binding depends on two native artifacts: the JavaCPP-generated JNI
 * glue ({@code jnizvec}) and the Zvec C-API shared library
 * ({@code zvec_c_api}). This loader resolves them with the following
 * precedence, mirroring the JavaCPP runtime model:
 *
 * <ol>
 *   <li><b>Explicit path</b> &mdash; when {@code -Dzvec.native.path=/dir} (or
 *       the {@code ZVEC_NATIVE_PATH} environment variable) is set, JavaCPP is
 *       steered to resolve {@code zvec_c_api} from that directory first (via
 *       {@code pathsFirst} + {@code platform.preloadpath}). Intended for local
 *       development and custom native builds.</li>
 *   <li><b>Classpath / fat JAR</b> &mdash; the default JavaCPP behavior extracts
 *       the platform-specific libraries bundled under {@code <platform>/} on the
 *       classpath (e.g. inside a self-contained fat JAR) to a cache directory
 *       and loads them.</li>
 *   <li><b>System library path</b> &mdash; if nothing is bundled, JavaCPP falls
 *       back to the operating-system loader search path
 *       ({@code java.library.path}, {@code LD_LIBRARY_PATH}, {@code PATH}, ...).</li>
 * </ol>
 *
 * <p>Tier&nbsp;1 configuration is applied as early as possible from the generated
 * {@code ZvecNative}'s superclass initializer ({@code presets.ZvecConfig}), so it
 * takes effect regardless of which public entry point is touched first.
 * {@link #load()} simply forces eager loading and converts low-level failures
 * into an actionable {@link UnsatisfiedLinkError}.
 */
public final class NativeLoader {

    /** System property naming an explicit directory that holds the native libraries. */
    public static final String PATH_PROPERTY = "zvec.native.path";

    /** Environment variable equivalent of {@link #PATH_PROPERTY}. */
    public static final String PATH_ENV = "ZVEC_NATIVE_PATH";

    private static volatile boolean loaded;

    private NativeLoader() {
    }

    /**
     * Resolve the configured explicit native directory, or {@code null} when
     * neither {@value #PATH_PROPERTY} nor {@value #PATH_ENV} is set. The system
     * property takes precedence over the environment variable.
     */
    public static String explicitPath() {
        String dir = System.getProperty(PATH_PROPERTY);
        if (dir == null || dir.trim().isEmpty()) {
            String env = System.getenv(PATH_ENV);
            if (env != null && !env.trim().isEmpty()) {
                dir = env;
            }
        }
        return (dir == null || dir.trim().isEmpty()) ? null : dir.trim();
    }

    /**
     * Steer JavaCPP toward the explicit native directory (tier&nbsp;1) when one is
     * configured. Safe to call repeatedly and a no-op when no explicit path is
     * set, so it may run during JavaCPP code generation without side effects.
     *
     * <p>Must run before {@code ZvecNative} triggers its own {@link Loader#load()};
     * this is guaranteed by invoking it from the generated class' superclass
     * static initializer.
     */
    public static void configure() {
        String dir = explicitPath();
        if (dir == null) {
            return;
        }
        // Prefer caller-provided paths over libraries bundled on the classpath.
        System.setProperty("org.bytedeco.javacpp.pathsFirst", "true");
        // Register the directory as a place to find the preloaded dependency
        // (zvec_c_api) and its link target.
        String sep = File.pathSeparator;
        String[] keys = {
                "org.bytedeco.javacpp.platform.preloadpath",
                "org.bytedeco.javacpp.platform.linkpath",
        };
        for (String key : keys) {
            String current = System.getProperty(key, "");
            if (current.isEmpty()) {
                System.setProperty(key, dir);
            } else if (!current.contains(dir)) {
                System.setProperty(key, dir + sep + current);
            }
        }
    }

    /**
     * Eagerly load the Zvec native libraries, applying the three-tier resolution
     * described in the class documentation. Idempotent.
     *
     * @throws UnsatisfiedLinkError if no native library can be resolved
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        configure();
        try {
            Loader.load(ZvecNative.class);
            loaded = true;
        } catch (Throwable t) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError(failureMessage());
            error.initCause(t);
            throw error;
        }
    }

    /** Returns {@code true} once the native libraries have been loaded. */
    public static boolean isLoaded() {
        return loaded;
    }

    private static String failureMessage() {
        String dir = explicitPath();
        StringBuilder sb = new StringBuilder();
        sb.append("Failed to load the Zvec native libraries (jnizvec + zvec_c_api). ");
        sb.append("Resolution order: ");
        sb.append("(1) -D").append(PATH_PROPERTY).append("=/dir or the ").append(PATH_ENV).append(" env var; ");
        sb.append("(2) libraries bundled on the classpath / fat JAR; ");
        sb.append("(3) the system library path.");
        if (dir != null) {
            sb.append(" Tier 1 directory was set to '").append(dir)
              .append("' \u2014 verify it contains the zvec_c_api shared library for this platform.");
        } else {
            sb.append(" Tip: set -D").append(PATH_PROPERTY)
              .append("=/path/to/zvec/build/lib to point at a local native build.");
        }
        return sb.toString();
    }
}
