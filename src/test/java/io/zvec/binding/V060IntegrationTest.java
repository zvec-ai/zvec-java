package io.zvec.binding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for v0.6.0 features that require the native
 * zvec_c_api library: FTS-only collections and hybrid (vector + FTS) multi-query.
 */
class V060IntegrationTest extends TestSupport {

    // ------------------------------------------------------------------ FTS-only collection

    @Test
    void testFtsOnlyCollection(@TempDir Path dir) {
        CollectionSchema schema = new CollectionSchema("fts_only");
        try (FieldSchema title = new FieldSchema("title", DataType.STRING, false, 0)) {
            schema.addField(title);
        }
        try (FieldSchema content = new FieldSchema("content", DataType.STRING, false, 0)) {
            try (IndexParams fts = IndexParams.createFTS("standard", new String[]{"lowercase"}, null)) {
                content.setIndexParams(fts);
            }
            schema.addField(content);
        }

        String path = dir.resolve("coll").toString();
        try (Collection coll = Zvec.createAndOpen(path, schema, null)) {
            schema.close();

            List<Doc> docs = new ArrayList<>();
            docs.add(makeDoc("pk_0", "hello world", "intro"));
            docs.add(makeDoc("pk_1", "hello foo bar", "guide"));
            docs.add(makeDoc("pk_2", "hello baz", "tips"));
            docs.add(makeDoc("pk_3", "hello hello", "more"));
            docs.add(makeDoc("pk_4", "nothing relevant", "other"));
            Collection.WriteResult wr = coll.insert(docs);
            Doc.freeDocs(docs);
            assertEquals(5, wr.successCount, "all docs should insert");
            coll.flush();

            List<Doc> hits = ftsQuery(coll, "hello");
            try {
                assertFalse(hits.isEmpty(), "FTS must match 'hello'");
                Set<String> matched = new HashSet<>();
                for (Doc d : hits) {
                    matched.add(d.getPK());
                }
                assertTrue(matched.contains("pk_0"), "pk_0 should match hello");
                assertTrue(matched.contains("pk_3"), "pk_3 should match hello");
                assertFalse(matched.contains("pk_4"), "pk_4 has no hello");
            } finally {
                Doc.freeDocs(hits);
            }

            List<Doc> none = ftsQuery(coll, "missing_term_xyz");
            try {
                assertTrue(none.isEmpty(), "nonexistent term should return empty");
            } finally {
                Doc.freeDocs(none);
            }
        }
    }

    // ------------------------------------------------------------------ Hybrid vector + FTS

    @Test
    void testHybridVectorAndFts(@TempDir Path dir) {
        CollectionSchema schema = new CollectionSchema("hybrid");
        try (FieldSchema vec = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
            try (IndexParams hnsw = IndexParams.createHNSW(MetricType.COSINE, 16, 200)) {
                vec.setIndexParams(hnsw);
            }
            schema.addField(vec);
        }
        try (FieldSchema content = new FieldSchema("content", DataType.STRING, false, 0)) {
            try (IndexParams fts = IndexParams.createFTS("standard", new String[]{"lowercase"}, null)) {
                content.setIndexParams(fts);
            }
            schema.addField(content);
        }

        String path = dir.resolve("coll").toString();
        try (Collection coll = Zvec.createAndOpen(path, schema, null)) {
            schema.close();

            // Insert docs where vector similarity and text relevance disagree.
            List<Doc> docs = new ArrayList<>();
            docs.add(makeHybridDoc("pk_0", vecOf(0), "zvec database"));
            docs.add(makeHybridDoc("pk_1", vecOf(1), "java binding"));
            docs.add(makeHybridDoc("pk_2", vecOf(2), "zvec java binding"));
            docs.add(makeHybridDoc("pk_3", vecOf(3), "unrelated text"));
            Collection.WriteResult wr = coll.insert(docs);
            Doc.freeDocs(docs);
            assertEquals(4, wr.successCount);
            coll.flush();

            try (MultiQuery mq = new MultiQuery()) {
                mq.setTopk(10);

                try (SubQuery vecSq = new SubQuery()) {
                    vecSq.setFieldName("vec");
                    vecSq.setQueryVector(vecOf(2)); // closest to pk_2
                    mq.addSubQuery(vecSq);
                }

                try (SubQuery ftsSq = new SubQuery()) {
                    ftsSq.setFieldName("content");
                    try (FtsPayload fts = new FtsPayload()) {
                        fts.setMatchString("zvec");
                        ftsSq.setFts(fts);
                    }
                    mq.addSubQuery(ftsSq);
                }

                mq.setRerankRrf(60);

                List<Doc> results = coll.query(mq);
                try {
                    assertFalse(results.isEmpty(), "hybrid query should return results");
                    Set<String> pks = new HashSet<>();
                    for (Doc d : results) {
                        pks.add(d.getPK());
                    }
                    // pk_2 matches both vector near q=2 and text "zvec" -> should appear.
                    assertTrue(pks.contains("pk_2"), "pk_2 must be in hybrid results");
                } finally {
                    Doc.freeDocs(results);
                }
            }
        }
    }

    @Test
    void testMultiQueryVectorOnly(@TempDir Path dir) {
        try (Collection coll = openIndexed(dir, "multi_vec")) {
            insertGraded(coll, 8);
            coll.flush();

            try (MultiQuery mq = new MultiQuery()) {
                mq.setTopk(3);
                mq.setIncludeVector(true);
                mq.setOutputFields(new String[]{"num"});

                try (SubQuery sq1 = new SubQuery()) {
                    sq1.setFieldName("vec");
                    sq1.setQueryVector(vecOf(3));
                    mq.addSubQuery(sq1);
                }

                try (SubQuery sq2 = new SubQuery()) {
                    sq2.setFieldName("vec");
                    sq2.setQueryVector(vecOf(2));
                    mq.addSubQuery(sq2);
                }

                List<Doc> results = coll.query(mq);
                try {
                    assertEquals(3, results.size(), "topk should be respected");
                    assertEquals("pk_3", results.get(0).getPK(), "exact vector match first");
                    assertEquals(3, results.get(0).getInt32Field("num"));
                } finally {
                    Doc.freeDocs(results);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private List<Doc> ftsQuery(Collection coll, String term) {
        try (VectorQuery vq = new VectorQuery()) {
            vq.setFieldName("content");
            vq.setTopK(10);
            vq.setOutputFields(new String[]{"title", "content"});
            try (FtsPayload fts = new FtsPayload()) {
                fts.setMatchString(term);
                vq.setFts(fts);
            }
            return coll.query(vq);
        }
    }

    private Doc makeDoc(String pk, String content, String title) {
        Doc d = new Doc();
        d.setPK(pk);
        d.addStringField("content", content);
        if (title != null) {
            d.addStringField("title", title);
        }
        return d;
    }

    private Doc makeHybridDoc(String pk, float[] vec, String content) {
        Doc d = new Doc();
        d.setPK(pk);
        d.addVectorFP32Field("vec", vec);
        d.addStringField("content", content);
        return d;
    }
}
