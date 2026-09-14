# Changelog

Notable changes to zvec-java are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the version number
tracks the bundled zvec native version: `0.7.0` ships the libraries built from
zvec `v0.7.0`. A binding-only fix bumps the third segment.

## [Unreleased]

## [0.7.0]

First release, cut from the zvec v0.7.0 C API and published to Maven Central as
`org.zvec:zvec-java`.

### Added

- JavaCPP-generated low-level binding (`ZvecNative`) for `zvec/c_api.h`, driven
  by the `presets/ZvecConfig` info mapper — no hand-written JNI.
- High-level wrappers in `org.zvec.binding`: `Zvec`, `Collection`,
  `CollectionSchema`, `FieldSchema`, `Doc`, `IndexParams`, `VectorQuery`,
  `MultiQuery`, `ConfigData`, `LogConfig` and the enum types, all
  `AutoCloseable` where they own native memory.
- zvec v0.7.0 API coverage: DiskANN and IVF-RaBitQ index and query parameters,
  the collection snapshot iterator (`Collection.createIterator`, `DocIterator`,
  `IteratorOptions`), and I/O backend introspection
  (`Zvec.getIoBackendType` and friends).
- Typed query parameters for every index family — `FlatQueryParams`,
  `HnswQueryParams`, `IvfQueryParams`, `IvfRabitqQueryParams`,
  `DiskAnnQueryParams`, `VamanaQueryParams` and `FtsQueryParams` — accepted by
  `VectorQuery`, `GroupByVectorQuery` and `SubQuery`, each defaulting to the
  values the C API documents. They replace setters that took a raw `Pointer`,
  which left the ownership transfer invisible at the call site, and close the
  last coverage gaps: Vamana was missing from both vector queries and
  IVF-RaBitQ from `SubQuery`.
- `Doc.getBinaryField()`, completing a binary-field round trip that was
  previously write-only.
- `setOutputFields(null)` on `VectorQuery`, `MultiQuery` and
  `GroupByVectorQuery` means "return every field", the way the C API reads a
  null field list and the way `IteratorOptions` already behaved.
- Artifact family following the `org.duckdb:duckdb_jdbc` layout: a main JAR with
  the natives for every supported platform, single-platform classifier JARs
  (`macosx-arm64`, `linux-x86_64`, `linux-arm64`, `windows-x86_64`), a `nolib`
  JAR for consumers that supply their own `zvec_c_api`, plus sources and
  javadoc.
- The cppjieba dictionary bundled under `zvec/jieba_dict/`, extracted to a
  per-version cache directory and registered automatically by
  `Zvec.initialize()`, so the `jieba` full-text tokenizer works with no setup.
- Three-tier native library resolution (`-Dzvec.native.path` /
  `ZVEC_NATIVE_PATH`, then the JAR, then the system library path) with an
  actionable `UnsatisfiedLinkError` when all three fail.
- `ZvecException` messages carry the native error detail (code and text from
  `zvec_get_last_error_details`), so a failed call reports what zvec said
  instead of a bare error code.
- `Collection` and `Doc` guard every native call with a use-after-close check
  and expose `isOpen()`; `close()`/`destroy()` are safe to call from several
  threads. Query parameters whose ownership has already transferred to a query
  reject further use instead of passing a released handle to the C API.
- Test suite of 139 tests, with strong assertions on the DML/DQL paths and
  `assumeTrue` skips for platform-gated indexes.
- `scripts/smoke-test.sh`: loads a built JAR exactly as a consumer does —
  natives out of the JAR, dictionary extraction, collection create/insert/flush,
  vector search and jieba full-text search — needing nothing but a JDK.
- Every JAR declares `Automatic-Module-Name: org.zvec.binding` and its
  `Implementation-*` coordinates, and carries `LICENSE` plus `NOTICE` under
  `META-INF/`.

### Fixed

- `Collection.query()` released the native result array only when the query
  matched something. The C API `malloc()`s that array unconditionally and
  `malloc(0)` may return a non-NULL block, so every empty result set leaked it.
- `FtsPayload.setQueryString()` and `setMatchString()` were the last call sites
  marshalling through the generated `String` overload, i.e. modified UTF-8,
  which encodes astral characters (emoji, CJK extension B) as surrogate pairs
  the C++ side cannot read back. Both now marshal as UTF-8 like the rest of the
  binding.
- The `IndexParams` factories leaked the freshly allocated native params when a
  later initialization step failed, and reported such a failure as a bare method
  name. Initialization errors now release the handle and name the offending
  argument.
- A closed `Doc` inside a write batch reached the C API as a NULL element, which
  `convert_zvec_docs_to_internal()` dereferences without a check: the result was
  a `SIGSEGV` that took the whole JVM down. `insert()`, `update()` and
  `upsert()` now reject closed and null documents with a `ZvecException`.
- `SubQuery.setDiskAnnParams()` passed a borrowed handle to a C call that takes
  ownership, so the sub-query freed parameters its caller still held and a
  later `close()` freed them again.
- `ConfigData.setLogConfig()` leaked the native log configuration when
  `zvec_config_data_set_log_config` rejected it: the C side only takes ownership
  on success, so the failure path has to release it.
- `NativeSupport.strArray()` threw on a null array and encoded its elements with
  the platform default charset. It is now null-safe and pins UTF-8.
- `CollectionStats` released the native stats object only after every getter had
  succeeded, leaking it when one threw mid-construction.

### CI / release automation

- `release.yml`: builds `zvec_c_api` for all four platforms and publishes a
  GitHub Release with the C library archives and `.sha256` checksums.
- `publish-jar.yml`: builds the JAR family inside `manylinux_2_28`, verifies the
  bundle, optionally deploys it to the Sonatype Central Portal, and smoke-tests
  the result on both Linux architectures before anyone clicks **Publish**.
- The Linux natives are checked against a `GLIBC_2.27` symbol floor, matching the
  distributions the README claims to support.
- Every shipped native library is scanned for the GPL-only RocksDB `range_tree`
  code so the Apache-2.0 licensing statement in the README stays true, and
  `NOTICE` is checked against the upstream `zvec/NOTICE` it reproduces.
- A standalone `lint` job runs Checkstyle (`config/checkstyle.xml`) over the
  hand-written sources, and every job declares an explicit `timeout-minutes`.
- Third-party actions are pinned by commit SHA.

[Unreleased]: https://github.com/zvec-ai/zvec-java/compare/v0.7.0...HEAD
[0.7.0]: https://github.com/zvec-ai/zvec-java/releases/tag/v0.7.0
