## What

One or two sentences on the change.

## Why

Link the issue, or describe the bug/gap this closes.

## How

Anything a reviewer should know: which layer changed (preset / generated
binding / high-level wrapper / packaging / CI), and whether the zvec C API is
involved.

## Checklist

- [ ] `mvn test` passes locally (120 tests; needs `zvec/build/lib`, see README)
- [ ] Public API changes carry javadoc — the javadoc JAR is published to Maven Central
- [ ] Native resources are released on every path (`AutoCloseable`, `zvec_free`)
- [ ] New behaviour is covered by a test in `src/test/java/org/zvec/binding/`
- [ ] README.md and README_CN.md updated if this changes usage, platforms or versions
- [ ] `NOTICE` regenerated if the `zvec` submodule bump changed `zvec/NOTICE`
