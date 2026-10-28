#!/usr/bin/env bash
# Compiles the pure-Java core package with NO Minecraft on the classpath and fails if it imports Minecraft/Forge.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; CORE="$ROOT/src/main/java/com/whalestorm/chestsorter/core"
if grep -rnE '^\s*import\s+(net\.minecraft|cpw\.mods|net\.minecraftforge)' "$CORE"; then echo "FAIL: core must not import Minecraft/Forge"; exit 1; fi
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac --release 8 -Xlint:all,-options -Werror -d "$OUT" $(find "$CORE" -name '*.java')
echo "core OK ($(find "$OUT" -name '*.class' | wc -l) classes)"
