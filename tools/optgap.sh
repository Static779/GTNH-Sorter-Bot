#!/usr/bin/env bash
# Compares the planner's route with the exact shortest route on small rooms (3-6 chests, up to 9 stacks to move):
#   tools/optgap.sh [rooms]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac --release 8 -nowarn -d "$OUT" $(find "$ROOT/src/main/java/com/whalestorm/chestsorter/core" -name '*.java') "$ROOT/tools/sweep/OptGap.java"
java -Xmx2g -cp "$OUT" OptGap "$@"
