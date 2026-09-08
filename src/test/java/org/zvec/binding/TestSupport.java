package org.zvec.binding;

import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shared base for the coverage test classes.
 *
 * <p>Initializes the Zvec library once per JVM (guarded, never shut down mid-run
 * so multiple test classes can coexist) and provides helpers for building
 * indexed collections and deterministic vectors used by strong-assertion tests.
 */
abstract class TestSupport {

    static final Random RAND = new Random(7);

    @BeforeAll
    static void initLibrary() {
        if (!Zvec.isInitialized()) {
            Zvec.initialize(null);
        }
    }

    /** A distinct, deterministic, L2-normalized 4-d vector for document {@code i}. */
    static float[] vecOf(int i) {
        float[] v = {i + 1f, i + 2f, i + 3f, i + 4f};
        float norm = 0;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        for (int k = 0; k < v.length; k++) v[k] /= norm;
        return v;
    }

    static float[] randVec(int dim) {
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

    /**
     * Create and open a collection with an HNSW/COSINE-indexed 4-d vector field
     * {@code vec}, an invert-indexed string field {@code cat}, and an int field
     * {@code num}. Mirrors the working setup in {@code BasicExample}.
     */
    static Collection openIndexed(Path dir, String name) {
        CollectionSchema schema = new CollectionSchema(name);
        try (FieldSchema vecF = new FieldSchema("vec", DataType.VECTOR_FP32, false, 4)) {
            try (IndexParams hnsw = IndexParams.createHNSW(MetricType.COSINE, 16, 200)) {
                vecF.setIndexParams(hnsw);
            }
            schema.addField(vecF);
        }
        try (FieldSchema catF = new FieldSchema("cat", DataType.STRING, true, 0)) {
            try (IndexParams inv = IndexParams.createInvert(true, false)) {
                catF.setIndexParams(inv);
            }
            schema.addField(catF);
        }
        try (FieldSchema numF = new FieldSchema("num", DataType.INT32, true, 0)) {
            schema.addField(numF);
        }
        String path = dir.resolve("coll").toString();
        Collection coll = Zvec.createAndOpen(path, schema, null);
        schema.close();
        return coll;
    }

    /** Insert {@code n} docs (pk_i / vec=vecOf(i) / cat=c{i%2} / num=i) and return their PKs. */
    static List<String> insertGraded(Collection coll, int n) {
        List<Doc> docs = new ArrayList<>(n);
        List<String> pks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Doc d = new Doc();
            String pk = "pk_" + i;
            d.setPK(pk);
            d.addVectorFP32Field("vec", vecOf(i));
            d.addStringField("cat", "c" + (i % 2));
            d.addInt32Field("num", i);
            docs.add(d);
            pks.add(pk);
        }
        Collection.WriteResult r = coll.insert(docs);
        Doc.freeDocs(docs);
        if (r.successCount != n) {
            throw new IllegalStateException("insert failed: " + r);
        }
        return pks;
    }
}
