package org.zvec.binding.presets;

import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.Properties;
import org.bytedeco.javacpp.tools.Info;
import org.bytedeco.javacpp.tools.InfoMap;
import org.bytedeco.javacpp.tools.InfoMapper;

/**
 * JavaCPP preset configuration for the Zvec C API.
 *
 * <p>This class drives the JavaCPP {@code Parser}, which reads
 * {@code zvec/c_api.h} and generates the low-level bindings class
 * {@code org.zvec.binding.ZvecNative}. The JavaCPP {@code Generator} then emits
 * the JNI glue for every {@code zvec_*} function and compiles it into the
 * native library {@code jnizvec}, which links against {@code zvec_c_api}.
 *
 * <p>Include and link search paths are supplied by the javacpp-maven-plugin
 * ({@code includePaths} / {@code linkPaths}) from the {@code zvec.include.path}
 * and {@code zvec.lib.path} Maven properties.
 */
@Properties(
        target = "org.zvec.binding.ZvecNative",
        value = @Platform(
                include = {"zvec/c_api.h"},
                link = "zvec_c_api"
        )
)
public class ZvecConfig implements InfoMapper {

    static {
        // Tier 1 (runtime only): when the caller sets -Dzvec.native.path=/dir
        // (or the ZVEC_NATIVE_PATH env var), steer JavaCPP to resolve the
        // zvec_c_api dependency from that directory first. This runs from the
        // superclass initializer of the generated ZvecNative, i.e. before its
        // own Loader.load(). It is intentionally self-contained (no reference
        // to org.zvec.binding.NativeLoader) so it stays safe during the JavaCPP
        // code-generation build, and it is a no-op when the path is unset.
        configureExplicitNativePath();
    }

    private static void configureExplicitNativePath() {
        String dir = System.getProperty("zvec.native.path");
        if (dir == null || dir.trim().isEmpty()) {
            String env = System.getenv("ZVEC_NATIVE_PATH");
            if (env != null && !env.trim().isEmpty()) {
                dir = env;
            }
        }
        if (dir == null || dir.trim().isEmpty()) {
            return;
        }
        dir = dir.trim();
        System.setProperty("org.bytedeco.javacpp.pathsFirst", "true");
        String sep = java.io.File.pathSeparator;
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

    @Override
    public void map(InfoMap infoMap) {
        infoMap
                // Neutralize the API export / calling-convention macros so the
                // parser does not trip over __attribute__/__declspec/__cdecl.
                .put(new Info("ZVEC_EXPORT").cppText("#define ZVEC_EXPORT").cppTypes())
                .put(new Info("ZVEC_CALL").cppText("#define ZVEC_CALL").cppTypes())
                .put(new Info("ZVEC_BUILD_SHARED", "ZVEC_USE_SHARED").define(false))
                // Include guard.
                .put(new Info("ZVEC_C_API_H").define(false))
                // Map plain C strings to Java String for ergonomic high-level wrappers
                // (JavaCPP handles the const char* <-> String marshalling and, for
                // arguments, generates the necessary temporary NUL-terminated copies).
                .put(new Info("const char*", "char*").valueTypes("String").pointerTypes("String"))
                // Exception: this returns a heap-allocated (malloc'd) buffer that the
                // caller must release via zvec_free(), so keep it as a BytePointer to
                // retain the raw address for explicit lifetime management.
                .put(new Info("zvec_doc_get_pk_copy").javaText(
                        "public static native @Cast(\"char*\") BytePointer zvec_doc_get_pk_copy(@Const zvec_doc_t doc);"));
    }
}
