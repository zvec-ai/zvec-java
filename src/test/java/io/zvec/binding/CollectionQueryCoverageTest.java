package io.zvec.binding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strong-assertion coverage for DML/DQL: query top-K count / ordering / PK hit,
 * output-field retrieval, filtering, update read-back and delete verification.
 * Follows the project's strong-assertion testing convention (no "does-not-throw"
 * only checks; results are validated).
 */
class CollectionQueryCoverageTest extends TestSupport {

    private List<Doc> queryVec(Collection coll, float[] q, int topK, String[] outputFields) {
        try (VectorQuery vq = new VectorQuery()) {
            vq.setFieldName("vec");
            vq.setTopK(topK);
            vq.setQueryVector(q);
            if (outputFields != null) {
                vq.setOutputFields(outputFields);
            }
            return coll.query(vq);
        }
    }

    @Test
    void testQueryTopKAndOrdering(@TempDir Path dir) {
        try (Collection coll = openIndexed(dir, "q_order")) {
            insertGraded(coll, 8);
            coll.flush();

            List<Doc> res = queryVec(coll, vecOf(3), 3, null);
            try {
                assertEquals(3, res.size(), "top-K count");
                // Exact-match vector must be the nearest neighbour.
                assertEquals("pk_3", res.get(0).getPK(), "exact match should rank first");

                // Scores must be monotonic (sorted by distance/similarity).
                boolean asc = true, desc = true;
                for (int i = 1; i < res.size(); i++) {
                    float prev = res.get(i - 1).getScore();
                    float cur = res.get(i).getScore();
                    if (prev > cur) asc = false;
                    if (prev < cur) desc = false;
                }
                assertTrue(asc || desc, "results must be sorted by score");
            } finally {
                Doc.freeDocs(res);
            }
        }
    }

    @Test
    void testQueryReturnsScalarFields(@TempDir Path dir) {
        try (Collection coll = openIndexed(dir, "q_scalar")) {
            insertGraded(coll, 8);
            coll.flush();

            List<Doc> res = queryVec(coll, vecOf(2), 1, new String[]{"cat", "num"});
            try {
                assertEquals(1, res.size());
                Doc r = res.get(0);
                assertEquals("pk_2", r.getPK());
                assertEquals("c0", r.getStringField("cat")); // 2 % 2 == 0
                assertEquals(2, r.getInt32Field("num"));
            } finally {
                Doc.freeDocs(res);
            }
        }
    }

    @Test
    void testQueryWithFilter(@TempDir Path dir) {
        try (Collection coll = openIndexed(dir, "q_filter")) {
            insertGraded(coll, 8);
            coll.flush();

            try (VectorQuery vq = new VectorQuery()) {
                vq.setFieldName("vec");
                vq.setTopK(8);
                vq.setQueryVector(vecOf(0));
                vq.setFilter("num >= 4");
                vq.setOutputFields(new String[]{"num"});
                List<Doc> res = coll.query(vq);
                try {
                    assertFalse(res.isEmpty(), "filter should still match docs");
                    for (Doc r : res) {
                        assertTrue(r.getInt32Field("num") >= 4,
                                "filtered result num must satisfy num>=4 but was " + r.getInt32Field("num"));
                    }
                } finally {
                    Doc.freeDocs(res);
                }
            }
        }
    }

    @Test
    void testUpdateReadBackViaQuery(@TempDir Path dir) {
        try (Collection coll = openIndexed(dir, "q_update")) {
            insertGraded(coll, 8);
            coll.flush();

            // Full-doc update of pk_2.
            Doc upd = new Doc();
            upd.setPK("pk_2");
            upd.addVectorFP32Field("vec", vecOf(2));
            upd.addStringField("cat", "updated");
            upd.addInt32Field("num", 999);
            List<Doc> batch = new ArrayList<>();
            batch.add(upd);
            Collection.WriteResult wr = coll.update(batch);
            Doc.freeDocs(batch);
            assertEquals(1, wr.successCount, "update should affect exactly 1 doc");
            coll.flush();

            List<Doc> res = queryVec(coll, vecOf(2), 1, new String[]{"cat", "num"});
            try {
                assertEquals(1, res.size());
                assertEquals("pk_2", res.get(0).getPK());
                assertEquals("updated", res.get(0).getStringField("cat"), "cat must reflect update");
                assertEquals(999, res.get(0).getInt32Field("num"), "num must reflect update");
            } finally {
                Doc.freeDocs(res);
            }
        }
    }

    @Test
    void testDeleteRemovesFromQuery(@TempDir Path dir) {
        try (Collection coll = openIndexed(dir, "q_delete")) {
            insertGraded(coll, 8);
            coll.flush();

            // pk_5 present before delete.
            List<Doc> before = queryVec(coll, vecOf(5), 1, null);
            try {
                assertEquals("pk_5", before.get(0).getPK());
            } finally {
                Doc.freeDocs(before);
            }

            Collection.WriteResult wr = coll.delete(java.util.Collections.singletonList("pk_5"));
            assertEquals(1, wr.successCount, "delete should remove exactly 1 doc");
            coll.flush();

            // pk_5 must no longer appear anywhere in the result set.
            List<Doc> after = queryVec(coll, vecOf(5), 8, null);
            try {
                for (Doc r : after) {
                    assertNotEquals("pk_5", r.getPK(), "deleted pk_5 must not appear");
                }
            } finally {
                Doc.freeDocs(after);
            }
        }
    }

    @Test
    void testDeleteByFilterRemovesMatching(@TempDir Path dir) {
        try (Collection coll = openIndexed(dir, "q_delfilter")) {
            insertGraded(coll, 8); // even -> c0, odd -> c1
            coll.flush();

            coll.deleteByFilter("cat = 'c1'");
            coll.flush();

            try (VectorQuery vq = new VectorQuery()) {
                vq.setFieldName("vec");
                vq.setTopK(8);
                vq.setQueryVector(vecOf(0));
                vq.setOutputFields(new String[]{"cat"});
                List<Doc> res = coll.query(vq);
                try {
                    assertFalse(res.isEmpty());
                    for (Doc r : res) {
                        assertEquals("c0", r.getStringField("cat"),
                                "all c1 docs should have been deleted");
                    }
                } finally {
                    Doc.freeDocs(res);
                }
            }
        }
    }
}
