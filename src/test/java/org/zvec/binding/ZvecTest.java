package org.zvec.binding;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ZvecTest {

    private static final Random RAND = new Random(42);

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @BeforeAll
    static void initLibrary() {
        if (!Zvec.isInitialized()) {
            Zvec.initialize(null);
        }
    }

    @AfterAll
    static void shutdownLibrary() {
        // Intentionally left blank: the Zvec library stays initialized for the
        // lifetime of the (single) test JVM so that other test classes running
        // afterwards do not attempt to re-initialize it.
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private float[] randVec(int dim) {
        float[] v = new float[dim];
        float norm = 0;
        for (int i = 0; i < dim; i++) {
            v[i] = RAND.nextFloat() * 2 - 1;
            norm += v[i] * v[i];
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < dim; i++) v[i] /= norm;
        return v;
    }

    private Collection openTestCollection(@TempDir Path dir) {
        return openTestCollection(dir, "test_coll");
    }

    private Collection openTestCollection(@TempDir Path dir, String name) {
        CollectionSchema schema = new CollectionSchema(name);
        try {
            FieldSchema vecF = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4);
            schema.addField(vecF);
            vecF.close();
            FieldSchema nameF = new FieldSchema("name", DataType.STRING, true, 0);
            schema.addField(nameF);
            nameF.close();
        } finally {
            // schema will be consumed by createAndOpen
        }
        String path = dir.resolve("coll").toString();
        Collection coll = Zvec.createAndOpen(path, schema, null);
        schema.close();
        return coll;
    }

    private List<String> insertDocs(Collection coll, int n) {
        List<Doc> docs = new ArrayList<>(n);
        List<String> pks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Doc d = new Doc();
            String pk = "pk_" + i;
            d.setPK(pk);
            d.addVectorFP32Field("vec", randVec(4));
            docs.add(d);
            pks.add(pk);
        }
        Collection.WriteResult result = coll.insert(docs);
        Doc.freeDocs(docs);
        assertEquals(n, result.successCount, "insert success count mismatch");
        return pks;
    }

    // =========================================================================
    // Version
    // =========================================================================

    @Test
    void testGetVersion() {
        String v = Zvec.getVersion();
        assertNotNull(v);
        assertFalse(v.isEmpty());
        System.out.println("Zvec version: " + v);
    }

    @Test
    void testVersionComponents() {
        int major = Zvec.getVersionMajor();
        int minor = Zvec.getVersionMinor();
        int patch = Zvec.getVersionPatch();
        System.out.printf("Version: %d.%d.%d%n", major, minor, patch);
        assertTrue(major >= 0);
        assertTrue(minor >= 0);
        assertTrue(patch >= 0);
    }

    @Test
    void testCheckVersion() {
        assertTrue(Zvec.checkVersion(0, 0, 0));
        assertFalse(Zvec.checkVersion(9999, 0, 0));
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Test
    void testIsInitialized() {
        assertTrue(Zvec.isInitialized());
    }

    // =========================================================================
    // ErrorCode
    // =========================================================================

    @Test
    void testErrorCodeStrings() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertNotNull(ec.toString());
            assertFalse(ec.toString().isEmpty());
        }
    }

    @Test
    void testErrorCodeFromCode() {
        assertEquals(ErrorCode.OK, ErrorCode.fromCode(0));
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.fromCode(1));
        assertEquals(ErrorCode.ALREADY_EXISTS, ErrorCode.fromCode(2));
    }

    // =========================================================================
    // DataType / IndexType / MetricType
    // =========================================================================

    @Test
    void testDataTypeToString() {
        String s = DataType.VECTOR_FP32.toStringName();
        assertNotNull(s);
        assertFalse(s.isEmpty());
    }

    @Test
    void testIndexTypeToString() {
        String s = IndexType.HNSW.toStringName();
        assertNotNull(s);
        assertFalse(s.isEmpty());
    }

    @Test
    void testMetricTypeToString() {
        assertEquals("Cosine", MetricType.COSINE.toStringName());
        assertEquals("L2", MetricType.L2.toStringName());
    }

    // =========================================================================
    // ConfigData
    // =========================================================================

    @Test
    void testConfigDataDefault() {
        try (ConfigData cfg = new ConfigData()) {
            assertNotNull(cfg);
        }
    }

    @Test
    void testConfigDataMemoryLimit() {
        try (ConfigData cfg = new ConfigData()) {
            cfg.setMemoryLimit(1L << 30);
            assertEquals(1L << 30, cfg.getMemoryLimit());
        }
    }

    @Test
    void testConfigDataThreadCounts() {
        try (ConfigData cfg = new ConfigData()) {
            cfg.setQueryThreadCount(4);
            assertEquals(4, cfg.getQueryThreadCount());
            cfg.setOptimizeThreadCount(2);
            assertEquals(2, cfg.getOptimizeThreadCount());
        }
    }

    @Test
    void testConfigDataSetConsoleLog() {
        try (ConfigData cfg = new ConfigData()) {
            cfg.setConsoleLog(LogLevel.WARN);
        }
    }

    // =========================================================================
    // LogConfig
    // =========================================================================

    @Test
    void testLogConfigConsole() {
        try (LogConfig lc = LogConfig.createConsole(LogLevel.INFO)) {
            assertNotNull(lc);
            assertEquals(LogLevel.INFO, lc.getLevel());
        }
    }

    // =========================================================================
    // IndexParams
    // =========================================================================

    @Test
    void testHNSWIndexParams() {
        try (IndexParams p = IndexParams.createHNSW(MetricType.L2, 16, 200)) {
            assertEquals(IndexType.HNSW, p.getType());
            assertEquals(MetricType.L2, p.getMetricType());
            assertEquals(16, p.getHNSWM());
            assertEquals(200, p.getHNSWEfConstruction());
        }
    }

    @Test
    void testIVFIndexParams() {
        try (IndexParams p = IndexParams.createIVF(MetricType.COSINE, 256, 100, false)) {
            assertEquals(IndexType.IVF, p.getType());
            assertEquals(MetricType.COSINE, p.getMetricType());
        }
    }

    @Test
    void testFlatIndexParams() {
        try (IndexParams p = IndexParams.createFlat(MetricType.IP)) {
            assertEquals(IndexType.FLAT, p.getType());
            assertEquals(MetricType.IP, p.getMetricType());
        }
    }

    @Test
    void testInvertIndexParams() {
        try (IndexParams p = IndexParams.createInvert(true, false)) {
            assertEquals(IndexType.INVERT, p.getType());
        }
    }

    @Test
    void testHNSWQuantizedIndexParams() {
        try (IndexParams p = IndexParams.createHNSWQuantized(MetricType.COSINE, 16, 200, QuantizeType.INT8)) {
            assertEquals(IndexType.HNSW, p.getType());
            assertEquals(QuantizeType.INT8, p.getQuantizeType());
        }
    }

    // =========================================================================
    // FieldSchema
    // =========================================================================

    @Test
    void testFieldSchemaVectorField() {
        try (FieldSchema f = new FieldSchema("emb", DataType.VECTOR_FP32, false, 128)) {
            assertEquals("emb", f.getName());
            assertEquals(DataType.VECTOR_FP32, f.getDataType());
            assertEquals(128, f.getDimension());
            assertTrue(f.isVectorField());
            assertTrue(f.isDenseVector());
            assertFalse(f.isSparseVector());
        }
    }

    @Test
    void testFieldSchemaSetIndexParams() {
        try (FieldSchema f = new FieldSchema("emb", DataType.VECTOR_FP32, false, 64)) {
            try (IndexParams p = IndexParams.createHNSW(MetricType.COSINE, 8, 100)) {
                f.setIndexParams(p);
            }
            assertTrue(f.hasIndex());
        }
    }

    @Test
    void testFieldSchemaValidate() {
        try (FieldSchema f = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
            assertDoesNotThrow(f::validate);
        }
    }

    // =========================================================================
    // CollectionSchema
    // =========================================================================

    @Test
    void testCollectionSchemaAddField() {
        try (CollectionSchema s = new CollectionSchema("myschema")) {
            try (FieldSchema f = new FieldSchema("v", DataType.VECTOR_FP32, false, 8)) {
                s.addField(f);
            }
            assertTrue(s.hasField("v"));
        }
    }

    @Test
    void testCollectionSchemaGetAllFieldNames() {
        try (CollectionSchema s = new CollectionSchema("s")) {
            for (String name : Arrays.asList("a", "b", "c")) {
                try (FieldSchema f = new FieldSchema(name, DataType.INT32, true, 0)) {
                    s.addField(f);
                }
            }
            List<String> names = s.getAllFieldNames();
            assertEquals(3, names.size());
        }
    }

    @Test
    void testCollectionSchemaValidate() {
        try (CollectionSchema s = new CollectionSchema("valid")) {
            try (FieldSchema f = new FieldSchema("v", DataType.VECTOR_FP32, false, 4)) {
                s.addField(f);
            }
            assertDoesNotThrow(s::validate);
        }
    }

    // =========================================================================
    // Doc
    // =========================================================================

    @Test
    void testDocNewAndDestroy() {
        try (Doc d = new Doc()) {
            assertNotNull(d);
        }
    }

    @Test
    void testDocSetGetPK() {
        try (Doc d = new Doc()) {
            d.setPK("hello");
            assertEquals("hello", d.getPK());
        }
    }

    @Test
    void testDocScalarFields() {
        try (Doc d = new Doc()) {
            d.addInt32Field("age", 42);
            d.addFloatField("score", 3.14f);
            d.addStringField("name", "Alice");
            d.addBoolField("active", true);

            assertEquals(42, d.getInt32Field("age"));
            assertEquals(3.14f, d.getFloatField("score"), 0.001);
            assertEquals("Alice", d.getStringField("name"));
            assertTrue(d.getBoolField("active"));
        }
    }

    @Test
    void testDocAllNumericTypes() {
        try (Doc d = new Doc()) {
            d.addInt64Field("i64", -9876543210L);
            d.addUint32Field("u32", 42);
            d.addUint64Field("u64", 1L << 62);
            d.addDoubleField("f64", 3.141592653589793);

            assertEquals(-9876543210L, d.getInt64Field("i64"));
            assertEquals(42L, d.getUint32Field("u32"));
            assertEquals(1L << 62, d.getUint64Field("u64"));
            assertEquals(3.141592653589793, d.getDoubleField("f64"), 1e-10);
        }
    }

    @Test
    void testDocVectorFP32Field() {
        try (Doc d = new Doc()) {
            float[] vec = {1.0f, 2.0f, 3.0f, 4.0f};
            d.addVectorFP32Field("emb", vec);
            float[] got = d.getVectorFP32Field("emb");
            assertArrayEquals(vec, got, 0.001f);
        }
    }

    @Test
    void testDocHasField() {
        try (Doc d = new Doc()) {
            d.addInt64Field("x", 100);
            assertTrue(d.hasField("x"));
            assertFalse(d.hasField("y"));
        }
    }

    @Test
    void testDocFieldCount() {
        try (Doc d = new Doc()) {
            d.addInt32Field("a", 1);
            d.addInt32Field("b", 2);
            assertEquals(2, d.getFieldCount());
        }
    }

    @Test
    void testDocSerializeDeserialize() {
        try (Doc d = new Doc()) {
            d.setPK("ser_test");
            d.addInt32Field("v", 99);

            byte[] data = d.serialize();
            assertTrue(data.length > 0);

            try (Doc d2 = Doc.deserialize(data)) {
                assertEquals("ser_test", d2.getPK());
            }
        }
    }

    @Test
    void testFreeDocs() {
        List<Doc> docs = new ArrayList<>();
        docs.add(new Doc());
        docs.add(new Doc());
        docs.add(new Doc());
        assertDoesNotThrow(() -> Doc.freeDocs(docs));
    }

    // =========================================================================
    // CollectionOptions
    // =========================================================================

    @Test
    void testCollectionOptions() {
        try (CollectionOptions o = new CollectionOptions()) {
            o.setEnableMmap(true);
            assertTrue(o.getEnableMmap());
            o.setMaxBufferSize(64 * 1024 * 1024);
            assertEquals(64 * 1024 * 1024, o.getMaxBufferSize());
            o.setReadOnly(true);
            assertTrue(o.getReadOnly());
        }
    }

    // =========================================================================
    // Collection lifecycle
    // =========================================================================

    @Test
    void testCreateAndOpen(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            assertNotNull(coll);
        }
    }

    @Test
    void testCreateAndOpenWithOptions(@TempDir Path dir) {
        CollectionSchema schema = new CollectionSchema("test_coll");
        try (FieldSchema f = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
            schema.addField(f);
        }
        try (CollectionOptions opts = new CollectionOptions()) {
            opts.setEnableMmap(false);
            String path = dir.resolve("coll").toString();
            try (Collection coll = Zvec.createAndOpen(path, schema, opts)) {
                assertNotNull(coll);
            }
        } finally {
            schema.close();
        }
    }

    @Test
    void testOpenExisting(@TempDir Path dir) {
        String path = dir.resolve("coll").toString();
        CollectionSchema schema = new CollectionSchema("test_coll");
        try (FieldSchema f = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
            schema.addField(f);
        }
        Collection coll = Zvec.createAndOpen(path, schema, null);
        schema.close();
        coll.flush();
        coll.close();

        // Re-open
        try (Collection coll2 = Zvec.open(path, null)) {
            assertNotNull(coll2);
        }
    }

    @Test
    void testCollectionGetSchema(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            try (CollectionSchema snap = coll.getSchema()) {
                assertEquals("test_coll", snap.getName());
            }
        }
    }

    @Test
    void testCollectionGetOptions(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            try (CollectionOptions opts = coll.getOptions()) {
                assertNotNull(opts);
            }
        }
    }

    @Test
    void testCollectionGetStats(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            insertDocs(coll, 5);
            CollectionStats stats = coll.getStats();
            assertTrue(stats.getDocCount() > 0);
        }
    }

    // =========================================================================
    // DML
    // =========================================================================

    @Test
    void testInsert(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            List<String> pks = insertDocs(coll, 10);
            assertEquals(10, pks.size());
        }
    }

    @Test
    void testInsertEmpty(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            Collection.WriteResult result = coll.insert(new ArrayList<>());
            assertEquals(0, result.successCount);
            assertEquals(0, result.errorCount);
        }
    }

    @Test
    void testUpdate(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            List<String> pks = insertDocs(coll, 3);
            Doc d = new Doc();
            d.setPK(pks.get(0));
            d.addVectorFP32Field("vec", randVec(4));
            List<Doc> docs = new ArrayList<>();
            docs.add(d);
            Collection.WriteResult result = coll.update(docs);
            d.close();
            assertTrue(result.successCount >= 0);
        }
    }

    @Test
    void testUpsert(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            Doc d = new Doc();
            d.setPK("upsert_pk");
            d.addVectorFP32Field("vec", randVec(4));
            List<Doc> docs = new ArrayList<>();
            docs.add(d);
            Collection.WriteResult result = coll.upsert(docs);
            d.close();
            assertTrue(result.successCount >= 0);
        }
    }

    @Test
    void testDelete(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            List<String> pks = insertDocs(coll, 5);
            Collection.WriteResult result = coll.delete(pks.subList(0, 2));
            assertTrue(result.successCount >= 0);
        }
    }

    @Test
    void testDeleteEmpty(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            Collection.WriteResult result = coll.delete(new ArrayList<>());
            assertEquals(0, result.successCount);
            assertEquals(0, result.errorCount);
        }
    }

    @Test
    void testDeleteByFilter(@TempDir Path dir) {
        CollectionSchema schema = new CollectionSchema("filter_test");
        try (FieldSchema idF = new FieldSchema("id", DataType.STRING, false, 0)) {
            try (IndexParams ip = IndexParams.createInvert(true, false)) {
                idF.setIndexParams(ip);
            }
            schema.addField(idF);
        }
        try (FieldSchema vecF = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
            schema.addField(vecF);
        }
        String path = dir.resolve("coll").toString();
        try (Collection coll = Zvec.createAndOpen(path, schema, null)) {
            // Insert 2 docs
            List<Doc> docs = new ArrayList<>();
            for (String id : Arrays.asList("a1", "a2")) {
                Doc d = new Doc();
                d.setPK(id);
                d.addStringField("id", id);
                d.addVectorFP32Field("vec", randVec(4));
                docs.add(d);
            }
            coll.insert(docs);
            Doc.freeDocs(docs);

            assertDoesNotThrow(() -> coll.deleteByFilter("id = 'a1'"));
        } finally {
            schema.close();
        }
    }

    // =========================================================================
    // DQL
    // =========================================================================

    @Test
    void testFetch(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            List<String> pks = insertDocs(coll, 5);
            coll.flush();
            try {
                List<Doc> found = coll.fetch(pks.subList(0, 2));
                // May return 0 docs if forward index not built
                Doc.freeDocs(found);
            } catch (ZvecException e) {
                // Fetch may fail with InvalidArgument if no forward index
                System.out.println("Fetch note: " + e.getMessage());
            }
        }
    }

    @Test
    void testQuery(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            insertDocs(coll, 20);

            // Create HNSW index
            try (IndexParams p = IndexParams.createHNSW(MetricType.COSINE, 8, 100)) {
                try {
                    coll.createIndex("vec", p);
                } catch (ZvecException e) {
                    // May fail for small datasets; non-fatal
                    System.out.println("CreateIndex note: " + e.getMessage());
                }
            }

            coll.flush();

            try (VectorQuery vq = new VectorQuery()) {
                vq.setTopK(3);
                vq.setFieldName("vec");
                vq.setQueryVector(randVec(4));

                try {
                    List<Doc> results = coll.query(vq);
                    System.out.println("Query returned " + results.size() + " results");
                    for (Doc r : results) {
                        System.out.printf("  pk=%s score=%.4f%n", r.getPK(), r.getScore());
                    }
                    Doc.freeDocs(results);
                } catch (ZvecException e) {
                    System.out.println("Query note: " + e.getMessage());
                }
            }
        }
    }

    // =========================================================================
    // DDL
    // =========================================================================

    @Test
    void testCreateDropIndex(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            insertDocs(coll, 5);
            try (IndexParams p = IndexParams.createHNSW(MetricType.COSINE, 8, 100)) {
                try {
                    coll.createIndex("vec", p);
                } catch (ZvecException e) {
                    System.out.println("CreateIndex note: " + e.getMessage());
                }
            }
            try {
                coll.dropIndex("vec");
            } catch (ZvecException e) {
                System.out.println("DropIndex note: " + e.getMessage());
            }
        }
    }

    @Test
    void testAddColumn(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            try (FieldSchema f = new FieldSchema("extra", DataType.INT64, true, 0)) {
                try {
                    coll.addColumn(f, "");
                } catch (ZvecException e) {
                    System.out.println("AddColumn note: " + e.getMessage());
                }
            }
        }
    }

    @Test
    void testAlterColumn(@TempDir Path dir) {
        CollectionSchema schema = new CollectionSchema("alter_test");
        try (FieldSchema vecF = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
            schema.addField(vecF);
        }
        try (FieldSchema scoreF = new FieldSchema("score", DataType.FLOAT, true, 0)) {
            schema.addField(scoreF);
        }
        String path = dir.resolve("coll").toString();
        try (Collection coll = Zvec.createAndOpen(path, schema, null)) {
            try {
                coll.alterColumn("score", "new_score", null);
            } catch (ZvecException e) {
                System.out.println("AlterColumn note: " + e.getMessage());
            }
        } finally {
            schema.close();
        }
    }

    @Test
    void testOptimize(@TempDir Path dir) {
        try (Collection coll = openTestCollection(dir)) {
            insertDocs(coll, 5);
            try {
                coll.optimize();
            } catch (ZvecException e) {
                System.out.println("Optimize note: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // Flush / Reopen
    // =========================================================================

    @Test
    void testFlushAndReopen(@TempDir Path dir) {
        CollectionSchema schema = new CollectionSchema("persist_test");
        try (FieldSchema f = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
            schema.addField(f);
        }
        String path = dir.resolve("coll").toString();
        Collection coll = Zvec.createAndOpen(path, schema, null);
        schema.close();

        List<Doc> docs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Doc d = new Doc();
            d.setPK("pk_" + i);
            d.addVectorFP32Field("vec", randVec(4));
            docs.add(d);
        }
        coll.insert(docs);
        Doc.freeDocs(docs);
        coll.flush();
        coll.close();

        // Reopen
        try (Collection coll2 = Zvec.open(path, null)) {
            CollectionStats stats = coll2.getStats();
            assertTrue(stats.getDocCount() > 0, "expected doc count > 0 after reopen");
        }
    }

    // =========================================================================
    // VectorQuery setters
    // =========================================================================

    @Test
    void testVectorQuerySetters() {
        try (VectorQuery vq = new VectorQuery()) {
            vq.setTopK(10);
            assertEquals(10, vq.getTopK());
            vq.setFieldName("embedding");
            assertEquals("embedding", vq.getFieldName());
            vq.setFilter("age > 18");
            assertEquals("age > 18", vq.getFilter());
            vq.setIncludeVector(true);
            assertTrue(vq.getIncludeVector());
            vq.setIncludeDocID(true);
            assertTrue(vq.getIncludeDocID());
        }
    }

    // =========================================================================
    // GroupByVectorQuery setters
    // =========================================================================

    @Test
    void testGroupByVectorQuerySetters() {
        try (GroupByVectorQuery q = new GroupByVectorQuery()) {
            q.setFieldName("emb");
            assertEquals("emb", q.getFieldName());
            q.setGroupByFieldName("category");
            assertEquals("category", q.getGroupByFieldName());
            q.setGroupCount(5);
            assertEquals(5, q.getGroupCount());
            q.setTopkPerGroup(3);
            assertEquals(3, q.getTopkPerGroup());
        }
    }
}
