package org.zvec.binding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Strong-assertion coverage for the wider zvec C API surface: enum codes,
 * index and query parameters (DiskANN, IVF-RaBitQ, Vamana, FTS), full-text and
 * multi-query wrappers, the collection document iterator, I/O backend
 * introspection and the bundled jieba dictionary.
 *
 * <p>Complements {@link ZvecTest} and the DML/DQL suites. Everything here is
 * either a parameter round-trip against a native handle or an end-to-end
 * collection run, so results are asserted instead of merely checked for
 * "does not throw".
 */
class ApiCoverageTest extends TestSupport {

    // =========================================================================
    // Enum codes
    // =========================================================================

    @Test
    void testIndexTypeCodes() {
        assertEquals(0, IndexType.UNDEFINED.getCode());
        assertEquals(1, IndexType.HNSW.getCode());
        assertEquals(2, IndexType.IVF.getCode());
        assertEquals(3, IndexType.FLAT.getCode());
        assertEquals(4, IndexType.HNSW_RABITQ.getCode());
        assertEquals(5, IndexType.DISKANN.getCode());
        assertEquals(6, IndexType.VAMANA.getCode());
        assertEquals(7, IndexType.IVF_RABITQ.getCode());
        assertEquals(10, IndexType.INVERT.getCode());
        assertEquals(11, IndexType.FTS.getCode());

        assertEquals(IndexType.DISKANN, IndexType.fromCode(5));
        assertEquals(IndexType.FTS, IndexType.fromCode(11));
        assertEquals(IndexType.UNDEFINED, IndexType.fromCode(999));
    }

    @Test
    void testIoBackendTypeCodes() {
        assertEquals(0, IoBackendType.PREAD.getCode());
        assertEquals(1, IoBackendType.LIBAIO.getCode());
        assertEquals(2, IoBackendType.IO_URING.getCode());

        assertEquals(IoBackendType.PREAD, IoBackendType.fromCode(0));
        assertEquals(IoBackendType.LIBAIO, IoBackendType.fromCode(1));
        assertEquals(IoBackendType.IO_URING, IoBackendType.fromCode(2));
        assertEquals(IoBackendType.PREAD, IoBackendType.fromCode(42),
                "unknown codes fall back to pread");
    }

    @Test
    void testRabitqQuantizeTypeCode() {
        assertEquals(4, QuantizeType.RABITQ.getCode());
        assertEquals(QuantizeType.RABITQ, QuantizeType.fromCode(4));
    }


    // =========================================================================
    // Index parameters
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
        // pqChunkNum == 0 lets the native layer pick the chunk count itself.
        try (IndexParams auto = IndexParams.createDiskAnn(MetricType.L2, 64, 128, 0)) {
            assertEquals(0, auto.getDiskAnnPqChunkNum());
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
    void testFtsIndexParamsRoundtrip() {
        try (IndexParams p = IndexParams.createFTS(
                "standard", new String[]{"lowercase", "ascii_folding"}, null)) {
            assertEquals(IndexType.FTS, p.getType());
            String[] params = p.getFTSParams();
            assertEquals("standard", params[0]);
            assertEquals("lowercase", params[1]);
            assertEquals("ascii_folding", params[2]);
            assertEquals("", params[3]);
        }
    }

    // =========================================================================
    // Query parameters
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
    void testTransferredIvfRabitqParamsRejectReuse() {
        IvfRabitqQueryParams params = new IvfRabitqQueryParams();
        try (VectorQuery vq = new VectorQuery()) {
            vq.setIvfRabitqParams(params);
            // Ownership moved to the query, so the wrapper must fail loudly
            // instead of handing a NULL handle to the C API.
            ZvecException ex = assertThrows(ZvecException.class, () -> params.setNprobe(8));
            assertEquals(ErrorCode.FAILED_PRECONDITION, ex.getErrorCode());
            assertThrows(ZvecException.class, params::getRadius);
        }
        params.close(); // documented no-op once ownership has transferred
    }

    @Test
    void testTransferredDiskAnnParamsRejectReuse() {
        DiskAnnQueryParams params = new DiskAnnQueryParams();
        try (VectorQuery vq = new VectorQuery()) {
            vq.setDiskAnnParams(params);
            ZvecException ex = assertThrows(ZvecException.class, () -> params.setListSize(64));
            assertEquals(ErrorCode.FAILED_PRECONDITION, ex.getErrorCode());
            assertThrows(ZvecException.class, params::getRadius);
        }
        params.close();
    }

    @Test
    void testHnswQueryParamsRoundtrip() {
        try (HnswQueryParams p = new HnswQueryParams(64, 1.5f, false, true)) {
            assertEquals(64, p.getEf());
            assertEquals(1.5f, p.getRadius(), 1e-6);
            assertFalse(p.getIsLinear());
            assertTrue(p.getIsUsingRefiner());

            p.setEf(128);
            assertEquals(128, p.getEf());
            p.setRadius(2.5f);
            assertEquals(2.5f, p.getRadius(), 1e-6);
            p.setIsLinear(true);
            assertTrue(p.getIsLinear());
        }
    }

    @Test
    void testIvfQueryParamsRoundtrip() {
        try (IvfQueryParams p = new IvfQueryParams(32, true, 5.0f)) {
            assertEquals(32, p.getNprobe());
            assertTrue(p.getIsUsingRefiner());
            assertEquals(5.0f, p.getScaleFactor(), 1e-6);

            p.setNprobe(64);
            assertEquals(64, p.getNprobe());
            p.setRadius(1.25f);
            assertEquals(1.25f, p.getRadius(), 1e-6);
            p.setIsLinear(true);
            assertTrue(p.getIsLinear());
        }
    }

    @Test
    void testFlatQueryParamsRoundtrip() {
        try (FlatQueryParams p = new FlatQueryParams(true, 4.0f)) {
            assertTrue(p.getIsUsingRefiner());
            assertEquals(4.0f, p.getScaleFactor(), 1e-6);

            p.setRadius(0.5f);
            assertEquals(0.5f, p.getRadius(), 1e-6);
            p.setIsLinear(true);
            assertTrue(p.getIsLinear());
            p.setIsUsingRefiner(false);
            assertFalse(p.getIsUsingRefiner());
        }
    }

    @Test
    void testVamanaQueryParamsRoundtrip() {
        try (VamanaQueryParams p = new VamanaQueryParams(300, 2.0f, false, true)) {
            assertEquals(300, p.getEfSearch());
            assertEquals(2.0f, p.getRadius(), 1e-6);
            assertFalse(p.getIsLinear());
            assertTrue(p.getIsUsingRefiner());

            p.setEfSearch(150);
            assertEquals(150, p.getEfSearch());
            p.setIsLinear(true);
            assertTrue(p.getIsLinear());
        }
    }

    @Test
    void testQueryParamDefaultsMatchTheCApi() {
        // The no-argument constructors hardcode the defaults documented in
        // c_api.h; reading them back proves they still match the library.
        try (HnswQueryParams hnsw = new HnswQueryParams();
             IvfQueryParams ivf = new IvfQueryParams();
             FlatQueryParams flat = new FlatQueryParams();
             VamanaQueryParams vamana = new VamanaQueryParams()) {
            assertEquals(40, hnsw.getEf());
            assertEquals(0.0f, hnsw.getRadius(), 1e-6);
            assertEquals(10, ivf.getNprobe());
            assertEquals(10.0f, ivf.getScaleFactor(), 1e-6);
            assertEquals(10.0f, flat.getScaleFactor(), 1e-6);
            assertFalse(flat.getIsUsingRefiner());
            assertEquals(200, vamana.getEfSearch());
        }
    }

    @Test
    void testTypedQueryParamsTransferOwnershipToVectorQuery(@TempDir Path dir) {
        HnswQueryParams hnsw = new HnswQueryParams();
        IvfQueryParams ivf = new IvfQueryParams();
        FlatQueryParams flat = new FlatQueryParams();
        VamanaQueryParams vamana = new VamanaQueryParams();
        try (Collection coll = openIndexed(dir, "typed_params");
             VectorQuery vq = new VectorQuery()) {
            insertGraded(coll, 8);
            coll.flush();

            vq.setFieldName("vec");
            vq.setTopK(2);
            vq.setQueryVector(vecOf(3));
            vq.setHNSWParams(hnsw);
            assertNull(hnsw.getHandle(), "ownership must transfer to the query");

            List<Doc> res = coll.query(vq);
            try {
                assertEquals(2, res.size());
                assertEquals("pk_3", res.get(0).getPK());
            } finally {
                Doc.freeDocs(res);
            }

            // Each setter replaces the previous params, so the query owns them
            // all in turn and none of the wrappers may free anything.
            vq.setIVFParams(ivf);
            vq.setFlatParams(flat);
            vq.setVamanaParams(vamana);
            assertNull(ivf.getHandle());
            assertNull(flat.getHandle());
            assertNull(vamana.getHandle());
        }
        hnsw.close();
        ivf.close();
        flat.close();
        vamana.close();
    }

    @Test
    void testSubQueryTypedParamsTransferOwnership() {
        DiskAnnQueryParams diskAnn = new DiskAnnQueryParams(100);
        IvfRabitqQueryParams rabitq = new IvfRabitqQueryParams();
        try (SubQuery sq = new SubQuery()) {
            sq.setFieldName("vec");
            // Passing the handle without transferring it used to leave the
            // sub-query pointing at memory the wrapper would then free.
            sq.setDiskAnnParams(diskAnn);
            assertNull(diskAnn.getHandle(), "ownership must transfer to the sub-query");
            sq.setIvfRabitqParams(rabitq);
            assertNull(rabitq.getHandle(), "ownership must transfer to the sub-query");
        }
        diskAnn.close();
        rabitq.close();
    }

    @Test
    void testDiskAnnEndToEndQuery(@TempDir Path dir) {
        assumeTrue(isDiskAnnSupported(),
                "zvec enables DiskANN only on Linux x86_64/aarch64 and macOS arm64");
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
    // Full-text search and multi-query
    // =========================================================================

    @Test
    void testFtsQueryParamsRoundtrip() {
        try (FtsQueryParams p = new FtsQueryParams("AND")) {
            assertEquals("AND", p.getDefaultOperator());
            p.setDefaultOperator("OR");
            assertEquals("OR", p.getDefaultOperator());
        }
    }

    @Test
    void testFtsPayloadRoundtrip() {
        try (FtsPayload fts = new FtsPayload()) {
            fts.setQueryString("zvec AND java");
            assertEquals("zvec AND java", fts.getQueryString());

            fts.setMatchString("full text search");
            assertEquals("full text search", fts.getMatchString());
        }
    }

    @Test
    void testFtsPayloadKeepsNonBmpCharacters() {
        // Marshalling through the generated String overload would use modified
        // UTF-8, where an astral character becomes a surrogate pair the C++
        // side cannot read back. The explicit UTF-8 path must round-trip it.
        String match = "向量检索 \uD83D\uDE80 zvec";
        String query = "title:\"中文标题\" AND tag:\uD83C\uDFF7";
        try (FtsPayload fts = new FtsPayload()) {
            fts.setMatchString(match);
            assertEquals(match, fts.getMatchString());

            fts.setQueryString(query);
            assertEquals(query, fts.getQueryString());
        }
    }

    @Test
    void testVectorQueryFtsAttachment() {
        try (VectorQuery vq = new VectorQuery()) {
            try (FtsQueryParams p = new FtsQueryParams("OR")) {
                vq.setFtsParams(p);
            }
            try (FtsPayload fts = new FtsPayload()) {
                fts.setQueryString("hello");
                vq.setFts(fts);
            }
            FtsPayload attached = vq.getFts();
            if (attached != null) {
                attached.close();
            }
        }
    }

    @Test
    void testMultiQueryLifecycle() {
        try (MultiQuery mq = new MultiQuery()) {
            mq.setTopk(10);
            assertEquals(10, mq.getTopk());

            mq.setFilter("num > 0");
            assertEquals("num > 0", mq.getFilter());

            mq.setIncludeVector(true);
            assertTrue(mq.getIncludeVector());

            mq.setOutputFields(new String[]{"pk", "vec"});

            mq.setRerankRrf(60);
            mq.setRerankWeighted(new double[]{0.7, 0.3});

            try (SubQuery sq = new SubQuery()) {
                sq.setFieldName("vec");
                sq.setNumCandidates(100);
                assertEquals(100, sq.getNumCandidates());
                sq.setQueryVector(new float[]{0.1f, 0.2f, 0.3f, 0.4f});
                mq.addSubQuery(sq);
            }

            assertEquals(1, mq.getSubQueryCount());
        }
    }

    @Test
    void testSubQuerySparseVector() {
        try (SubQuery sq = new SubQuery()) {
            sq.setFieldName("sparse_vec");
            sq.setSparseVector(new int[]{0, 2, 5}, new float[]{1.0f, 2.0f, 3.0f});
            sq.setSparseIndices(new int[]{1, 3});
            sq.setSparseValues(new float[]{0.5f, 1.5f});
        }
    }

    @Test
    void testSubQueryFtsAttachment() {
        try (SubQuery sq = new SubQuery()) {
            sq.setFieldName("content");
            try (FtsQueryParams p = new FtsQueryParams("AND")) {
                sq.setFtsParams(p);
            }
            try (FtsPayload fts = new FtsPayload()) {
                fts.setQueryString("zvec");
                sq.setFts(fts);
            }
        }
    }

    // =========================================================================
    // Document iterator
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
    // I/O backend introspection
    // =========================================================================

    @Test
    void testIoBackendIntrospection() {
        IoBackendType type = Zvec.getIoBackendType();
        assertNotNull(type);
        String name = type.getName();
        assertTrue(name.equals("pread") || name.equals("libaio") || name.equals("io_uring"),
                "unexpected backend name: " + name);
        assertEquals(name, Zvec.getIoBackendTypeName(type),
                "the enum accessor and the static helper must agree");
        String description = Zvec.getIoBackendDescription();
        assertNotNull(description);
        assertFalse(description.isEmpty(), "backend description must not be empty");
    }

    // =========================================================================
    // Bundled jieba dict
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
