# zvec-java

English | [简体中文](README.md)

**zvec-java** provides industrial-grade Java bindings for the [Zvec](https://github.com/alibaba/zvec) vector database C API, built on [JavaCPP](https://github.com/bytedeco/javacpp) for JNI binding generation. JavaCPP auto-generates the JNI glue from `zvec/c_api.h`, bundles the per-platform native libraries into the JAR, and extracts and loads them at runtime — **no hand-written JNI code, and no manual library-path configuration required**.

## Features

- **JavaCPP + JNI**: JavaCPP parses `c_api.h` to auto-generate the low-level binding class `ZvecNative` and the JNI glue, balancing performance and maintainability.
- **Cross-platform, zero-config**: native libraries (`libzvec_c_api` + `libjniZvecNative`) are packed into the JAR under `platform-arch` directories and loaded automatically at runtime with no configuration.
- **High-level wrappers**: type-safe, resource-safe Java objects layered on top of the generated `ZvecNative`.
- **AutoCloseable resource management**: every object holding native resources implements `AutoCloseable` for use with try-with-resources.
- **Rich index support**: HNSW, IVF, Flat, Invert (inverted) and their quantized variants.
- **Many data types**: 30+ field types, including sparse/dense vectors of various dimensions.
- **Java 8+**: compatible with Java 8 and above.
- **95 unit tests**: all passing; critical DML/DQL paths use strong assertions (topK count / score ordering / PK hits, update read-back, delete-removal verification).

## Quick Start

### Prerequisites

| Tool | Minimum version | Purpose |
|------|-----------------|---------|
| JDK  | 8       | Compile & run |
| Maven | 3.6    | Build |
| C++ compiler (clang / gcc / MSVC) | C++17 support | JavaCPP compiles the JNI glue |
| CMake + Ninja | 3.30 / 1.11 | Build the Zvec C library |

### Get the source (with submodules)

The Zvec core is included as a **git submodule** at `./zvec` (mirroring DuckDB Java's layout):

```bash
git clone <this-repo>
cd zvec-java
git submodule update --init --recursive
```

### Build the Zvec C library

```bash
cd zvec
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_C_BINDINGS=ON -G Ninja
cmake --build . --target zvec_c_api -j
# Artifacts are placed under zvec/build/lib/
cd ../..
```

### Build the Java binding

```bash
# By default, headers and libraries are read from the ./zvec submodule (zvec.home=${project.basedir}/zvec)
mvn package

# To reuse an existing Zvec checkout (e.g. a sibling directory), override with -Dzvec.home:
mvn package -Dzvec.home=/path/to/zvec
```

Key build-time properties:

| Property | Default | Description |
|----------|---------|-------------|
| `zvec.home` | `${project.basedir}/zvec` | Zvec core root directory (submodule) |
| `zvec.include.path` | `${zvec.home}/src/include` | Header directory (JavaCPP parse) |
| `zvec.lib.path` | `${zvec.home}/build/lib` | Link library directory (JavaCPP link) |

### Run tests

```bash
mvn test
# Or specify the Zvec location
mvn test -Dzvec.home=/path/to/zvec
```

### Run the example

The packaged fat JAR **already embeds the native library for the current platform**, so no library-path configuration is needed at runtime:

```bash
mvn package -DskipTests
java -jar target/zvec-java-1.0.0-with-dependencies.jar
```

## Project Structure

```
zvec-java/
├── pom.xml                                          # Maven build (two-stage JavaCPP plugin: parse + build)
├── zvec/                                            # git submodule: Zvec core
└── src/
    ├── main/java/io/zvec/binding/
    │   ├── presets/ZvecConfig.java                  # JavaCPP InfoMapper: guides parsing of c_api.h
    │   ├── ZvecNative.java                          # [generated] low-level JNI binding (do not edit; git-ignored)
    │   ├── NativeSupport.java                        # Bridging helpers: String <-> const char*, etc.
    │   ├── NativeLoader.java                         # Three-tier native library loader
    │   ├── Zvec.java                                # Top-level entry: init, version, Collection factory
    │   ├── Collection.java                          # Collection ops (CRUD, search)
    │   ├── CollectionOptions.java / CollectionSchema.java / CollectionStats.java
    │   ├── FieldSchema.java                         # Field schema definitions
    │   ├── Doc.java                                 # Document CRUD (read/write typed fields)
    │   ├── IndexParams.java                         # Index params (HNSW/IVF/Flat/Invert)
    │   ├── VectorQuery.java / GroupByVectorQuery.java
    │   ├── ConfigData.java / LogConfig.java
    │   ├── ZvecException.java
    │   └── DataType / IndexType / MetricType / QuantizeType / LogLevel / DocOperator / ErrorCode (enums)
    └── test/java/io/zvec/binding/
        ├── ZvecTest.java                            # Basic API tests
        ├── TestSupport.java                         # Test base (guarded init + indexed collection/vector helpers)
        ├── DocCoverageTest.java                     # Doc metadata / UTF-8 / exception strong assertions
        ├── SchemaIndexConfigCoverageTest.java       # Schema / IndexParams (out params) / Config / exceptions
        └── CollectionQueryCoverageTest.java         # DML/DQL strong assertions (query/update/delete/filter)
```

## Code Examples

### Initialize and shut down

```java
// Initialize with default configuration
Zvec.initialize(null);

// Custom configuration
try (ConfigData config = new ConfigData()) {
    config.setQueryThreadCount(4);
    config.setMemoryLimit(512 * 1024 * 1024L); // 512MB
    config.setConsoleLog(LogLevel.INFO);
    Zvec.initialize(config);
}

// Shut down (call before process exit)
Zvec.shutdown();
```

### Define a schema and create a collection

```java
CollectionSchema schema = new CollectionSchema("my_collection");

// FP32 vector field
try (FieldSchema vecField = new FieldSchema("embedding", DataType.VECTOR_FP32, false, 128)) {
    try (IndexParams hnsw = IndexParams.createHNSW(MetricType.L2, 32, 200)) {
        vecField.setIndexParams(hnsw);
    }
    schema.addField(vecField);
}

// Metadata field
try (FieldSchema titleField = new FieldSchema("title", DataType.STRING, true, 0)) {
    schema.addField(titleField);
}

// Create and open the collection
Collection coll = Zvec.createAndOpen("/tmp/my_db", schema, null);
schema.close();
```

### Insert and query

```java
// Insert documents
List<Doc> docs = new ArrayList<>();
Doc doc = new Doc();
doc.setPK("doc_001");
doc.addStringField("title", "Hello Zvec");
doc.addVectorFP32Field("embedding", new float[128]); // illustrative: all-zero vector
docs.add(doc);
coll.insert(docs);
Doc.freeDocs(docs);
coll.flush();

// Vector query
try (VectorQuery query = new VectorQuery()) {
    query.setTopK(10);
    query.setFieldName("embedding");
    query.setQueryVector(new float[128]); // query vector

    List<Doc> results = coll.query(query);
    for (Doc d : results) {
        System.out.printf("id=%s, score=%.4f%n", d.getPK(), d.getScore());
    }
    Doc.freeDocs(results); // results are backed by native memory; free them after use
}
coll.close();
```

### Index types

```java
IndexParams hnsw   = IndexParams.createHNSW(MetricType.L2, 32, 200);
IndexParams hnswQ  = IndexParams.createHNSWQuantized(MetricType.IP, 32, 200, QuantizeType.FP16);
IndexParams ivf    = IndexParams.createIVF(MetricType.COSINE, 256, 100, false);
IndexParams flat   = IndexParams.createFlat(MetricType.L2);
IndexParams invert = IndexParams.createInvert(true, false); // inverted, for text/tags
```

## Dependencies

| Dependency | Version | Purpose | License |
|------------|---------|---------|---------|
| `org.bytedeco:javacpp` | 1.5.11 | JNI code generation + cross-platform native loading | Apache-2.0 **or** GPL-2.0-or-later **or** GPL-2.0-with-classpath-exception; used under Apache-2.0 |
| `org.junit.jupiter:junit-jupiter` | 5.10.2 | Unit tests (test scope only) | EPL-2.0 |

## Architecture

```
  Java application code
       │
       ▼
  High-level API (Zvec / Collection / Doc / VectorQuery …)   type-safe + AutoCloseable
       │
       ▼
  ZvecNative (JavaCPP-generated JNI binding)
       │  JavaCPP-generated JNI glue (libjniZvecNative)
       ▼
  libzvec_c_api.(so|dylib|dll)
       │
       ▼
  Zvec C++ core engine
```

Build flow: JavaCPP `Parser` parses `c_api.h` (guided by `presets/ZvecConfig`) to generate `ZvecNative.java` → compile → JavaCPP `Generator/Compiler` generates and compiles the JNI glue into `libjniZvecNative`, linked against `zvec_c_api` → both are packed into the JAR's `platform-arch` directory.

## FAQ

### How are native libraries loaded?

The native libraries (`zvec_c_api` + the JavaCPP JNI glue `jnizvec`) are resolved by `NativeLoader` using a **three-tier priority** (highest to lowest):

1. **Tier 1 · Explicit path**: set `-Dzvec.native.path=/dir` or the `ZVEC_NATIVE_PATH` environment variable to resolve `zvec_c_api` from that directory first (implemented internally via JavaCPP's `pathsFirst` + `platform.preloadpath`). Intended for local development or custom-built native libraries.
2. **Tier 2 · Classpath / fat JAR**: the default behavior — extract from the `platform-arch` resource directory inside the JAR to a temp directory and load, **with no library-path configuration needed**.
3. **Tier 3 · System library path**: fall back to `java.library.path` / `LD_LIBRARY_PATH` / `DYLD_LIBRARY_PATH` / `PATH`.

Loading is attempted Tier 1 → Tier 2 → Tier 3; if all three fail, an actionable `UnsatisfiedLinkError` listing the sources above is thrown to aid diagnosis.

```bash
# Tier 1: point at a locally built native library directory
java -Dzvec.native.path=/path/to/zvec/build/lib -jar app.jar
# Or via environment variable
ZVEC_NATIVE_PATH=/path/to/zvec/build/lib java -jar app.jar
```

A local `mvn package` produces a fat JAR containing only the native library for **the platform it was built on**; the CI **Publish JAR** workflow builds on each platform and aggregates a **multi-platform fat JAR bundling native libraries for all platforms** (see `.github/workflows/publish-jar.yml`).

### `Collection.fetch()` returns empty / InvalidArgument

`fetch()` requires the target field to have a **forward index**. Make sure a forward index is configured in the schema for the fields you want to fetch, or use `query()` instead.

### Memory management

- Every `AutoCloseable` object (`Collection`, `Doc`, `IndexParams`, `VectorQuery`, etc.) should be used within a try-with-resources block, or explicitly `close()`d in a finally block.
- The `List<Doc>` returned by `Collection.query()` / `Collection.fetch()` is backed by native memory and must be released with `Doc.freeDocs(list)` after use.

### `Doc.validate(...)`

The Zvec C API provides no document-level validation function (`zvec_doc_validate` does not exist); this method throws `UnsupportedOperationException`. Use `CollectionSchema.validate()` / `FieldSchema.validate()` instead.

## License

This project is licensed under the **Apache License 2.0**, consistent with the main Zvec project; see [zvec/LICENSE](zvec/LICENSE).

### Third-party license notes

- **JavaCPP** (`org.bytedeco:javacpp:1.5.11) is triple-licensed:
  `Apache-2.0 OR GPL-2.0-or-later OR GPL-2.0-with-classpath-exception`.
  This project uses JavaCPP under the **Apache-2.0** terms.
- **JUnit 5** is used only in the `test` scope and is licensed under the EPL-2.0.
  It is not included in the released JAR.
- The native `zvec_c_api` library (built from the `zvec` submodule) may include
  third-party code such as **RocksDB**. Some RocksDB components (for example the
  PerconaFT-derived `range_tree` code under `utilities/transactions/lock/range/`)
  are under GPL/AGPL-style licenses. Distributors of binary packages should
  verify that the `zvec` core is built in a way that is compatible with their
  desired license terms.
