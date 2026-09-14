# Contributing to zvec-java

Thanks for your interest! zvec-java is a thin, resource-safe Java layer over the
[zvec](https://github.com/alibaba/zvec) C API: JavaCPP parses `c_api.h`, and the
hand-written wrappers in `org.zvec.binding` turn the generated pointers into
`AutoCloseable` Java objects.

By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).
Security problems go to [SECURITY.md](SECURITY.md), not to a public issue.

## Development setup

| Tool | Minimum | Purpose |
|------|---------|---------|
| JDK | 8 | Compile and run (CI builds on Temurin 11) |
| Maven | 3.6 | Build |
| C++ compiler | C++17 | JavaCPP compiles the JNI glue |
| CMake + Ninja | 3.30 / 1.11 | Build the zvec C library |

```bash
git clone https://github.com/zvec-ai/zvec-java.git
cd zvec-java
git submodule update --init --recursive

# 1. Build the zvec C library the binding links against
cd zvec && mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_C_BINDINGS=ON -G Ninja
cmake --build . --target zvec_c_api -j
cd ../..

# 2. Build and test the binding
mvn test
mvn package          # fat JAR with this platform's natives
mvn checkstyle:check # style gate CI runs (rules: config/checkstyle.xml)
```

Point Maven at an existing zvec checkout instead of the submodule with
`-Dzvec.home=/path/to/zvec`, or at prebuilt libraries with
`-Dzvec.lib.path=/path/to/build/lib`.

To check an already-built artifact the way a consumer resolves it, run
`scripts/smoke-test.sh --jar target/zvec-java-0.7.0.jar` (needs a JDK only).

Checkstyle is deliberately narrow — it only flags things that are actual bugs
(a `switch` that falls through, `equals()` without `hashCode()`, a string
compared with `==`, an unused import). Indentation and line length are left to
your IDE, so do not "fix" formatting that the build does not complain about.

## How the binding is put together

1. `presets/ZvecConfig.java` is the JavaCPP `InfoMapper`: it tells the parser how
   to read `zvec/src/include/zvec/c_api.h` (which macros to neutralize, how C
   strings and out-parameters map to Java).
2. The parser generates `src/main/java/org/zvec/binding/ZvecNative.java`. That
   file is **generated and git-ignored — never edit it**. To change how a type
   or function is mapped, edit `ZvecConfig.java` and regenerate.
3. The JavaCPP generator/compiler turns `ZvecNative` into the JNI glue
   (`libjniZvecNative`), linked against `zvec_c_api`.
4. The classes in `org.zvec.binding` wrap `ZvecNative` and are the public API.

### Conventions

- Every object that owns native memory implements `AutoCloseable` and its
  `close()` is idempotent; `close()` nulls the handle so a double close cannot
  turn into a double free.
- Marshal through `NativeSupport` (`utf8`, `string`, `strArray`, `stringArray`)
  rather than calling `BytePointer` directly, and release native strings with
  `ZvecNative.zvec_free`.
- Convert every C return code with `ZvecException.throwIfError(...)`; do not
  swallow non-zero codes.
- Helpers that are not part of the public API stay package-private
  (`NativeSupport`, `JiebaDictSupport`).
- Library code does not print to `System.out`.

### Covering a new C API function

1. Bump the `zvec` submodule to the release that has it and rebuild the C
   library, then `mvn package` to regenerate `ZvecNative.java`.
2. Add the wrapper in the matching class, following the conventions above.
3. Add a test under `src/test/java/org/zvec/binding/`. Prefer strong assertions
   (read back what you wrote, check ordering and counts) over "did not throw".
   Skip platform-gated features with `assumeTrue(...)` so the suite stays green
   on every OS.
4. Update `README.md` **and** `README_CN.md` — the two are kept in sync.
5. If the submodule bump changed `zvec/NOTICE`, regenerate `NOTICE` (see below).

## Pull requests

- Conventional commits (`feat:`, `fix:`, `ci:`, `docs:`, `test:`, `build:`);
  explain the *why* in the body.
- CI must pass: `lint` runs Checkstyle, the `source-test` matrix builds zvec
  from the submodule and the `vendor-test` matrix builds against prebuilt
  libraries, each on macOS ARM64, Linux x86_64 and Windows x86_64.
- Keep both READMEs and the javadoc of anything public up to date — the javadoc
  JAR is published to Maven Central.

## License and NOTICE

`NOTICE` is what Apache-2.0 §4(d) requires us to ship with the redistributed
zvec binaries: our own header followed by `zvec/NOTICE` reproduced verbatim.
Regenerate it whenever the submodule bump changes the upstream file:

```bash
python3 - <<'REGEN'
import io
marker = 'zvec/NOTICE, reproduced verbatim\n' + '=' * 80 + '\n\n'
head = io.open('NOTICE', encoding='utf-8').read().split(marker)[0] + marker
up = io.open('zvec/NOTICE', encoding='utf-8').read()
io.open('NOTICE', 'w', encoding='utf-8').write(head + up)
REGEN
```

That keeps our own header (everything above the `zvec/NOTICE, reproduced
verbatim` banner) and replaces the rest with the current upstream file. The
`Publish JAR` workflow fails if any line of `zvec/NOTICE` is missing from ours,
so drift cannot ship silently.

## Cutting a release

Releases are driven by the zvec version the binding targets; the Maven version
is that version without the leading `v` (`v0.7.0` → `0.7.0`).

1. Bump the `zvec` submodule to the release tag and commit the gitlink only —
   the submodule's own working tree stays dirty from nested submodules and
   `thirdparty/protobuf/` build products, which must not be committed.
2. Update `<version>` in `pom.xml`, the version strings in both READMEs, and
   regenerate `NOTICE` if needed.
3. `mvn test` locally (all tests green) and `mvn checkstyle:check`, then push.
4. Tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`. This runs
   `release.yml`, which builds `zvec_c_api` for all four platforms, verifies the
   Linux glibc floor and the absence of GPL-only RocksDB code, and publishes a
   GitHub Release with the C library archives and their `.sha256`.
5. Dry-run the JAR pipeline:
   `gh workflow run publish-jar.yml --repo zvec-ai/zvec-java -f tag=vX.Y.Z -f deploy_central=false`
6. Deploy for real with `-f deploy_central=true`. This builds the whole artifact
   family inside `manylinux_2_28`, signs it with GPG, and uploads one bundle to
   the Sonatype Central Portal. Because `autoPublish=false` and
   `waitUntil=validated`, the bundle stops at **VALIDATED**: nothing is public
   yet.
7. Review the deployment at <https://central.sonatype.com/publishing/deployments>
   and click **Publish**. This is irreversible — Maven Central never deletes,
   overwrites or reissues a version, so a mistake costs a version number.

What gets published: the main multi-platform JAR, the `macosx-arm64`,
`linux-x86_64`, `linux-arm64`, `windows-x86_64` and `nolib` classifier JARs,
plus sources and javadoc.

Required repository secrets: `CENTRAL_TOKEN_USER`, `CENTRAL_TOKEN_PASSWORD`
(a Central Portal *user token*, not account credentials), `GPG_SIGNING_KEY`
(base64 of the armored private key) and `GPG_SIGNING_PASSPHRASE`. The
`org.zvec` namespace must be verified in the Portal, and the signing key must be
on a public keyserver — Central fetches it from `keyserver.ubuntu.com`,
`keys.openpgp.org` or `pgp.mit.edu`, so publishing to any one of them is enough.

The release key is `1E35478A9977D23F` (fingerprint
`7D0332FD912CE0CBD1043EE21E35478A9977D23F`,
`zvec-java maintainers <zvec@alibaba-inc.com>`), published on
`keyserver.ubuntu.com`. Check that it is still there before a release:

```bash
curl -sS "https://keyserver.ubuntu.com/pks/lookup?op=index&options=mr&search=0x1E35478A9977D23F"
```

A `pub:` line plus a `uid:` line means the key is published and Central can
verify signatures with it.

When rotating the key, push the new one over HKPS rather than the default
`hkp://`. Port 11371 is blocked on many corporate networks, where `--send-keys`
fails with `Network is unreachable` even though HTTPS to the same host works
fine — easy to misread as "the key never got published":

```bash
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <NEW_FINGERPRINT>
```

Then refresh `GPG_SIGNING_KEY` and `GPG_SIGNING_PASSPHRASE` from the new
private key and update the fingerprint above.

## Troubleshooting

- **`testCompile` fails with `incompatible types: org.zvec.binding.X cannot be
  converted to X`.** An Eclipse-JDT-based IDE (or its language server) has
  written its own compiler output into `target/classes` while Maven was running,
  and those class files carry `Unresolved compilation problems`. Run `mvn clean`
  and rebuild, and keep the IDE's builder off `target/`.
- **`UnsatisfiedLinkError` at runtime.** Work through the three tiers in the
  README FAQ; `-Dzvec.native.path=/path/to/zvec/build/lib` is the quickest way
  to test against a local build.
- **Version reported as `v0.0.0-g<sha>`.** The natives were built without
  `-DOVERRIDE_GIT_DESCRIBE`; the release workflows set it from the tag.
