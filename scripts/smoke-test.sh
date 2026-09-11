#!/usr/bin/env bash
#
# Standalone smoke test for a zvec-java artifact.
#
# Compiles and runs scripts/smoke-test/SmokeTest.java against an already-built
# JAR, so it exercises exactly what a consumer resolves: JavaCPP unpacks and
# loads libjniZvecNative + libzvec_c_api for the current platform out of the
# JAR, the bundled cppjieba dictionary is extracted and registered, and a real
# collection is created, written to and queried (HNSW vector search plus a
# jieba full-text search).
#
# Needs a JDK and nothing else - no Docker, no Maven, no zvec checkout. Run it
# on a machine whose glibc is at or below the floor the README advertises
# before publishing a staged deployment to Maven Central; that is the only way
# to prove the shipped .so files really load there.
#
# Usage:
#   scripts/smoke-test.sh --jar target/zvec-java-0.7.0.jar
#   scripts/smoke-test.sh --repo /tmp/central-staging --version 0.7.0
#   scripts/smoke-test.sh --repo /tmp/central-staging --version 0.7.0 --classifier linux-arm64
#   scripts/smoke-test.sh --jar zvec-java-0.7.0-nolib.jar --lib-dir /opt/zvec/lib
#
# Options:
#   --jar PATH         the zvec-java JAR to test
#   --repo DIR         a Maven-layout directory (contains org/zvec/zvec-java/...)
#   --version V        version to resolve out of --repo (default: 0.7.0)
#   --classifier C     resolve a single-platform classifier JAR from --repo
#   --javacpp PATH     javacpp JAR; auto-resolved from ~/.m2, mvn or repo1 if omitted
#   --lib-dir DIR      natives dir for a nolib JAR, passed as -Dzvec.native.path
#   --keep             do not delete the temporary work directory
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE="$SCRIPT_DIR/smoke-test/SmokeTest.java"
CENTRAL_BASE="https://repo1.maven.org/maven2"

JAR="" REPO="" VERSION="0.7.0" CLASSIFIER="" JAVACPP="" LIB_DIR="" KEEP=0
WORK="$(mktemp -d)"

# Print the header comment block above; stops at the first non-comment line,
# so the help text cannot drift out of sync with the prose.
usage() { awk 'NR < 3 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"; }

cleanup() { [ "$KEEP" = 1 ] || rm -rf "$WORK"; }
trap cleanup EXIT

die() { echo "smoke-test: $*" >&2; exit 1; }

# Pull a <key>value</key> out of the POM descriptor a Maven JAR carries inside
# itself. Prefers unzip, falls back to the JDK's own jar tool, because the
# manylinux images this runs in are not guaranteed to ship unzip.
read_pom_property() {
  local jar="$1" entry="$2" key="$3" xml=""
  if command -v unzip >/dev/null 2>&1; then
    xml="$(unzip -p "$jar" "$entry" 2>/dev/null || true)"
  fi
  if [ -z "$xml" ] && command -v jar >/dev/null 2>&1; then
    local scratch
    scratch="$(mktemp -d)"
    (cd "$scratch" && jar xf "$jar" "$entry" >/dev/null 2>&1) || true
    [ -f "$scratch/$entry" ] && xml="$(cat "$scratch/$entry")"
    rm -rf "$scratch"
  fi
  [ -n "$xml" ] || return 0
  printf '%s' "$xml" | sed -n "s:.*<$key>\([^<]*\)</$key>.*:\1:p" | head -1
}

while [ $# -gt 0 ]; do
  case "$1" in
    --jar)        JAR="${2:?}"; shift 2 ;;
    --repo)       REPO="${2:?}"; shift 2 ;;
    --version)    VERSION="${2:?}"; shift 2 ;;
    --classifier) CLASSIFIER="${2:?}"; shift 2 ;;
    --javacpp)    JAVACPP="${2:?}"; shift 2 ;;
    --lib-dir)    LIB_DIR="${2:?}"; shift 2 ;;
    --keep)       KEEP=1; shift ;;
    -h|--help)    usage; exit 0 ;;
    -*)           die "unknown option $1 (see --help)" ;;
    *)            JAR="$1"; shift ;;
  esac
done

[ -f "$SOURCE" ] || die "cannot find $SOURCE"

# --- Locate the JAR under test -------------------------------------------
if [ -z "$JAR" ]; then
  [ -n "$REPO" ] || die "pass --jar PATH or --repo DIR (see --help)"
  name="zvec-java-$VERSION"
  [ -z "$CLASSIFIER" ] || name="$name-$CLASSIFIER"
  JAR="$REPO/org/zvec/zvec-java/$VERSION/$name.jar"
fi
[ -f "$JAR" ] || die "no such JAR: $JAR"
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"

command -v javac >/dev/null 2>&1 || die "javac not found - install a JDK (11+) and put it on PATH"
command -v java  >/dev/null 2>&1 || die "java not found"

echo "=== Host ==="
echo "  kernel           = $(uname -sr)"
echo "  arch             = $(uname -m)"
if command -v getconf >/dev/null 2>&1 && getconf GNU_LIBC_VERSION >/dev/null 2>&1; then
  echo "  glibc            = $(getconf GNU_LIBC_VERSION | awk '{print $2}')"
elif command -v ldd >/dev/null 2>&1; then
  echo "  glibc            = $(ldd --version 2>&1 | head -1)"
fi
echo "  java             = $(java -version 2>&1 | head -1)"
echo "  jar under test   = $JAR ($(du -h "$JAR" | cut -f1))"

# --- Read the javacpp version out of the artifact itself -----------------
# The JAR carries its own POM descriptor, so the dependency version always
# matches the artifact under test instead of a constant in this script.
if [ -z "$JAVACPP" ]; then
  POM_ENTRY="META-INF/maven/org.zvec/zvec-java/pom.xml"
  JC_VERSION="$(read_pom_property "$JAR" "$POM_ENTRY" javacpp.version)"
  if [ -z "$JC_VERSION" ] && [ -f "$SCRIPT_DIR/../pom.xml" ]; then
    JC_VERSION="$(sed -n 's:.*<javacpp.version>\([^<]*\)</javacpp.version>.*:\1:p' \
      "$SCRIPT_DIR/../pom.xml" | head -1)"
  fi
  [ -n "$JC_VERSION" ] || die "cannot determine the javacpp version; pass --javacpp PATH"
  echo "  javacpp version  = $JC_VERSION (from the artifact's own POM)"

  JC_COORD="org/bytedeco/javacpp/$JC_VERSION/javacpp-$JC_VERSION.jar"
  for candidate in \
    "${HOME}/.m2/repository/$JC_COORD" \
    "${MAVEN_REPO_LOCAL:-/nonexistent}/$JC_COORD"; do
    if [ -f "$candidate" ]; then JAVACPP="$candidate"; break; fi
  done

  if [ -z "$JAVACPP" ] && command -v mvn >/dev/null 2>&1; then
    echo "  resolving javacpp via mvn ..."
    mvn -q -B dependency:copy \
      -Dartifact="org.bytedeco:javacpp:$JC_VERSION" \
      -DoutputDirectory="$WORK" >/dev/null 2>&1 || true
    [ -f "$WORK/javacpp-$JC_VERSION.jar" ] && JAVACPP="$WORK/javacpp-$JC_VERSION.jar"
  fi

  if [ -z "$JAVACPP" ]; then
    echo "  downloading javacpp from repo1 ..."
    curl -fsSL -o "$WORK/javacpp.jar" "$CENTRAL_BASE/$JC_COORD" \
      || die "could not fetch javacpp; pass --javacpp PATH"
    JAVACPP="$WORK/javacpp.jar"
  fi
fi
[ -f "$JAVACPP" ] || die "no such javacpp JAR: $JAVACPP"
echo "  javacpp jar      = $JAVACPP"

# --- Compile and run ------------------------------------------------------
CP="$JAR:$JAVACPP"
mkdir -p "$WORK/classes"
javac -encoding UTF-8 -cp "$CP" -d "$WORK/classes" "$SOURCE"

JAVA_OPTS=(-Dfile.encoding=UTF-8)
if [ -n "$LIB_DIR" ]; then
  # Tier 1 native resolution, for a nolib classifier JAR that ships no .so.
  JAVA_OPTS+=("-Dzvec.native.path=$LIB_DIR")
fi

echo
exec java "${JAVA_OPTS[@]}" -cp "$WORK/classes:$CP" SmokeTest
