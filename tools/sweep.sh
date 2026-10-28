#!/usr/bin/env bash
# Theoretical runs on generated rooms, one setting varied at a time: tools/sweep.sh [items.tsv] [seeds] > out.csv
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac --release 8 -nowarn -d "$OUT" $(find "$ROOT/src/main/java/com/whalestorm/chestsorter/core" "$ROOT/tools/sweep" -name '*.java')
cd "$ROOT" && java -cp "$OUT" Sweep "$@"
