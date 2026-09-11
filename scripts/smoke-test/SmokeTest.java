import org.zvec.binding.Collection;
import org.zvec.binding.CollectionSchema;
import org.zvec.binding.ConfigData;
import org.zvec.binding.DataType;
import org.zvec.binding.Doc;
import org.zvec.binding.FieldSchema;
import org.zvec.binding.FtsPayload;
import org.zvec.binding.IndexParams;
import org.zvec.binding.LogLevel;
import org.zvec.binding.MetricType;
import org.zvec.binding.VectorQuery;
import org.zvec.binding.Zvec;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Standalone smoke test for a built (or published) zvec-java artifact.
 *
 * <p>Deliberately not part of {@code src/main/java} or {@code src/test/java}:
 * it must not end up inside the shipped JAR, and it must run against an
 * already-built JAR rather than against freshly compiled classes. That is what
 * makes it a real deployment check - it exercises the exact artifact a consumer
 * would resolve, loading the natives through JavaCPP the way a consumer does.
 *
 * <p>What it covers, in order:
 * <ol>
 *   <li>the JNI glue and {@code zvec_c_api} actually load on this platform;</li>
 *   <li>the cppjieba dictionary bundled in the JAR extracts and registers;</li>
 *   <li>a collection can be created with HNSW, jieba-FTS and invert indexes;</li>
 *   <li>insert / flush / vector query / full-text query all return real data.</li>
 * </ol>
 *
 * <p>Run it via {@code scripts/smoke-test.sh}, which assembles the classpath.
 * Exits non-zero on the first failure so CI can gate on it.
 */
public final class SmokeTest {

    private static final int DIM = 8;
    private static final Random RAND = new Random(42);

    private SmokeTest() {
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            System.out.println();
            System.out.println("SMOKE TEST FAILED: " + t);
            t.printStackTrace(System.out);
            System.exit(1);
        }
        System.out.println();
        System.out.println("SMOKE TEST PASSED");
    }

    private static void run() throws Exception {
        banner("Environment");
        System.out.println("  os.name          = " + System.getProperty("os.name"));
        System.out.println("  os.arch          = " + System.getProperty("os.arch"));
        System.out.println("  java.version     = " + System.getProperty("java.version"));
        System.out.println("  javacpp platform = " + org.bytedeco.javacpp.Loader.getPlatform());
        System.out.println("  zvec-java jar    = "
                + jarOf(Zvec.class) + " <- " + jarOf(Collection.class));

        banner("1. Load the native libraries and initialize");
        // Touching Zvec triggers JavaCPP's Loader, which unpacks
        // libjniZvecNative and libzvec_c_api for this platform out of the JAR.
        // A glibc-too-old or wrong-arch build dies right here with an
        // UnsatisfiedLinkError, which is the failure this script exists to catch.
        try (ConfigData config = new ConfigData()) {
            config.setConsoleLog(LogLevel.WARN);
            Zvec.initialize(config);
        }
        String version = Zvec.getVersion();
        System.out.println("  zvec version     = " + version);
        check(version != null && !version.isEmpty(), "Zvec.getVersion() returned nothing");
        verifyVersion(version);

        banner("2. Extract the bundled jieba dictionary");
        check(Zvec.isJiebaDictAvailable(), "no jieba dict registered after initialize");
        String dictDir = Zvec.useBundledJiebaDict();
        System.out.println("  dict dir         = " + dictDir);
        for (String name : new String[] {"jieba.dict.utf8", "hmm_model.utf8"}) {
            File file = new File(dictDir, name);
            check(file.isFile() && file.length() > 0, "missing or empty dict file " + file);
            System.out.printf("  %-19s %,d bytes%n", name, file.length());
        }

        banner("3. Create a collection (HNSW + jieba FTS + int field)");
        File tmp = Files.createTempDirectory("zvec-smoke-").toFile();
        String dbPath = new File(tmp, "coll").getAbsolutePath();

        CollectionSchema schema = new CollectionSchema("smoke");
        try (FieldSchema vector = new FieldSchema("embedding", DataType.VECTOR_FP32, false, DIM)) {
            try (IndexParams hnsw = IndexParams.createHNSW(MetricType.COSINE, 16, 200)) {
                vector.setIndexParams(hnsw);
            }
            schema.addField(vector);
        }
        try (FieldSchema content = new FieldSchema("content", DataType.STRING, false, 0)) {
            // "jieba" is the tokenizer that needs the bundled dictionary, so this
            // is the check that the dict really reached the native layer.
            try (IndexParams fts = IndexParams.createFTS("jieba", new String[] {"lowercase"}, null)) {
                content.setIndexParams(fts);
            }
            schema.addField(content);
        }
        try (FieldSchema year = new FieldSchema("year", DataType.INT32, true, 0)) {
            schema.addField(year);
        }
        schema.validate();

        Collection collection = Zvec.createAndOpen(dbPath, schema, null);
        schema.close();
        System.out.println("  collection opened at " + dbPath);

        try {
            insertAndFlush(collection);
            queryByVector(collection);
            queryByFts(collection);

            banner("6. Collection stats and shutdown");
            System.out.println("  stats            = " + collection.getStats());
        } finally {
            collection.close();
            Zvec.shutdown();
            deleteRecursively(tmp);
        }
    }

    private static void insertAndFlush(Collection collection) {
        banner("4. Insert five documents and flush");
        String[][] rows = {
                {"doc_0", "向量数据库支持全文检索", "2020"},
                {"doc_1", "机器学习入门教程", "2021"},
                {"doc_2", "全文检索使用结巴分词", "2022"},
                {"doc_3", "深度学习与向量索引", "2023"},
                {"doc_4", "完全不相关的内容", "2024"},
        };
        List<Doc> docs = new ArrayList<>();
        for (String[] row : rows) {
            Doc doc = new Doc();
            doc.setPK(row[0]);
            doc.addVectorFP32Field("embedding", randomVector());
            doc.addStringField("content", row[1]);
            doc.addInt32Field("year", Integer.parseInt(row[2]));
            docs.add(doc);
        }
        Collection.WriteResult result = collection.insert(docs);
        System.out.println("  inserted         = " + result.successCount + "/" + rows.length);
        check(result.successCount == rows.length,
                "insert accepted " + result.successCount + " of " + rows.length + " docs");
        Doc.freeDocs(docs);
        collection.flush();
    }

    private static void queryByVector(Collection collection) {
        banner("5a. Vector query (top-3, cosine)");
        try (VectorQuery query = new VectorQuery()) {
            query.setFieldName("embedding");
            query.setTopK(3);
            query.setQueryVector(randomVector());
            List<Doc> hits = collection.query(query);
            for (Doc hit : hits) {
                System.out.printf("    pk=%s score=%.4f%n", hit.getPK(), hit.getScore());
            }
            check(hits.size() == 3, "expected 3 vector hits, got " + hits.size());
            Doc.freeDocs(hits);
        }
    }

    private static void queryByFts(Collection collection) {
        banner("5b. Full-text query through the bundled jieba tokenizer");
        try (VectorQuery query = new VectorQuery()) {
            query.setFieldName("content");
            query.setTopK(10);
            try (FtsPayload fts = new FtsPayload()) {
                fts.setMatchString("全文检索");
                query.setFts(fts);
            }
            List<Doc> hits = collection.query(query);
            for (Doc hit : hits) {
                System.out.println("    pk=" + hit.getPK());
            }
            check(!hits.isEmpty(),
                    "jieba FTS query matched nothing - the bundled dictionary is not wired up");
            Doc.freeDocs(hits);
        }
    }

    /**
     * Asserts the native library reports the version this artifact was released
     * as, when the caller supplies one via {@code --expect-version}.
     *
     * <p>zvec resolves its version from {@code git describe --tags} at configure
     * time and falls back to a deliberate dummy {@code v0.0.0} when it cannot -
     * which is what happens in CI, where the submodule is checked out detached
     * and without tags. The dummy then surfaces to every consumer through
     * {@link Zvec#getVersion()} and {@link Zvec#getVersionMajor()}, so the
     * release build passes {@code -DOVERRIDE_GIT_DESCRIBE}. This check is what
     * keeps that plumbing honest: if the override ever stops reaching cmake,
     * the smoke test fails instead of silently shipping a 0.0.0 version.
     */
    private static void verifyVersion(String reported) {
        String expected = System.getProperty("zvec.smoke.expectVersion", "").trim();
        if (expected.isEmpty()) {
            System.out.println("  version check    = skipped (no --expect-version given)");
            return;
        }
        // A describe suffix such as "-3-gabc1234" is legitimate, so only the
        // leading vX.Y.Z has to match - but it has to match exactly.
        check(reported.startsWith(expected),
                "the native library reports version '" + reported + "', expected '" + expected
                        + "' - OVERRIDE_GIT_DESCRIBE did not reach the cmake configure step");

        int[] want = parseVersion(expected);
        int[] got = {Zvec.getVersionMajor(), Zvec.getVersionMinor(), Zvec.getVersionPatch()};
        System.out.println("  version parts    = " + got[0] + "." + got[1] + "." + got[2]
                + " (expected " + want[0] + "." + want[1] + "." + want[2] + ")");
        String[] accessor = {"Major", "Minor", "Patch"};
        for (int i = 0; i < 3; i++) {
            check(got[i] == want[i], "Zvec.getVersion" + accessor[i] + "() returned " + got[i]
                    + ", expected " + want[i] + " for release " + expected);
        }
    }

    private static int[] parseVersion(String version) {
        String digits = version.startsWith("v") ? version.substring(1) : version;
        String[] parts = digits.split("[.-]");
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = i < parts.length ? Integer.parseInt(parts[i]) : 0;
        }
        return out;
    }

    private static float[] randomVector() {
        float[] vector = new float[DIM];
        double norm = 0;
        for (int i = 0; i < DIM; i++) {
            vector[i] = RAND.nextFloat() * 2 - 1;
            norm += vector[i] * vector[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < DIM; i++) {
            vector[i] /= norm;
        }
        return vector;
    }

    private static String jarOf(Class<?> type) {
        String location = type.getProtectionDomain().getCodeSource().getLocation().toString();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            System.out.println("  note: could not delete " + file);
        }
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
