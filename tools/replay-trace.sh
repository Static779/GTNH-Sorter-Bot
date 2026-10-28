#!/usr/bin/env bash
# Re-plans a run from its trace file with the current core code (no Minecraft):
#   tools/replay-trace.sh trace.log [minStacksForOwnChest] [layoutWeight] [groups-file]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac --release 8 -nowarn -d "$OUT" $(find "$ROOT/src/main/java/com/whalestorm/chestsorter/core" "$ROOT/tools/replay" -name '*.java')
java -cp "$OUT" Replay "$@"
