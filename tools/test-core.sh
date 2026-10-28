#!/usr/bin/env bash
# Compiles the core package plus tools/core-tests and runs them. No Minecraft, no Gradle.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac --release 8 -Xlint:all,-options -d "$OUT" $(find "$ROOT/src/main/java/com/whalestorm/chestsorter/core" "$ROOT/tools/core-tests" -name '*.java')
java -cp "$OUT" CoreTests
