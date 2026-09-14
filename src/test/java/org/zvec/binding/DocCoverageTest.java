package org.zvec.binding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strong-assertion coverage for {@link Doc} metadata, mutation, UTF-8 handling
 * and error paths that the original {@code ZvecTest} did not exercise.
 */
class DocCoverageTest extends TestSupport {

    @Test
    void testDocIdRoundtrip() {
        try (Doc d = new Doc()) {
            d.setDocID(1234567890123L);
            assertEquals(1234567890123L, d.getDocID());
        }
    }

    @Test
    void testScoreRoundtrip() {
        try (Doc d = new Doc()) {
            d.setScore(0.875f);
            assertEquals(0.875f, d.getScore(), 1e-6);
        }
    }

    @Test
    void testOperatorRoundtrip() {
        try (Doc d = new Doc()) {
            d.setOperator(DocOperator.UPDATE);
            assertEquals(DocOperator.UPDATE, d.getOperator());
            d.setOperator(DocOperator.DELETE);
            assertEquals(DocOperator.DELETE, d.getOperator());
        }
    }

    @Test
    void testEmptyAndClear() {
        try (Doc d = new Doc()) {
            assertTrue(d.isEmpty());
            d.addInt32Field("x", 1);
            assertFalse(d.isEmpty());
            assertEquals(1, d.getFieldCount());
            d.clear();
            assertTrue(d.isEmpty());
            assertEquals(0, d.getFieldCount());
        }
    }

    @Test
    void testFieldNull() {
        try (Doc d = new Doc()) {
            d.addInt32Field("x", 7);
            assertTrue(d.hasField("x"));
            assertTrue(d.hasFieldValue("x"));
            d.setFieldNull("x");
            assertTrue(d.isFieldNull("x"));
        }
    }

    @Test
    void testRemoveField() {
        try (Doc d = new Doc()) {
            d.addInt32Field("a", 1);
            d.addInt32Field("b", 2);
            assertEquals(2, d.getFieldCount());
            d.removeField("a");
            assertFalse(d.hasField("a"));
            assertTrue(d.hasField("b"));
            assertEquals(1, d.getFieldCount());
        }
    }

    @Test
    void testGetFieldNames() {
        try (Doc d = new Doc()) {
            d.addInt32Field("alpha", 1);
            d.addStringField("beta", "x");
            d.addFloatField("gamma", 1.0f);
            List<String> names = d.getFieldNames();
            assertEquals(3, names.size());
            assertTrue(names.contains("alpha"));
            assertTrue(names.contains("beta"));
            assertTrue(names.contains("gamma"));
        }
    }

    @Test
    void testMerge() {
        try (Doc a = new Doc(); Doc b = new Doc()) {
            a.addInt32Field("x", 1);
            b.addInt32Field("y", 2);
            a.merge(b);
            assertTrue(a.hasField("x"));
            assertTrue(a.hasField("y"));
            assertEquals(2, a.getInt32Field("y"));
        }
    }

    @Test
    void testMemoryUsage() {
        try (Doc d = new Doc()) {
            d.setPK("m");
            d.addVectorFP32Field("vec", vecOf(1));
            assertTrue(d.memoryUsage() > 0, "memoryUsage should be positive");
        }
    }

    @Test
    void testToDetailString() {
        try (Doc d = new Doc()) {
            d.setPK("detail_pk");
            d.addInt32Field("n", 5);
            String s = d.toDetailString();
            assertNotNull(s);
            assertFalse(s.isEmpty());
        }
    }

    @Test
    void testBinaryFieldRoundtrip() {
        try (Doc d = new Doc()) {
            byte[] blob = {1, 2, 3, 4, 5, 0, -1, 127};
            d.addBinaryField("blob", blob);
            assertTrue(d.hasField("blob"));
            assertArrayEquals(blob, d.getBinaryField("blob"),
                    "binary data must survive the write/read round trip, including NUL bytes");
        }
    }

    @Test
    void testGetMissingBinaryFieldThrows() {
        try (Doc d = new Doc()) {
            // The C API rejects an unknown field with InvalidArgument rather
            // than returning an empty buffer.
            ZvecException ex = assertThrows(ZvecException.class, () -> d.getBinaryField("nope"));
            assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
        }
    }

    @Test
    void testUtf8Roundtrip() {
        try (Doc d = new Doc()) {
            d.setPK("文档_甲");
            d.addStringField("name", "向量数据库 Zvec 🚀");
            assertEquals("文档_甲", d.getPK());
            assertEquals("向量数据库 Zvec 🚀", d.getStringField("name"));
        }
    }

    @Test
    void testGetMissingFieldThrows() {
        try (Doc d = new Doc()) {
            assertThrows(ZvecException.class, () -> d.getInt32Field("does_not_exist"));
        }
    }

    @Test
    void testDeserializeEmptyThrows() {
        ZvecException ex = assertThrows(ZvecException.class, () -> Doc.deserialize(new byte[0]));
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    }

    @Test
    void testSerializeRoundtripFields() {
        try (Doc d = new Doc()) {
            d.setPK("ser");
            d.addInt32Field("num", 123);
            d.addStringField("cat", "hello");
            byte[] bytes = d.serialize();
            assertTrue(bytes.length > 0);
            try (Doc d2 = Doc.deserialize(bytes)) {
                assertEquals("ser", d2.getPK());
                assertEquals(123, d2.getInt32Field("num"));
                assertEquals("hello", d2.getStringField("cat"));
            }
        }
    }

    @Test
    void testClosedDocRejectsOperations() {
        Doc d = new Doc();
        d.setPK("closed");
        assertTrue(d.isOpen());
        d.close();
        assertFalse(d.isOpen(), "isOpen() must report a closed document");

        // The C API treats a NULL document as a no-op, so a document reused
        // after close() would silently discard writes. It has to throw instead.
        ZvecException setPk = assertThrows(ZvecException.class, () -> d.setPK("again"));
        assertEquals(ErrorCode.FAILED_PRECONDITION, setPk.getErrorCode());
        assertTrue(setPk.getMessage().contains("closed"), setPk.getMessage());

        assertThrows(ZvecException.class, () -> d.addInt32Field("num", 1));
        assertThrows(ZvecException.class, () -> d.addStringField("cat", "x"));
        assertThrows(ZvecException.class, d::getPK);
        assertThrows(ZvecException.class, () -> d.getStringField("cat"));
        assertThrows(ZvecException.class, d::getFieldNames);
        assertThrows(ZvecException.class, d::memoryUsage);

        try (Doc other = new Doc()) {
            ZvecException merge = assertThrows(ZvecException.class, () -> other.merge(d));
            assertEquals(ErrorCode.FAILED_PRECONDITION, merge.getErrorCode());
        }

        // close() stays idempotent once the handle is gone.
        d.close();
        Doc.freeDocs(java.util.Collections.singletonList(d));
    }

    @Test
    void testNullArgumentsRejected() {
        try (Doc d = new Doc()) {
            // A NULL primary key is ignored by the C API and would only surface
            // later as an opaque write failure.
            ZvecException pk = assertThrows(ZvecException.class, () -> d.setPK(null));
            assertEquals(ErrorCode.INVALID_ARGUMENT, pk.getErrorCode());

            ZvecException merge = assertThrows(ZvecException.class, () -> d.merge(null));
            assertEquals(ErrorCode.INVALID_ARGUMENT, merge.getErrorCode());
        }
    }
}
