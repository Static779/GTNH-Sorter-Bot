#!/usr/bin/env bash
# Typo check: compiles ALL mod sources against tools/stubs (hand-checked 1.7.10 signatures).
# Catches syntax, import and type mistakes. It is NOT the real build: run ./gradlew build for that.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac --release 8 -Xlint:all,-options -d "$OUT" $(find "$ROOT/tools/stubs" "$ROOT/src/main/java" -name '*.java')
echo "stub compile OK"
