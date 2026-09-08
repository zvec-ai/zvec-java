package io.zvec.binding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal helper that materializes the jieba FTS dictionary files bundled
 * inside the zvec-java JAR (under {@code zvec/jieba_dict/}) onto disk, so
 * the native {@code jieba} tokenizer can read them.
 *
 * <p>The zvec CMake build bundles {@code jieba.dict.utf8} and
 * {@code hmm_model.utf8} from cppjieba into every SDK package; the Java
 * build mirrors that by packing the same two files as classpath resources
 * and extracting them on first use into a stable per-version cache
 * directory (default {@code ~/.zvec/jieba_dict/<version>}, falling back to
 * {@code <tmpdir>/zvec-java/jieba_dict/<version>} when the home directory
 * is not writable).
 *
 * <p>Package-private; not part of the public API. Use
 * {@link Zvec#useBundledJiebaDict()} or rely on the automatic setup
 * performed by {@link Zvec#initialize(ConfigData)}.
 */
final class JiebaDictSupport {

    /** Classpath directory holding the bundled dictionary files. */
    static final String RESOURCE_DIR = "zvec/jieba_dict";

    /** Dictionary files required by the native jieba tokenizer. */
    static final String[] DICT_FILES = {"jieba.dict.utf8", "hmm_model.utf8"};

    /** System property overriding the extraction cache directory. */
    static final String CACHE_DIR_PROPERTY = "zvec.jieba.cache.dir";

    /** Environment variable overriding the extraction cache directory. */
    static final String CACHE_DIR_ENV = "ZVEC_JIEBA_CACHE_DIR";

    private JiebaDictSupport() {
    }

    /** Returns true when the dictionary resources are present on the classpath. */
    static boolean isBundledDictAvailable() {
        return JiebaDictSupport.class.getClassLoader()
                .getResource(RESOURCE_DIR + "/" + DICT_FILES[0]) != null;
    }

    /**
     * Extract the bundled dictionary files into a cache directory (a no-op
     * for files that are already present) and return that directory. Safe to
     * call from multiple JVMs concurrently.
     *
     * <p>Candidate directories, in order: the explicit override
     * ({@value #CACHE_DIR_PROPERTY} / {@value #CACHE_DIR_ENV}),
     * {@code ~/.zvec/jieba_dict/<version>}, then
     * {@code <tmpdir>/zvec-java/jieba_dict/<version>}.
     *
     * @throws ZvecException when no candidate directory is usable
     */
    static String extractBundledDict() {
        IOException lastFailure = null;
        for (Path dir : candidateDirs()) {
            try {
                extractInto(dir);
                return dir.toString();
            } catch (IOException e) {
                lastFailure = e;
            }
        }
        throw new ZvecException(ErrorCode.INTERNAL_ERROR,
                "failed to extract bundled jieba dict: "
                        + (lastFailure != null ? lastFailure.getMessage() : "no candidate directory"));
    }

    private static void extractInto(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (String name : DICT_FILES) {
            Path target = dir.resolve(name);
            if (Files.exists(target) && Files.size(target) > 0) {
                continue;
            }
            extractOne(name, dir, target);
        }
    }

    private static void extractOne(String name, Path dir, Path target) throws IOException {
        try (InputStream in = JiebaDictSupport.class.getClassLoader()
                .getResourceAsStream(RESOURCE_DIR + "/" + name)) {
            if (in == null) {
                throw new IOException("resource " + RESOURCE_DIR + "/" + name
                        + " not found on the classpath");
            }
            // Write to a unique temp file first, then move into place so
            // concurrent JVMs never observe a half-written dictionary.
            Path tmp = Files.createTempFile(dir, name, ".tmp");
            try {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException e) {
                    // Another JVM won the race; the existing file is valid.
                    Files.deleteIfExists(tmp);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
    }

    private static List<Path> candidateDirs() {
        List<Path> candidates = new ArrayList<>(3);
        String override = System.getProperty(CACHE_DIR_PROPERTY);
        if (override == null || override.trim().isEmpty()) {
            override = System.getenv(CACHE_DIR_ENV);
        }
        if (override != null && !override.trim().isEmpty()) {
            candidates.add(Paths.get(override.trim()));
            return candidates; // explicit override: no silent fallbacks
        }
        String version = Zvec.getVersion();
        String versionDir = (version == null || version.isEmpty()) ? "unknown" : version;
        String home = System.getProperty("user.home");
        if (home != null && !home.isEmpty()) {
            candidates.add(Paths.get(home, ".zvec", "jieba_dict", versionDir));
        }
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null && !tmp.isEmpty()) {
            candidates.add(Paths.get(tmp, "zvec-java", "jieba_dict", versionDir));
        }
        return candidates;
    }
}
