# Changelog

Notable changes to zvec-java are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the version number
tracks the bundled zvec native version: `0.7.0` ships the libraries built from
zvec `v0.7.0`. A binding-only fix bumps the third segment.

## [Unreleased]

Tracks zvec `v0.8.0-beta` (`c133f20`); the artifact version is
`0.8.0-SNAPSHOT` until the release is cut.

### Added

- `DataType.VECTOR_UINT8` (28) and `IoBackendType.WINDOWS_OVERLAPPED` (3),
  following the two constants the C API gained since v0.7.0 — a uint8 vector
  storage type for the configurable flat/refine storage, and the Windows
  unbuffered overlapped-I/O backend DiskAnn now uses there.
- `SearchIntegrationTest.testJiebaFtsOnEmbeddedDict`: a Chinese full-text
  search that configures no dictionary anywhere, which is what proves the
  native library's embedded default dictionary actually works.
- Two more platforms in the artifact family: `macosx-x86_64` (Intel macOS) and
  `windows-arm64` (native Windows on ARM), the two SDK targets upstream zvec
  added to its own CI in `_build_macos_x64_sdk.yml` and the
  `aarch64-pc-windows-msvc` leg of `_build_prebuilt.yml`. Each gets a
  classifier JAR, a prebuilt C library archive on the GitHub Release, and a
  `source-test` CI leg that runs the whole suite against natives built on that
  platform - built on the same runners upstream uses, `macos-15-intel` and
  `windows-11-arm`. Both platforms ship a reduced index set, because zvec
  compiles DiskAnn for Linux/macOS-arm64/Windows-x86_64 only and IVF-RaBitQ for
  Linux x86_64 and Windows x86_64 only; the READMEs now say so.
- Architecture guards in both publish workflows, next to the existing glibc and
  macOS deployment-target checks: `lipo -archs` for every staged dylib and the
  COFF `Machine` field for every staged DLL. Without them a toolchain left at
  its default - an `msvc-dev-cmd` without `arch: arm64`, or a
  `CMAKE_OSX_ARCHITECTURES` that never reached the compiler - ships an x86_64
  binary under the ARM64 classifier, and the consumer only finds out when the
  library fails to load.
- A JavaCPP platform-name guard on every `build-natives` leg: the directory
  javacpp emits has to equal the classifier the matrix declares for that leg.
  The aggregation job copies platform directories by name alone, so a leg whose
  JVM resolved a different platform - an x86_64 JVM under emulation on
  `windows-11-arm`, for instance - used to overwrite a sibling leg's bundle and
  only surfaced later as a missing classifier in `build-jar`.

### Changed

- The `zvec` submodule moved from `v0.7.0` to `v0.8.0-beta`. The native
  libraries report `v0.8.0-beta`, so `Zvec.getVersionMajor/Minor/Patch()`
  return 0.8.0.
- This project no longer ships or registers the jieba FTS dictionary. zvec
  v0.8.0 compiles cppjieba's default dictionary and HMM model into
  `libzvec_c_api` (LZ4-compressed, decompressed in memory on first use), so
  the JAR carries no `zvec/jieba_dict/` resources — 5.6 MB of dictionary,
  2.1 MB deflated, out of every artifact — and `Zvec.initialize()` no longer
  extracts anything to `~/.zvec/jieba_dict/<version>`, which also retires
  `-Dzvec.jieba.cache.dir` / `ZVEC_JIEBA_CACHE_DIR` and the read-only-host
  fallback. An external dictionary still wins when one is supplied: the
  per-field `jieba_dict_dir` extra param first, then `ZVEC_JIEBA_DICT_DIR`.
- Vamana two-pass graph construction now reads back as **on** by default,
  matching the native default zvec flipped in #761. `createVamana()` does not
  pass the flag, so the change is upstream's, and the test that pinned the old
  default now pins the new one.
- The `Release` workflow no longer stages `data/jieba_dict` into the prebuilt
  C library archives, mirroring the same removal in zvec's own packaging; the
  `Publish JAR` workflow now fails if dictionary resources reappear inside a
  JAR.
- JavaCPP moved from 1.5.11 to 1.5.14, which is what makes the new
  `windows-arm64` platform buildable at all. 1.5.11 has no
  `windows-arm64.properties`, so on a Windows on ARM runner it fell back to the
  generic GCC command line - `g++ ... -shared -o libjniZvecNative.so` - and the
  x86_64 MinGW `ld` the runner ships then died on our ARM64 import library with
  `unhandled machine type (0xaa64)`. 1.5.13 added the platform (pull #813) with
  the same `cl` / `.dll` settings `windows-x86_64` uses. The license is
  unchanged - still `Apache-2.0 OR GPL-2.0-or-later OR
  GPL-2.0-with-classpath-exception`, still used under Apache-2.0 - and the
  generated `ZvecNative.java` is byte-identical apart from its version header,
  so nothing downstream of the parser moved. As a side effect the JNI glue no
  longer warns about `std::char_traits<unsigned short>`, which 1.5.12 replaced
  with `char16_t` and which libstdc++ has scheduled for removal.

### Removed

- `Zvec.setDefaultJiebaDictDir()`, `Zvec.getDefaultJiebaDictDir()`,
  `Zvec.useBundledJiebaDict()`, `Zvec.isJiebaDictAvailable()`,
  `ConfigData.setJiebaDictDir()`, `ConfigData.getJiebaDictDir()` and the
  internal `JiebaDictSupport`. **Breaking**: these were public in 0.7.0, but
  the four C entry points behind them (`zvec_set_default_jieba_dict_dir`,
  `zvec_get_default_jieba_dict_dir`, `zvec_config_data_set_jieba_dict_dir`,
  `zvec_config_data_get_jieba_dict_dir`) no longer exist in the v0.8.0 C API.
  Nothing replaces them, because there is nothing left to configure; callers
  can simply delete the setup, and `Zvec.initialize()` no longer touches the
  filesystem on their behalf.

### Fixed

- The macOS natives — both architectures now — are built with an 11.0
  deployment target instead of inheriting the macOS version of whatever runner
  built them. `dyld` refuses to load a dylib whose `LC_BUILD_VERSION` minos is
  newer than the host, so the 0.7.0 artifacts — built on `macos-15` with no
  target set — load only on macOS 15, and nothing caught it because the smoke
  test runs on the same runner generation. Both shipped libraries are pinned:
  `libzvec_c_api` through `-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0` (the floor
  upstream zvec builds its own macOS SDK with) and the JavaCPP-built
  `libjniZvecNative` through a mac-only pom profile, and both release workflows
  now fail if a staged dylib reports a newer floor.
- `isDiskAnnSupported()` in the test suite counts Windows x86_64 as supported,
  which it is as of zvec v0.8.0: that release added the overlapped-I/O backend
  (`ZVEC_IO_BACKEND_TYPE_WINDOWS_OVERLAPPED`) DiskAnn reads through there. The
  gate mirrored v0.7.0, where DiskAnn was Linux and macOS-arm64 only, so the
  end-to-end DiskANN test skipped on the `windows-x64` leg instead of running.
  It now runs, and the two platforms zvec really does stub out — macOS x86_64
  and Windows arm64 — keep skipping it.

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
- Both release workflows check out the tag they were given. `actions/checkout`
  without a `ref` resolves to the default branch on a `workflow_dispatch` run,
  so a dispatch started after new commits landed on `main` used to build those
  commits and label them with the tag's version.
- The Windows archive's `.sha256` is written with LF, matching what `sha256sum`
  produces for the other three platforms, so `sha256sum -c` verifies it on
  Linux and macOS instead of failing on a trailing CR in the file name.
- The `-sources` JAR carries sources. The publish pipeline stages every
  platform's natives into `src/main/resources`, which the source plugin used to
  sweep in along with the 5.6 MB dictionary — 44 MB of binaries that are
  already inside the main and classifier JARs.
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
