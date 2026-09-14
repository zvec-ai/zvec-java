# Security Policy

## Supported Versions

zvec-java tracks the zvec native version it bundles, so a version line is
supported for as long as the corresponding zvec release is.

| Version | Bundled zvec | Supported |
|---------|--------------|-----------|
| 0.7.x   | v0.7.0       | Yes       |
| < 0.7.0 | —            | No (never published) |

## Reporting a Vulnerability

**Please do not open a public GitHub issue for a security problem.**

Report privately by email to **zvec@alibaba-inc.com** with:

- A description of the issue and its impact
- Step-by-step reproduction (Java snippet, JVM version, OS and architecture)
- The affected coordinate, including the classifier if relevant
  (`org.zvec:zvec-java:<version>[:classifier]`)
- Whether the problem is in the Java binding, in the bundled native library, or
  in zvec itself

We aim to acknowledge reports within 3 business days and to publish a fix or a
mitigation as soon as one is available.

## Scope Notes

- Vulnerabilities in the zvec C++ engine, or in `zvec/src/include/zvec/c_api.h`
  itself, belong to the upstream project: https://github.com/alibaba/zvec. This
  repository wraps that API and bundles libraries built from it.
- The published artifacts contain no network-facing code: zvec is an
  in-process, embedded database and zvec-java only exposes it over JNI.
- Note that Maven Central is immutable. A published version cannot be deleted
  or overwritten, so a fix ships as a new version; if a published version must
  be avoided, it is flagged in `CHANGELOG.md` and in the GitHub Security
  Advisories for this repository.
