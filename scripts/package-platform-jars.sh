#!/usr/bin/env bash
# Builds the per-platform classifier JARs and the nolib JAR from the current
# build output (run `mvn package` first); the artifact layout follows
# org.duckdb:duckdb_jdbc. Outputs:
#
#   target/zvec-java-<version>-macosx-arm64.jar     (classes + jieba dict +
#   target/zvec-java-<version>-linux-x86_64.jar      that platform's natives
#   target/zvec-java-<version>-windows-x86_64.jar    only)
#   target/zvec-java-<version>-nolib.jar            (classes + dict, no natives)
#
# Natives inside the classifier JARs are normalized to the JavaCPP package
# layout org/zvec/binding/<platform>/ regardless of where they were staged.
#
# Native source candidates per platform (first directory containing a shared
# library wins):
#   target/classes/org/zvec/binding/<platform>/      (javacpp compiler output)
#   target/classes/<platform>/                      (root-level layout)
#   src/main/resources/org/zvec/binding/<platform>/  (CI staging)
#   src/main/resources/<platform>/                  (CI staging)
#
# Usage: package-platform-jars.sh <version> [--require-all]
#   --require-all  fail when any supported platform's natives are missing
#                  (used by the Maven Central release pipeline)

set -euo pipefail

VERSION="${1:?usage: package-platform-jars.sh <version> [--require-all]}"
REQUIRE_ALL=0
if [ "${2:-}" = "--require-all" ]; then
  REQUIRE_ALL=1
fi

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

CLASSES_DIR="target/classes"
TARGET_DIR="target"
PKG="org/zvec/binding"
PLATFORMS="macosx-arm64 linux-x86_64 windows-x86_64"

if [ ! -d "$CLASSES_DIR" ]; then
  echo "error: $CLASSES_DIR not found - run 'mvn package' first" >&2
  exit 1
fi

find_platform_src() {
  local p="$1" cand
  for cand in "$CLASSES_DIR/$PKG/$p" "$CLASSES_DIR/$p" \
              "src/main/resources/$PKG/$p" "src/main/resources/$p"; do
    if [ -d "$cand" ] && find "$cand" -maxdepth 1 \
        \( -name '*.so' -o -name '*.dylib' -o -name '*.dll' \) -print -quit | grep -q .; then
      printf '%s' "$cand"
      return 0
    fi
  done
  return 1
}

# Base staging tree: compiled classes + bundled resources (jieba dict), with
# every platform's native directory removed (both known layouts).
base_stage="$(mktemp -d)"
trap 'rm -rf "$base_stage"' EXIT
cp -R "$CLASSES_DIR/." "$base_stage/"
for p in $PLATFORMS; do
  rm -rf "$base_stage/$p" "$base_stage/$PKG/$p"
done

if find "$base_stage" \( -name '*.so' -o -name '*.dylib' -o -name '*.dll' \) -print -quit | grep -q .; then
  echo "error: unexpected native library left in the base staging tree:" >&2
  find "$base_stage" \( -name '*.so' -o -name '*.dylib' -o -name '*.dll' \) >&2
  exit 1
fi

nolib_jar="$TARGET_DIR/zvec-java-$VERSION-nolib.jar"
(cd "$base_stage" && jar cf "$PROJECT_DIR/$nolib_jar" .)
echo "built $nolib_jar (no native libraries)"

missing=""
for p in $PLATFORMS; do
  src="$(find_platform_src "$p" || true)"
  if [ -z "$src" ]; then
    missing="$missing $p"
    continue
  fi
  stage="$(mktemp -d)"
  cp -R "$base_stage/." "$stage/"
  mkdir -p "$stage/$PKG/$p"
  cp -R "$src/." "$stage/$PKG/$p/"
  out="$TARGET_DIR/zvec-java-$VERSION-$p.jar"
  (cd "$stage" && jar cf "$PROJECT_DIR/$out" .)
  rm -rf "$stage"
  echo "built $out (natives from $src)"
done

if [ -n "$missing" ]; then
  if [ "$REQUIRE_ALL" = 1 ]; then
    echo "error: missing native bundles for:$missing" >&2
    echo "hint: stage every platform's natives under $CLASSES_DIR/$PKG/<platform>/" >&2
    echo "      or src/main/resources/<platform>/ (the publish-jar CI job does this)" >&2
    exit 1
  fi
  echo "warning: skipped classifier JARs for:$missing (no staged natives)" >&2
fi

echo "=== platform JAR summary ==="
for f in "$TARGET_DIR"/zvec-java-"$VERSION"-*.jar; do
  [ -e "$f" ] || continue
  case "$f" in *-with-dependencies.jar) continue ;; esac
  echo "$f: $(du -h "$f" | cut -f1)"
done
