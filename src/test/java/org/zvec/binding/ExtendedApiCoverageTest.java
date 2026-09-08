package org.zvec.binding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strong-assertion coverage for the C APIs added in zvec v0.7.0: DiskANN /
 * IVF-RaBitQ index and query parameters, the collection document iterator,
 * I/O backend introspection and the bundled jieba FTS dictionary.
 */
class ExtendedApiCoverageTest extends TestSupport {

    // =========================================================================
    // Index parameters (new in v0.7.0)
    // =========================================================================

    @Test
    void testDiskAnnIndexParamsRoundtrip() {
        try (IndexParams p = IndexParams.createDiskAnn(MetricType.L2, 64, 128, 16)) {
            assertEquals(IndexType.DISKANN, p.getType());
            assertEquals(MetricType.L2, p.getMetricType());
            assertEquals(64, p.getDiskAnnMaxDegree());
            assertEquals(128, p.getDiskAnnListSize());
            assertEquals(16, p.getDiskAnnPqChunkNum());

            p.setDiskAnnParams(32, 100, 0);
            assertEquals(32, p.getDiskAnnMaxDegree());
            assertEquals(100, p.getDiskAnnListSize());
            assertEquals(0, p.getDiskAnnPqChunkNum());
        }
    }

    @Test
    void testDiskAnnQuantizedFactory() {
        try (IndexParams p = IndexParams.createDiskAnnQuantized(
                MetricType.COSINE, 48, 96, 8, QuantizeType.RABITQ)) {
            assertEquals(IndexType.DISKANN, p.getType());
            assertEquals(MetricType.COSINE, p.getMetricType());
            assertEquals(QuantizeType.RABITQ, p.getQuantizeType());
            assertEquals(48, p.getDiskAnnMaxDegree());
        }
    }

    @Test
    void testIvfRabitqIndexParamsRoundtrip() {
        try (IndexParams p = IndexParams.createIvfRabitq(MetricType.L2, 128, 64, 0)) {
            assertEquals(IndexType.IVF_RABITQ, p.getType());
            int[] params = p.getIvfRabitqParams();
            assertEquals(128, params[0], "nlist");
            assertEquals(64, params[1], "totalBits");
            assertEquals(0, params[2], "sampleCount");

            p.setIvfRabitqParams(256, 32, 1000);
            params = p.getIvfRabitqParams();
            assertEquals(256, params[0]);
            assertEquals(32, params[1]);
            assertEquals(1000, params[2]);
        }
    }

    @Test
    void testVamanaTwoPassBuild() {
        try (IndexParams p = IndexParams.createVamana(
                MetricType.L2, 32, 100, 1.2f, false, false)) {
            assertEquals(IndexType.VAMANA, p.getType());
            assertFalse(p.getVamanaTwoPassBuild(), "two-pass build defaults to off");
            p.setVamanaTwoPassBuild(true);
            assertTrue(p.getVamanaTwoPassBuild());
            p.setVamanaTwoPassBuild(false);
            assertFalse(p.getVamanaTwoPassBuild());
        }
    }

    @Test
    void testRabitqQuantizeTypeCode() {
        assertEquals(4, QuantizeType.RABITQ.getCode());
        assertEquals(QuantizeType.RABITQ, QuantizeType.fromCode(4));
    }

    // =========================================================================
    // Query parameters (new in v0.7.0)
    // =========================================================================

    @Test
    void testDiskAnnQueryParamsRoundtrip() {
        try (DiskAnnQueryParams p = new DiskAnnQueryParams(256)) {
            assertEquals(256, p.getListSize());
            p.setListSize(512);
            assertEquals(512, p.getListSize());
            p.setRadius(1.5f);
            assertEquals(1.5f, p.getRadius(), 1e-6);
            p.setIsLinear(true);
            assertTrue(p.getIsLinear());
            p.setIsUsingRefiner(true);
            assertTrue(p.getIsUsingRefiner());
        }
    }

    @Test
    void testIvfRabitqQueryParamsRoundtrip() {
        try (IvfRabitqQueryParams p = new IvfRabitqQueryParams(20, 2.0f, false, true)) {
            assertEquals(20, p.getNprobe());
            assertEquals(2.0f, p.getRadius(), 1e-6);
            assertFalse(p.getIsLinear());
            assertTrue(p.getIsUsingRefiner());

            p.setNprobe(40);
            assertEquals(40, p.getNprobe());
            p.setScaleFactor(3.0f);
            assertEquals(3.0f, p.getScaleFactor(), 1e-6);
            p.setIsLinear(true);
            assertTrue(p.getIsLinear());
        }
    }

    @Test
    void testDiskAnnEndToEndQuery(@TempDir Path dir) {
        // Build a DiskANN-indexed collection and query it with DiskANN params.
        CollectionSchema schema = new CollectionSchema("diskann_e2e");
        try (FieldSchema vecF = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4);
             IndexParams diskann = IndexParams.createDiskAnn(MetricType.L2, 16, 100, 0)) {
            vecF.setIndexParams(diskann);
            schema.addField(vecF);
        }
        // insertGraded() also writes the cat/num scalar fields.
        try (FieldSchema catF = new FieldSchema("cat", DataType.STRING, true, 0)) {
            schema.addField(catF);
        }
        try (FieldSchema numF = new FieldSchema("num", DataType.INT32, true, 0)) {
            schema.addField(numF);
        }
        try (Collection coll = Zvec.createAndOpen(dir.resolve("coll").toString(), schema, null)) {
            schema.close();
            insertGraded(coll, 16);
            coll.flush();

            try (VectorQuery vq = new VectorQuery();
                 DiskAnnQueryParams params = new DiskAnnQueryParams(100)) {
                vq.setFieldName("vec");
                vq.setTopK(3);
                vq.setQueryVector(vecOf(5));
                vq.setDiskAnnParams(params);
                assertNull(params.getHandle(), "ownership must transfer to the query");

                List<Doc> res = coll.query(vq);
                try {
                    assertEquals(3, res.size());
                    assertEquals("pk_5", res.get(0).getPK(), "exact match should rank first");
                } finally {
                    Doc.freeDocs(res);
                }
            }
        }
    }

    // =========================================================================
    // Document iterator (new in v0.7.0)
    // =========================================================================

    @Test
    void testIteratorFullScan(@TempDir Path dir) {
        try (Collection coll = openIndexed(dir, "iter_full")) {
            List<String> pks = insertGraded(coll, 10);
            coll.flush();

            Set<String> seen = new HashSet<>();
            int count = 0;
            try (DocIterator it = coll.createIterator(null)) {
                Doc doc;
                while ((doc = it.next()) != null) {
                    try {
                        seen.add(doc.getPK());
                        count++;
                    } finally {
                        doc.close();
                    }
                }
                // A second pass over the same (exhausted) iterator yields EOF.
                assertNull(it.next(), "exhausted iterator must report EOF");
            }
            assertEquals(10, count, "iterator must visit every document");
            assertEquals(new HashSet<>(pks), seen, "iterator must return exactly the inserted PKs");
        }
    }

    @Test
    void testIteratorOutputFields(@TempDir Path dir) {
        try (Collection coll = openIndexed(dir, "iter_fields")) {
            insertGraded(coll, 4);
            coll.flush();

            try (IteratorOptions opts = new IteratorOptions()) {
                opts.setOutputFields(new String[]{"cat"});
                opts.setIncludeVector(false);
                try (DocIterator it = coll.createIterator(opts)) {
                    Doc doc = it.next();
                    assertNotNull(doc, "iterator should return at least one document");
                    try {
                        int i = Integer.parseInt(doc.getPK().substring(3));
                        assertEquals("c" + (i % 2), doc.getStringField("cat"),
                                "cat field must match the inserted value for " + doc.getPK());
                        assertTrue(doc.hasField("cat"));
                        assertFalse(doc.hasField("num"),
                                "fields not requested via output_fields must be omitted");
                    } finally {
                        doc.close();
                    }
                }
            }
        }
    }

    // =========================================================================
    // I/O backend introspection (new in v0.7.0)
    // =========================================================================

    @Test
    void testIoBackendIntrospection() {
        IoBackendType type = Zvec.getIoBackendType();
        assertNotNull(type);
        String name = type.getName();
        assertTrue(name.equals("pread") || name.equals("libaio") || name.equals("io_uring"),
                "unexpected backend name: " + name);
        String description = Zvec.getIoBackendDescription();
        assertNotNull(description);
        assertFalse(description.isEmpty(), "backend description must not be empty");
    }

    // =========================================================================
    // Bundled jieba dict (v0.7.0 packaging requirement)
    // =========================================================================

    @Test
    void testBundledJiebaDict() {
        // TestSupport.initLibrary() -> Zvec.initialize(null) auto-registers the
        // bundled dict as the process-wide default.
        assertTrue(Zvec.isJiebaDictAvailable(), "a jieba dict source must be configured");

        String dir = Zvec.getDefaultJiebaDictDir();
        assertNotNull(dir);
        assertFalse(dir.isEmpty(), "the bundled dict should be auto-registered at initialize");

        for (String f : new String[]{"jieba.dict.utf8", "hmm_model.utf8"}) {
            File file = new File(dir, f);
            assertTrue(file.isFile(), "missing dict file " + file);
            assertTrue(file.length() > 0, "empty dict file " + file);
        }

        // useBundledJiebaDict() is idempotent and reports the same directory.
        assertEquals(dir, Zvec.useBundledJiebaDict());
    }

    @Test
    void testConfigDataJiebaDictDir() {
        try (ConfigData cfg = new ConfigData()) {
            cfg.setJiebaDictDir("/tmp/some-dict-dir");
            assertEquals("/tmp/some-dict-dir", cfg.getJiebaDictDir());
            cfg.setJiebaDictDir("");
            assertEquals("", cfg.getJiebaDictDir());
        }
    }
}
