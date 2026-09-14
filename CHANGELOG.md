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
  instead of only naming the error code.
- `Collection` guards every native call with a use-after-close check and exposes
  `isOpen()`; `close()`/`destroy()` are safe to call from several threads.
- Test suite of 122 tests, with strong assertions on the DML/DQL paths and
  `assumeTrue` skips for platform-gated indexes.
- `scripts/smoke-test.sh`: loads a built JAR exactly as a consumer does —
  natives out of the JAR, dictionary extraction, collection create/insert/flush,
  vector search and jieba full-text search — needing nothing but a JDK.

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

[Unreleased]: https://github.com/zvec-ai/zvec-java/compare/v0.7.0...HEAD
[0.7.0]: https://github.com/zvec-ai/zvec-java/releases/tag/v0.7.0
