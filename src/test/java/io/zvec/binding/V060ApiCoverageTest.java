package io.zvec.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for v0.6.0 API additions: DiskANN, I/O backend introspection,
 * new index types, and FTS / multi-query wrappers.
 *
 * <p>These tests exercise object lifecycle and accessor round-trips only;
 * actual index building / querying requires the native zvec_c_api library
 * and is covered by integration tests once the library is built.</p>
 */
class V060ApiCoverageTest {

    // ------------------------------------------------------------------ IndexType

    @Test
    void testIndexTypeCodes() {
        assertEquals(0, IndexType.UNDEFINED.getCode());
        assertEquals(1, IndexType.HNSW.getCode());
        assertEquals(2, IndexType.IVF.getCode());
        assertEquals(3, IndexType.FLAT.getCode());
        assertEquals(4, IndexType.HNSW_RABITQ.getCode());
        assertEquals(5, IndexType.DISKANN.getCode());
        assertEquals(6, IndexType.VAMANA.getCode());
        assertEquals(10, IndexType.INVERT.getCode());
        assertEquals(11, IndexType.FTS.getCode());

        assertEquals(IndexType.DISKANN, IndexType.fromCode(5));
        assertEquals(IndexType.FTS, IndexType.fromCode(11));
        assertEquals(IndexType.UNDEFINED, IndexType.fromCode(999));
    }

    // ------------------------------------------------------------------ IoBackendType

    @Test
    void testIoBackendTypeCodes() {
        assertEquals(0, IoBackendType.PREAD.getCode());
        assertEquals(1, IoBackendType.LIBAIO.getCode());

        assertEquals(IoBackendType.PREAD, IoBackendType.fromCode(0));
        assertEquals(IoBackendType.LIBAIO, IoBackendType.fromCode(1));
        assertEquals(IoBackendType.PREAD, IoBackendType.fromCode(42));
    }

    @Test
    void testIoBackendIntrospectionDoesNotThrow() {
        // Pure introspection, no side effects.
        IoBackendType type = Zvec.getIoBackendType();
        assertNotNull(type);
        assertNotNull(Zvec.getIoBackendTypeName(type));
        assertNotNull(Zvec.getIoBackendDescription());
    }

    // ------------------------------------------------------------------ FTS IndexParams

    @Test
    void testFtsIndexParamsRoundtrip() {
        try (IndexParams p = IndexParams.createFTS("standard", new String[]{"lowercase", "ascii_folding"}, null)) {
            assertEquals(IndexType.FTS, p.getType());
            String[] params = p.getFTSParams();
            assertEquals("standard", params[0]);
            assertEquals("lowercase", params[1]);
            assertEquals("ascii_folding", params[2]);
            assertEquals("", params[3]);
        }
    }

    // ------------------------------------------------------------------ DiskANN IndexParams

    @Test
    void testDiskAnnIndexParamsRoundtrip() {
        try (IndexParams p = IndexParams.createDiskANN(MetricType.L2, 64, 128, 0)) {
            assertEquals(IndexType.DISKANN, p.getType());
            assertEquals(MetricType.L2, p.getMetricType());
            assertEquals(64, p.getDiskANNMaxDegree());
            assertEquals(128, p.getDiskANNListSize());
            assertEquals(0, p.getDiskANNPqChunkNum());

            p.setDiskANNParams(32, 256, 4);
            assertEquals(32, p.getDiskANNMaxDegree());
            assertEquals(256, p.getDiskANNListSize());
            assertEquals(4, p.getDiskANNPqChunkNum());
        }
    }

    // ------------------------------------------------------------------ DiskANN query params

    @Test
    void testDiskAnnQueryParamsRoundtrip() {
        try (DiskAnnQueryParams p = new DiskAnnQueryParams(100)) {
            assertEquals(100, p.getListSize());
            p.setListSize(200);
            assertEquals(200, p.getListSize());

            p.setRadius(0.5f);
            assertEquals(0.5f, p.getRadius(), 1e-6);

            p.setLinear(true);
            assertTrue(p.isLinear());

            p.setUsingRefiner(true);
            assertTrue(p.isUsingRefiner());
        }
    }

    // ------------------------------------------------------------------ FTS query params

    @Test
    void testFtsQueryParamsRoundtrip() {
        try (FtsQueryParams p = new FtsQueryParams("AND")) {
            assertEquals("AND", p.getDefaultOperator());
            p.setDefaultOperator("OR");
            assertEquals("OR", p.getDefaultOperator());
        }
    }

    // ------------------------------------------------------------------ FTS payload

    @Test
    void testFtsPayloadRoundtrip() {
        try (FtsPayload fts = new FtsPayload()) {
            fts.setQueryString("zvec AND java");
            assertEquals("zvec AND java", fts.getQueryString());

            fts.setMatchString("full text search");
            assertEquals("full text search", fts.getMatchString());
        }
    }

    // ------------------------------------------------------------------ VectorQuery FTS attachment

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
            // getFts returns the attached payload; no further assertions without native lib.
            FtsPayload attached = vq.getFts();
            if (attached != null) {
                attached.close();
            }
        }
    }

    // ------------------------------------------------------------------ MultiQuery + SubQuery

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
}
