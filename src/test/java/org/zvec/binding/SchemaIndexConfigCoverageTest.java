package org.zvec.binding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strong-assertion coverage for FieldSchema / CollectionSchema / IndexParams
 * (including the {@code @ByPtrPtr} out-parameter readers), ConfigData, LogConfig
 * and error-code mapping.
 */
class SchemaIndexConfigCoverageTest extends TestSupport {

    // ---------------------------------------------------------------- FieldSchema

    @Test
    void testFieldSchemaAccessors() {
        try (FieldSchema f = new FieldSchema("emb", DataType.VECTOR_FP32, false, 128)) {
            assertEquals("emb", f.getName());
            assertEquals(DataType.VECTOR_FP32, f.getDataType());
            assertEquals(128, f.getDimension());
            assertTrue(f.isVectorField());
            assertTrue(f.isDenseVector());
            assertFalse(f.isSparseVector());
            assertFalse(f.isArrayType());
            assertFalse(f.isNullable());
        }
    }

    @Test
    void testFieldSchemaSetters() {
        try (FieldSchema f = new FieldSchema("a", DataType.VECTOR_FP32, false, 4)) {
            f.setName("renamed");
            assertEquals("renamed", f.getName());
            f.setNullable(true);
            assertTrue(f.isNullable());
            f.setDimension(16);
            assertEquals(16, f.getDimension());
        }
    }

    @Test
    void testFieldSchemaIndexType() {
        try (FieldSchema f = new FieldSchema("emb", DataType.VECTOR_FP32, false, 8)) {
            assertFalse(f.hasIndex());
            try (IndexParams p = IndexParams.createHNSW(MetricType.L2, 8, 100)) {
                f.setIndexParams(p);
            }
            assertTrue(f.hasIndex());
            assertEquals(IndexType.HNSW, f.getIndexType());
        }
    }

    @Test
    void testFieldSchemaInvertIndexFlag() {
        try (FieldSchema f = new FieldSchema("tag", DataType.STRING, true, 0)) {
            try (IndexParams inv = IndexParams.createInvert(true, false)) {
                f.setIndexParams(inv);
            }
            assertTrue(f.hasInvertIndex());
        }
    }

    // ------------------------------------------------------------ CollectionSchema

    @Test
    void testCollectionSchemaFieldLookup() {
        try (CollectionSchema s = new CollectionSchema("look")) {
            try (FieldSchema vec = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
                s.addField(vec);
            }
            try (FieldSchema cat = new FieldSchema("cat", DataType.STRING, true, 0)) {
                s.addField(cat);
            }
            assertTrue(s.hasField("vec"));
            assertTrue(s.hasField("cat"));

            FieldSchema vec = s.getField("vec");
            assertNotNull(vec);
            assertEquals(DataType.VECTOR_FP32, vec.getDataType());

            assertNotNull(s.getVectorField("vec"));
            assertNotNull(s.getForwardField("cat"));

            List<String> names = s.getAllFieldNames();
            assertEquals(2, names.size());
            assertTrue(names.contains("vec"));
            assertTrue(names.contains("cat"));
        }
    }

    @Test
    void testCollectionSchemaIndexAndDropField() {
        try (CollectionSchema s = new CollectionSchema("idx")) {
            try (FieldSchema vec = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
                s.addField(vec);
            }
            assertFalse(s.hasIndex("vec"));
            try (IndexParams p = IndexParams.createHNSW(MetricType.L2, 8, 100)) {
                s.addIndex("vec", p);
            }
            assertTrue(s.hasIndex("vec"));
            s.dropIndex("vec");
            assertFalse(s.hasIndex("vec"));

            try (FieldSchema tmp = new FieldSchema("tmp", DataType.INT32, true, 0)) {
                s.addField(tmp);
            }
            assertTrue(s.hasField("tmp"));
            s.dropField("tmp");
            assertFalse(s.hasField("tmp"));
        }
    }

    @Test
    void testCollectionSchemaNameAndSegment() {
        try (CollectionSchema s = new CollectionSchema("orig")) {
            assertEquals("orig", s.getName());
            s.setName("renamed");
            assertEquals("renamed", s.getName());
            s.setMaxDocCountPerSegment(123456L);
            assertEquals(123456L, s.getMaxDocCountPerSegment());
        }
    }

    // ---------------------------------------------------------------- IndexParams

    @Test
    void testIVFParamsRoundtrip() {
        try (IndexParams p = IndexParams.createIVF(MetricType.COSINE, 256, 100, false)) {
            int[] ivf = p.getIVFParams();
            assertEquals(256, ivf[0], "nList");
            assertEquals(100, ivf[1], "nIters");
            assertEquals(0, ivf[2], "useSoar");
        }
        try (IndexParams p = IndexParams.createIVF(MetricType.L2, 64, 50, true)) {
            int[] ivf = p.getIVFParams();
            assertEquals(64, ivf[0]);
            assertEquals(50, ivf[1]);
            assertEquals(1, ivf[2], "useSoar=true");
        }
    }

    @Test
    void testIVFParamsSetter() {
        try (IndexParams p = IndexParams.createIVF(MetricType.L2, 8, 10, false)) {
            p.setIVFParams(512, 200, true);
            int[] ivf = p.getIVFParams();
            assertEquals(512, ivf[0]);
            assertEquals(200, ivf[1]);
            assertEquals(1, ivf[2]);
        }
    }

    @Test
    void testInvertParamsRoundtrip() {
        try (IndexParams p = IndexParams.createInvert(true, false)) {
            boolean[] inv = p.getInvertParams();
            assertTrue(inv[0], "enableRangeOpt");
            assertFalse(inv[1], "enableWildcard");
        }
        try (IndexParams p = IndexParams.createInvert(false, true)) {
            boolean[] inv = p.getInvertParams();
            assertFalse(inv[0]);
            assertTrue(inv[1]);
        }
    }

    @Test
    void testIndexParamsMetricAndQuantize() {
        try (IndexParams p = IndexParams.createFlat(MetricType.L2)) {
            assertEquals(MetricType.L2, p.getMetricType());
            p.setMetricType(MetricType.IP);
            assertEquals(MetricType.IP, p.getMetricType());
        }
        try (IndexParams p = IndexParams.createHNSW(MetricType.COSINE, 16, 200)) {
            p.setQuantizeType(QuantizeType.INT8);
            assertEquals(QuantizeType.INT8, p.getQuantizeType());
            assertEquals(16, p.getHNSWM());
            assertEquals(200, p.getHNSWEfConstruction());
        }
    }

    // ---------------------------------------------------------------- ConfigData

    @Test
    void testConfigDataRatiosAndThreads() {
        try (ConfigData cfg = new ConfigData()) {
            cfg.setInvertToForwardScanRatio(0.25f);
            assertEquals(0.25f, cfg.getInvertToForwardScanRatio(), 1e-6);
            cfg.setBruteForceByKeysRatio(0.75f);
            assertEquals(0.75f, cfg.getBruteForceByKeysRatio(), 1e-6);
            cfg.setOptimizeThreadCount(3);
            assertEquals(3, cfg.getOptimizeThreadCount());
        }
    }

    // ---------------------------------------------------------------- LogConfig

    @Test
    void testLogConfigFile() {
        try (LogConfig lc = LogConfig.createFile(LogLevel.INFO, "/tmp/zveclog", "app", 10, 7)) {
            assertTrue(lc.isFileType());
            assertEquals("/tmp/zveclog", lc.getDir());
            assertEquals("app", lc.getBasename());
            assertEquals(10, lc.getFileSize());
            assertEquals(7, lc.getOverdueDays());
            assertEquals(LogLevel.INFO, lc.getLevel());
            lc.setLevel(LogLevel.WARN);
            assertEquals(LogLevel.WARN, lc.getLevel());
        }
    }

    @Test
    void testLogConfigConsoleNotFileType() {
        try (LogConfig lc = LogConfig.createConsole(LogLevel.ERROR)) {
            assertFalse(lc.isFileType());
            assertEquals(LogLevel.ERROR, lc.getLevel());
        }
    }

    // ---------------------------------------------------------------- Exceptions

    @Test
    void testOpenNonExistentThrows() {
        assertThrows(ZvecException.class, () -> Zvec.open("/no/such/zvec/collection/xyz", null));
    }

    @Test
    void testErrorCodeMappingAndPredicates() {
        ZvecException ex = new ZvecException(3, "bad arg");
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
        assertTrue(ex.isInvalidArgument());
        assertFalse(ex.isNotFound());
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.fromCode(1));
        assertEquals(ErrorCode.ALREADY_EXISTS, ErrorCode.fromCode(2));
    }
}
